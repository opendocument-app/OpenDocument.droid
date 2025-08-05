#include "core_wrapper.hpp"

#include "tmpfile_hack.hpp"

#include <odr/document.hpp>
#include <odr/file.hpp>
#include <odr/html.hpp>
#include <odr/logger.hpp>
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
        const char *cstring = env->GetStringUTFChars(string, &isCopy);
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

    class AndroidLogger final : public odr::Logger {
    public:
        static int to_android_log_level(odr::LogLevel level) {
            switch (level) {
                case odr::LogLevel::verbose:
                    return ANDROID_LOG_VERBOSE;
                case odr::LogLevel::debug:
                    return ANDROID_LOG_DEBUG;
                case odr::LogLevel::info:
                    return ANDROID_LOG_INFO;
                case odr::LogLevel::warning:
                    return ANDROID_LOG_WARN;
                case odr::LogLevel::error:
                    return ANDROID_LOG_ERROR;
                case odr::LogLevel::fatal:
                    return ANDROID_LOG_FATAL;
                default:
                    return ANDROID_LOG_UNKNOWN;
            }
        }

        void flush() override {}

        [[nodiscard]] bool will_log(odr::LogLevel level) const override {
            return true;
        }

    protected:
        void log_impl(Time time, odr::LogLevel level, const std::string &message,
                      const std::source_location &location) override {
            __android_log_print(to_android_log_level(level), "smn", "%s", message.c_str());
        }

    private:

    };

}

std::optional<odr::Document> s_document;

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_setGlobalParams(JNIEnv *env, jclass clazz,
                                                                jobject params) {
    jclass paramsClass = env->GetObjectClass(params);

    std::string odrCoreDataPath = getStringField(env, paramsClass, params, "coreDataPath");
    std::string fontconfigDataPath = getStringField(env, paramsClass, params, "fontconfigDataPath");
    std::string popplerDataPath = getStringField(env, paramsClass, params, "popplerDataPath");
    std::string pdf2htmlexDataPath = getStringField(env, paramsClass, params, "pdf2htmlexDataPath");
    std::string customTmpfilePath = getStringField(env, paramsClass, params, "customTmpfilePath");

    odr::GlobalParams::set_odr_core_data_path(odrCoreDataPath);
    odr::GlobalParams::set_fontconfig_data_path(fontconfigDataPath);
    odr::GlobalParams::set_poppler_data_path(popplerDataPath);
    odr::GlobalParams::set_pdf2htmlex_data_path(pdf2htmlexDataPath);

    tmpfile_hack::set_tmpfile_directory(customTmpfilePath);
}

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_parseNative(JNIEnv *env, jclass clazz,
                                                            jobject options) {
    std::error_code ec;
    auto logger = std::make_shared<AndroidLogger>();

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
        std::string cachePathCpp = getStringField(env, optionsClass, options, "cachePath");

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
                const auto types = odr::list_file_types(inputPathCpp, *logger);
                if (types.empty()) {
                    env->SetIntField(result, errorField, -5);
                    return result;
                }

                fileType = types.back();
            } catch (odr::UnsupportedFileType &e) {
                fileType = e.file_type;
            }

            std::string extensionCpp = odr::file_type_to_string(fileType);
            jstring extension = env->NewStringUTF(extensionCpp.c_str());
            jfieldID extensionField = env->GetFieldID(resultClass, "extension",
                                                      "Ljava/lang/String;");
            env->SetObjectField(result, extensionField, extension);

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "Open %s", inputPathCpp.c_str());

            auto file = odr::open(inputPathCpp, *logger);

            if (file.password_encrypted()) {
                if (!passwordCpp.has_value()) {
                    env->SetIntField(result, errorField, -2);
                    return result;
                }
                try {
                    file = file.decrypt(passwordCpp.value());
                } catch (...) {
                    env->SetIntField(result, errorField, -2);
                    return result;
                }
            }

            if (file.is_document_file()) {
                // TODO this will cause a second load
                s_document = file.as_document_file().document();
            }

            extensionCpp = odr::file_type_to_string(file.file_type());
            extension = env->NewStringUTF(extensionCpp.c_str());
            env->SetObjectField(result, extensionField, extension);

            odr::HtmlConfig htmlConfig;
            htmlConfig.editable = editable;
            htmlConfig.text_document_margin = paging;

            __android_log_print(ANDROID_LOG_VERBOSE, "smn", "Translate to HTML");

            std::filesystem::remove_all(cachePathCpp, ec);
            std::filesystem::create_directories(cachePathCpp);
            odr::HtmlService service = odr::html::translate(file, cachePathCpp, htmlConfig, logger);
            odr::Html html = service.bring_offline(outputPathCpp);
            std::filesystem::remove_all(cachePathCpp);

            for (const odr::HtmlPage &page: html.pages()) {
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
            __android_log_print(ANDROID_LOG_ERROR, "smn",
                                "Unhandled C++ exception without further information");
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

    if (!s_document.has_value()) {
        env->SetIntField(result, errorField, -1);
        return result;
    }

    try {
        std::string outputPathPrefixCpp = getStringField(env, options, "outputPath");

        jboolean isCopy;
        const auto htmlDiffC = env->GetStringUTFChars(htmlDiff, &isCopy);

        const auto extension = odr::file_type_to_string(s_document->file_type());
        const auto outputPathCpp = outputPathPrefixCpp + "." + extension;
        const char *outputPathC = outputPathCpp.c_str();
        jstring outputPath = env->NewStringUTF(outputPathC);

        jfieldID outputPathField = env->GetFieldID(resultClass, "outputPath", "Ljava/lang/String;");
        env->SetObjectField(result, outputPathField, outputPath);

        try {
            odr::html::edit(*s_document, htmlDiffC);

            env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);
        } catch (...) {
            env->ReleaseStringUTFChars(htmlDiff, htmlDiffC);

            env->SetIntField(result, errorField, -6);
            return result;
        }

        try {
            s_document->save(outputPathCpp);
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
    s_document.reset();
}

std::optional<odr::HttpServer> s_server;

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_createServerNative(JNIEnv *env, jclass clazz,
                                                                   jstring cachePath) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "create server");

    std::string cachePathCpp = convertString(env, cachePath);

    std::filesystem::create_directories(cachePathCpp);

    odr::HttpServer::Config config;
    config.cache_path = cachePathCpp;
    s_server = odr::HttpServer(config);
}

