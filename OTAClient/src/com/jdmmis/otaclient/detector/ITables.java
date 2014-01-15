package com.jdmmis.otaclient.detector;

import java.util.Map;

/**
 * The table interface which could be regarded as the abstract layer between
 * this OTA client and servers. To communicate with specific server (such as AWS
 * server), it juse needs a specific implement of this interface (such as AwsTableProxy).
 * So all data in this interface is supposed to stay unchanged.
 */
public interface ITables {
	// Upgrade control value, "yes" means the upgrade is allowed.
	public final static String UC_YES = "yes";
	// Upgrade control value, "no" means the upgrade is prohibited.
	public final static String UC_NO = "no";
	// The device table that records all devices with version information.
	public interface DeviceTable {
		// Uniquely identify a device.
		public final static String IMEI = "IMEI";
		// Current version stored in table. 
		// Reminder that this may be not the device real version.
		// The device will update it while if detects real version is newer than it.
		// This field is also used for statistics.
		public final static String CURRENT_VERSION = "CurrentVersion";
		// The latest version.
		public final static String LATEST_VERSION  = "LatestVersion";
		// To control the upgrade for a device is allowed or not.
		public final static String UPGRADE_CONTROL = "UpgradeControl";
	}

	// No patch value means the release item is a main release.
	public final static String NO_PATCH = "-";
	// Patch flag.
	public final static String PATCH_FLAG = "-patch";
	// No dependency value means the release has no dependent version.
	public final static String NO_DEPENDENCY = "-";
	// Release table records all release versions.
	public interface ReleaseTable {
		// Hash Key: the main version of a release.
		public final static String MAIN_VERSION="MainVersion";
		// Range Primary Key: the patch version.
		public final static String PATCH_VERSION="PatchVersion";
		// The dependent version.
		public final static String DEPENDENT_VERSION="DependentVersion";
		// MD5 information.
		public final static String MD5 = "MD5";
	}

	/**
	 * Get a device from the device table by the given imei number.
	 *
	 * @param result
	 *			The detect result returned with error code if any exception happens.
	 *
	 * @param imei
	 *			The imei string that uniquely identifies a device.
	 *
	 * @return the device item with version information, upgrade control, etc.
	 */
	public DeviceItem getDevice(DetectResult result, String imei);

	/**
	 * Update a device information to device table by the given imei, this api is used
	 * to update device version to current version field for statitics.
	 *
	 * @param result
	 * 			The detect result returned with error code if any exception happens.
	 *
	 * @param imei
	 *			The imei string that uniquely identifies a device.
	 *
	 * @param device
	 *			The new device.
	 */
	public void updateDevice(DetectResult result, String imei, DeviceItem device);

	/**
	 * Get the patch versions so as to find a correct patch version while patch upgrade
	 * is needed, which means same main version between current device version and upgrade
	 * version.
	 *
	 * @param result
	 *			The detect result returned with error code if any exception happens.
	 *
	 * @param main
	 *			The main string which is the hash key of release table.
	 *
	 * @param patchFrom
	 *			From which patch string (which is the range primary key of release 
	 * 			table) to start query.
	 *
	 * @param patchTo
	 *			To which patch string (which is the range primary key of release 
	 *			table) to end query.
	 * 
	 * @return Map<String, ReleaseItem> the hash map with all patch release items.
	 *			String is the whole version string. ReleaseItem stands for an item.
	 */
	public Map<String, ReleaseItem> getPatches(DetectResult result, String main, String patchFrom, String patchTo);

	/**
	 * Get a release version from the release table by the given key (main, patch),
	 * the main string is the hash key of the release table, while the patch string
	 * is the range primary key of the release table, which could uniquely get one
	 * release information.
	 *
	 * @param result
	 *			The detect result returned with error code if any exception happens.
	 *
	 * @param main
	 *			The main string.
	 *
	 * @param patch
	 *			The patch string.
	 *
	 * @return Return the release item while get one item. Return null if no found.
	 */
	public ReleaseItem getRelease(DetectResult result, String main, String patch);
	
	// Close the connection between client and server.
	public void close();
}
