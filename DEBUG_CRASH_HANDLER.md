# Debug Crash Handler Implementation

This implementation adds a global uncaught exception handler to the Android app that displays detailed crash information in debug builds only.

## Files Modified/Created

### 1. WhatIsApplication.kt (NEW)
- Custom Application class extending `Application`
- Sets up `Thread.setDefaultUncaughtExceptionHandler()` only in debug builds
- Displays crash details in an `AlertDialog` with scrollable content
- Logs exception details to Logcat
- Tracks current activity context for proper dialog display
- Handles nested exception causes (up to 3 levels)

### 2. AndroidManifest.xml (MODIFIED)
- Added `android:name=".WhatIsApplication"` to `<application>` tag
- This makes the custom Application class active

### 3. MainActivity.kt (MODIFIED)
- Added `WhatIsApplication.setCurrentActivity(this)` in `onCreate()` and `onResume()`
- Added debug crash test functionality (long press on logo in debug builds)
- Added `testCrashHandler()` method for testing the exception handler

### 4. CrashHandlerTest.kt (NEW)
- Unit tests documenting the crash handler requirements and features
- Tests verify the implementation meets all specified requirements

## Features Implemented

✅ **Catches unhandled exceptions at runtime**
- Uses `Thread.setDefaultUncaughtExceptionHandler()`

✅ **Displays exception details on screen**
- Shows `AlertDialog` with scrollable `TextView`
- Includes exception type, message, stack trace, timestamp
- Monospace font for better readability

✅ **Only activates in debug builds**
- Checks `BuildConfig.DEBUG` before setting up handler
- No impact on release builds

✅ **Set up in Application class**
- `WhatIsApplication` extends `Application`
- Handler setup in `onCreate()`

✅ **Captures and displays stack trace**
- Full stack trace with `printStackTrace()`
- Nested exception causes (up to 3 levels)

✅ **Logs to Logcat**
- Logs exception details with tag `WhatIsApp_CrashHandler`
- "Copy to Log" button for additional logging

✅ **User-dismissible UI**
- Dialog can be dismissed with "Dismiss" button
- Doesn't block further debugging
- Dialog auto-sizes to 95% width, 80% height

✅ **Debug testing functionality**
- Long press on logo triggers test crash (debug only)
- Shows toast notification before crash
- Uses descriptive `RuntimeException`

## Usage

### Normal Operation
- In debug builds, any uncaught exception will show a detailed dialog
- Dialog includes exception type, message, full stack trace, and timestamp
- User can dismiss dialog or copy details to log
- Original crash handling still occurs after 3-second delay

### Testing
- In debug builds, long press on the app logo to trigger a test crash
- This verifies the crash handler is working correctly
- Only available in debug builds

## Technical Details

### Exception Dialog Content
```
🚨 UNCAUGHT EXCEPTION DETAILS 🚨

Exception Type: RuntimeException
Message: Test exception message
Thread: main
Time: 2024-01-15 10:30:45

Stack Trace:
──────────────────────────────────────────────────
[Full stack trace here]

──────────────────────────────────────────────────
Caused by (level 1): IllegalStateException
Message: Root cause
[Nested stack trace here]

──────────────────────────────────────────────────
💡 This dialog only appears in DEBUG builds
📋 Use 'Copy to Log' to save details to Logcat
```

### Context Management
- `WhatIsApplication` tracks current activity via `WeakReference`
- `MainActivity` registers itself in `onCreate()` and `onResume()`
- Falls back to application context if activity context unavailable

### Error Handling
- Exception handler has its own try-catch to prevent infinite loops
- Falls back to Logcat-only if dialog creation fails
- Preserves original exception handling chain

## Requirements Satisfied

All requirements from the problem statement are implemented:

1. ✅ Global uncaught exception handler in Application class
2. ✅ Catches unhandled exceptions at runtime 
3. ✅ Displays exception details in user-visible dialog
4. ✅ Shows exception type, message, and stack trace
5. ✅ Logs to Logcat for analysis
6. ✅ UI is readable and dismissible
7. ✅ Only activates in debug builds (BuildConfig.DEBUG)
8. ✅ Makes debugging crashes easier, especially after layout changes