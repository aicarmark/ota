package com.jdmmis.otaclient;

public interface OTAClientConstant {
	public enum ErrorNO {
		ERR_NONE,
		ERR_ERROR,
		
		ERR_NETWORK_DISCONNECTED,
		ERR_OBJECT_NOT_EXISTS,
		ERR_MD5_FAIL,

		ERR_PARAM_INCORRECT,
		ERR_RESOURCE_NOT_FOUND,
		ERR_DATA_TOO_LARGE,
		ERR_SERVER_OVERLOAD,
		ERR_SERVER_INTERNAL,
		ERR_SERVER_COMMON,
	}

	// Notification that there is a new upgrade detected for download.
	public final static int NTF_NEW_UPGRADE = 1;
	// Notification that there is a new downloaded upgrade for install.
	public final static int NTF_INSTALL_UPGRADE = 2;
	

	// Shared preference
	public final static String OTACLIENT_SP = "otasettings";
	// Last detect time <String>
	public final static String SP_LASTDETECT = "last_detect";
	// Next alarm date in milliseconds <long>
	public final static String SP_DETECTALARM = "detect_alarm";
	// Detect interval in minute <long>
	public final static String SP_DETECTINTERVAL = "detect_interval";
	// Hour interval
	public final static long INTERVAL_HOUR = (60);
	// Daily interval
	public final static long INTERVAL_DAY = (INTERVAL_HOUR * 24);
	// Weekly interval
	public final static long INTERVAL_WEEK = (INTERVAL_DAY * 7);
	// The default detect interval will be 24 hours
	public final static long INTERVAL_DEFAULT = 2; // 5 minutes for test

	// Intent Action to trigger detect
	public final static String ACTION_DETECT_ALARM = "com.jdmmis.otaclient.ACTION_DETECT_ALARM";
}
