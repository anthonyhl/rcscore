package com.orangelabs.rcs.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.orangelabs.rcs.core.ImsModule;

public class OutgoingCallReceiver extends BroadcastReceiver {
    @Override
	public void onReceive(Context context, Intent intent) {
    	ImsModule ims = ImsModule.getInstance();
    	if (ims != null) {
    		ims.updateRemoteParty(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER));
    	}
	}
}