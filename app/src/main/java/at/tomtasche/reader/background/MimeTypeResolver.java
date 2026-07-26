package at.tomtasche.reader.background;

/**
 * Derives the final mime type / file extension pair of a document from whatever the detection steps
 * in {@link MetadataLoader} came up with.
 *
 * <p>Deliberately free of Android dependencies so it can be covered by plain JVM unit tests; in
 * production {@link ExtensionLookup} is backed by {@code MimeTypeMap}.
 */
public class MimeTypeResolver {

    /** The lookups {@code MimeTypeMap} provides, as an interface so tests can fake them. */
    public interface ExtensionLookup {

        String extensionFromMimeType(String mimeType);

        String mimeTypeFromExtension(String extension);
    }

    public static class Resolution {

        public final String mimeType;
        public final String extension;

        Resolution(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    /**
     * Returns the extension of the given filename, or null if it does not have one. Dotfiles
     * ("{@code .bashrc}") and trailing dots ("{@code report.}") count as "no extension".
     */
    public static String parseExtension(String filename) {
        if (filename == null) {
            return null;
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            return null;
        }

        return filename.substring(lastDot + 1);
    }

    /**
     * Combines a detected mime type with the extension taken from the filename. If the mime type is
     * known its canonical extension wins, but the filename extension is kept as a fallback; if no
     * mime type could be detected it is looked up from the extension instead. Either field of the
     * result may be null.
     */
    public static Resolution resolve(
            String mimeType, String filenameExtension, ExtensionLookup lookup) {
        if (mimeType != null) {
            String mimeExtension = lookup.extensionFromMimeType(mimeType);

            return new Resolution(
                    mimeType, mimeExtension != null ? mimeExtension : filenameExtension);
        }

        return new Resolution(lookup.mimeTypeFromExtension(filenameExtension), filenameExtension);
    }
}
