package at.tomtasche.reader.ui

/**
 * Implemented by the TTS action mode and forwarded by [at.tomtasche.reader.ui.widget.PageView]. The
 * calls originate in the page's javascript, so [text] can be null.
 */
interface ParagraphListener {

    fun paragraph(text: String?)

    fun increaseIndex()

    fun end()
}
