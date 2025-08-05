package com.ddakta.matching.config

import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachine
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.listener.StateMachineListener
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import java.util.*

@Configuration
@EnableStateMachine
class StateMachineConfig : EnumStateMachineConfigurerAdapter<RideStatus, RideEvent>() {

    override fun configure(states: StateMachineStateConfigurer<RideStatus, RideEvent>) {
        states.withStates()
            .initial(RideStatus.REQUESTED)
            .states(EnumSet.allOf(RideStatus::class.java))
            .end(RideStatus.COMPLETED)
            .end(RideStatus.CANCELLED)
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<RideStatus, RideEvent>) {
        transitions
            // 요청에서 매칭으로
            .withExternal()
            .source(RideStatus.REQUESTED).target(RideStatus.MATCHED)
            .event(RideEvent.MATCH_FOUND)
            .and()
            
            // 매칭에서 드라이버 할당으로
            .withExternal()
            .source(RideStatus.MATCHED).target(RideStatus.DRIVER_ASSIGNED)
            .event(RideEvent.DRIVER_ACCEPTED)
            .and()
            
            // 드라이버 할당에서 픽업 이동 중으로
            .withExternal()
            .source(RideStatus.DRIVER_ASSIGNED).target(RideStatus.EN_ROUTE_TO_PICKUP)
            .event(RideEvent.DRIVER_EN_ROUTE)
            .and()
            
            // 픽업 이동 중에서 픽업 도착으로
            .withExternal()
            .source(RideStatus.EN_ROUTE_TO_PICKUP).target(RideStatus.ARRIVED_AT_PICKUP)
            .event(RideEvent.DRIVER_ARRIVED)
            .and()
            
            // 픽업 도착에서 운행 중으로
            .withExternal()
            .source(RideStatus.ARRIVED_AT_PICKUP).target(RideStatus.ON_TRIP)
            .event(RideEvent.TRIP_STARTED)
            .and()
            
            // 운행 중에서 운행 완료로
            .withExternal()
            .source(RideStatus.ON_TRIP).target(RideStatus.COMPLETED)
            .event(RideEvent.TRIP_COMPLETED)
            .and()
            
            // 다양한 상태에서 취소
            .withExternal()
            .source(RideStatus.REQUESTED).target(RideStatus.CANCELLED)
            .event(RideEvent.RIDE_CANCELLED)
            .and()
            .withExternal()
            .source(RideStatus.MATCHED).target(RideStatus.CANCELLED)
            .event(RideEvent.RIDE_CANCELLED)
            .and()
            .withExternal()
            .source(RideStatus.DRIVER_ASSIGNED).target(RideStatus.CANCELLED)
            .event(RideEvent.RIDE_CANCELLED)
            .and()
            .withExternal()
            .source(RideStatus.EN_ROUTE_TO_PICKUP).target(RideStatus.CANCELLED)
            .event(RideEvent.RIDE_CANCELLED)
            .and()
            .withExternal()
            .source(RideStatus.ARRIVED_AT_PICKUP).target(RideStatus.CANCELLED)
            .event(RideEvent.RIDE_CANCELLED)
    }

    override fun configure(config: StateMachineConfigurationConfigurer<RideStatus, RideEvent>) {
        config
            .withConfiguration()
            .autoStartup(true)
            .listener(listener())
    }

    fun listener(): StateMachineListener<RideStatus, RideEvent> {
        return object : StateMachineListenerAdapter<RideStatus, RideEvent>() {
            override fun stateChanged(from: State<RideStatus, RideEvent>?, to: State<RideStatus, RideEvent>?) {
                println("State changed from ${from?.id} to ${to?.id}")
            }
        }
    }
}