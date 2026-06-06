#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

#define TAG "MegaEffectsTCC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* libtcc public API - minimal subset we need */
typedef struct TCCState TCCState;

typedef void (*TCCErrorFunc)(void *opaque, const char *msg);

extern TCCState *tcc_new(void);
extern void      tcc_delete(TCCState *s);
extern void      tcc_set_error_func(TCCState *s, void *error_opaque, TCCErrorFunc error_func);
extern int       tcc_set_options(TCCState *s, const char *str);
extern int       tcc_add_include_path(TCCState *s, const char *pathname);
extern int       tcc_add_library_path(TCCState *s, const char *pathname);
extern int       tcc_add_library(TCCState *s, const char *libraryname);
extern int       tcc_compile_string(TCCState *s, const char *buf);
extern int       tcc_set_output_type(TCCState *s, int output_type);
extern int       tcc_output_file(TCCState *s, const char *filename);

#define TCC_OUTPUT_DLL 2

static char g_error_buf[4096];

static void error_handler(void *opaque, const char *msg) {
    size_t len = strlen(g_error_buf);
    if (len < sizeof(g_error_buf) - 2) {
        strncat(g_error_buf, msg, sizeof(g_error_buf) - len - 2);
        strcat(g_error_buf, "\n");
    }
    LOGE("TCC: %s", msg);
}

JNIEXPORT jstring JNICALL
Java_com_megaeffects_TccCompiler_nativeCompile(
    JNIEnv *env, jobject thiz,
    jstring j_source,
    jstring j_output_path,
    jstring j_include_path)
{
    memset(g_error_buf, 0, sizeof(g_error_buf));

    const char *source      = (*env)->GetStringUTFChars(env, j_source,      NULL);
    const char *output_path = (*env)->GetStringUTFChars(env, j_output_path, NULL);
    const char *include_path= (*env)->GetStringUTFChars(env, j_include_path,NULL);

    TCCState *s = tcc_new();
    if (!s) {
        (*env)->ReleaseStringUTFChars(env, j_source,       source);
        (*env)->ReleaseStringUTFChars(env, j_output_path,  output_path);
        (*env)->ReleaseStringUTFChars(env, j_include_path, include_path);
        return (*env)->NewStringUTF(env, "ERROR: tcc_new() failed");
    }

    tcc_set_error_func(s, NULL, error_handler);
    tcc_set_output_type(s, TCC_OUTPUT_DLL);
    tcc_add_include_path(s, include_path);
    tcc_add_library(s, "m");

    int compile_result = tcc_compile_string(s, source);
    int output_result  = -1;

    if (compile_result == 0) {
        output_result = tcc_output_file(s, output_path);
    }

    tcc_delete(s);

    (*env)->ReleaseStringUTFChars(env, j_source,       source);
    (*env)->ReleaseStringUTFChars(env, j_output_path,  output_path);
    (*env)->ReleaseStringUTFChars(env, j_include_path, include_path);

    if (compile_result != 0 || output_result != 0) {
        char result[4200];
        snprintf(result, sizeof(result), "ERROR:%s",
                 g_error_buf[0] ? g_error_buf : " unknown compile error");
        return (*env)->NewStringUTF(env, result);
    }

    return (*env)->NewStringUTF(env, "OK");
}

JNIEXPORT jstring JNICALL
Java_com_megaeffects_TccCompiler_nativeVersion(
    JNIEnv *env, jobject thiz)
{
    return (*env)->NewStringUTF(env, "libtcc embedded");
}
