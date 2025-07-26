package com.ddakta.auth.entity

import com.ddakta.auth.dto.LoginUserRequest
import jakarta.persistence.*
import org.hibernate.annotations.NaturalId

@Entity
@Table(name = "users")
class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @NaturalId
    val email: String,

    val name: String,

    @Enumerated(EnumType.STRING)
    val role: UserRole,

    val googleId: String?,
    val naverId: String?,

    val oAuthList: MutableList<String>,

    ) {
    companion object {
        fun register(request: LoginUserRequest): User {

            return User(
                id = null,
                email = request.email,
                name = request.name,
                role = UserRole.PENDING_REGISTRATION,
                googleId = request.userId.takeIf { it.equals("google", ignoreCase = true) },
                naverId = request.userId.takeIf { it.equals("naver", ignoreCase = true) },
                oAuthList = mutableListOf(request.oAuthType)
            )
        }
    }

    fun oAuthIntegration(oauth: String) {
        this.oAuthList.add(oauth)
    }
}
