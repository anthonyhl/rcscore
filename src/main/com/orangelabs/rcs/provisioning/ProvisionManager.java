package com.orangelabs.rcs.provisioning;

import java.util.Date;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.orangelabs.rcs.provider.LocalContentResolver;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provisioning.https.HttpsProvisioningManager;
import com.orangelabs.rcs.service.LauncherUtils;
import com.orangelabs.rcs.utils.logger.Logger;

public class ProvisionManager {
	/**
	 * Launch boot flag
	 */
	public boolean boot;
	/**
	 * Launch user flag
	 */
	public boolean user;
	/**
	 * Retry Intent
	 */
	public PendingIntent retryIntent;
	/**
	 * Provisioning manager
	 */
	public HttpsProvisioningManager httpsProvisioningMng;
	/**
	 * Retry receiver
	 */
	public BroadcastReceiver retryReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        Thread t = new Thread() {
	            public void run() {
	                httpsProvisioningMng.updateConfig();
	            }
	        };
	        t.start();
	    }
	};
	private LocalContentResolver mLocalContentResolver;
	private RcsSettings settings;
	
	Logger logger = Logger.getLogger(ProvisionManager.class.getSimpleName());
	private Context ctx;
	
	/**
	 * Retry action for provisioning failure
	 */
	public static final String ACTION_RETRY = "com.orangelabs.rcs.provisioning.ACTION_RETRY";

	public ProvisionManager(boolean boot, boolean user, PendingIntent retryIntent) {
		this.boot = boot;
		this.user = user;
		this.retryIntent = retryIntent;
	}

	public ProvisionManager(Context context,
			LocalContentResolver resolver, RcsSettings settings) {
		this.ctx = context;
		this.mLocalContentResolver = resolver;
		this.settings = settings;
	}

	/**
	 * Start retry alarm
	 * 
	 * @param context
	 * @param intent
	 * @param delay
	 *            delay in milli seconds
	 */
	public static void startRetryAlarm(Context context, PendingIntent intent, long delay) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, intent);
	}

	/**
	 * Cancel retry alarm
	 * 
	 * @param context
	 * @param intent
	 */
	public static void cancelRetryAlarm(Context context, PendingIntent intent) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.cancel(intent);
	}

	public void close() {
		if (httpsProvisioningMng != null) {
			// Unregister network state listener
			httpsProvisioningMng.unregisterNetworkStateListener();
	
			// Unregister wifi disabling listener
			httpsProvisioningMng.unregisterWifiDisablingListener();
	
			// Unregister SMS provisioning receiver
			httpsProvisioningMng.unregisterSmsProvisioningReceiver();
			
			cancelRetryAlarm(ctx, retryIntent);
	        // Unregister retry receiver
	        try {
		        ctx.unregisterReceiver(retryReceiver);
		    } catch (IllegalArgumentException e) {
		    	// Nothing to do
		    }
		}
	}

	public void startProvision(boolean first, boolean user) {
		if (httpsProvisioningMng != null) {
			logger.debug("HTTPS provisioning is running");
			return;
		}
	
		if (logger.isActivated()) {
			logger.debug("Start HTTPS provisioning");
		}
	
		String version = getSettings().getProvisioningVersion();
		// It makes no sense to start service if version is 0 (unconfigured)
		// if version = 0, then (re)set first to true
		try {
			int ver = Integer.parseInt(version);
		    if (ver == 0) {
		        first = true;
		    }
		} catch (NumberFormatException e) {
		    // Nothing to do
		}
		
		retryIntent = PendingIntent.getBroadcast(ctx.getApplicationContext(), 0, new Intent(ProvisionManager.ACTION_RETRY), 0);
		ctx.registerReceiver(retryReceiver, new IntentFilter(ProvisionManager.ACTION_RETRY));
	
		httpsProvisioningMng = new HttpsProvisioningManager(ctx.getApplicationContext(),
				mLocalContentResolver, retryIntent, first, user);
		if (logger.isActivated()) {
			logger.debug("Provisioning parameter: boot=" + first + ", user=" + user + ", version=" + version);
		}
	
		boolean requestConfig = false;
		if (first) {
		    requestConfig = true;
		} else {
		    if (ProvisioningInfo.Version.RESETED_NOQUERY.equals(version)) {
		        // Nothing to do
		    } else if (ProvisioningInfo.Version.DISABLED_NOQUERY.equals(version)) {
		        if (user == true) {
		            requestConfig = true;
		        }
		    } else if (ProvisioningInfo.Version.DISABLED_DORMANT.equals(version) && user == true) {
		        requestConfig = true;
		    } else { // version > 0
		        Date expiration = LauncherUtils.getProvisioningExpirationDate(ctx);
		        if (expiration == null) {
		            requestConfig = true;
		        } else {
		            Date now = new Date();
		            if (expiration.before(now)) {
		                if (logger.isActivated())
		                    logger.debug("Configuration validity expired at " + expiration);
		                requestConfig = true;
		            } else {
		                long delay = (expiration.getTime() - now.getTime());
		                if (delay <= 0L) {
		                    requestConfig = true;
		                } else {
		                    Long validity = LauncherUtils.getProvisioningValidity(ctx) * 1000L;
		                    if (validity != null && delay > validity) {
		                        delay = validity;
		                    }
		                    if (logger.isActivated())
		                        logger.debug("Configuration will expire in " + (delay / 1000)
		                                + " secs at " + expiration);
		                    ProvisionManager.startRetryAlarm(ctx, retryIntent, delay);
		                    
		                }
		            }
		        }
		    }
		}
	
		if (requestConfig) {
			if (logger.isActivated())
				logger.debug("Request HTTP configuration update");
			// Send default connection event
			if (!httpsProvisioningMng.connectionEvent(ConnectivityManager.CONNECTIVITY_ACTION)) {
				// If the UpdateConfig has NOT been done:
				httpsProvisioningMng.registerNetworkStateListener();
			}
		}
		else {
			LauncherUtils.launchRcsCoreService(ctx);
		}
	}

	private RcsSettings getSettings() {
		return settings;
	}
}