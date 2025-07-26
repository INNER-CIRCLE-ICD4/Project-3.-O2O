package com.ddakta.auth.service

import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.domain.repository.UserRepository
import com.ddakta.utils.exception.ResourceNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository
) {
    
    @Cacheable(value = ["users"], key = "#userId")
    fun findById(userId: UUID): User {
        return userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User", userId) }
    }
    
    fun findByIds(userIds: List<UUID>): List<User> {
        return userRepository.findAllById(userIds)
    }
    
    fun findByEmail(email: String): User {
        return userRepository.findByEmail(email)
            ?: throw ResourceNotFoundException("User", email)
    }
    
    // 향후 사용자 정보 업데이트 시 캐시도 함께 업데이트
    @Transactional
    @CachePut(value = ["users"], key = "#userId")
    fun updateUser(userId: UUID, updateFunction: (User) -> User): User? {
        return userRepository.findById(userId)
            .map { user ->
                val updatedUser = updateFunction(user)
                userRepository.save(updatedUser)
            }
            .orElse(null)
    }
    
    // 사용자 삭제 시 캐시도 함께 삭제
    @Transactional
    @CacheEvict(value = ["users"], key = "#userId")
    fun deleteUser(userId: UUID) {
        userRepository.deleteById(userId)
    }
}