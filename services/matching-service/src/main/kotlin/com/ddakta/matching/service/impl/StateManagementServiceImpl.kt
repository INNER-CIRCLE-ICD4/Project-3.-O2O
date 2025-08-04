package com.ddakta.matching.service.impl

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.exception.InvalidRideStateTransitionException
import com.ddakta.matching.service.StateManagementService
import mu.KotlinLogging
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.persist.StateMachinePersister
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
@Transactional
class StateManagementServiceImpl(
    private val stateMachineFactory: StateMachineFactory<RideStatus, RideEvent>,
    private val stateMachinePersister: StateMachinePersister<RideStatus, RideEvent, UUID>
) : StateManagementService {

    private val logger = KotlinLogging.logger {}
    private val stateMachines = ConcurrentHashMap<UUID, StateMachine<RideStatus, RideEvent>>()

    companion object {
        const val RIDE_ID_HEADER = "rideId"
        const val ACTOR_ID_HEADER = "actorId"
        const val RIDE_ENTITY_HEADER = "ride"
        const val STATE_TRANSITION_TIMEOUT_MS = 5000L
    }

    override fun processStateTransition(ride: Ride, event: RideEvent, actorId: UUID): Ride {
        val stateMachine = getOrCreateStateMachine(ride.id!!, ride.status)

        try {
            // 현재 상태 확인
            val currentState = stateMachine.state.id
            if (currentState != ride.status) {
                logger.warn { "State machine state $currentState doesn't match ride status ${ride.status}" }
                // 상태 동기화
                resetStateMachine(stateMachine, ride.id, ride.status)
            }

            // 전이 가능성 검증
            if (!validateTransition(ride.status, event)) {
                throw InvalidRideStateTransitionException(
                    "Invalid transition from ${ride.status} with event $event"
                )
            }

            // 이벤트 메시지 생성
            val message = MessageBuilder
                .withPayload(event)
                .setHeader(RIDE_ID_HEADER, ride.id)
                .setHeader(ACTOR_ID_HEADER, actorId)
                .setHeader(RIDE_ENTITY_HEADER, ride)
                .build()

            // 상태 전이 실행
            val result = stateMachine.sendEvent(message)

            if (!result) {
                throw InvalidRideStateTransitionException(
                    "State transition failed for ride ${ride.id} from ${ride.status} with event $event"
                )
            }

            // 타임아웃 처리를 위한 대기
            var waitTime = 0L
            while (stateMachine.state.id == currentState && waitTime < STATE_TRANSITION_TIMEOUT_MS) {
                TimeUnit.MILLISECONDS.sleep(100)
                waitTime += 100
            }

            if (stateMachine.state.id == currentState) {
                throw InvalidRideStateTransitionException(
                    "State transition timeout for ride ${ride.id}"
                )
            }

            // 새로운 상태로 운행 업데이트
            val newStatus = stateMachine.state.id
            updateRideStatus(ride, newStatus, event, actorId)

            // 상태 저장
            persistStateMachine(stateMachine, ride.id)

            logger.info { "Successfully transitioned ride ${ride.id} from $currentState to $newStatus with event $event" }

            return ride

        } catch (e: Exception) {
            logger.error(e) { "Error processing state transition for ride ${ride.id}" }
            throw e
        }
    }

    override fun validateTransition(from: RideStatus, event: RideEvent): Boolean {
        return when (from) {
            RideStatus.REQUESTED -> event in listOf(
                RideEvent.MATCH_FOUND,
                RideEvent.RIDE_CANCELLED,
                RideEvent.MATCHING_TIMEOUT
            )
            RideStatus.MATCHED -> event in listOf(
                RideEvent.ASSIGN_DRIVER,
                RideEvent.RIDE_CANCELLED,
                RideEvent.DRIVER_TIMEOUT
            )
            RideStatus.DRIVER_ASSIGNED -> event in listOf(
                RideEvent.DRIVER_EN_ROUTE,
                RideEvent.RIDE_CANCELLED
            )
            RideStatus.EN_ROUTE_TO_PICKUP -> event in listOf(
                RideEvent.DRIVER_ARRIVED,
                RideEvent.RIDE_CANCELLED
            )
            RideStatus.ARRIVED_AT_PICKUP -> event in listOf(
                RideEvent.START_TRIP,
                RideEvent.RIDE_CANCELLED
            )
            RideStatus.ON_TRIP -> event in listOf(
                RideEvent.TRIP_COMPLETED
            )
            RideStatus.COMPLETED -> false // 최종 상태
            RideStatus.CANCELLED -> false // 최종 상태
            RideStatus.FAILED -> false // 최종 상태
        }
    }

    override fun getStateMachine(rideId: UUID): StateMachine<RideStatus, RideEvent> {
        return stateMachines[rideId] ?: throw IllegalStateException("State machine not found for ride $rideId")
    }

    override fun releaseStateMachine(rideId: UUID) {
        stateMachines.remove(rideId)?.let { stateMachine ->
            stateMachine.stop()
            logger.debug { "Released state machine for ride $rideId" }
        }
    }

    private fun getOrCreateStateMachine(
        rideId: UUID,
        currentStatus: RideStatus
    ): StateMachine<RideStatus, RideEvent> {
        return stateMachines.computeIfAbsent(rideId) { id ->
            val stateMachine = stateMachineFactory.getStateMachine(id)

            // 저장된 상태 복원 시도
            try {
                stateMachinePersister.restore(stateMachine, id)
            } catch (e: Exception) {
                logger.debug { "No persisted state found for ride $id, initializing with status $currentStatus" }
                // 저장된 상태가 없으면 현재 상태로 초기화
                resetStateMachine(stateMachine, id, currentStatus)
            }

            stateMachine.start()
            stateMachine
        }
    }

    private fun resetStateMachine(
        stateMachine: StateMachine<RideStatus, RideEvent>,
        rideId: UUID,
        status: RideStatus
    ) {
        stateMachine.stop()

        // TODO: 임시 수정 - DefaultStateMachineContext 생성자 파라미터 조정
        val context = DefaultStateMachineContext<RideStatus, RideEvent>(
            status,
            null,
            mutableMapOf<String, Any>(),
            null,
            mutableMapOf<RideStatus, RideStatus>(),
            rideId.toString()
        )

        stateMachine.stateMachineAccessor.doWithAllRegions { accessor ->
            accessor.resetStateMachine(context)
        }

        stateMachine.start()
    }

    private fun persistStateMachine(
        stateMachine: StateMachine<RideStatus, RideEvent>,
        rideId: UUID
    ) {
        try {
            stateMachinePersister.persist(stateMachine, rideId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist state machine for ride $rideId" }
        }
    }

    private fun updateRideStatus(
        ride: Ride,
        newStatus: RideStatus,
        event: RideEvent,
        actorId: UUID
    ) {
        when (newStatus) {
            RideStatus.MATCHED -> {
                // 매칭됨 상태 업데이트는 MatchingService에서 처리
            }
            RideStatus.DRIVER_ASSIGNED -> {
                // 드라이버 할당은 별도 처리
            }
            RideStatus.EN_ROUTE_TO_PICKUP -> {
                ride.updateStatus(newStatus)
            }
            RideStatus.ARRIVED_AT_PICKUP -> {
                ride.updateStatus(newStatus)
            }
            RideStatus.ON_TRIP -> {
                ride.startTrip()
            }
            RideStatus.COMPLETED -> {
                // 완료 처리는 별도 메서드에서
            }
            RideStatus.CANCELLED -> {
                // 취소 처리는 별도 메서드에서
            }
            RideStatus.FAILED -> {
                ride.updateStatus(newStatus)
            }
            else -> {
                ride.updateStatus(newStatus)
            }
        }
    }
}
