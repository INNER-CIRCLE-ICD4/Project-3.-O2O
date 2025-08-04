package com.ddakta.matching.configuration

import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.StateMachinePersist
import org.springframework.statemachine.persist.DefaultStateMachinePersister
import org.springframework.statemachine.persist.StateMachinePersister
import org.springframework.statemachine.support.DefaultStateMachineContext
import java.util.*

/**
 * StateMachine 영속성 설정
 * 현재는 Redis StateMachine 의존성이 없어서 기본 구현을 사용
 */
@Configuration
class StateMachinePersisterConfig {
    
    @Bean
    fun stateMachineMemoryPersister(): StateMachinePersist<RideStatus, RideEvent, UUID> {
        return object : StateMachinePersist<RideStatus, RideEvent, UUID> {
            private val storage = mutableMapOf<UUID, DefaultStateMachineContext<RideStatus, RideEvent>>()
            
            override fun write(context: org.springframework.statemachine.StateMachineContext<RideStatus, RideEvent>?, contextObj: UUID?) {
                if (context != null && contextObj != null) {
                    storage[contextObj] = DefaultStateMachineContext(
                        context.state,
                        context.event,
                        context.eventHeaders,
                        context.extendedState
                    )
                }
            }
            
            override fun read(contextObj: UUID?): org.springframework.statemachine.StateMachineContext<RideStatus, RideEvent>? {
                return contextObj?.let { storage[it] }
            }
        }
    }
    
    @Bean
    fun stateMachinePersister(
        stateMachineMemoryPersister: StateMachinePersist<RideStatus, RideEvent, UUID>
    ): StateMachinePersister<RideStatus, RideEvent, UUID> {
        return DefaultStateMachinePersister(stateMachineMemoryPersister)
    }
}