package com.jdmmis.otaclient;

import android.app.Service;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

//import android.net.ConnectivityManager;
//import android.net.NetworkInfo;

import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.detector.OTAClientDetector;
import com.jdmmis.otaclient.detector.OTAClientDetector.IDetectorListener;
import com.jdmmis.otaclient.download.OTAClientDownload;
import com.jdmmis.otaclient.download.OTAClientDownload.IDownloadListener;
import com.jdmmis.otaclient.detector.DetectResult;
import com.jdmmis.otaclient.detector.ReleaseItem;
import com.jdmmis.otaclient.OTAClientConstant;

public class OTAClientService extends Service
	implements IDetectorListener, IDownloadListener,
		OTAClientConstant {

	private OTAClientDetector mDetector;
	private OTAClientDownload mDownload;

	private Handler mHandler;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// The handler which runs on looper of the main thread.
		mHandler = new Handler();
		mDetector = OTAClientDetector.getInstance(this);
		mDetector.attach(mHandler, this);
		mDownload = OTAClientDownload.getInstance(this);
		mDownload.attach(mHandler, this);

		Utils.Network.updateNetwork(this);

		// Check new upgrade installable
		if (mDownload.getOngoingDownloadPercent() == 100) {
			sendNotification(NTF_INSTALL_UPGRADE, mDownload.getOngoingDownloadVersion());
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final Intent i = intent;
		final int f = flags;
		final int s = startId;
		new Utils.RunInBackgroundThread() {
			public void run() {
				onStartCommandImpl(i, f, s);
			}
		};
		//onStartCommandImpl(intent, flags, startId);
		
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mDetector.detach(mHandler, this);
		mDownload.detach(mHandler, this);
	}

	private void handleConnectivityChange() {
		Utils.Network.updateNetwork(this);
		if (Utils.Network.isNetworkNone()) {
			mDownload.stopDownload();

		// Network available: Mobile or Wifi
		} else {
			// Stop or resume download according to network type
			if (Utils.Network.isNetworkMobile()) {
				mDownload.stopDownload();
			} else {
				mDownload.resumeDownload();
			}

			// Perform detect if alarm expired while no downloading progress
			if (mDownload.getOngoingDownloadSize() == 0 ||
				mDownload.getOngoingDownloadPercent() == 100) {
				// Detect immediately if detect alarm expired.
				mDetector.detectByAlarm();
			} 
		}
	}

	private void handleDetectAlarm() {
		Utils.Log.d("OTAClientService handle detect alarm");
		// Only perform detect if network allows
		if (Utils.Network.isNetworkNone()) {
			// Could not perform detect now...
			// DO NOT update detect alarm so as to let it do detect as soon as
			// network available again.

		// The detector will update alarm automatically if a detect has been really
		// performed. But, if no detect performed, the update alarm will not get 
		// updated and future detect triggered by alarm will not be continued...
		// Besides, the detect will be performed only while no downloading progress.
		} else if (mDownload.getOngoingDownloadSize() == 0 ||
					mDownload.getOngoingDownloadPercent() == 100) {
			mDetector.detect();
		}
	}
	
	private void onStartCommandImpl(Intent intent, int flags, int startId) {
		if (intent == null) return;
		String action = intent.getAction();
		Utils.Log.d("OTAClientService service start action:" + action);

		// Handle network status change
		if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {
			handleConnectivityChange();

		// Handle boot completed
		} else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {

		// Handle detect upgrade intent
		} else if (action.equals(ACTION_DETECT_ALARM)) {
			handleDetectAlarm();
		}
	}

	// DetectorListener callbacks: all these functions are invoked in main thread
	public void onDetectStart() {
		Utils.Log.d("OTAClientService onDetectStart");
	}

	private void updateLastDetected() {
		SharedPreferences sp = getSharedPreferences(OTACLIENT_SP, Context.MODE_PRIVATE);  
		String last = Utils.getDateString();
		SharedPreferences.Editor editor = sp.edit();
		editor.putString(SP_LASTDETECT, last);
		editor.commit();
	}

	private void handleUpgrade(DetectResult result) {
		// Check whether there is ongoing download...
		String ongoing  = mDownload.getOngoingDownloadVersion();
		ReleaseItem upgrade = result.getUpgradeVersion();
		Utils.Log.d("OTAClientService detect complete, ongoing:" + ongoing 
			+ ", detected upgrade:" + upgrade);
		
		boolean startNewDownload = false;
		// No ongoing download, start download the detected version
		if (ongoing == null) {
			startNewDownload = true;

		// Same version as ongoing download, keep downloading...
		} else if (upgrade.sameAs(ongoing)) { 
			startNewDownload = false;

		// Another new version which is a newer firmware, cancel ongoing and start new...
		} else if (result.firmwareUpgradable()) {
			startNewDownload = true;
			/**
			* Cancel the current download before starting the newer.
			* The following newer download will get started util the current 
			* download (if any) been fully cancelled as only one download running
			* in background thread at a time.
			*/
			mDownload.cancelDownload();

		// Another new version which is a newer patch, impossible???
		} else if (result.patchUpgradable()) {
			// nothing...
		}

		if (startNewDownload) {
			if (Utils.Network.isNetworkWifi()) {
				mDownload.startDownload(upgrade.getVersion(), upgrade.getMD5());
			} else {
				mDownload.recordDownload(upgrade.getVersion(), upgrade.getMD5());
			}
			// Notify new upgrade available
			sendNotification(NTF_NEW_UPGRADE, upgrade.getVersion());
		}
	}

	private void sendNotification(int which, String objectName) {
		String title = which == NTF_NEW_UPGRADE ? 
			getString(R.string.system_update_detected) :
			getString(R.string.system_update_download_install);
		
		NotificationManager ntfmgr = (NotificationManager) 
			getSystemService(NOTIFICATION_SERVICE);
		Notification notification = new Notification(
			R.drawable.update_icon, 
			title, System.currentTimeMillis()); 
		
		Intent intent = new Intent(this, OTAClientSetting.class);	 
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK); 
		PendingIntent contentIntent = PendingIntent.getActivity(
			this, which, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		notification.setLatestEventInfo(this, 
			title, objectName, contentIntent);	 
		ntfmgr.notify(which, notification);
	}
	
	public void onDetectComplete(final DetectResult result) {
		updateLastDetected();
		if (result.upgradable()) {
			handleUpgrade(result);
		} else {
			// no upgrade
		}
	}
	
	/*public void onDetectCancel() {
		Utils.Log.d("OTAClientService onDetectCancel");
	}*/

	public void onDetectError(ErrorNO err) {
		Utils.Log.e("OTAClientService onDetectError err:" + err);
	}
	
	// DownloadListener callbacks
	public void onDownloadStart(String objectName) {
		Utils.Log.d("OTAClientService onDownloadStart, object:" + objectName);
	}
	
	public void onDownloadPercentage(String objectName, int persentage) {
		Utils.Log.d("OTAClientService onDownloadPercentage objectName:" 
			+ objectName + ", persentage:" + persentage + "%");
	}
	
	public void onDownloadComplete(String objectName, String savePath) {
		Utils.Log.d("OTAClientService onDownloadComplete, object:" + objectName 
			+ ", savePath:" + savePath);
		sendNotification(NTF_INSTALL_UPGRADE, objectName);
		// Start alarm trigger again
		mDetector.updateDetectAlarm();
	}
	
	public void onDownloadCancel(String objectName) {
		Utils.Log.d("OTAClientService onDownloadCancel, object:" + objectName);
	}
	
	public void onDownloadStop(String objectName) {
		Utils.Log.d("OTAClientService onDownloadStop, object:" + objectName);
	}
	
	public void onDownloadError(String objectName, ErrorNO err) {
		Utils.Log.e("OTAClientService onDownloadError, object:" + objectName + 
			", error:" + err);
	}
}
