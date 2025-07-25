package com.ddakta.auth.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.*

@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID()
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
}