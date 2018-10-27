package at.tomtasche.reader.ui.activity;

import java.io.FileNotFoundException;
import java.util.zip.ZipException;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import at.stefl.opendocument.java.odf.IllegalMimeTypeException;
import at.stefl.opendocument.java.odf.UnsupportedMimeTypeException;
import at.stefl.opendocument.java.odf.ZipEntryNotFoundException;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.FileLoader;
import at.tomtasche.reader.background.UpLoader;
import at.tomtasche.reader.ui.CroutonHelper;
import at.tomtasche.reader.ui.widget.PageView;
import at.tomtasche.reader.ui.widget.ProgressDialogFragment;
import de.keyboardsurfer.android.widget.crouton.Style;

public class DocumentFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<Document>, DocumentLoadingActivity, android.support.v7.app.ActionBar.TabListener {

	private static final String EXTRA_URI = "uri";
	private static final String EXTRA_LIMIT = "limit";
	private static final String EXTRA_PASSWORD = "password";
	private static final String EXTRA_TRANSLATABLE = "translatable";
	private static final String EXTRA_TAB_POSITION = "tab_position";
	private static final String EXTRA_SCROLL_POSITION = "scroll_position";

	private Handler mainHandler;

	private int lastPosition;

	private ProgressDialogFragment progressDialog;
	private PageView pageView;

	private Document document;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getActivity().setTitle("");

		mainHandler = new Handler();
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			lastPosition = savedInstanceState.getInt(EXTRA_TAB_POSITION);

