package app.opendocument.droid.test

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.core.Odr
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.SupportedDocumentTypes
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keeps the two remaining declarations of what the app opens in step: [SupportedDocumentTypes],
 * which reads odrcore's format table, and the STRICT_CATCH `activity-alias` in AndroidManifest.xml,
 * which is XML and cannot read it - but can be *asked*, which is what [resolvesToUs] does.
 *
 * So neither side needs a list of formats of its own: the test walks every mime type spelling
 * odrcore accepts and demands the same answer from both.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class SupportedFormatsTest {

    /**
     * For every mime type odrcore accepts, what the app offers itself for and what the manifest
     * offers it for have to be the same answer.
     *
     * This is the assertion that catches a format being added to the loader without being added to
     * the manifest, which is the failure the user sees as "the app is not in the share sheet".
     */
    @Test
    fun theManifestClaimsExactlyWhatTheAppClaims() {
        var checked = 0

        for (fileType in Odr.allFileTypes()) {
            for (mimeType in Odr.mimetypesByFileType(fileType)) {
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
        }

        // a guard on the guard: if the core ever stops naming mime types the loop above would
        // silently assert nothing at all
        Assert.assertTrue("only $checked mime types were checked", checked > 50)
    }

    /**
     * The app never claims a format the core cannot decode, which is the invariant the old prefix
     * list could not hold: `application/vnd.ms-excel.sheet.binary.macroEnabled.12` starts like an
     * excel mime type, so a `.xlsb` was offered for and then failed to open. odrcore 6.1 gives it a
     * file type of its own with an empty capability row, and the app follows that instead.
     */
    @Test
    fun whatTheCoreCannotDecodeIsNotClaimed() {
        for (fileType in Odr.allFileTypes()) {
            if (Odr.capabilitiesByFileType(fileType).open) {
                continue
            }

            for (mimeType in Odr.mimetypesByFileType(fileType)) {
                Assert.assertFalse(
                    "$fileType ($mimeType) has no decoder, but the core loader claims it",
                    SupportedDocumentTypes.isRenderedByCore(mimeType),
                )
            }
        }
    }

    /**
     * Everything the app offers itself for is something the core renders. Claiming a mime type
     * nothing loads is the "cannot open" the user gets on a file they picked us for, so
     * `CLAIMED_FILE_TYPES` and `CORE_FILE_TYPES` must not come apart.
     */
    @Test
    fun everythingTheAppClaimsIsLoadedByTheCore() {
        for (mimeType in SupportedDocumentTypes.MIME_TYPES) {
            val canonical = SupportedDocumentTypes.canonicalMimeType(mimeType)

            Assert.assertTrue(
                "$mimeType is claimed by the app, but the core does not render it" +
                    " (as $canonical)",
                SupportedDocumentTypes.isRenderedByCore(canonical),
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
     * extension out of the manifest was invisible until a test asked for it, and `.ott` had been
     * missing for as long as the table had claimed it.
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
        for (extension in listOf("vcf", "mp3", "mp4", "bin", "apk", "xlsb")) {
            Assert.assertFalse(
                "the manifest offers for .$extension",
                resolvesToUs(null, "document.$extension"),
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

    companion object {

        // @JvmStatic because junit requires @BeforeClass to be static
        @JvmStatic
        @BeforeClass
        fun prepare() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            // STRICT_CATCH is what this test is about, and the component states survive a test run
            // - a previous one that flipped the switch would otherwise leave CATCH_ALL answering
            // for everything. False is the shipped default, so nothing has to be put back.
            CatchAllSetting.setEnabled(context, false)
        }
    }
}
