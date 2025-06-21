package com.whatis

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.content.Intent
import android.util.Log
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
    private lateinit var askButton: Button
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = FrameLayout(this)
        imageView = ImageView(this)
        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.GONE

        askButton = Button(this).apply {
            text = "Ask again"
            setOnClickListener { startVoiceRecognition() }
        }

        errorText = TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.RED)
            gravity = Gravity.CENTER
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
            bottomMargin = 50
        })
        layout.addView(errorText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = 50
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
        errorText.text = ""
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
                val thumbnail = json.optJSONObject("thumbnail")
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
                Triple(bitmap, extract, null)
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