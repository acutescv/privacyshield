#include "llama_bridge.h"
#include "llama.h"
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>
#include <cstring>

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

// ── Global state ───────────────────────────────────────────────────────────────
static llama_model*   g_model   = nullptr;
static llama_context* g_ctx     = nullptr;
static int            g_n_cur   = 0;
static std::atomic<bool> g_abort{false};

extern "C" {

// ── loadModel ──────────────────────────────────────────────────────────────────
JNIEXPORT jlong JNICALL
Java_com_privacyshield_llm_LlamaJNI_loadModel(
    JNIEnv* env, jobject /* thiz */,
    jstring modelPath, jint nThreads, jint nCtx)
{
    // Clean up previous instance
    if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }

    llama_backend_init();

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s  threads=%d  ctx=%d", path, nThreads, nCtx);

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap  = true;    // memory-mapped — avoids full RAM copy
    mparams.use_mlock = false;   // allow OS to page out if RAM is tight
    mparams.n_gpu_layers = 0;    // CPU-only on Android (no CUDA/Metal)

    g_model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model from file");
        return -1L;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = (uint32_t)nCtx;
    cparams.n_threads        = (uint32_t)nThreads;
    cparams.n_threads_batch  = (uint32_t)nThreads;
    cparams.flash_attn       = true;   // reduces memory bandwidth pressure
    cparams.offload_kqv      = false;  // keep KV cache on CPU

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(g_model);
        g_model = nullptr;
        return -1L;
    }

    g_n_cur = 0;
    LOGI("Model loaded successfully. Vocab size: %d", llama_n_vocab(g_model));
    return (jlong)(uintptr_t)g_ctx;
}

// ── generate ──────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_generate(
    JNIEnv* env, jobject thiz,
    jlong /* ctxHandle */, jstring jPrompt,
    jint maxTokens, jobject callback)
{
    if (!g_ctx || !g_model) {
        LOGE("generate() called but model not loaded");
        return;
    }
    g_abort.store(false);

    // Get prompt string
    const char* promptStr = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptStr);
    env->ReleaseStringUTFChars(jPrompt, promptStr);

    // Tokenize
    const int nVocab = llama_n_vocab(g_model);
    std::vector<llama_token> tokens(prompt.size() + 64);
    int nTokens = llama_tokenize(
        g_model,
        prompt.c_str(), (int32_t)prompt.size(),
        tokens.data(), (int32_t)tokens.size(),
        /*add_special=*/true,
        /*parse_special=*/false
    );

    if (nTokens < 0) {
        LOGE("Tokenization failed: buffer too small? nTokens=%d", nTokens);
        return;
    }
    tokens.resize(nTokens);
    LOGI("Prompt tokenized: %d tokens", nTokens);

    // Check context budget
    const int nCtxMax = (int)llama_n_ctx(g_ctx);
    if (nTokens >= nCtxMax - 4) {
        LOGW("Prompt too long (%d >= %d). Truncating.", nTokens, nCtxMax);
        tokens.resize(nCtxMax / 2);
        nTokens = (int)tokens.size();
    }

    // Prefill / prompt eval
    llama_batch batch = llama_batch_get_one(tokens.data(), nTokens, 0, 0);
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("llama_decode failed during prompt eval");
        return;
    }
    g_n_cur = nTokens;

    // Get callback method IDs
    jclass  cbClass  = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onDone  = env->GetMethodID(cbClass, "onDone",  "()V");

    // Sampler chain: temperature → top-p → distribution sample
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(/*seed=*/42));

    // Autoregressive decode loop
    char   tokenBuf[256];
    int    nGenerated = 0;

    while (nGenerated < maxTokens && !g_abort.load()) {
        llama_token newToken = llama_sampler_sample(sampler, g_ctx, -1);

        if (llama_token_is_eog(g_model, newToken)) {
            LOGI("EOG token reached after %d tokens", nGenerated);
            break;
        }

        // Detokenize to text piece
        int pieceLen = llama_token_to_piece(
            g_model, newToken, tokenBuf, (int)sizeof(tokenBuf) - 1, 0, false);

        if (pieceLen > 0) {
            tokenBuf[pieceLen] = '\0';
            jstring jtok = env->NewStringUTF(tokenBuf);
            env->CallVoidMethod(callback, onToken, jtok);
            env->DeleteLocalRef(jtok);
        }

        // Feed new token back
        llama_batch next = llama_batch_get_one(&newToken, 1, g_n_cur, 0);
        if (llama_decode(g_ctx, next) != 0) {
            LOGE("llama_decode failed at token %d", nGenerated);
            break;
        }

        g_n_cur++;
        nGenerated++;
    }

    llama_sampler_free(sampler);
    LOGI("Generation complete. %d tokens generated.", nGenerated);
    env->CallVoidMethod(callback, onDone);
}

// ── abort ─────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_abort(JNIEnv*, jobject) {
    g_abort.store(true);
    LOGI("Abort signal sent");
}

// ── getContextUsed ────────────────────────────────────────────────────────────
JNIEXPORT jint JNICALL
Java_com_privacyshield_llm_LlamaJNI_getContextUsed(JNIEnv*, jobject) {
    return (jint)g_n_cur;
}

// ── freeModel ─────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_freeModel(JNIEnv*, jobject) {
    g_abort.store(true);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    g_n_cur = 0;
    LOGI("Model and context freed");
}

} // extern "C"
