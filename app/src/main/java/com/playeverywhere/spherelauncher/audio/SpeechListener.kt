package com.playeverywhere.spherelauncher.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeechState {
    IDLE, LISTENING, PROCESSING, ERROR, SUCCESS
}

class SpeechListener(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _rmsValues = MutableStateFlow<Float>(0f)
    val rmsValues: StateFlow<Float> = _rmsValues.asStateFlow()

    private var currentRmsEnvelope = mutableListOf<Float>()
    private var startTimeMs = 0L

    var onResult: ((text: String, durationMs: Long, rmsEnvelope: List<Float>) -> Unit)? = null
    var onPartialResult: ((text: String) -> Unit)? = null

    init {
        setupRecognizer()
    }

    private fun setupRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _speechState.value = SpeechState.LISTENING
                    currentRmsEnvelope.clear()
                    startTimeMs = System.currentTimeMillis()
                }

                override fun onBeginningOfSpeech() {
                    startTimeMs = System.currentTimeMillis()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val positiveRms = if (rmsdB < 0) 0f else rmsdB
                    _rmsValues.value = positiveRms
                    currentRmsEnvelope.add(positiveRms)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _speechState.value = SpeechState.PROCESSING
                }

                override fun onError(error: Int) {
                    Log.e("SpeechListener", "Error: $error")
                    _speechState.value = SpeechState.ERROR
                    _rmsValues.value = 0f
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val durationMs = System.currentTimeMillis() - startTimeMs
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        _speechState.value = SpeechState.SUCCESS
                        onResult?.invoke(text, durationMs, currentRmsEnvelope.toList())
                    } else {
                        _speechState.value = SpeechState.ERROR
                    }
                    _rmsValues.value = 0f
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onPartialResult?.invoke(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            setupRecognizer()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _speechState.value = SpeechState.LISTENING
        currentRmsEnvelope.clear()
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _speechState.value = SpeechState.ERROR
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _speechState.value = SpeechState.IDLE
        _rmsValues.value = 0f
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
