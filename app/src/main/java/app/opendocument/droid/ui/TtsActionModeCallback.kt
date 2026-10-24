package app.opendocument.droid.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import app.opendocument.droid.R
import app.opendocument.droid.ui.widget.PageView
import java.text.BreakIterator
import java.util.Locale

/**
 * Reads the document out loud, one paragraph at a time: [PageView] hands the text of the paragraph
 * at [lastParagraphIndex] back through [ParagraphListener], and every finished utterance asks for
 * the next one. [locale] is the document's language as a BCP 47 tag, read in where a voice has it.
 */
// the replacements for the deprecated utterance callback and parameter map need the utterance
// queued with an id, which is a behaviour change rather than a rename
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class TtsActionModeCallback(
    private val context: Context,
    private val pageView: PageView,
    private val locale: String?,
) : ActionMode.Callback, OnInitListener, ParagraphListener, OnUtteranceCompletedListener {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var menu: Menu
    private lateinit var statusView: TextView

    private var lastParagraphIndex = 0
    private var paragraphCount = 0
    private var paused = false

    // the id of the paragraph's last part. Stopping reports the utterance as completed too, later
    // and from another thread, so this is cleared first and a stopped paragraph does not advance
    @Volatile private var awaitedUtteranceId: String? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        statusView = TextView(context)
        statusView.setText(R.string.tts_status_initializing)
        mode.customView = statusView

        mode.menuInflater.inflate(R.menu.tts, menu)

        this.menu = menu

        pageView.setParagraphListener(this)

        textToSpeech = TextToSpeech(context, this)

        return true
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            statusView.setText(R.string.tts_status_ready)

            textToSpeech.setOnUtteranceCompletedListener(this)

            // otherwise the engine's own language, which is the phone's rather than the document's
            locale
                ?.let { Locale.forLanguageTag(it) }
                ?.takeIf { textToSpeech.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                ?.let { textToSpeech.setLanguage(it) }

            menu.findItem(R.id.tts_play).isEnabled = true
            menu.findItem(R.id.tts_pause).isEnabled = true
            menu.findItem(R.id.tts_previous).isEnabled = true
            menu.findItem(R.id.tts_next).isEnabled = true
        } else {
            statusView.setText(R.string.tts_status_failed)

            // TODO: download voices?
        }
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.tts_previous -> {
                statusView.setText(R.string.tts_status_reading)

                silence()

                lastParagraphIndex -= 2

                nextParagraph()
            }

            R.id.tts_play -> {
                if (!textToSpeech.isSpeaking) {
                    statusView.setText(R.string.tts_status_reading)

                    paused = false

                    nextParagraph()
                }
            }

            R.id.tts_pause -> {
                statusView.setText(R.string.tts_status_paused)

                paused = true

                silence()

                lastParagraphIndex--
            }

            R.id.tts_next -> {
                statusView.setText(R.string.tts_status_reading)

                silence()

                nextParagraph()
            }

            else -> return false
        }

        return true
    }

    @JavascriptInterface
    override fun paragraph(text: String?) {
        if (!text.isNullOrBlank()) {
            // the engine rejects longer text, and a rejection completes like a spoken utterance
            val parts = split(text, TextToSpeech.getMaxSpeechInputLength())
            val lastId = "odr-${++paragraphCount}"
            awaitedUtteranceId = lastId
            parts.forEachIndexed { i, part ->
                val id = if (i == parts.lastIndex) lastId else "$lastId-$i"
                val params = hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to id)
                textToSpeech.speak(part, TextToSpeech.QUEUE_ADD, params)
            }
        } else {
            nextParagraph()
        }
    }

    @JavascriptInterface
    override fun increaseIndex() {
        nextParagraph()
    }

    private fun nextParagraph() {
        pageView.getParagraph(lastParagraphIndex++)
    }

    @JavascriptInterface
    override fun end() {
        pageView.post { statusView.setText(R.string.tts_status_finished) }
    }

    override fun onUtteranceCompleted(utteranceId: String?) {
        if (paused || utteranceId != awaitedUtteranceId) {
            return
        }

        awaitedUtteranceId = null
        nextParagraph()
    }

    fun stop() {
        paused = true

        silence()
    }

    private fun silence() {
        awaitedUtteranceId = null
        textToSpeech.stop()
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        paused = true
        pageView.setParagraphListener(null)

        silence()
        textToSpeech.shutdown()
    }

    /** Splits [text] into parts of at most [limit] characters, at sentences or words if it can. */
    private fun split(text: String, limit: Int): List<String> {
        val parts = mutableListOf<String>()
        val sentences = BreakIterator.getSentenceInstance(language())
        sentences.setText(text)
        var start = 0
        while (text.length - start > limit) {
            var end = sentences.preceding(start + limit + 1)
            if (end <= start) {
                end = text.lastIndexOf(' ', start + limit).takeIf { it > start } ?: (start + limit)
            }
            parts += text.substring(start, end)
            start = end
        }
        parts += text.substring(start)
        return parts
    }

    private fun language(): Locale =
        locale?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
}
