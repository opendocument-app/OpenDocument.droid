package app.opendocument.android.pdf2htmlex;

import java.io.Closeable;
import java.io.File;

final class NativeConverter implements Closeable {
    long mConverter;

    static native long createNewConverterObject(String tmpDir, String dataDir, String popplerDir);
    NativeConverter(File tmpDir, File dataDir, File popplerDataDir) {
        mConverter = createNewConverterObject(
                tmpDir.getAbsolutePath(),
                dataDir.getAbsolutePath(),
                popplerDataDir.getAbsolutePath()
        );
    }

    private static native void dealloc(long converter);
    @Override
    public void close() {
        dealloc(mConverter);
        mConverter = 0;
    }

    static native int convert(long converter);
    static native void setInputFile(long converter, String inputFile);
    static native void setOutputFilename(long converter, String outputFilename);
    static native void setFirstPage(long converter, int firstPage);
    static native void setLastPage(long converter, int lastPage);
    static native void setZoomRatio(long converter, double zoomRatio);
    static native void setFitWidth(long converter, double fitWidth);
    static native void setFitHeight(long converter, double fitHeight);
    static native void setUseCropBox(long converter, boolean useCropBox);
    static native void setDPI(long converter, double desiredDPI);
    static native void setEmbedCSS(long converter, boolean embedCSS);
    static native void setEmbedFont(long converter, boolean embed);
    static native void setEmbedImage(long converter, boolean embedImage);
    static native void setEmbedJavascript(long converter, boolean embedJavascript);
    static native void setEmbedOutline(long converter, boolean embedOutline);
    static native void setSplitPages(long converter, boolean splitPages);
    static native void setDestinationDir(long converter, String destinationDir);
    static native void setCSSFilename(long converter, String cssFilename);
    static native void setPageFilename(long converter, String pageFilename);
    static native void setOutlineFilename(long converter, String outlineFilename);
    static native void setProcessNonText(long converter, boolean processNonText);
    static native void setProcessOutline(long converter, boolean process);
    static native void setProcessAnnotation(long converter, boolean process);
    static native void setProcessForm(long converter, boolean processForm);
    static native void setPrinting(long converter, boolean printing);
    static native void setFallback(long converter, boolean fallback);
    static native void setEmbedExternalFont(long converter, boolean embed);
    static native void setFontFormat(long converter, String fontFormat);
    static native void setDecomposeLigature(long converter, boolean decomposeLigature);
    static native void setAutoHint(long converter, boolean autoHint);
    static native void setStretchNarrowGlyph(long converter, boolean stretchNarrowGlyph);
    static native void setSqueezeWideGlyph(long converter, boolean squeezeWideGlyph);
    static native void setOverrideFstype(long converter, boolean overrideFSType);
    static native void setProcessType3(long converter, boolean processType3);
    static native void setHorizontalEpsilon(long converter, double horizontalEpsilon);
    static native void setVerticalEpsilon(long converter, double verticalEpsilon);
    static native void setSpaceThreshold(long converter, double spaceThreshold);
    static native void setFontSizeMultiplier(long converter, double fontSizeMultiplier);
    static native void setSpaceAsOffset(long converter, boolean spaceAsOffset);
    static native void setToUnicode(long converter, int toUnicode);
    static native void setOptimizeText(long converter, boolean optimizeText);
    static native void setCorrectTextVisibility(long converter, int textVisibilityCorrection);
    static native void setCoveredTextDPI(long converter, double coveredTextDPI);
    static native void setBackgroundImageFormat(long converter, String backgroundImageFormat);
    static native void setSVGNodeCountLimit(long converter, int svgNodeCountLimit);
    static native void setOwnerPassword(long converter, String ownerPassword);
    static native void setUserPassword(long converter, String userPassword);
    static native void setDrm(long converter, boolean enable);
    static native void setDebug(long converter, boolean debug);
    static native void setProof(long converter, boolean proof);
    static native void setQuiet(long converter, boolean quiet);
}
