package com.cmcc.ccs.profile;

import java.util.HashMap;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceListener;

public class ProfileService extends RcsService {
	
	public static final String PHONE_NUMBER = "PHONE_NUMBER";
	public static final String NAME = "NAME";
	public static final String PROTRAIT = "PROTRAIT";
	public static final String ADDRESS = "ADDRESS";
	public static final String PHONE_NUMBER_SECOND = "PHONE_NUMBER_SECOND";
	public static final String EMAIL = "EMAIL";
	public static final String BIRTHDAY = "BIRTHDAY";
	public static final String COMPANY = "COMPANY";
	public static final String COMPANY_TEL = "COMPANY_TEL";
	public static final String TITLE = "TITLE";
	public static final String COMPANY_ADDR = "COMPANY_ADDR";
	public static final String COMPANY_FAX = "COMPANY_FAX";
	
	public static final String HOME1 = "HOME1";
	public static final String HOME2 = "HOME2";
	public static final String HOME3 = "HOME3";
	public static final String HOME4 = "HOME4";
	public static final String HOME5 = "HOME5";
	public static final String HOME6 = "HOME6";
	public static final String WORK1 = "WORK1";
	public static final String WORK2 = "WORK2";
	public static final String WORK3 = "WORK3";
	public static final String WORK4 = "WORK4";
	public static final String WORK5 = "WORK5";
	public static final String WORK6 = "WORK6";
	
	
	public static final int OK = 0;
	public static final int TIMEOUT = 1;
	public static final int UNKNOW = 2;
	public static final int UNAUTHORIZED = 3;
	public static final int FORBIDEN = 4;
	public static final int NOTFOUND = 5;
	public static final int INTERNEL_ERROR = 6;
	
	/**
	 * API
	 */
	private IProfileService api = null;

	public ProfileService(Context ctx, RcsServiceListener listener) {
		super(ctx, listener);
		// TODO Auto-generated constructor stub
	}
	
	 /**
     * Connects to the API
     */
    public void connect() {
    	//bindService(ctx, IProfileService.class, apiConnection);
    }
    
    /**
     * Disconnects from the API
     */
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
    	
        this.api = (IProfileService)api;
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
    
    
    void setProfileInfo(HashMap<String, String> profile) {
    	
    }
    
    void getProfileInfo() {
    	
    }
    
    void addProfileListener(ProfileListener listener) {
    	
    }
    
    void removeProfileListener(ProfileListener listener) {
    	
    }
    
}
