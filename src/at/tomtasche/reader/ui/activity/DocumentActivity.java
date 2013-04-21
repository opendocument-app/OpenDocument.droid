package at.tomtasche.reader.ui.activity;

import java.io.FileNotFoundException;
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
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import at.andiwand.odf2html.odf.IllegalMimeTypeException;
import at.andiwand.odf2html.odf.ZipEntryNotFoundException;
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

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.devspark.appmsg.AppMsg;

public class DocumentActivity extends SherlockFragmentActivity implements
		LoaderCallbacks<Document>, ActionBar.TabListener,
		DocumentLoadingActivity {

	private static final String EXTRA_URI = "uri";
	private static final String EXTRA_LIMIT = "limit";
	private static final String EXTRA_PASSWORD = "password";
	private static final String EXTRA_TAB_POSITION = "tab_position";

	private ProgressDialogFragment progressDialog;
	private PageFragment pageFragment;

	private LoadingListener loadingListener;

	private Document document;
	private int lastPosition;

	private Handler handler;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setTitle("");
		setContentView(R.layout.main);

		handler = new Handler();

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

		if (savedInstanceState != null)
			lastPosition = savedInstanceState.getInt(EXTRA_TAB_POSITION);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(EXTRA_TAB_POSITION, getSupportActionBar()
				.getSelectedNavigationIndex());
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		pageFragment.loadPage(document.getPageAt(tab.getPosition()));
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (intent.getData() != null) {
			loadUri(intent.getData());
		}
	}

	@Override
	public DocumentLoader loadUri(Uri uri) {
		return loadUri(uri, null, true);
	}

	public DocumentLoader loadUri(Uri uri, String password) {
		return loadUri(uri, password, true);
	}

	public DocumentLoader loadUri(Uri uri, boolean limit) {
		return loadUri(uri, null, limit);
	}

	public DocumentLoader loadUri(Uri uri, String password, boolean limit) {
		Bundle bundle = new Bundle();
		bundle.putString(EXTRA_PASSWORD, password);
		bundle.putParcelable(EXTRA_URI, uri);
		bundle.putBoolean(EXTRA_LIMIT, limit);

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
		String password = null;
		Uri uri = DocumentLoader.URI_INTRO;
		if (bundle != null) {
			uri = bundle.getParcelable(EXTRA_URI);
			limit = bundle.getBoolean(EXTRA_LIMIT);
			password = bundle.getString(EXTRA_PASSWORD);
		}

		switch (id) {
		case 0:
			DocumentLoader documentLoader = new DocumentLoader(this, uri);
			documentLoader.setPassword(password);
			documentLoader.setLimit(limit);

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
			onError(error, uri);
		} else if (document != null) {
			this.document = document;

			ActionBar bar = getSupportActionBar();
			bar.removeAllTabs();

			int pages = document.getPages().size();
			if (pages > 1) {
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
				for (int i = 0; i < pages; i++) {
					ActionBar.Tab tab = bar.newTab();
					String name = document.getPageAt(i).getName();
					if (name == null)
						name = "Sheet " + (i + 1);
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
				pageFragment.loadPage(document.getPageAt(0));
			}

			if (document.isLimited()) {
				showCrouton(R.string.toast_info_limited, new Runnable() {

					@Override
					public void run() {
						loadUri(uri, ((DocumentLoader) loader).getPassword(),
								false);
					}
				}, AppMsg.STYLE_INFO);
			}

			if (loadingListener != null)
				loadingListener.onSuccess(uri);
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

					progressDialog.show(transaction,
							ProgressDialogFragment.FRAGMENT_TAG);

				if (!upload) {
					final FileLoader fileLoader = (FileLoader) loader;

					handler.postDelayed(new Runnable() {

						@Override
						public void run() {
								if (progressDialog == null)
									return;

								progressDialog.setProgress(fileLoader
										.getProgress());

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
								.findFragmentByTag(
										ProgressDialogFragment.FRAGMENT_TAG);

					if (progressDialog != null
							&& progressDialog.getShowsDialog() && progressDialog.isNotNull()) {
						progressDialog.dismissAllowingStateLoss();

						progressDialog = null;
					}
	}

	public void onError(Throwable error, final Uri uri) {
		Log.e("OpenDocument Reader", "Error opening file at " + uri.toString(),
				error);

		// used for JUnit tests
		if (loadingListener != null) {
			loadingListener.onError(error, uri);

			return;
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
				|| error instanceof ZipEntryNotFoundException) {
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

	public void setLoadingListener(LoadingListener loadingListener) {
		this.loadingListener = loadingListener;
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
