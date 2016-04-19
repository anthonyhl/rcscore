package com.tct.rcs.features;

import java.util.HashMap;
import java.util.Map;


public class ConnectionManager implements AutoCloseable {
	
	Map<String, Connection> connections = new HashMap<>();
	private Listener listener;
	
	public interface Listener {
		void onStatusChange (Status status);
		void onConfigurationChange();
	}
	
	public enum Status {
		Deinit, InProgress, Success, Failure
	};
	
	public int init(){
		return 0;
	}
	
	public Connection createConnection (String featureTag){
		
		return new Connection(featureTag);
	}
	
	@Override
	public void close () {
		
	}
	
	public void register() {
		
	}
	
	public UserConfiguration getUserConfiguration() {
		return null;
		
	}
	
	public DeviceConfiguration getDeviceConfiguration() {
		return null;
		
	}
	
	public void addListener (Listener listener){
		this.listener = listener;
	}
	
	public void removeListener (Listener listener){
		if (this.listener == listener) {
			this.listener = null;
		}
	}

	public static ConnectionManager createInstance() {
		return null;
		
	}

}
