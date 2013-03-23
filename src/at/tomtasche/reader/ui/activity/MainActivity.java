package at.tomtasche.reader.ui.activity;

import io.filepicker.FPService;
import io.filepicker.FilePicker;
import io.filepicker.FilePickerAPI;

import java.util.List;

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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.ReportUtil;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.devspark.appmsg.AppMsg;
import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class MainActivity extends DocumentActivity implements
		BillingController.IConfiguration {

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";

	private AdView adView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		billingObserver = new AbstractBillingObserver(this) {

			public void onBillingChecked(boolean supported) {
				if (!supported)
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							showCrouton(
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
							showCrouton(
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
							showCrouton(R.string.crouton_error_billing,
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
							showCrouton(R.string.crouton_error_billing,
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
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getSupportMenuInflater().inflate(R.menu.menu_main, menu);

		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
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
							getPageFragment().searchDocument(
									input.getText().toString());
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

	public void findDocument() {
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/vnd.oasis.opendocument.*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		PackageManager pm = getPackageManager();
		final List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
		int size = targets.size();
		String[] targetNames = new String[size];
		for (int i = 0; i < size; i++) {
			targetNames[i] = targets.get(i).loadLabel(pm).toString();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

				startActivityForResult(intent, 42);

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