			pageView = new PageView(getActivity(),
					savedInstanceState.getInt(EXTRA_SCROLL_POSITION));
		} else {
			pageView = new PageView(getActivity());
			pageView.loadData("", "text/plain", PageView.ENCODING);
		}

		return pageView;
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Intent intent = getActivity().getIntent();
		Uri uri = intent.getData();
		if (Intent.ACTION_VIEW.equals(intent.getAction()) && uri != null) {
			loadUri(uri);
		}
	}

	@Override
	public DocumentLoader loadUri(Uri uri) {
		return loadUri(uri, null, false, false);
	}

	public DocumentLoader loadUri(Uri uri, String password) {
		return loadUri(uri, password, false, false);
	}

	public DocumentLoader loadUri(Uri uri, String password, boolean limit,
			boolean translatable) {
		Bundle bundle = new Bundle();
		bundle.putString(EXTRA_PASSWORD, password);
		bundle.putParcelable(EXTRA_URI, uri);
		bundle.putBoolean(EXTRA_LIMIT, limit);
		bundle.putBoolean(EXTRA_TRANSLATABLE, translatable);

		return (DocumentLoader) getLoaderManager().restartLoader(0,
				bundle, this);
	}

	public UpLoader uploadUri(Uri uri) {
		Bundle bundle = new Bundle();
		bundle.putParcelable(EXTRA_URI, uri);

		return (UpLoader) getLoaderManager().restartLoader(1, bundle,
				this);
	}

	@Override
	public Loader<Document> onCreateLoader(int id, Bundle bundle) {
		boolean limit = true;
		boolean translatable = false;
		String password = null;
		Uri uri = null;
		if (bundle != null) {
			uri = bundle.getParcelable(EXTRA_URI);
			limit = bundle.getBoolean(EXTRA_LIMIT);
			translatable = bundle.getBoolean(EXTRA_TRANSLATABLE);
			password = bundle.getString(EXTRA_PASSWORD);
		}

		switch (id) {
		case 0:
			DocumentLoader documentLoader = new DocumentLoader(getActivity(), uri);
			documentLoader.setPassword(password);
			documentLoader.setLimit(limit);
			documentLoader.setTranslatable(translatable);

			showProgress(documentLoader, false);

			return documentLoader;

		case 1:
		default:
			UpLoader upLoader = new UpLoader(getActivity(), uri);

			showProgress(upLoader, true);

			return upLoader;
		}
	}

	@Override
	public void onLoadFinished(final Loader<Document> loader, Document document) {
		dismissProgress();

		FileLoader fileLoader = (FileLoader) loader;
		Throwable error = fileLoader.getLastError();
		final Uri uri = fileLoader.getLastUri();
		if (error != null) {
			handleError(error, uri);
		} else if (document != null) {
			this.document = document;

			// TODO: we should load the first page here already
			// DocumentFragment should - basically - work out-of-the-box
			// (without any further logic)!

			if (document.isLimited()) {
				CroutonHelper.showCrouton(getActivity(), R.string.toast_info_limited, new Runnable() {

					@Override
					public void run() {
						loadUri(uri, ((DocumentLoader) loader).getPassword(),
								false, false);
					}
				}, Style.INFO);
			}

			android.support.v7.app.ActionBar bar = ((MainActivity) getActivity()).getSupportActionBar();
			bar.removeAllTabs();

			int pages = document.getPages().size();
			if (pages > 1) {
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
				for (int i = 0; i < pages; i++) {
					android.support.v7.app.ActionBar.Tab tab = bar.newTab();
					String name = document.getPageAt(i).getName();
					if (name == null)
						name = "Page " + (i + 1);
					tab.setText(name);
					tab.setTabListener(this);

					bar.addTab(tab);
				}

				if (lastPosition > 0) {
					bar.setSelectedNavigationItem(lastPosition);

					lastPosition = -1;
				}
			} else {
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

				if (pages == 1) {
					showPage(document.getPageAt(0));
				}
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Document> loader) {
	}

	private void showProgress(final Loader<Document> loader,
			final boolean upload) {
		if (progressDialog != null) {
			return;
		}

		try {
			progressDialog = new ProgressDialogFragment(upload);

			FragmentTransaction transaction = getFragmentManager()
					.beginTransaction();
			progressDialog.show(transaction,
					ProgressDialogFragment.FRAGMENT_TAG);

			if (!upload) {
				final FileLoader fileLoader = (FileLoader) loader;

				mainHandler.postDelayed(new Runnable() {

					@Override
					public void run() {
						if (progressDialog == null) {
							return;
						}

						progressDialog.setProgress(fileLoader.getProgress());

						if (loader.isStarted()) {
							mainHandler.postDelayed(this, 1000);
						}
					}
				}, 1000);
			}
		} catch (IllegalStateException e) {
			e.printStackTrace();

			progressDialog = null;
		}
	}

	private void dismissProgress() {
		// dirty hack because committing isn't allowed right after
		// onLoadFinished:
		// "java.lang.IllegalStateException: Can not perform this action inside of onLoadFinished"
		if (progressDialog == null) {
			progressDialog = (ProgressDialogFragment) getFragmentManager()
					.findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG);
		}

		if (progressDialog != null && progressDialog.getShowsDialog()
				&& progressDialog.isNotNull()) {
			try {
				progressDialog.dismissAllowingStateLoss();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}

			progressDialog = null;
		}
	}

	public void handleError(Throwable error, final Uri uri) {
		Log.e("OpenDocument Reader", "Error opening file at " + uri.toString(),
				error);

		((MainActivity) getActivity()).getCrashManager().log(error, uri);

		final Uri cacheUri = AndroidFileCache.getCacheFileUri();

		int errorDescription;
		if (error == null) {
			return;
		} else if (error instanceof EncryptedDocumentException) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(R.string.toast_error_password_protected);

			final EditText input = new EditText(getActivity());
			input.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
			builder.setView(input);

			builder.setPositiveButton(getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							loadUri(cacheUri, input.getText().toString());

							dialog.dismiss();
						}
					});
			builder.setNegativeButton(getString(android.R.string.cancel), null);
			builder.show();

			return;
		} else if (error instanceof IllegalMimeTypeException
				|| error instanceof ZipException
				|| error instanceof ZipEntryNotFoundException
				|| error instanceof UnsupportedMimeTypeException) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(R.string.toast_error_illegal_file);
			builder.setMessage(R.string.dialog_upload_file);
			builder.setPositiveButton(getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							uploadUri(uri);

							dialog.dismiss();
						}
					});
			builder.setNegativeButton(getString(android.R.string.cancel), null);
			builder.show();

			return;
		} else if (error instanceof FileNotFoundException) {
			if (Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED_READ_ONLY)
					|| Environment.getExternalStorageState().equals(
							Environment.MEDIA_MOUNTED)) {
				errorDescription = R.string.toast_error_find_file;
			} else {
				errorDescription = R.string.toast_error_storage;
			}
		} else if (error instanceof IllegalArgumentException) {
			errorDescription = R.string.toast_error_illegal_file;
		} else if (error instanceof OutOfMemoryError) {
			errorDescription = R.string.toast_error_out_of_memory;
		} else {
			errorDescription = R.string.toast_error_generic;
		}

		CroutonHelper.showCrouton(getActivity(), errorDescription, null, Style.ALERT);
	}

	@Override
	public void onTabSelected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
		Document.Page page = getDocument().getPageAt(tab.getPosition());
		showPage(page);
	}

	@Override
	public void onTabUnselected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
	}

	@Override
	public void onTabReselected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
	}

	private void showPage(Document.Page page) {
		loadData(page.getUrl());
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(EXTRA_TAB_POSITION, ((MainActivity) getActivity()).getSupportActionBar().getSelectedNavigationIndex());
		outState.putInt(EXTRA_SCROLL_POSITION, pageView.getScrollY());
	}

	public Document getDocument() {
		return document;
	}

	private void loadData(String url) {
		pageView.loadUrl(url);
	}

	@SuppressWarnings("deprecation")
	public void searchDocument(String query) {
		pageView.findAll(query);
	}

	public PageView getPageView() {
		return pageView;
	}
}
