package app.opendocument.droid.background

import org.junit.Assert
import org.junit.Test

/**
 * Covers the bookkeeping that keeps a grant alive between taking it and the recently opened list
 * naming it. Taking and releasing the grants themselves needs a content resolver, so that part is
 * device work.
 */
class PersistedUriPermissionsTest {

    @Test
    fun keepsAGrantUntilTheListNamesIt() {
        val uri = "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D5"

        PersistedUriPermissions.markPending(uri)

        // the document is still being read, so nothing on disk points at the grant yet
        Assert.assertTrue(uri in PersistedUriPermissions.settlePending(emptySet()))

        // and once the entry is written, the ordinary reconciliation takes over
        Assert.assertFalse(uri in PersistedUriPermissions.settlePending(setOf(uri)))
        Assert.assertFalse(uri in PersistedUriPermissions.settlePending(emptySet()))
    }

    @Test
    fun leavesGrantsItDidNotTakeAlone() {
        // what an earlier process left behind is nobody's in flight document, and prune reclaiming
        // those is the whole point of reconciling rather than bookkeeping every removal
        val leaked = "content://com.android.providers.downloads.documents/document/17"

        Assert.assertFalse(leaked in PersistedUriPermissions.settlePending(emptySet()))
    }
}
