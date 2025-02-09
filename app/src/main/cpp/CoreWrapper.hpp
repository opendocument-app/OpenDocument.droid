#ifndef ANDROID_CORE_WRAPPER_H
#define ANDROID_CORE_WRAPPER_H

#include <jni.h>

extern "C" {

    JNIEXPORT jobject JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jclass clazz, jobject options);

    JNIEXPORT jobject JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_backtranslateNative(JNIEnv *env, jclass clazz, jobject options, jstring htmlDiff);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_closeNative(JNIEnv *env, jclass clazz, jobject options);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_createServerNative(JNIEnv *env, jclass clazz, jstring outputPath);

    JNIEXPORT jstring JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_hostFileNative(JNIEnv *env, jclass clazz, jobject options);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_listenServerNative(JNIEnv *env, jclass clazz);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_stopServerNative(JNIEnv *env, jclass clazz);
}

#endif //ANDROID_CORE_WRAPPER_H
