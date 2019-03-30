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
import at.tomtasche.reader.nonfree.HelpManager;
import at.tomtasche.reader.ui.activity.DocumentFragment;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.PageView;

public class EditActionModeCallback implements ActionMode.Callback {

    public static int PERMISSION_CODE = 21874;

    private MainActivity activity;
    private DocumentFragment documentFragment;
    private AdManager adManager;
    private PageView pageView;
    private HelpManager helpManager;
    private TextView statusView;

    private InputMethodManager imm;

    private int permissionDialogCount;

    public EditActionModeCallback(MainActivity activity, DocumentFragment documentFragment, AdManager adManager, HelpManager helpManager) {
        this.activity = activity;
        this.documentFragment = documentFragment;
        this.adManager = adManager;
        this.helpManager = helpManager;
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
                helpManager.show();

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
            if (permissionDialogCount > 1) {
                // some users keep denying the permission
                return;
            }

            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_CODE);

            permissionDialogCount++;
        } else {
            final File htmlFile = new File(AndroidFileCache.getCacheDirectory(activity), "content.html");
            pageView.requestHtml(htmlFile, new Runnable() {

                @Override
                public void run() {
                    documentFragment.save(htmlFile);

                    pageView.post(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText(R.string.edit_status_saved);
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
