package app.opendocument.droid.background

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The persisted uri permissions the app holds on to.
 *
 * A document picked through the storage access framework is only readable for as long as we hold a
 * grant for it. Since the recently opened documents list hands those uris back on a later launch,
 * the grants have to outlive the process - they are reclaimed by [prune] once nothing refers to
 * them any more, rather than being released when the document is closed.
 */
object PersistedUriPermissions {

    private const val READ_FLAG = Intent.FLAG_GRANT_READ_URI_PERMISSION

    /**
     * Takes a persistable read permission for [uri].
     *
     * @return whether a persisted grant is held afterwards. False for providers that do not offer
     *   persistable permissions at all, and for uris that did not arrive on an intent of ours.
     */
    fun takeRead(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(uri, READ_FLAG)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Whether [uri] is readable through a grant that survives a restart, either because it is
     * persisted itself or because it sits below a directory tree we hold a grant for.
     */
    fun isRetained(context: Context, uri: Uri): Boolean {
        val value = uri.toString()

        for (permission in context.contentResolver.persistedUriPermissions) {
            if (!permission.isReadPermission) {
                continue
            }

            val held = permission.uri.toString()
            if (held == value || value.startsWith("$held/")) {
                return true
            }
        }

        return false
    }

    /**
     * Releases every persisted grant nothing refers to any more.
     *
     * Reconciling against the stored lists rather than releasing on eviction keeps this idempotent:
     * a grant that is covered twice is not released twice, and grants leaked by earlier versions
     * get mopped up on the next launch.
     *
     * Does file and binder work, so it must not run on the main thread.
     */
    fun prune(context: Context) {
        val keep = HashSet<String>()
        for (entry in RecentDocumentsUtil.getRecentDocuments(context)) {
            keep.add(entry.uri)
        }

        for (permission in context.contentResolver.persistedUriPermissions) {
            val held = permission.uri.toString()
            if (held in keep) {
                continue
            }

            var flags = 0
            if (permission.isReadPermission) {
                flags = flags or READ_FLAG
            }
            if (permission.isWritePermission) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

            try {
                // releasing with flags we do not hold throws, hence taking them off the grant
                context.contentResolver.releasePersistableUriPermission(permission.uri, flags)
            } catch (e: Exception) {
                // nothing to do about it, and it will be retried on the next launch
            }
        }
    }
}
