package at.tomtasche.reader.ui;

public interface ParagraphListener {

    void paragraph(String text);

    void increaseIndex();

    void end();
}
