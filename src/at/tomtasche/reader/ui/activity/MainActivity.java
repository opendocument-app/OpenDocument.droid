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
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
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
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Part;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.DocumentLoader.OnErrorCallback;
import at.tomtasche.reader.background.DocumentLoader.OnSuccessCallback;
import at.tomtasche.reader.background.ReportUtil;
import at.tomtasche.reader.ui.widget.DocumentFragment;

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class MainActivity extends FragmentActivity implements
		OnSuccessCallback, OnErrorCallback, BillingController.IConfiguration {

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";
	private static final AdRequest AD_REQUEST;

	static {
		AD_REQUEST = new AdRequest();
		AD_REQUEST.addKeyword("office");
		AD_REQUEST.addKeyword("productivity");
		AD_REQUEST.addKeyword("document");
	}

	private DocumentFragment documentFragment;
	private AdView adView;

	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);

		setTitle("");
		setContentView(R.layout.main);

		documentFragment = new DocumentFragment();
		getSupportFragmentManager()
				.beginTransaction()
				.add(R.id.document_container, documentFragment,
						DocumentFragment.FRAGMENT_TAG).commit();

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

		if (!BillingController.isPurchased(this, BILLING_PRODUCT_YEAR)
				|| !BillingController
						.isPurchased(this, BILLING_PRODUCT_FOREVER)) {
			adView = new AdView(this, AdSize.SMART_BANNER, "a15042277f73506");
			adView.loadAd(AD_REQUEST);

			((LinearLayout) findViewById(R.id.ad_container)).addView(adView);
		}

		if (getIntent().getData() != null) {
			loadUri(getIntent().getData());
		} else {
			loadUri(DocumentLoader.URI_INTRO);
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
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (adView != null)
			adView.loadAd(AD_REQUEST);
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

	private void findDocument() {
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/vnd.oasis.opendocument.*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		final List<ResolveInfo> targets = getPackageManager()
				.queryIntentActivities(intent, 0);
		final String[] explorerUrls = new String[] {
				"https://play.google.com/store/apps/details?id=org.openintents.filemanager",
				"https://play.google.com/store/apps/details?id=com.speedsoftware.explorer" };
		String[] explorerNames = new String[] { "OI File Manager", "Explorer" };
		if (targets.size() == 0) {

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
		} else {
			final String[] targetNames = new String[targets.size() + 2];
			for (int i = 0; i < targets.size(); i++) {
				targetNames[i] = targets.get(i).loadLabel(getPackageManager())
						.toString();
			}

			for (int i = 0; i < explorerNames.length; i++) {
				targetNames[targetNames.length - 1 - i] = "Install "
						+ explorerNames[i];
			}

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
						String url = explorerUrls[targetNames.length - which
								- 1];

						startActivity(new Intent(Intent.ACTION_VIEW, Uri
								.parse(url)));
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

	@Override
	public void onError(Throwable error, final Uri uri) {
		if (error instanceof EncryptedDocumentException) {
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
		} else if (error instanceof IllegalMimeTypeException
				|| error instanceof ZipException
				|| error instanceof ZipEntryNotFoundException) {
			showToast(R.string.toast_error_open_file);

			if (Build.VERSION.SDK_INT >= 14)
				startActivity(ReportUtil.createFeedbackIntent(this, error));

			return;
		} else if (error instanceof FileNotFoundException) {
			if (Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED_READ_ONLY)
					|| Environment.getExternalStorageState().equals(
							Environment.MEDIA_MOUNTED)) {
				showToast(R.string.toast_error_find_file);

				if (Build.VERSION.SDK_INT >= 14)
					startActivity(ReportUtil.createFeedbackIntent(this, error));
			} else {
				showToast(R.string.toast_error_storage);
			}

			return;
		} else if (error instanceof IllegalArgumentException) {
			showToast(R.string.toast_error_illegal_file);
		} else if (error instanceof OutOfMemoryError) {
			showToast(R.string.toast_error_out_of_memory);
		} else {
			showToast(getString(R.string.toast_error_generic) + ": "
					+ error.getClass().getSimpleName());
		}

		submitFile(error, uri);
	}

	private void submitFile(final Throwable error, final Uri uri) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.toast_error_generic);
		builder.setMessage(R.string.dialog_submit_file);
		builder.setNegativeButton(android.R.string.no, null);
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
				printer.println(Build.MODEL + " running Android "
						+ Build.VERSION.SDK_INT);
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
	public void onSuccess(Document document) {
		documentFragment.loadDocument(document);
	}

	public DocumentLoader loadUri(Uri uri) {
		return loadUri(uri, null);
	}

	public DocumentLoader loadUri(Uri uri, String password) {
		DocumentLoader documentLoader = new DocumentLoader(this);
		documentLoader.setOnSuccessCallback(this);
		documentLoader.setOnErrorCallback(this);
		documentLoader.setPassword(password);
		documentLoader.execute(uri);

		return documentLoader;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (adView != null)
			adView.destroy();

		BillingController.unregisterObserver(billingObserver);
		BillingController.setConfiguration(null);

		// TODO: ugly threading
		new Thread() {

			@Override
			public void run() {
				AndroidFileCache.cleanup(MainActivity.this);
			}
		}.start();
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
