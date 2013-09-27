package at.tomtasche.reader.ui;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.app.MediaRouteDialogFactory;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.view.Menu;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.Document.Page;
import at.tomtasche.reader.ui.activity.MainActivity;

import com.devspark.appmsg.AppMsg;
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

import fi.iki.elonen.SimpleWebServer;

public class ChromecastManager implements MediaRouteAdapter {

	private boolean enabled;

	private CastContext mCastContext;
	private CastDevice mSelectedDevice;
	private ContentMetadata mMetaData;
	private ApplicationSession mSession;
	private MediaProtocolMessageStream mMessageStream;
	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MediaRouter.Callback mMediaRouterCallback;

	private SimpleWebServer simpleWebServer;

	private MainActivity activity;

	public ChromecastManager(MainActivity activity) {
		this.activity = activity;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void onCreateOptionsMenu(Menu menu) {
		if (!enabled)
			return;

		mCastContext = new CastContext(activity.getApplicationContext());
		mMetaData = new ContentMetadata();

		MediaRouteHelper.registerMinimalMediaRouteProvider(mCastContext, this);
		mMediaRouter = MediaRouter
				.getInstance(activity.getApplicationContext());
		mMediaRouteSelector = MediaRouteHelper
				.buildMediaRouteSelector(MediaRouteHelper.CATEGORY_CAST);

		MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
				.getActionProvider(menu.findItem(R.id.media_route_menu_item));
		mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
		mediaRouteActionProvider
				.setDialogFactory(new MediaRouteDialogFactory());
		mMediaRouterCallback = new MyMediaRouterCallback();

		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
				MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
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
								AndroidFileCache.getCacheDirectory(activity),
								true);
						simpleWebServer.start();
					}

					mMessageStream = new MediaProtocolMessageStream();
					channel.attachMessageStream(mMessageStream);

					load(activity.getCurrentPage());
				} catch (IOException e) {
					e.printStackTrace();

					activity.showCrouton(R.string.chromecast_failed, null,
							AppMsg.STYLE_ALERT);
				}
			}

			@Override
			public void onSessionStartFailed(SessionError error) {
				activity.showCrouton(R.string.chromecast_failed, null,
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
	public void load(Page page) {
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

			activity.showCrouton(R.string.chromecast_failed, null,
					AppMsg.STYLE_ALERT);
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

	public void onStop() {
		if (mMediaRouter != null) {
			mMediaRouter.removeCallback(mMediaRouterCallback);
		}
	}

	public void onDestroy() {
		if (simpleWebServer != null) {
			simpleWebServer.stop();
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
	}
}
