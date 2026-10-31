package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val MODEL = "private val MODEL = "gemini-flash-lite-latest"
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var keyBox: EditText
    private val prefs by lazy { getSharedPreferences("p", Context.MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }
        keyBox = EditText(this).apply {
            hint = "Gemini API key यहाँ paste करो"
            setText(prefs.getString("key", ""))
            setSingleLine()
        }
        val save = Button(this).apply {
            text = "Key सेव करो"
            setOnClickListener {
                prefs.edit().putString("key", keyBox.text.toString().trim()).apply()
                status.text = "Key सेव हो गई"
            }
        }
        status = TextView(this).apply { text = "तैयार"; textSize = 16f; setPadding(0, 30, 0, 30) }
        val mic = Button(this).apply {
            text = "🎤 बोलो"
            textSize = 24f
            setOnClickListener { listen() }
        }
        output = TextView(this).apply { textSize = 18f; setPadding(0, 30, 0, 0) }
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(keyBox); root.addView(save); root.addView(status)
        root.addView(mic); root.addView(scroll)
        setContentView(root)
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN")
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) listen()
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition नहीं मिला"; return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) { status.text = "सुन नहीं पाया (error $e), फिर कोशिश करो" }
            override fun onResults(r: Bundle?) {
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) ask(text) else status.text = "कुछ सुनाई नहीं दिया"
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        recognizer?.startListening(i)
        status.text = "सुन रहा हूँ..."
    }

    private fun ask(q: String) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) { status.text = "पहले API key सेव करो"; return }
        status.text = "सोच रहा हूँ..."
        output.text = "तुम: $q\n"
        thread {
            try {
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                        "तुम एक दोस्ताना वॉइस असिस्टेंट हो। यूज़र की भाषा (हिंदी/हिंग्लिश/English) में छोटा जवाब दो। कोई * # या markdown मत लगाओ।"))))
                    .put("contents", JSONArray().put(JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", q)))))
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val ok = c.responseCode == 200
                val resp = (if (ok) c.inputStream else c.errorStream).bufferedReader().readText()
                val reply = if (ok) JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                else "Error ${c.responseCode}: $resp"
                runOnUiThread {
                    output.append("\nAI: $reply")
                    status.text = "तैयार"
                    if (ok) tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "r")
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error: ${e.message}" }
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy(); tts.shutdown(); super.onDestroy()
    }
}
