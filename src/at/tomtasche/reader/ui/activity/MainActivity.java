package at.tomtasche.reader.ui.activity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.BillingPreferences;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Page;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.LoadingListener;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.TtsActionModeCallback;
import at.tomtasche.reader.ui.widget.DocumentChooserDialogFragment;

import com.amazon.device.ads.AdError;
import com.amazon.device.ads.AdLayout;
import com.amazon.device.ads.AdProperties;
import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.AdTargetingOptions;
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
import com.google.ads.Ad;
import com.google.ads.AdListener;
import com.google.ads.AdRequest;
import com.google.ads.AdRequest.ErrorCode;
import com.google.ads.AdSize;
import com.google.ads.AdView;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.Tracker;
import com.google.cast.ApplicationChannel;
import com.google.cast.ApplicationMetadata;
import com.google.cast.ApplicationSession;
import com.google.cast.CastContext;
import com.google.cast.CastDevice;
import com.google.cast.ContentMetadata;
import com.google.cast.MediaProtocolCommand;
import com.google.cast.MediaProtocolMessageStream;
import com.google.cast.MediaRouteAdapter;
import com.google.cast.MediaRouteHelper;
import com.google.cast.MediaRouteStateChangeListener;
import com.google.cast.SessionError;
import com.kskkbys.rate.RateThisApp;

import fi.iki.elonen.SimpleWebServer;

