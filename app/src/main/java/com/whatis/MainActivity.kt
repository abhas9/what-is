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
        "apple" to "Apple is a part of our world. We see it, use it, or play with it every day. Apple is something you might see or hear about. It can be fun to learn more about!",
        "banana" to "Banana is a part of our world. We see it, use it, or play with it every day. Banana is something fun and useful. It helps us do things or makes us happy.",
        "car" to "Car is something fun and useful. It helps us do things or makes us happy. Car is something you might see or hear about. It can be fun to learn more about!",
        "dog" to "Dog is a part of our world. We see it, use it, or play with it every day. Dog is something you might see or hear about. It can be fun to learn more about!",
        "cat" to "Cat is a part of our world. We see it, use it, or play with it every day. Cat is something fun and useful. It helps us do things or makes us happy.",
        "elephant" to "Elephant is a part of our world. We see it, use it, or play with it every day. Elephant is something fun and useful. It helps us do things or makes us happy.",
        "ball" to "Ball is a part of our world. We see it, use it, or play with it every day. Ball is something fun and useful. It helps us do things or makes us happy.",
        "train" to "Train is something you might see or hear about. It can be fun to learn more about! Train is a part of our world. We see it, use it, or play with it every day.",
        "duck" to "Duck is a part of our world. We see it, use it, or play with it every day. Duck is something fun and useful. It helps us do things or makes us happy.",
        "fish" to "Fish is a part of our world. We see it, use it, or play with it every day. Fish is something you might see or hear about. It can be fun to learn more about!",
        "milk" to "Milk is a part of our world. We see it, use it, or play with it every day. Milk is something fun and useful. It helps us do things or makes us happy.",
        "water" to "Water is something you might see or hear about. It can be fun to learn more about! Water is something fun and useful. It helps us do things or makes us happy.",
        "chair" to "Chair is something you might see or hear about. It can be fun to learn more about! Chair is something fun and useful. It helps us do things or makes us happy.",
        "table" to "Table is something fun and useful. It helps us do things or makes us happy. Table is something you might see or hear about. It can be fun to learn more about!",
        "bed" to "Bed is a part of our world. We see it, use it, or play with it every day. Bed is something you might see or hear about. It can be fun to learn more about!",
        "shoe" to "Shoe is something fun and useful. It helps us do things or makes us happy. Shoe is a part of our world. We see it, use it, or play with it every day.",
        "sock" to "Sock is something fun and useful. It helps us do things or makes us happy. Sock is something you might see or hear about. It can be fun to learn more about!",
        "hat" to "Hat is something fun and useful. It helps us do things or makes us happy. Hat is a part of our world. We see it, use it, or play with it every day.",
        "book" to "Book is a part of our world. We see it, use it, or play with it every day. Book is something you might see or hear about. It can be fun to learn more about!",
        "tree" to "Tree is something you might see or hear about. It can be fun to learn more about! Tree is a part of our world. We see it, use it, or play with it every day.",
        "sun" to "Sun is something you might see or hear about. It can be fun to learn more about! Sun is something fun and useful. It helps us do things or makes us happy.",
        "moon" to "Moon is a part of our world. We see it, use it, or play with it every day. Moon is something you might see or hear about. It can be fun to learn more about!",
        "star" to "Star is a part of our world. We see it, use it, or play with it every day. Star is something fun and useful. It helps us do things or makes us happy.",
        "cloud" to "Cloud is something fun and useful. It helps us do things or makes us happy. Cloud is a part of our world. We see it, use it, or play with it every day.",
        "rain" to "Rain is a part of our world. We see it, use it, or play with it every day. Rain is something fun and useful. It helps us do things or makes us happy.",
        "snow" to "Snow is something fun and useful. It helps us do things or makes us happy. Snow is something you might see or hear about. It can be fun to learn more about!",
        "flower" to "Flower is something you might see or hear about. It can be fun to learn more about! Flower is something fun and useful. It helps us do things or makes us happy.",
        "bee" to "Bee is something you might see or hear about. It can be fun to learn more about! Bee is something fun and useful. It helps us do things or makes us happy.",
        "butterfly" to "Butterfly is something you might see or hear about. It can be fun to learn more about! Butterfly is a part of our world. We see it, use it, or play with it every day.",
        "frog" to "Frog is something you might see or hear about. It can be fun to learn more about! Frog is a part of our world. We see it, use it, or play with it every day.",
        "cow" to "Cow is something fun and useful. It helps us do things or makes us happy. Cow is something you might see or hear about. It can be fun to learn more about!",
        "sheep" to "Sheep is a part of our world. We see it, use it, or play with it every day. Sheep is something you might see or hear about. It can be fun to learn more about!",
        "goat" to "Goat is a part of our world. We see it, use it, or play with it every day. Goat is something you might see or hear about. It can be fun to learn more about!",
        "pig" to "Pig is something you might see or hear about. It can be fun to learn more about! Pig is something fun and useful. It helps us do things or makes us happy.",
        "chicken" to "Chicken is something fun and useful. It helps us do things or makes us happy. Chicken is something you might see or hear about. It can be fun to learn more about!",
        "horse" to "Horse is something fun and useful. It helps us do things or makes us happy. Horse is a part of our world. We see it, use it, or play with it every day.",
        "zebra" to "Zebra is a part of our world. We see it, use it, or play with it every day. Zebra is something fun and useful. It helps us do things or makes us happy.",
        "lion" to "Lion is something fun and useful. It helps us do things or makes us happy. Lion is something you might see or hear about. It can be fun to learn more about!",
        "tiger" to "Tiger is something fun and useful. It helps us do things or makes us happy. Tiger is a part of our world. We see it, use it, or play with it every day.",
        "bear" to "Bear is something fun and useful. It helps us do things or makes us happy. Bear is something you might see or hear about. It can be fun to learn more about!",
        "monkey" to "Monkey is something you might see or hear about. It can be fun to learn more about! Monkey is a part of our world. We see it, use it, or play with it every day.",
        "giraffe" to "Giraffe is something fun and useful. It helps us do things or makes us happy. Giraffe is something you might see or hear about. It can be fun to learn more about!",
        "kangaroo" to "Kangaroo is a part of our world. We see it, use it, or play with it every day. Kangaroo is something fun and useful. It helps us do things or makes us happy.",
        "koala" to "Koala is something fun and useful. It helps us do things or makes us happy. Koala is something you might see or hear about. It can be fun to learn more about!",
        "panda" to "Panda is something fun and useful. It helps us do things or makes us happy. Panda is a part of our world. We see it, use it, or play with it every day.",
        "apple juice" to "Apple juice is a part of our world. We see it, use it, or play with it every day. Apple juice is something you might see or hear about. It can be fun to learn more about!",
        "cookie" to "Cookie is something you might see or hear about. It can be fun to learn more about! Cookie is something fun and useful. It helps us do things or makes us happy.",
        "cake" to "Cake is something fun and useful. It helps us do things or makes us happy. Cake is something you might see or hear about. It can be fun to learn more about!",
        "bread" to "Bread is a part of our world. We see it, use it, or play with it every day. Bread is something you might see or hear about. It can be fun to learn more about!",
        "cheese" to "Cheese is something fun and useful. It helps us do things or makes us happy. Cheese is a part of our world. We see it, use it, or play with it every day.",
        "egg" to "Egg is something you might see or hear about. It can be fun to learn more about! Egg is something fun and useful. It helps us do things or makes us happy.",
        "yogurt" to "Yogurt is something you might see or hear about. It can be fun to learn more about! Yogurt is something fun and useful. It helps us do things or makes us happy.",
        "cereal" to "Cereal is something fun and useful. It helps us do things or makes us happy. Cereal is something you might see or hear about. It can be fun to learn more about!",
        "orange" to "Orange is something you might see or hear about. It can be fun to learn more about! Orange is something fun and useful. It helps us do things or makes us happy.",
        "grape" to "Grape is a part of our world. We see it, use it, or play with it every day. Grape is something fun and useful. It helps us do things or makes us happy.",
        "watermelon" to "Watermelon is something you might see or hear about. It can be fun to learn more about! Watermelon is a part of our world. We see it, use it, or play with it every day.",
        "strawberry" to "Strawberry is something fun and useful. It helps us do things or makes us happy. Strawberry is something you might see or hear about. It can be fun to learn more about!",
        "blueberry" to "Blueberry is a part of our world. We see it, use it, or play with it every day. Blueberry is something fun and useful. It helps us do things or makes us happy.",
        "peach" to "Peach is something fun and useful. It helps us do things or makes us happy. Peach is something you might see or hear about. It can be fun to learn more about!",
        "plum" to "Plum is something fun and useful. It helps us do things or makes us happy. Plum is something you might see or hear about. It can be fun to learn more about!",
        "carrot" to "Carrot is something you might see or hear about. It can be fun to learn more about! Carrot is something fun and useful. It helps us do things or makes us happy.",
        "broccoli" to "Broccoli is something you might see or hear about. It can be fun to learn more about! Broccoli is a part of our world. We see it, use it, or play with it every day.",
        "corn" to "Corn is a part of our world. We see it, use it, or play with it every day. Corn is something you might see or hear about. It can be fun to learn more about!",
        "potato" to "Potato is something you might see or hear about. It can be fun to learn more about! Potato is a part of our world. We see it, use it, or play with it every day.",
        "tomato" to "Tomato is a part of our world. We see it, use it, or play with it every day. Tomato is something you might see or hear about. It can be fun to learn more about!",
        "onion" to "Onion is a part of our world. We see it, use it, or play with it every day. Onion is something fun and useful. It helps us do things or makes us happy.",
        "lettuce" to "Lettuce is something fun and useful. It helps us do things or makes us happy. Lettuce is something you might see or hear about. It can be fun to learn more about!",
        "cucumber" to "Cucumber is something you might see or hear about. It can be fun to learn more about! Cucumber is something fun and useful. It helps us do things or makes us happy.",
        "pepper" to "Pepper is something you might see or hear about. It can be fun to learn more about! Pepper is a part of our world. We see it, use it, or play with it every day.",
        "pumpkin" to "Pumpkin is a part of our world. We see it, use it, or play with it every day. Pumpkin is something fun and useful. It helps us do things or makes us happy.",
        "crayon" to "Crayon is a part of our world. We see it, use it, or play with it every day. Crayon is something fun and useful. It helps us do things or makes us happy.",
        "pencil" to "Pencil is something fun and useful. It helps us do things or makes us happy. Pencil is a part of our world. We see it, use it, or play with it every day.",
        "pen" to "Pen is something fun and useful. It helps us do things or makes us happy. Pen is something you might see or hear about. It can be fun to learn more about!",
        "paper" to "Paper is a part of our world. We see it, use it, or play with it every day. Paper is something fun and useful. It helps us do things or makes us happy.",
        "scissors" to "Scissors is something you might see or hear about. It can be fun to learn more about! Scissors is something fun and useful. It helps us do things or makes us happy.",
        "glue" to "Glue is something fun and useful. It helps us do things or makes us happy. Glue is a part of our world. We see it, use it, or play with it every day.",
        "paint" to "Paint is something you might see or hear about. It can be fun to learn more about! Paint is a part of our world. We see it, use it, or play with it every day.",
        "brush" to "Brush is something fun and useful. It helps us do things or makes us happy. Brush is something you might see or hear about. It can be fun to learn more about!",
        "color" to "Color is something fun and useful. It helps us do things or makes us happy. Color is a part of our world. We see it, use it, or play with it every day.",
        "drawing" to "Drawing is a part of our world. We see it, use it, or play with it every day. Drawing is something you might see or hear about. It can be fun to learn more about!",
        "phone" to "Phone is something you might see or hear about. It can be fun to learn more about! Phone is a part of our world. We see it, use it, or play with it every day.",
        "television" to "Television is something fun and useful. It helps us do things or makes us happy. Television is something you might see or hear about. It can be fun to learn more about!",
        "computer" to "Computer is something fun and useful. It helps us do things or makes us happy. Computer is a part of our world. We see it, use it, or play with it every day.",
        "laptop" to "Laptop is something fun and useful. It helps us do things or makes us happy. Laptop is a part of our world. We see it, use it, or play with it every day.",
        "tablet" to "Tablet is a part of our world. We see it, use it, or play with it every day. Tablet is something fun and useful. It helps us do things or makes us happy.",
        "remote" to "Remote is a part of our world. We see it, use it, or play with it every day. Remote is something you might see or hear about. It can be fun to learn more about!",
        "camera" to "Camera is something fun and useful. It helps us do things or makes us happy. Camera is something you might see or hear about. It can be fun to learn more about!",
        "clock" to "Clock is something fun and useful. It helps us do things or makes us happy. Clock is something you might see or hear about. It can be fun to learn more about!",
        "lamp" to "Lamp is something you might see or hear about. It can be fun to learn more about! Lamp is something fun and useful. It helps us do things or makes us happy.",
        "fan" to "Fan is something you might see or hear about. It can be fun to learn more about! Fan is something fun and useful. It helps us do things or makes us happy.",
        "door" to "Door is something you might see or hear about. It can be fun to learn more about! Door is a part of our world. We see it, use it, or play with it every day.",
        "window" to "Window is something fun and useful. It helps us do things or makes us happy. Window is something you might see or hear about. It can be fun to learn more about!",
        "wall" to "Wall is something you might see or hear about. It can be fun to learn more about! Wall is something fun and useful. It helps us do things or makes us happy.",
        "floor" to "Floor is something fun and useful. It helps us do things or makes us happy. Floor is a part of our world. We see it, use it, or play with it every day.",
        "ceiling" to "Ceiling is something you might see or hear about. It can be fun to learn more about! Ceiling is a part of our world. We see it, use it, or play with it every day.",
        "roof" to "Roof is something fun and useful. It helps us do things or makes us happy. Roof is something you might see or hear about. It can be fun to learn more about!",
        "stairs" to "Stairs is something fun and useful. It helps us do things or makes us happy. Stairs is something you might see or hear about. It can be fun to learn more about!",
        "elevator" to "Elevator is a part of our world. We see it, use it, or play with it every day. Elevator is something you might see or hear about. It can be fun to learn more about!",
        "mirror" to "Mirror is something you might see or hear about. It can be fun to learn more about! Mirror is something fun and useful. It helps us do things or makes us happy.",
        "bathtub" to "Bathtub is a part of our world. We see it, use it, or play with it every day. Bathtub is something you might see or hear about. It can be fun to learn more about!",
        "toothbrush" to "Toothbrush is something fun and useful. It helps us do things or makes us happy. Toothbrush is a part of our world. We see it, use it, or play with it every day.",
        "toothpaste" to "Toothpaste is something fun and useful. It helps us do things or makes us happy. Toothpaste is a part of our world. We see it, use it, or play with it every day.",
        "soap" to "Soap is something you might see or hear about. It can be fun to learn more about! Soap is something fun and useful. It helps us do things or makes us happy.",
        "shampoo" to "Shampoo is a part of our world. We see it, use it, or play with it every day. Shampoo is something fun and useful. It helps us do things or makes us happy.",
        "towel" to "Towel is something fun and useful. It helps us do things or makes us happy. Towel is something you might see or hear about. It can be fun to learn more about!",
        "comb" to "Comb is something fun and useful. It helps us do things or makes us happy. Comb is a part of our world. We see it, use it, or play with it every day.",
        "diaper" to "Diaper is a part of our world. We see it, use it, or play with it every day. Diaper is something you might see or hear about. It can be fun to learn more about!",
        "bib" to "Bib is something fun and useful. It helps us do things or makes us happy. Bib is something you might see or hear about. It can be fun to learn more about!",
        "bottle" to "Bottle is something you might see or hear about. It can be fun to learn more about! Bottle is something fun and useful. It helps us do things or makes us happy.",
        "blanket" to "Blanket is something fun and useful. It helps us do things or makes us happy. Blanket is a part of our world. We see it, use it, or play with it every day.",
        "pillow" to "Pillow is something you might see or hear about. It can be fun to learn more about! Pillow is something fun and useful. It helps us do things or makes us happy.",
        "teddy bear" to "Teddy bear is something fun and useful. It helps us do things or makes us happy. Teddy bear is a part of our world. We see it, use it, or play with it every day.",
        "doll" to "Doll is a part of our world. We see it, use it, or play with it every day. Doll is something you might see or hear about. It can be fun to learn more about!",
        "robot" to "Robot is something fun and useful. It helps us do things or makes us happy. Robot is something you might see or hear about. It can be fun to learn more about!",
        "truck" to "Truck is a part of our world. We see it, use it, or play with it every day. Truck is something you might see or hear about. It can be fun to learn more about!",
        "bus" to "Bus is something fun and useful. It helps us do things or makes us happy. Bus is a part of our world. We see it, use it, or play with it every day.",
        "bike" to "Bike is something you might see or hear about. It can be fun to learn more about! Bike is something fun and useful. It helps us do things or makes us happy.",
        "scooter" to "Scooter is a part of our world. We see it, use it, or play with it every day. Scooter is something fun and useful. It helps us do things or makes us happy.",
        "airplane" to "Airplane is a part of our world. We see it, use it, or play with it every day. Airplane is something fun and useful. It helps us do things or makes us happy.",
        "helicopter" to "Helicopter is a part of our world. We see it, use it, or play with it every day. Helicopter is something fun and useful. It helps us do things or makes us happy.",
        "boat" to "Boat is something you might see or hear about. It can be fun to learn more about! Boat is a part of our world. We see it, use it, or play with it every day.",
        "ship" to "Ship is something you might see or hear about. It can be fun to learn more about! Ship is a part of our world. We see it, use it, or play with it every day.",
        "submarine" to "Submarine is something you might see or hear about. It can be fun to learn more about! Submarine is something fun and useful. It helps us do things or makes us happy.",
        "train track" to "Train track is a part of our world. We see it, use it, or play with it every day. Train track is something you might see or hear about. It can be fun to learn more about!",
        "railway" to "Railway is a part of our world. We see it, use it, or play with it every day. Railway is something fun and useful. It helps us do things or makes us happy.",
        "bridge" to "Bridge is a part of our world. We see it, use it, or play with it every day. Bridge is something you might see or hear about. It can be fun to learn more about!",
        "tunnel" to "Tunnel is something you might see or hear about. It can be fun to learn more about! Tunnel is something fun and useful. It helps us do things or makes us happy.",
        "road" to "Road is a part of our world. We see it, use it, or play with it every day. Road is something you might see or hear about. It can be fun to learn more about!",
        "traffic light" to "Traffic light is something fun and useful. It helps us do things or makes us happy. Traffic light is a part of our world. We see it, use it, or play with it every day.",
        "ambulance" to "Ambulance is something you might see or hear about. It can be fun to learn more about! Ambulance is a part of our world. We see it, use it, or play with it every day.",
        "fire truck" to "Fire truck is something you might see or hear about. It can be fun to learn more about! Fire truck is something fun and useful. It helps us do things or makes us happy.",
        "police car" to "Police car is a part of our world. We see it, use it, or play with it every day. Police car is something you might see or hear about. It can be fun to learn more about!",
        "doctor" to "Doctor is something you might see or hear about. It can be fun to learn more about! Doctor is something fun and useful. It helps us do things or makes us happy.",
        "nurse" to "Nurse is something you might see or hear about. It can be fun to learn more about! Nurse is something fun and useful. It helps us do things or makes us happy.",
        "teacher" to "Teacher is something you might see or hear about. It can be fun to learn more about! Teacher is a part of our world. We see it, use it, or play with it every day.",
        "friend" to "Friend is something fun and useful. It helps us do things or makes us happy. Friend is something you might see or hear about. It can be fun to learn more about!",
        "mommy" to "Mommy is something fun and useful. It helps us do things or makes us happy. Mommy is a part of our world. We see it, use it, or play with it every day.",
        "daddy" to "Daddy is something you might see or hear about. It can be fun to learn more about! Daddy is a part of our world. We see it, use it, or play with it every day.",
        "baby" to "Baby is something fun and useful. It helps us do things or makes us happy. Baby is a part of our world. We see it, use it, or play with it every day.",
        "grandma" to "Grandma is something fun and useful. It helps us do things or makes us happy. Grandma is a part of our world. We see it, use it, or play with it every day.",
        "grandpa" to "Grandpa is a part of our world. We see it, use it, or play with it every day. Grandpa is something fun and useful. It helps us do things or makes us happy.",
        "uncle" to "Uncle is something you might see or hear about. It can be fun to learn more about! Uncle is something fun and useful. It helps us do things or makes us happy.",
        "aunt" to "Aunt is a part of our world. We see it, use it, or play with it every day. Aunt is something fun and useful. It helps us do things or makes us happy.",
        "cousin" to "Cousin is something you might see or hear about. It can be fun to learn more about! Cousin is a part of our world. We see it, use it, or play with it every day.",
        "school" to "School is a part of our world. We see it, use it, or play with it every day. School is something you might see or hear about. It can be fun to learn more about!",
        "classroom" to "Classroom is a part of our world. We see it, use it, or play with it every day. Classroom is something you might see or hear about. It can be fun to learn more about!",
        "playground" to "Playground is something fun and useful. It helps us do things or makes us happy. Playground is a part of our world. We see it, use it, or play with it every day.",
        "slide" to "Slide is something you might see or hear about. It can be fun to learn more about! Slide is something fun and useful. It helps us do things or makes us happy.",
        "swing" to "Swing is something fun and useful. It helps us do things or makes us happy. Swing is something you might see or hear about. It can be fun to learn more about!",
        "sandbox" to "Sandbox is something fun and useful. It helps us do things or makes us happy. Sandbox is something you might see or hear about. It can be fun to learn more about!",
        "toy" to "Toy is something fun and useful. It helps us do things or makes us happy. Toy is a part of our world. We see it, use it, or play with it every day.",
        "game" to "Game is a part of our world. We see it, use it, or play with it every day. Game is something you might see or hear about. It can be fun to learn more about!",
        "puzzle" to "Puzzle is something you might see or hear about. It can be fun to learn more about! Puzzle is a part of our world. We see it, use it, or play with it every day.",
        "block" to "Block is a part of our world. We see it, use it, or play with it every day. Block is something you might see or hear about. It can be fun to learn more about!",
        "lego" to "Lego is something fun and useful. It helps us do things or makes us happy. Lego is a part of our world. We see it, use it, or play with it every day.",
        "music" to "Music is a part of our world. We see it, use it, or play with it every day. Music is something fun and useful. It helps us do things or makes us happy.",
        "song" to "Song is a part of our world. We see it, use it, or play with it every day. Song is something fun and useful. It helps us do things or makes us happy.",
        "drum" to "Drum is something you might see or hear about. It can be fun to learn more about! Drum is a part of our world. We see it, use it, or play with it every day.",
        "guitar" to "Guitar is a part of our world. We see it, use it, or play with it every day. Guitar is something fun and useful. It helps us do things or makes us happy.",
        "piano" to "Piano is something you might see or hear about. It can be fun to learn more about! Piano is a part of our world. We see it, use it, or play with it every day.",
        "violin" to "Violin is a part of our world. We see it, use it, or play with it every day. Violin is something you might see or hear about. It can be fun to learn more about!",
        "trumpet" to "Trumpet is something fun and useful. It helps us do things or makes us happy. Trumpet is a part of our world. We see it, use it, or play with it every day.",
        "bell" to "Bell is a part of our world. We see it, use it, or play with it every day. Bell is something you might see or hear about. It can be fun to learn more about!",
        "whistle" to "Whistle is something you might see or hear about. It can be fun to learn more about! Whistle is something fun and useful. It helps us do things or makes us happy.",
        "horn" to "Horn is something you might see or hear about. It can be fun to learn more about! Horn is something fun and useful. It helps us do things or makes us happy.",
        "animal" to "Animal is a part of our world. We see it, use it, or play with it every day. Animal is something you might see or hear about. It can be fun to learn more about!",
        "bird" to "Bird is something you might see or hear about. It can be fun to learn more about! Bird is something fun and useful. It helps us do things or makes us happy.",
        "nest" to "Nest is something you might see or hear about. It can be fun to learn more about! Nest is something fun and useful. It helps us do things or makes us happy.",
        "ant" to "Ant is something you might see or hear about. It can be fun to learn more about! Ant is a part of our world. We see it, use it, or play with it every day.",
        "spider" to "Spider is something you might see or hear about. It can be fun to learn more about! Spider is something fun and useful. It helps us do things or makes us happy.",
        "worm" to "Worm is a part of our world. We see it, use it, or play with it every day. Worm is something fun and useful. It helps us do things or makes us happy.",
        "snail" to "Snail is something you might see or hear about. It can be fun to learn more about! Snail is something fun and useful. It helps us do things or makes us happy.",
        "ladybug" to "Ladybug is a part of our world. We see it, use it, or play with it every day. Ladybug is something fun and useful. It helps us do things or makes us happy.",
        "mouse" to "Mouse is a part of our world. We see it, use it, or play with it every day. Mouse is something fun and useful. It helps us do things or makes us happy.",
        "rat" to "Rat is a part of our world. We see it, use it, or play with it every day. Rat is something fun and useful. It helps us do things or makes us happy.",
        "squirrel" to "Squirrel is something you might see or hear about. It can be fun to learn more about! Squirrel is a part of our world. We see it, use it, or play with it every day.",
        "rabbit" to "Rabbit is something fun and useful. It helps us do things or makes us happy. Rabbit is a part of our world. We see it, use it, or play with it every day.",
        "deer" to "Deer is a part of our world. We see it, use it, or play with it every day. Deer is something fun and useful. It helps us do things or makes us happy.",
        "fox" to "Fox is a part of our world. We see it, use it, or play with it every day. Fox is something you might see or hear about. It can be fun to learn more about!",
        "wolf" to "Wolf is something fun and useful. It helps us do things or makes us happy. Wolf is a part of our world. We see it, use it, or play with it every day.",
        "bat" to "Bat is something fun and useful. It helps us do things or makes us happy. Bat is something you might see or hear about. It can be fun to learn more about!",
        "owl" to "Owl is a part of our world. We see it, use it, or play with it every day. Owl is something fun and useful. It helps us do things or makes us happy.",
        "eagle" to "Eagle is something fun and useful. It helps us do things or makes us happy. Eagle is something you might see or hear about. It can be fun to learn more about!",
        "shark" to "Shark is something fun and useful. It helps us do things or makes us happy. Shark is something you might see or hear about. It can be fun to learn more about!",
        "whale" to "Whale is a part of our world. We see it, use it, or play with it every day. Whale is something fun and useful. It helps us do things or makes us happy.",
        "dolphin" to "Dolphin is something fun and useful. It helps us do things or makes us happy. Dolphin is a part of our world. We see it, use it, or play with it every day.",
        "octopus" to "Octopus is something fun and useful. It helps us do things or makes us happy. Octopus is something you might see or hear about. It can be fun to learn more about!",
        "starfish" to "Starfish is something fun and useful. It helps us do things or makes us happy. Starfish is a part of our world. We see it, use it, or play with it every day."
    )
}