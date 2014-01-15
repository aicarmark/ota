package com.jdmmis.otaclient.download;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.OTAClientConstant;

public class DownloadStatus implements OTAClientConstant {

	private ErrorNO mError = ErrorNO.ERR_NONE;
	
	// Key of an object, that means which object would be downloaded from server.
	private String mObjectName = null;

	// The object size.
	private int mObjectSize = 0;

	// MD5 information.
	private String mMD5 = null;

	// The block size which determines the start pos and end pos for every thread.
	private int mBlock = 0;

	// The file to save the downloaded object.
	private File mSaveFile;

	// The totally downloaded size.
	private int mDownloaded = 0;

	// Save path could be determined by object name.
	// The destinated file path.
	//protected String mSavePath;

	/**
	 * Hold all threads' download status <Integer, Integer>
	 * Integer: the id of the download thread. It also determines the start pos for that thread.
	 * Integer: the downloaded size for that thread.
	 */
	private Map<Integer, Integer> mThreadStatus;

	private DownloadProvider mProvider;

	public DownloadStatus() {
		mThreadStatus = new ConcurrentHashMap<Integer, Integer>();
	}

	public DownloadStatus(String object, String md5, int number) {
		mThreadStatus = new ConcurrentHashMap<Integer, Integer>();
		for (int i=0; i<number; i++) {
			mThreadStatus.put(i+1, 0);
		}
		mObjectName = object;
		mMD5 = md5;
	}

	public void setDownloadProvider(DownloadProvider provider) {
		mProvider = provider;
	}

	public synchronized void updateStatus(int id, int downloaded) {
		this.mThreadStatus.put(id, downloaded);
		mProvider.updateThreads(this);
	}

	public synchronized void appendDownloaded(int downloaded) {
		mDownloaded += downloaded;
	}

	public void setDownloadError(ErrorNO err) {
		mError = err;
	}

	public ErrorNO getDownloadError() {
		return mError;
	}

	public boolean isSuccess() {
		return (mError == ErrorNO.ERR_NONE);
	}

	public String getDownloadObject() {
		return mObjectName;
	}

	public void setDownloadObject(String object) {
		mObjectName = object;
	}

	public void setObjectSize(int size) {
		mObjectSize = size;
	}

	public int getObjectSize() {
		return mObjectSize;
	}

	public void updateObjectSize(int size) {
		if (mObjectSize != size) {
			mObjectSize = size;
			mProvider.updateObject(this);
		}
	}

	public void setBlockSize(int block) {
		mBlock = block;
	}

	public int getBlockSize() {
		return mBlock;
	}

	public void setMD5(String md5) {
		mMD5 = md5;
	}

	public String getMD5() {
		return mMD5;
	}

	public boolean checkMD5() {
		return Utils.MD5.checkMD5(mMD5, mSaveFile);
	}

	public File getSaveFile() {
		if (mSaveFile == null && mObjectName != null) {
			mSaveFile = new File(Utils.getSavePath(mObjectName));
		}
		return mSaveFile;
	}

	public int getTotalDownloaded() {
		return mDownloaded;
	}

	public void setTotalDownloaded(int downloaded) {
		mDownloaded = downloaded;
	}

	public int getThreadDownloaded(int threadId) {
		return mThreadStatus.get(threadId);
	}

	public void putThreadDownloaded(int threadId, int threadDownloaded) {
		mThreadStatus.put(threadId, threadDownloaded);
	}

	public Map<Integer, Integer> getThreadStatusMap() {
		return mThreadStatus;
	}

	public int getDownloadPercentage() {
		// max int: 2147483647
		long downloaded = (long) mDownloaded * 100;
		return mObjectSize==0 ? 0 : (int)(downloaded / mObjectSize);
	}

	public void resetDownload() {
		mError = ErrorNO.ERR_NONE;
		mDownloaded = 0;
		for (Map.Entry<Integer, Integer> entry : mThreadStatus.entrySet()) {
			entry.setValue(0);
		}
		mProvider.deleteThreads(mObjectName);
		mProvider.insertThreads(this);
	}

	public String toString() {
		return "DownloadStatus: object[" + mObjectName 
			+ "], error[" + mError + "], downloaded[" + mDownloaded
			+ "], total size[:" + mObjectSize
			+ "], persentage:[" + getDownloadPercentage() + "%]";
	}
}
