package at.tomtasche.reader.ui;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.widget.TextView;

import java.util.HashMap;

import androidx.appcompat.view.ActionMode;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.PageView;

public class TtsActionModeCallback implements ActionMode.Callback,
        OnInitListener, ParagraphListener, OnUtteranceCompletedListener {

    private final Context context;
    private final PageView pageView;
    private TextToSpeech textToSpeech;
    private Menu menu;
    private TextView statusView;
    private int lastParagraphIndex = 0;
    private final HashMap<String, String> ttsParams;
    private boolean paused;

    public TtsActionModeCallback(Context context, PageView pageView) {
        this.context = context;
        this.pageView = pageView;

        ttsParams = new HashMap<String, String>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "odr");
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        statusView = new TextView(context);
        statusView.setText(R.string.tts_status_initializing);
        mode.setCustomView(statusView);

        mode.getMenuInflater().inflate(R.menu.tts, menu);

        this.menu = menu;

        pageView.setParagraphListener(this);

        textToSpeech = new TextToSpeech(context, this);

        return true;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            statusView.setText(R.string.tts_status_ready);

            textToSpeech.setOnUtteranceCompletedListener(this);

            menu.findItem(R.id.tts_play).setEnabled(true);
            menu.findItem(R.id.tts_pause).setEnabled(true);
            menu.findItem(R.id.tts_previous).setEnabled(true);
            menu.findItem(R.id.tts_next).setEnabled(true);
        } else {
            statusView.setText(R.string.tts_status_failed);

            // TODO: download voices?
        }
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.tts_previous) {
            statusView.setText(R.string.tts_status_reading);

            textToSpeech.stop();

            lastParagraphIndex -= 2;

            nextParagraph();
        } else if (itemId == R.id.tts_play) {
            if (!textToSpeech.isSpeaking()) {
                statusView.setText(R.string.tts_status_reading);

                paused = false;

                nextParagraph();
            }
        } else if (itemId == R.id.tts_pause) {
            statusView.setText(R.string.tts_status_paused);

            paused = true;

            textToSpeech.stop();

            lastParagraphIndex--;
        } else if (itemId == R.id.tts_next) {
            statusView.setText(R.string.tts_status_reading);

            textToSpeech.stop();

            nextParagraph();
        } else {
            return false;
        }

        return true;
    }

    @Override
    @JavascriptInterface
    public void paragraph(String text) {
        if (text != null && text.length() > 0) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, ttsParams);
        } else {
            nextParagraph();
        }
    }

    @Override
    @JavascriptInterface
    public void increaseIndex() {
        nextParagraph();
    }

    private void nextParagraph() {
        pageView.getParagraph(lastParagraphIndex++);
    }

    @Override
    @JavascriptInterface
    public void end() {
        pageView.post(new Runnable() {

            @Override
            public void run() {
                statusView.setText(R.string.tts_status_finished);
            }
        });
    }

    @Override
    public void onUtteranceCompleted(String utteranceId) {
        if (paused) {
            return;
        }

        nextParagraph();
    }

    public void stop() {
        paused = true;

        textToSpeech.stop();
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        paused = true;

        textToSpeech.stop();
        textToSpeech.shutdown();
    }
}
