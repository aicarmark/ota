package com.jdmmis.otaclient.download;

import java.io.RandomAccessFile;
import java.io.File;

import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.OTAClientConstant;

public class Downloader implements OTAClientConstant {

	private IDownloadObject mProxy;
	private DownloadStatus mStatus;
	
	private DownloadThread[] mThreads;
	private int mThreadNumber;

	private int mLastPercentage = -1;

	private boolean mStopped = false;
	private boolean mCompleted = false;
	private boolean mRunning = false;

	// Download persentage listener
	public interface PersentageListener {
		public void onPersentage(DownloadStatus status);
	}
	
	public Downloader (IDownloadObject proxy, DownloadStatus status, int threadNumber) {
		mProxy = proxy;
		mStatus = status;
		mThreadNumber = threadNumber;
		mThreads = new DownloadThread[mThreadNumber];
	}

	public void download(PersentageListener listener) {
		try {
			mRunning = true;

			int fileSize = mProxy.getObjectSize(mStatus, mStatus.getDownloadObject());
			if (!mStatus.isSuccess()) {
				mRunning = false;
				return;
			}
			
			mStatus.updateObjectSize(fileSize);
			int block = (int) ((fileSize % mThreadNumber)==0 ? 
				fileSize / mThreadNumber : fileSize / mThreadNumber + 1);
			mStatus.setBlockSize(block);

			// Save file
			File saveFile = mStatus.getSaveFile();
			Utils.Log.d("Downloader start downloading status:" + mStatus
				+ " to save path:" + saveFile.getPath());
			RandomAccessFile randOut = new RandomAccessFile(saveFile, "rw");
			if(fileSize>0) randOut.setLength(fileSize);
			randOut.close();

			// New the threads and start them for download
			for (int i=0; i<mThreadNumber; i++) {
				int downloaded = mStatus.getThreadDownloaded(i+1);
				// The running threads
				if (downloaded < block && mStatus.getTotalDownloaded()<fileSize) {
					mThreads[i] = new DownloadThread(mProxy, mStatus, i+1);
					mThreads[i].setPriority(7);
					mThreads[i].start();
				} else {
					mThreads[i] = null;
				}
			}

			// Block current thread util the download completed or stopped.
			while (mStatus.isSuccess() && !mStopped && !mCompleted) {
				Thread.sleep(900);

				// Judge complete
				boolean completed = true;
				for (int i=0; i<mThreadNumber; i++) {
					// Thread error occurred, restart a new thread to download
					if (mThreads[i] != null && mThreads[i].getThreadDownloaded() == -1) {
						mThreads[i] = new DownloadThread(mProxy, mStatus, i+1);
						mThreads[i].setPriority(7);
						mThreads[i].start();
					}
					if (mThreads[i] != null && !mThreads[i].isCompleted()) {
						completed = false;
						break;
					}
				}
				mCompleted = completed;

				// Judge stopped
				boolean stopped = true;
				for (int i=0; i<mThreadNumber; i++) {
					if (mThreads[i]!= null && !mThreads[i].isStopped()) {
						stopped = false;
						break;
					}
				}
				mStopped = stopped;

				//Utils.Log.d("Downloader run over, stopped:" + mStopped + ", completed:" + mCompleted);
				// Notify downloaded size...
				int percent = mStatus.getDownloadPercentage();
				if (listener != null && mLastPercentage != percent) {
					listener.onPersentage(mStatus);
					mLastPercentage = percent;
				}
			}

			// Downloader stopped or completed
			if (mStopped || mCompleted) {
				for (int i=0; i<mThreadNumber; i++) {
					mThreads[i] = null;
				}
			}
		} catch (Exception e) {
			Utils.Log.e("Downloader download met an error:" + e);
			e.printStackTrace();
			mStatus.setDownloadError(ErrorNO.ERR_ERROR);
		}

		mRunning = false;
	}

	public void stop() {
		Utils.Log.d("Downloader stop all threads...");
		for (int i=0; i<mThreadNumber; i++) {
			if (mThreads[i] != null) {
				mThreads[i].stopRunning();
			}
		}
	}

	public boolean stopped() {
		return mStopped;
	}

	public boolean completed() {
		return mCompleted;
	}

	public boolean running() {
		return mRunning;
	}
}
