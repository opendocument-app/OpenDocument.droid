package at.enajat.david.android.filechooser;

import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

    static {
        if (Integer.parseInt(Build.VERSION.SDK) > 5) {
            ROOT = "/mnt/sdcard/";
        } else {
            ROOT = "/sdcard/";
        }
    }

    private static final String ROOT;

    private static final boolean BREAKOUT = true;
    private static final FileFilter FILTER = new FileFilter() {
        public boolean accept(File f) {
            return true;
        }
    };
    private static final Comparator<File> COMPARATOR = new Comparator<File>() {
        public int compare(File f1, File f2) {
            if (f1.isDirectory()) {
                if (f2.isDirectory()) {
                    return f1.getName().compareTo(f2.getName());
                }
                else {
                    return -1;
                }
            }
            else {
                if (f2.isDirectory()) {
                    return 1;
                }
                else {
                    return f1.getName().compareTo(f2.getName());
                }
            }
        }	
    };
    private FileAdapter adapter;
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new FileAdapter(this, ROOT, BREAKOUT, FILTER, COMPARATOR);
        setListAdapter(adapter);
        ListView list = getListView();
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
    public void result(Uri uri) {
        Intent data = new Intent();
        data.setData(uri);
        setResult(RESULT_OK, data);
        // Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
        finish();
    }
    private OnItemClickListener itemClick = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (adapter.isDirectory(id)) {
                if (!adapter.changeDir(id)) {
                    Toast.makeText(FileChooser.this, R.string.denied, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (adapter.getCount() == 0) {
                    Toast.makeText(FileChooser.this, R.string.nofiles, Toast.LENGTH_SHORT).show();
                }
                updateTitle();
            }
            else {
                result(adapter.getUri(id));
            }
        }
    };
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list_option_menu, menu);
        return true;
    }
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_back) {
            if (!adapter.changeUp()) {
                Toast.makeText(this, R.string.ontop, Toast.LENGTH_SHORT).show();
                return true;
            }
            updateTitle();
            return true;
        }
        else if (item.getItemId() == R.id.menu_cancel) {
            cancel();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public boolean onKeyDown(int keyCode, KeyEvent event) {
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
