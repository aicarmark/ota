package com.jdmmis.otaclient.detector;

import java.util.ArrayList;
import java.util.Map;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;

import com.jdmmis.otaclient.detector.ITables;
import com.jdmmis.otaclient.detector.TablesProxy;
import com.jdmmis.otaclient.detector.DeviceItem;
import com.jdmmis.otaclient.detector.DetectResult;
import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.OTAClientConstant;

/**
 * OTA Client Detector is a singleton instance object to run the detect work to see
 * whether new upgrade available in server. It provides the running environment ( a
 * background thread ) for real detect task.
 */
public class OTAClientDetector implements OTAClientConstant {
	// Running thread for detect
	private Thread  mBackgroundThread;
	private Handler mBackgroundHandler;

	private ITables mProxy;
	private boolean mIsDetecting;
	private ArrayList<IDetectorListener> mListeners;
	private ArrayList<Handler> mHandlers;

	private DetectAlarm mAlarm;

	private static OTAClientDetector sInstance;

	// Detector listener callbacks
	public interface IDetectorListener {
		public void onDetectStart();
		public void onDetectComplete(final DetectResult result);
		//public void onDetectCancel();
		public void onDetectError(final ErrorNO err);
	}
	
	private OTAClientDetector(Context context) {
		initialize();

		// Initialize imei string...
		TelephonyManager telephonyManager= (TelephonyManager) 
			context.getSystemService(Context.TELEPHONY_SERVICE);
		String imei=telephonyManager.getDeviceId();
		Utils.IMEI.SetIMEI(imei);

		// Initialize detect alarm
		mAlarm = new DetectAlarm(context);
		mAlarm.updateNextAlarm(false);
	}
	
	public static synchronized OTAClientDetector getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new OTAClientDetector(context);
		}
		return sInstance;
	}

	private void initBackground() {
		mBackgroundThread = new Thread() {
			public void run() {
				Looper.prepare();
				mBackgroundHandler = new Handler();
				synchronized(this) {
					notifyAll();
				}
				Looper.loop();
			}
		};

		mBackgroundThread.start();

		synchronized(mBackgroundThread) {
			if (mBackgroundHandler == null) {
				Utils.Log.d("OTAClient Detector waiting for background handler init");
				try {
					mBackgroundThread.wait();
				} catch (InterruptedException e) {
					Utils.Log.d("OTAClient Detector error", (Exception)e);
				}
			}
			if (mBackgroundHandler == null) {
				Utils.Log.e("OTAClient Detector could not create bg handler");
			} else {
				Utils.Log.d("OTAClient Detector bg handler created");
			}
		}
	}
	
	private void initialize() {
		mProxy = new TablesProxy();
		mIsDetecting = false;
		mListeners = new ArrayList<IDetectorListener>();
		mHandlers  = new ArrayList<Handler>();

		initBackground();
	}

	public void attach(Handler handler, IDetectorListener listener) {
		if (handler == null) throw new NullPointerException("listener add handler is null");
		if (listener == null) throw new NullPointerException("listener add callback is null");
		if (!mHandlers.contains(handler)) {
			mHandlers.add(handler);
			mListeners.add(listener);
		}
	}

	public void detach(Handler handler, IDetectorListener listener) {
		///TODO: remove all posted runnables???
		mHandlers.remove(handler);
		mListeners.remove(listener);
	}

	public boolean isDetecting() {
		return mIsDetecting;
	}

	private void getCorrectUpgrade(DeviceItem device, DetectResult result) {
		/**
		 * Now upgrade is needed, it should get correct upgrade version and reutrn
		 * it back with result. If firmware upgrade needed, just get and return latest
		 * firmware upgrade version, otherwise, it should be patch upgrade, query all
		 * higher patches in same main version and find the correct one.
		 */
		ReleaseVersion latest = device.getTableLatest();
		// Firmware upgrade.
		if (device.isFirmwareUpgradeNeeded()) {
			// Fetch the latest main release.
			ReleaseItem release = mProxy.getRelease(result, 
				latest.getMainString(), ITables.NO_PATCH);
			if (release != null) {
				result.setUpgradeVersion(release);
				result.setUpgradeType(DetectResult.TYPE_UPGRADE_FIRMWARE);
			}

		// Patch upgrade.
		} else {
			ReleaseVersion current = device.getDeviceVersion();
			Map<String, ReleaseItem> patches = mProxy.getPatches(result, 
				latest.getMainString(), current.getPatchString(), latest.getPatchString());
			if (patches != null && patches.size() > 0) {
				ReleaseItem correct = patches.get(latest.getVersion());
				//Utils.Log.d("get patch, current:" + current + ", correct:" + correct);
				while (correct != null &&
					// The correct patch version should be the one whose dependent
					// version is same as the current version.
					!current.sameAs(correct.getDependency())) {
					correct = patches.get(correct.getDependency());
				}
				Utils.Log.d("get patch correct version:" + correct);
				if (correct != null) {
					result.setUpgradeVersion(correct);
					result.setUpgradeType(DetectResult.TYPE_UPGRADE_PATCH);
				}
			}
		}
	}

	private void performDetect() {
		DetectResult result = new DetectResult();
		
		DeviceItem device = mProxy.getDevice(result, Utils.IMEI.getIMEI());
		Utils.Log.d("OTAClientDetector got one device item:" + device);
		if (device != null) {
			if (device.isUpgradeAllowed()) {
				if (device.isUpgradeNeeded()) {
					getCorrectUpgrade(device, result);
				}
			} else {
				result.setUpgradeType(DetectResult.TYPE_NO_UPGRADE);
			}
		}

		// notify detect status
		if (!result.isDetectSuccessful()) {
			notifyDetectError(result.getDetectError());
		} else {
			notifyDetectComplete(result);
		}

		// Update current field in device table
		if (device != null && device.isCurrentUpdateNeeded()) {
			//Utils.Log.d("OTAClientDetector update device:" + device);
			mProxy.updateDevice(result, Utils.IMEI.getIMEI(), device);
		}

		// Update detect interval
		if (device != null) {
			mAlarm.updateInterval(device.getDetectInterval());
		}
		// Update next alarm which will be started from current.
		mAlarm.updateNextAlarm(true);

		// Close.
		mProxy.close();
	}

	public boolean detect() {
		if (mBackgroundHandler != null) {
			if (mIsDetecting) {
				// Actually, a detect is ongoing...
				return true;
			}
			
			notifyDetectStart();
			
			mBackgroundHandler.post(new Runnable() {
				public void run() {
					performDetect();
				}
			});
			
			return true;
		}
		return false;
	}

	public boolean detectByAlarm() {
		// Detect immediately if alarm has been expired
		if (mAlarm.isAlarmExpired()) {
			return detect();
			
		// Wait for the next alarm to trigger detect
		} else {
			return false;
		}
	}

	public void updateDetectAlarm() {
		mAlarm.updateNextAlarm(false);
	}

	private void notifyDetectStart() {
		mIsDetecting = true;
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDetectorListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDetectStart();
					}
				});
			}
		}
	}

	private void notifyDetectComplete(final DetectResult result) {
		mIsDetecting = false;
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDetectorListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDetectComplete(result);
					}
				});
			}
		}
	}

	/*private void notifyDetectCancel() {
		mIsDetecting = false;
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDetectorListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDetectCancel();
					}
				});
			}
		}
	}*/

	private void notifyDetectError(final ErrorNO err) {
		mIsDetecting = false;
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDetectorListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDetectError(err);
					}
				});
			}
		}
	}
}

