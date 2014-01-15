package com.jdmmis.otaclient;

import java.io.File;
import java.io.IOException;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.RecoverySystem;
import android.content.Context;
import android.content.SharedPreferences;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.download.OTAClientDownload;
import com.jdmmis.otaclient.download.DownloadStatus;
import com.jdmmis.otaclient.detector.OTAClientDetector;
import com.jdmmis.otaclient.detector.DetectResult;
import com.jdmmis.otaclient.detector.ReleaseItem;

public class OTAClientSetting extends Activity 
	implements OTAClientDownload.IDownloadListener, 
		OTAClientDetector.IDetectorListener,
		OTAClientConstant,
		SharedPreferences.OnSharedPreferenceChangeListener {
	
	private TextView mTextStatus;
	private TextView mTextLastChecked;
	private ViewGroup mProgressLayout;
	private ProgressBar mProgressBar;
	private TextView mProgressPercent;
	private TextView mProgressNumber;
	
	private ProgressBar mProgressSwitching;
	private boolean mIsSwitching = false;
	
	private Button mCheckNow;
	private Button mDownloadButton;

	private OTAClientDetector mDetector;
	private OTAClientDownload mDownload;
	private Handler mHandler;

	// Each state has corresponding ui.
	// Please reminder that the service, OTAClientService will be always running
	// in the background. It will handle many OTA events/tasks which may cause 
	// state changed.
	private enum State {
		STATE_IDLE,
		STATE_DETECTING,
		STATE_NEW_UPGRADE,
		STATE_DOWNLOAD_STARTING,
		STATE_DOWNLOADING,
		STATE_DOWNLOAD_STOP,
		STATE_DOWNLOAD_COMPLETE,
		STATE_INSTALLING,
		STATE_MAX,
	}
	private State mState = State.STATE_IDLE;
	private State mLastState = mState;
	private DetectResult mResult = null;
	
	
	@Override    
	protected void onCreate(Bundle savedInstanceState) {     
		super.onCreate(savedInstanceState);
		setContentView(R.layout.check_update);

		mHandler = new Handler();
		mDetector = OTAClientDetector.getInstance((Context)this.getApplication());
		mDetector.attach(mHandler, this);
		mDownload = OTAClientDownload.getInstance((Context)this.getApplication());
		mDownload.attach(mHandler, this);

		mTextStatus = (TextView) findViewById(R.id.status);
		mTextLastChecked = (TextView) findViewById(R.id.last_check);
		mProgressLayout = (ViewGroup) findViewById(R.id.progress_group);
		mProgressBar = (ProgressBar) findViewById(R.id.progress);
		mProgressPercent = (TextView) findViewById(R.id.progress_percent);
		mProgressNumber = (TextView) findViewById(R.id.progress_number);
		mProgressSwitching = (ProgressBar) findViewById(R.id.progress_switching);

		// Shared preference
		SharedPreferences sp = getSharedPreferences(OTACLIENT_SP, Context.MODE_PRIVATE);  
		sp.registerOnSharedPreferenceChangeListener(this);

		// Check now button
		mCheckNow = (Button) findViewById(R.id.check_update_button);
		mCheckNow.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (mIsSwitching) {
					Toast.makeText(OTAClientSetting.this, 
						"Switching", Toast.LENGTH_SHORT).show(); 
					return;
				}
				if (Utils.Network.isNetworkNone()) {
					Toast.makeText(OTAClientSetting.this, 
						getString(R.string.network_not_available), Toast.LENGTH_SHORT).show();
					return;
				} 
				if (mState == State.STATE_IDLE) {
					setSwitching(mDetector.detect());
				}
			}
		});

		// Download button
		mDownloadButton = (Button) findViewById(R.id.download_button);
		mDownloadButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (mIsSwitching) {
					Toast.makeText(OTAClientSetting.this, 
						"Switching", Toast.LENGTH_SHORT).show(); 
					return;
				}
				// Install does not need network
				if (mState == State.STATE_DOWNLOAD_COMPLETE) {
					install();
					return;
				}
				
				if (Utils.Network.isNetworkNone()) {
					Toast.makeText(OTAClientSetting.this, 
						getString(R.string.network_not_available), Toast.LENGTH_SHORT).show();
					return;
				} else if (Utils.Network.isNetworkMobile()) {
					Toast.makeText(OTAClientSetting.this, 
						getString(R.string.network_wifi_not_available), Toast.LENGTH_SHORT).show();
					return;
				}
				if (mState == State.STATE_NEW_UPGRADE) {
					
				} else if (mState == State.STATE_DOWNLOADING) {
					mDownload.stopDownload();
					setSwitching(true);
				} else if (mState == State.STATE_DOWNLOAD_STOP) {
					mDownload.resumeDownload();
					setSwitching(true);
				} 
			}
		});

		mState = initState();
	}

	private void install() {
		Toast.makeText(OTAClientSetting.this, 
						"Installing...", Toast.LENGTH_SHORT).show(); 
		final DownloadStatus ongoing = mDownload.getOngoingDownload();
		final File otaFile = ongoing.getSaveFile();
		new Utils.RunInBackgroundThread() {
			public void run() {
				//try {
					Toast.makeText(OTAClientSetting.this, 
						"Checking MD5...", Toast.LENGTH_SHORT).show(); 
					boolean isOK = ongoing.checkMD5();
					if (isOK) {
						Toast.makeText(OTAClientSetting.this, 
							"MD5 checked OK, install update soon", Toast.LENGTH_LONG).show(); 
						//RecoverySystem.installPackage(OTAClientSetting.this, otaFile);
						Utils.Log.d("Still running after install OTA update?!");

					} else {
						Toast.makeText(OTAClientSetting.this, 
							"MD5 checked fail, not install", Toast.LENGTH_LONG).show(); 
					}
					
                //} catch (IOException e) {
				//	Utils.Log.e("Can't perform master clear/factory reset:" + e);
                //}
			}
		};
	}

	private State initState() {
		State state = State.STATE_IDLE;
		if (mDownload.isDownloading()) {
			state = State.STATE_DOWNLOADING;
		} else if (mDownload.getOngoingDownloadVersion() != null) {
			int percent = mDownload.getOngoingDownloadPercent();
			if (percent == 0) {
				// Judge download started before...
				state = (mDownload.getOngoingDownloadSize()==0 ? 
					State.STATE_NEW_UPGRADE : State.STATE_DOWNLOAD_STOP);
			} else if (percent == 100){
				state = State.STATE_DOWNLOAD_COMPLETE;
			} else {
				state = State.STATE_DOWNLOAD_STOP;
			}
		} else if (mDetector.isDetecting()) {
			state = State.STATE_DETECTING;
		} 
		return state;
	}

	private void update() {
		// update state
		State state = mState;
		mState = State.STATE_MAX;
		switchTo(state);

		// update switching
		setSwitching(mIsSwitching);
	}

	private void setSwitching(boolean switching) {
		mIsSwitching = switching;
		if (mIsSwitching) {
			mProgressSwitching.setVisibility(View.VISIBLE);
			mCheckNow.setVisibility(View.GONE);
			mDownloadButton.setVisibility(View.GONE);
		} else {
			mProgressSwitching.setVisibility(View.GONE);
		}
	}
	
	private void switchTo(State state) {
		if (mState == state) return;
		
		switch (state) {
			case STATE_IDLE: {
				mTextStatus.setText(R.string.system_update_to_date);
				mProgressLayout.setVisibility(View.GONE);
				mTextLastChecked.setVisibility(View.VISIBLE);
				mCheckNow.setVisibility(View.VISIBLE);
				mDownloadButton.setVisibility(View.GONE);
				SharedPreferences sp = getSharedPreferences(OTACLIENT_SP, Context.MODE_PRIVATE);
				String last = sp.getString(SP_LASTDETECT, Utils.getDateString());
				mTextLastChecked.setText(getString(R.string.last_checked) + ": " + last);
			} break;

			case STATE_DETECTING: {
				mTextStatus.setText(R.string.system_update_detecting);
				mProgressLayout.setVisibility(View.GONE);
				mTextLastChecked.setVisibility(View.VISIBLE);
				//mCheckNow.setVisibility(View.VISIBLE);
				//mDownloadButton.setVisibility(View.GONE);
			} break;

			case STATE_NEW_UPGRADE: {
				// Ensure result is not null
				mTextStatus.setText(R.string.system_update_detected);
				// The new upgrade is got from download data base.
				if (mResult == null) {
					mTextLastChecked.setText(mDownload.getOngoingDownloadVersion());
				// The new upgrade is got from detect complete callback.
				} else {
					mTextLastChecked.setText(mResult.getUpgradeVersion().getVersion());
				}
				mCheckNow.setVisibility(View.GONE);
				mDownloadButton.setVisibility(View.VISIBLE);
				mDownloadButton.setText(R.string.download);
			} break;

			case STATE_DOWNLOAD_STARTING: {
				mTextStatus.setText(R.string.system_update_download_start);
				mTextLastChecked.setVisibility(View.GONE);
			} break;

			case STATE_DOWNLOADING: {
				mDownloadButton.setVisibility(View.VISIBLE);
				mDownloadButton.setText(R.string.download_pause);
				mTextStatus.setText(R.string.system_update_downloading);

				mProgressLayout.setVisibility(View.VISIBLE);
				mTextLastChecked.setVisibility(View.GONE);
				mCheckNow.setVisibility(View.GONE);
				
				int percent = mDownload.getOngoingDownloadPercent();
				int size = mDownload.getOngoingDownloadSize();
				mProgressBar.setProgress(percent);
				mProgressPercent.setText(percent + "%");
				mProgressNumber.setText(Utils.getSizeString(size));
			} break;

			case STATE_DOWNLOAD_STOP: {
				mDownloadButton.setVisibility(View.VISIBLE);
				mDownloadButton.setText(R.string.download_continue);
				mTextStatus.setText(R.string.system_update_download_stop);
				
				mProgressLayout.setVisibility(View.VISIBLE);
				mTextLastChecked.setVisibility(View.GONE);
				mCheckNow.setVisibility(View.GONE);
				int percent = mDownload.getOngoingDownloadPercent();
				int size = mDownload.getOngoingDownloadSize();
				mProgressBar.setProgress(percent);
				mProgressPercent.setText(percent + "%");
				mProgressNumber.setText(Utils.getSizeString(size));
			} break;

			case STATE_DOWNLOAD_COMPLETE: {
				mDownloadButton.setVisibility(View.VISIBLE);
				mDownloadButton.setText(R.string.download_install);
				mTextStatus.setText(R.string.system_update_download_install);

				mProgressLayout.setVisibility(View.VISIBLE);
				mTextLastChecked.setVisibility(View.GONE);
				mCheckNow.setVisibility(View.GONE);
				
				int percent = mDownload.getOngoingDownloadPercent();
				int size = mDownload.getOngoingDownloadSize();
				mProgressBar.setProgress(percent);
				mProgressPercent.setText(percent + "%");
				mProgressNumber.setText(Utils.getSizeString(size));
			} break;

			case STATE_INSTALLING: {
				mTextStatus.setText(R.string.system_update_download_install);
				mProgressLayout.setVisibility(View.GONE);
				mTextLastChecked.setVisibility(View.GONE);
				mCheckNow.setVisibility(View.GONE);
			} break;

			default:
				break;
		}

		mLastState = mState;
		mState = state;
	}

	@Override    
	protected void onResume() {   
		super.onResume();   
		update();
	}    

	@Override    
	protected void onPause() {     
		super.onPause();    
	}    

	@Override    
	protected void onDestroy() {    
		super.onDestroy();  
		mDetector.detach(mHandler, this);
		mDownload.detach(mHandler, this);

		// Shared preference
		SharedPreferences sp = getSharedPreferences(OTACLIENT_SP, Context.MODE_PRIVATE);  
		sp.unregisterOnSharedPreferenceChangeListener(this);
	}

	// DetectorListener callbacks: all these functions are invoked in main thread
	public void onDetectStart() {
		Utils.Log.d("OTAClientSetting onDetectStart");
		throw new NullPointerException("OTAClientSetting exception intentionally");
		//setSwitching(true);
		//switchTo(State.STATE_DETECTING);
	}
	
	public void onDetectComplete(final DetectResult result) {
		Utils.Log.d("OTAClientSetting onDetectComplete");
		setSwitching(false);
		if (result.upgradable()) {
			String ongoing  = mDownload.getOngoingDownloadVersion();
			ReleaseItem upgrade = result.getUpgradeVersion();
			boolean newVersion = false;
			if (ongoing == null) {
				newVersion = true;
			} else if (upgrade.sameAs(ongoing)) {
				newVersion = false;
			} else if (result.firmwareUpgradable()) {
				newVersion = true;
			} else if (result.patchUpgradable()) {
				newVersion = false;
			}

			// If there is new version, stay new upgrade UI.
			if (newVersion) {
				mResult = result;
				switchTo(State.STATE_NEW_UPGRADE);
				
			// Otherwise, switch to previous UI.
			} else {
				switchTo(initState());
			}
		} else {
			switchTo(initState());
		}
	}
	
	/*public void onDetectCancel() {
		Utils.Log.d("OTAClientSetting onDetectCancel");
		setSwitching(false);
		switchTo(State.STATE_IDLE);
	}*/

	public void onDetectError(ErrorNO err) {
		Utils.Log.e("OTAClientSetting onDetectError err:" + err);
		setSwitching(false);
		switchTo(State.STATE_IDLE);
	}
	
	// DownloadListener callbacks
	public void onDownloadStart(String objectName) {
		Utils.Log.d("OTAClientSetting onDownloadStart, object:" + objectName);
		setSwitching(true);
		switchTo(State.STATE_DOWNLOAD_STARTING);
	}
	
	public void onDownloadPercentage(String objectName, int persentage) {
		Utils.Log.d("OTAClientSetting onDownloadPercentage objectName:" 
			+ objectName + ", persentage:" + persentage + "%");
		setSwitching(false);
		switchTo(State.STATE_DOWNLOADING);
		int percent = mDownload.getOngoingDownloadPercent();
		int size = mDownload.getOngoingDownloadSize();
		mProgressBar.setProgress(percent);
		mProgressPercent.setText(percent + "%");
		mProgressNumber.setText(Utils.getSizeString(size));
	}
	
	public void onDownloadComplete(String objectName, String savePath) {
		Utils.Log.d("OTAClientSetting onDownloadComplete, object:" + objectName 
			+ ", savePath:" + savePath);
		switchTo(State.STATE_DOWNLOAD_COMPLETE);
	}
	
	public void onDownloadCancel(String objectName) {
		Utils.Log.d("OTAClientSetting onDownloadCancel, object:" + objectName);
		setSwitching(false);
		switchTo(State.STATE_IDLE);
	}
	
	public void onDownloadStop(String objectName) {
		Utils.Log.d("OTAClientSetting onDownloadStop, object:" + objectName);
		setSwitching(false);
		switchTo(State.STATE_DOWNLOAD_STOP);
	}
	
	public void onDownloadError(String objectName, ErrorNO err) {
		Utils.Log.e("OTAClientSetting onDownloadError, object:" + objectName + 
			", error:" + err);
		setSwitching(false);
		switch (err) {
			case ERR_OBJECT_NOT_EXISTS: {
				Toast.makeText(OTAClientSetting.this, 
						R.string.err_version_not_exist, Toast.LENGTH_LONG).show();
				switchTo(State.STATE_IDLE);
			} break;
			
			case ERR_MD5_FAIL: {
				Toast.makeText(OTAClientSetting.this, 
						R.string.err_md5_check_fail, Toast.LENGTH_LONG).show();
				switchTo(State.STATE_NEW_UPGRADE);
			} break;

			// Others will switch to DOWNLOAD_STOP state.
			case ERR_NETWORK_DISCONNECTED:
				Toast.makeText(OTAClientSetting.this, 
						R.string.err_wifi_not_connected, Toast.LENGTH_LONG).show();
			default: {
				switchTo(State.STATE_DOWNLOAD_STOP);
			} break;
		}
	}

	private void updateLastChecked(SharedPreferences sp) {
		if (mState == State.STATE_IDLE) {
			String last = sp.getString(SP_LASTDETECT, Utils.getDateString());
			mTextLastChecked.setText(getString(R.string.last_checked) + ": " + last);
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		Utils.Log.d("OTAClientSetting on sp changed, key:" + key);
		if (key.equals(SP_LASTDETECT)) {
			updateLastChecked(sp);
		}
	}
}
