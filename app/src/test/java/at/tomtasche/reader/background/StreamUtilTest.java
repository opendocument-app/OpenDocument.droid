package at.tomtasche.reader.background;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StreamUtilTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copyStreamToStream() throws IOException {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamUtil.copy(new ByteArrayInputStream(data), out);

        Assert.assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void copyLargeStream() throws IOException {
        // larger than the internal 1024 byte buffer
        byte[] data = new byte[10000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamUtil.copy(new ByteArrayInputStream(data), out);

        Assert.assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void copyEmptyStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamUtil.copy(new ByteArrayInputStream(new byte[0]), out);

        Assert.assertEquals(0, out.size());
    }

    @Test
    public void copyStreamToFile() throws IOException {
        byte[] data = "file content".getBytes(StandardCharsets.UTF_8);
        File target = temporaryFolder.newFile();

        StreamUtil.copy(new ByteArrayInputStream(data), target);

        Assert.assertArrayEquals(data, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void copyFileToFile() throws IOException {
        byte[] data = "copy me".getBytes(StandardCharsets.UTF_8);
        File source = temporaryFolder.newFile();
        Files.write(source.toPath(), data);
        File target = temporaryFolder.newFile();

        StreamUtil.copy(source, target);

        Assert.assertArrayEquals(data, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void readFullyReturnsUtf8String() throws IOException {
        String text = "umlauts: äöü, emoji: 😀";
        InputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        Assert.assertEquals(text, StreamUtil.readFully(in));
    }

    @Test
    public void copyClosesInputStream() throws IOException {
        File source = temporaryFolder.newFile();
        Files.write(source.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        FileInputStream in = new FileInputStream(source);

        StreamUtil.copy(in, new ByteArrayOutputStream());

        try {
            in.read();
            Assert.fail("input stream should be closed after copy");
        } catch (IOException expected) {
            // expected
        }
    }
}
