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
        documentFragment.reloadUri(true);

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
                if (Build.VERSION.SDK_INT >= 19) {
                    activity.requestSave();
                } else {
                    Runnable onPermission = new Runnable() {
                        @Override
                        public void run() {
                            DateFormat dateFormat = new SimpleDateFormat("MMddyyyy-HHmmss", Locale.US);
                            Date nowDate = Calendar.getInstance().getTime();
                            String nowString = dateFormat.format(nowDate);

                            File modifiedFile = new File(Environment.getExternalStorageDirectory(),
                                    "modified-by-opendocument-reader-on-" + nowString);
                            Uri fileUri = Uri.parse("file://"
                                    + modifiedFile.getAbsolutePath());

                            documentFragment.save(fileUri);
                        }
                    };

                    boolean hasPermission = activity.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, onPermission);
                    if (hasPermission) {
                        onPermission.run();
                    }
                }

                break;
            }

            default:
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
