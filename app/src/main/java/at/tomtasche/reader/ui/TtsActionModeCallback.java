package at.tomtasche.reader.ui;

import java.util.HashMap;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import androidx.appcompat.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.widget.TextView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.PageView;

public class TtsActionModeCallback implements ActionMode.Callback,
		OnInitListener, ParagraphListener, OnUtteranceCompletedListener {

	private Context context;
	private PageView pageView;
	private TextToSpeech textToSpeech;
	private Menu menu;
	private TextView statusView;
	private int lastParagraphIndex = 0;
	private HashMap<String, String> ttsParams;
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
		statusView.setText("Initializing TTS...");
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
			statusView.setText("Ready!");

			textToSpeech.setOnUtteranceCompletedListener(this);

			menu.findItem(R.id.tts_play).setEnabled(true);
			menu.findItem(R.id.tts_pause).setEnabled(true);
			menu.findItem(R.id.tts_previous).setEnabled(true);
			menu.findItem(R.id.tts_next).setEnabled(true);
		} else {
			statusView.setText("TTS failed.");

			// TODO: download voices?
		}
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.tts_previous: {
			statusView.setText("Reading...");

			textToSpeech.stop();

			lastParagraphIndex -= 2;

			nextParagraph();

			break;
		}

		case R.id.tts_play: {
			if (!textToSpeech.isSpeaking()) {
				statusView.setText("Reading...");

				paused = false;

				nextParagraph();
			}

			break;
		}

		case R.id.tts_pause: {
			statusView.setText("Paused.");

			paused = true;

			textToSpeech.stop();

			lastParagraphIndex--;

			break;
		}

		case R.id.tts_next: {
			statusView.setText("Reading...");

			textToSpeech.stop();

			nextParagraph();

			break;
		}

		default:
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
				statusView.setText("Finished.");
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
