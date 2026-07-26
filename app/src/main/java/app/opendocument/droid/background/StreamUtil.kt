package app.opendocument.droid.background

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object StreamUtil {

    const val ENCODING: String = "UTF-8"

    fun copy(src: File, dst: File) {
        copy(FileInputStream(src), dst)
    }

    fun copy(src: File, out: OutputStream) {
        copy(FileInputStream(src), out)
    }

    fun copy(input: InputStream, dst: File) {
        FileOutputStream(dst).use { out -> copy(input, out) }
    }

    fun readFully(input: InputStream): String {
        val out = ByteArrayOutputStream()
        copy(input, out)

        return out.toString(ENCODING)
    }

    /** Closes the input, never the output - the caller owns that one. */
    fun copy(input: InputStream, out: OutputStream) {
        input.use {
            it.copyTo(out)
            out.flush()
        }
    }
}
