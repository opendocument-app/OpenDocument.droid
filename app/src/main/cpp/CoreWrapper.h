#ifndef ANDROID_CORE_WRAPPER_H
#define ANDROID_CORE_WRAPPER_H

#include <jni.h>

extern "C" {

    JNIEXPORT bool JNICALL
    Java_at_tomtasche_reader_background_CoreWrapper_init(JNIEnv *env, jobject instance);
}

#endif //ANDROID_CORE_WRAPPER_H