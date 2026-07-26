package at.tomtasche.reader.background

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** @JvmStatic throughout, because the java callers still spell these as static calls. */
object AndroidFileCache {

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

    @JvmStatic
    fun getCacheDirectory(cacheFile: File): File {
        // !! rather than a graceful fallback: reaching the filesystem root means the file was
        // never below a cache directory, which the java version blew up on just as loudly
        val parentDirectory = cacheFile.parentFile!!
        if (!parentDirectory.name.startsWith(CACHE_DIRECTORY_PREFIX)) {
            return getCacheDirectory(parentDirectory)
        }

        return parentDirectory
    }

    private fun parseCacheFileName(path: String): String {
        return path.substring(path.indexOf(CACHE_DIRECTORY_PREFIX))
    }

    @JvmStatic
    fun getCacheFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, getProviderAuthority(context), file)
    }

    @JvmStatic
    fun isCached(context: Context, uri: Uri): Boolean {
        return uri.host == getProviderAuthority(context) &&
            uri.toString().contains(CACHE_DIRECTORY_PREFIX)
    }

    @JvmStatic
    fun getCacheFile(context: Context, uri: Uri): File? {
        if (!isCached(context, uri)) {
            return null
        }

        val cacheFileString = parseCacheFileName(uri.toString())

        return File(getRootCacheDirectory(context), cacheFileString)
    }

    @JvmStatic
    fun createCacheFile(context: Context): File {
        val cacheRoot = getRootCacheDirectory(context)
        val cacheDirectory = File(cacheRoot, CACHE_DIRECTORY_PREFIX + System.currentTimeMillis())

        cacheDirectory.mkdirs()

        return File(cacheDirectory, "cached-file.tmp")
    }

    @JvmStatic
    fun cleanup(context: Context) {
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
