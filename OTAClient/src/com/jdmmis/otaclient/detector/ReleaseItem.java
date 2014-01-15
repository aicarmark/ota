package com.jdmmis.otaclient.detector;

public class ReleaseItem {
	// The version
	private ReleaseVersion mVersion;
	// The dependency version of this version.
	// It will be a main version (firmware upgrade) if no dependency version, otherwise, 
	// it will be a patch version (patch upgrade).
	private ReleaseVersion mDependency;
	// The MD5 information for checksum.
	private String mMD5;

	public ReleaseItem(String main, String patch, String dependency, String md5) {
		mVersion = new ReleaseVersion(main, patch);
		mDependency = new ReleaseVersion(dependency);
		mMD5 = md5;
	}

	public String getVersion() {
		return mVersion.getVersion();
	}

	public String getDependency() {
		return mDependency.getVersion();
	}

	public String getMD5() {
		return mMD5;
	}

	public boolean isFirmwareRelease() {
		return (mDependency.getVersion().equals(ITables.NO_DEPENDENCY));
	}

	public boolean sameAs(String version) {
		return (mVersion.getVersion().compareToIgnoreCase(version) == 0);
	}

	public boolean sameAs(ReleaseVersion version) {
		return sameAs(version.getVersion());
	}

	public String toString() {
		return "ReleaseItem{ version:" + mVersion + ", dependency:" + mDependency 
			+ ", md5:" + mMD5 + " }";
	}
}
