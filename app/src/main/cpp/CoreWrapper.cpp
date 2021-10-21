#include "CoreWrapper.h"
#include <string>
#include <optional>
#include <odr/document.h>
#include <odr/document_cursor.h>
#include <odr/document_element.h>
#include <odr/file.h>
#include <odr/html.h>
#include <odr/open_document_reader.h>
#include <odr/exceptions.h>
#include <android/log.h>

std::optional<odr::Html> html;

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jobject instance, jobject options)
{
    jboolean isCopy;

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    jclass optionsClass = env->GetObjectClass(options);
    jfieldID inputPathField = env->GetFieldID(optionsClass, "inputPath", "Ljava/lang/String;");
    jstring inputPath = (jstring) env->GetObjectField(options, inputPathField);

    const auto inputPathC = env->GetStringUTFChars(inputPath, &isCopy);
    auto inputPathCpp = std::string(inputPathC, env->GetStringUTFLength(inputPath));
    env->ReleaseStringUTFChars(inputPath, inputPathC);

    try {
        std::optional<std::string> passwordCpp;
        jfieldID passwordField = env->GetFieldID(optionsClass, "password", "Ljava/lang/String;");
        jstring password = (jstring) env->GetObjectField(options, passwordField);
        if (password != nullptr) {
            const auto passwordC = env->GetStringUTFChars(password, &isCopy);
            passwordCpp = std::string(passwordC, env->GetStringUTFLength(password));
            env->ReleaseStringUTFChars(password, passwordC);
        }

        jfieldID editableField = env->GetFieldID(optionsClass, "editable", "Z");
        jboolean editable = env->GetBooleanField(options, editableField);

        jfieldID outputPathField = env->GetFieldID(optionsClass, "outputPath", "Ljava/lang/String;");
        jstring outputPath = (jstring) env->GetObjectField(options, outputPathField);

        const auto outputPathC = env->GetStringUTFChars(outputPath, &isCopy);
        auto outputPathCpp = std::string(outputPathC, env->GetStringUTFLength(outputPath));
        env->ReleaseStringUTFChars(outputPath, outputPathC);

        jclass listClass = env->FindClass("java/util/List");
        jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

        jfieldID pageNamesField = env->GetFieldID(resultClass, "pageNames", "Ljava/util/List;");
        jobject pageNames = (jobject) env->GetObjectField(result, pageNamesField);

        jfieldID ooxmlField = env->GetFieldID(optionsClass, "ooxml", "Z");
        jboolean ooxml = env->GetBooleanField(options, ooxmlField);

        try {
            odr::HtmlConfig config;
            config.editable = editable;

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "1");

            const char* passwordC = nullptr;
            if (passwordCpp.has_value()) {
                passwordC = passwordCpp.value().c_str();
            }

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "2");
            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "%s", inputPathCpp.c_str());
            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "%s", outputPathCpp.c_str());

            html = odr::OpenDocumentReader::html(inputPathCpp, passwordC, outputPathCpp, config);

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "3");

            //const auto extensionCpp = html->file_type().type_as_string();
            //const auto extensionC = extensionCpp.c_str();
            //jstring extension = env->NewStringUTF(extensionC);

            //jfieldID extensionField = env->GetFieldID(resultClass, "extension", "Ljava/lang/String;");
            //env->SetObjectField(result, extensionField, extension);

            if (!ooxml &&
                (html->file_type() == odr::FileType::office_open_xml_document || html->file_type() == odr::FileType::office_open_xml_workbook || html->file_type() == odr::FileType::office_open_xml_presentation)) {
                // TODO: this is stupid and should happen BEFORE translation

                env->SetIntField(result, errorField, -5);
                return result;
            }

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "4");

            for (auto &&page : html->pages()) {
                __android_log_print(ANDROID_LOG_VERBOSE, "smn", "5");
                jstring pageName = env->NewStringUTF(page.name.c_str());
                env->CallBooleanMethod(pageNames, addMethod, pageName);

                __android_log_print(ANDROID_LOG_VERBOSE, "smn", "6");
            }
        } catch (odr::UnknownFileType) {
            env->SetIntField(result, errorField, -5);
            return result;
        } catch (odr::WrongPassword) {
            env->SetIntField(result, errorField, -2);
            return result;
        } catch (...) {
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

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_backtranslateNative(JNIEnv *env, jobject instance, jobject options, jstring htmlDiff)
{
    jboolean isCopy;

    jclass optionsClass = env->GetObjectClass(options);

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    try {
        jfieldID outputPathPrefixField = env->GetFieldID(optionsClass, "outputPath", "Ljava/lang/String;");
        jstring outputPathPrefix = (jstring) env->GetObjectField(options, outputPathPrefixField);

        const auto outputPathPrefixC = env->GetStringUTFChars(outputPathPrefix, &isCopy);
        auto outputPathPrefixCpp = std::string(outputPathPrefixC, env->GetStringUTFLength(outputPathPrefix));
        env->ReleaseStringUTFChars(outputPathPrefix, outputPathPrefixC);

        const auto htmlDiffC = env->GetStringUTFChars(htmlDiff, &isCopy);

        /*const auto extension = documentFile->file_meta().type_as_string();
        const auto outputPathCpp = outputPathPrefixCpp + "." + extension;
        const char *outputPathC = outputPathCpp.c_str();
        jstring outputPath = env->NewStringUTF(outputPathC);

        jfieldID outputPathField = env->GetFieldID(resultClass, "outputPath", "Ljava/lang/String;");
        env->SetObjectField(result, outputPathField, outputPath);

        try {
            html->edit(htmlDiffC);

            env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);
        } catch (...) {
            env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);

            env->SetIntField(result, errorField, -6);
            return result;
        }

        try {
            html->save(outputPathCpp);
        } catch (...) {
            env->SetIntField(result, errorField, -7);
            return result;
        }*/
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
    html.reset();
}