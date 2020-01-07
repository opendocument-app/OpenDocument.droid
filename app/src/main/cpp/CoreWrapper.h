#ifndef ANDROID_CORE_WRAPPER_H
#define ANDROID_CORE_WRAPPER_H

#include <jni.h>

extern "C" {

    JNIEXPORT int JNICALL
    Java_at_tomtasche_reader_background_OdfLoader_parse(JNIEnv *env, jobject instance, jstring inputPath, jstring outputPath, jstring password, jboolean editable, jobject pageNames);

    JNIEXPORT int JNICALL
    Java_at_tomtasche_reader_background_OdfLoader_backtranslate(JNIEnv *env, jobject instance, jstring htmlDiff, jstring outputPath);
}

#endif //ANDROID_CORE_WRAPPER_H