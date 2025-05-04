#include "CoreWrapper.hpp"

#include <odr/document.hpp>
#include <odr/file.hpp>
#include <odr/html.hpp>
#include <odr/odr.hpp>
#include <odr/exceptions.hpp>
#include <odr/http_server.hpp>
#include <odr/global_params.hpp>

#include <android/log.h>

#include <string>
#include <optional>
#include <filesystem>

namespace {

std::string convertString(JNIEnv *env, jstring string) {
    jboolean isCopy;
    const char* cstring = env->GetStringUTFChars(string, &isCopy);
    auto cppstring = std::string(cstring, env->GetStringUTFLength(string));
    env->ReleaseStringUTFChars(string, cstring);
    return cppstring;
}

std::string getStringField(JNIEnv *env, jclass clazz, jobject object, const char *name) {
    jfieldID field = env->GetFieldID(clazz, name, "Ljava/lang/String;");
    auto string = (jstring) env->GetObjectField(object, field);
    return convertString(env, string);
}

std::string getStringField(JNIEnv *env, jobject object, const char *name) {
    jclass clazz = env->GetObjectClass(object);
    return getStringField(env, clazz, object, name);
}

}

std::optional<odr::Html> s_html;

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_setGlobalParams(JNIEnv *env, jclass clazz,
                                                                jobject params) {
    jboolean isCopy;

    jclass paramsClass = env->GetObjectClass(params);

    std::string odrCoreDataPath = getStringField(env, paramsClass, params, "coreDataPath");
    std::string fontconfigDataPath = getStringField(env, paramsClass, params, "fontconfigDataPath");
    std::string popplerDataPath = getStringField(env, paramsClass, params, "popplerDataPath");
    std::string pdf2htmlexDataPath = getStringField(env, paramsClass, params, "pdf2htmlexDataPath");

    odr::GlobalParams::set_odr_core_data_path(odrCoreDataPath);
    odr::GlobalParams::set_fontconfig_data_path(fontconfigDataPath);
    odr::GlobalParams::set_poppler_data_path(popplerDataPath);
    odr::GlobalParams::set_pdf2htmlex_data_path(pdf2htmlexDataPath);
}

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jclass clazz,
                                                            jobject options) {
    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    jclass optionsClass = env->GetObjectClass(options);
    std::string inputPathCpp = getStringField(env, optionsClass, options, "inputPath");

    try {
        std::optional<std::string> passwordCpp;
        jfieldID passwordField = env->GetFieldID(optionsClass, "password", "Ljava/lang/String;");
        auto password = (jstring) env->GetObjectField(options, passwordField);
        if (password != nullptr) {
            passwordCpp = convertString(env, password);
        }

        jfieldID editableField = env->GetFieldID(optionsClass, "editable", "Z");
        jboolean editable = env->GetBooleanField(options, editableField);

        std::string outputPathCpp = getStringField(env, optionsClass, options, "outputPath");

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

            std::string extensionCpp = odr::type_to_string(fileType);
            jstring extension = env->NewStringUTF(extensionCpp.c_str());
            jfieldID extensionField = env->GetFieldID(resultClass, "extension",
                                                      "Ljava/lang/String;");
            env->SetObjectField(result, extensionField, extension);

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "Open %s", inputPathCpp.c_str());

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

            if (file.is_document_file()) {
                odr::DocumentFile document_file = file.document_file();
                if (document_file.password_encrypted()) {
                    if (!passwordCpp.has_value() || !document_file.decrypt(passwordCpp.value())) {
                        env->SetIntField(result, errorField, -2);
                        return result;
                    }
                }
            }

            extensionCpp = odr::type_to_string(file.file_type());
            extension = env->NewStringUTF(extensionCpp.c_str());
            env->SetObjectField(result, extensionField, extension);

            odr::HtmlConfig config;
            config.editable = editable;

            if (paging) {
                config.text_document_margin = true;
            }

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "Translate to HTML");

            s_html = odr::html::translate(file, outputPathCpp, config);

            for (auto &&page: s_html->pages()) {
                jstring pageName = env->NewStringUTF(page.name.c_str());
                env->CallBooleanMethod(pageNames, addMethod, pageName);

                jstring pagePath = env->NewStringUTF(page.path.c_str());
                env->CallBooleanMethod(pagePaths, addMethod, pagePath);
            }
        } catch (const odr::UnknownFileType &e) {
            __android_log_print(ANDROID_LOG_ERROR, "smn", "Unknown file type: %s", e.what());
            env->SetIntField(result, errorField, -5);
            return result;
        } catch (const odr::UnsupportedFileType &e) {
            __android_log_print(ANDROID_LOG_ERROR, "smn", "Unsupported file type: %s", e.what());
            env->SetIntField(result, errorField, -5);
            return result;
        } catch (const std::exception &e) {
            __android_log_print(ANDROID_LOG_ERROR, "smn", "Unhandled C++ exception: %s", e.what());
            env->SetIntField(result, errorField, -4);
            return result;
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, "smn", "Unhandled C++ exception without further information");
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
    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    try {
        std::string outputPathPrefixCpp = getStringField(env, options, "outputPath");

        jboolean isCopy;
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
Java_at_tomtasche_reader_background_CoreWrapper_createServer(JNIEnv *env, jclass clazz, jstring outputPath) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "create server");

    std::string output_path = convertString(env, outputPath);

    std::filesystem::create_directories(output_path);

    odr::HttpServer::Config config;
    config.output_path = output_path;
    s_server = odr::HttpServer(config);
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_hostFile(JNIEnv *env, jclass clazz, jstring prefix, jobject options) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "host file");

    std::string inputPathCpp = getStringField(env, options, "inputPath");
    std::string prefixCpp = convertString(env, prefix);

    odr::DecodePreference decodePreference;
    decodePreference.engine_priority = {odr::DecoderEngine::poppler, odr::DecoderEngine::wvware, odr::DecoderEngine::odr};
    odr::DecodedFile file = odr::open(inputPathCpp, decodePreference);

    try {
        s_server->serve_file(file, prefixCpp, odr::HtmlConfig());
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_ERROR, "smn", "Unhandled C++ exception: %s", e.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, "smn", "Unhandled C++ exception without further information");
    }
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_listenServer(JNIEnv *env, jclass clazz, jint port) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "listen ...");

    s_server->listen("127.0.0.1", port);

    __android_log_print(ANDROID_LOG_INFO, "smn", "done listening");
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_stopServer(JNIEnv *env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "stop server");

    s_server->stop();
    s_server.reset();
}
