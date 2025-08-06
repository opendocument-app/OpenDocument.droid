#pragma once

#include <jni.h>

extern "C" {

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_setGlobalParams(JNIEnv *env, jclass clazz,
                                                                jobject params);

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jclass clazz,
                                                            jobject options);

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_backtranslateNative(JNIEnv *env, jclass clazz,
                                                                    jobject options,
                                                                    jstring htmlDiff);

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_closeNative(JNIEnv *env, jclass clazz,
                                                            jobject options);

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_createServerNative(JNIEnv *env, jclass clazz,
                                                                   jstring outputPath);

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_hostFileNative(JNIEnv *env, jclass clazz,
                                                               jstring prefix, jobject options);

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_listenServerNative(JNIEnv *env, jclass clazz,
                                                                   jint port);

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_stopServerNative(JNIEnv *env, jclass clazz);

}
