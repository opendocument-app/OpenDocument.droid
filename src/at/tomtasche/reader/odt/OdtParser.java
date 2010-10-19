package at.tomtasche.reader.odt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import at.tomtasche.reader.FileOpener;

public class OdtParser {
	private final InputStream inStream;

	public OdtParser(final InputStream file) {
		inStream = file;
	}

	public void parse(final ByteArrayOutputStream resultStream) throws IOException {
		final ZipInputStream odt = new ZipInputStream(inStream);

		ZipEntry entry = odt.getNextEntry();
		while (entry != null) {
			if ("content.xml".equals(entry.getName())) {
				break;
			}

			odt.closeEntry();
			entry = odt.getNextEntry();
		}

		if (entry == null) {
			return;
		}

		final ByteArrayOutputStream parseStream = new ByteArrayOutputStream();
		for (int i = 0; i >= 0; i = odt.read()) {
			parseStream.write(i);
		}
		parseStream.close();

		Dejunker.parse(parseStream.toString(FileOpener.ENCODING), resultStream);
		resultStream.flush();

		try {
			resultStream.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		try {
			odt.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
}