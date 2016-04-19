package com.orangelabs.rcs.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import android.content.Context;
import android.os.Environment;
import android.telephony.TelephonyManager;

import com.orangelabs.rcs.utils.logger.Logger;

public class SimCard {
	private String imsi;
	private String line1Number;
	private String mc, mcc, mnc;

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private String configHost;

	private SimCard() {
	}

	private boolean loadfile() {
		// Check if a configuration file for HTTPS provisioning exists
		File file = new File(Environment.getExternalStorageDirectory(), "joyn_sim.txt");

		if (!file.exists()) {
			return false;
		}

		if (logger.isActivated()) {
			logger.debug("sim card file found !");
		}

		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			try {
				imsi = br.readLine();
				line1Number = br.readLine();
				configHost = br.readLine();
				return true;
			} finally {
				br.close();
			}
		} catch (Exception e) {
			return false;
		}
	}

	void read(Context context) {
		if (!loadfile()) {
			TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			imsi = tm.getSubscriberId();
			line1Number = tm.getLine1Number();
			mc = tm.getSimOperator();
		}
		else {
			mc = imsi.substring(0,  5); //assume 3-digits mnc
		}
		
		if (line1Number == null) {
			logger.error("Can't read Phone Number SIM");
			return;
		}
		
		if (mc != null) {
			mcc = mc.substring(0, 3);
			mnc = mc.substring(3);
			while (mnc.length() < 3) { 
				mnc = "0" + mnc;
			}
		}
	}
	
	public String getImsi() {
		return imsi;
	}
	
	public String getNumber() {
		return line1Number;
	}

	public static SimCard load(Context context) {
		SimCard sim = new SimCard();
	    sim.read(context);
		return sim;
	}
	
	public String getMcc() {
		return mcc;
	}
	
	public String getMnc() {
		return mnc;
	}
	
	public String getRcsConfigDomain() {
		if (configHost != null) {
			return configHost;
		}

		return "config.rcs." + "mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org";
	}
	
	public String getHomeDomain() {
		if (configHost != null) {
			return configHost;
		}

		return "ims.mnc" + mnc + ".mcc" + mcc + ".3gppnetwork.org";
	}
}