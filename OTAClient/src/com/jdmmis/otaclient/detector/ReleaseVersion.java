package com.jdmmis.otaclient.detector;

public class ReleaseVersion {
	private String mMain;
	private String mPatch;

	public ReleaseVersion(String version) {
		int index = version.indexOf(ITables.PATCH_FLAG);
		if (index == -1) {
			mMain = version;
			mPatch = ITables.NO_PATCH;
		} else {
			mMain = version.substring(0, index);
			mPatch = version.substring(index+1);
		}
	}

	public ReleaseVersion(String main, String patch) {
		mMain = main;
		mPatch = patch;
	}

	public String getVersion() {
		return mPatch.equals(ITables.NO_PATCH) ? mMain : (mMain + "-" + mPatch);
	}

	public String getMainString() {
		return mMain;
	}

	public String getPatchString() {
		return mPatch;
	}

	public boolean isMainRelease() {
		return mPatch.equals(ITables.NO_PATCH);
	}

	public boolean higherThan(ReleaseVersion that) {
		return (this.getVersion().compareToIgnoreCase(that.getVersion()) > 0);
	}

	public boolean higherThan(String that) {
		return (this.getVersion().compareToIgnoreCase(that) > 0);
	}

	public boolean higherMainThan(ReleaseVersion that) {
		return (mMain.compareToIgnoreCase(that.getMainString()) > 0);
	}

	public boolean sameMainAs(ReleaseVersion that) {
		return (mMain.compareToIgnoreCase(that.getMainString()) == 0);
	}

	public boolean sameAs(String version) {
		return (this.getVersion().compareToIgnoreCase(version) == 0);
	}

	public String toString() {
		return mPatch.equals(ITables.NO_PATCH) ?
			"ReleaseVersion{" + mMain + "}" :
			"ReleaseVersion{" + mMain + "-" + mPatch + "}";
	}
}
