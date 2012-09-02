package at.tomtasche.reader.ui.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import net.robotmedia.billing.BillingController;
import net.robotmedia.billing.BillingController.BillingStatus;
import net.robotmedia.billing.BillingRequest.ResponseCode;
import net.robotmedia.billing.helper.AbstractBillingActivity;
import net.robotmedia.billing.helper.AbstractBillingObserver;
import net.robotmedia.billing.model.Transaction;
import net.robotmedia.billing.model.Transaction.PurchaseState;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import at.andiwand.odf2html.odf.IllegalMimeTypeException;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Part;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.OnErrorCallback;
import at.tomtasche.reader.background.DocumentLoader.OnSuccessCallback;
import at.tomtasche.reader.ui.widget.DocumentFragment;

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class MainActivity extends FragmentActivity implements OnSuccessCallback, OnErrorCallback,
	BillingController.IConfiguration {

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
	getSupportFragmentManager().beginTransaction()
		.add(R.id.document_container, documentFragment).commit();

	mBillingObserver = new AbstractBillingObserver(this) {

	    public void onBillingChecked(boolean supported) {
	    }

	    public void onSubscriptionChecked(boolean supported) {
	    }

	    public void onPurchaseStateChanged(String itemId, PurchaseState state) {
		MainActivity.this.onPurchaseStateChanged(itemId, state);
	    }

	    public void onRequestPurchaseResponse(String itemId, ResponseCode response) {
	    }
	};
	BillingController.registerObserver(mBillingObserver);
	BillingController.setConfiguration(this);
	// This activity will provide the public key and salt
	this.checkBillingSupported();
	if (!mBillingObserver.isTransactionsRestored()) {
	    BillingController.restoreTransactions(this);
	}

	if (!BillingController.isPurchased(this, "remove_ads_1y")
		|| !BillingController.isPurchased(this, "remove_ads_for_eva")) {
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
    protected void onNewIntent(Intent intent) {
	super.onNewIntent(intent);

	if (intent.getData() != null) {
	    loadUri(intent.getData());
	} else {
	    loadUri(DocumentLoader.URI_INTRO);
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
		Toast.makeText(this, "There's only one page", Toast.LENGTH_LONG).show();
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
			public void onClick(final DialogInterface dialog, final int whichButton) {
			    documentFragment.searchDocument(input.getText().toString());
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
	    documentFragment.nextPage();

	    break;
	}

	case R.id.menu_page_previous: {
	    documentFragment.previousPage();

	    break;
	}

	case R.id.menu_remove_ads_for_1y: {
	    BillingController.requestPurchase(this, "remove_ads_for_1y", true, null);

	    break;
	}

	case R.id.menu_remove_ads_forever: {
	    BillingController.requestPurchase(this, "remove_ads_for_eva", true, null);

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
	Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
	intent.setType("application/vnd.oasis.opendocument.*");
	intent.addCategory(Intent.CATEGORY_OPENABLE);

	List<ResolveInfo> targets = getPackageManager().queryIntentActivities(intent, 0);
	if (targets.size() == 0) {
	    String[] explorerNames = new String[] { "OI File Manager", "Explorer" };
	    final String[] explorerUrls = new String[] {
		    "https://play.google.com/store/apps/details?id=org.openintents.filemanager",
		    "https://play.google.com/store/apps/details?id=com.speedsoftware.explorer" };

	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle("No file explorer found. We recommend one of these:");
	    builder.setItems(explorerNames, new OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
		    Intent intent = new Intent(Intent.ACTION_VIEW);
		    intent.setData(Uri.parse(explorerUrls[which]));

		    startActivity(intent);

		    dialog.dismiss();
		}
	    });
	    builder.create().show();
	} else {
	    startActivityForResult(intent, 42);
	}
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
	super.onActivityResult(requestCode, resultCode, data);

	if (requestCode == 42 && resultCode == RESULT_OK && data != null && data.getData() != null) {
	    loadUri(data.getData());
	} else if (requestCode == 1993) {
	    if (BillingController.isPurchased(this, "remove_ads_1y")
		    || BillingController.isPurchased(this, "remove_ads_4eva")) {
		if (adView != null)
		    adView.setVisibility(View.GONE);
	    }
	}
    }

    private void showToast(int resId) {
	Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onError(Throwable error) {
	if (error instanceof IllegalMimeTypeException) {
	    showToast(R.string.toast_error_open_file);
	} else if (error instanceof FileNotFoundException) {
	    showToast(R.string.toast_error_find_file);
	} else if (error instanceof IllegalArgumentException) {
	    showToast(R.string.toast_error_illegal_file);
	} else if (error instanceof OutOfMemoryError) {
	    showToast(R.string.toast_error_out_of_memory);
	} else {
	    showToast(R.string.toast_error_generic);
	}
    }

    @Override
    public void onSuccess(Document document) {
	documentFragment.loadDocument(document);
    }

    protected void loadUri(Uri uri) {
	DocumentLoader documentLoader = new DocumentLoader(this);
	documentLoader.setOnSuccessCallback(this);
	documentLoader.setOnErrorCallback(this);
	documentLoader.execute(uri);
    }

    @Override
    protected void onDestroy() {
	super.onDestroy();

	if (adView != null)
	    adView.destroy();

	BillingController.unregisterObserver(mBillingObserver);
	// Avoid receiving notifications after destroy
	BillingController.setConfiguration(null);

	// TODO: ugly threading
	new Thread() {

	    @Override
	    public void run() {
		// clean cache
		for (final String s : getCacheDir().list()) {
		    try {
			new File(getCacheDir() + "/" + s).delete();
		    } catch (final Exception e) {
			e.printStackTrace();
		    }
		}
	    }
	}.start();
    }

    // taken from net.robotmedia.billing.helper.AbstractBillingActivity

    protected AbstractBillingObserver mBillingObserver;

    /**
     * <p>
     * Returns the in-app product billing support status, and checks it
     * asynchronously if it is currently unknown.
     * {@link AbstractBillingActivity#onBillingChecked(boolean)} will be called
     * eventually with the result.
     * </p>
     * <p>
     * In-app product support does not imply subscription support. To check if
     * subscriptions are supported, use
     * {@link AbstractBillingActivity#checkSubscriptionSupported()}.
     * </p>
     * 
     * @return the current in-app product billing support status (unknown,
     *         supported or unsupported). If it is unsupported, subscriptions
     *         are also unsupported.
     * @see AbstractBillingActivity#onBillingChecked(boolean)
     * @see AbstractBillingActivity#checkSubscriptionSupported()
     */
    public BillingStatus checkBillingSupported() {
	return BillingController.checkBillingSupported(this);
    }

    /**
     * <p>
     * Returns the subscription billing support status, and checks it
     * asynchronously if it is currently unknown.
     * {@link AbstractBillingActivity#onSubscriptionChecked(boolean)} will be
     * called eventually with the result.
     * </p>
     * <p>
     * No support for subscriptions does not imply that in-app products are also
     * unsupported. To check if subscriptions are supported, use
     * {@link AbstractBillingActivity#checkSubscriptionSupported()}.
     * </p>
     * 
     * @return the current in-app product billing support status (unknown,
     *         supported or unsupported). If it is unsupported, subscriptions
     *         are also unsupported.
     * @see AbstractBillingActivity#onBillingChecked(boolean)
     * @see AbstractBillingActivity#checkSubscriptionSupported()
     */
    public BillingStatus checkSubscriptionSupported() {
	return BillingController.checkSubscriptionSupported(this);
    }

    public void onPurchaseStateChanged(String itemId, PurchaseState state) {
	List<Transaction> transactions = BillingController.getTransactions(this);
	for (Transaction t : transactions) {
	    if (t.purchaseState == PurchaseState.PURCHASED) {
		if (adView != null)
		    adView.setVisibility(View.GONE);
	    }
	}
    }

    /**
     * Requests the purchase of the specified item. The transaction will not be
     * confirmed automatically; such confirmation could be handled in
     * {@link AbstractBillingActivity#onPurchaseExecuted(String)}. If automatic
     * confirmation is preferred use
     * {@link BillingController#requestPurchase(android.content.Context, String, boolean)}
     * instead.
     * 
     * @param itemId
     *            id of the item to be purchased.
     */
    public void requestPurchase(String itemId) {
	BillingController.requestPurchase(this, itemId);
    }

    /**
     * Requests the purchase of the specified subscription item. The transaction
     * will not be confirmed automatically; such confirmation could be handled
     * in {@link AbstractBillingActivity#onPurchaseExecuted(String)}. If
     * automatic confirmation is preferred use
     * {@link BillingController#requestPurchase(android.content.Context, String, boolean)}
     * instead.
     * 
     * @param itemId
     *            id of the item to be purchased.
     */
    public void requestSubscription(String itemId) {
	BillingController.requestSubscription(this, itemId);
    }

    /**
     * Requests to restore all transactions.
     */
    public void restoreTransactions() {
	BillingController.restoreTransactions(this);
    }

    @Override
    public byte[] getObfuscationSalt() {
	return new byte[] { 16, 1, 19, 93, -16, -1, -19, -93, 23, 7 };
    }

    @Override
    public String getPublicKey() {
	return "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDsdGybFkj9/26Fpu2mNASpAC8xQDRYocvVkxbpN6mF8k4a9L5ocnyUAY7sfKb0wjEc5e+vxL21kFKvvW0zEZX8a5wSXUfD5oiaXaiMPrp7cC1YbPPAelZvFEAzriA6pyk7PPKuqtAN2tcTiJED+kpiVAyEVU42lDUqE70xlRE6dQIDAQAB";
    }
}