public class MainActivity extends DocumentActivity implements
		ActionBar.TabListener, LoadingListener, AdListener,
		com.amazon.device.ads.AdListener, MediaRouteAdapter {

	private int PURCHASE_CODE = 1337;

	private static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
	private static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";

	private static final String EXTRA_TAB_POSITION = "tab_position";

	private View madView;

	private int lastPosition;
	private boolean fullscreen;

	private Page currentPage;
	// private List<DocumentPresentation> presentations;

	private IabHelper billingHelper;
	private BillingPreferences billingPreferences;

	private TtsActionModeCallback ttsActionMode;

	private Tracker analytics;

	private long loadingStartTime;

	private CastContext mCastContext = null;
	private CastDevice mSelectedDevice;
	private ContentMetadata mMetaData;
	private ApplicationSession mSession;
	private MediaProtocolMessageStream mMessageStream;
	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MediaRouter.Callback mMediaRouterCallback;

	private SimpleWebServer simpleWebServer;

	private LinearLayout adContainer;

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

		EasyTracker.getInstance().activityStart(this);
		analytics = EasyTracker.getTracker();

		adContainer = (LinearLayout) findViewById(R.id.ad_container);

		if (savedInstanceState != null) {
			lastPosition = savedInstanceState.getInt(EXTRA_TAB_POSITION);
		} else if (getIntent().getData() == null) {
			analytics.sendEvent("ui", "open", "google", null);
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
							showAmazonAds();

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
												showAmazonAds();
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

	private void showAds(View adView) {
		this.madView = adView;

		adContainer.removeAllViews();

		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);
		adContainer.addView(adView, params);

		analytics.sendEvent("monetization", "ads", "show", null);
	}

	private void showAmazonAds() {
		AdRegistration.setAppKey("eb900b26936d42e780bba6041ed7e400");

		AdLayout adView = new AdLayout(this);
		adView.setListener(this);

		showAds(adView);

		adView.loadAd(new AdTargetingOptions());
	}

	private void showGoogleAds() {
		AdView adView = new AdView(MainActivity.this, AdSize.SMART_BANNER,
				"a15042277f73506");
		adView.setAdListener(this);
		adView.loadAd(new AdRequest());

		showAds(adView);
	}

	private void adLoaded() {
		showCrouton(R.string.consume_ad, null, AppMsg.STYLE_CONFIRM);
	}

	// amazon
	@Override
	public void onAdCollapsed(AdLayout arg0) {
	}

	@Override
	public void onAdExpanded(AdLayout arg0) {
		removeAds();
	}

	@Override
	public void onAdFailedToLoad(AdLayout arg0, AdError arg1) {
		((AdLayout) madView).destroy();

		showGoogleAds();
	}

	@Override
	public void onAdLoaded(AdLayout arg0, AdProperties arg1) {
		adLoaded();

		// seems to be necessary - otherwise the view won't show up at all
		adContainer.invalidate();
		adContainer.requestLayout();

		analytics.sendEvent("monetization", "ads", "amazon", null);
	}

	// admob
	@Override
	public void onDismissScreen(Ad arg0) {
		// user returned from AdActivity
		removeAds();
	}

	@Override
	public void onFailedToReceiveAd(Ad arg0, ErrorCode arg1) {
	}

	@Override
	public void onLeaveApplication(Ad arg0) {
	}

	@Override
	public void onPresentScreen(Ad arg0) {
	}

	@Override
	public void onReceiveAd(Ad arg0) {
		adLoaded();

		analytics.sendEvent("monetization", "ads", "google", null);
	}

	@Override
	protected void onStart() {
		super.onStart();

		RateThisApp.onStart(this);

		// shows after 10 launches after 7 days
		RateThisApp.showRateDialogIfNeeded(this);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (intent.getData() != null) {
			loadUri(intent.getData());

			analytics.sendEvent("ui", "open", "other", null);
		}
	}

	@Override
	public DocumentLoader loadUri(Uri uri, String password, boolean limit) {
		loadingStartTime = System.currentTimeMillis();

		return super.loadUri(uri, password, limit);
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

		outState.putInt(EXTRA_TAB_POSITION, getSupportActionBar()
				.getSelectedNavigationIndex());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu_main, menu);

		// mCastContext = new CastContext(getApplicationContext());
		// mMetaData = new ContentMetadata();
		//
		// MediaRouteHelper.registerMinimalMediaRouteProvider(mCastContext,
		// this);
		// mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		// mMediaRouteSelector = MediaRouteHelper
		// .buildMediaRouteSelector(MediaRouteHelper.CATEGORY_CAST);
		//
		// MediaRouteActionProvider mediaRouteActionProvider =
		// (MediaRouteActionProvider) MenuItemCompat
		// .getActionProvider(menu.findItem(R.id.media_route_menu_item));
		// mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
		// mediaRouteActionProvider
		// .setDialogFactory(new MediaRouteDialogFactory());
		// mMediaRouterCallback = new MyMediaRouterCallback();
		//
		// mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
		// MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_recent: {
			FragmentTransaction transaction = getSupportFragmentManager()
					.beginTransaction();

			DialogFragment chooserDialog = new DocumentChooserDialogFragment();
			chooserDialog.show(transaction,
					DocumentChooserDialogFragment.FRAGMENT_TAG);

			analytics.sendEvent("ui", "open", "recent", null);

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
				startSupportActionMode(findActionModeCallback);
			}

			analytics.sendEvent("ui", "search", "start", null);

			break;
		}

		case R.id.menu_open: {
			findDocument();

			analytics.sendEvent("ui", "open", "choose", null);

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

										analytics.sendEvent("monetization",
												"in-app", purchase.getSku(),
												null);
									} else {
										analytics.sendEvent("monetization",
												"in-app", "abort", null);
									}
								}
							}, null);

					analytics.sendEvent("monetization", "in-app", "attempt",
							null);

					dialog.dismiss();
				}
			});
			builder.show();

			break;
		}

		case R.id.menu_about: {
			loadUri(DocumentLoader.URI_ABOUT);

			analytics.sendEvent("ui", "open", "about", null);

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

			analytics.sendEvent("ui", "feedback", null, null);

			break;
		}
		case R.id.menu_reload: {
			Loader<Document> loader = getSupportLoaderManager().getLoader(0);
			DocumentLoader documentLoader = (DocumentLoader) loader;

			loadUri(documentLoader.getLastUri(), documentLoader.getPassword(),
					false);

			analytics.sendEvent("ui", "reload", "no-limit", null);

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

				analytics.sendEvent("ui", "fullscreen", "enter", null);
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

			analytics.sendEvent("ui", "share", null, null);

			break;
		}
		case R.id.menu_print: {
			int index = getSupportActionBar().getSelectedNavigationIndex();
			if (index < 0)
				index = 0;
			Page page = getDocument().getPageAt(index);
			Uri uri = Uri.parse("content://at.tomtasche.reader/"
					+ page.getUrl());

			Intent printIntent = new Intent(Intent.ACTION_SEND);
			printIntent.setType("text/html");
			printIntent.putExtra(Intent.EXTRA_TITLE, "OpenDocument Reader - "
					+ uri.getLastPathSegment());

			printIntent.putExtra(Intent.EXTRA_STREAM, uri);

			try {
				startActivity(printIntent);
			} catch (ActivityNotFoundException e) {
				Intent installIntent = new Intent(
						Intent.ACTION_VIEW,
						Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.cloudprint"));

				startActivity(installIntent);
			}

			analytics.sendEvent("ui", "print", null, null);

			break;
		}
		case R.id.menu_tts: {
			ttsActionMode = new TtsActionModeCallback(this, getPageFragment()
					.getPageView());
			startSupportActionMode(ttsActionMode);

			analytics.sendEvent("ui", "tts", null, null);

			break;
		}
		case R.id.menu_googleplus: {
			startActivity(new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://plus.google.com/communities/113494011673882132018")));

			analytics.sendEvent("ui", "google+", null, null);
		}
		default: {
			return super.onOptionsItemSelected(item);
		}
		}

		return true;
	}

	private void removeAds() {
		if (madView != null)
			madView.setVisibility(View.GONE);

		analytics.sendEvent("monetization", "ads", "hide", null);
	}

	private void leaveFullscreen() {
		getSupportActionBar().show();

		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		fullscreen = false;

		analytics.sendEvent("ui", "fullscreen", "leave", null);
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

		loadMedia(page);
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

				analytics.sendEvent("ui", "open",
						target.activityInfo.packageName, null);

				dialog.dismiss();
			}
		});
		builder.show();
	}

	@Override
	protected void onStop() {
		if (mMediaRouter != null) {
			mMediaRouter.removeCallback(mMediaRouterCallback);
		}

		billingHelper.dispose();

		EasyTracker.getInstance().activityStop(this);

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (simpleWebServer != null) {
			simpleWebServer.stop();
		}

		if (madView != null) {
			if (madView instanceof AdView) {
				((AdView) madView).destroy();
			} else if (madView instanceof AdLayout) {
				((AdLayout) madView).destroy();
			}
		}

		if (mSession != null) {
			try {
				if (!mSession.hasStopped()) {
					mSession.endSession();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		mSession = null;

		super.onDestroy();
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
			} else {
				BugSenseHandler.sendExceptionMessage("uri", uri.toString(),
						new IllegalStateException("empty document"));
			}
		}

		if (loadingStartTime > 0) {
			analytics.sendTiming("app", System.currentTimeMillis()
					- loadingStartTime, "load", "document");

			loadingStartTime = 0;
		}
	}

	@Override
	public void onError(Throwable error, Uri uri) {
		// DO NOT call the super-method here! otherwise we end up in an infinite
		// recursion.

		BugSenseHandler.sendExceptionMessage("uri", uri.toString(),
				new Exception(error));

		analytics.sendException(error.getMessage(), error, false);
	}

	@Override
	public void onDeviceAvailable(CastDevice device, String arg1,
			MediaRouteStateChangeListener arg2) {
		mSelectedDevice = device;
		openSession();
	}

	@Override
	public void onSetVolume(double arg0) {
		// doesn't make sense for presentations
	}

	@Override
	public void onUpdateVolume(double arg0) {
		// doesn't make sense for presentations
	}

	/**
	 * Starts a new video playback session with the current CastContext and
	 * selected device.
	 */
	private void openSession() {
		mSession = new ApplicationSession(mCastContext, mSelectedDevice);

		int flags = 0;

		// Comment out the below line if you are not writing your own
		// Notification Screen.
		flags |= ApplicationSession.FLAG_DISABLE_NOTIFICATION;

		// Comment out the below line if you are not writing your own Lock
		// Screen.
		flags |= ApplicationSession.FLAG_DISABLE_LOCK_SCREEN_REMOTE_CONTROL;
		mSession.setApplicationOptions(flags);

		mSession.setListener(new com.google.cast.ApplicationSession.Listener() {

			@Override
			public void onSessionStarted(ApplicationMetadata appMetadata) {
				ApplicationChannel channel = mSession.getChannel();
				if (channel == null) {
					return;
				}

				try {
					if (simpleWebServer == null) {
						simpleWebServer = new SimpleWebServer(null, 1993,
								AndroidFileCache
										.getCacheDirectory(MainActivity.this),
								true);
						simpleWebServer.start();
					}

					mMessageStream = new MediaProtocolMessageStream();
					channel.attachMessageStream(mMessageStream);

					loadMedia(currentPage);
				} catch (IOException e) {
					e.printStackTrace();

					showCrouton(R.string.chromecast_failed, null,
							AppMsg.STYLE_ALERT);
				}
			}

			@Override
			public void onSessionStartFailed(SessionError error) {
				showCrouton(R.string.chromecast_failed, null,
						AppMsg.STYLE_ALERT);
			}

			@Override
			public void onSessionEnded(SessionError error) {
			}
		});

		try {
			mSession.startSession("c529f89e-2377-48fb-b949-b753d9094119");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// taken from: http://stackoverflow.com/a/1720431/198996
	public String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();

					String address = inetAddress.getHostAddress().toString();
					// filter loopback and ipv6
					if (!inetAddress.isLoopbackAddress()
							&& !address.contains(":")) {
						return address;
					}
				}
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		}

		return null;
	}

	/**
	 * Loads the stored media object and casts it to the currently selected
	 * device.
	 */
	protected void loadMedia(Page page) {
		if (mMessageStream == null) {
			return;
		}

		mMetaData.setTitle("odr");
		try {
			String ip = getLocalIpAddress();
			String fileName = page.getUri().getLastPathSegment();

			MediaProtocolCommand cmd = mMessageStream.loadMedia("http://" + ip
					+ ":1993/" + fileName, mMetaData, true);
			cmd.setListener(new MediaProtocolCommand.Listener() {

				@Override
				public void onCompleted(MediaProtocolCommand mPCommand) {
				}

				@Override
				public void onCancelled(MediaProtocolCommand mPCommand) {
				}
			});
		} catch (Exception e) {
			e.printStackTrace();

			showCrouton(R.string.chromecast_failed, null, AppMsg.STYLE_ALERT);
		}
	}

	/**
	 * A callback class which listens for route select or unselect events and
	 * processes devices and sessions accordingly.
	 */
	private class MyMediaRouterCallback extends MediaRouter.Callback {

		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo route) {
			MediaRouteHelper.requestCastDeviceForRoute(route);
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo route) {
			try {
				if (mSession != null) {
					mSession.setStopApplicationWhenEnding(false);
					mSession.endSession();
				}
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mMessageStream = null;
			mSelectedDevice = null;
		}
	}
}
