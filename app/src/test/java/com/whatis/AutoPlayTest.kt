package com.whatis

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AutoPlay logic validation
 */
class AutoPlayTest {
    
    @Test
    fun localAnswerStore_hasMultipleItems() {
        assertTrue("LocalAnswerStore should have multiple items for AutoPlay", 
                   LocalAnswerStore.answers.size > 10)
    }
    
    @Test
    fun localAnswerStore_hasValidKeys() {
        // Verify that all keys are non-empty strings
        LocalAnswerStore.answers.keys.forEach { key ->
            assertFalse("Key should not be empty", key.isEmpty())
            assertFalse("Key should not be blank", key.isBlank())
        }
    }
    
    @Test
    fun localAnswerStore_hasValidAnswers() {
        // Verify that all answers are non-empty strings
        LocalAnswerStore.answers.values.forEach { answer ->
            assertFalse("Answer should not be empty", answer.isEmpty())
            assertFalse("Answer should not be blank", answer.isBlank())
        }
    }
    
    @Test
    fun autoPlay_cycleLogic_noDuplicatesInSingleCycle() {
        // Simulate the autoplay cycle logic
        val availableItems = LocalAnswerStore.answers.keys.toMutableList()
        availableItems.shuffle()
        val usedItems = mutableSetOf<String>()
        
        // Pick items until all are used
        val selectedItems = mutableListOf<String>()
        while (usedItems.size < LocalAnswerStore.answers.size) {
            val nextItem = availableItems.firstOrNull { !usedItems.contains(it) }
            assertNotNull("Should always find an unused item", nextItem)
            
            usedItems.add(nextItem!!)
            selectedItems.add(nextItem)
        }
        
        // Verify no duplicates
        assertEquals("Should select all unique items in a cycle", 
                     LocalAnswerStore.answers.size, selectedItems.toSet().size)
        
        // Verify all original items are selected
        assertEquals("Should select all available items", 
                     LocalAnswerStore.answers.keys.toSet(), selectedItems.toSet())
    }
    
    @Test
    fun autoPlay_multipleCycles_workCorrectly() {
        // Simulate multiple cycles
        val totalCycles = 3
        val allSelectedItems = mutableListOf<String>()
        
        repeat(totalCycles) {
            val availableItems = LocalAnswerStore.answers.keys.toMutableList()
            availableItems.shuffle()
            val usedItems = mutableSetOf<String>()
            
            while (usedItems.size < LocalAnswerStore.answers.size) {
                val nextItem = availableItems.firstOrNull { !usedItems.contains(it) }
                usedItems.add(nextItem!!)
                allSelectedItems.add(nextItem)
            }
        }
        
        // Each cycle should have exactly all items
        assertEquals("Total items should be correct across cycles",
                     LocalAnswerStore.answers.size * totalCycles, allSelectedItems.size)
    }
}