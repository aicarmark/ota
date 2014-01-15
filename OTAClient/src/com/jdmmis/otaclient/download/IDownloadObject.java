package com.jdmmis.otaclient.download;

import java.io.InputStream;

public interface IDownloadObject {
	public int getObjectSize(DownloadStatus status, String objectName);
	public InputStream getObjectStream(DownloadStatus status, String objectName, int start, int pos);
	public void close();
}
