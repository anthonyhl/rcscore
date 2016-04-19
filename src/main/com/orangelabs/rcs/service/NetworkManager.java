/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.orangelabs.rcs.service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.telephony.TelephonyManager;

import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData.NetworkAccessType;
import com.orangelabs.rcs.utils.logger.Logger;

public class NetworkManager {
	
	private ListenerAdapter listener;

	public interface ConnectivityListener {
		void onConnected(final int network);
		void onDisconnected(final int network);
	}
	
	public class ListenerAdapter  implements ConnectivityListener {

		private Executor exec;
		private ConnectivityListener listener;
		
		ListenerAdapter(ConnectivityListener l) {
			exec = Executors.newSingleThreadExecutor();
			listener = l;
		}

		@Override
		public void onConnected(final int network) {
			exec.execute(new Runnable() {
				public void run () {
					listener.onConnected(network);
				}
			});			
		}

		@Override
		public void onDisconnected(final int network) {
			exec.execute(new Runnable() {
				public void run () {
					listener.onDisconnected(network);
				}
			});	
			
		}
		
	}
	

    
    /**
     * Connectivity manager
     */
	private ConnectivityManager connectivityMgr;
	
	/**
	 * Network access type
	 */
	private NetworkAccessType network;

	/**
	 * Operator
	 */
	private String operator;
	
    /**
     * Battery level state
     */
    private boolean disconnectedByBattery = false;

    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

	private Context applicationContext;
	
    /**
	 * Constructor

	 */
	public NetworkManager(Context applicationContext, ConnectivityListener listener){
		this.listener = new ListenerAdapter(listener);
		this.applicationContext = applicationContext;

		RcsSettings rcsSettings = RcsSettings.getInstance();
		// Get network access parameters
		network = rcsSettings.getNetworkAccess();

		// Get network operator parameters
		operator = rcsSettings.getNetworkOperator();
		
		// Set the connectivity manager
		connectivityMgr = getConnectivityManager(applicationContext);
		

		
		// Register network state listener
		networkStateListener.start(applicationContext, ConnectivityManager.CONNECTIVITY_ACTION);

        // Battery management
		batteryLevelListener.start(applicationContext, Intent.ACTION_BATTERY_CHANGED); 
	}

	private static ConnectivityManager getConnectivityManager(Context context) {
		return (ConnectivityManager)context.getSystemService(
				Context.CONNECTIVITY_SERVICE);
	}
	
    /**
     * Is disconnected by battery
     *
     * @return Returns true if disconnected by battery, else returns false
     */
    public boolean isDisconnectedByBattery() {
        return disconnectedByBattery;
    }	
	
	/**
     * Terminate the connection manager
     */
    public void terminate() {
    	batteryLevelListener.stop(applicationContext);
    	networkStateListener.stop(applicationContext);
		    	
    	if (logger.isActivated()) {
    		logger.info("IMS connection manager has been terminated");
    	}
    }
    
    abstract static class Receiver extends BroadcastReceiver {
    	
    	void start(Context context, String action) {
    		IntentFilter intentFilter = new IntentFilter(action);
    		context.registerReceiver(this, intentFilter);
    	}
    	
    	void stop(Context context) {    		
        	try {
        		context.unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
            	// Nothing to do
            }
    	}
    	
        @Override
        public void onReceive(Context context, final Intent intent) {
        	Thread t = new Thread() {
        		public void run() {
        			onReceive(intent);
        		}
        	};
        	t.start();
        }
        
		abstract void onReceive(final Intent intent);
    }
    
    private Receiver networkStateListener = new Receiver() {
    	public void onReceive(final Intent intent) {
    		connectionEvent(intent);
    	}
    };

    
    public static class ConnectivityEvent {
    	
    	private boolean noConnectivity;
		private String reason;
		private boolean failover;
		private NetworkInfo networkInfo;
		private String extraInfo;
		private int networkType;

		ConnectivityEvent(Intent intent, ConnectivityManager connectivityMgr) {
			noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
			extraInfo = intent.getStringExtra(ConnectivityManager.EXTRA_EXTRA_INFO);
			networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
			failover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);			
    	
			networkInfo = connectivityMgr.getActiveNetworkInfo();
		}
		
		int getNetworkType() {
			if (networkInfo == null) return -1;
			return networkInfo.getType();
		}
		
