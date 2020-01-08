#ifndef ANDROID_CORE_WRAPPER_H
#define ANDROID_CORE_WRAPPER_H

#include <jni.h>

extern "C" {

    JNIEXPORT jobject JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jobject instance, jobject options);

    JNIEXPORT int JNICALL
    Java_at_tomtasche_reader_background_OdfLoader_backtranslate(JNIEnv *env, jobject instance, jstring htmlDiff, jstring outputPath);

    JNIEXPORT void JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_closeNative(JNIEnv *env, jobject instance, jobject options);
}

#endif //ANDROID_CORE_WRAPPER_H