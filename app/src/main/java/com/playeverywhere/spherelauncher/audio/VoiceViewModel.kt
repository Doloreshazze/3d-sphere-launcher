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
    private val store = VoicePrintStore(application)
    
    // We will initialize SpeechListener in the composables to tie it to the lifecycle better,
    // or we can host it here. Let's host it here so it survives config changes.
    private var speechListener: SpeechListener? = null

    init {
        // Load persisted prints on startup
        val savedPrints = store.loadPrints()
        savedPrints.forEach { voiceMatcher.enrollVoicePrint(it) }
    }

    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _rmsValues = MutableStateFlow(0f)
    val rmsValues: StateFlow<Float> = _rmsValues.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _lastEnrolledPrint = MutableStateFlow<VoiceMatcher.VoicePrint?>(null)
    val lastEnrolledPrint: StateFlow<VoiceMatcher.VoicePrint?> = _lastEnrolledPrint.asStateFlow()
    
    val allEnrolledPrints = MutableStateFlow<List<VoiceMatcher.VoicePrint>>(store.loadPrints())

    fun initializeListener(context: Context) {
        if (speechListener == null) {
            val listener = SpeechListener(context.applicationContext)
            speechListener = listener
            
            listener.onPartialResult = { text ->
                _recognizedText.value = text
            }

            viewModelScope.launch {
                listener.speechState.collect { state ->
                    _speechState.value = state
                }
            }

            viewModelScope.launch {
                listener.rmsValues.collect { rms ->
                    _rmsValues.value = rms
                }
            }
        }
    }

    fun startListeningForEnrollment(wakeWord: String, appWord: String, packageName: String) {
        _recognizedText.value = "Listening..."
        speechListener?.onResult = { text, duration, envelope ->
            _recognizedText.value = text
            val print = VoiceMatcher.VoicePrint(wakeWord, appWord, packageName, duration, envelope)
            
            // Remove any existing print for this package before adding the new one
            val currentPrints = voiceMatcher.getEnrolledPrints().toMutableList()
            currentPrints.removeAll { it.packageName == packageName }
            
            voiceMatcher.clear()
            currentPrints.forEach { voiceMatcher.enrollVoicePrint(it) }
            voiceMatcher.enrollVoicePrint(print)
            
            // Save
            val newPrints = voiceMatcher.getEnrolledPrints()
            store.savePrints(newPrints)
            allEnrolledPrints.value = newPrints
            
            _lastEnrolledPrint.value = print
        }
        speechListener?.startListening()
    }

    fun deleteEnrollment(packageName: String) {
        val currentPrints = voiceMatcher.getEnrolledPrints().toMutableList()
        currentPrints.removeAll { it.packageName == packageName }
        
        voiceMatcher.clear()
        currentPrints.forEach { voiceMatcher.enrollVoicePrint(it) }
        
        store.savePrints(currentPrints)
        allEnrolledPrints.value = currentPrints
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
