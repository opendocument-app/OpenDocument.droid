package at.tomtasche.reader.ui.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;

public class DocumentAdapter extends BaseAdapter {
    
    Context context;
    
    DocumentLoader loader;
    
    
    public DocumentAdapter(Context context) {
	this.context = context;
    }
    
    
    public Adapter getPageAdapter() {
	return new ArrayAdapter<String>(context, R.id.list_text, loader.getPageNames());
    }

    @Override
    public int getCount() {
	// TODO Auto-generated method stub
	return 0;
    }

    @Override
    public Object getItem(int position) {
	// TODO Auto-generated method stub
	return null;
    }

    @Override
    public long getItemId(int position) {
	// TODO Auto-generated method stub
	return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
	// TODO Auto-generated method stub
	return null;
    }
}
