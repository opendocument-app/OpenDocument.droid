package app.opendocument.droid.background

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamUtilTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun copyStreamToStream() {
        val data = "hello world".toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()

        StreamUtil.copy(ByteArrayInputStream(data), out)

        Assert.assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun copyLargeStream() {
        // larger than the internal 1024 byte buffer
        val data = ByteArray(10000)
        for (i in data.indices) {
            data[i] = (i % 256).toByte()
        }
        val out = ByteArrayOutputStream()

        StreamUtil.copy(ByteArrayInputStream(data), out)

        Assert.assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun copyEmptyStream() {
        val out = ByteArrayOutputStream()

        StreamUtil.copy(ByteArrayInputStream(ByteArray(0)), out)

        Assert.assertEquals(0, out.size())
    }

    @Test
    fun copyStreamToFile() {
        val data = "file content".toByteArray(StandardCharsets.UTF_8)
        val target = temporaryFolder.newFile()

        StreamUtil.copy(ByteArrayInputStream(data), target)

        Assert.assertArrayEquals(data, Files.readAllBytes(target.toPath()))
    }

    @Test
    fun copyFileToFile() {
        val data = "copy me".toByteArray(StandardCharsets.UTF_8)
        val source = temporaryFolder.newFile()
        Files.write(source.toPath(), data)
        val target = temporaryFolder.newFile()

        StreamUtil.copy(source, target)

        Assert.assertArrayEquals(data, Files.readAllBytes(target.toPath()))
    }

    @Test
    fun readFullyReturnsUtf8String() {
        val text = "umlauts: äöü, emoji: 😀"
        val input: InputStream = ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8))

        Assert.assertEquals(text, StreamUtil.readFully(input))
    }

    @Test
    fun copyClosesInputStream() {
        val source = temporaryFolder.newFile()
        Files.write(source.toPath(), "x".toByteArray(StandardCharsets.UTF_8))
        val input = FileInputStream(source)

        StreamUtil.copy(input, ByteArrayOutputStream())

        try {
            input.read()
            Assert.fail("input stream should be closed after copy")
        } catch (expected: IOException) {
            // expected
        }
    }
}
