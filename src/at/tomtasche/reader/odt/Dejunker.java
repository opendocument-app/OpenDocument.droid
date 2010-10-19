package at.tomtasche.reader.odt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import at.tomtasche.reader.FileOpener;

public class Dejunker {
	private static final String PARAGRAPH_OPEN = "<text:p";
	private static final String PARAGRAPH_CLOSE = "</text:p>";

	private static void findParagraphs(String s, final ByteArrayOutputStream resultStream) throws IOException {
		String temp = s.substring(0, s.indexOf(PARAGRAPH_OPEN));
		s = s.substring(s.indexOf(PARAGRAPH_OPEN));
		temp = deJunk(temp).trim();
		if (temp.length() > 0) {
			resultStream.write(temp.getBytes(FileOpener.ENCODING));
			temp = "";
		}

		while (s.contains(PARAGRAPH_CLOSE)) {
			int indexOpen = s.indexOf(PARAGRAPH_OPEN);
			int indexClose = s.indexOf(PARAGRAPH_CLOSE, s.indexOf(PARAGRAPH_OPEN));

			if (s.indexOf("/>", indexOpen + 2) < indexClose) {
				resultStream.write(FileOpener.NEW_LINE.getBytes(FileOpener.ENCODING));
			}

			if (indexOpen > 0) {
				temp = s.substring(indexOpen);
				s = s.substring(s.indexOf(PARAGRAPH_OPEN));
				temp = deJunk(temp);
				resultStream.write(temp.getBytes(FileOpener.ENCODING));

				indexOpen = s.indexOf(PARAGRAPH_OPEN);
				indexClose = s.indexOf(PARAGRAPH_CLOSE, s.indexOf(PARAGRAPH_OPEN));
			}

			final String dejunked = deJunk(s.substring(0, indexClose + PARAGRAPH_CLOSE.length()));
			final String finalString = dejunked + FileOpener.NEW_LINE;
			resultStream.write(finalString.getBytes(FileOpener.ENCODING));
			s = s.substring(indexClose + PARAGRAPH_CLOSE.length());
			temp = "";
		}
	}

	private static String deJunk(String s) {
		String result = "";

		while (s.contains("<")) {
			if (s.startsWith("<")) {
				final int index = s.indexOf('>');
				s = s.substring(index + 1);
			} else {
				final int index = s.indexOf('<');
				result += s.substring(0, index);
				s = s.substring(index);
			}
		}

		return result;
	}

	public static void parse(final String content, final ByteArrayOutputStream resultStream) throws IOException {
		findParagraphs(content, resultStream);
	}
}