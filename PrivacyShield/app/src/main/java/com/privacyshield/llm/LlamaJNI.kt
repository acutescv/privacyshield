package com.privacyshield.llm

/**
 * Callback interface for streaming token output from llama.cpp.
 * Called on the IO thread — do NOT do UI work here directly.
 */
interface TokenStreamCallback {
    fun onToken(token: String)
    fun onDone()
}

/**
 * JNI bridge to the native llama_bridge.so.
 *
 * Threading contract:
 *   loadModel()  — call on IO thread
 *   generate()   — blocks caller until done; call on IO thread
 *   abort()      — safe to call from any thread
 *   freeModel()  — call on IO thread after generate() returns
 */
object LlamaJNI {

    init {
        System.loadLibrary("llama_bridge")
    }

    /**
     * Load a GGUF model via mmap.
     * @return context handle or -1 on failure
     */
    external fun loadModel(modelPath: String, nThreads: Int, nCtx: Int): Long

    /**
     * Generate tokens for [prompt], streaming each piece via [callback].
     * Blocks until generation finishes or is aborted.
     */
    external fun generate(
        ctxHandle: Long,
        prompt: String,
        maxTokens: Int,
        callback: TokenStreamCallback
    )

    /** Signal the current generate() call to stop. Thread-safe. */
    external fun abort()

    /** Return how many tokens are in the current KV cache. */
    external fun getContextUsed(): Int

    /** Release model + context memory. */
    external fun freeModel()
}
