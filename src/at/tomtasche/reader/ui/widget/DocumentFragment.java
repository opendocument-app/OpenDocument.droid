package at.tomtasche.reader.ui.widget;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import at.tomtasche.reader.background.service.DocumentService;
import at.tomtasche.reader.background.service.DocumentServiceConnection;
import at.tomtasche.reader.background.service.DocumentServiceConnection.DocumentServiceConnectionListener;

public class DocumentFragment extends Fragment implements DocumentServiceConnectionListener {

    DocumentServiceConnection connection;

    BroadcastReceiver receiver;

    DocumentView view;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	if (container == null) {
	    return null;
	}

	connection = new DocumentServiceConnection(getActivity());
	connection.setListener(this);

	receiver = new DocumentChangedReceiver();
	IntentFilter filter = new IntentFilter(DocumentService.DOCUMENT_CHANGED_INTENT);
	getActivity().registerReceiver(receiver, filter);

	view = new DocumentView(getActivity());

	return view;
    }


    private void reload() {
	String data = connection.getDocumentService().getData();

	view.loadData(data);
    }
    
    public void nextPage() {
	connection.getDocumentService().nextPage();
    }
    
    public void previousPage() {
	connection.getDocumentService().previousPage();
    }
    
    public void jumpToPage(int page) {
	connection.getDocumentService().jumpToPage(page);
    }
    
    public int getPageCount() {
	return connection.getDocumentService().getPageCount();
    }
    
    public List<String> getPageNames() {
	return connection.getDocumentService().getPageNames();
    }


    @Override
    public void onConnected() {
	reload();
    }

    @Override
    public void onDestroyView() {
	super.onDestroyView();

	if (connection != null && connection.isConnected()) connection.unbind();

	if (receiver != null) {
	    getActivity().unregisterReceiver(receiver);
	    receiver = null;
	}
    }


    public int getShownIndex() {
	return getArguments().getInt("page", 0);
    }

    public DocumentView getDocumentView() {
	return view;
    }


    class DocumentChangedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
	    if (!connection.isConnected()) return;

	    reload();
	}
    }
}
