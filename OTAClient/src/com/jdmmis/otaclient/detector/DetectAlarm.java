package com.jdmmis.otaclient.detector;

import java.util.Date;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.jdmmis.otaclient.OTAClientConstant;
import com.jdmmis.otaclient.OTAClientService;
import com.jdmmis.otaclient.utils.Utils;

public class DetectAlarm implements OTAClientConstant{
	private Context mContext;
	public DetectAlarm(Context context) {
		mContext = context;
	}

	// Calculate alarm date
	private Date calcAlarmDate() {
		Date date = new Date();
		///TODO: Calculate alarm date according to device count in device table.
		return date;
	}

	public boolean isAlarmExpired() {
		SharedPreferences sp = mContext.getSharedPreferences(
			OTACLIENT_SP, Context.MODE_PRIVATE);  
		long alarm = sp.getLong(SP_DETECTALARM, -1); 
		Date date = new Date();
		return (alarm == -1 ? false : (alarm<date.getTime()));
	}

	private void updateAlarm(SharedPreferences sp, long alarm, long interval) {
		// Set alarm
		AlarmManager mgr = (AlarmManager)
			mContext.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(ACTION_DETECT_ALARM);
		intent.setClass(mContext, OTAClientService.class);
		PendingIntent pending = PendingIntent.getService(mContext, 0, intent, 1);
		mgr.cancel(pending);
		mgr.set(AlarmManager.RTC_WAKEUP, alarm, pending);

		// Save it into sp
		SharedPreferences.Editor editor = sp.edit();
		editor.putLong(SP_DETECTALARM, alarm);
		editor.putLong(SP_DETECTINTERVAL, interval);
		editor.commit();
	}

	public void updateNextAlarm(boolean toCurrent) {
		SharedPreferences sp = mContext.getSharedPreferences(
			OTACLIENT_SP, Context.MODE_PRIVATE);  
		long alarm = sp.getLong(SP_DETECTALARM, -1); 
		long interval = sp.getLong(SP_DETECTINTERVAL, -1);
		boolean needsUpdate = false;

		// First time run
		if (alarm == -1) {
			alarm = calcAlarmDate().getTime();
			interval = INTERVAL_DEFAULT;
			needsUpdate = true;
		} else {
			//alarm += (interval * 60 * 1000);
		}

		// Needs update if the previous alarm is expired
		Date curr = new Date();
		if (toCurrent) {
			alarm = curr.getTime();
		}
		while (alarm <= curr.getTime()) {
			needsUpdate = true;
			alarm += (interval * 60 * 1000);
		}

		Utils.Log.d("DetectAlarm next alarm:" + 
			Utils.getDateString(new Date(alarm)) + ", interval:" 
			+ interval + ", needsUpdate:" + needsUpdate);
		if (needsUpdate) {
			updateAlarm(sp, alarm, interval);
		}
	}

	public void updateInterval(long interval) {
		SharedPreferences sp = mContext.getSharedPreferences(
			OTACLIENT_SP, Context.MODE_PRIVATE);  
		long old = sp.getLong(SP_DETECTINTERVAL, -1);
		if (interval != old) {
			long alarm = sp.getLong(SP_DETECTALARM, -1); 
			Date curr = new Date();
			alarm = alarm - old;
			while (alarm < curr.getTime()) {
				alarm += interval;
			}
			updateAlarm(sp, alarm, interval);
		}
	}
}
