#pragma once
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Load a GGUF model from disk via memory mapping.
 * @param modelPath  Absolute path to the .gguf file
 * @param nThreads   Number of CPU threads for inference
 * @param nCtx       Context window size (tokens)
 * @return           Context handle (cast to jlong), or -1 on failure
 */
JNIEXPORT jlong JNICALL
Java_com_privacyshield_llm_LlamaJNI_loadModel(
    JNIEnv* env, jobject thiz,
    jstring modelPath, jint nThreads, jint nCtx);

/**
 * Stream tokens from a prompt. Calls callback.onToken() for each token,
 * callback.onDone() when generation ends or is aborted.
 */
JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_generate(
    JNIEnv* env, jobject thiz,
    jlong ctxHandle, jstring prompt,
    jint maxTokens, jobject callback);

/** Signal the generation loop to stop at next token. */
JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_abort(JNIEnv* env, jobject thiz);

/** Free model + context memory. */
JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_freeModel(JNIEnv* env, jobject thiz);

/** Return current context token count. */
JNIEXPORT jint JNICALL
Java_com_privacyshield_llm_LlamaJNI_getContextUsed(JNIEnv* env, jobject thiz);

#ifdef __cplusplus
}
#endif