		@Override
		public	String toString() {
			StringBuilder sb = new StringBuilder("Connectivity event change:");
			sb.append("network=").append(networkType).append('\n');
			if (failover) sb.append("failover").append('\n');
			if (noConnectivity) sb.append("no conn").append('\n');
			if (reason != null) sb.append("reason:").append(reason).append('\n');
			if (extraInfo != null) sb.append("extra:").append(extraInfo).append('\n');
			
			return sb.toString();
		}
    }

    /**
     * Connection event
     * 
     * @param intent Intent
     */
    private synchronized void connectionEvent(Intent intent) {
    	if (disconnectedByBattery) {
    		return;
    	}
    	
		if (!intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			return;
		}
		
		ConnectivityEvent ev = new ConnectivityEvent(intent, connectivityMgr);
		
		if (logger.isActivated()) {
			logger.debug(ev.toString());
		}
		
		checkConnectInfo();
    }
    
    void checkConnectInfo() {

		// Check received network info
    	NetworkInfo networkInfo = connectivityMgr.getActiveNetworkInfo();
		if (networkInfo == null) {
			// Disconnect from IMS network interface
			if (logger.isActivated()) {
				logger.debug("Disconnect from IMS: no network (e.g. air plane mode)");
			}
			disconnectFromIms();
			return;
		}
		
		if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE
				&& networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
			if (logger.isActivated()) {
				logger.debug("Disconnect from IMS:" + networkInfo.getTypeName()
						+ " network");
			}
			return ;
		}
					

        // Check if SIM account has changed (i.e. hot SIM swap)
		if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
			
	        if (LauncherUtils.isAccountChanged()) {
	        	handleSimHasChanged();
	        	return;	            
	        }	        
	        connectToIms(ConnectivityManager.TYPE_MOBILE);
	        return;
		}
		
		if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {        
	        connectToIms(ConnectivityManager.TYPE_WIFI);
	        return;
		}
    }



	void handleSimHasChanged() {
		//listener.onConfigurationChange();
	}

	private boolean isAuthorizedOperator() {
		TelephonyManager tm = (TelephonyManager)applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
		String currentOpe = tm.getSimOperatorName();
		return (operator.length() > 0) && !currentOpe.equalsIgnoreCase(operator);
	}

	/**
     * Connect to IMS network interface
     * 
     * @param ipAddr IP address
     */
    private void connectToIms(int network) {
    	
        // Check if SIM account has changed (i.e. hot SIM swap)
		if (network == ConnectivityManager.TYPE_MOBILE) {
			
	        if (LauncherUtils.isAccountChanged()) {
	        	handleSimHasChanged(); 
	        	return;
	        }
	        
	        this.network = NetworkAccessType.valueOf(network);
	        listener.onConnected(network);
	        return;
		}
		
		if (network == ConnectivityManager.TYPE_WIFI) {        
	        this.network = NetworkAccessType.valueOf(network);
	        listener.onConnected(network);
	        return;
		}
    }
    
    public int getNetwork() {
		return network.toInt();
	}

	/**
     * Disconnect from IMS network interface
     */
    private void disconnectFromIms() {
		listener.onDisconnected(network.toInt());
    }

    /**
     * Battery level listener
     */
    private Receiver batteryLevelListener = new Receiver() {
        @Override
        public void onReceive(Intent intent) {
            onBattery(intent);
        }
    };
    
    boolean isBatteryLow(Intent intent) {
    	int batteryLimit = RcsSettings.getInstance().getMinBatteryLevel();
    	int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
    	int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 1);
    	if (logger.isActivated()) {
    		logger.info("Battery level: " + level + "% plugged: " + plugged);
    	}

    	return level <= batteryLimit && plugged == 0;
    }
    
    boolean isBatteryConfiged() {
    	int batteryLimit = RcsSettings.getInstance().getMinBatteryLevel();
    	return batteryLimit > 0;
    }
    
	void onBattery(Intent intent) {

		if (!isBatteryConfiged()) {
			return;
		}
		
		if (isBatteryLow(intent)) {
			if (!disconnectedByBattery) {
				disconnectedByBattery = true;	
				disconnectFromIms();
			}	
		}
		else {
			if (disconnectedByBattery) {
				disconnectedByBattery = false;
				checkConnectInfo();
			}
		}
	}
    
	/**
	 * @return true is device is in roaming
	 */
	public boolean isInRoaming() {
		if (connectivityMgr != null && connectivityMgr.getActiveNetworkInfo() != null) {
			return connectivityMgr.getActiveNetworkInfo().isRoaming();
		}
		return false;
	}
}
