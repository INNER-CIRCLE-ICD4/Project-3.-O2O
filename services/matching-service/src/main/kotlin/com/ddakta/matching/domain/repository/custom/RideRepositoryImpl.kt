package com.ddakta.matching.domain.repository.custom

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.Predicate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
class RideRepositoryImpl : RideRepositoryCustom {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findRidesForMatching(
        h3Indexes: List<String>,
        maxAge: LocalDateTime,
        limit: Int
    ): List<Ride> {
        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(Ride::class.java)
        val root = query.from(Ride::class.java)

        val predicates = mutableListOf<Predicate>()
        predicates.add(cb.equal(root.get<RideStatus>("status"), RideStatus.REQUESTED))
        predicates.add(cb.greaterThan(root.get("requestedAt"), maxAge))
        predicates.add(root.get<String>("pickupLocation").get<String>("h3Index").`in`(h3Indexes))

        query.select(root)
            .where(*predicates.toTypedArray())
            .orderBy(cb.asc(root.get<LocalDateTime>("requestedAt")))

        return entityManager.createQuery(query)
            .setMaxResults(limit)
            .resultList
    }

    override fun findCompletedRidesInTimeRange(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        passengerId: UUID?,
        driverId: UUID?
    ): List<Ride> {
        val jpql = buildString {
            append("SELECT r FROM Ride r WHERE r.status = :status ")
            append("AND r.completedAt BETWEEN :startTime AND :endTime ")
            passengerId?.let { append("AND r.passengerId = :passengerId ") }
            driverId?.let { append("AND r.driverId = :driverId ") }
            append("ORDER BY r.completedAt DESC")
        }

        val query = entityManager.createQuery(jpql, Ride::class.java)
            .setParameter("status", RideStatus.COMPLETED)
            .setParameter("startTime", startTime)
            .setParameter("endTime", endTime)

        passengerId?.let { query.setParameter("passengerId", it) }
        driverId?.let { query.setParameter("driverId", it) }

        return query.resultList
    }

    override fun updateRideStatusBulk(
        rideIds: List<UUID>,
        newStatus: RideStatus
    ): Int {
        if (rideIds.isEmpty()) return 0

        val jpql = """
            UPDATE Ride r
            SET r.status = :newStatus, r.updatedAt = :now
            WHERE r.id IN :rideIds
        """

        return entityManager.createQuery(jpql)
            .setParameter("newStatus", newStatus)
            .setParameter("now", LocalDateTime.now())
            .setParameter("rideIds", rideIds)
            .executeUpdate()
    }

    override fun findRidesNearLocation(
        h3Index: String,
        neighboringH3Indexes: List<String>,
        statuses: List<RideStatus>,
        limit: Int
    ): List<Ride> {
        val allH3Indexes = listOf(h3Index) + neighboringH3Indexes

        val jpql = """
            SELECT r FROM Ride r
            WHERE r.pickupLocation.h3Index IN :h3Indexes
            AND r.status IN :statuses
            ORDER BY r.requestedAt DESC
        """

        return entityManager.createQuery(jpql, Ride::class.java)
            .setParameter("h3Indexes", allH3Indexes)
            .setParameter("statuses", statuses)
            .setMaxResults(limit)
            .resultList
    }

    override fun getRideStatistics(
        h3Index: String,
        timeWindow: LocalDateTime
    ): RideStatistics {
        val statsQuery = """
            SELECT
                COUNT(r) as totalRides,
                SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedRides,
                SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelledRides,
                AVG(CASE WHEN r.matchedAt IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (r.matchedAt - r.requestedAt))
                    ELSE NULL END) as avgWaitTime,
                AVG(CASE WHEN r.completedAt IS NOT NULL AND r.startedAt IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (r.completedAt - r.startedAt))
                    ELSE NULL END) as avgTripDuration
            FROM rides r
            WHERE r.pickup_h3 = :h3Index
            AND r.requested_at > :timeWindow
        """

        val result = entityManager.createNativeQuery(statsQuery)
            .setParameter("h3Index", h3Index)
            .setParameter("timeWindow", timeWindow)
            .singleResult as Array<*>

        val demandQuery = """
            SELECT COUNT(DISTINCT r.id)
            FROM rides r
            WHERE r.pickup_h3 = :h3Index
            AND r.status = 'REQUESTED'
            AND r.requested_at > :timeWindow
        """

        val demandCount = entityManager.createNativeQuery(demandQuery)
            .setParameter("h3Index", h3Index)
            .setParameter("timeWindow", timeWindow)
            .singleResult as Number

        // Note: Supply count would come from Location Service
        // For now, returning a placeholder
        val supplyCount = 0

        return RideStatistics(
            totalRides = (result[0] as Number).toLong(),
            completedRides = (result[1] as Number).toLong(),
            cancelledRides = (result[2] as Number).toLong(),
            averageWaitTime = (result[3] as Number?)?.toDouble() ?: 0.0,
            averageTripDuration = (result[4] as Number?)?.toDouble() ?: 0.0,
            demandCount = demandCount.toInt(),
            supplyCount = supplyCount
        )
    }
}
