package com.jdmmis.otaclient.utils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Environment;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import android.util.Log;

public class Utils {

	private final static String TAG = "OTAClient";
	private final static boolean LOGD = true;

	private final static int KB = 1024;
	private final static int MB = KB * 1024;
	private final static int GB = MB * 1024;

	public static class MD5 {
		public static String calculateMD5(File updateFile) {
			MessageDigest digest;
			try {
				digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				Log.e("MD5 calculateMD5 exception while getting digest:" + e);
				return null;
			}

			InputStream is;
			try {
				is = new FileInputStream(updateFile);
			} catch (IOException e) {
				Utils.Log.e("MD5 calculateMD5 exception while getting FileInputStream:" + e);
				return null;
			}

			byte[] buffer = new byte[8192];
			int read;
			try {
				while ((read = is.read(buffer)) > 0) {
					digest.update(buffer, 0, read);
				}
				byte[] md5sum = digest.digest();
				BigInteger bigInt = new BigInteger(1, md5sum);
				String output = bigInt.toString(16);
				// Fill to 32 chars
				output = String.format("%32s", output).replace(' ', '0');
				return output;
			} catch (IOException e) {
				throw new RuntimeException("Unable to process file for MD5", e);
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					Utils.Log.d("Exception on closing MD5 input stream:" + e);
				}
			}
		}
		
