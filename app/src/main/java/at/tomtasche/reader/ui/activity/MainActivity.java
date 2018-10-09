package at.tomtasche.reader.ui.activity;

import java.io.File;
import java.util.List;

import com.crashlytics.android.Crashlytics;
import com.github.jberkel.pay.me.IabHelper;
import com.github.jberkel.pay.me.IabResult;
import com.github.jberkel.pay.me.listener.OnIabPurchaseFinishedListener;
import com.github.jberkel.pay.me.listener.OnIabSetupFinishedListener;
import com.github.jberkel.pay.me.listener.QueryInventoryFinishedListener;
import com.github.jberkel.pay.me.model.Inventory;
import com.github.jberkel.pay.me.model.ItemType;
import com.github.jberkel.pay.me.model.Purchase;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.kobakei.ratethisapp.RateThisApp;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.BillingPreferences;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Page;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.KitKatPrinter;
import at.tomtasche.reader.background.LoadingListener;
import at.tomtasche.reader.ui.EditActionModeCallback;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.TtsActionModeCallback;
import at.tomtasche.reader.ui.widget.RecentDocumentDialogFragment;
import de.keyboardsurfer.android.widget.crouton.Style;

public class MainActivity extends DocumentActivity implements ActionBar.TabListener, LoadingListener {

	private int PURCHASE_CODE = 1337;

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";
	private static final String BILLING_PRODUCT_LOVE = "love_and_everything";

	private static final String EXTRA_TAB_POSITION = "tab_position";

	private LinearLayout adContainer;
	private AdView madView;
	private InterstitialAd interstitial;

	private int lastPosition;
	private boolean fullscreen;
	private boolean showAds;

	private Page currentPage;

	private IabHelper billingHelper;
	private BillingPreferences billingPreferences;

	private TtsActionModeCallback ttsActionMode;

	private SharedPreferences preferences;

	private Runnable saveCroutonRunnable;

	private FirebaseAnalytics analytics;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MobileAds.initialize(getApplicationContext(), "ca-app-pub-8161473686436957~9025061963");

		analytics = FirebaseAnalytics.getInstance(this);

		adContainer = (LinearLayout) findViewById(R.id.ad_container);

		if (savedInstanceState != null) {
			lastPosition = savedInstanceState.getInt(EXTRA_TAB_POSITION);
		}

		addLoadingListener(this);

        billingPreferences = new BillingPreferences(this);

