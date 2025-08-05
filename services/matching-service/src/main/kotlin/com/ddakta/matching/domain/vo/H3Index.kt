package com.ddakta.matching.domain.vo

import java.io.Serializable

data class H3Index(
    val value: String
) : Serializable {
    
    init {
        require(value.isNotBlank()) { "H3 index cannot be blank" }
        require(value.length == 15) { "H3 index must be 15 characters long" }
    }
    
    fun getParent(resolution: Int): H3Index {
        // 실제 구현에서는 H3 라이브러리를 사용할 예정
        // 현재는 플레이스홀더로 동일한 값 반환
        return this
    }
    
    fun getNeighbors(): List<H3Index> {
        // 실제 구현에서는 H3 라이브러리를 사용할 예정
        // 현재는 플레이스홀더로 빈 리스트 반환
        return emptyList()
    }
    
    override fun toString(): String = value
}