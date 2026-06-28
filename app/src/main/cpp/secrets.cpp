#include <jni.h>
#include <string>

// To prevent simple string extraction via `strings` command, 
// you can obfuscate these further by using character arrays or XOR encryption.
// For now, we return them directly via JNI which is already a step up from plain Java constants.

extern "C" JNIEXPORT jstring JNICALL
Java_com_oflix_app_api_Secrets_getApiBaseUrl(JNIEnv *env, jobject /* this */) {
    std::string url = "https://h5-api.aoneroom.com/wefeed-h5api-bff/";
    return env->NewStringUTF(url.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oflix_app_api_Secrets_getFallbackBaseUrl(JNIEnv *env, jobject /* this */) {
    std::string url = "https://netnaija.film/";
    return env->NewStringUTF(url.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oflix_app_api_Secrets_getMovieBoxUrl(JNIEnv *env, jobject /* this */) {
    std::string url = "https://movieboxapp.in/";
    return env->NewStringUTF(url.c_str());
}
