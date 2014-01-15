package com.jdmmis.otaclient.download;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import javax.net.ssl.SSLException;

import com.jdmmis.otaclient.download.IDownloadObject;
import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.OTAClientConstant;

public class DownloadThread extends Thread 
	implements OTAClientConstant {

	private static int BUFFER_SIZE = 1024*2;

	private IDownloadObject mProxy;
	private DownloadStatus mStatus;
	private int mThreadId = -1;
	private int mThreadDownloaded;
	
	private boolean mComplete = false;
	private boolean mStop = false;
	private boolean mStopped = false;
	
	public DownloadThread(IDownloadObject proxy, DownloadStatus status, int threadId) {
		mProxy = proxy;
		mStatus = status;
		mThreadDownloaded = mStatus.getThreadDownloaded(threadId);
		mThreadId = threadId;
	}

	@Override
	public void run() {
		int block = mStatus.getBlockSize();
		Utils.Log.d("DownloadThread[" + mThreadId + "] start downloading, downloaded:" +
			mThreadDownloaded + ", block:" + block);
		
		// Download not complete
		if (mThreadDownloaded < block) {
			try {
				int startPos = block * (mThreadId - 1) + mThreadDownloaded; // start pos
				int endPos = block * mThreadId -1; // end pos
				InputStream is = mProxy.getObjectStream(mStatus, 
					mStatus.getDownloadObject(), startPos, endPos);
				
				// Get object stream successfully.
				if (mStatus.isSuccess()) {
					byte[] buffer = new byte[BUFFER_SIZE];
					int offset = 0;
					RandomAccessFile threadfile = new RandomAccessFile(mStatus.getSaveFile(), "rwd");
					threadfile.seek(startPos);
					
					while (!mStop && (offset = is.read(buffer, 0, BUFFER_SIZE)) != -1) {
						threadfile.write(buffer, 0, offset);
						mThreadDownloaded += offset;
						mStatus.updateStatus(mThreadId, mThreadDownloaded);
						mStatus.appendDownloaded(offset);
					}
					threadfile.close();
					///is.close();
				}

				if (mStop) {
					mStopped = true;
					Utils.Log.d("DownloadThread[" + mThreadId + 
						"], stopped and downloaded:" + mThreadDownloaded);
				} else {
					mComplete = true;
					Utils.Log.d("DownloadThread[" + mThreadId + 
						"], completed:" + mThreadDownloaded);
				}

				///TODO: Why is.close is so heavy that will block the thread run over
				if (is != null) {
					is.close();
				}
				
			} catch (SSLException e) {
				Utils.Log.e("DownloadThread id:" + mThreadId + " met net ssl error:" + e);
				e.printStackTrace();
				mStatus.setDownloadError(ErrorNO.ERR_NETWORK_DISCONNECTED);
				
			} catch (Exception e) {
				Utils.Log.e("DownloadThread id:" + mThreadId + " met an error:" + e);
				e.printStackTrace();
				//mThreadDownloaded = -1;
				mStatus.setDownloadError(ErrorNO.ERR_ERROR);
			}
		}
	}

	public void stopRunning() {
		mStop = true;
	}

	public boolean isCompleted() {
		return mComplete;
	}

	public boolean isStopped() {
		return mStopped;
	}

	public int getThreadDownloaded() {
		return mThreadDownloaded;
	}
}
