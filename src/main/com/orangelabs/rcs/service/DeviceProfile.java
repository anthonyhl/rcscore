package com.orangelabs.rcs.service;

import android.content.Context;

public final class DeviceProfile {
	
	static Context applicationContext;
	static DeviceProfile instance;	

	public static DeviceProfile getInstance(Context ctx) {
		if (instance == null) {
			instance = new DeviceProfile(ctx);
			return instance;
		}
		if (ctx == applicationContext || ctx.getApplicationContext() == applicationContext){
			return instance;
		}
		return instance;
	}
	
	public DeviceProfile(Context ctx) {
		
	}
}