JNIEXPORT jobject JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_hostFileNative(JNIEnv *env, jclass clazz,
                                                               jstring prefix,
                                                               jobject options) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "host file");

    std::error_code ec;
    auto logger = std::make_shared<AndroidLogger>();

    jclass resultClass = env->FindClass("at/tomtasche/reader/background/CoreWrapper$CoreResult");
    jmethodID resultConstructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, resultConstructor);

    jfieldID errorField = env->GetFieldID(resultClass, "errorCode", "I");

    if (!s_server.has_value()) {
        env->SetIntField(result, errorField, -1);
        return result;
    }

    try {
        s_server->clear();

        jclass optionsClass = env->GetObjectClass(options);

        std::optional<std::string> passwordCpp;
        jfieldID passwordField = env->GetFieldID(optionsClass, "password", "Ljava/lang/String;");
        auto password = (jstring) env->GetObjectField(options, passwordField);
        if (password != nullptr) {
            passwordCpp = convertString(env, password);
        }

        jfieldID pagingField = env->GetFieldID(optionsClass, "paging", "Z");
        jboolean paging = env->GetBooleanField(options, pagingField);

        jfieldID editableField = env->GetFieldID(optionsClass, "editable", "Z");
        jboolean editable = env->GetBooleanField(options, editableField);

        std::string outputPathCpp = getStringField(env, optionsClass, options, "outputPath");
        std::string cachePathCpp = getStringField(env, optionsClass, options, "cachePath");

        jclass listClass = env->FindClass("java/util/List");
        jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

        jfieldID pageNamesField = env->GetFieldID(resultClass, "pageNames", "Ljava/util/List;");
        auto pageNames = (jobject) env->GetObjectField(result, pageNamesField);

        jfieldID pagePathsField = env->GetFieldID(resultClass, "pagePaths", "Ljava/util/List;");
        auto pagePaths = (jobject) env->GetObjectField(result, pagePathsField);

        std::string inputPathCpp = getStringField(env, options, "inputPath");
        std::string prefixCpp = convertString(env, prefix);

        try {
            odr::DecodePreference decodePreference;
            decodePreference.engine_priority = {odr::DecoderEngine::poppler,
                                                odr::DecoderEngine::wvware,
                                                odr::DecoderEngine::odr};
            odr::DecodedFile file = odr::open(inputPathCpp, decodePreference, *logger);

            if (file.password_encrypted()) {
                if (!passwordCpp.has_value()) {
                    env->SetIntField(result, errorField, -2);
                    return result;
                }
                try {
                    file = file.decrypt(passwordCpp.value());
                } catch (...) {
                    env->SetIntField(result, errorField, -2);
                    return result;
                }
            }

            if (file.is_document_file()) {
                // TODO this will cause a second load
                s_document = file.as_document_file().document();
            }

            odr::HtmlConfig htmlConfig;
            htmlConfig.embed_images = false;
            htmlConfig.embed_shipped_resources = true;
            htmlConfig.relative_resource_paths = false;
            htmlConfig.text_document_margin = paging;
            htmlConfig.editable = editable;

            std::filesystem::remove_all(cachePathCpp, ec);
            std::filesystem::create_directories(cachePathCpp);
            odr::HtmlService service = odr::html::translate(file, cachePathCpp, htmlConfig, logger);
            s_server->connect_service(service, prefixCpp);
            odr::HtmlViews htmlViews = service.list_views();

            for (const auto &view: htmlViews) {
                __android_log_print(ANDROID_LOG_INFO, "smn", "view name=%s path=%s",
                                    view.name().c_str(), view.path().c_str());
                if (file.is_document_file() && (
                        (((file.as_document_file().document_type() ==
                           odr::DocumentType::presentation) ||
                          (file.as_document_file().document_type() ==
                           odr::DocumentType::drawing)) &&
                         (view.name() != "document")) ||
                        ((file.as_document_file().document_type() ==
                          odr::DocumentType::spreadsheet) &&
                         (view.name() == "document")))) {
                    continue;
                }

                jstring pageName = env->NewStringUTF(view.name().c_str());
                env->CallBooleanMethod(pageNames, addMethod, pageName);

                std::string pagePathCpp =
                        "http://localhost:29665/file/" + prefixCpp + "/" + view.path();
                jstring pagePath = env->NewStringUTF(pagePathCpp.c_str());
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
            __android_log_print(ANDROID_LOG_ERROR, "smn",
                                "Unhandled C++ exception without further information");
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
Java_at_tomtasche_reader_background_CoreWrapper_listenServerNative(JNIEnv *env, jclass clazz,
                                                                   jint port) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "listen ...");

    s_server->listen("127.0.0.1", port);

    __android_log_print(ANDROID_LOG_INFO, "smn", "done listening");
}

JNIEXPORT void JNICALL
Java_at_tomtasche_reader_background_CoreWrapper_stopServerNative(JNIEnv *env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, "smn", "stop server");

    s_server->stop();
    s_server.reset();
}
