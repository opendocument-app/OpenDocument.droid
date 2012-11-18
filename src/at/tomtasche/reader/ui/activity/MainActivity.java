package at.tomtasche.reader.ui.activity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.zip.ZipException;

import net.robotmedia.billing.BillingController;
import net.robotmedia.billing.BillingRequest.ResponseCode;
import net.robotmedia.billing.helper.AbstractBillingObserver;
import net.robotmedia.billing.model.Transaction;
import net.robotmedia.billing.model.Transaction.PurchaseState;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class MainActivity extends FragmentActivity implements
		BillingController.IConfiguration, LoaderCallbacks<Document> {

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";

	private static final String EXTRA_URI = "uri";
	private static final String EXTRA_PASSWORD = "password";

	private DocumentFragment documentFragment;
	private ProgressDialog progressDialog;
	private AdRequest adRequest;
	private AdView adView;

	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);

		setTitle("");
		setContentView(R.layout.main);

		documentFragment = (DocumentFragment) getSupportFragmentManager()
				.findFragmentByTag(DocumentFragment.FRAGMENT_TAG);
		if (documentFragment == null) {
			documentFragment = new DocumentFragment();
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.document_container, documentFragment,
							DocumentFragment.FRAGMENT_TAG).commit();

			if (getIntent().getData() != null) {
				loadUri(getIntent().getData());
			} else {
				loadUri(DocumentLoader.URI_INTRO);
			}
		}

		billingObserver = new AbstractBillingObserver(this) {

			public void onBillingChecked(boolean supported) {
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
				}
			}
		};
		BillingController.registerObserver(billingObserver);
		BillingController.setConfiguration(this);
		if (!billingObserver.isTransactionsRestored()) {
			BillingController.restoreTransactions(this);
		}

		adRequest = new AdRequest();
		adRequest.addKeyword("office");
		adRequest.addKeyword("productivity");
		adRequest.addKeyword("document");
		if (!BillingController.isPurchased(this, BILLING_PRODUCT_YEAR)
				|| !BillingController
						.isPurchased(this, BILLING_PRODUCT_FOREVER)) {
			adView = new AdView(this, AdSize.SMART_BANNER, "a15042277f73506");
			adView.loadAd(adRequest);

			((LinearLayout) findViewById(R.id.ad_container)).addView(adView);
		}

		getSupportLoaderManager().initLoader(0, null, this);
	}

	@Override
	protected void onStart() {
		super.onStart();

		if (BillingController.isPurchased(this, BILLING_PRODUCT_YEAR)
				|| BillingController.isPurchased(this, BILLING_PRODUCT_FOREVER)) {
			if (adView != null)
				adView.setVisibility(View.GONE);
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
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
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
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.page_dialog_title));

				List<Part> pages = documentFragment.getPages();
				String[] items = new String[pages.size()];
				for (int i = 0; i < pages.size(); i++) {
					items[i] = pages.get(i).getName();
				}

				builder.setItems(items, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int item) {
						documentFragment.goToPage(item);
					}
				});
				builder.create().show();
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
						public void onClick(final DialogInterface dialog,
								final int whichButton) {
							documentFragment.searchDocument(input.getText()
									.toString());
						}
					});
			alert.setNegativeButton(getString(android.R.string.cancel), null);
			alert.show();

			break;
		}

		case R.id.menu_open: {
			findDocument();

			break;
		}

		case R.id.menu_page_next: {
			if (!documentFragment.nextPage())
				showToast(R.string.toast_error_no_next);

			break;
		}

		case R.id.menu_page_previous: {
			if (!documentFragment.previousPage())
				showToast(R.string.toast_error_no_previous);

			break;
		}

		case R.id.menu_remove_ads_for_1y: {
			BillingController.requestPurchase(this, BILLING_PRODUCT_YEAR, true,
					null);

			break;
		}

		case R.id.menu_remove_ads_forever: {
			BillingController.requestPurchase(this, BILLING_PRODUCT_FOREVER,
					true, null);

			break;
		}

		case R.id.menu_remove_ads: {
			if (adView != null)
				adView.setVisibility(View.GONE);

			break;
		}

		case R.id.menu_about: {
			loadUri(DocumentLoader.URI_INTRO);

			break;
		}
		}

		return super.onMenuItemSelected(featureId, item);
	}

	private void loadUri(Uri uri) {
		loadUri(uri, null);
	}

	private void loadUri(Uri uri, String password) {
		Bundle bundle = new Bundle();
		bundle.putString(EXTRA_PASSWORD, password);
		bundle.putParcelable(EXTRA_URI, uri);

		getSupportLoaderManager().restartLoader(0, bundle, this);
	}

	@Override
	public Loader<Document> onCreateLoader(int id, Bundle bundle) {
		if (progressDialog == null || !progressDialog.isShowing()) {
			progressDialog = new ProgressDialog(this);
			progressDialog.setTitle(getString(R.string.dialog_loading_title));
			progressDialog
					.setMessage(getString(R.string.dialog_loading_message));
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			progressDialog.show();
		}

		Uri uri = bundle.getParcelable(EXTRA_URI);
		String password = bundle.getParcelable(EXTRA_PASSWORD);

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

	private void installExplorer() {
		final String[] explorerUrls = new String[] {
				"https://play.google.com/store/apps/details?id=org.openintents.filemanager",
				"https://play.google.com/store/apps/details?id=com.speedsoftware.explorer" };
		String[] explorerNames = new String[] { "OI File Manager", "Explorer" };

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_no_filemanager);
		builder.setItems(explorerNames, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent explorerIntent = new Intent(Intent.ACTION_VIEW);
				explorerIntent.setData(Uri.parse(explorerUrls[which]));

				startActivity(explorerIntent);

				dialog.dismiss();
			}
		});
		builder.create().show();
	}

	private void findDocument() {
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/vnd.oasis.opendocument.*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		final List<ResolveInfo> targets = getPackageManager()
				.queryIntentActivities(intent, 0);
		if (targets.size() == 0) {
			installExplorer();
		} else {
			final String[] targetNames = new String[targets.size() + 1];
			for (int i = 0; i < targets.size(); i++) {
				targetNames[i] = targets.get(i).loadLabel(getPackageManager())
						.toString();
			}

			targetNames[targetNames.length - 1] = getString(R.string.dialog_find_explorer);

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.dialog_choose_filemanager);
			builder.setItems(targetNames, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which < targets.size()) {
						ResolveInfo target = targets.get(which);
						intent.setComponent(new ComponentName(
								target.activityInfo.packageName,
								target.activityInfo.name));

						startActivityForResult(intent, 42);
					} else {
						installExplorer();
					}

					dialog.dismiss();
				}
			});
			builder.create().show();
		}
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == 42 && resultCode == RESULT_OK && data != null
				&& data.getData() != null) {
			loadUri(data.getData());
		}
	}

	private void showToast(int resId) {
		showToast(getString(resId));
	}

	private void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	private void dismissProgress() {
		if (progressDialog != null && progressDialog.isShowing())
			progressDialog.dismiss();
	}

	public void onError(Throwable error, final Uri uri) {
		int errorDescription;
		if (error == null) {
			return;
		} else if (error instanceof EncryptedDocumentException) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.toast_error_password_protected);

			final EditText input = new EditText(this);
			input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
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

		submitFile(error, uri, errorDescription);
	}

	private void submitFile(final Throwable error, final Uri uri,
			final int errorDescription) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.toast_error_generic);
		builder.setMessage(getString(errorDescription)
				+ System.getProperty("line.separator")
				+ getString(R.string.dialog_submit_file));
		builder.setNegativeButton(android.R.string.no, null);
		builder.setNeutralButton(R.string.dialog_error_send_error_only,
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						startActivity(ReportUtil.createFeedbackIntent(
								MainActivity.this, error));
					}
				});
		builder.setPositiveButton(android.R.string.yes, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Bundle bundle = new Bundle();
				bundle.putStringArray(Intent.EXTRA_EMAIL,
						new String[] { "tomtasche+reader@gmail.com" });
				bundle.putParcelable(Intent.EXTRA_STREAM, uri);

				String version;
				try {
					version = getPackageManager().getPackageInfo(
							getPackageName(), 0).versionName;
				} catch (NameNotFoundException e1) {
					version = "unknown";
				}
				bundle.putString(Intent.EXTRA_SUBJECT, "OpenDocument Reader ("
						+ version + "): Couldn't open file");

				StringWriter writer = new StringWriter();
				PrintWriter printer = new PrintWriter(writer);
				printer.println("Important information for the developer:");
				printer.println("- " + Build.MODEL + " running Android "
						+ Build.VERSION.SDK_INT);
				printer.println("- The following error occured while opening the file located at: "
						+ uri.toString());
				printer.println(getString(errorDescription));
				printer.println();
				error.printStackTrace(printer);
				printer.println();
				printer.println("-----------------");
				printer.println("Feel free to append further information here.");

				try {
					printer.close();
					writer.close();
				} catch (IOException e) {
				}

				bundle.putString(Intent.EXTRA_TEXT, writer.toString());

				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("plain/text");
				intent.putExtras(bundle);

				startActivity(Intent.createChooser(intent,
						getString(R.string.dialog_submit_file_title)));

				dialog.dismiss();
			}
		});

		builder.show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (adView != null)
			adView.destroy();

		BillingController.unregisterObserver(billingObserver);
		BillingController.setConfiguration(null);
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
