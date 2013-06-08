package at.tomtasche.reader.ui.activity;

import google.com.android.cloudprint.PrintDialogActivity;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.BillingPreferences;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Page;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.LoadingListener;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.widget.DocumentChooserDialogFragment;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.bugsense.trace.BugSenseHandler;
import com.devspark.appmsg.AppMsg;
import com.github.jberkel.pay.me.IabHelper;
import com.github.jberkel.pay.me.IabResult;
import com.github.jberkel.pay.me.listener.OnIabPurchaseFinishedListener;
import com.github.jberkel.pay.me.listener.OnIabSetupFinishedListener;
import com.github.jberkel.pay.me.listener.QueryInventoryFinishedListener;
import com.github.jberkel.pay.me.model.Inventory;
import com.github.jberkel.pay.me.model.ItemType;
import com.github.jberkel.pay.me.model.Purchase;
import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;
import com.google.analytics.tracking.android.EasyTracker;

public class MainActivity extends DocumentActivity implements
		ActionBar.TabListener, LoadingListener {

	private int PURCHASE_CODE = 1337;

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";

	private static final String EXTRA_TAB_POSITION = "tab_position";

	private AdView adView;

	private int lastPosition;
	private boolean fullscreen;

	// private List<DocumentPresentation> presentations;

	private IabHelper billingHelper;
	private BillingPreferences billingPreferences;

	// @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	// public class DocumentPresentation extends Presentation implements
	// LoadingListener {
	//
	// private PageView pageView;
	//
	// public DocumentPresentation(Context outerContext, Display display) {
	// super(outerContext, display);
	// }
	//
	// @Override
	// protected void onCreate(Bundle savedInstanceState) {
	// super.onCreate(savedInstanceState);
	//
	// pageView = new PageView(getContext());
	// pageView.loadData(
	// getContext().getString(R.string.message_get_started),
	// "text/plain", PageView.ENCODING);
	//
	// pageView.setLayoutParams(new LinearLayout.LayoutParams(
	// LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
	//
	// setContentView(pageView);
	// }
	//
	// @Override
	// public void onSuccess(Document document, Uri uri) {
	// }
	//
	// @Override
	// public void onError(Throwable error, Uri uri) {
	// // trolololo :)
	// pageView.loadUrl("http://goo.gl/HgQJc");
	// }
	//
	// public void loadPage(Page page) {
	// pageView.loadUrl(page.getUrl());
	// }
	// }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// if (ActivityManager.isUserAMonkey())
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		// WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
		// .detectAll().penaltyLog().penaltyDeath().build());
		// StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
		// .penaltyLog().penaltyDeath().build());

		BugSenseHandler.initAndStartSession(this, "efe5d68e");

		// presentations = new LinkedList<MainActivity.DocumentPresentation>();
		// TODO: fix zoom
		// TODO: listen for connected / disconnected displays:
		// http://blog.stylingandroid.com/archives/1440
		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
		// multiscreen();

		EasyTracker.getInstance().activityStart(this);

		if (savedInstanceState != null) {
			lastPosition = savedInstanceState.getInt(EXTRA_TAB_POSITION);
		} else if (getIntent().getData() == null) {
			EasyTracker.getTracker().sendEvent("ui", "open", "intro", null);
		}

		addLoadingListener(this);

		billingPreferences = new BillingPreferences(this);

		billingHelper = new IabHelper(this, getPublicKey());
		billingHelper.startSetup(new OnIabSetupFinishedListener() {

			@Override
			public void onIabSetupFinished(IabResult result) {
				if (billingPreferences.hasPurchased()) {
					return;
				}

				if (result.isFailure()) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							showAds();

							showCrouton(
									getString(R.string.crouton_error_billing),
									null, AppMsg.STYLE_ALERT);
						}
					});
				} else if (result.isSuccess()) {
					// query every 30 days
					if ((billingPreferences.getLastQueryTime() + 1000 * 60 * 60
							* 24 * 30) < System.currentTimeMillis()) {
						billingHelper
								.queryInventoryAsync(new QueryInventoryFinishedListener() {

									@Override
									public void onQueryInventoryFinished(
											IabResult result, Inventory inv) {
										if (result.isSuccess()) {
											boolean purchased = inv
													.getPurchase(BILLING_PRODUCT_FOREVER) != null;
											purchased |= inv
													.getPurchase(BILLING_PRODUCT_YEAR) != null;

											if (purchased) {
												removeAds();
											} else {
												showAds();
											}

											billingPreferences
													.setPurchased(purchased);
										}

										billingPreferences
												.setLastQueryTime(System
														.currentTimeMillis());
									}
								});
					}
				}
			}
		});
	}

	private void showAds() {
		adView = new AdView(MainActivity.this, AdSize.SMART_BANNER,
				"a15042277f73506");
		adView.loadAd(new AdRequest());

		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);
		((LinearLayout) findViewById(R.id.ad_container))
				.addView(adView, params);

		EasyTracker.getTracker().sendEvent("monetization", "ads", "show", null);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (intent.getData() != null) {
			loadUri(intent.getData());

			EasyTracker.getTracker().sendEvent("ui", "open", "other", null);
		}
	}

	// @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	// private void multiscreen() {
	// DisplayManager displayManager = (DisplayManager)
	// getSystemService(DISPLAY_SERVICE);
	// if (displayManager != null) {
	// Display[] displays = displayManager
	// .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
	// for (Display display : displays) {
	// DocumentPresentation presentation = new DocumentPresentation(
	// this, display);
	// presentation.show();
	//
	// addLoadingListener(presentation);
	//
	// presentations.add(presentation);
	// }
	// }
	// }

	// @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	// private void loadPageOnMultiscreens(Page page) {
	// for (DocumentPresentation presentation : presentations) {
	// presentation.loadPage(page);
	// }
	// }

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(EXTRA_TAB_POSITION, getSupportActionBar()
				.getSelectedNavigationIndex());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getSupportMenuInflater().inflate(R.menu.menu_main, menu);

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (requestCode == PURCHASE_CODE) {
			billingHelper.handleActivityResult(requestCode, resultCode, intent);
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_recent: {
			FragmentTransaction transaction = getSupportFragmentManager()
					.beginTransaction();

			DialogFragment chooserDialog = new DocumentChooserDialogFragment();
			chooserDialog.show(transaction,
					DocumentChooserDialogFragment.FRAGMENT_TAG);

			EasyTracker.getTracker().sendEvent("ui", "open", "recent", null);

			break;
		}

		case R.id.menu_search: {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
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
				alert.setNegativeButton(getString(android.R.string.cancel),
						null);
				alert.show();
			} else {
				FindActionModeCallback findActionModeCallback = new FindActionModeCallback(
						this);
				findActionModeCallback.setWebView(getPageFragment()
						.getPageView());
				startActionMode(findActionModeCallback);
			}

			EasyTracker.getTracker().sendEvent("ui", "search", "start", null);

			break;
		}

		case R.id.menu_open: {
			findDocument();

			EasyTracker.getTracker().sendEvent("ui", "open", "choose", null);

			break;
		}

		case R.id.menu_remove_ads: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.dialog_remove_ads_title);
			builder.setItems(R.array.remove_ads_options, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					String product = null;

					switch (which) {
					case 0:
						product = BILLING_PRODUCT_YEAR;

						break;

					case 1:
						product = BILLING_PRODUCT_FOREVER;

						break;

					default:
						removeAds();

						dialog.dismiss();

						return;
					}

					billingHelper.launchPurchaseFlow(MainActivity.this,
							product, ItemType.INAPP, PURCHASE_CODE,
							new OnIabPurchaseFinishedListener() {
								public void onIabPurchaseFinished(
										IabResult result, Purchase purchase) {
									// remove ads even if the purchase failed /
									// the user canceled the purchase
									runOnUiThread(new Runnable() {

										@Override
										public void run() {
											removeAds();
										}
									});

									if (result.isSuccess()) {
										billingPreferences.setPurchased(true);
										billingPreferences
												.setLastQueryTime(System
														.currentTimeMillis());

										EasyTracker.getTracker().sendEvent(
												"monetization", "in-app",
												purchase.getSku(), null);
									} else {
										EasyTracker.getTracker().sendEvent(
												"monetization", "in-app",
												"failed", null);
									}
								}
							}, null);

					dialog.dismiss();
				}
			});
			builder.show();

			break;
		}

		case R.id.menu_about: {
			loadUri(DocumentLoader.URI_ABOUT);

			EasyTracker.getTracker().sendEvent("ui", "open", "about", null);

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

			EasyTracker.getTracker()
					.sendEvent("ui", "feedback", "launch", null);

			break;
		}
		case R.id.menu_reload: {
			Loader<Document> loader = getSupportLoaderManager().getLoader(0);
			DocumentLoader documentLoader = (DocumentLoader) loader;

			loadUri(documentLoader.getLastUri(), documentLoader.getPassword(),
					false);

			EasyTracker.getTracker()
					.sendEvent("ui", "reload", "no-limit", null);

			break;
		}
		case R.id.menu_fullscreen: {
			if (fullscreen) {
				leaveFullscreen();
			} else {
				getWindow().setFlags(
						WindowManager.LayoutParams.FLAG_FULLSCREEN,
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
				getWindow().clearFlags(
						WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

				getSupportActionBar().hide();

				removeAds();

				showCrouton(R.string.crouton_leave_fullscreen, new Runnable() {

					@Override
					public void run() {
						leaveFullscreen();
					}
				}, AppMsg.STYLE_INFO);

				EasyTracker.getTracker().sendEvent("ui", "fullscreen", "enter",
						null);
			}

			fullscreen = !fullscreen;

			break;
		}
		case R.id.menu_share: {
			Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
			intent.setType("text/html");

			ArrayList<Uri> uris = new ArrayList<Uri>();
			for (Page page : getDocument().getPages()) {
				uris.add(Uri.parse("content://at.tomtasche.reader/"
						+ page.getUrl()));
			}

			intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

			startActivity(intent);

			EasyTracker.getTracker().sendEvent("ui", "share", "", null);

			break;
		}
		case R.id.menu_print: {
			int index = getSupportActionBar().getSelectedNavigationIndex();
			if (index < 0)
				index = 0;
			Page page = getDocument().getPageAt(index);
			Uri uri = Uri.parse(page.getUrl());

			Intent printIntent = new Intent(this, PrintDialogActivity.class);
			printIntent.setDataAndType(uri, "text/html");
			printIntent.putExtra("title",
					"OpenDocument Reader - " + uri.getLastPathSegment());
			startActivity(printIntent);

			EasyTracker.getTracker().sendEvent("ui", "print", "", null);

			break;
		}
		}

		return super.onMenuItemSelected(featureId, item);
	}

	private void removeAds() {
		if (adView != null)
			adView.setVisibility(View.GONE);

		EasyTracker.getTracker().sendEvent("monetization", "ads", "hide", null);
	}

	private void leaveFullscreen() {
		getSupportActionBar().show();

		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		fullscreen = false;

		EasyTracker.getTracker().sendEvent("ui", "fullscreen", "leave", null);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (fullscreen && keyCode == KeyEvent.KEYCODE_BACK) {
			leaveFullscreen();

			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		Page page = getDocument().getPageAt(tab.getPosition());
		showPage(page);
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
	}

	private void showPage(Page page) {
		getPageFragment().loadPage(page);

		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
		// loadPageOnMultiscreens(page);
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

				startActivityForResult(intent, 42);

				dialog.dismiss();
			}
		});
		builder.show();
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (adView != null)
			adView.destroy();

		billingHelper.dispose();

		EasyTracker.getInstance().activityStop(this);
	}

	public String getPublicKey() {
		return "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDsdGybFkj9/26Fpu2mNASpAC8xQDRYocvVkxbpN6mF8k4a9L5ocnyUAY7sfKb0wjEc5e+vxL21kFKvvW0zEZX8a5wSXUfD5oiaXaiMPrp7cC1YbPPAelZvFEAzriA6pyk7PPKuqtAN2tcTiJED+kpiVAyEVU42lDUqE70xlRE6dQIDAQAB";
	}

	@Override
	public void onSuccess(Document document, Uri uri) {
		ActionBar bar = getSupportActionBar();
		bar.removeAllTabs();

		int pages = document.getPages().size();
		if (pages > 1) {
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			for (int i = 0; i < pages; i++) {
				ActionBar.Tab tab = bar.newTab();
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

			showPage(document.getPageAt(0));
		}
	}

	@Override
	public void onError(Throwable error, Uri uri) {
		// DO NOT call the super-method here! otherwise we end up in an infinite
		// recursion.

		BugSenseHandler.sendExceptionMessage("uri", uri.toString(),
				new Exception(error));

		EasyTracker.getTracker().sendEvent("app", "error",
				error.getClass().getSimpleName(), null);
	}
}
