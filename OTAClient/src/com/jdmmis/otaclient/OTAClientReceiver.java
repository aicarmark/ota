package com.jdmmis.otaclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jdmmis.otaclient.utils.Utils;

public class OTAClientReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		//Utils.Log.d("OTAClientReceiver receives an intent:" + intent);
		intent.setClass(context, OTAClientService.class);
		context.startService(intent);
	}
}
