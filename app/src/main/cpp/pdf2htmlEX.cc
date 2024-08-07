/*
 * pdf2htmlEX.cc
 *
 * pdf2htmlEX-Android (https://github.com/ViliusSutkus89/pdf2htmlEX-Android)
 * Android port of pdf2htmlEX - Convert PDF to HTML without losing text or format.
 *
 * Copyright (c) 2019, 2022 ViliusSutkus89.com
 *
 * pdf2htmlEX-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <android/log.h>
#include "CCharGC.h"
#include "pdf2htmlEX.h"

#define retValOK 0
#define retValError 1
#define retValEncryptionError 2
#define retValCopyProtected 3

extern "C"
JNIEXPORT jlong JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_createNewConverterObject(JNIEnv *env, jclass,
                                                                                    jstring tmp_dir,
                                                                                    jstring data_dir,
                                                                                    jstring poppler_dir) {
    auto * pdf2htmlEX = new pdf2htmlEX::pdf2htmlEX();

    pdf2htmlEX->setTMPDir(CCharGC(env, tmp_dir).c_str());
    pdf2htmlEX->setDataDir(CCharGC(env, data_dir).c_str());
    pdf2htmlEX->setPopplerDataDir(CCharGC(env, poppler_dir).c_str());

    pdf2htmlEX->setDebug(true);

    return (jlong) pdf2htmlEX;
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_dealloc(JNIEnv *, jclass, jlong converter) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    delete pdf2htmlEX;
}

extern "C"
JNIEXPORT jint JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_convert(JNIEnv *, jclass, jlong converter) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    try {
        pdf2htmlEX->convert();
    } catch (const pdf2htmlEX::EncryptionPasswordException & e) {
        return retValEncryptionError;
    } catch (const pdf2htmlEX::DocumentCopyProtectedException & e) {
        return retValCopyProtected;
    } catch (const pdf2htmlEX::ConversionFailedException & e) {
        __android_log_print(ANDROID_LOG_ERROR, "pdf2htmlEX-Android" , "%s", e.what());
        return retValError;
    }
    return retValOK;
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setInputFile(JNIEnv *env, jclass, jlong converter,
                                                                        jstring input_file) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setInputFilename(CCharGC(env, input_file).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setOutputFilename(JNIEnv *env, jclass, jlong converter,
                                                                         jstring output_filename) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setOutputFilename(CCharGC(env, output_filename).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setOwnerPassword(JNIEnv *env, jclass, jlong converter,
                                                                            jstring owner_password) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setOwnerPassword(CCharGC(env, owner_password).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setUserPassword(JNIEnv *env, jclass, jlong converter,
                                                                           jstring user_password) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setUserPassword(CCharGC(env, user_password).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setProcessOutline(JNIEnv *, jclass, jlong converter,
                                                                             jboolean process) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setProcessOutline(JNI_TRUE == process);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setDrm(JNIEnv *, jclass, jlong converter,
                                                                  jboolean enable) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setDRM(JNI_TRUE == enable);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setEmbedFont(JNIEnv *, jclass, jlong converter,
                                                                        jboolean embed) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setEmbedFont(JNI_TRUE == embed);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setEmbedExternalFont(JNIEnv *, jclass, jlong converter,
                                                                                jboolean embed) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setEmbedExternalFont(JNI_TRUE == embed);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setProcessAnnotation(JNIEnv *, jclass, jlong converter,
                                                                                jboolean process) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setProcessAnnotation(JNI_TRUE == process);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setBackgroundImageFormat(JNIEnv *env, jclass, jlong converter,
                                                                               jstring background_format) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setBackgroundImageFormat(CCharGC(env, background_format).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setFirstPage(JNIEnv *, jclass,
                                                                        jlong converter,
                                                                        jint first_page) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setFirstPage(first_page);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setLastPage(JNIEnv *, jclass,
                                                                       jlong converter,
                                                                       jint last_page) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setLastPage(last_page);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setZoomRatio(JNIEnv *, jclass,
                                                                        jlong converter,
                                                                        jdouble zoom_ratio) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setZoomRatio(zoom_ratio);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setFitWidth(JNIEnv *, jclass,
                                                                       jlong converter,
                                                                       jdouble fit_width) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setFitWidth(fit_width);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setFitHeight(JNIEnv *, jclass,
                                                                        jlong converter,
                                                                        jdouble fit_height) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setFitHeight(fit_height);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setUseCropBox(JNIEnv *, jclass,
                                                                         jlong converter,
                                                                         jboolean use_crop_box) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setUseCropBox(JNI_TRUE == use_crop_box);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setDPI(JNIEnv *, jclass,
                                                                  jlong converter,
                                                                  jdouble desired_dpi) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setDPI(desired_dpi);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setEmbedCSS(JNIEnv *, jclass,
                                                                       jlong converter,
                                                                       jboolean embed_css) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setEmbedCSS(JNI_TRUE == embed_css);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setEmbedImage(JNIEnv *, jclass,
                                                                         jlong converter,
                                                                         jboolean embed_image) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setEmbedImage(JNI_TRUE == embed_image);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setEmbedJavascript(JNIEnv *, jclass,
                                                                              jlong converter,
                                                                              jboolean embed_javascript) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setEmbedJavascript(JNI_TRUE == embed_javascript);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setEmbedOutline(JNIEnv *, jclass,
                                                                           jlong converter,
                                                                           jboolean embed_outline) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setEmbedOutline(JNI_TRUE == embed_outline);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setSplitPages(JNIEnv *, jclass,
                                                                         jlong converter,
                                                                         jboolean split_pages) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setSplitPages(JNI_TRUE == split_pages);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setProcessNonText(JNIEnv *, jclass,
                                                                             jlong converter,
                                                                             jboolean process_non_text) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setProcessNonText(JNI_TRUE == process_non_text);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setProcessForm(JNIEnv *, jclass,
                                                                          jlong converter,
                                                                          jboolean process_form) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setProcessForm(JNI_TRUE == process_form);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setPrinting(JNIEnv *, jclass,
                                                                       jlong converter,
                                                                       jboolean printing) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setPrinting(JNI_TRUE == printing);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setFallback(JNIEnv *, jclass,
                                                                       jlong converter,
                                                                       jboolean fallback) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setFallback(JNI_TRUE == fallback);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setFontFormat(JNIEnv *env, jclass,
                                                                         jlong converter,
                                                                         jstring font_format) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setFontFormat(CCharGC(env, font_format).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setDecomposeLigature(JNIEnv *, jclass,
                                                                                jlong converter,
                                                                                jboolean decompose_ligature) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setDecomposeLigature(JNI_TRUE == decompose_ligature);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setAutoHint(JNIEnv *, jclass,
                                                                       jlong converter,
                                                                       jboolean auto_hint) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setAutoHint(JNI_TRUE == auto_hint);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setStretchNarrowGlyph(JNIEnv *, jclass,
                                                                                 jlong converter,
                                                                                 jboolean stretch_narrow_glyph) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setStretchNarrowGlyph(JNI_TRUE == stretch_narrow_glyph);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setSqueezeWideGlyph(JNIEnv *, jclass,
                                                                               jlong converter,
                                                                               jboolean squeeze_wide_glyph) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setSqueezeWideGlyph(JNI_TRUE == squeeze_wide_glyph);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setOverrideFstype(JNIEnv *, jclass,
                                                                             jlong converter,
                                                                             jboolean override_fstype) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setOverrideFstype(JNI_TRUE == override_fstype);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setProcessType3(JNIEnv *, jclass,
                                                                           jlong converter,
                                                                           jboolean process_type3) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setProcessType3(JNI_TRUE == process_type3);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setHorizontalEpsilon(JNIEnv *, jclass,
                                                                                jlong converter,
                                                                                jdouble horizontal_epsilon) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setHorizontalEpsilon(horizontal_epsilon);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setVerticalEpsilon(JNIEnv *, jclass,
                                                                              jlong converter,
                                                                              jdouble vertical_epsilon) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setVEpsilon(vertical_epsilon);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setSpaceThreshold(JNIEnv *, jclass,
                                                                             jlong converter,
                                                                             jdouble space_threshold) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setSpaceThreshold(space_threshold);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setFontSizeMultiplier(JNIEnv *, jclass,
                                                                                 jlong converter,
                                                                                 jdouble font_size_multiplier) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setFontSizeMultiplier(font_size_multiplier);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setSpaceAsOffset(JNIEnv *, jclass,
                                                                            jlong converter,
                                                                            jboolean space_as_offset) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setSpaceAsOffset(JNI_TRUE == space_as_offset);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setToUnicode(JNIEnv *, jclass,
                                                                        jlong converter,
                                                                        jint toUnicode) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setToUnicode(toUnicode);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setOptimizeText(JNIEnv *, jclass,
                                                                           jlong converter,
                                                                           jboolean optimize_text) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setOptimizeText(JNI_TRUE == optimize_text);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setCorrectTextVisibility(JNIEnv *, jclass,
                                                                                    jlong converter,
                                                                                    jint textVisibilityCorrection) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setCorrectTextVisibility(textVisibilityCorrection);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setCoveredTextDPI(JNIEnv *, jclass,
                                                                             jlong converter,
                                                                             jdouble covered_text_dpi) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setCoveredTextDPI(covered_text_dpi);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setSVGNodeCountLimit(JNIEnv *, jclass,
                                                                                jlong converter,
                                                                                jint svg_node_count_limit) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setSVGNodeCountLimit(svg_node_count_limit);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setDebug(JNIEnv *, jclass,
                                                                    jlong converter,
                                                                    jboolean debug) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setDebug(JNI_TRUE == debug);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setProof(JNIEnv *, jclass,
                                                                    jlong converter,
                                                                    jboolean proof) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setProof(JNI_TRUE == proof);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setQuiet(JNIEnv *, jclass,
                                                                    jlong converter,
                                                                    jboolean quiet) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setQuiet(JNI_TRUE == quiet);
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setDestinationDir(JNIEnv *env, jclass,
                                                                             jlong converter,
                                                                             jstring destination_dir) {

    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setDestinationDir(CCharGC(env, destination_dir).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setCSSFilename(JNIEnv *env, jclass,
                                                                          jlong converter,
                                                                          jstring css_filename) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setCSSFilename(CCharGC(env, css_filename).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setPageFilename(JNIEnv *env, jclass,
                                                                           jlong converter,
                                                                           jstring page_filename) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setPageFilename(CCharGC(env, page_filename).c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_app_opendocument_android_pdf2htmlex_NativeConverter_setOutlineFilename(JNIEnv *env, jclass,
                                                                              jlong converter,
                                                                              jstring outline_filename) {
    auto * pdf2htmlEX = (pdf2htmlEX::pdf2htmlEX *) converter;
    pdf2htmlEX->setOutlineFilename(CCharGC(env, outline_filename).c_str());
}
