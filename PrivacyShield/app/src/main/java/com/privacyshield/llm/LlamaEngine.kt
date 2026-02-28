package com.privacyshield.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LlamaEngine"

class LlamaEngine(private val context: Context) {

    companion object {
        private const val N_CTX          = 2048   // context window
        private const val MAX_NEW_TOKENS = 512    // max generation length
        private val N_THREADS = (Runtime.getRuntime().availableProcessors() * 0.6)
            .toInt().coerceIn(2, 6)

        private const val SYSTEM_PROMPT = """You are a privacy assistant embedded in a secure, \
fully offline ID card protection app called Privacy Shield. Help users understand privacy risks, \
safe ID sharing practices, and identity theft prevention. Keep answers concise and practical. \
Never request or store any personal information. You operate completely offline — no internet."""
    }

    private var ctxHandle: Long = -1L
    var isLoaded: Boolean = false
        private set

    suspend fun loadModel(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                return@withContext Result.failure(Exception("Model not found at ${modelFile.path}"))
            }

            // Sanity-check available heap before loading
            val runtime    = Runtime.getRuntime()
            val freeMemMB  = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1_048_576
            Log.i(TAG, "Available heap: ${freeMemMB}MB")
            if (freeMemMB < 700) {
                return@withContext Result.failure(
                    Exception("Insufficient memory: ${freeMemMB}MB free, need 700MB+")
                )
            }

            Log.i(TAG, "Loading model from ${modelFile.absolutePath}")
            ctxHandle = LlamaJNI.loadModel(modelFile.absolutePath, N_THREADS, N_CTX)

            if (ctxHandle == -1L) {
                return@withContext Result.failure(Exception("JNI loadModel returned -1"))
            }

            isLoaded = true
            Log.i(TAG, "Model loaded OK — threads=$N_THREADS  ctx=$N_CTX")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            Result.failure(e)
        }
    }

    /**
     * Stream a response as a [Flow] of token strings.
     * Collects on the calling coroutine; emits on Dispatchers.IO.
     */
    fun generateResponse(
        userMessage: String,
        history: List<Message>
    ): Flow<String> = flow {
        if (!isLoaded || ctxHandle == -1L) {
            emit("[Error: Model not loaded]")
            return@flow
        }

        val prompt  = buildPrompt(userMessage, history)
        val channel = Channel<String>(Channel.UNLIMITED)

        withContext(Dispatchers.IO) {
            LlamaJNI.generate(
                ctxHandle  = ctxHandle,
                prompt     = prompt,
                maxTokens  = MAX_NEW_TOKENS,
                callback   = object : TokenStreamCallback {
                    override fun onToken(token: String) { channel.trySend(token) }
                    override fun onDone()               { channel.close() }
                }
            )
        }

        for (token in channel) emit(token)
    }

    /** Build TinyLlama/Llama chat template prompt. */
    private fun buildPrompt(userMessage: String, history: List<Message>): String {
        return buildString {
            append("<|system|>\n$SYSTEM_PROMPT</s>\n")
            // Include only recent history to stay within context budget
            history.takeLast(8).forEach { msg ->
                if (msg.isUser) append("<|user|>\n${msg.content}</s>\n")
                else            append("<|assistant|>\n${msg.content}</s>\n")
            }
            append("<|user|>\n$userMessage</s>\n")
            append("<|assistant|>\n")
        }
    }

    fun abortGeneration() = LlamaJNI.abort()

    fun unload() {
        if (isLoaded) {
            LlamaJNI.freeModel()
            ctxHandle = -1L
            isLoaded  = false
        }
    }
}
