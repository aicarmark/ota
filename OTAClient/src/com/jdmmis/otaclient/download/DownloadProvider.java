package com.jdmmis.otaclient.download;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;

import com.jdmmis.otaclient.utils.Utils;

/**
 * Records download status into database.
 */
public class DownloadProvider {
	private DBOpenHandler mDBHelper;

	class DBOpenHandler extends SQLiteOpenHelper {
		private final static String DBNAME = "otadownloadlog.db";
		private final static int VERSION = 1;

		public DBOpenHandler(Context context) {
			super(context, DBNAME, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			try {
				// Threads table records all threads' downloaded status.
				db.execSQL("CREATE TABLE IF NOT EXISTS downloadthreads(" +
					"id integer primary key autoincrement," +
					"objectname varchar(100)," + 
					"threadid INTEGER," +
					"downloaded INTEGER)");
				// Object table records downloading object information.
				db.execSQL("CREATE TABLE IF NOT EXISTS downloadobject(" +
					"id integer primary key autoincrement," +
					"objectname varchar(100)," + 
					"md5 varchar(100)," +
					"objectsize INTEGER)");
			} catch (Exception e) {
				Utils.Log.e("Download provider create db met an error:" + e);
			}
			Utils.Log.d("DownloadProvider db gets created successfully");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			try {
				db.execSQL("DROP TABLE IF EXISTS downloadthreads");
				db.execSQL("DROP TABLE IF EXISTS downloadobject");
			} catch (Exception e) {
				Utils.Log.e("Download provider upgrade db met an error:" + e);
			}
			onCreate(db);
		}
	}

	public DownloadProvider (Context context) {
		mDBHelper = new DBOpenHandler(context);
	}

	// Read download status only from object table
	public void readObject(DownloadStatus status) {
		Cursor cursor = null;
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		// Read from object table to get the object to be downloaded.
		//if (status.getDownloadObject()== null) {
			cursor = db.rawQuery("select objectname, md5, objectsize from downloadobject", null);
			while (cursor.moveToNext()) {
				status.setDownloadObject(cursor.getString(0));
				status.setMD5(cursor.getString(1));
				status.setObjectSize(cursor.getInt(2));
			}
			cursor.close();
		//}
		db.close();
	}

	// Update object only to object table
	public void updateObject(DownloadStatus status) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			db.execSQL("update downloadobject set objectsize=? where objectname=?", 
				new Object[]{status.getObjectSize(), status.getDownloadObject()});
		} catch (Exception e) {
			Utils.Log.e("Download provider delete status met an error:" + e);
		}
		db.close();
	}

	// Insert object into only object table
	public void insertObject(DownloadStatus status) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try{
			db.execSQL("insert into downloadobject(objectname, md5, objectsize) values(?,?,?)",
					new Object[]{status.getDownloadObject(), status.getMD5(), status.getObjectSize()});
		} catch (Exception e){
			Utils.Log.e("insert object met an error:" + e);
			e.printStackTrace();
		}
		db.close();
	}

	// Delete only object table
	public void deleteObjects() {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			db.execSQL("delete from downloadobject");
		} catch (Exception e) {
			Utils.Log.e("Download provider delete object met an error:" + e);
		}
		db.close();
	}

	public void deleteObject(String objName){
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			db.execSQL("delete from downloadobject where objectname=?", new Object[]{objName});
		} catch (Exception e) {
			Utils.Log.e("Download provider delete status met an error:" + e);
		}
		db.close();
	}

	// Insert only threads info to thread table
	public void insertThreads(DownloadStatus status) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		db.beginTransaction();
		try{
			for(Map.Entry<Integer, Integer> entry : status.getThreadStatusMap().entrySet()){
				db.execSQL("insert into downloadthreads(objectname, threadid, downloaded) values(?,?,?)",
					new Object[]{status.getDownloadObject(), entry.getKey(), entry.getValue()});
			}
			db.setTransactionSuccessful();
		}finally{
			db.endTransaction();
		}
		db.close();
	}

	// Update only threads info to thread table
	public void updateThreads(DownloadStatus status) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		db.beginTransaction();
		try{
			for (Map.Entry<Integer, Integer> entry : status.getThreadStatusMap().entrySet()) {
				db.execSQL("update downloadthreads set downloaded=? where objectname=? and threadid=?",
					new Object[]{entry.getValue(), status.getDownloadObject(), entry.getKey()});
			}
			db.setTransactionSuccessful();
		} finally{
			db.endTransaction();
		}
		db.close();
	}

	// Delete all threads information if MD5 check failed.
	public void deleteThreads(String objName) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			db.execSQL("delete from downloadthreads where objectname=?", new Object[]{objName});
		} catch (Exception e) {
			Utils.Log.e("Download provider delete status met an error:" + e);
		}
		db.close();
	}

	
	// Read both object and threads.
	public void readStatus(DownloadStatus status) {
		Cursor cursor = null;
		SQLiteDatabase db = mDBHelper.getReadableDatabase();
		
		// Read from object table to get the object to be downloaded.
		if (status.getDownloadObject()== null) {
			cursor = db.rawQuery("select objectname, md5, objectsize from downloadobject", null);
			while (cursor.moveToNext()) {
				status.setDownloadObject(cursor.getString(0));
				status.setMD5(cursor.getString(1));
				status.setObjectSize(cursor.getInt(2));
			}
			cursor.close();
		}

		// Read from threads table to get all threads status.
		if (status.getDownloadObject() != null) {
			cursor = db.rawQuery("select threadid, downloaded from downloadthreads where objectname=?", 
				new String[]{status.getDownloadObject()});
			int downloaded = 0;
			while (cursor.moveToNext()) {
				status.putThreadDownloaded(cursor.getInt(0), cursor.getInt(1));
				downloaded += cursor.getInt(1);
			}
			status.setTotalDownloaded(downloaded);
			cursor.close();
			Utils.Log.d("DownloadProvider read status got one status:" + status);
		}
		db.close();
	}

	public void deleteStatus() {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			db.execSQL("delete from downloadobject");
			db.execSQL("delete from downloadthreads");
		} catch (Exception e) {
			Utils.Log.e("Download provider delete status met an error:" + e);
		}
	}

	public void deleteStatus(String objName){
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			db.execSQL("delete from downloadobject where objectname=?", new Object[]{objName});
			db.execSQL("delete from downloadthreads where objectname=?", new Object[]{objName});
		} catch (Exception e) {
			Utils.Log.e("Download provider delete status met an error:" + e);
		}
		db.close();
	}
}
