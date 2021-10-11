#include "CoreWrapper.h"
#include <string>
#include <optional>
#include <odr/document.h>
#include <odr/file.h>
#include <odr/html.h>
#include <android/log.h>
#include <odr/document_cursor.h>
#include <odr/document_element.h>

std::optional<odr::DocumentFile> documentFile;
std::optional<odr::Document> document;

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jobject instance, jobject options)
{
    jboolean isCopy;

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    __android_log_print(ANDROID_LOG_VERBOSE, "smn", "1");

    jclass optionsClass = env->GetObjectClass(options);
    jfieldID inputPathField = env->GetFieldID(optionsClass, "inputPath", "Ljava/lang/String;");
    jstring inputPath = (jstring) env->GetObjectField(options, inputPathField);

    const auto inputPathC = env->GetStringUTFChars(inputPath, &isCopy);
    auto inputPathCpp = std::string(inputPathC, env->GetStringUTFLength(inputPath));
    env->ReleaseStringUTFChars(inputPath, inputPathC);

    __android_log_print(ANDROID_LOG_VERBOSE, "smn", "2");

    try {
        odr::DecodedFile file(inputPathCpp);
        try {
            documentFile = file.document_file();
        } catch (...) {
            env->SetIntField(result, errorField, -1);
            return result;
        }

        __android_log_print(ANDROID_LOG_VERBOSE, "smn", "3");

        jfieldID passwordField = env->GetFieldID(optionsClass, "password", "Ljava/lang/String;");
        jstring password = (jstring) env->GetObjectField(options, passwordField);

        bool decrypted = !documentFile->password_encrypted();
        if (password != nullptr) {
            const auto passwordC = env->GetStringUTFChars(password, &isCopy);
            const auto passwordCpp = std::string(passwordC, env->GetStringUTFLength(password));
            env->ReleaseStringUTFChars(password, passwordC);

            decrypted = documentFile->decrypt(passwordCpp);
        }

        __android_log_print(ANDROID_LOG_VERBOSE, "smn", "4");

        if (!decrypted) {
            env->SetIntField(result, errorField, -2);
            return result;
        }

        const auto extensionCpp = documentFile->file_meta().type_as_string();
        const auto extensionC = extensionCpp.c_str();
        jstring extension = env->NewStringUTF(extensionC);

        __android_log_print(ANDROID_LOG_VERBOSE, "smn", "5");

        jfieldID extensionField = env->GetFieldID(resultClass, "extension", "Ljava/lang/String;");
        env->SetObjectField(result, extensionField, extension);

        jfieldID editableField = env->GetFieldID(optionsClass, "editable", "Z");
        jboolean editable = env->GetBooleanField(options, editableField);

        odr::HtmlConfig config;
        config.editable = editable;
        config.entry_count = 1;
        config.table_limit = {10000, 500};

        __android_log_print(ANDROID_LOG_VERBOSE, "smn", "6");

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
        if (!ooxml &&
            (documentFile->file_type() == odr::FileType::office_open_xml_document || documentFile->file_type() == odr::FileType::office_open_xml_workbook || documentFile->file_type() == odr::FileType::office_open_xml_presentation)) {
            env->SetIntField(result, errorField, -5);
            return result;
        }

        __android_log_print(ANDROID_LOG_VERBOSE, "smn", "7");

        document = documentFile->document();

        __android_log_print(ANDROID_LOG_VERBOSE, "smn", "7.1");

        if (document->document_type() == odr::DocumentType::text) {
            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "7.2t");

            jstring pageName = env->NewStringUTF("Document");
            env->CallBooleanMethod(pageNames, addMethod, pageName);

            outputPathCpp = outputPathCpp + "0.html";

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "7.3");

            odr::html::translate(*document, outputPathCpp, config);

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "7.4");
        } else if (document->document_type() == odr::DocumentType::spreadsheet) {
            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "7.2");

            auto cursor = document->root_element();

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "8");

            cursor.for_each_child([&](odr::DocumentCursor &cursor, std::uint32_t i) {
                __android_log_print(ANDROID_LOG_VERBOSE, "smn", "8.1");

                auto sheet = cursor.element().sheet();

                __android_log_print(ANDROID_LOG_VERBOSE, "smn", "9");

                jstring sheetName = env->NewStringUTF(sheet.name().c_str());
                env->CallBooleanMethod(pageNames, addMethod, sheetName);

                const auto entryOutputPath = outputPathCpp + std::to_string(i) + ".html";
                config.entry_offset = i;

                try {
                    __android_log_print(ANDROID_LOG_VERBOSE, "smn", "10");

                    odr::html::translate(*document, entryOutputPath, config);

                    __android_log_print(ANDROID_LOG_VERBOSE, "smn", "11");
                } catch (...) {
                    env->SetIntField(result, errorField, -4);
                    return result;
                }
            });
        } else if (document->document_type() == odr::DocumentType::presentation) {
            auto cursor = document->root_element();

            cursor.for_each_child([&](odr::DocumentCursor &cursor, std::uint32_t i) {
                auto slide = cursor.element().slide();

                jstring sheetName = env->NewStringUTF(slide.name().c_str());
                env->CallBooleanMethod(pageNames, addMethod, sheetName);

                const auto entryOutputPath = outputPathCpp + std::to_string(i) + ".html";
                config.entry_offset = i;

                try {
                    odr::html::translate(*document, entryOutputPath, config);
                } catch (...) {
                    env->SetIntField(result, errorField, -4);
                    return result;
                }
            });
        } else if (document->document_type() == odr::DocumentType::drawing) {
            auto cursor = document->root_element();

            cursor.for_each_child([&](odr::DocumentCursor &cursor, std::uint32_t i) {
                auto page = cursor.element().page();

                jstring sheetName = env->NewStringUTF(std::to_string(i).c_str());
                env->CallBooleanMethod(pageNames, addMethod, sheetName);

                const auto entryOutputPath = outputPathCpp + std::to_string(i) + ".html";
                config.entry_offset = i;

                try {
                    odr::html::translate(*document, entryOutputPath, config);
                } catch (...) {
                    env->SetIntField(result, errorField, -4);
                    return result;
                }
            });
        } else {
            env->SetIntField(result, errorField, -5);
            return result;
        }
    } catch (...) {
        env->SetIntField(result, errorField, -3);
        return result;
    }

    env->SetIntField(result, errorField, 0);

    __android_log_print(ANDROID_LOG_VERBOSE, "smn", "12");

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
        auto htmlDiffCpp = std::string(htmlDiffC, env->GetStringUTFLength(htmlDiff));
        env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);

        const auto extension = documentFile->file_meta().type_as_string();
        const auto outputPathCpp = outputPathPrefixCpp + "." + extension;
        const char *outputPathC = outputPathCpp.c_str();
        jstring outputPath = env->NewStringUTF(outputPathC);

        jfieldID outputPathField = env->GetFieldID(resultClass, "outputPath", "Ljava/lang/String;");
        env->SetObjectField(result, outputPathField, outputPath);

        try {
            odr::html::edit(*document, htmlDiffCpp);
        } catch (...) {
            env->SetIntField(result, errorField, -6);
            return result;
        }

        try {
            document->save(outputPathCpp);
        } catch (...) {
            env->SetIntField(result, errorField, -7);
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
    document.reset();
    documentFile.reset();
}