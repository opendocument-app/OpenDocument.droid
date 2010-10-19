package at.tomtasche.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import at.tomtasche.reader.odt.OdtParser;

// author: tomtasche@gmail.com
// http://tomtasche.at/
public class Parser {
	private static final String[] POSTFIXES = {"odt", "doc", "txt", "xml", "java", "html"};

	// i think .docx would be possible too with POI-API

	public static void parse(final InputStream fileStream, final ByteArrayOutputStream resultStream) throws IOException {
		final OdtParser odtParser = new OdtParser(fileStream);
		odtParser.parse(resultStream);
	}
}