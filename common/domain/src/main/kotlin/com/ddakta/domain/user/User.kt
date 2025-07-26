package com.ddakta.domain.user

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val username: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: UserType = UserType.PASSENGER,

    @Column(nullable = false)
    val role: String = "ROLE_USER"

) {
    companion object {
        fun create(
            username: String,
            name: String,
            email: String,
            type: UserType
        ): User {
            return User(
                username = username,
                name = name,
                email = email,
                type = type,
                role = when (type) {
                    UserType.PASSENGER -> "ROLE_USER"
                    UserType.DRIVER -> "ROLE_DRIVER"
                    UserType.ADMIN -> "ROLE_ADMIN"
                }
            )
        }
    }
}
