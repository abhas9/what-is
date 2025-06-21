package com.whatis

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Test to verify the app logo functionality
 */
@RunWith(AndroidJUnit4::class)
class LogoTest {
    @Test
    fun appLogoResourceExists() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Verify the logo drawable resource exists and can be loaded
        val logoDrawable: Drawable? = try {
            appContext.getDrawable(R.drawable.app_logo)
        } catch (e: Exception) {
            null
        }
        
        assertNotNull("Logo drawable should exist and be loadable", logoDrawable)
    }
    
    @Test
    fun logoResourceHasCorrectType() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Verify the resource exists in the drawable folder
        val resourceId = appContext.resources.getIdentifier("app_logo", "drawable", appContext.packageName)
        assertTrue("Logo resource should exist in drawable folder", resourceId != 0)
    }
}