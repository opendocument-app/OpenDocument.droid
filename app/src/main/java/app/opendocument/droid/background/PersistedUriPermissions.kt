package app.opendocument.droid.background

import android.content.Context
import android.content.Intent
import android.content.UriPermission
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
     * The grants taken during this process that nothing on disk names yet.
     *
     * Taking a grant and writing down what needs it are not one step: a document only reaches the
     * recent list once [MetadataLoader] has read it, which is long after [takeRead] ran - `loadUri`
     * merely queues the load - and a folder only reaches the tree list once the landing view
     * model's executor gets to it. A [prune] in either window would enumerate the fresh grant, find
     * nothing referring to it and hand it straight back, leaving the entry that is about to be
     * written unreadable on the next launch - the very failure releasing on close used to cause.
     *
     * Empty on a fresh process, so a grant whose document never made it onto a list is reclaimed on
     * the next launch rather than leaking for good.
     */
    private val pendingGrants = HashSet<String>()

    /**
     * Takes a persistable read permission for [uri].
     *
     * @return whether a persisted grant is held afterwards. False for providers that do not offer
     *   persistable permissions at all, and for uris that did not arrive on an intent of ours.
     */
    fun takeRead(context: Context, uri: Uri): Boolean {
        // marked before the grant exists rather than after, so there is no instant in which a
        // concurrent prune could enumerate it while nothing speaks for it. a uri that turns out not
        // to be persistable is left in the set: we are reading it either way, and if an earlier
        // session did persist it, that grant is exactly the one to hold on to
        markPending(uri.toString())

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
        // the reads are in this order on purpose. a grant taken while this runs is either missing
        // from the snapshot, or still pending when the pending set is read - which happens after
        // the lists that would have settled it, so it cannot fall through both
        val held = context.contentResolver.persistedUriPermissions

        val keep = HashSet<String>()
        for (entry in RecentDocumentsUtil.getRecentDocuments(context)) {
            keep.add(entry.uri)
        }

        val trees = FolderTreesUtil.getFolderTrees(context).map { it.uri.toString() }
        keep.addAll(trees)

        keep.addAll(settlePending(keep))

        for (permission in held) {
            if (!isReferenced(permission.uri.toString(), keep, trees)) {
                release(context, permission)
            }
        }
    }

    /**
     * Whether [uri] is still spoken for, either by name or by sitting below a granted tree.
     *
     * [prune]'s whole decision, and free of android imports, so a plain jvm test can cover it.
     */
    internal fun isReferenced(uri: String, keep: Set<String>, trees: List<String>): Boolean {
        if (uri in keep) {
            return true
        }

        // a document below a granted tree is reachable through that grant, and its uri spells
        // the tree out: content://authority/tree/<treeId>/document/<documentId>. so coverage
        // can be recognised from the string, without recording where a document came from.
        return trees.any { uri.startsWith("$it/") }
    }

    private fun release(context: Context, permission: UriPermission) {
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

    /** Remembers a grant until one of the stored lists names it. */
    @Synchronized
    internal fun markPending(uri: String) {
        pendingGrants.add(uri)
    }

    /**
     * Drops the pending grants [keep] has caught up with - those are reclaimable the ordinary way
     * from now on - and hands back the ones that are still in flight.
     */
    @Synchronized
    internal fun settlePending(keep: Set<String>): Set<String> {
        pendingGrants.removeAll(keep)

        return HashSet(pendingGrants)
    }
}
