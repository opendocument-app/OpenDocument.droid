#include <stdlib.h>
#include <jni.h>

JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_EnvVar_set(JNIEnv *env, __attribute__((unused)) jclass clazz,
                                                      jstring key, jstring value) {
    const char *key_c = (*env)->GetStringUTFChars(env, key, JNI_FALSE);
    const char *value_c = (*env)->GetStringUTFChars(env, value, JNI_FALSE);
    setenv(key_c, value_c, 1);
    (*env)->ReleaseStringUTFChars(env, value, value_c);
    (*env)->ReleaseStringUTFChars(env, key, key_c);
}
