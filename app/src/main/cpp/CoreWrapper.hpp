#ifndef ANDROID_CORE_WRAPPER_H
#define ANDROID_CORE_WRAPPER_H

#include <jni.h>

extern "C" {

    JNIEXPORT jobject JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jobject instance, jobject options);

    JNIEXPORT jobject JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_backtranslateNative(JNIEnv *env, jobject instance, jobject options, jstring htmlDiff);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_closeNative(JNIEnv *env, jobject instance, jobject options);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_createServerNative(JNIEnv *env, jobject instance);

    JNIEXPORT jstring JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_hostFileNative(JNIEnv *env, jobject instance, jobject options);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_listenServerNative(JNIEnv *env, jobject instance);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_stopServerNative(JNIEnv *env, jobject instance);
}

#endif //ANDROID_CORE_WRAPPER_H
