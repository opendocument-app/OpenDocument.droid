#include "CoreWrapper.hpp"

#include <odr/document.hpp>
#include <odr/file.hpp>
#include <odr/html.hpp>
#include <odr/odr.hpp>
#include <odr/exceptions.hpp>
#include <odr/http_server.hpp>

#include <android/log.h>

#include <string>
#include <optional>
#include <filesystem>

std::optional<odr::Html> s_html;

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jclass clazz,
                                                            jobject options) {
    jboolean isCopy;

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    jclass optionsClass = env->GetObjectClass(options);
    jfieldID inputPathField = env->GetFieldID(optionsClass, "inputPath", "Ljava/lang/String;");
    auto inputPath = (jstring) env->GetObjectField(options, inputPathField);

    const auto inputPathC = env->GetStringUTFChars(inputPath, &isCopy);
    auto inputPathCpp = std::string(inputPathC, env->GetStringUTFLength(inputPath));
    env->ReleaseStringUTFChars(inputPath, inputPathC);

    try {
        std::optional<std::string> passwordCpp;
        jfieldID passwordField = env->GetFieldID(optionsClass, "password", "Ljava/lang/String;");
        auto password = (jstring) env->GetObjectField(options, passwordField);
        if (password != nullptr) {
            const auto passwordC = env->GetStringUTFChars(password, &isCopy);
            passwordCpp = std::string(passwordC, env->GetStringUTFLength(password));
            env->ReleaseStringUTFChars(password, passwordC);
        }

        jfieldID editableField = env->GetFieldID(optionsClass, "editable", "Z");
        jboolean editable = env->GetBooleanField(options, editableField);

        jfieldID outputPathField = env->GetFieldID(optionsClass, "outputPath",
                                                   "Ljava/lang/String;");
        auto outputPath = (jstring) env->GetObjectField(options, outputPathField);

        const auto outputPathC = env->GetStringUTFChars(outputPath, &isCopy);
        auto outputPathCpp = std::string(outputPathC, env->GetStringUTFLength(outputPath));
        env->ReleaseStringUTFChars(outputPath, outputPathC);

        jclass listClass = env->FindClass("java/util/List");
        jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

        jfieldID pageNamesField = env->GetFieldID(resultClass, "pageNames", "Ljava/util/List;");
        auto pageNames = (jobject) env->GetObjectField(result, pageNamesField);

        jfieldID pagePathsField = env->GetFieldID(resultClass, "pagePaths", "Ljava/util/List;");
        auto pagePaths = (jobject) env->GetObjectField(result, pagePathsField);

        jfieldID ooxmlField = env->GetFieldID(optionsClass, "ooxml", "Z");
        jboolean ooxml = env->GetBooleanField(options, ooxmlField);

        jfieldID txtField = env->GetFieldID(optionsClass, "txt", "Z");
        jboolean txt = env->GetBooleanField(options, txtField);

        jfieldID pdfField = env->GetFieldID(optionsClass, "pdf", "Z");
        jboolean pdf = env->GetBooleanField(options, pdfField);

        jfieldID pagingField = env->GetFieldID(optionsClass, "paging", "Z");
        jboolean paging = env->GetBooleanField(options, pagingField);

        try {
            odr::FileType fileType;
            try {
                const auto types = odr::types(inputPathCpp);
                if (types.empty()) {
                    env->SetIntField(result, errorField, -5);
                    return result;
                }

                fileType = types.back();
            } catch (odr::UnsupportedFileType &e) {
                fileType = e.file_type;
            }

            const auto extensionCpp = odr::type_to_string(fileType);
            const auto extensionC = extensionCpp.c_str();
            jstring extension = env->NewStringUTF(extensionC);

            jfieldID extensionField = env->GetFieldID(resultClass, "extension",
                                                      "Ljava/lang/String;");
            env->SetObjectField(result, extensionField, extension);

            // __android_log_print(ANDROID_LOG_VERBOSE, "smn", "%s", extensionCpp.c_str());

            const auto file = odr::open(inputPathCpp);
            const auto fileCategory = odr::category_by_type(file.file_type());

            if (!ooxml &&
                (file.file_type() == odr::FileType::office_open_xml_document ||
                 file.file_type() == odr::FileType::office_open_xml_workbook ||
                 file.file_type() == odr::FileType::office_open_xml_presentation ||
                 file.file_type() == odr::FileType::office_open_xml_encrypted)) {
                env->SetIntField(result, errorField, -5);
                return result;
            }

            if (!txt && fileCategory == odr::FileCategory::text) {
                env->SetIntField(result, errorField, -5);
                return result;
            }

            if (!pdf && file.file_type() == odr::FileType::portable_document_format) {
                env->SetIntField(result, errorField, -5);
                return result;
            }

            odr::HtmlConfig config;
            config.editable = editable;

            if (paging) {
                config.text_document_margin = true;
            }

            s_html = odr::html::translate(odr::File(inputPathCpp), outputPathCpp, config,
                                          [&passwordCpp]() -> std::string {
                if (passwordCpp.has_value()) {
                    return passwordCpp.value();
                }
                return "";
            });

            {
                const auto extensionCpp = odr::type_to_string(
                        s_html->file_type());
                const auto extensionC = extensionCpp.c_str();
                jstring extension = env->NewStringUTF(extensionC);

                jfieldID extensionField = env->GetFieldID(resultClass, "extension",
                                                          "Ljava/lang/String;");
                env->SetObjectField(result, extensionField, extension);
            }

            for (auto &&page: s_html->pages()) {
                jstring pageName = env->NewStringUTF(page.name.c_str());
                env->CallBooleanMethod(pageNames, addMethod, pageName);

                jstring pagePath = env->NewStringUTF(page.path.c_str());
                env->CallBooleanMethod(pagePaths, addMethod, pagePath);
            }
        } catch (odr::UnknownFileType &) {
            env->SetIntField(result, errorField, -5);
            return result;
        } catch (odr::WrongPassword &) {
            env->SetIntField(result, errorField, -2);
            return result;
        } catch (odr::UnsupportedFileType &) {
            env->SetIntField(result, errorField, -5);
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
Java_at_tomtasche_reader_background_CoreWrapper_backtranslateNative(JNIEnv *env, jclass clazz,
                                                                    jobject options,
                                                                    jstring htmlDiff) {
    jboolean isCopy;

    jclass optionsClass = env->GetObjectClass(options);

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    try {
        jfieldID outputPathPrefixField = env->GetFieldID(optionsClass, "outputPath",
                                                         "Ljava/lang/String;");
        jstring outputPathPrefix = (jstring) env->GetObjectField(options, outputPathPrefixField);

        const auto outputPathPrefixC = env->GetStringUTFChars(outputPathPrefix, &isCopy);
        auto outputPathPrefixCpp = std::string(outputPathPrefixC,
                                               env->GetStringUTFLength(outputPathPrefix));
        env->ReleaseStringUTFChars(outputPathPrefix, outputPathPrefixC);

        const auto htmlDiffC = env->GetStringUTFChars(htmlDiff, &isCopy);

        const auto extension = odr::type_to_string(s_html->file_type());
        const auto outputPathCpp = outputPathPrefixCpp + "." + extension;
        const char *outputPathC = outputPathCpp.c_str();
        jstring outputPath = env->NewStringUTF(outputPathC);

        jfieldID outputPathField = env->GetFieldID(resultClass, "outputPath", "Ljava/lang/String;");
        env->SetObjectField(result, outputPathField, outputPath);

        try {
            s_html->edit(htmlDiffC);

            env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);
        } catch (...) {
            env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);

            env->SetIntField(result, errorField, -6);
            return result;
        }

        try {
            s_html->save(outputPathCpp);
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
Java_at_tomtasche_reader_background_CoreWrapper_closeNative(JNIEnv *env, jclass clazz,
                                                            jobject options) {
    s_html.reset();
}

std::optional<odr::HttpServer> s_server;

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_createServerNative(JNIEnv *env, jclass clazz, jstring outputPath) {
    const char* outputPathC = env->GetStringUTFChars(outputPath, nullptr);
    std::string output_path = outputPathC;
    env->ReleaseStringUTFChars(outputPath, outputPathC);

    std::filesystem::create_directories(output_path);

    odr::HttpServer::Config config;
    config.output_path = output_path;
    s_server = odr::HttpServer(config);
}

JNIEXPORT jstring JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_hostFileNative(JNIEnv *env, jclass clazz, jobject options) {
    jboolean isCopy;

    jclass optionsClass = env->GetObjectClass(options);
    jfieldID inputPathField = env->GetFieldID(optionsClass, "inputPath", "Ljava/lang/String;");
    auto inputPath = (jstring) env->GetObjectField(options, inputPathField);

    const auto inputPathC = env->GetStringUTFChars(inputPath, &isCopy);
    auto inputPathCpp = std::string(inputPathC, env->GetStringUTFLength(inputPath));
    env->ReleaseStringUTFChars(inputPath, inputPathC);

    odr::DecodePreference decodePreference;
decodePreference.engine_priority = {
        odr::DecoderEngine::poppler, odr::DecoderEngine::wvware, odr::DecoderEngine::odr};
    odr::DecodedFile file = odr::open(inputPathCpp, decodePreference);

    __android_log_print(ANDROID_LOG_INFO, "smn", "file type %i", file.file_type());

    try {
        std::string prefix = "hi";
        s_server->serve_file(file, prefix, odr::HtmlConfig());
        return env->NewStringUTF(prefix.c_str());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, "smn", "error");
        return env->NewStringUTF("error");
    }
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_listenServerNative(JNIEnv *env, jclass clazz) {
    s_server->listen("127.0.0.1", 29665);
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_stopServerNative(JNIEnv *env, jclass clazz) {
    s_server->stop();
    s_server.reset();
}
