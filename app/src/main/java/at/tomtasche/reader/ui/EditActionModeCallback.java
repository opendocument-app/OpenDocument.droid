package at.tomtasche.reader.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.io.File;

import androidx.appcompat.view.ActionMode;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.ui.activity.DocumentFragment;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.PageView;

public class EditActionModeCallback implements ActionMode.Callback {

    public static int PERMISSION_CODE = 21874;

    private MainActivity activity;
    private DocumentFragment documentFragment;
    private AdManager adManager;
    private PageView pageView;
    private TextView statusView;

    private InputMethodManager imm;

    public EditActionModeCallback(MainActivity activity, DocumentFragment documentFragment, AdManager adManager) {
        this.activity = activity;
        this.documentFragment = documentFragment;
        this.adManager = adManager;
        this.pageView = documentFragment.getPageView();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        statusView = new TextView(activity);
        statusView.setText(R.string.action_edit_banner);
        mode.setCustomView(statusView);

        mode.getMenuInflater().inflate(R.menu.edit, menu);

        imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // reload document with translation enabled
        documentFragment.reloadUri(false, true);

        imm.toggleSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.edit_help: {
                documentFragment.startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://plus.google.com/communities/113494011673882132018")));

                break;
            }

            case R.id.edit_save: {
                adManager.showInterstitial();

                save();

                break;
            }

            default:
                return false;
        }

        return true;
    }

    public void save() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_CODE);
        } else {
            final File htmlFile = new File(AndroidFileCache.getCacheDirectory(activity), "content.html");
            pageView.requestHtml(htmlFile, new Runnable() {

                @Override
                public void run() {
                    documentFragment.save(htmlFile);

                    pageView.post(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Document saved to your SD card");
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        imm.toggleSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), 0, 0);

        documentFragment.reloadUri(false, false);
    }
}
