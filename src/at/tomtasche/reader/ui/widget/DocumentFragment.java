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

public class DocumentFragment extends Fragment {

    DocumentService service;

    BroadcastReceiver receiver;

    DocumentView view;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	if (container == null) {
	    return null;
	}

	receiver = new DocumentChangedReceiver();
	IntentFilter filter = new IntentFilter(DocumentService.DOCUMENT_CHANGED_INTENT);
	getActivity().registerReceiver(receiver, filter);

	service = new DocumentService(getActivity());

	view = new DocumentView(getActivity());

	return view;
    }

    private void reload() {
	String data = service.getData();

	view.loadData(data);
    }

    public void nextPage() {
	service.nextPage();
    }

    public void previousPage() {
	service.previousPage();
    }

    public void jumpToPage(int page) {
	service.jumpToPage(page);
    }

    public int getPageCount() {
	return service.getPageCount();
    }

    public List<String> getPageNames() {
	return service.getPageNames();
    }

    @Override
    public void onDestroyView() {
	super.onDestroyView();

//	if (service != null)
//	    service.stop();

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
	    if (service == null)
		return;

	    reload();
	}
    }
}
