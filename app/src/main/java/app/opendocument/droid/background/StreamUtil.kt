package app.opendocument.droid.background

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** @JvmStatic throughout, because the java callers still spell these as static calls. */
object StreamUtil {

    const val ENCODING: String = "UTF-8"

    @JvmStatic
    @Throws(IOException::class)
    fun copy(src: File, dst: File) {
        copy(FileInputStream(src), dst)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(src: File, out: OutputStream) {
        copy(FileInputStream(src), out)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(input: InputStream, dst: File) {
        FileOutputStream(dst).use { out -> copy(input, out) }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFully(input: InputStream): String {
        val out = ByteArrayOutputStream()
        copy(input, out)

        return out.toString(ENCODING)
    }

    /** Closes the input, never the output - the caller owns that one. */
    @JvmStatic
    @Throws(IOException::class)
    fun copy(input: InputStream, out: OutputStream) {
        input.use {
            it.copyTo(out)
            out.flush()
        }
    }
}
