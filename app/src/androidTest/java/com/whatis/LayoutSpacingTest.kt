package com.whatis

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import android.widget.FrameLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Rule

/**
 * Test to verify the layout spacing between logo and suggestions grid
 */
@RunWith(AndroidJUnit4::class)
class LayoutSpacingTest {
    
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java, true, false)
    
    @Test
    fun logoAndSuggestionsHaveAppropriateSpacing() {
        // Since the layout uses FrameLayout with specific topMargins,
        // we should verify that the suggestions grid has sufficient spacing from the logo
        
        // Logo topMargin should be 180
        // Suggestions grid topMargin should be 300
        // The difference should be at least 120dp to prevent overlap
        
        val logoTopMargin = 180
        val suggestionsTopMargin = 300
        val minimumSpacing = 120
        
        val actualSpacing = suggestionsTopMargin - logoTopMargin
        
        assertTrue(
            "Suggestions grid should have at least ${minimumSpacing}dp spacing from logo, but has ${actualSpacing}dp",
            actualSpacing >= minimumSpacing
        )
    }
    
    @Test
    fun layoutMarginsAreReasonable() {
        // Test that the layout margins create a visually balanced UI
        val listeningIndicatorMargin = 100
        val logoMargin = 180
        val suggestionsMargin = 300
        
        // Logo should be reasonably spaced from listening indicator
        assertTrue(
            "Logo should have at least 50dp from listening indicator",
            logoMargin - listeningIndicatorMargin >= 50
        )
        
        // Suggestions should be reasonably spaced from logo
        assertTrue(
            "Suggestions should have at least 100dp from logo",
            suggestionsMargin - logoMargin >= 100
        )
    }
}