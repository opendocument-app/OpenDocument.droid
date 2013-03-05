package at.tomtasche.reader.ui.activity;

import io.filepicker.FPService;
import io.filepicker.FilePicker;
import io.filepicker.FilePickerAPI;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.zip.ZipException;

import net.robotmedia.billing.BillingController;
import net.robotmedia.billing.BillingRequest.ResponseCode;
import net.robotmedia.billing.helper.AbstractBillingObserver;
import net.robotmedia.billing.model.Transaction;
import net.robotmedia.billing.model.Transaction.PurchaseState;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;
import at.andiwand.odf2html.odf.IllegalMimeTypeException;
import at.andiwand.odf2html.odf.ZipEntryNotFoundException;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Part;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.ReportUtil;
import at.tomtasche.reader.ui.widget.DocumentFragment;
import at.tomtasche.reader.ui.widget.ProgressDialogFragment;

import com.devspark.appmsg.AppMsg;
import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class MainActivity extends FragmentActivity implements
		BillingController.IConfiguration, LoaderCallbacks<Document> {

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";

	private static final String EXTRA_URI = "uri";
	private static final String EXTRA_PASSWORD = "password";

	private ProgressDialogFragment progressDialog;
	private DocumentFragment documentFragment;
	private AdView adView;

	private Uri resultData;

	@Override
	public void onCreate(Bundle arg0) {
		super.onCreate(arg0);

		// if (ActivityManager.isUserAMonkey())
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		// WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
		// .detectAll().penaltyLog().penaltyDeath().build());
		// StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
		// .penaltyLog().penaltyDeath().build());

		setTitle("");
		setContentView(R.layout.main);

		getSupportLoaderManager().initLoader(0, null, this);

		// getSupportFragmentManager().beginTransaction()
		// .replace(R.id.sliding_menu, new DocumentChooserFragment())
		// .commit();

		documentFragment = (DocumentFragment) getSupportFragmentManager()
				.findFragmentByTag(DocumentFragment.FRAGMENT_TAG);
		if (documentFragment == null) {
			documentFragment = new DocumentFragment();
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.document_container, documentFragment,
							DocumentFragment.FRAGMENT_TAG).commit();

			Uri uri = getIntent().getData();
			if (Intent.ACTION_VIEW.equals(getIntent().getAction())
					&& uri != null) {
				loadUri(uri);
			} else {
				loadUri(DocumentLoader.URI_INTRO);
			}
		}

		billingObserver = new AbstractBillingObserver(this) {

			public void onBillingChecked(boolean supported) {
				if (!supported)
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							showCrouton(MainActivity.this,
									getString(R.string.crouton_error_billing),
									null, AppMsg.STYLE_ALERT);
						}
					});
			}

			public void onSubscriptionChecked(boolean supported) {
			}

			public void onPurchaseStateChanged(String itemId,
					PurchaseState state) {
				List<Transaction> transactions = BillingController
						.getTransactions(MainActivity.this);
				for (Transaction t : transactions) {
					if (t.purchaseState == PurchaseState.PURCHASED) {
						if (adView != null)
							adView.setVisibility(View.GONE);
					}
				}
			}

			public void onRequestPurchaseResponse(String itemId,
					ResponseCode response) {
				if (response == ResponseCode.RESULT_OK) {
					if (adView != null)
						adView.setVisibility(View.GONE);
				} else {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							showCrouton(MainActivity.this,
									getString(R.string.crouton_error_billing),
									null, AppMsg.STYLE_ALERT);
						}
					});
				}
			}
		};
		BillingController.registerObserver(billingObserver);
		BillingController.setConfiguration(this);
		// // TODO: ugly.
		new Thread() {
			public void run() {
				try {
					if (!billingObserver.isTransactionsRestored())
						BillingController
								.restoreTransactions(getApplicationContext());

					if (!BillingController.isPurchased(getApplicationContext(),
							BILLING_PRODUCT_YEAR)
							|| !BillingController.isPurchased(
									getApplicationContext(),
									BILLING_PRODUCT_FOREVER)) {
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								adView = new AdView(MainActivity.this,
										AdSize.SMART_BANNER, "a15042277f73506");
								adView.loadAd(new AdRequest());

								LayoutParams params = new LayoutParams(
										LayoutParams.FILL_PARENT,
										LayoutParams.FILL_PARENT);
								((LinearLayout) findViewById(R.id.ad_container))
										.addView(adView, params);
							}
						});
					}
				} catch (final Exception e) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							showCrouton(MainActivity.this,
									R.string.crouton_error_billing,
									new Runnable() {

										@Override
										public void run() {
											ReportUtil.createFeedbackIntent(
													MainActivity.this, e);
										}
									}, AppMsg.STYLE_ALERT);
						}
					});
				}
			}
		}.start();
	}

	@Override
	protected void onStart() {
		super.onStart();

		// TODO: ugly.
		new Thread() {
			public void run() {
				try {
					if (BillingController.isPurchased(getApplicationContext(),
							BILLING_PRODUCT_YEAR)
							|| BillingController.isPurchased(
									getApplicationContext(),
									BILLING_PRODUCT_FOREVER)) {
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								if (adView != null)
									adView.setVisibility(View.GONE);
							}
						});
					}
				} catch (final Exception e) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							showCrouton(MainActivity.this,
									R.string.crouton_error_billing,
									new Runnable() {

										@Override
										public void run() {
											ReportUtil.createFeedbackIntent(
													MainActivity.this, e);
										}
									}, AppMsg.STYLE_ALERT);
						}
					});
				}
			}
		}.start();
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();

		// TODO: god, it's so ugly... doing that because commiting fragments is
		// not allowed "after onSaveInstanceState"
		if (resultData != null) {
			loadUri(resultData);

			resultData = null;
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (intent.getData() != null) {
			loadUri(intent.getData());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu_main, menu);

		return true;
	}

	@Override
	public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_page_list: {
			if (documentFragment.getPages().size() > 1) {
				List<Part> pages = documentFragment.getPages();
				int size = pages.size();
				String[] names = new String[size];
				for (int i = 0; i < size; i++) {
					names[i] = pages.get(i).getName();
				}

				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.dialog_page_title);
				builder.setItems(names, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int item) {
						documentFragment.goToPage(item);
					}
				});
				builder.show();
			} else {
				Toast.makeText(this, R.string.toast_error_only_one_page,
						Toast.LENGTH_LONG).show();
			}

			break;
		}

		case R.id.menu_search: {
			// http://www.androidsnippets.org/snippets/20/
			final AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setTitle(getString(R.string.menu_search));

			final EditText input = new EditText(this);
			alert.setView(input);

			alert.setPositiveButton(getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							documentFragment.searchDocument(input.getText()
									.toString());
						}
					});
			alert.setNegativeButton(getString(android.R.string.cancel), null);
			alert.show();

			break;
		}

		case R.id.menu_open: {
			findDocument(this);

			break;
		}

		case R.id.menu_page_next: {
			if (!documentFragment.nextPage())
				showToast(this, R.string.toast_error_no_next);

			break;
		}

		case R.id.menu_page_previous: {
			if (!documentFragment.previousPage())
				showToast(this, R.string.toast_error_no_previous);

			break;
		}

		case R.id.menu_remove_ads: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.dialog_remove_ads_title);
			builder.setItems(R.array.remove_ads_options, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						BillingController.requestPurchase(MainActivity.this,
								BILLING_PRODUCT_YEAR, true, null);

						break;

					case 1:
						BillingController.requestPurchase(MainActivity.this,
								BILLING_PRODUCT_FOREVER, true, null);

						break;

					default:
						if (adView != null)
							adView.setVisibility(View.GONE);

						break;
					}

					dialog.dismiss();
				}
			});
			builder.show();

			break;
		}

		case R.id.menu_about: {
			loadUri(DocumentLoader.URI_INTRO);

			break;
		}

		case R.id.menu_feedback: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.dialog_feedback_title);
			builder.setMessage(R.string.dialog_feedback_message);
			builder.setPositiveButton(android.R.string.ok,
					new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							startActivity(new Intent(
									Intent.ACTION_VIEW,
									Uri.parse("https://opendocument.uservoice.com/")));
						}
					});
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.show();

			break;
		}
		}

		return super.onMenuItemSelected(featureId, item);
	}

	public DocumentLoader loadUri(Uri uri) {
		return loadUri(uri, null);
	}

	public DocumentLoader loadUri(Uri uri, String password) {
		Bundle bundle = new Bundle();
		bundle.putString(EXTRA_PASSWORD, password);
		bundle.putParcelable(EXTRA_URI, uri);

		return (DocumentLoader) getSupportLoaderManager().restartLoader(0,
				bundle, this);
	}

	@Override
	public Loader<Document> onCreateLoader(int id, Bundle bundle) {
		showProgress();

		Uri uri;
		String password = null;
		if (bundle != null) {
			uri = bundle.getParcelable(EXTRA_URI);
			password = bundle.getString(EXTRA_PASSWORD);
		} else {
			uri = DocumentLoader.URI_INTRO;
		}

		DocumentLoader documentLoader = new DocumentLoader(this, uri);
		documentLoader.setPassword(password);

		return documentLoader;
	}

	@Override
	public void onLoadFinished(Loader<Document> loader, Document document) {
		dismissProgress();

		Throwable lastError = ((DocumentLoader) loader).getLastError();
		Uri uri = ((DocumentLoader) loader).getLastUri();
		if (lastError != null) {
			onError(lastError, uri);
		} else if (document != null) {
			documentFragment.loadDocument(document);
		} else {
			onError(new IllegalStateException("document and lastError null"),
					uri);
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
		if (requestCode == 42 && resultCode == RESULT_OK && uri != null) {
			resultData = uri;
		}
	}

	private void showProgress() {
		if (progressDialog != null)
			return;

		FragmentTransaction transaction = getSupportFragmentManager()
				.beginTransaction();

		progressDialog = new ProgressDialogFragment();
		progressDialog.show(transaction, ProgressDialogFragment.FRAGMENT_TAG);
	}

	private void dismissProgress() {
		// dirty hack because committing isn't allowed in onLoadFinished
		new Handler(getMainLooper()).post(new Runnable() {

			@Override
			public void run() {
				if (progressDialog == null)
					progressDialog = (ProgressDialogFragment) getSupportFragmentManager()
							.findFragmentByTag(
									ProgressDialogFragment.FRAGMENT_TAG);

				if (progressDialog != null && progressDialog.getShowsDialog()) {
					progressDialog.dismiss();

					progressDialog = null;
				}
			}
		});
	}

	public void onError(Throwable error, final Uri uri) {
		Log.e("OpenDocument Reader", "Error opening file at " + uri.toString(),
				error);

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
			errorDescription = R.string.toast_error_open_file;
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

		showCrouton(this, errorDescription, null, AppMsg.STYLE_ALERT);

		if (uri.toString().endsWith(".odt") || uri.toString().endsWith(".ods")
				|| uri.toString().endsWith(".ott")
				|| uri.toString().endsWith(".ots"))
			ReportUtil.submitFile(this, error, uri, errorDescription);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (adView != null)
			adView.destroy();

		BillingController.unregisterObserver(billingObserver);
		BillingController.setConfiguration(null);
	}

	private static void findDocument(final Activity activity) {
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/vnd.oasis.opendocument.*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		PackageManager pm = activity.getPackageManager();
		final List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
		int size = targets.size();
		String[] targetNames = new String[size];
		for (int i = 0; i < size; i++) {
			targetNames[i] = targets.get(i).loadLabel(pm).toString();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.dialog_choose_filemanager);
		builder.setItems(targetNames, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				ResolveInfo target = targets.get(which);
				intent.setComponent(new ComponentName(
						target.activityInfo.packageName,
						target.activityInfo.name));

				if (FilePicker.class.getCanonicalName().equals(
						target.activityInfo.name)) {
					FilePickerAPI.setKey("Ao7lHjOFkSnuR9mgQ5Jhtz");

					intent.putExtra("services", new String[] {
							FPService.DROPBOX, FPService.BOX, FPService.GDRIVE,
							FPService.GMAIL });

					intent.setType(null);
				}

				activity.startActivityForResult(intent, 42);

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private static void showToast(Context context, int resId) {
		showToast(context, context.getString(resId));
	}

	private static void showToast(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
	}

	private static void showCrouton(Activity activity, int resId,
			final Runnable callback, AppMsg.Style style) {
		showCrouton(activity, activity.getString(resId), callback, style);
	}

	private static void showCrouton(final Activity activity, String message,
			final Runnable callback, AppMsg.Style style) {
		AppMsg crouton = AppMsg.makeText(activity, message, style);
		crouton.setDuration(AppMsg.LENGTH_LONG);
		crouton.getView().setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (callback != null)
					activity.runOnUiThread(callback);
			}
		});
		crouton.show();
	}

	// taken from net.robotmedia.billing.helper.AbstractBillingActivity
	protected AbstractBillingObserver billingObserver;

	@Override
	public byte[] getObfuscationSalt() {
		return new byte[] { 16, 1, 19, 93, -16, -1, -19, -93, 23, 7 };
	}

	@Override
	public String getPublicKey() {
		return "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDsdGybFkj9/26Fpu2mNASpAC8xQDRYocvVkxbpN6mF8k4a9L5ocnyUAY7sfKb0wjEc5e+vxL21kFKvvW0zEZX8a5wSXUfD5oiaXaiMPrp7cC1YbPPAelZvFEAzriA6pyk7PPKuqtAN2tcTiJED+kpiVAyEVU42lDUqE70xlRE6dQIDAQAB";
	}
}
