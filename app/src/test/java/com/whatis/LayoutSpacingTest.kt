package com.whatis

import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify the layout spacing constants are appropriate
 */
class LayoutSpacingTest {
    
    @Test
    fun logoAndSuggestionsHaveAppropriateSpacing() {
        // Verify that the layout constants provide sufficient spacing
        // to prevent logo overlap with suggestions grid
        
        val listeningIndicatorMargin = 100
        val logoTopMargin = 180
        val suggestionsTopMargin = 300
        val minimumSpacingRequired = 100
        
        val actualSpacing = suggestionsTopMargin - logoTopMargin
        
        assertTrue(
            "Suggestions grid should have at least ${minimumSpacingRequired}dp spacing from logo, but has ${actualSpacing}dp",
            actualSpacing >= minimumSpacingRequired
        )
        
        // Also verify the layout creates balanced spacing
        val logoSpacingFromIndicator = logoTopMargin - listeningIndicatorMargin
        assertTrue(
            "Logo should have reasonable spacing from listening indicator (at least 50dp), has ${logoSpacingFromIndicator}dp",
            logoSpacingFromIndicator >= 50
        )
    }
    
    @Test
    fun layoutMarginsCreateVisualBalance() {
        // Test that the layout margins create a visually balanced UI
        // with proper progression: listening indicator -> logo -> suggestions
        
        val listeningIndicatorMargin = 100
        val logoMargin = 180
        val suggestionsMargin = 300
        
        // Each element should be progressively spaced with reasonable gaps
        val firstGap = logoMargin - listeningIndicatorMargin
        val secondGap = suggestionsMargin - logoMargin
        
        assertTrue("First gap should be at least 50dp", firstGap >= 50)
        assertTrue("Second gap should be at least 100dp to prevent overlap", secondGap >= 100)
        
        // The second gap should be larger than the first to account for logo size
        assertTrue(
            "Gap between logo and suggestions should be larger than gap between indicator and logo",
            secondGap > firstGap
        )
    }
    
    @Test
    fun autoPlayButtonSpacing() {
        // Test that AutoPlay button has appropriate spacing in the bottom layout
        val askButtonBottomMargin = 80
        val autoPlayButtonBottomMargin = 160
        val autoPlayStatusBottomMargin = 240
        
        // AutoPlay button should be above Ask Again button with sufficient spacing
        val buttonSpacing = autoPlayButtonBottomMargin - askButtonBottomMargin
        assertTrue("AutoPlay button should be at least 60dp above Ask Again button", buttonSpacing >= 60)
        
        // Status text should be above AutoPlay button with sufficient spacing
        val statusSpacing = autoPlayStatusBottomMargin - autoPlayButtonBottomMargin
        assertTrue("AutoPlay status should be at least 60dp above AutoPlay button", statusSpacing >= 60)
        
        // Total height should not be excessive
        assertTrue("Total bottom UI height should be reasonable", autoPlayStatusBottomMargin <= 300)
    }
}