#include "CoreWrapper.h"

#include <odr/TranslationHelper.h>
#include <odr/TranslationConfig.h>
#include <odr/FileMeta.h>

JNIEXPORT int JNICALL
Java_at_tomtasche_reader_background_OdfLoader_parse(JNIEnv *env, jobject instance, jstring inputPath, jstring outputPath, jstring password, jboolean editable, jobject pageNames)
{
    jboolean isCopy;
    const char *inputPathC = env->GetStringUTFChars(inputPath, &isCopy);
    std::string inputPathCpp = std::string(inputPathC, env->GetStringUTFLength(inputPath));
    env->ReleaseStringUTFChars(inputPath, inputPathC);

    try {
        odr::TranslationHelper translator;
        bool opened = translator.openOpenDocument(inputPathCpp);
        if (!opened) {
            return -1;
        }

        const auto meta = translator.getMeta();

        bool decrypted = !meta->encrypted;
        if (password != NULL) {
            const char *passwordC = env->GetStringUTFChars(password, &isCopy);
            std::string passwordCpp = std::string(passwordC, env->GetStringUTFLength(password));
            env->ReleaseStringUTFChars(password, passwordC);

            decrypted = translator.decrypt(passwordCpp);
        }

        if (!decrypted) {
            return -2;
        }

        odr::TranslationConfig config = {};
        config.editable = editable;
        config.entryCount = 1;
        config.tableLimitRows = 10000;

        const char *outputPathC = env->GetStringUTFChars(outputPath, &isCopy);
        std::string outputPathCpp = std::string(outputPathC, env->GetStringUTFLength(outputPath));
        env->ReleaseStringUTFChars(outputPath, outputPathC);

        jclass listClass = env->FindClass("java/util/List");
        jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

        if (meta->type == odr::FileType::OPENDOCUMENT_TEXT) {
            jstring pageName = env->NewStringUTF("Text document");
            env->CallBooleanMethod(pageNames, addMethod, pageName);

            outputPathCpp = outputPathCpp + "0.html";

            bool translated = translator.translate(outputPathCpp, config);
            if (!translated) {
                return -4;
            }
        } else {
            int i = 0;
            for (auto page = meta->entries.begin(); page != meta->entries.end(); page++) {
                jstring pageName = env->NewStringUTF(page->name.c_str());
                env->CallBooleanMethod(pageNames, addMethod, pageName);

                std::string entryOutputPath = outputPathCpp + std::to_string(i) + ".html";
                config.entryOffset = i;

                bool translated = translator.translate(entryOutputPath, config);
                if (!translated) {
                    return -4;
                }

                i++;
            }
        }
    } catch (...) {
        return -3;
    }

    return 0;
}
