package at.tomtasche.reader.ui.activity;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipException;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import at.stefl.opendocument.java.odf.IllegalMimeTypeException;
import at.stefl.opendocument.java.odf.UnsupportedMimeTypeException;
import at.stefl.opendocument.java.odf.ZipEntryNotFoundException;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.FileLoader;
import at.tomtasche.reader.background.LoadingListener;
import at.tomtasche.reader.background.ReportUtil;
import at.tomtasche.reader.background.UpLoader;
import at.tomtasche.reader.ui.widget.PageFragment;
import at.tomtasche.reader.ui.widget.ProgressDialogFragment;

import com.devspark.appmsg.AppMsg;

public abstract class DocumentActivity extends ActionBarActivity implements
		LoaderCallbacks<Document>, DocumentLoadingActivity {

	private static final String EXTRA_URI = "uri";
	private static final String EXTRA_LIMIT = "limit";
	private static final String EXTRA_PASSWORD = "password";
	private static final String EXTRA_TRANSLATABLE = "translatable";

	private ProgressDialogFragment progressDialog;
	private PageFragment pageFragment;

	private List<LoadingListener> loadingListeners;

	private Document document;

	private Handler handler;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setTitle("");
		setContentView(R.layout.main);

		handler = new Handler();
		loadingListeners = new LinkedList<LoadingListener>();

		getSupportLoaderManager().initLoader(0, null, this);
		getSupportLoaderManager().initLoader(1, null, this);

		pageFragment = (PageFragment) getSupportFragmentManager()
				.findFragmentByTag(PageFragment.FRAGMENT_TAG);
		if (pageFragment == null) {
			pageFragment = new PageFragment();
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.document_container, pageFragment,
							PageFragment.FRAGMENT_TAG).commit();

			Uri uri = getIntent().getData();
			if (Intent.ACTION_VIEW.equals(getIntent().getAction())
					&& uri != null) {
				loadUri(uri);
			}
		}
	}

	@Override
	public DocumentLoader loadUri(Uri uri) {
		return loadUri(uri, null, true, false);
	}

	public DocumentLoader loadUri(Uri uri, String password) {
		return loadUri(uri, password, true, false);
	}

	public DocumentLoader loadUri(Uri uri, boolean limit) {
		return loadUri(uri, null, limit, false);
	}

	public DocumentLoader loadUri(Uri uri, String password, boolean limit,
			boolean translatable) {
		Bundle bundle = new Bundle();
		bundle.putString(EXTRA_PASSWORD, password);
		bundle.putParcelable(EXTRA_URI, uri);
		bundle.putBoolean(EXTRA_LIMIT, limit);
		bundle.putBoolean(EXTRA_TRANSLATABLE, translatable);

		return (DocumentLoader) getSupportLoaderManager().restartLoader(0,
				bundle, this);
	}

	public UpLoader uploadUri(Uri uri) {
		Bundle bundle = new Bundle();
		bundle.putParcelable(EXTRA_URI, uri);

		return (UpLoader) getSupportLoaderManager().restartLoader(1, bundle,
				this);
	}

	@Override
	public Loader<Document> onCreateLoader(int id, Bundle bundle) {
		boolean limit = true;
		boolean translatable = false;
		String password = null;
		Uri uri = DocumentLoader.URI_INTRO;
		if (bundle != null) {
			uri = bundle.getParcelable(EXTRA_URI);
			limit = bundle.getBoolean(EXTRA_LIMIT);
			translatable = bundle.getBoolean(EXTRA_TRANSLATABLE);
			password = bundle.getString(EXTRA_PASSWORD);
		}

		switch (id) {
		case 0:
			DocumentLoader documentLoader = new DocumentLoader(this, uri);
			documentLoader.setPassword(password);
			documentLoader.setLimit(limit);
			documentLoader.setTranslatable(translatable);

			showProgress(documentLoader, false);

			return documentLoader;

		case 1:
		default:
			UpLoader upLoader = new UpLoader(this, uri);

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
			// DocumentActivity should - basically - work out-of-the-box
			// (without any further logic)!

			if (document.isLimited()) {
				showCrouton(R.string.toast_info_limited, new Runnable() {

					@Override
					public void run() {
						loadUri(uri, ((DocumentLoader) loader).getPassword(),
								false, false);
					}
				}, AppMsg.STYLE_INFO);
			}

			for (LoadingListener listener : loadingListeners) {
				listener.onSuccess(document, uri);
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Document> loader) {
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (intent == null)
			return;

		Uri uri = intent.getData();
		if (requestCode == 42 && resultCode == RESULT_OK && uri != null)
			loadUri(uri);
	}

	private void showProgress(final Loader<Document> loader,
			final boolean upload) {
		if (progressDialog != null)
			return;

		progressDialog = new ProgressDialogFragment(upload);

		FragmentTransaction transaction = getSupportFragmentManager()
				.beginTransaction();
		progressDialog.show(transaction, ProgressDialogFragment.FRAGMENT_TAG);

		if (!upload) {
			final FileLoader fileLoader = (FileLoader) loader;

			handler.postDelayed(new Runnable() {

				@Override
				public void run() {
					if (progressDialog == null)
						return;

					progressDialog.setProgress(fileLoader.getProgress());

					if (loader.isStarted())
						handler.postDelayed(this, 1000);
				}
			}, 1000);
		}
	}

	private void dismissProgress() {
		// dirty hack because committing isn't allowed right after
		// onLoadFinished:
		// "java.lang.IllegalStateException: Can not perform this action inside of onLoadFinished"
		if (progressDialog == null)
			progressDialog = (ProgressDialogFragment) getSupportFragmentManager()
					.findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG);

		if (progressDialog != null && progressDialog.getShowsDialog()
				&& progressDialog.isNotNull()) {
			progressDialog.dismissAllowingStateLoss();

			progressDialog = null;
		}
	}

	public void handleError(Throwable error, final Uri uri) {
		Log.e("OpenDocument Reader", "Error opening file at " + uri.toString(),
				error);

		for (LoadingListener listener : loadingListeners) {
			listener.onError(error, uri);

			// TODO: return here, but only if the listener was registered by a
			// JUnit test
		}

		int errorDescription;
		if (error == null) {
			return;
		} else if (error instanceof EncryptedDocumentException) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.toast_error_password_protected);

			final EditText input = new EditText(this);
			input.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
			builder.setView(input);

			builder.setPositiveButton(getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							loadUri(uri, input.getText().toString());

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
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

		showCrouton(errorDescription, null, AppMsg.STYLE_ALERT);

		if (uri.toString().endsWith(".odt") || uri.toString().endsWith(".ods")
				|| uri.toString().endsWith(".ott")
				|| uri.toString().endsWith(".ots")
				|| uri.toString().endsWith(".odp")
				|| uri.toString().endsWith(".otp"))
			ReportUtil.submitFile(this, error, uri, errorDescription);
	}

	public void addLoadingListener(LoadingListener loadingListener) {
		loadingListeners.add(loadingListener);
	}

	public Document getDocument() {
		return document;
	}

	public PageFragment getPageFragment() {
		return pageFragment;
	}

	public void showToast(int resId) {
		showToast(getString(resId));
	}

	public void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	public void showCrouton(int resId, final Runnable callback,
			AppMsg.Style style) {
		showCrouton(getString(resId), callback, style);
	}

	public void showCrouton(String message, final Runnable callback,
			AppMsg.Style style) {
		AppMsg crouton = AppMsg.makeText(this, message, style);
		crouton.setDuration(AppMsg.LENGTH_LONG);
		crouton.getView().setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (callback != null)
					callback.run();
			}
		});
		crouton.show();
	}
}
