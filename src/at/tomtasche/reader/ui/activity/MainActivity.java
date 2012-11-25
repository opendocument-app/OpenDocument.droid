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
import android.app.ProgressDialog;
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
		if (!billingObserver.isTransactionsRestored())
			BillingController.restoreTransactions(this);

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
				builder.setTitle(getString(R.string.page_dialog_title));
				builder.setItems(names, new DialogInterface.OnClickListener() {

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

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (intent == null)
			return;

		Uri uri = intent.getData();
		if (requestCode == 42 && resultCode == RESULT_OK && intent != null
				&& uri != null) {
			loadUri(uri);
		}
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
		FilePickerAPI.setKey("Ao7lHjOFkSnuR9mgQ5Jhtz");

		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/vnd.oasis.opendocument.*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		PackageManager pm = activity.getPackageManager();
		final List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
		if (targets.size() == 0) {
			installExplorer(activity);
		} else {
			int size = targets.size();
			final String[] targetNames = new String[size + 1];
			for (int i = 0; i < size; i++) {
				targetNames[i] = targets.get(i).loadLabel(pm).toString();
			}

			targetNames[targetNames.length - 1] = activity
					.getString(R.string.dialog_find_explorer);

			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle(R.string.dialog_choose_filemanager);
			builder.setItems(targetNames, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which < targets.size()) {
						ResolveInfo target = targets.get(which);
						intent.setComponent(new ComponentName(
								target.activityInfo.packageName,
								target.activityInfo.name));

						if (FilePicker.class.getCanonicalName().equals(
								target.activityInfo.name)) {
							intent.putExtra("services", new String[] {
									FPService.DROPBOX, FPService.BOX,
									FPService.GDRIVE, FPService.GMAIL });

							intent.setType(null);
						}

						activity.startActivityForResult(intent, 42);
					} else {
						installExplorer(activity);
					}

					dialog.dismiss();
				}
			});
			builder.show();
		}
	}

	private static void installExplorer(final Context context) {
		final String[] explorerUrls = new String[] {
				"https://play.google.com/store/apps/details?id=org.openintents.filemanager",
				"https://play.google.com/store/apps/details?id=com.speedsoftware.explorer" };
		String[] explorerNames = new String[] { "OI File Manager", "Explorer" };

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.dialog_no_filemanager);
		builder.setItems(explorerNames, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent explorerIntent = new Intent(Intent.ACTION_VIEW);
				explorerIntent.setData(Uri.parse(explorerUrls[which]));

				context.startActivity(explorerIntent);

				dialog.dismiss();
			}
		});
		builder.create().show();
	}

	private static void showToast(Context context, int resId) {
		showToast(context, context.getString(resId));
	}

	private static void showToast(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
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
