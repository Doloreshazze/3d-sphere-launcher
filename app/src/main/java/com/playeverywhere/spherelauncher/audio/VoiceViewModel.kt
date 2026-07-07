package com.playeverywhere.spherelauncher.audio

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    val voiceMatcher = VoiceMatcher()
    
    // We will initialize SpeechListener in the composables to tie it to the lifecycle better,
    // or we can host it here. Let's host it here so it survives config changes.
    private var speechListener: SpeechListener? = null

    val speechState: StateFlow<SpeechState>
        get() = speechListener?.speechState ?: MutableStateFlow(SpeechState.IDLE)

    val rmsValues: StateFlow<Float>
        get() = speechListener?.rmsValues ?: MutableStateFlow(0f)

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _lastEnrolledPrint = MutableStateFlow<VoiceMatcher.VoicePrint?>(null)
    val lastEnrolledPrint: StateFlow<VoiceMatcher.VoicePrint?> = _lastEnrolledPrint.asStateFlow()

    fun initializeListener(context: Context) {
        if (speechListener == null) {
            speechListener = SpeechListener(context.applicationContext)
            
            speechListener?.onPartialResult = { text ->
                _recognizedText.value = text
            }
        }
    }

    fun startListeningForEnrollment(wakeWord: String, appWord: String, packageName: String) {
        _recognizedText.value = "Listening..."
        speechListener?.onResult = { text, duration, envelope ->
            _recognizedText.value = text
            val print = VoiceMatcher.VoicePrint(wakeWord, appWord, packageName, duration, envelope)
            voiceMatcher.enrollVoicePrint(print)
            _lastEnrolledPrint.value = print
        }
        speechListener?.startListening()
    }

    fun startListeningForLaunch(onLaunch: (String) -> Unit) {
        _recognizedText.value = "Listening for launch..."
        speechListener?.onResult = { text, duration, envelope ->
            _recognizedText.value = text
            val matchedPrint = voiceMatcher.match(text, duration, envelope)
            if (matchedPrint != null) {
                onLaunch(matchedPrint.packageName)
            }
        }
        speechListener?.startListening()
    }

    fun stopListening() {
        speechListener?.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        speechListener?.destroy()
    }
}
