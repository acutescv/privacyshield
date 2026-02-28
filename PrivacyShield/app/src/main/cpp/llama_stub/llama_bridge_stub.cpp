// Stub implementation — used when llama.cpp submodule is not cloned.
// The app will compile and run, but the chatbot will show an error message
// prompting the user to set up the model properly.
#include <jni.h>
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LlamaStub", __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_privacyshield_llm_LlamaJNI_loadModel(
    JNIEnv* env, jobject, jstring, jint, jint) {
    LOGE("STUB: llama.cpp not available. Clone submodule first.");
    return -1L;
}

JNIEXPORT void JNICALL
Java_com_privacyshield_llm_LlamaJNI_generate(
    JNIEnv* env, jobject, jlong, jstring, jint, jobject callback) {
    jclass cls = env->GetObjectClass(callback);
    jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
    jmethodID onDone  = env->GetMethodID(cls, "onDone",  "()V");
    jstring msg = env->NewStringUTF(
        "Model not available. Please follow setup instructions to add llama.cpp.");
    env->CallVoidMethod(callback, onError, msg);
    env->DeleteLocalRef(msg);
    env->CallVoidMethod(callback, onDone);
}

JNIEXPORT void  JNICALL Java_com_privacyshield_llm_LlamaJNI_abort(JNIEnv*, jobject) {}
JNIEXPORT void  JNICALL Java_com_privacyshield_llm_LlamaJNI_freeModel(JNIEnv*, jobject) {}
JNIEXPORT jboolean JNICALL Java_com_privacyshield_llm_LlamaJNI_isLoaded(JNIEnv*, jobject) { return JNI_FALSE; }
JNIEXPORT jint JNICALL Java_com_privacyshield_llm_LlamaJNI_getContextSize(JNIEnv*, jobject) { return 0; }

} // extern "C"