        billingHelper = new IabHelper(this, getPublicKey());
        billingHelper.startSetup(new OnIabSetupFinishedListener() {

            @Override
            public void onIabSetupFinished(IabResult result) {
                if (billingPreferences.hasPurchased()) {
                    removeAds();

                    return;
                }

                if (result.isFailure()) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            showGoogleAds();

                            showCrouton(getString(R.string.crouton_error_billing), null, Style.ALERT);
                        }
                    });
                } else if (result.isSuccess()) {
                    // query every 7 days
                    if ((billingPreferences.getLastQueryTime() + 1000 * 60 * 60 * 24 * 7) < System
                            .currentTimeMillis()) {
                        billingHelper.queryInventoryAsync(new QueryInventoryFinishedListener() {

                            @Override
                            public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                                if (result.isSuccess()) {
                                    boolean purchased = inv.getPurchase(BILLING_PRODUCT_FOREVER) != null;
                                    purchased |= inv.getPurchase(BILLING_PRODUCT_YEAR) != null;
                                    purchased |= inv.getPurchase(BILLING_PRODUCT_LOVE) != null;

                                    if (purchased) {
                                        removeAds();
                                    } else {
                                        showGoogleAds();
                                    }

                                    billingPreferences.setPurchased(purchased);
                                }

                                billingPreferences.setLastQueryTime(System.currentTimeMillis());
                            }
                        });
                    }
                }
            }
        });

		RateThisApp.onCreate(this);

		// shows after 10 launches after 7 days
		RateThisApp.showRateDialogIfNeeded(this);
	}

	private void showAds(AdView adView) {
		this.madView = adView;

		adContainer.removeAllViews();

		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		adContainer.addView(adView, params);
	}

	private void showGoogleAds() {
		showAds = true;

		AdView adView = new AdView(MainActivity.this);
		adView.setAdSize(AdSize.SMART_BANNER);
		adView.setAdUnitId("ca-app-pub-8161473686436957/5931994762");
		adView.setAdListener(new MyAdListener());

		AdRequest adRequest = new AdRequest.Builder().build();

		adView.loadAd(adRequest);

		showAds(adView);
	}

	@Override
	protected void onResume() {
		super.onResume();

		showSaveCrouton();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (intent.getData() != null) {
			loadUri(intent.getData());

			Bundle bundle = new Bundle();
			bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "other");
			analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
		}
	}

	@Override
	public DocumentLoader loadUri(Uri uri, String password, boolean limit, boolean translatable) {
		return super.loadUri(uri, password, limit, translatable);
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (ttsActionMode != null) {
			ttsActionMode.stop();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(EXTRA_TAB_POSITION, getSupportActionBar().getSelectedNavigationIndex());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu_main, menu);

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == PURCHASE_CODE) {
			billingHelper.handleActivityResult(requestCode, resultCode, intent);
		} else {
			showInterstitial();
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	public void showInterstitial() {
		if (interstitial != null) {
			interstitial.show();
		}

		interstitial = null;
	}

	// TODO: that's super ugly - good job :)
	public void showSaveCroutonLater(final File modifiedFile, final Uri fileUri) {
		saveCroutonRunnable = new Runnable() {

			@Override
			public void run() {
				showCrouton("Document successfully saved. You can find it on your sdcard: " + modifiedFile.getName(),
						new Runnable() {

							@Override
							public void run() {
								share(fileUri);
							}
						}, Style.INFO);
			}
		};

		// also execute it immediately, for users who don't see ads
		saveCroutonRunnable.run();
	}

	private void showSaveCrouton() {
		if (saveCroutonRunnable != null) {
			saveCroutonRunnable.run();

			saveCroutonRunnable = null;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean showDocumentMissing = false;

		switch (item.getItemId()) {
		case R.id.menu_search: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
				// http://www.androidsnippets.org/snippets/20/
				final AlertDialog.Builder alert = new AlertDialog.Builder(this);
				alert.setTitle(getString(R.string.menu_search));

				final EditText input = new EditText(this);
				alert.setView(input);

				alert.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int whichButton) {
						getPageFragment().searchDocument(input.getText().toString());
					}
				});
				alert.setNegativeButton(getString(android.R.string.cancel), null);
				alert.show();
			} else {
				FindActionModeCallback findActionModeCallback = new FindActionModeCallback(this);
				findActionModeCallback.setWebView(getPageFragment().getPageView());
				startSupportActionMode(findActionModeCallback);
			}

			analytics.logEvent(FirebaseAnalytics.Event.SEARCH, null);

			break;
		}
		case R.id.menu_open: {
			findDocument();

			Bundle bundle = new Bundle();
			bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "choose");
			analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

			break;
		}
		case R.id.menu_remove_ads: {
			buyAdRemoval();

			break;
		}
		case R.id.menu_fullscreen: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			if (fullscreen) {
				leaveFullscreen();
			} else {
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

				getSupportActionBar().hide();

				removeAds();

				showCrouton(R.string.crouton_leave_fullscreen, new Runnable() {

					@Override
					public void run() {
						leaveFullscreen();
					}
				}, Style.INFO);

				analytics.logEvent("fullscreen_start", null);
			}

			fullscreen = !fullscreen;

			break;
		}
		case R.id.menu_share: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			share(Uri.parse("content://at.tomtasche.reader/document.odt"));

			break;
		}
		case R.id.menu_print: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			if (Build.VERSION.SDK_INT >= 19) {
				KitKatPrinter.print(this, getPageFragment().getPageView());
			} else {
				int index = getSupportActionBar().getSelectedNavigationIndex();
				if (index < 0)
					index = 0;

				Page page = getDocument().getPageAt(index);
				Uri uri = Uri.parse("content://at.tomtasche.reader/" + page.getUrl());

				Intent printIntent = new Intent(Intent.ACTION_SEND);
				printIntent.setType("text/html");
				printIntent.putExtra(Intent.EXTRA_TITLE, "OpenDocument Reader - " + uri.getLastPathSegment());

				printIntent.putExtra(Intent.EXTRA_STREAM, uri);

				try {
					startActivity(printIntent);
				} catch (ActivityNotFoundException e) {
					Intent installIntent = new Intent(Intent.ACTION_VIEW, Uri
							.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.cloudprint"));

					startActivity(installIntent);
				}
			}

			analytics.logEvent("print", null);

			break;
		}
		case R.id.menu_tts: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			ttsActionMode = new TtsActionModeCallback(this, getPageFragment().getPageView());
			startSupportActionMode(ttsActionMode);

			analytics.logEvent("tts", null);


			break;
		}
		case R.id.menu_edit: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			if (showAds) {
				loadInterstitial();
			}

			EditActionModeCallback editActionMode = new EditActionModeCallback(this, getPageFragment().getPageView(),
					getDocument().getOrigin());
			startSupportActionMode(editActionMode);

			analytics.logEvent("edit", null);

			break;
		}
		default: {
			return super.onOptionsItemSelected(item);
		}
		}

		if (showDocumentMissing) {
			Toast.makeText(this, "Please open a document first", Toast.LENGTH_LONG).show();
		}

		return true;
	}

	private void showRecent() {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

		DialogFragment chooserDialog = new RecentDocumentDialogFragment();
		chooserDialog.show(transaction, RecentDocumentDialogFragment.FRAGMENT_TAG);

		Bundle bundle = new Bundle();
		bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "recent");
		analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
	}

	public void share(Uri uri) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setDataAndType(uri, "application/*");
		intent.putExtra(Intent.EXTRA_STREAM, uri);

		try {
			startActivity(intent);

			analytics.logEvent(FirebaseAnalytics.Event.SHARE, null);
		} catch (Exception e) {
			e.printStackTrace();

			showCrouton(R.string.crouton_error_open_app, null, Style.ALERT);
		}
	}

	private void loadInterstitial() {
		interstitial = new InterstitialAd(this);
		interstitial.setAdUnitId("ca-app-pub-8161473686436957/2477707165");

		AdRequest adRequest = new AdRequest.Builder().build();

		interstitial.loadAd(adRequest);

		interstitial.setAdListener(new MyAdListener());
	}

	private void buyAdRemoval() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_remove_ads_title);
		builder.setItems(R.array.dialog_remove_ads_options, new OnClickListener() {

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

				case 2:
					product = BILLING_PRODUCT_LOVE;

					break;

				default:
					removeAds();

					dialog.dismiss();

					return;
				}

				billingHelper.launchPurchaseFlow(MainActivity.this, product, ItemType.INAPP, PURCHASE_CODE,
						new OnIabPurchaseFinishedListener() {
							public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
								// remove ads even if the
								// purchase failed /
								// the user canceled the
								// purchase
								runOnUiThread(new Runnable() {

									@Override
									public void run() {
										removeAds();
									}
								});

								if (result.isSuccess()) {
									billingPreferences.setPurchased(true);
									billingPreferences.setLastQueryTime(System.currentTimeMillis());
								} else {
									analytics.logEvent("purchase_abort", null);
								}
							}
						}, null);

				analytics.logEvent("purchase_attempt", null);

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void removeAds() {
		showAds = false;

		if (madView != null) {
			madView.setVisibility(View.GONE);
		}

		analytics.logEvent("remove_ads", null);
	}

	private void leaveFullscreen() {
		getSupportActionBar().show();

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		fullscreen = false;

		analytics.logEvent("fullscreen_end", null);
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
		currentPage = page;

		getPageFragment().loadPage(page);

		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
		// loadPageOnMultiscreens(page);
	}

	public void findDocument() {
		if (showAds) {
			loadInterstitial();
		}

		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		// remove mime-type because most apps don't support ODF mime-types
		intent.setType("application/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		PackageManager pm = getPackageManager();
		final List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
		int size = targets.size() + 1;
		String[] targetNames = new String[size];
		for (int i = 0; i < targets.size(); i++) {
			targetNames[i] = targets.get(i).loadLabel(pm).toString();
		}

		targetNames[size - 1] = getString(R.string.menu_recent);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_choose_filemanager);
		builder.setItems(targetNames, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (which == size - 1) {
					showRecent();

					return;
				}

				ResolveInfo target = targets.get(which);
				if (target == null) {
					return;
				}

				intent.setComponent(new ComponentName(target.activityInfo.packageName, target.activityInfo.name));

				try {
					startActivityForResult(intent, 42);
				} catch (Exception e) {
					e.printStackTrace();

					showCrouton(R.string.crouton_error_open_app, new Runnable() {

						@Override
						public void run() {
							findDocument();
						}
					}, Style.ALERT);
				}

				Bundle bundle = new Bundle();
				bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, target.activityInfo.packageName);
				analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

				dialog.dismiss();
			}
		});
		builder.show();
	}

	@Override
	protected void onStop() {
		if (billingHelper != null) {
			billingHelper.dispose();
		}

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		try {
			// keeps throwing exceptions for some users:
			// Caused by: java.lang.NullPointerException
			// android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
			// android.webkit.WebView.requestFocus(WebView.java:2133)
			// android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)
			if (madView != null) {
				((AdView) madView).destroy();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			// keeps throwing exceptions for some users:
			// Caused by: java.lang.NullPointerException
			// android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
			// android.webkit.WebView.requestFocus(WebView.java:2133)
			// android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)

			super.onDestroy();
		} catch (Exception e) {
			e.printStackTrace();
		}
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

			if (pages == 1) {
				showPage(document.getPageAt(0));
			}
		}
	}

	@Override
	public void onError(Throwable error, Uri uri) {
		// DO NOT call the super-method here! otherwise we end up in an infinite
		// recursion.

		Crashlytics.log(Log.ERROR, "MainActivity", "could not load document at: " + uri.toString());
		Crashlytics.logException(error);
	}

	public Page getCurrentPage() {
		return currentPage;
	}

	private class MyAdListener extends AdListener {

		@Override
		public void onAdFailedToLoad(int arg0) {
			if (showAds) {
				showCrouton(R.string.crouton_remove_ads, new Runnable() {

					@Override
					public void run() {
						buyAdRemoval();
					}
				}, Style.CONFIRM);
			}
		}

		@Override
		public void onAdClicked() {
			removeAds();
		}

		@Override
		public void onAdLoaded() {
			if (interstitial != null) {
				analytics.logEvent("ads_interstitial_shown", null);
			}
		}
	}
}
