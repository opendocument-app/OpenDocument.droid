package app.opendocument.droid.test

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.core.FileType
import app.opendocument.core.Odr
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.CoreLoader
import app.opendocument.droid.background.SupportedDocumentTypes
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keeps the two remaining declarations of what the app opens in step with each other, so that
 * changing one and forgetting the other is a failing test rather than a bug report.
 *
 * [SupportedDocumentTypes] is one of them. The other is the STRICT_CATCH `activity-alias` in
 * AndroidManifest.xml, which is XML and cannot read a kotlin list - but it can be *asked*: an
 * intent-filter is only worth anything if the package manager resolves a real intent through it,
 * which is what [resolvesToUs] does here. There used to be a third copy in `CoreLoader`; that one
 * is gone, it defers to [SupportedDocumentTypes] now.
 *
 * The mime side needs no list of its own at all: [FileType] is the core's complete set of formats
 * and [Odr.mimetypeByFileType] names each one, so the test walks what odrcore knows and demands
 * that the app and the manifest give the same answer for every entry.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class SupportedFormatsTest {

    /**
     * For every format odrcore can name a mime type for, what the app offers itself for and what
     * the manifest offers it for have to be the same answer.
     *
     * This is the assertion that catches a format being added to the loader without being added to
     * the manifest, which is the failure the user sees as "the app is not in the share sheet".
     */
    @Test
    fun theManifestClaimsExactlyWhatTheAppClaims() {
        var checked = 0

        for (fileType in FileType.values()) {
            val mimeType = mimeTypeOrNull(fileType) ?: continue
            checked++

            val claimedByApp = SupportedDocumentTypes.isSupported(mimeType, null)
            val claimedByManifest = resolvesToUs(mimeType, "document")

            Assert.assertEquals(
                "$fileType ($mimeType): the app says $claimedByApp, the manifest says" +
                    " $claimedByManifest - AndroidManifest.xml and SupportedDocumentTypes have" +
                    " drifted apart",
                claimedByApp,
                claimedByManifest,
            )
        }

        // a guard on the guard: if the core ever stops naming mime types the loop above would
        // silently assert nothing at all
        Assert.assertTrue("no file type was checked", checked > 10)
    }

    /** Every extension named after the core's own table has to be one odrcore still recognises. */
    @Test
    fun theExtensionFallbackNamesFormatsTheCoreKnows() {
        for (extension in SupportedDocumentTypes.CORE_EXTENSIONS) {
            Assert.assertNotEquals(
                "odrcore does not know the extension .$extension",
                FileType.UNKNOWN,
                Odr.fileTypeByFileExtension(extension),
            )
        }
    }

    /**
     * The other half of the fallback, and why it is kept apart: odrcore's extension table names one
     * suffix per format, so it answers UNKNOWN for the ooxml templates, macro enabled documents and
     * slideshows even though it renders all of them - a `.dotx` is the same wordprocessing package
     * as a `.docx`, and libmagic identifies it by content once the file is in the cache.
     *
     * Kept as an assertion rather than a comment so that a future core which does learn them turns
     * this red instead of leaving the split in place for no reason.
     */
    @Test
    fun theOoxmlVariantExtensionsAreOnesTheCoreDoesNotName() {
        for (extension in SupportedDocumentTypes.OOXML_VARIANT_EXTENSIONS) {
            Assert.assertEquals(
                "odrcore now knows .$extension - move it to CORE_EXTENSIONS",
                FileType.UNKNOWN,
                Odr.fileTypeByFileExtension(extension),
            )
        }
    }

    /**
     * The case the extension fallback exists for: a provider that volunteers nothing better than
     * `application/octet-stream`. The app goes by the name, and the manifest has to as well - it
     * claims `application/octet-stream` outright, so this is really a check that the claim stays.
     */
    @Test
    fun anOctetStreamWithAKnownExtensionReachesUs() {
        for (extension in SupportedDocumentTypes.EXTENSIONS) {
            Assert.assertTrue(
                "the app does not offer for document.$extension",
                SupportedDocumentTypes.isSupported(
                    "application/octet-stream",
                    "document.$extension",
                ),
            )
            Assert.assertTrue(
                "the manifest does not offer for document.$extension",
                resolvesToUs("application/octet-stream", "document.$extension"),
            )
        }
    }

    /**
     * A file manager that sends `ACTION_VIEW` with no mime type at all, which is the case the
     * `pathPattern` filters exist for - and the one an `application/octet-stream` check cannot
     * stand in for, because that mime type is claimed outright and so matches whatever the patterns
     * happen to say.
     *
     * This is what pins the pattern list against [SupportedDocumentTypes.EXTENSIONS]: leaving an
     * extension out of the manifest was invisible until now, and `.ott` had been missing from it
     * for as long as the table has claimed it.
     */
    @Test
    fun aFileWithNoMimeTypeReachesUsByItsExtension() {
        for (extension in SupportedDocumentTypes.EXTENSIONS) {
            Assert.assertTrue(
                "the manifest has no pathPattern for .$extension",
                resolvesToUs(null, "document.$extension"),
            )
        }
    }

    /** And the same route stays shut for what the app does not claim. */
    @Test
    fun aFileWithNoMimeTypeAndAnUnrelatedExtensionReachesNobody() {
        for (extension in listOf("vcf", "mp3", "mp4", "bin", "apk")) {
            Assert.assertFalse(
                "the manifest offers for .$extension",
                resolvesToUs(null, "document.$extension"),
            )
        }
    }

    /**
     * The spellings the mime *prefixes* carry along - the ooxml templates, slideshows and macro
     * enabled variants - which [theManifestClaimsExactlyWhatTheAppClaims] cannot reach: odrcore
     * names one canonical mime type per format, so its [FileType] walk never produces any of these.
     *
     * They still have to hold, because an intent-filter matches a mime type exactly. Without them
     * spelled out in the manifest a .dotx opened from the folder browser while the same file from
     * another app did not offer OpenDocument at all.
     */
    @Test
    fun theOoxmlVariantsReachUsToo() {
        val variants =
            listOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.openxmlformats-officedocument.presentationml.template",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-word.document.macroEnabled.12",
                "application/vnd.ms-word.template.macroEnabled.12",
                "application/vnd.ms-excel.sheet.macroEnabled.12",
                "application/vnd.ms-excel.template.macroEnabled.12",
                "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
                "application/vnd.ms-powerpoint.template.macroEnabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroEnabled.12",
            )

        for (mimeType in variants) {
            Assert.assertTrue(
                "the app does not offer for $mimeType",
                SupportedDocumentTypes.isSupported(mimeType, null),
            )
            Assert.assertTrue(
                "the manifest does not offer for $mimeType",
                resolvesToUs(mimeType, "document"),
            )
        }
    }

    /** What the app deliberately stays out of - the reason the catch-all filter defaults to off. */
    @Test
    fun unrelatedTypesReachNeither() {
        for (mimeType in listOf("text/vcard", "text/calendar", "audio/mpeg", "video/mp4")) {
            Assert.assertFalse(
                "the app should not offer for $mimeType",
                SupportedDocumentTypes.isSupported(mimeType, null),
            )
            Assert.assertFalse(
                "the manifest should not offer for $mimeType",
                resolvesToUs(mimeType, "document"),
            )
        }
    }

    /**
     * Whether an `ACTION_VIEW` for this mime type and filename lands on our own activity - the only
     * thing an intent-filter is actually good for, and the one question a hand written XML list can
     * still be asked.
     *
     * A null [mimeType] is the no-type intent a file manager sends, which only the `pathPattern`
     * filters can answer; `setData` rather than `setDataAndType`, because passing a null type to
     * the latter clears the data along with it.
     */
    private fun resolvesToUs(mimeType: String?, filename: String): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val uri = Uri.parse("content://app.opendocument.test/$filename")

        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                if (mimeType == null) setData(uri) else setDataAndType(uri, mimeType)
            }

        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName == context.packageName }
    }

    /** Null for the types odrcore has no mime type for - word perfect, rtf, cfb and the like. */
    private fun mimeTypeOrNull(fileType: FileType): String? =
        try {
            Odr.mimetypeByFileType(fileType)
        } catch (e: RuntimeException) {
            null
        }

    companion object {

        // @JvmStatic because junit requires @BeforeClass to be static
        @JvmStatic
        @BeforeClass
        fun prepare() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            // Odr's tables live in libodr_jni; nothing here opens a file, so the data paths that
            // initializeCore also sets up are along for the ride rather than needed
            CoreLoader.initializeCore(context)

            // STRICT_CATCH is what this test is about, and the component states survive a test run
            // - a previous one that flipped the switch would otherwise leave CATCH_ALL answering
            // for everything. False is the shipped default, so nothing has to be put back.
            CatchAllSetting.setEnabled(context, false)
        }
    }
}
