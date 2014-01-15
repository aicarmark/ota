package com.jdmmis.otaclient.detector;

import java.util.Map;
import com.jdmmis.otaclient.detector.aws.AwsTablesProxy;

public class TablesProxy implements ITables {

	private ITables mProxy;
	public TablesProxy() {
		mProxy = new AwsTablesProxy();
	}

	public DeviceItem getDevice(DetectResult result, String imei) {
		return mProxy.getDevice(result, imei);
	}

	public void updateDevice(DetectResult result, String imei, DeviceItem device) {
		mProxy.updateDevice(result, imei, device);
	}

	public Map<String, ReleaseItem> getPatches(DetectResult result, String main, String from, String to) {
		return mProxy.getPatches(result, main, from, to);
	}

	public ReleaseItem getRelease(DetectResult result, String main, String patch){
		return mProxy.getRelease(result, main, patch);
	}

	public void close() {
		mProxy.close();
	}
}
