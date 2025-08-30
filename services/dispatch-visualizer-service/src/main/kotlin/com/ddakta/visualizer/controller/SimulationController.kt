package com.ddakta.visualizer.controller

import com.ddakta.visualizer.service.DriverSimulationService
import com.ddakta.visualizer.service.MatchingSimulationService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulate")
class SimulationController(
    private val driverSimulationService: DriverSimulationService,
    private val matchingSimulationService: MatchingSimulationService
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping("/drivers/start")
    fun startDriverSimulation(@RequestParam(defaultValue = "10") count: Int): ResponseEntity<String> {
        logger.info { "Starting driver simulation for $count drivers" }
        driverSimulationService.startSimulation(count)
        return ResponseEntity.ok("Driver simulation started for $count drivers")
    }

    @PostMapping("/rides/request")
    fun requestRideSimulation(): ResponseEntity<String> {
        logger.info { "Requesting ride simulation" }
        matchingSimulationService.requestRide()
        return ResponseEntity.ok("Ride simulation requested")
    }
}
