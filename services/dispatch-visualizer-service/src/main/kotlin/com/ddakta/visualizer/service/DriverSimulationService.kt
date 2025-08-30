package com.ddakta.visualizer.service

import com.ddakta.visualizer.client.LocationServiceClient
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class DriverSimulationService(
    private val locationServiceClient: LocationServiceClient
) {

    private val logger = KotlinLogging.logger {}
    private val scheduler = Executors.newScheduledThreadPool(5)
    private val simulatedDrivers = mutableMapOf<String, SimulatedDriver>()
    private val driverTasks = mutableMapOf<String, ScheduledFuture<*>>()

    fun startSimulation(count: Int) {
        stopSimulation() // 기존 시뮬레이션 중지
        simulatedDrivers.clear()
        driverTasks.clear()

        repeat(count) {
            val driverId = UUID.randomUUID().toString()
            val initialLat = 37.5 + (Random.nextDouble() - 0.5) * 0.1 // 서울 중심 근처
            val initialLon = 127.0 + (Random.nextDouble() - 0.5) * 0.1 // 서울 중심 근처
            val driver = SimulatedDriver(driverId, initialLat, initialLon)
            simulatedDrivers[driverId] = driver

            val task = scheduler.scheduleAtFixedRate({
                driver.moveRandomly() // 위치 랜덤 이동
                locationServiceClient.sendLocationUpdate(
                    driver.id,
                    driver.latitude,
                    driver.longitude
                )
            }, 0, 5, TimeUnit.SECONDS) // 5초마다 위치 업데이트

            driverTasks[driverId] = task
            logger.info { "Simulating driver $driverId at (${driver.latitude}, ${driver.longitude})" }
        }
    }

    fun stopSimulation() {
        driverTasks.values.forEach { it.cancel(true) }
        scheduler.shutdownNow()
        logger.info { "Driver simulation stopped" }
    }

    data class SimulatedDriver(
        val id: String,
        var latitude: Double,
        var longitude: Double
    ) {
        fun moveRandomly() {
            latitude += (Random.nextDouble() - 0.5) * 0.001 // 작은 범위 내에서 랜덤 이동
            longitude += (Random.nextDouble() - 0.5) * 0.001
        }
    }
}
