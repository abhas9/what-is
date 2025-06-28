package com.whatis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : Activity() {
    companion object {
        private const val TAG = "WhatIsApp"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1
    }

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var askButton: Button
    private lateinit var errorText: TextView
    private lateinit var listeningIndicator: TextView
    private lateinit var suggestionsGrid: GridLayout
    private lateinit var logoImageView: ImageView
    
    // AutoPlay components
    private lateinit var autoPlayButton: Button
    private lateinit var autoPlayStatusText: TextView
    private val autoPlayHandler = Handler(Looper.getMainLooper())
    private var isAutoPlayActive = false
    private val autoPlayUsedItems = mutableSetOf<String>()
    private var autoPlayRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components from XML layout
        imageView = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        askButton = findViewById(R.id.askButton)
        errorText = findViewById(R.id.errorText)
        listeningIndicator = findViewById(R.id.listeningIndicator)
        logoImageView = findViewById(R.id.logoImageView)
        autoPlayButton = findViewById(R.id.autoPlayButton)
        autoPlayStatusText = findViewById(R.id.autoPlayStatusText)
        suggestionsGrid = findViewById(R.id.suggestionsGrid)

        // Set up button click listeners
        askButton.setOnClickListener { 
            startVoiceRecognition()
        }
        
        autoPlayButton.setOnClickListener { 
            toggleAutoPlay()
        }

        // Create suggestion buttons programmatically
        createSuggestionsGrid()

        // Set up dynamic width constraint for logo (60% of available width)
        val rootLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootLayout)
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Calculate 60% of the root layout width minus margins
                val layoutWidth = rootLayout.width
                val density = resources.displayMetrics.density
                val leftMarginPx = (32 * density).toInt()
                val rightMarginPx = (32 * density).toInt()
                val availableWidth = layoutWidth - leftMarginPx - rightMarginPx
                val maxLogoWidth = (availableWidth * 0.6).toInt()
                
                // Apply the maximum width constraint to the logo
                logoImageView.maxWidth = maxLogoWidth
            }
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            setupSpeechAndTTS()
        }
    }

    private fun createSuggestionsGrid() {
        // Clear any existing children
        suggestionsGrid.removeAllViews()
        
        // Popular items from LocalAnswerStore that children would find interesting
        val suggestions = listOf(
            "dog", "cat", "car", "airplane", "apple", "ball", 
            "bird", "flower", "fish", "train", "cookie", "book"
        )
        
        // Colorful drawable resources with corresponding text colors
        val colorVariants = listOf(
            Pair(R.drawable.suggestion_button_blue, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white)),
            Pair(R.drawable.suggestion_button_green, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white)),
            Pair(R.drawable.suggestion_button_yellow, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_dark)),
            Pair(R.drawable.suggestion_button_orange, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white)),
            Pair(R.drawable.suggestion_button_purple, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white)),
            Pair(R.drawable.suggestion_button_pink, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white)),
            Pair(R.drawable.suggestion_button_teal, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white)),
            Pair(R.drawable.suggestion_button_red, ContextCompat.getColor(this@MainActivity, R.color.suggestion_text_white))
        )
        
        // Content description string resource IDs
        val contentDescriptions = listOf(
            R.string.suggestion_button_dog, R.string.suggestion_button_cat, R.string.suggestion_button_car,
            R.string.suggestion_button_airplane, R.string.suggestion_button_apple, R.string.suggestion_button_ball,
            R.string.suggestion_button_bird, R.string.suggestion_button_flower, R.string.suggestion_button_fish,
            R.string.suggestion_button_train, R.string.suggestion_button_cookie, R.string.suggestion_button_book
        )
        
        // Shuffle color variants to ensure random assignment but avoid adjacent duplicates
        val shuffledColors = colorVariants.shuffled()
        
        suggestions.forEachIndexed { index, item ->
            // Use modulo to cycle through colors if we have more suggestions than colors
            val colorIndex = index % shuffledColors.size
            val (backgroundDrawable, textColor) = shuffledColors[colorIndex]
            
            val button = Button(this@MainActivity).apply {
                text = item
                textSize = 22f  // Increased from 16f for better child readability
                setBackgroundResource(backgroundDrawable)
                setTextColor(textColor)
                elevation = 6f  // Increased elevation for better depth
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAllCaps = false
                letterSpacing = 0.05f
                
                // Accessibility enhancements
                contentDescription = getString(contentDescriptions[index])
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                
                // Ensure minimum touch target size (48dp)
                val minTouchTarget = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
                minHeight = minTouchTarget
                minWidth = minTouchTarget
                
                // Set layout params for grid positioning with increased margins for better spacing
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    setMargins(12, 12, 12, 12)  // Increased margins from 8dp to 12dp
                }
                layoutParams = params
                
                setOnClickListener {
                    handleSuggestionClick(item)
                }
            }
            suggestionsGrid.addView(button)
        }
    }

    private fun handleSuggestionClick(item: String) {
        hideSuggestionsGrid()
        handleQuestion("what is $item")
    }

    private fun showSuggestionsGrid() {
        suggestionsGrid.visibility = GridLayout.VISIBLE
        logoImageView.visibility = ImageView.VISIBLE
    }

    private fun hideSuggestionsGrid() {
        suggestionsGrid.visibility = GridLayout.GONE
        logoImageView.visibility = ImageView.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            setupSpeechAndTTS()
        } else {
            Toast.makeText(this, "Microphone permission is required to use speech input", Toast.LENGTH_LONG).show()
            showSuggestionsGrid()
        }
    }

    private fun setupSpeechAndTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setPitch(1.3f)     // Higher pitch (1.0 is default)
                tts.setSpeechRate(0.8f) // Slower speed (1.0 is default)
                initSpeechRecognizer()
                showSuggestionsGrid()
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listeningIndicator.visibility = TextView.VISIBLE
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listeningIndicator.visibility = TextView.GONE
            }

            override fun onError(error: Int) {
                listeningIndicator.visibility = TextView.GONE
                errorText.text = "Speech recognition error: $error"
                showSuggestionsGrid()
            }

            override fun onResults(results: Bundle) {
                listeningIndicator.visibility = TextView.GONE
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let {
                    handleQuestion(it.lowercase(Locale.ROOT))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoiceRecognition() {
        errorText.text = ""
        hideSuggestionsGrid()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleQuestion(question: String) {
        hideSuggestionsGrid()
        val keyword = extractKeyword(question)
        val localAnswer = LocalAnswerStore.answers[keyword]
        if (localAnswer != null) {
            FetchImageTask(keyword, localAnswer).execute()
        } else {
            FetchFromWikipediaTask(keyword).execute()
        }
    }

    private fun extractKeyword(question: String): String {
        val parts = question.split(" ")
        return parts.last()
    }

    private fun speakAnswer(answer: String) {
        tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun toggleAutoPlay() {
        if (isAutoPlayActive) {
            stopAutoPlay()
        } else {
            startAutoPlay()
        }
    }

    private fun startAutoPlay() {
        isAutoPlayActive = true
        autoPlayButton.text = "Stop AutoPlay"
        autoPlayStatusText.text = "AutoPlay Active - Cycling through items..."
        autoPlayStatusText.visibility = TextView.VISIBLE
        
        // Disable other UI interactions
        askButton.isEnabled = false
        disableSuggestionButtons()
        
        hideSuggestionsGrid()
        playNextAutoPlayItem() // Start first item immediately
    }

    private fun stopAutoPlay() {
        isAutoPlayActive = false
        autoPlayButton.text = "Start AutoPlay"
        autoPlayStatusText.visibility = TextView.GONE
        
        // Re-enable UI interactions
        askButton.isEnabled = true
        enableSuggestionButtons()
        
        // Cancel scheduled autoplay
        autoPlayRunnable?.let { autoPlayHandler.removeCallbacks(it) }
        autoPlayRunnable = null
        
        showSuggestionsGrid()
    }

    private fun scheduleNextAutoPlayItem() {
        if (!isAutoPlayActive) return
        
        autoPlayRunnable = Runnable {
            if (isAutoPlayActive) {
                playNextAutoPlayItem()
            }
        }
        autoPlayHandler.postDelayed(autoPlayRunnable!!, 10000) // 10 second delay
    }

    private fun playNextAutoPlayItem() {
        if (!isAutoPlayActive) return
        
        val availableItems = LocalAnswerStore.answers.keys.toList()
        val unusedItems = availableItems.filter { !autoPlayUsedItems.contains(it) }
        
        if (unusedItems.isEmpty()) {
            // Reset memory when all items are exhausted
            autoPlayUsedItems.clear()
            playNextAutoPlayItem()
            return
        }
        
        // Pick random item from unused items
        val randomItem = unusedItems.random()
        autoPlayUsedItems.add(randomItem)
        
        // Update status text to show current item
        autoPlayStatusText.text = "AutoPlay: $randomItem (${autoPlayUsedItems.size}/${availableItems.size})"
        
        handleQuestion("what is $randomItem")
        
        // Schedule next item
        scheduleNextAutoPlayItem()
    }

    private fun disableSuggestionButtons() {
        for (i in 0 until suggestionsGrid.childCount) {
            val child = suggestionsGrid.getChildAt(i)
            if (child is Button) {
                child.isEnabled = false
                child.alpha = 0.5f
            }
        }
    }

    private fun enableSuggestionButtons() {
        for (i in 0 until suggestionsGrid.childCount) {
            val child = suggestionsGrid.getChildAt(i)
            if (child is Button) {
                child.isEnabled = true
                child.alpha = 1.0f
            }
        }
    }

    private inner class FetchImageTask(val keyword: String, val answer: String) : AsyncTask<Void, Void, Pair<Bitmap?, String?>>() {
        override fun onPreExecute() {
            progressBar.visibility = ProgressBar.VISIBLE
            errorText.text = ""
        }

        override fun doInBackground(vararg params: Void?): Pair<Bitmap?, String?> {
            return try {
                val urlStr = "https://en.wikipedia.org/api/rest_v1/page/summary/$keyword"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val thumbnail = json.optJSONObject("originalimage")
                val bitmap = thumbnail?.getString("source")?.let {
                    val inputStream = URL(it).openStream()
                    BitmapFactory.decodeStream(inputStream)
                }
                Pair(bitmap, null)
            } catch (e: Exception) {
                Log.e(TAG, "Image fetch failed", e)
                Pair(null, e.toString())
            }
        }

        override fun onPostExecute(result: Pair<Bitmap?, String?>) {
            imageView.setImageBitmap(result.first ?: getDefaultBitmap())
            speakAnswer(answer)
            progressBar.visibility = ProgressBar.GONE
            result.second?.let { errorText.text = it }
        }
    }

    private inner class FetchFromWikipediaTask(val keyword: String) : AsyncTask<Void, Void, Triple<Bitmap?, String?, String?>>() {
        override fun onPreExecute() {
            progressBar.visibility = ProgressBar.VISIBLE
            errorText.text = ""
        }

        override fun doInBackground(vararg params: Void?): Triple<Bitmap?, String?, String?> {
            return try {
                val urlStr = "https://en.wikipedia.org/api/rest_v1/page/summary/$keyword"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val extract = json.optString("extract")
                val explanation = simplifyTextForToddlers(extract)

                val bitmap = json.optJSONObject("thumbnail")?.getString("source")?.let {
                    val inputStream = URL(it).openStream()
                    BitmapFactory.decodeStream(inputStream)
                }
                Triple(bitmap, explanation, null)
            } catch (e: Exception) {
                Log.e(TAG, "Wikipedia fetch failed", e)
                Triple(null, null, e.toString())
            }
        }

        override fun onPostExecute(result: Triple<Bitmap?, String?, String?>) {
            imageView.setImageBitmap(result.first ?: getDefaultBitmap())
            speakAnswer(result.second ?: "Hmm, I don't know that yet. Let's ask again later!")
            progressBar.visibility = ProgressBar.GONE
            result.third?.let { errorText.text = it }
        }
    }

    private fun simplifyTextForToddlers(extract: String?): String? {
        if (extract.isNullOrEmpty()) return null
        val periodIndex = extract.indexOf(".")
        return if (periodIndex != -1) extract.substring(0, periodIndex + 1) else extract
    }

    private fun getDefaultBitmap(): Bitmap? {
        return BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_help)
    }

    override fun onPause() {
        super.onPause()
        if (isAutoPlayActive) {
            stopAutoPlay()
        }
    }

    override fun onDestroy() {
        if (isAutoPlayActive) {
            stopAutoPlay()
        }
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        super.onDestroy()
    }
}
