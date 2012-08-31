
package at.enajat.david.android.filechooser;

import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import at.tomtasche.reader.R;

public class FragmentFileChooser extends ListFragment {

    private static final File ROOT = Environment.getExternalStorageDirectory();

    private static final boolean BREAKOUT = false;

    private static final FileFilter FILTER = new FileFilter() {
	@Override
	public boolean accept(final File f) {
	    final String name = f.getName();
	    return !f.getName().startsWith(".")
		    && (f.isDirectory() || name.endsWith(".odt") || name.endsWith(".ods")
			    || name.endsWith(".ott") || name.endsWith(".ots"));
	}
    };

    private static final Comparator<File> COMPARATOR = new Comparator<File>() {
	@Override
	public int compare(final File f1, final File f2) {
	    final String name1 = f1.getName().toUpperCase();
	    final String name2 = f2.getName().toUpperCase();
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

    private FragmentFileAdapter adapter;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        if (!FragmentFileAdapter.checkDir(ROOT)) {
	    Toast.makeText(getActivity(), getString(R.string.toast_error_find_file), Toast.LENGTH_LONG)
	    .show();
	    return;
	}
	adapter = new FragmentFileAdapter(getActivity(), ROOT, BREAKOUT, FILTER, COMPARATOR);
	setListAdapter(adapter);
	final ListView list = getListView();
	list.setTextFilterEnabled(true);
	list.setOnItemClickListener(itemClick);
	registerForContextMenu(list);
    }

    private final OnItemClickListener itemClick = new OnItemClickListener() {
	@Override
	public void onItemClick(final AdapterView<?> parent, final View view, final int position,
		final long id) {
	    if (id == 0) adapter.changeUp();
	    
	    if (adapter.isDirectory(id)) {
		if (!adapter.changeDir(id)) {
		    Toast.makeText(getActivity(), getString(R.string.toast_error_access_denied),
			    Toast.LENGTH_SHORT).show();
		    return;
		}
		if (adapter.getCount() == 0) {
		    Toast.makeText(getActivity(), getString(R.string.toast_error_no_files),
			    Toast.LENGTH_SHORT).show();
		}
	    } else {
//		Intent documentIntent = new Intent(getActivity(), DocumentService.class);
//		documentIntent.setData(adapter.getUri(id));
//		
//		getActivity().startService(documentIntent);
	    }
	}
    };
}
