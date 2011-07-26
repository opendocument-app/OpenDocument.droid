
package at.enajat.david.android.filechooser;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import at.tomtasche.reader.R;

public class FileAdapter extends BaseAdapter {
    private final Context context;

    private final File root;

    private final boolean breakout;

    private final FileFilter filter;

    private final Comparator<File> comparator;

    private File dir;

    private File[] files;

    public FileAdapter(final Context context, final File root, final boolean breakout,
            final FileFilter filter, final Comparator<File> comparator) {
        if (!checkDir(root)) {
            throw new IllegalArgumentException("illegal root directory");
        }
        this.context = context;
        this.root = root;
        this.breakout = breakout;
        this.filter = filter;
        this.comparator = comparator;
        updateDir(root);
    }

    public static boolean checkDir(final File f) {
        return f.exists() && f.isDirectory() && f.canRead();
    }

    private void updateDir(final File f) {
        dir = f;
        files = f.listFiles(filter);
        Arrays.sort(files, comparator);
    }

    private boolean changeDir(final File newDir) {
        if (!checkDir(newDir)) {
            return false;
        }
        updateDir(newDir);
        notifyDataSetChanged();
        return true;
    }

    public boolean changeDir(final long id) {
        return changeDir(files[(int)id]);
    }

    public boolean changeUp() {
        try {
            final File f = dir.getParentFile();
            if (f == null || !breakout && !f.getCanonicalPath().startsWith(root.getCanonicalPath())) {
                return false;
            }
            return changeDir(f);
        } catch (final IOException e) {
            return false;
        }
    }

    public Uri getUri(final long id) {
        return Uri.fromFile(files[(int)id]);
    }

    public boolean isDirectory(final long id) {
        return files[(int)id].isDirectory();
    }

    public String getCurrentPath() {
        try {
            String p = dir.getCanonicalPath();
            if (!p.endsWith("/")) {
                p += "/";
            }
            if (breakout) {
                return p;
            } else {
                return p.substring(root.getAbsolutePath().length());
            }
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public int getCount() {
        return files.length;
    }

    @Override
    public Object getItem(final int position) {
        return files[position];
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final View v = LayoutInflater.from(context).inflate(R.layout.list_item_chooser, parent, false);
        final File f = files[position];
        if (f.isDirectory()) {
            final File[] fs = f.listFiles();
            if (fs != null && fs.length > 0) {
                ((ImageView)v.findViewById(R.id.list_image)).setImageResource(R.drawable.dir_full);
            } else {
                ((ImageView)v.findViewById(R.id.list_image)).setImageResource(R.drawable.dir_empty);
            }
        } else {
            ((ImageView)v.findViewById(R.id.list_image)).setImageResource(R.drawable.file);
        }
        ((TextView)v.findViewById(R.id.list_text)).setText(f.getName());
        return v;
    }
}
