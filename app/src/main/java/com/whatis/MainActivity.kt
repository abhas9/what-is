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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = FrameLayout(this)
        imageView = ImageView(this)
        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        askButton = Button(this).apply {
            text = "Ask again"
            textSize = 28f
            setBackgroundResource(R.drawable.ask_button_selector)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.button_text))
            elevation = 8f
            stateListAnimator = null // Remove default state animator to use custom elevation
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, ContextCompat.getColor(this@MainActivity, R.color.button_text_shadow))
            isAllCaps = false // More friendly appearance
            letterSpacing = 0.05f // Slight letter spacing for readability
            minHeight = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) // Ensure accessible touch target
            contentDescription = "Ask again button - tap to record your voice and ask a question"
            setOnClickListener { 
                startVoiceRecognition()
            }
        }

        errorText = TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.RED)
            gravity = Gravity.CENTER
        }

        listeningIndicator = TextView(this).apply {
            text = "Listening..."
            textSize = 60f
            setTextColor(Color.parseColor("#4CAF50"))
            gravity = Gravity.CENTER
            visibility = TextView.GONE
        }

        createSuggestionsGrid()

        logoImageView = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "What Is app logo"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            visibility = ImageView.GONE // Initially hidden, shown with suggestions grid
        }

        layout.addView(imageView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        layout.addView(progressBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })
        layout.addView(askButton, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 80 // Increased margin for better spacing with gradient button
            leftMargin = 32
            rightMargin = 32
        })
        layout.addView(errorText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = 50
        })
        layout.addView(listeningIndicator, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = 100
        })
        layout.addView(logoImageView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = 180 // Increased from 150 to better center between listeningIndicator and suggestions
            leftMargin = 32
            rightMargin = 32
        })
        layout.addView(suggestionsGrid, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = 300 // Increased from 230 to provide more space below logo
            leftMargin = 32
            rightMargin = 32
            bottomMargin = 160 // Maintain space above the "Ask Again" button
        })

        setContentView(layout)

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
        suggestionsGrid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            
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
                addView(button)
            }
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

    override fun onDestroy() {
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
