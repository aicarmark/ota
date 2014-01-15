package com.jdmmis.otaclient.detector;

import com.jdmmis.otaclient.OTAClientConstant;

public class DetectResult implements OTAClientConstant {
	public final static String TYPE_NO_UPGRADE = "no_upgrade";
	public final static String TYPE_UPGRADE_FIRMWARE = "firmware_upgrade";
	public final static String TYPE_UPGRADE_PATCH = "patch_upgrade";
	//public final static String TYPE_UPDATE_TABLE = "update_table";

	private ErrorNO mError;
	private String  mUpgradeType;
	private ReleaseItem mUpgradeVersion;

	public DetectResult() {
		mError = ErrorNO.ERR_NONE;
		mUpgradeType = TYPE_NO_UPGRADE;
		mUpgradeVersion = null;
	}

	public void setDetectError(ErrorNO err) {
		mError = err;
	}

	public ErrorNO getDetectError() {
		return mError;
	}

	public boolean isDetectSuccessful() {
		return (mError==ErrorNO.ERR_NONE);
	}

	public void setUpgradeVersion(ReleaseItem version) {
		mUpgradeVersion = version;
	}

	public ReleaseItem getUpgradeVersion() {
		return mUpgradeVersion;
	}

	public void setUpgradeType(String type) {
		mUpgradeType = type;
	}

	public String getUpgradeType() {
		return mUpgradeType;
	}

	public boolean patchUpgradable() {
		return mUpgradeType.equals(TYPE_UPGRADE_PATCH);
	}

	public boolean firmwareUpgradable() {
		return mUpgradeType.equals(TYPE_UPGRADE_FIRMWARE);
	}

	public boolean upgradable() {
		return (mError==ErrorNO.ERR_NONE && 
			(mUpgradeType.equals(TYPE_UPGRADE_PATCH) 
			|| mUpgradeType.equals(TYPE_UPGRADE_FIRMWARE)));
	}

	public String toString() {
		return "DetectResult:{ Err:" + mError + ", type:" + mUpgradeType + 
			", version:" + mUpgradeVersion + " }";
	}
}
