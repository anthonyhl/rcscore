package com.tct.rcs.features;

public class Connection {
	public enum Event {
		NotRegistered, Registered, Allowed, NotAllowed, Closing
	};
	
	public interface Listener {
		void onEvent (int event, Connection conn);
		void onMessage (String message);
		void onCommandStatus (int status, int messageId);
	}
	
	public String getFeatureTag (){
		return null;
		
	}
	public void close (){
		
	}
	
	public void addListener (Listener listener){
		
	}
	
	public void removeListener (Listener listener){
		
	}
	
	public void sendMessage (String outBoundProxy, String callId, String message, int messageId){
		
	}
	
	public void closeTransaction (String callId){
		
	}
	
	public void closeAllTransactions (){
		
	}

	//package 
	Connection (String featuretag){
		
	}

}
