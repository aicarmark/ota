package com.jdmmis.otaclient.download;

import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import com.jdmmis.otaclient.utils.Utils;
import com.jdmmis.otaclient.OTAClientConstant;

/**
 * Download strategy:
 * - If there is no pending download, start download while new upgrade available;
 * - Under the scenario that there is pending download, if the new detected upgrade
 *   is a patch version, current download should be continued and then start the 
 *   new download; If the new detected upgrade is a firmware version, current 
 *   download woule be cancelled and start the new download immediately.
 */
public class OTAClientDownload 
	implements Downloader.PersentageListener,
				OTAClientConstant {
	private final static int THREAD_NUMBER = 5;

	private Thread  mBackgroundThread;
	private Handler mBackgroundHandler;

	private ArrayList<IDownloadListener> mListeners;
	private ArrayList<Handler> mHandlers;

	private DownloadProvider mProvider;
	private DownloadStatus mOngoingDownload;
	private IDownloadObject mProxy;
	private Downloader mDownloader;

	private boolean mCancelling = false;
	private boolean mDownloading = false;

	public interface IDownloadListener {
		public void onDownloadStart(String objectName);
		public void onDownloadPercentage(String objectName, int persentage);
		public void onDownloadComplete(String objectName, String savePath);
		public void onDownloadCancel(String objectName);
		public void onDownloadStop(String objectName);
		public void onDownloadError(String objectName, ErrorNO err);
	}

	private static OTAClientDownload sInstance;
	private OTAClientDownload(Context context) {
		mListeners = new ArrayList<IDownloadListener>();
		mHandlers  = new ArrayList<Handler>();
		mProvider  = new DownloadProvider(context);
		mOngoingDownload = new DownloadStatus();
		mProxy = new DownloadObjectProxy();

		initBackground();
		initOngoingDownload();
	}

	public static synchronized OTAClientDownload getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new OTAClientDownload(context);
		}
		return sInstance;
	}

	private void initOngoingDownload() {
		//if (mBackgroundHandler != null) {
		//	mBackgroundHandler.post(new Runnable() {
		//		public void run() {
					mProvider.readStatus(mOngoingDownload);
		//		}
		//	});
		//}
	}
	
	private void initBackground() {
		mBackgroundThread = new Thread() {
			public void run() {
				Looper.prepare();
				mBackgroundHandler = new Handler();
				synchronized(this) {
					notifyAll();
				}
				Looper.loop();
			}
		};

		mBackgroundThread.start();

		synchronized(mBackgroundThread) {
			if (mBackgroundHandler == null) {
				Utils.Log.d("OTAClient download waiting for background handler init");
				try {
					mBackgroundThread.wait();
				} catch (InterruptedException e) {
					Utils.Log.d("OTAClient download error", (Exception)e);
				}
			}
			if (mBackgroundHandler == null) {
				Utils.Log.e("OTAClient download could not create bg handler");
			} else {
				Utils.Log.d("OTAClient download bg handler created");
			}
		}
	}

	public void onPersentage(DownloadStatus status) {
		final String objectName = status.getDownloadObject();
		final int persentage = status.getDownloadPercentage();
		notifyDownloadPercentage(objectName, persentage);
	}

	private void handleComplete() {
		boolean isOK = mOngoingDownload.checkMD5();
		if (isOK) {
			notifyDownloadComplete(mOngoingDownload.getDownloadObject(),
				mOngoingDownload.getSaveFile().getPath());

		} else {
			mOngoingDownload.setDownloadError(ErrorNO.ERR_MD5_FAIL);
			// The error handling will totally be handled in notify
			//mOngoingDownload.getSaveFile().delete(); // delete downloaded file
			//mOngoingDownload.resetDownload(); // reset download
		}
	}

	private void handleStop() {
		notifyDownloadStop(mOngoingDownload.getDownloadObject());
	}

	private void handleError() {
		ErrorNO err = mOngoingDownload.getDownloadError();
		Utils.Log.e("OTAClientDownload handle error:" +err);
		
		switch (err) {
			case ERR_OBJECT_NOT_EXISTS: {
				mOngoingDownload.getSaveFile().delete(); // delete downloaded file
				mProvider.deleteStatus();
				mOngoingDownload = new DownloadStatus();
			} break;

			case ERR_MD5_FAIL: {
				mOngoingDownload.getSaveFile().delete(); // delete downloaded file
				mOngoingDownload.resetDownload(); // reset download
			} break;

			default: {
			} break;
		}
		
		notifyDownloadError(mOngoingDownload.getDownloadObject(), err);
	}

	private void handleCancel() {
		Utils.Log.d("OTAClientDownload handle cancel");
		notifyDownloadCancel(mOngoingDownload.getDownloadObject());
		mProvider.deleteStatus(mOngoingDownload.getDownloadObject());
		mOngoingDownload.getSaveFile().delete(); // delete downloading file
		mCancelling = false;
	}
	
	private void performDownload() {
		// Set provider to update status to db
		mOngoingDownload.setDownloadProvider(mProvider);
		
		mDownloader = new Downloader(mProxy, mOngoingDownload, THREAD_NUMBER);
		// This will block current thread util the whole download is completed
		// or stopped or some error occurred during the download
		mDownloader.download(OTAClientDownload.this);
		// Close the connection with the server
		mProxy.close();
		Utils.Log.d("OTAClientDownload download just returned, has downloaded:"
				+ mOngoingDownload.getTotalDownloaded());

		// Higher priority to check complete
		if (mDownloader.completed()) {
			handleComplete();
			
		// Download has been stopped
		} else if (mDownloader.stopped()) {
			handleStop();
		}

		// Some error occurred
		if (!mOngoingDownload.isSuccess()) {
			handleError();
		}
		
		// Download has been cancelled
		if (mCancelling) {
			handleCancel();
		}

		mDownloader = null;
		mDownloading = false;
	}

	public void attach(Handler handler, IDownloadListener listener) {
		if (handler == null) throw new NullPointerException("dlistener add handler is null");
		if (listener== null) throw new NullPointerException("dlistener add callback is null");
		mHandlers.add(handler);
		mListeners.add(listener);
	}

	public void detach(Handler handler, IDownloadListener listener) {
		mHandlers.remove(handler);
		mListeners.remove(listener);
	}

	public String getOngoingDownloadVersion() {
		return mOngoingDownload.getDownloadObject();
	}

	public int getOngoingDownloadPercent() {
		return mOngoingDownload.getDownloadPercentage();
	}

	public int getOngoingDownloadSize() {
		return mOngoingDownload.getObjectSize();
	}

	public DownloadStatus getOngoingDownload() {
		return mOngoingDownload;
	}

	public boolean recordDownload(final String objectName, String md5) {
		if (mBackgroundHandler != null && objectName != null && !mDownloading) {
			mOngoingDownload = new DownloadStatus(objectName, md5, THREAD_NUMBER);
			mProvider.deleteStatus();
			mProvider.insertObject(mOngoingDownload);
			mProvider.insertThreads(mOngoingDownload);
			return true;
		}
		return false;
	}
	
	public boolean startDownload(final String objectName, String md5) {
		if (mBackgroundHandler != null && objectName != null && !mDownloading) {
			mDownloading = true;
			
			mOngoingDownload = new DownloadStatus(objectName, md5, THREAD_NUMBER);
			mProvider.deleteStatus();
			mProvider.insertObject(mOngoingDownload);
			mProvider.insertThreads(mOngoingDownload);

			Utils.Log.d("OTAClientDownload startDownload obj:" + mOngoingDownload);
			notifyDownloadStart(objectName);
			mBackgroundHandler.post(new Runnable() {
				public void run() {
					performDownload();
				}
			});
			return true;
		}
		return false;
	}

	public boolean resumeDownload() {
		if (mBackgroundHandler != null && !mDownloading) {
			mProvider.readStatus(mOngoingDownload);
			mOngoingDownload.setDownloadError(DownloadStatus.ErrorNO.ERR_NONE);
			if (mOngoingDownload.getDownloadObject() != null &&
				mOngoingDownload.getDownloadPercentage() < 100) { 
				mDownloading = true;
				
				Utils.Log.d("OTAClientDownload resumeDownload obj:" + mOngoingDownload);
				notifyDownloadStart(mOngoingDownload.getDownloadObject());
				mBackgroundHandler.post(new Runnable() {
					public void run() {
						performDownload();
					}
				});
				return true;
			}
		}
		return false;
	}

	public void cancelDownload() {
		if (mDownloader != null && mDownloader.running()) {
			mCancelling = true;
			mDownloader.stop();
		}
	}

	public void stopDownload() {
		Utils.Log.d("OTAClientDownload stop download...");
		if (mDownloader != null && mDownloader.running()) {
			mDownloader.stop();
		}
	}

	public boolean isDownloading() {
		return mDownloading;
	}

	private void notifyDownloadStart(final String objectName) {
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDownloadListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDownloadStart(objectName);
					}
				});
			}
		}
	}

	private void notifyDownloadPercentage(final String objectName, final int persentage) {
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDownloadListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDownloadPercentage(objectName, persentage);
					}
				});
			}
		}
	}

	private void notifyDownloadComplete(final String objectName, final String savePath) {
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDownloadListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDownloadComplete(objectName, savePath);
					}
				});
			}
		}
	}

	private void notifyDownloadCancel(final String objectName) {
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDownloadListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDownloadCancel(objectName);
					}
				});
			}
		}
	}

	private void notifyDownloadStop(final String objectName) {
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDownloadListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDownloadStop(objectName);
					}
				});
			}
		}
	}

	private void notifyDownloadError(final String objectName, final ErrorNO err) {
		if (mListeners.size() > 0) {
			for (int i=0; i<mListeners.size(); i++) {
				Handler h = mHandlers.get(i);
				final IDownloadListener l = mListeners.get(i);
				h.post(new Runnable(){
					public void run() {
						l.onDownloadError(objectName, err);
					}
				});
			}
		}
	}
}
