package com.ddakta.auth.entity

import com.ddakta.auth.domain.enums.AuthProvider
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener::class)
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: AuthProvider,

    @Column(name = "provider_id", nullable = false, unique = true)
    val providerId: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "nickname", nullable = false)
    var nickname: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")]
    )
    @Column(name = "role", length = 50)
    val roles: MutableSet<String> = mutableSetOf("ROLE_PASSENGER")
) {

    /** 생성 일시 (자동으로 채워집니다) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    /** 수정 일시 (자동으로 갱신됩니다) */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
}
