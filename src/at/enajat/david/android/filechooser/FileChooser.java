package at.enajat.david.android.filechooser;

import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import at.tomtasche.reader.R;

public class FileChooser extends ListActivity {

    private static final File ROOT = Environment.getExternalStorageDirectory();

    private static final boolean BREAKOUT = false;

    private static final FileFilter FILTER = new FileFilter() {
        @Override
        public boolean accept(final File f) {
            String name = f.getName();
            return !f.getName().startsWith(".") && (f.isDirectory() ||
            		name.endsWith(".odt") || name.endsWith(".ods") ||
            		name.endsWith(".ott") || name.endsWith(".ots"));
        }
    };

    private static final Comparator<File> COMPARATOR = new Comparator<File>() {
        @Override
        public int compare(final File f1, final File f2) {
        	String name1 = f1.getName().toUpperCase();
        	String name2 = f2.getName().toUpperCase();
            if (f1.isDirectory()) {
                if (f2.isDirectory()) {
                    return name1.compareTo(name2);
                } else {
                    return -1;
                }
            } else {
                if (f2.isDirectory()) {
                    return 1;
                } else {
                    return name1.compareTo(name2);
                }
            }
        }
    };

    private FileAdapter adapter;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            adapter = new FileAdapter(this, ROOT, BREAKOUT, FILTER, COMPARATOR);
        } catch (final IllegalArgumentException e) {
            Toast.makeText(this, getString(R.string.toast_error_find_file), Toast.LENGTH_LONG)
                    .show();
            cancel();
        }
        setListAdapter(adapter);
        final ListView list = getListView();
        list.setTextFilterEnabled(true);
        list.setOnItemClickListener(itemClick);
        registerForContextMenu(list);
        updateTitle();
    }

    public void updateTitle() {
        setTitle(getString(R.string.app_name) + " " + adapter.getCurrentPath());
    }

    public void cancel() {
        setResult(RESULT_CANCELED);
        // Toast.makeText(this, R.string.canceled, Toast.LENGTH_SHORT).show();
        finish();
    }

    public void result(final Uri uri) {
        final Intent data = new Intent();
        data.setData(uri);
        setResult(RESULT_OK, data);
        // Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
        finish();
    }

    private final OnItemClickListener itemClick = new OnItemClickListener() {
        @Override
        public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                final long id) {
            if (adapter.isDirectory(id)) {
                if (!adapter.changeDir(id)) {
                    Toast.makeText(FileChooser.this, getString(R.string.toast_error_access_denied),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (adapter.getCount() == 0) {
                    Toast.makeText(FileChooser.this, getString(R.string.toast_error_no_files),
                            Toast.LENGTH_SHORT).show();
                }
                updateTitle();
            } else {
                result(adapter.getUri(id));
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chooser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_back) {
            if (!adapter.changeUp()) {
                Toast.makeText(this, getString(R.string.toast_error_ontop), Toast.LENGTH_SHORT)
                        .show();
                return true;
            }
            updateTitle();
            return true;
        } else if (item.getItemId() == R.id.menu_cancel) {
            cancel();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!adapter.changeUp()) {
                cancel();
                return true;
            }
            updateTitle();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}