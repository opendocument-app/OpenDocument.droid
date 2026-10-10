package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files

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
     * Copies [uri] into a fresh cache directory, or reuses an existing cached file. Read failures
     * propagate to the caller.
     */
    fun store(context: Context, uri: Uri): File {
        if (isCached(context, uri)) {
            return checkNotNull(getCacheFile(context, uri))
        }

        cleanup(context)

        val cacheFile = createCacheFile(context)
        try {
            val stream = context.contentResolver.openInputStream(uri)
            StreamUtil.copy(checkNotNull(stream) { "cannot open $uri" }, cacheFile)
            return cacheFile
        } catch (e: Throwable) {
            deleteCacheFile(cacheFile)
            throw e
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

    fun getCacheFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, getProviderAuthority(context), file)
    }

    fun isCached(context: Context, uri: Uri): Boolean = getCacheFile(context, uri) != null

    fun getCacheFile(context: Context, uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != getProviderAuthority(context)) {
            return null
        }

        val segments = uri.pathSegments
        if (
            segments.size < 4 ||
                segments.take(2) != listOf("cache", "cache") ||
                !segments[2].startsWith(CACHE_DIRECTORY_PREFIX)
        ) {
            return null
        }

        val root = getRootCacheDirectory(context).canonicalFile
        val directory = File(root, segments[2]).canonicalFile
        val file = File(root, segments.drop(2).joinToString("/")).canonicalFile
        return file.takeIf {
            directory.parentFile == root && file.startsWith(directory) && file != directory
        }
    }

    fun createCacheFile(context: Context): File {
        val directory =
            Files.createTempDirectory(
                getRootCacheDirectory(context).toPath(),
                CACHE_DIRECTORY_PREFIX + System.currentTimeMillis() + ".",
            )
        return directory.resolve("cached-file.tmp").toFile()
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
