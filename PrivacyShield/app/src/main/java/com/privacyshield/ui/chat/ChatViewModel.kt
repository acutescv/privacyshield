package com.privacyshield.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyshield.llm.ConversationManager
import com.privacyshield.llm.LlamaEngine
import com.privacyshield.llm.LlamaJNI
import com.privacyshield.llm.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class ModelStatus { NOT_LOADED, LOADING, READY, ERROR }

data class ChatUiState(
    val messages: List<Message>  = emptyList(),
    val streamingText: String    = "",
    val isGenerating: Boolean    = false,
    val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
    val errorMessage: String?    = null,
    val contextUsed: Int         = 0
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private lateinit var llamaEngine: LlamaEngine
    private val conversationManager = ConversationManager(maxMessages = 20)
    private var generationJob: Job? = null

    fun init(context: Context) {
        if (::llamaEngine.isInitialized) return  // already initialised
        llamaEngine = LlamaEngine(context)

        val modelFile =
            File(context.getExternalFilesDir(null), "models/tinyllama-q4.gguf")
                .takeIf { it.exists() }
                ?: File(context.filesDir, "models/tinyllama-q4.gguf")

        loadModel(modelFile)
    }

    private fun loadModel(modelFile: File) {
        _uiState.update { it.copy(modelStatus = ModelStatus.LOADING) }
        viewModelScope.launch {
            llamaEngine.loadModel(modelFile)
                .onSuccess {
                    _uiState.update { s -> s.copy(modelStatus = ModelStatus.READY) }
                }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(modelStatus = ModelStatus.ERROR, errorMessage = e.message)
                    }
                }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return
        if (_uiState.value.modelStatus != ModelStatus.READY) return

        conversationManager.addUserMessage(text.trim())
        _uiState.update { it.copy(
            messages      = conversationManager.messages,
            isGenerating  = true,
            streamingText = "",
            errorMessage  = null
        )}

        generationJob = viewModelScope.launch {
            val buffer = StringBuilder()
            llamaEngine.generateResponse(
                userMessage = text.trim(),
                history     = conversationManager.messages
            ).collect { token ->
                buffer.append(token)
                _uiState.update { it.copy(streamingText = buffer.toString()) }
            }

            val reply = buffer.toString()
            conversationManager.addAssistantMessage(reply)
            _uiState.update { it.copy(
                messages      = conversationManager.messages,
                isGenerating  = false,
                streamingText = "",
                contextUsed   = LlamaJNI.getContextUsed()
            )}
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        llamaEngine.abortGeneration()
        _uiState.update { it.copy(isGenerating = false, streamingText = "") }
    }

    fun resetChat() {
        stopGeneration()
        conversationManager.clear()
        _uiState.update { it.copy(
            messages      = emptyList(),
            streamingText = "",
            isGenerating  = false,
            errorMessage  = null
        )}
    }

    val suggestedPrompts = listOf(
        "Why should I mask my ID number?",
        "What fields are safe to share for school registration?",
        "How does identity theft happen?",
        "What's the minimum info needed for a bank account?",
        "How do I protect my passport when traveling?"
    )

    override fun onCleared() {
        super.onCleared()
        if (::llamaEngine.isInitialized) llamaEngine.unload()
    }
}
