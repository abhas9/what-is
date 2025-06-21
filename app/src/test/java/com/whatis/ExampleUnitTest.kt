package com.whatis

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for the MainActivity disambiguation logic.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    
    @Test
    fun test_extractKeyword_simple() {
        val mainActivity = MainActivity()
        // Test the existing extractKeyword method
        val result1 = mainActivity.extractKeyword("what is apple")
        assertEquals("apple", result1)
        
        val result2 = mainActivity.extractKeyword("what is a red apple")
        assertEquals("apple", result2)
        
        val result3 = mainActivity.extractKeyword("dog")
        assertEquals("dog", result3)
    }
}