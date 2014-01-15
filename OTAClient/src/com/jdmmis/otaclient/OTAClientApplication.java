package com.jdmmis.otaclient;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jdmmis.otaclient.detector.OTAClientDetector;
import com.jdmmis.otaclient.download.OTAClientDownload;
import com.jdmmis.otaclient.utils.Utils;

public class OTAClientApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		Utils.Log.d("OTAClientApplication has been created");
		OTAClientDetector detector = OTAClientDetector.getInstance(this);
		OTAClientDownload download = OTAClientDownload.getInstance(this);
		// Nothing to do
		// Ensure the service be started
		Intent intent = new Intent("Start OTAClient Service for test");
		intent.setClass(this, OTAClientService.class);
		this.startService(intent);
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		Utils.Log.d("OTAClientApplication has been terminated");
	}
}
