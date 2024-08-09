/*
 * pdf2htmlEX.java
 *
 * Copyright (C) 2019, 2020, 2022, 2023, 2024 ViliusSutkus89.com
 *
 * pdf2htmlEX-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.opendocument.android.pdf2htmlex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.getkeepsafe.relinker.ReLinker;
import com.viliussutkus89.android.assetextractor.AssetExtractor;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


@SuppressWarnings("unused")
public class pdf2htmlEX implements Closeable {
  public static class ConversionFailedException extends Exception {
    public ConversionFailedException(String errorMessage) {
      super(errorMessage);
    }
  }

  public static class PasswordRequiredException extends ConversionFailedException {
    public PasswordRequiredException(String errorMessage) {
      super(errorMessage);
    }
  }

  public static class WrongPasswordException extends ConversionFailedException {
    public WrongPasswordException(String errorMessage) {
      super(errorMessage);
    }
  }

  public static class CopyProtectionException extends ConversionFailedException {
    public CopyProtectionException(String errorMessage) {
      super(errorMessage);
    }
  }

  private final NativeConverter nc;

  static private final Object s_initSynchronizer = new Object();

  // subdir of cache dir
  private final File mOutputDir;

  // subdir of mOutputDir by default, unless requested otherwise by setDestinationDir() / setOutputHtml()
  private File mDestinationDir;

  private String mOutputFilename;

  private File mInputPdf;

  private boolean mUserPasswordEntered;
  private boolean mOwnerPasswordEntered;

  public pdf2htmlEX(@NonNull Context ctx) {
    ReLinker.recursively().loadLibrary(ctx, "pdf2htmlEX-android");

    AssetExtractor ae = new AssetExtractor(ctx.getAssets()).setNoOverwrite();

    File filesDir = new File(ctx.getFilesDir(), "pdf2htmlEX");
    File cacheDir = new File(ctx.getCacheDir(), "pdf2htmlEX");

    // tmpDir is where pdf2htmlEX (not pdf2htmlEX-Android wrapper) does it's work
    File pdf2htmlEX_tmpDir = new File(cacheDir, "pdf2htmlEX-tmp");
    mOutputDir = new File(cacheDir, "output-htmls");

    File fontforgeHome = new File(cacheDir, "FontforgeHome");
    File envTMPDIR = new File(cacheDir, "envTMPDIR");

    synchronized (s_initSynchronizer) {
      cacheDir.mkdir();

      // @TODO: https://github.com/ViliusSutkus89/pdf2htmlEX-Android/issues/9
      // pdf2htmlEX_dataDir is where pdf2htmlEX's share folder contents are
      File pdf2htmlEX_dataDir = ae.extract(new File(filesDir, "share"), "pdf2htmlEX");

      // @TODO: https://github.com/ViliusSutkus89/pdf2htmlEX-Android/issues/10
      // Poppler requires encoding data
      File poppler_dataDir = ae.extract(new File(filesDir, "share"), "poppler-data");

      pdf2htmlEX_tmpDir.mkdir();
      mOutputDir.mkdir();

      fontforgeHome.mkdir();
      EnvVar.set("HOME", fontforgeHome.getAbsolutePath());

      envTMPDIR.mkdir();
      EnvVar.set("TMPDIR", envTMPDIR.getAbsolutePath());

      FontconfigAndroid.init(ctx.getAssets(), cacheDir, filesDir);

      EnvVar.set("USER", android.os.Build.MODEL);

      nc = new NativeConverter(pdf2htmlEX_tmpDir, pdf2htmlEX_dataDir, poppler_dataDir);
    }
  }

  @Override
  public void close() {
    nc.close();
  }

  private static String generateOutputFilenameWithoutExt(String inputFilename) {
    if (inputFilename.endsWith(".pdf"))
      return inputFilename.substring(0, inputFilename.length() - 4);
    else
      return inputFilename;
  }

  private static String generateOutputFilename(String inputFilename) {
    return generateOutputFilenameWithoutExt(inputFilename) + ".html";
  }

  private static File generateOutputSubDirectory(File outputDir, String inputFilename) {
    String outputFilenameNoExt = generateOutputFilenameWithoutExt(inputFilename);

    File outputSubDir = new File(outputDir, outputFilenameNoExt);
    for (int i = 2; !outputSubDir.mkdir(); i++) {
      outputSubDir = new File(outputDir, outputFilenameNoExt + "-" + i);
    }

    return outputSubDir;
  }

  public File convert() throws IOException, ConversionFailedException {
    File inputPdf = mInputPdf;
    if (null == inputPdf) {
      throw new ConversionFailedException("No Input PDF given!");
    } else if (!inputPdf.exists()) {
      throw new FileNotFoundException();
    }
    NativeConverter.setInputFile(nc.mConverter, inputPdf.getAbsolutePath());

    String outputFilename = mOutputFilename;
    File destinationDir = mDestinationDir;

    if (null == outputFilename)
      outputFilename = generateOutputFilename(inputPdf.getName());

    if (null == destinationDir)
      destinationDir = generateOutputSubDirectory(mOutputDir, inputPdf.getName());

    File outputFile = new File(destinationDir, outputFilename);

    NativeConverter.setOutputFilename(nc.mConverter, outputFilename);
    NativeConverter.setDestinationDir(nc.mConverter, destinationDir.getAbsolutePath());

    int retVal = NativeConverter.convert(nc.mConverter);
    if (0 == retVal) {
        return outputFile;
    }

    outputFile.delete();
    if (null == mDestinationDir) {
      if (destinationDir.exists() && destinationDir.isDirectory()) {
        File[] outputFiles = destinationDir.listFiles();
        if (outputFiles != null) {
          for (File i : outputFiles) {
            i.delete();
          }
        }
        destinationDir.delete();
      }
    }

    // retVal values defined in pdf2htmlEX.cc
    switch (retVal) {
      case 2:
        if (!mUserPasswordEntered && !mOwnerPasswordEntered) {
          throw new PasswordRequiredException("Password is required to decrypt this encrypted document!");
        } else {
          throw new WrongPasswordException("Wrong password is supplied to decrypt this encrypted document!");
        }

      case 3:
        throw new CopyProtectionException("Document is copy protected!");

      default:
        throw new ConversionFailedException("Return value from pdf2htmlEX: " + retVal);
    }
  }

  public File convert(@NonNull File inputPDF) throws IOException, ConversionFailedException {
    this.setInputPDF(inputPDF);
    return this.convert();
  }

  public pdf2htmlEX setInputPDF(@Nullable File inputPDF) {
    mInputPdf = inputPDF;
    return this;
  }

  public pdf2htmlEX setOutputHtml(@Nullable File outputHtml) {
    File destinationDir = null;
    String filename = null;
    if (null != outputHtml) {
      destinationDir = outputHtml.getAbsoluteFile().getParentFile();
      filename = outputHtml.getName();
    }
    return this.setDestinationDir(destinationDir).setOutputFilename(filename);
  }

  public pdf2htmlEX setOutputFilename(@Nullable String outputFilename) {
    mOutputFilename = outputFilename;
    return this;
  }

  public pdf2htmlEX setFirstPage(int firstPage) {
    NativeConverter.setFirstPage(nc.mConverter, firstPage);
    return this;
  }

  public pdf2htmlEX setLastPage(int lastPage) {
    NativeConverter.setLastPage(nc.mConverter, lastPage);
    return this;
  }

  public pdf2htmlEX setZoomRatio(double zoomRatio) {
    NativeConverter.setZoomRatio(nc.mConverter, zoomRatio);
    return this;
  }

  public pdf2htmlEX setFitWidth(double fitWidth) {
    NativeConverter.setFitWidth(nc.mConverter, fitWidth);
    return this;
  }

  public pdf2htmlEX setFitHeight(double fitHeight) {
    NativeConverter.setFitHeight(nc.mConverter, fitHeight);
    return this;
  }

  public pdf2htmlEX setUseCropBox(boolean useCropBox) {
    NativeConverter.setUseCropBox(nc.mConverter, useCropBox);
    return this;
  }

  public pdf2htmlEX setDPI(double desiredDPI) {
    NativeConverter.setDPI(nc.mConverter, desiredDPI);
    return this;
  }

  public pdf2htmlEX setEmbedCSS(boolean embedCSS) {
    NativeConverter.setEmbedCSS(nc.mConverter, embedCSS);
    return this;
  }

  public pdf2htmlEX setEmbedFont(boolean embedFont) {
    NativeConverter.setEmbedFont(nc.mConverter, embedFont);
    return this;
  }

  public pdf2htmlEX setEmbedImage(boolean embedImage) {
    NativeConverter.setEmbedImage(nc.mConverter, embedImage);
    return this;
  }

  public pdf2htmlEX setEmbedJavascript(boolean embedJavascript) {
    NativeConverter.setEmbedJavascript(nc.mConverter, embedJavascript);
    return this;
  }

  public pdf2htmlEX setEmbedOutline(boolean embedOutline) {
    NativeConverter.setEmbedOutline(nc.mConverter, embedOutline);
    return this;
  }

  public pdf2htmlEX setSplitPages(boolean splitPages) {
    NativeConverter.setSplitPages(nc.mConverter, splitPages);
    return this;
  }

  public pdf2htmlEX setDestinationDir(@Nullable File destinationDir) {
    mDestinationDir = destinationDir;
    return this;
  }

  public pdf2htmlEX setCSSFilename(@Nullable String cssFilename) {
    NativeConverter.setCSSFilename(nc.mConverter, cssFilename);
    return this;
  }

  public pdf2htmlEX setPageFilename(@Nullable String pageFilename) {
    NativeConverter.setPageFilename(nc.mConverter, pageFilename);
    return this;
  }

  public pdf2htmlEX setOutlineFilename(@Nullable String outlineFilename) {
    NativeConverter.setOutlineFilename(nc.mConverter, outlineFilename);
    return this;
  }

  public pdf2htmlEX setProcessNonText(boolean processNonText) {
    NativeConverter.setProcessNonText(nc.mConverter, processNonText);
    return this;
  }

  public pdf2htmlEX setProcessOutline(boolean processOutline) {
    NativeConverter.setProcessOutline(nc.mConverter, processOutline);
    return this;
  }

  public pdf2htmlEX setProcessAnnotation(boolean processAnnotation) {
    NativeConverter.setProcessAnnotation(nc.mConverter, processAnnotation);
    return this;
  }

  public pdf2htmlEX setProcessForm(boolean processForm) {
    NativeConverter.setProcessForm(nc.mConverter, processForm);
    return this;
  }

  public pdf2htmlEX setPrinting(boolean printing) {
    NativeConverter.setPrinting(nc.mConverter, printing);
    return this;
  }

  public pdf2htmlEX setFallback(boolean fallback) {
    NativeConverter.setFallback(nc.mConverter, fallback);
    return this;
  }

//  skipped:
//  setTmpFileSizeLimit(int tmpFileSizeLimit);

  public pdf2htmlEX setEmbedExternalFont(boolean embedExternalFont) {
    NativeConverter.setEmbedExternalFont(nc.mConverter, embedExternalFont);
    return this;
  }

  public pdf2htmlEX setFontFormat(String fontFormat) {
    NativeConverter.setFontFormat(nc.mConverter, fontFormat);
    return this;
  }

  public pdf2htmlEX setDecomposeLigature(boolean decomposeLigature) {
    NativeConverter.setDecomposeLigature(nc.mConverter, decomposeLigature);
    return this;
  }

  public pdf2htmlEX setAutoHint(boolean autoHint) {
    NativeConverter.setAutoHint(nc.mConverter, autoHint);
    return this;
  }

//  skipped:
//  setExternalHintTool(const std::string &externalHintTool);

  public pdf2htmlEX setStretchNarrowGlyph(boolean stretchNarrowGlyph) {
    NativeConverter.setStretchNarrowGlyph(nc.mConverter, stretchNarrowGlyph);
    return this;
  }

  public pdf2htmlEX setSqueezeWideGlyph(boolean squeezeWideGlyph) {
    NativeConverter.setSqueezeWideGlyph(nc.mConverter, squeezeWideGlyph);
    return this;
  }

  //clear the fstype bits in TTF/OTF fonts
  public pdf2htmlEX setOverrideFstype(boolean overrideFSType) {
    NativeConverter.setOverrideFstype(nc.mConverter, overrideFSType);
    return this;
  }

  //convert Type 3 fonts for web (experimental)
  public pdf2htmlEX setProcessType3(boolean processType3) {
    NativeConverter.setProcessType3(nc.mConverter, processType3);
    return this;
  }

  public pdf2htmlEX setHorizontalEpsilon(double horizontalEpsilon) {
    NativeConverter.setHorizontalEpsilon(nc.mConverter, horizontalEpsilon);
    return this;
  }

  public pdf2htmlEX setVerticalEpsilon(double verticalEpsilon) {
    NativeConverter.setVerticalEpsilon(nc.mConverter, verticalEpsilon);
    return this;
  }

  public pdf2htmlEX setSpaceThreshold(double spaceThreshold) {
    NativeConverter.setSpaceThreshold(nc.mConverter, spaceThreshold);
    return this;
  }

  public pdf2htmlEX setFontSizeMultiplier(double fontSizeMultiplier) {
    NativeConverter.setFontSizeMultiplier(nc.mConverter, fontSizeMultiplier);
    return this;
  }

  public pdf2htmlEX setSpaceAsOffset(boolean spaceAsOffset) {
    NativeConverter.setSpaceAsOffset(nc.mConverter, spaceAsOffset);
    return this;
  }

  public enum ToUnicodeCMapsHandler {
    AUTO(0),
    FORCE(1),
    IGNORE(-1);

    private final int value;
    ToUnicodeCMapsHandler(int value) {
      this.value = value;
    }
    int getInt() {
      return value;
    }
  }
  // how to handle ToUnicode CMaps
  public pdf2htmlEX setToUnicode(ToUnicodeCMapsHandler toUnicode) {
    NativeConverter.setToUnicode(nc.mConverter, toUnicode.getInt());
    return this;
  }

  // try to reduce the number of HTML elements used for text
  public pdf2htmlEX setOptimizeText(boolean optimizeText) {
    NativeConverter.setOptimizeText(nc.mConverter, optimizeText);
    return this;
  }

  public enum TextVisibilityCorrection {
    NO(0),
    FULL(1),
    PARTIAL(2);

    private final int value;
    TextVisibilityCorrection(int value) { this.value = value; }
    int getInt() { return value; }
  }
  public pdf2htmlEX setCorrectTextVisibility(TextVisibilityCorrection textVisibilityCorrection) {
    NativeConverter.setCorrectTextVisibility(nc.mConverter, textVisibilityCorrection.getInt());
    return this;
  }

  public pdf2htmlEX setCoveredTextDPI(double coveredTextDPI) {
    NativeConverter.setCoveredTextDPI(nc.mConverter, coveredTextDPI);
    return this;
  }

  public enum BackgroundImageFormat {
    PNG("png"),
    JPG("jpg"),
    SVG("svg");

    private final String value;
    BackgroundImageFormat(String value) {
      this.value = value;
    }
    @NonNull @Override
    public String toString() {
      return value;
    }
  }
  public pdf2htmlEX setBackgroundImageFormat(BackgroundImageFormat backgroundImageFormat) {
    NativeConverter.setBackgroundImageFormat(nc.mConverter, backgroundImageFormat.toString());
    return this;
  }

  // if node count in a svg background image exceeds this limit,
  // fall back this page to bitmap background; negative value means no limit
  public pdf2htmlEX setSVGNodeCountLimit(int SVGNodeCountLimit) {
    NativeConverter.setSVGNodeCountLimit(nc.mConverter, SVGNodeCountLimit);
    return this;
  }

//  skipped
//  setSVGEmbedBitmap(int SVGEmbedBitmap);

  public pdf2htmlEX setOwnerPassword(@Nullable String ownerPassword) {
    NativeConverter.setOwnerPassword(nc.mConverter, ownerPassword);
    mOwnerPasswordEntered = ownerPassword != null && !ownerPassword.isEmpty();
    return this;
  }

  public pdf2htmlEX setUserPassword(@Nullable String userPassword) {
    NativeConverter.setUserPassword(nc.mConverter, userPassword);
    mUserPasswordEntered = userPassword != null && !userPassword.isEmpty();
    return this;
  }

  public pdf2htmlEX setDRM(boolean enableDrm) {
    NativeConverter.setDrm(nc.mConverter, enableDrm);
    return this;
  }

  public pdf2htmlEX setDebug(boolean debug) {
    NativeConverter.setDebug(nc.mConverter, debug);
    return this;
  }

  public pdf2htmlEX setProof(boolean proof) {
    NativeConverter.setProof(nc.mConverter, proof);
    return this;
  }

  public pdf2htmlEX setQuiet(boolean quiet) {
    NativeConverter.setQuiet(nc.mConverter, quiet);
    return this;
  }
}
