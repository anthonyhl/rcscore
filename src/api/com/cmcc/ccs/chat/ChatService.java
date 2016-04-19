package com.cmcc.ccs.chat;

import java.util.Set;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;

import com.cmcc.ccs.profile.IProfileService;
import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceListener;

public class ChatService extends RcsService {

	public static final int TIMEOUT = 0;
	public static final int UNKNOWN = 1;
	public static final int INTERNAL = 2;
	public static final int OUTOFSIZE = 3;
	/**
	 * API
	 */
	private IChatService api = null;

	public ChatService(Context ctx, RcsServiceListener listener) {
		super(ctx, listener);
		// TODO Auto-generated constructor stub
	}
	
	 /**
     * Connects to the API
     */
	@Override
    public void connect() {
    	//bindService(ctx, IChatService.class, apiConnection);
    }
    
    /**
     * Disconnects from the API
     */
	@Override
    public void disconnect() {
    	try {
    		ctx.unbindService(apiConnection);
        } catch(IllegalArgumentException e) {
        	// Nothing to do
        }
    }

	/**
	 * Set API interface
	 * 
	 * @param api API interface
	 */
    protected void setApi(IInterface api) {
    	super.setApi(api);
    	
        this.api = (IChatService)api;
    }
    
    /**
	 * Service connection
	 */
	private ServiceConnection apiConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	setApi(IProfileService.Stub.asInterface(service));
        	if (serviceListener != null) {
        		serviceListener.onServiceConnected();
        	}
        }

        public void onServiceDisconnected(ComponentName className) {
        	setApi(null);
        	if (serviceListener != null) {
        		serviceListener.onServiceDisconnected(Error.CONNECTION_LOST);
        	}
        }
    };
    
	long sendMessage(String contact, String message){
		return 0;
	}
	
	ChatMessage getChatMessage(long msgId) {
		return null;
	}
	
	String sendOTMMessage(Set<String> Contacts, String message) {
		return message;		
	}

	String resendMessage(long msgId) {
		return "";
	}
	
	boolean deleteMessage(long msgId) {
		return false;
	}
	
	boolean setMessageRead(long msgId) {
		return false;
	}
	
	boolean setMessageFavorite(long msgId) {
		return false;
	}
	
	boolean moveBlockMessagetoInbox(long msgId) {
		return false;
	}
}
