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

/**
 * Reads the document out loud, one paragraph at a time: [PageView] hands the text of the paragraph
 * at [lastParagraphIndex] back through [ParagraphListener], and every finished utterance asks for
 * the next one.
 */
// the utterance callback and the parameter map are the deprecated TextToSpeech API. Their
// replacements (UtteranceProgressListener, the Bundle overload) need the utterance to be queued
// with an id, which is a behaviour change rather than a rename - left for its own change.
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class TtsActionModeCallback(private val context: Context, private val pageView: PageView) :
    ActionMode.Callback, OnInitListener, ParagraphListener, OnUtteranceCompletedListener {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var menu: Menu
    private lateinit var statusView: TextView

    private var lastParagraphIndex = 0
    private val ttsParams = hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to "odr")
    private var paused = false

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

                textToSpeech.stop()

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

                textToSpeech.stop()

                lastParagraphIndex--
            }

            R.id.tts_next -> {
                statusView.setText(R.string.tts_status_reading)

                textToSpeech.stop()

                nextParagraph()
            }

            else -> return false
        }

        return true
    }

    @JavascriptInterface
    override fun paragraph(text: String?) {
        if (!text.isNullOrEmpty()) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, ttsParams)
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
        if (paused) {
            return
        }

        nextParagraph()
    }

    fun stop() {
        paused = true

        textToSpeech.stop()
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        paused = true

        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
