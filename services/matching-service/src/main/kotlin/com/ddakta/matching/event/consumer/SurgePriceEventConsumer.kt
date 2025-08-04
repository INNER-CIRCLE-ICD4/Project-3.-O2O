package com.ddakta.matching.event.consumer

import com.ddakta.matching.event.model.SurgePricingUpdatedEvent
import com.ddakta.matching.event.model.TrafficConditionsUpdatedEvent
import com.ddakta.matching.service.SurgePriceService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SurgePriceEventConsumer(
    private val surgePriceService: SurgePriceService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @KafkaListener(
        topics = ["surge-pricing-updated"],
        groupId = "matching-service-surge",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleSurgePricingUpdated(
        event: SurgePricingUpdatedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "Surge pricing updated for ${event.h3Index}: " +
            "${event.previousMultiplier} -> ${event.newMultiplier} (${event.reason})"
        }
        
        try {
            // Update local surge price cache
            surgePriceService.updateSurgeMultiplier(event.h3Index, event.newMultiplier)
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing surge pricing update for ${event.h3Index}" }
            throw e
        }
    }
    
    @KafkaListener(
        topics = ["traffic-conditions-updated"],
        groupId = "matching-service-surge",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleTrafficConditionsUpdated(
        event: TrafficConditionsUpdatedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "Traffic conditions updated for ${event.h3Index}: " +
            "congestion=${event.congestionLevel}, avgSpeed=${event.averageSpeed}km/h"
        }
        
        try {
            // Severe traffic could trigger surge pricing
            when (event.congestionLevel) {
                "SEVERE" -> {
                    logger.warn { "Severe traffic in ${event.h3Index}, considering surge increase" }
                    // The surge price service could factor this into calculations
                }
                "HIGH" -> {
                    logger.info { "High traffic in ${event.h3Index}" }
                }
            }
            
            // Log any incidents
            event.incidents?.forEach { incident ->
                logger.info { 
                    "${incident.type} incident at ${incident.location.h3Index}: " +
                    "severity=${incident.severity}, delay=${incident.estimatedDelay}s"
                }
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing traffic conditions update for ${event.h3Index}" }
            throw e
        }
    }
}