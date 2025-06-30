package com.whatis

import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference

class WhatIsApplication : Application() {
    
    companion object {
        private const val TAG = "WhatIsApp_CrashHandler"
        private var currentActivityRef: WeakReference<Context>? = null
        
        fun setCurrentActivity(activity: Context) {
            currentActivityRef = WeakReference(activity)
        }
        
        fun getCurrentActivity(): Context? {
            return currentActivityRef?.get()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        Log.d(TAG, "Debug crash handler initialized")
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", exception)
            
            // Show the exception dialog on the main thread
            Handler(Looper.getMainLooper()).post {
                showExceptionDialog(exception)
            }
            
            // Give some time for the dialog to show before calling the default handler
            Handler(Looper.getMainLooper()).postDelayed({
                defaultHandler?.uncaughtException(thread, exception)
            }, 3000) // 3 second delay to allow user to see the dialog
        }
    }
    
    private fun showExceptionDialog(exception: Throwable) {
        try {
            // Try to get the current activity context, fallback to application context
            val context = getCurrentActivity() ?: this
            
            // Create exception details
            val exceptionDetails = buildExceptionDetails(exception)
            
            // Create a scrollable text view for the exception details
            val textView = TextView(context).apply {
                text = exceptionDetails
                setPadding(32, 32, 32, 32)
                textSize = 11f
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            
            val scrollView = ScrollView(context).apply {
                addView(textView)
            }
            
            // Create and show the dialog
            val dialog = AlertDialog.Builder(context)
                .setTitle("🐛 Debug: Uncaught Exception")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setView(scrollView)
                .setPositiveButton("Dismiss") { dialog, _ -> 
                    dialog.dismiss()
                }
                .setNeutralButton("Copy to Log") { _, _ ->
                    Log.e(TAG, "Exception details copied to log:\n$exceptionDetails")
                }
                .setCancelable(true)
                .create()
            
            dialog.show()
            
            // Make the dialog larger for better readability
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                (resources.displayMetrics.heightPixels * 0.8).toInt()
            )
                
        } catch (e: Exception) {
            // Fallback: just log if we can't show the dialog
            Log.e(TAG, "Failed to show exception dialog", e)
            Log.e(TAG, "Original exception details:", exception)
        }
    }
    
    private fun buildExceptionDetails(exception: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        
        // Build detailed exception information
        val details = StringBuilder()
        details.append("🚨 UNCAUGHT EXCEPTION DETAILS 🚨\n\n")
        details.append("Exception Type: ${exception.javaClass.simpleName}\n")
        details.append("Message: ${exception.message ?: "No message"}\n")
        details.append("Thread: ${Thread.currentThread().name}\n")
        details.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
        details.append("Stack Trace:\n")
        details.append("─".repeat(50))
        details.append("\n")
        
        exception.printStackTrace(printWriter)
        details.append(stringWriter.toString())
        
        // Add cause information if available
        var cause = exception.cause
        var level = 1
        while (cause != null && level <= 3) { // Limit to 3 levels to avoid infinite loops
            details.append("\n")
            details.append("─".repeat(50))
            details.append("\nCaused by (level $level): ${cause.javaClass.simpleName}")
            details.append("\nMessage: ${cause.message ?: "No message"}\n")
            
            val causeWriter = StringWriter()
            val causePrintWriter = PrintWriter(causeWriter)
            cause.printStackTrace(causePrintWriter)
            details.append(causeWriter.toString())
            
            cause = cause.cause
            level++
        }
        
        details.append("\n")
        details.append("─".repeat(50))
        details.append("\n💡 This dialog only appears in DEBUG builds")
        details.append("\n📋 Use 'Copy to Log' to save details to Logcat")
        
        return details.toString()
    }
}