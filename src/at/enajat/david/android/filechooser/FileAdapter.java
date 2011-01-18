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
    public FileAdapter(Context context, String root, boolean breakout, FileFilter filter, Comparator<File> comparator) {
        this(context, new File(root), breakout, filter, comparator);
    }
    public FileAdapter(Context context, File root, boolean breakout, FileFilter filter, Comparator<File> comparator) {
        this.context = context;
        this.root = root;
        this.breakout = breakout;
        this.filter = filter;
        this.comparator = comparator;
        if (!checkDir(root)) {
            throw new IllegalArgumentException("illegal root directory");
        }
        updateDir(root);
    }
    public static boolean checkDir(File f) {
        return f.exists() && f.isDirectory() && f.canRead();
    }
    private void updateDir(File f) {
        dir = f;
        files = f.listFiles(filter);
        Arrays.sort(files, comparator);
    }
    private boolean changeDir(File newDir) {
        if (!checkDir(newDir)) {
            return false;
        }
        updateDir(newDir);
        notifyDataSetChanged();
        return true;
    }
    public boolean changeDir(long id)  {
        return changeDir(files[(int) id]);
    }
    public boolean changeUp() {
        try {
            File f = dir.getParentFile();
            if (f == null || (!breakout && !f.getCanonicalPath().startsWith(root.getCanonicalPath()))) {
                return false;
            }
            return changeDir(f);
        }
        catch (IOException e) {
            return false;
        }
    }
    public Uri getUri(long id) {
        return Uri.fromFile(files[(int) id]);
    }
    public boolean isDirectory(long id) {
        return files[(int) id].isDirectory();
    }
    public String getCurrentPath() {
        try {
            String p = dir.getCanonicalPath();
            if (!p.endsWith("/")) {
                p += "/";
            }
            if (breakout) {
                return p;
            }
            else {
                return p.substring(root.getAbsolutePath().length());
            }
        }
        catch (IOException e) {
            return null;
        }
    }
    public int getCount() {
        return files.length;
    }
    public Object getItem(int position) {
        return files[position];
    }
    public long getItemId(int position) {
        return position;
    }
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);	
        File f = files[position];
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null && fs.length > 0) {			
                ((ImageView) v.findViewById(R.id.list_image)).setImageResource(R.drawable.dir_full);
            }
            else {
                ((ImageView) v.findViewById(R.id.list_image)).setImageResource(R.drawable.dir_empty);
            }
        }
        else {
            ((ImageView) v.findViewById(R.id.list_image)).setImageResource(R.drawable.file);
        }
        ((TextView) v.findViewById(R.id.list_text)).setText(f.getName());
        return v;
    }
}
