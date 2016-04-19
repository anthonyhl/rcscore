package com.cmcc.ccs.capability;

import android.os.Parcel;
import android.os.Parcelable;

import com.gsma.services.rcs.capability.Capabilities;

public class ExtCapabilities extends Capabilities implements Parcelable {
	
	public ExtCapabilities(Parcel source) {
		super(source);
	}

	boolean isPublicAccountSupport() {
		return false;
	}
}
