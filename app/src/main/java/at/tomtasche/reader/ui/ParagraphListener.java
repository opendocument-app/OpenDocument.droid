package at.tomtasche.reader.ui;

public interface ParagraphListener {

    public void paragraph(String text);

    public void increaseIndex();

    public void end();
}
