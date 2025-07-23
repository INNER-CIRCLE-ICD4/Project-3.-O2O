package com.ddakta.auth.entity

import com.ddakta.auth.dto.UserDto
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val username: String,

    val name: String,

    val email: String,

    val role: String

) {
    companion object {
        fun register(request: UserDto): User {
            return User(
                id = null,
                username =  request.username,
                name = request.name,
                email = request.email,
                role = request.role
            )
        }
    }
}
