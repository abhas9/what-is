package com.whatis
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : Activity() {
    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
        private const val TAG = "WhatIsApp"
    }

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = FrameLayout(this)
        imageView = ImageView(this)
        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        layout.addView(imageView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        layout.addView(progressBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        })

        setContentView(layout)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                startVoiceRecognition()
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.let { handleQuestion(it.lowercase(Locale.ROOT)) }
        }
    }

    private fun handleQuestion(question: String) {
        val keyword = extractKeyword(question)
        val localAnswer = LocalAnswerStore.answers[keyword]
        if (localAnswer != null) {
            fetchImageFromWikipedia(keyword)
            speakAnswer(localAnswer)
        } else {
            fetchFromWikipedia(keyword)
        }
    }

    private fun extractKeyword(question: String): String {
        val parts = question.split(" ")
        return parts.last()
    }

    private fun speakAnswer(answer: String) {
        tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun fetchImageFromWikipedia(keyword: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val urlStr = "https://en.wikipedia.org/api/rest_v1/page/summary/$keyword"
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connect()

                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val thumbnail = json.optJSONObject("thumbnail")
                    thumbnail?.getString("source")?.let {
                        val inputStream = URL(it).openStream()
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Image fetch failed", e)
                    null
                }
            }
            imageView.setImageBitmap(bitmap ?: getDefaultBitmap())
            progressBar.visibility = ProgressBar.GONE
        }
    }

    private fun fetchFromWikipedia(keyword: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        lifecycleScope.launch {
            var imageBitmap: Bitmap? = null
            val explanation = withContext(Dispatchers.IO) {
                try {
                    val urlStr = "https://en.wikipedia.org/api/rest_v1/page/summary/$keyword"
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connect()

                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val extract = json.optString("extract")

                    json.optJSONObject("thumbnail")?.getString("source")?.let {
                        val inputStream = URL(it).openStream()
                        imageBitmap = BitmapFactory.decodeStream(inputStream)
                    }

                    simplifyTextForToddlers(extract)
                } catch (e: Exception) {
                    Log.e(TAG, "Wikipedia fetch failed", e)
                    null
                }
            }

            imageView.setImageBitmap(imageBitmap ?: getDefaultBitmap())
            speakAnswer(explanation ?: "Hmm, I don't know that yet. Let's ask again later!")
            progressBar.visibility = ProgressBar.GONE
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
        super.onDestroy()
    }
}

object LocalAnswerStore {
    val answers = mapOf(
        "apple" to "An apple is a yummy red fruit.",
        "dog" to "A dog is a furry friend that barks.",
        "cat" to "A cat is a soft animal that says meow.",
        "elephant" to "An elephant is a big animal with a long nose."
    )
} 