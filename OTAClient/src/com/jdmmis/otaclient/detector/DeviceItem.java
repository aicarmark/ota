package com.jdmmis.otaclient.detector;

import com.jdmmis.otaclient.detector.DetectResult;
import com.jdmmis.otaclient.utils.Utils;

// A device item that stored in device table in DynamoDB server.
public class DeviceItem {
	// Fields from server table
	private String mIMEI;
	private boolean mUpgradeControl;
	private ReleaseVersion mLatestVersion;
	private ReleaseVersion mCurrentVersion;

	// Device real version
	private ReleaseVersion mDeviceVersion;

	// Detect interval
	private long mDetectInterval;

	public DeviceItem(String imei, String uc, String lv, String cv) {
		mIMEI = imei;
		mUpgradeControl = uc.toLowerCase().equals(ITables.UC_YES);
		mLatestVersion  = new ReleaseVersion(lv);
		mCurrentVersion = new ReleaseVersion(cv);

		///TODO: test only, same as current version which could be controlled in server.
		mDeviceVersion = new ReleaseVersion(cv);
		// Device real version
		//mDeviceVersion = new ReleaseVersion(Utils.OTAString.getDeviceVersion());

		mDetectInterval = 24*60;
	}

	// Returns whether the upgrade is allowed (which is controlled from server) 
	// for this device.
	public boolean isUpgradeAllowed() {
		return mUpgradeControl;
	}

	// Returns whether upgrade is needed according to device version.
	public boolean isUpgradeNeeded() {
		return mLatestVersion.higherThan(mDeviceVersion);
	}

	// Returns whether CurrentVersion in server table needs update for statistics.
	public boolean isCurrentUpdateNeeded() {
		return !mDeviceVersion.sameAs(mCurrentVersion.getVersion());
	}

	public boolean isFirmwareUpgradeNeeded() {
		return mLatestVersion.higherMainThan(mDeviceVersion);
	}

	public ReleaseVersion getTableCurrent() {
		return mCurrentVersion;
	}

	public ReleaseVersion getTableLatest() {
		return mLatestVersion;
	}

	public ReleaseVersion getDeviceVersion() {
		return mDeviceVersion;
	}

	public long getDetectInterval() {
		return mDetectInterval;
	}
	
	public String toString() {
		return "DeviceItem: { IMEI:" + mIMEI + ", UC:" + mUpgradeControl +
			", LV:" + mLatestVersion + ", CV:" + mCurrentVersion + 
			", DeviceVersion:" + mDeviceVersion + 
			", DetectInterval:" + mDetectInterval + "}";
	}
}
