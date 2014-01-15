package com.jdmmis.otaclient.download;

import java.io.InputStream;

import com.jdmmis.otaclient.download.IDownloadObject;
import com.jdmmis.otaclient.download.aws.AwsDownloadObjectProxy;
public class DownloadObjectProxy  implements IDownloadObject {
	private IDownloadObject mProxy;
	public DownloadObjectProxy() {
		mProxy = new AwsDownloadObjectProxy();
	}

	public int getObjectSize(DownloadStatus status, String objectName) {
		return (int)mProxy.getObjectSize(status, objectName);
	}

	public InputStream getObjectStream(DownloadStatus status, String objectName, int start, int end) {
		return mProxy.getObjectStream(status, objectName, start, end);
	}

	public void close() {
		mProxy.close();
	}
}
