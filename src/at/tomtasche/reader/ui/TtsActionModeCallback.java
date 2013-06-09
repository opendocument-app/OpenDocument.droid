package at.tomtasche.reader.ui;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.widget.TextView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.PageView;
import at.tomtasche.reader.ui.widget.PageView.ParagraphListener;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class TtsActionModeCallback implements ActionMode.Callback,
		OnInitListener, ParagraphListener {

	private Context context;
	private PageView pageView;
	private TextToSpeech textToSpeech;
	private Menu menu;
	private TextView statusView;
	private boolean enqueued;

	public TtsActionModeCallback(Context context, PageView pageView) {
		this.context = context;
		this.pageView = pageView;
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		statusView = new TextView(context);
		statusView.setText("Initializing TTS...");
		mode.setCustomView(statusView);

		mode.getMenuInflater().inflate(R.menu.tts, menu);

		this.menu = menu;

		textToSpeech = new TextToSpeech(context, this);

		return true;
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			statusView.setText("Ready!");
			menu.findItem(R.id.tts_play).setEnabled(true);
			// menu.findItem(R.id.tts_pause).setEnabled(true);
			// menu.findItem(R.id.tts_previous).setEnabled(true);
			// menu.findItem(R.id.tts_next).setEnabled(true);
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
		case R.id.tts_play:
			statusView.setText("Reading...");

			if (!enqueued) {
				enqueued = true;

				pageView.getParagraphs(this);
			}

			break;

		// case R.id.tts_pause:
		// statusView.setText("Paused.");
		//
		// break;
		//
		// case R.id.tts_next:
		// statusView.setText("Reading...");
		//
		// break;

		default:
			return false;
		}

		return true;
	}

	@Override
	public void paragraph(String text) {
		textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null);
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		textToSpeech.stop();
		textToSpeech.shutdown();
	}
}
