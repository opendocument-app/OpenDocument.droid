#include "CoreWrapper.h"

#include <odr/TranslationHelper.h>
#include <odr/TranslationConfig.h>
#include <odr/FileMeta.h>

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jobject instance, jobject options)
{
    jboolean isCopy;

    jclass optionsClass = env->GetObjectClass(options);
    jfieldID pointerField = env->GetFieldID(optionsClass, "nativePointer", "J");

    //env->DeleteLocalRef(optionsClass);

    odr::TranslationHelper *translator;

    jlong pointer = env->GetLongField(options, pointerField);
    if (pointer == 0) {
        translator = new odr::TranslationHelper();
    } else {
        translator = (odr::TranslationHelper *) pointer;
    }

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID pointerResultField = env->GetFieldID(resultClass, "nativePointer", "J");
    env->SetLongField(result, pointerResultField, reinterpret_cast<jlong>(translator));

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    jfieldID inputPathField = env->GetFieldID(optionsClass, "inputPath", "Ljava/lang/String;");
    jstring inputPath = (jstring) env->GetObjectField(options, inputPathField);

    const char *inputPathC = env->GetStringUTFChars(inputPath, &isCopy);
    std::string inputPathCpp = std::string(inputPathC, env->GetStringUTFLength(inputPath));
    env->ReleaseStringUTFChars(inputPath, inputPathC);

    try {
        bool opened = translator->openOpenDocument(inputPathCpp);
        if (!opened) {
            env->SetIntField(result, errorField, -1);
            return result;
        }

        const auto meta = translator->getMeta();

        jfieldID passwordField = env->GetFieldID(optionsClass, "password", "Ljava/lang/String;");
        jstring password = (jstring) env->GetObjectField(options, passwordField);

        bool decrypted = !meta->encrypted;
        if (password != NULL) {
            const char *passwordC = env->GetStringUTFChars(password, &isCopy);
            std::string passwordCpp = std::string(passwordC, env->GetStringUTFLength(password));
            env->ReleaseStringUTFChars(password, passwordC);

            decrypted = translator->decrypt(passwordCpp);
        }

        if (!decrypted) {
            env->SetIntField(result, errorField, -2);
            return result;
        }

        jfieldID editableField = env->GetFieldID(optionsClass, "editable", "Z");
        jboolean editable = env->GetBooleanField(options, editableField);

        odr::TranslationConfig config = {};
        config.editable = editable;
        config.entryCount = 1;
        config.tableLimitRows = 10000;

        jfieldID outputPathField = env->GetFieldID(optionsClass, "outputPath", "Ljava/lang/String;");
        jstring outputPath = (jstring) env->GetObjectField(options, outputPathField);

        const char *outputPathC = env->GetStringUTFChars(outputPath, &isCopy);
        std::string outputPathCpp = std::string(outputPathC, env->GetStringUTFLength(outputPath));
        env->ReleaseStringUTFChars(outputPath, outputPathC);

        jclass listClass = env->FindClass("java/util/List");
        jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

        jfieldID pageNamesField = env->GetFieldID(resultClass, "pageNames", "Ljava/util/List;");
        jstring pageNames = (jstring) env->GetObjectField(result, pageNamesField);

        if (meta->type == odr::FileType::OPENDOCUMENT_TEXT) {
            jstring pageName = env->NewStringUTF("Text document");
            env->CallBooleanMethod(pageNames, addMethod, pageName);

            outputPathCpp = outputPathCpp + "0.html";

            bool translated = translator->translate(outputPathCpp, config);
            if (!translated) {
                env->SetIntField(result, errorField, -4);
                return result;
            }
        } else {
            int i = 0;
            for (auto page = meta->entries.begin(); page != meta->entries.end(); page++) {
                jstring pageName = env->NewStringUTF(page->name.c_str());
                env->CallBooleanMethod(pageNames, addMethod, pageName);

                std::string entryOutputPath = outputPathCpp + std::to_string(i) + ".html";
                config.entryOffset = i;

                bool translated = translator->translate(entryOutputPath, config);
                if (!translated) {
                    env->SetIntField(result, errorField, -4);
                    return result;
                }

                i++;
            }
        }
    } catch (...) {
        env->SetIntField(result, errorField, -3);
        return result;
    }

    env->SetIntField(result, errorField, 0);
    return result;
}

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_backtranslateNative(JNIEnv *env, jobject instance, jobject options, jstring htmlDiff)
{
    jboolean isCopy;

    jclass optionsClass = env->GetObjectClass(options);
    jfieldID pointerField = env->GetFieldID(optionsClass, "nativePointer", "J");

    jlong pointer = env->GetLongField(options, pointerField);
    odr::TranslationHelper *translator = (odr::TranslationHelper *) pointer;

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    try {
        jfieldID outputPathField = env->GetFieldID(optionsClass, "outputPath", "Ljava/lang/String;");
        jstring outputPath = (jstring) env->GetObjectField(options, outputPathField);

        const char *outputPathC = env->GetStringUTFChars(outputPath, &isCopy);
        std::string outputPathCpp = std::string(outputPathC, env->GetStringUTFLength(outputPath));
        env->ReleaseStringUTFChars(outputPath, outputPathC);

        const char *htmlDiffC = env->GetStringUTFChars(htmlDiff, &isCopy);
        std::string htmlDiffCpp = std::string(htmlDiffC, env->GetStringUTFLength(htmlDiff));
        env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);

        bool translated = translator->backTranslate(htmlDiffCpp, outputPathCpp);
        if (!translated) {
            env->SetIntField(result, errorField, -4);
            return result;
        }
    } catch (...) {
        env->SetIntField(result, errorField, -3);
        return result;
    }

    env->SetIntField(result, errorField, 0);
    return result;
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_closeNative(JNIEnv *env, jobject instance, jobject options)
{
    jclass optionsClass = env->GetObjectClass(options);
    jfieldID pointerField = env->GetFieldID(optionsClass, "nativePointer", "J");

    long pointer = env->GetLongField(options, pointerField);
    odr::TranslationHelper *translator = (odr::TranslationHelper *) pointer;

    delete translator;
}