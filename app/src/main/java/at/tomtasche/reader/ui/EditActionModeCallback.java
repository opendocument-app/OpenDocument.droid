package at.tomtasche.reader.ui;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.appcompat.view.ActionMode;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.DocumentFragment;
import at.tomtasche.reader.ui.activity.MainActivity;

public class EditActionModeCallback implements ActionMode.Callback {

    private final MainActivity activity;
    private final DocumentFragment documentFragment;

    private InputMethodManager imm;

    public EditActionModeCallback(MainActivity activity, DocumentFragment documentFragment) {
        this.activity = activity;
        this.documentFragment = documentFragment;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        TextView statusView = new TextView(activity);
        statusView.setText(R.string.action_edit_banner);
        mode.setCustomView(statusView);

        mode.getMenuInflater().inflate(R.menu.edit, menu);

        imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // reload document with translation enabled
        documentFragment.reloadUri(true);

        imm.toggleSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.edit_save) {
            documentFragment.prepareSave(new Runnable() {
                @Override
                public void run() {
                    activity.requestSave();
                }
            }, false);
        } else {
            return false;
        }

        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        imm.toggleSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), 0, 0);

        documentFragment.reloadUri(false);
    }
}
