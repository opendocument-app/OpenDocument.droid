package at.tomtasche.reader.ui.activity;

import java.io.File;
import java.util.List;

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
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.EditActionModeCallback;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.TtsActionModeCallback;
import at.tomtasche.reader.ui.widget.RecentDocumentDialogFragment;
import de.keyboardsurfer.android.widget.crouton.Style;

public class MainActivity extends DocumentActivity implements ActionBar.TabListener, LoadingListener {

	private static final boolean USE_PROPRIETARY_LIBRARIES = true;

	private int PURCHASE_CODE = 1337;

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";
	private static final String BILLING_PRODUCT_LOVE = "love_and_everything";

	private static final String EXTRA_TAB_POSITION = "tab_position";

	private int lastPosition;
	private boolean fullscreen;

	private IabHelper billingHelper;
	private BillingPreferences billingPreferences;

	private TtsActionModeCallback ttsActionMode;

	private SharedPreferences preferences;

	private Runnable saveCroutonRunnable;

	private CrashManager crashManager;
	private AnalyticsManager analyticsManager;
	private AdManager adManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		crashManager = new CrashManager();
		crashManager.setEnabled(USE_PROPRIETARY_LIBRARIES);
		crashManager.initialize();

		analyticsManager = new AnalyticsManager();
		analyticsManager.setEnabled(USE_PROPRIETARY_LIBRARIES);
		analyticsManager.initialize(this);

		adManager = new AdManager();
		adManager.setEnabled(USE_PROPRIETARY_LIBRARIES);
		adManager.initialize(getApplicationContext(), analyticsManager, new Runnable() {
			@Override
			public void run() {
				showCrouton(R.string.crouton_remove_ads, new Runnable() {

					@Override
					public void run() {
						buyAdRemoval();
					}
				}, Style.CONFIRM);
			}
		});

		adManager.setAdContainer(findViewById(R.id.ad_container));

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
                    adManager.removeAds();

                    return;
                }

                if (result.isFailure()) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            adManager.showGoogleAds();

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
                                        adManager.removeAds();
                                    } else {
                                        adManager.showGoogleAds();
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

			analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "other");
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
			adManager.showInterstitial();
		}

		super.onActivityResult(requestCode, resultCode, intent);
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

			analyticsManager.report(FirebaseAnalytics.Event.SEARCH);

			break;
		}
		case R.id.menu_open: {
			findDocument();

			analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "choose");

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

				adManager.removeAds();

				showCrouton(R.string.crouton_leave_fullscreen, new Runnable() {

					@Override
					public void run() {
						leaveFullscreen();
					}
				}, Style.INFO);

				analyticsManager.report("fullscreen_start");
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

			analyticsManager.report("print");

			break;
		}
		case R.id.menu_tts: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			ttsActionMode = new TtsActionModeCallback(this, getPageFragment().getPageView());
			startSupportActionMode(ttsActionMode);

			analyticsManager.report("tts");

			break;
		}
		case R.id.menu_edit: {
			if (getDocument() == null) {
				showDocumentMissing = true;

				break;
			}

			adManager.loadInterstitial();

			EditActionModeCallback editActionMode = new EditActionModeCallback(this, adManager, getPageFragment().getPageView(),
					getDocument().getOrigin());
			startSupportActionMode(editActionMode);

			analyticsManager.report("edit");

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

		analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "recent");
	}

	public void share(Uri uri) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setDataAndType(uri, "application/*");
		intent.putExtra(Intent.EXTRA_STREAM, uri);

		try {
			startActivity(intent);

			analyticsManager.report(FirebaseAnalytics.Event.SHARE);
		} catch (Exception e) {
			e.printStackTrace();

			showCrouton(R.string.crouton_error_open_app, null, Style.ALERT);
		}
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
										adManager.removeAds();
									}
								});

								if (result.isSuccess()) {
									billingPreferences.setPurchased(true);
									billingPreferences.setLastQueryTime(System.currentTimeMillis());
								} else {
									analyticsManager.report("purchase_abort");
								}
							}
						}, null);

				analyticsManager.report("purchase_attempt");

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void leaveFullscreen() {
		getSupportActionBar().show();

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		fullscreen = false;

		analyticsManager.report("fullscreen_end");
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
	}

	public void findDocument() {
		adManager.loadInterstitial();

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

				analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, target.activityInfo.packageName);

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
		adManager.destroyAds();

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

		crashManager.log(error, uri);
	}
}
