#include <jni.h>

JNIEXPORT jstring JNICALL
Java_com_megaeffects_TccCompiler_nativeCompile(
    JNIEnv *env, jobject thiz,
    jstring j_source, jstring j_output_path, jstring j_include_path)
{
    return (*env)->NewStringUTF(env,
        "ERROR: libtcc not bundled. Rebuild APK.");
}

JNIEXPORT jstring JNICALL
Java_com_megaeffects_TccCompiler_nativeVersion(
    JNIEnv *env, jobject thiz)
{
    return (*env)->NewStringUTF(env, "libtcc not available");
}
