package at.tomtasche.reader.ui.widget;

import java.util.List;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import at.tomtasche.reader.background.service.DocumentService;
import at.tomtasche.reader.ui.OfficeInterface;

public class DocumentFragment extends Fragment implements OfficeInterface {

    DocumentService service;

    DocumentView view;

    private final Uri uri;

    public DocumentFragment(Uri uri) {
	this.uri = uri;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	if (container == null) {
	    return null;
	}

	service = new DocumentService(getActivity(), this);

	view = new DocumentView(getActivity());

	loadUri(uri);

	return view;
    }

    public void loadUri(Uri uri) {
	service.loadUri(uri);
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

	if (service != null)
	    service.quit();
    }

    public int getShownIndex() {
	return getArguments().getInt("page", 0);
    }

    public DocumentView getDocumentView() {
	return view;
    }

    @Override
    public void onFinished() {
	reload();
    }

    @Override
    public void showToast(int resId) {
	Toast.makeText(getActivity(), getString(resId), Toast.LENGTH_LONG).show();
    }
}