		public static boolean checkMD5(String md5, File updateFile) {
			if (TextUtils.isEmpty(md5) || updateFile == null) {
				Log.e("MD5 checkMD5 md5 string empty or updateFile null");
				return false;
			}

			String calculatedDigest = calculateMD5(updateFile);
			if (calculatedDigest == null) {
				Log.e("MD5 calculatedDigest null");
				return false;
			}

			Log.v("MD5 Calculated digest:" + calculatedDigest);
			Log.v("MD5 Provided digest:" + md5);
			return calculatedDigest.equalsIgnoreCase(md5);
		}
	}

	public static class Network {
		public static String NETWORK_NONE = "none";
		public static String NETWORK_MOBILE = "mobile";
		public static String NETWORK_WIFI = "wifi";
		
		private static String mNetwork = NETWORK_NONE;
		public static void setNetworkMobile() {
			mNetwork = NETWORK_MOBILE;
		}
		public static void setNetworkWifi() {
			mNetwork = NETWORK_WIFI;
		}
		public static void setNetworkNone() {
			mNetwork = NETWORK_NONE;
		}
		public static boolean isNetworkMobile() {
			return mNetwork.equals(NETWORK_MOBILE);
		}
		public static boolean isNetworkWifi() {
			return mNetwork.equals(NETWORK_WIFI);
		}
		public static boolean isNetworkNone() {
			return mNetwork.equals(NETWORK_NONE);
		}

		public static void updateNetwork(Context context) {
			ConnectivityManager cm = (ConnectivityManager)
				context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo active = cm.getActiveNetworkInfo();
			boolean isConnected = active!=null && active.isConnectedOrConnecting();
			//Utils.Log.d("Utils network update network connected:" + isConnected);

			if (!isConnected) {
				setNetworkNone();
			} else {
				//Utils.Log.d("Utils network there is active connected network type:" 
				//	+ active.getType() + ", status:" + active.getState());
				if (active.getType() == ConnectivityManager.TYPE_MOBILE) {
					Utils.Network.setNetworkMobile();
				} else if (active.getType() == ConnectivityManager.TYPE_WIFI){
					Utils.Network.setNetworkWifi();
				}
			}
		}
	}

	// Log overrides with TAG & LOGD added.
	// Hope all logs with same TAG for sequential review.
	public static class Log {
		public static void i(String log) {
			i(Utils.LOGD, log);
		}

		public static void i(boolean logd, String log) {
			if (logd) android.util.Log.i(Utils.TAG, log);
		}
		
		public static void d(String log) {
			d(Utils.LOGD, log);
		}

		public static void d(boolean logd, String log) {
			if (logd) android.util.Log.d(Utils.TAG, log);
		}

		public static void d(String log, Exception ex) {
			d(Utils.LOGD, log, ex);
		}

		public static void d(boolean logd, String log, Exception ex) {
			if (logd) android.util.Log.d(Utils.TAG, log, ex);
		}
		
		public static void v(String log) {
			v(Utils.LOGD, log);
		}

		public static void v(boolean logd, String log) {
			if (logd) android.util.Log.v(Utils.TAG, log);
		}

		public static void e(String log) {
			// no if (Utils.LOGD) for e log
			e(true, log);
		}

		public static void e(boolean logd, String log) {
			if (logd) android.util.Log.e(Utils.TAG, log);
		}
	}


	// Class that facilitates offloading of processing from the main thread so as to
	// help avoid ANRs.
	public static abstract class RunInBackgroundThread implements Runnable {
		private static final String THREAD_NAME = "otaclient_bg";
		private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_DEFAULT +
			Process.THREAD_PRIORITY_LESS_FAVORABLE;

		private static Handler sHandler;

		static {
			// Create a non-main thread with a hanlder that runs at a slightly
			// lower priority than the main thread
			HandlerThread bgThread = new HandlerThread(THREAD_NAME, THREAD_PRIORITY);

			// Start the handler thread so that the looper gets created
			bgThread.start();

			// Create a handler on the queue of the newly created thread, that 
			// can later be used to post Runnable's to that thread.
			sHandler = new Handler(bgThread.getLooper());
		}

		public RunInBackgroundThread() {
			// Send this object to the queue of the non-main thread, 
			// for asynchronous execution
			sHandler.post(this);
		}
	}


	///TODO: Following information should be optimized later
	public static class Access {
		//private static String mAccessKey;
		//private static String mSecretKey;
		public static String getAccessKey() {
			return "AKIAJNAEUX2ONJLLZ6WQ";
		}

		public static String getSecretKey() {
			return "n93MssoPitavYl8Ex9CEIup4cJLKYEf6T9OQ7Ler";
		}
	}

	public static class OTAString {
		public static String getOTAString() {
			return "XT920.Retail.en.BR";
		}

		public static String getDeviceTableName() {
			return getOTAString() + "-Device";
		}

		public static String getReleaseTableName() {
			return getOTAString() + "-Release";
		}

		public static String getS3BucketName() {
			return "otatestbucket";
		}

		///TODO: Endpoint should be determined by OTAString which means
		// which server region will be used.
		public static String getDdbEndpoint() {
			return "dynamodb.us-west-2.amazonaws.com/";
		}

		// Return the device real version
		public static String getDeviceVersion() {
			return "M9615A-CEFWMAZM-2.0.1700.00";
		}
	}

	public static class IMEI {
		private static String mIMEIString = "355136058710560";
		public static String getIMEI() {
			return mIMEIString;
		}
		public static void SetIMEI(String imei) {
			mIMEIString = imei;
		}
	}

	public static String getSavePath(String objectName) {
		String directory = Environment.getExternalStorageDirectory() + "/otadownloads";
		//String directory = "/sdcard/Download" + "/otadownloads";
		File file = new File(directory);   
		if (!file.exists()) {                
			file.mkdirs();          
		}            
		return directory + "/" + objectName;
	}

	public static String getSizeString(int size) {
		DecimalFormat df = new DecimalFormat("#.00"); 
		String fileSizeString = ""; 
		if (size < 1024) { 
			fileSizeString = df.format((double) size) + "B"; 
		} else if (size < 1048576) { 
			fileSizeString = df.format((double) size / 1024) + "KB"; 
		} else if (size < 1073741824) { 
			fileSizeString = df.format((double) size / 1048576) + "MB"; 
		} else { 
			fileSizeString = df.format((double) size / 1073741824) + "GB"; 
		} 
		return fileSizeString; 
	}

	public static String getDateString() {
		return Utils.getDateString(new Date());
	}

	public static String getDateString(Date date) {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		return format.format(date);
	}
}