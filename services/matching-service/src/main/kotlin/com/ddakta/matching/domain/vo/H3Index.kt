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
        // In a real implementation, this would use the H3 library
        // For now, returning the same value as a placeholder
        return this
    }
    
    fun getNeighbors(): List<H3Index> {
        // In a real implementation, this would use the H3 library
        // For now, returning an empty list as a placeholder
        return emptyList()
    }
    
    override fun toString(): String = value
}