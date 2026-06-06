#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "render_engine.c"

#define TAG "MegaEffects"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── Composite ────────────────────────────────────────────────────────────── */
JNIEXPORT void JNICALL
Java_com_megaeffects_RenderEngine_nativeComposite(
    JNIEnv *env, jobject thiz,
    jbyteArray canvas, jbyteArray layer,
    jint width, jint height)
{
    jbyte *c = (*env)->GetByteArrayElements(env, canvas, NULL);
    jbyte *l = (*env)->GetByteArrayElements(env, layer,  NULL);
    engine_composite((uint8_t*)c, (uint8_t*)l, width, height);
    (*env)->ReleaseByteArrayElements(env, canvas, c, 0);
    (*env)->ReleaseByteArrayElements(env, layer,  l, JNI_ABORT);
}

/* ── Transform ────────────────────────────────────────────────────────────── */
JNIEXPORT jbyteArray JNICALL
Java_com_megaeffects_RenderEngine_nativeTransform(
    JNIEnv *env, jobject thiz,
    jbyteArray src_arr,
    jint width, jint height,
    jfloat tx, jfloat ty,
    jfloat scale_x, jfloat scale_y,
    jfloat rot_z, jfloat rot_x, jfloat rot_y,
    jfloat opacity, jfloat perspective)
{
    jsize len = (*env)->GetArrayLength(env, src_arr);
    jbyteArray dst_arr = (*env)->NewByteArray(env, len);
    jbyte *src = (*env)->GetByteArrayElements(env, src_arr, NULL);
    jbyte *dst = (*env)->GetByteArrayElements(env, dst_arr, NULL);

    engine_transform(
        (uint8_t*)src, (uint8_t*)dst,
        width, height,
        tx, ty, scale_x, scale_y,
        rot_z, rot_x, rot_y,
        opacity, perspective
    );

    (*env)->ReleaseByteArrayElements(env, src_arr, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst_arr, dst, 0);
    return dst_arr;
}

/* ── Fill ─────────────────────────────────────────────────────────────────── */
JNIEXPORT jbyteArray JNICALL
Java_com_megaeffects_RenderEngine_nativeFill(
    JNIEnv *env, jobject thiz,
    jint width, jint height,
    jint r, jint g, jint b, jint a)
{
    jbyteArray arr = (*env)->NewByteArray(env, width * height * 4);
    jbyte *buf = (*env)->GetByteArrayElements(env, arr, NULL);
    engine_fill((uint8_t*)buf, width, height,
                (uint8_t)r, (uint8_t)g, (uint8_t)b, (uint8_t)a);
    (*env)->ReleaseByteArrayElements(env, arr, buf, 0);
    return arr;
}

/* ── Version ──────────────────────────────────────────────────────────────── */
JNIEXPORT jstring JNICALL
Java_com_megaeffects_RenderEngine_nativeVersion(
    JNIEnv *env, jobject thiz)
{
    return (*env)->NewStringUTF(env, engine_version());
}
