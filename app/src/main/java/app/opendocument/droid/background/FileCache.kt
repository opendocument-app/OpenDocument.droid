package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * The working copy of the document being read.
 *
 * A document arrives as a stream that is only readable while the grant lasts, so [store] copies it
 * in first and every step after that reads a file of ours.
 */
object FileCache {

    private const val CACHE_DIRECTORY_PREFIX = "cache."

    private var providerAuthority: String? = null

    private fun getProviderAuthority(context: Context): String {
        return providerAuthority
            ?: (context.packageName + ".provider").also { providerAuthority = it }
    }

    private fun getRootCacheDirectory(context: Context): File {
        val cache = File(context.cacheDir, "cache")
        if (!cache.exists()) {
            cache.mkdirs()
        }

        return cache
    }

    /**
     * Copies what [uri] points at into a fresh cache directory and returns the file, making room
     * first. A uri that already names a file of ours is handed straight back.
     *
     * @throws java.io.FileNotFoundException and the rest of what opening the stream can throw - a
     *   document that cannot be read has nothing further to be done with it.
     */
    fun store(context: Context, uri: Uri): File {
        cleanup(context)

        if (isCached(context, uri)) {
            return checkNotNull(getCacheFile(context, uri))
        }

        return createCacheFile(context).also { cacheFile ->
            val stream = context.contentResolver.openInputStream(uri)
            StreamUtil.copy(checkNotNull(stream) { "cannot open $uri" }, cacheFile)
        }
    }

    fun getCacheDirectory(cacheFile: File): File {
        // !!: reaching the filesystem root means the file was never below a cache directory
        val parentDirectory = cacheFile.parentFile!!
        if (!parentDirectory.name.startsWith(CACHE_DIRECTORY_PREFIX)) {
            return getCacheDirectory(parentDirectory)
        }

        return parentDirectory
    }

    private fun parseCacheFileName(path: String): String {
        return path.substring(path.indexOf(CACHE_DIRECTORY_PREFIX))
    }

    fun getCacheFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, getProviderAuthority(context), file)
    }

    fun isCached(context: Context, uri: Uri): Boolean {
        return uri.host == getProviderAuthority(context) &&
            uri.toString().contains(CACHE_DIRECTORY_PREFIX)
    }

    fun getCacheFile(context: Context, uri: Uri): File? {
        if (!isCached(context, uri)) {
            return null
        }

        val cacheFileString = parseCacheFileName(uri.toString())

        return File(getRootCacheDirectory(context), cacheFileString)
    }

    fun createCacheFile(context: Context): File {
        val cacheRoot = getRootCacheDirectory(context)
        val cacheDirectory = File(cacheRoot, CACHE_DIRECTORY_PREFIX + System.currentTimeMillis())

        cacheDirectory.mkdirs()

        return File(cacheDirectory, "cached-file.tmp")
    }

    /**
     * Deletes [file] along with the directory [createCacheFile] made for it - [cleanup] keeps
     * whichever sorts last, so an empty leftover would be kept in place of the open document.
     */
    fun deleteCacheFile(file: File) {
        file.delete()

        // delete() on a directory only succeeds while it is empty, which is the intent
        file.parentFile?.takeIf { it.name.startsWith(CACHE_DIRECTORY_PREFIX) }?.delete()
    }

    /** Drops every cache directory but the newest, which is the document still open. */
    private fun cleanup(context: Context) {
        val cache = getRootCacheDirectory(context)
        val directories =
            cache.list { _, name -> name.startsWith(CACHE_DIRECTORY_PREFIX) } ?: return

        directories.sort()
        // delete all but the last cache directories!
        for (i in 0 until directories.size - 1) {
            cleanup(File(cache, directories[i]))
        }
    }

    private fun cleanup(directory: File) {
        val files = directory.list() ?: return

        for (name in files) {
            try {
                val file = File(directory, name)
                if (file.isDirectory) {
                    cleanup(file)
                } else {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            directory.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
