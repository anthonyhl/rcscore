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

package com.orangelabs.rcs.core;

import android.net.ConnectivityManager;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.capability.CapabilityService;
import com.orangelabs.rcs.core.extension.ServiceExtensionManager;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.filetransfer.http.HttpTransferManager;
import com.orangelabs.rcs.core.ipcall.IPCallService;
import com.orangelabs.rcs.core.network.ImsNetworkInterface;
import com.orangelabs.rcs.core.network.SipManager;
import com.orangelabs.rcs.core.richcall.RichcallService;
import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.core.security.cert.KeyStoreManager;
import com.orangelabs.rcs.core.security.cert.KeyStoreManagerException;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.core.terms.TermsConditionsService;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.protocol.msrp.MsrpConnection;
import com.orangelabs.rcs.protocol.presence.PresenceService;
import com.orangelabs.rcs.protocol.rtp.core.RtpSource;
import com.orangelabs.rcs.protocol.sip.SipEventListener;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.DeviceUtils;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

import com.tct.rcs.features.*;
import static com.tct.rcs.features.ConnectionManager.Status;

/**
 *  
 * @author JM. Auffret
 */
public class ImsModule implements SipEventListener, ConnectionManager.Listener {
	/**
	 * Singleton instance
	 */
	private static ImsModule instance = null;
	

    /**
     * IMS network interface
     */
    public ImsNetworkInterface currentNetworkInterface;
	
    /**
     * Core listener
     */
    private CoreListener listener;
    
    /**
     * Core status
     */
	private boolean started = false;

	/**
	 * Address book manager
	 */
	private AddressBookManager addressBookManager;

   
    /**
     * Returns the singleton instance
     * 
     * @return Core instance
     */
    public static ImsModule getInstance() {
    	return instance;
    }
    
    /**
     * Instanciate the core
     * 
	 * @param listener Listener
     * @return Core instance
     * @throws CoreException
     */
    public synchronized static ImsModule createCore(CoreListener listener) throws CoreException {
    	if (instance == null) {
    		instance = new ImsModule(listener);
    	}
    	return instance;
    }
    
    /**
     * Terminate the core
     */
    public synchronized static void terminateCore() {
    	if (instance != null) {
    		instance.stopCore();
    	}
   		instance = null;
    }

    /**
     * Constructor
     * 
	 * @param listener Listener
     * @throws CoreException
     */
    private ImsModule(CoreListener listener) throws CoreException {
		if (logger.isActivated()) {
        	logger.info("Terminal core initialization");
    	}

		// Set core event listener
		this.listener = listener;
       
        // Get UUID
		if (logger.isActivated()) {
			logger.info("My device UUID is " + DeviceUtils.getDeviceUUID(AndroidFactory.getApplicationContext()));
		}

        // Initialize the phone utils
    	PhoneUtils.initialize(AndroidFactory.getApplicationContext());

        // Create the address book manager
        addressBookManager = new AddressBookManager();
        
        // Create the IMS module
        create();
        
        if (logger.isActivated()) {
    		logger.info("Terminal core is created with success");
    	}
    }
    
    private void create() throws CoreException {
    	
    	if (logger.isActivated()) {
    		logger.info("IMS module initialization");
    	}
    	
		RcsSettings rcsSettings = RcsSettings.getInstance();
    	
    	// Get capability extensions
    	ServiceExtensionManager.getInstance().updateSupportedExtensions(AndroidFactory.getApplicationContext());
   	
    	
        // Create the service dispatcher
        serviceDispatcher = new ImsServiceDispatcher();
		// Create the IMS connection manager
        // Instantiates the IMS network interfaces
//        networkInterfaces[0] = ImsNetworkInterface.mobile(serviceDispatcher, rcsSettings, listener);
//        networkInterfaces[1] = ImsNetworkInterface.wifi(serviceDispatcher, rcsSettings, listener);

        // Set the mobile network interface by default
		currentNetworkInterface = 
				new ImsNetworkInterface(serviceDispatcher, listener);

        // Set general parameters
		SipManager.TIMEOUT = RcsSettings.getInstance().getSipTransactionTimeout();

		MsrpConnection.MSRP_TRACE_ENABLED = RcsSettings.getInstance().isMediaTraceActivated();
		HttpTransferManager.HTTP_TRACE_ENABLED = RcsSettings.getInstance().isMediaTraceActivated();


		ContactsManager contactsManager = ContactsManager.getInstance();

		MessagingLog messagingLog = MessagingLog.getInstance();

		// Instanciates the IMS services
        services = new ImsService[7];
        
        // Create terms & conditions service
        services[ImsService.TERMS_SERVICE] = new TermsConditionsService(this,rcsSettings);

        // Create capability discovery service
        services[ImsService.CAPABILITY_SERVICE] = new CapabilityService(this, rcsSettings, contactsManager);
        
        // Create IM service (mandatory)
        services[ImsService.IM_SERVICE] = new InstantMessagingService(this, rcsSettings, contactsManager, messagingLog);
        
        // Create IP call service (optional)
        services[ImsService.IPCALL_SERVICE] = new IPCallService(this, rcsSettings);
        
        // Create richcall service (optional)
        services[ImsService.RICHCALL_SERVICE] = new RichcallService(this);

        // Create presence service (optional)
        services[ImsService.PRESENCE_SERVICE] = new PresenceService(this, rcsSettings, contactsManager);

        // Create generic SIP service
        services[ImsService.SIP_SERVICE] = new SipService(this);


        for (ImsServiceDispatcher.Service o : services) {
        	serviceDispatcher.addService(o);
        }

        // Create the call manager
    	callManager = new CallManager(this);

    	if (logger.isActivated()) {
    		logger.info("IMS module has been created");
    	}
    }

	/**
	 * Returns the event listener
	 * 
	 * @return Listener
	 */
	public CoreListener getListener() {
		return listener;
	}

	/**
	 * Returns the address book manager
	 */
	public AddressBookManager getAddressBookManager(){
		return addressBookManager;
	}
	
	/**
     * Is core started
     * 
     * @return Boolean
     */
    public boolean isCoreStarted() {
    	return started;
    }

    /**
     * Start the terminal core
     * 
     * @throws CoreException
     */
    public synchronized void startCore() throws CoreException {
    	if (isReady) {
    		// Already started
    		return;
    	}

    	// Start the IMS module 
    	if (logger.isActivated()) {
			logger.info("Start the IMS module");
		}
		
		// Start the service dispatcher
		serviceDispatcher.start();
		
		// Start call monitoring
		callManager.startCallMonitoring();
		
		if (logger.isActivated()) {
			logger.info("IMS module is started");
		}

    	// Start the address book monitoring
    	addressBookManager.startAddressBookMonitoring();
    	
    	// Notify event listener
		listener.handleCoreLayerStarted();
		
    	if (logger.isActivated()) {
    		logger.info("RCS core service has been started with success");
    	}
    	
    	isReady = true;
    }
    	
    /**
     * Stop the terminal core
     */
    public synchronized void stopCore() {
    	if (!isReady) {
    		// Already stopped
    		return;
    	}    	
    	
    	if (logger.isActivated()) {
    		logger.info("Stop the RCS core service");
    	}
    	
    	// Stop the address book monitoring
    	addressBookManager.stopAddressBookMonitoring();

    	try {
	    	// Stop the IMS module 
	    	if (logger.isActivated()) {
				logger.info("Stop the IMS module");
			}
			
			// Stop call monitoring
			callManager.stopCallMonitoring();
			
			
			// Terminate the service dispatcher
			serviceDispatcher.terminate();
			
			if (logger.isActivated()) {
				logger.info("IMS module has been stopped");
			}	    	
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Error during core shutdown", e);
    		}
    	}
    	
    	// Notify event listener
		listener.handleCoreLayerStopped();

    	isReady = false;
    	if (logger.isActivated()) {
    		logger.info("RCS core service has been stopped with success");
    	}
    }

    /**
     * IMS services
     */
    private ImsService services[];

    /**
     * Service dispatcher
     */
    private ImsServiceDispatcher serviceDispatcher;    
    
    /**
	 * Call manager
	 */
	private CallManager callManager;    
    
    /**
     * flag to indicate whether instantiation is finished
     */
    private boolean isReady = false;

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());


    
    /**
	 * Returns the SIP manager
	 * 
	 * @return SIP manager
	 */
	public SipManager getSipManager() {
		return currentNetworkInterface.getSipManager();
	}
         
	public String getIpAddress() {
		return currentNetworkInterface.getIpAddress();
	}
	
    public boolean isBehindNat() {
		return currentNetworkInterface.isBehindNat();
	}	 
    
    public boolean isRegistered() {
		return currentNetworkInterface.isRegistered();
	}
	
	/**
     * Is connected to a Wi-Fi access
     * 
     * @return Boolean
     */
	public boolean isConnectedToWifiAccess() {
		return currentNetworkInterface.getType() == ConnectivityManager.TYPE_WIFI;
	}
	
	/**
     * Is connected to a mobile access
     * 
     * @return Boolean
     */
	public boolean isConnectedToMobileAccess() {
		return currentNetworkInterface.getType() == ConnectivityManager.TYPE_MOBILE;
	}

	/**
     * Start IMS services
     */
    public void startImsServices() {
    	// Start each services
		for(int i=0; i < services.length; i++) {
			if (services[i].isActivated()) {
				if (logger.isActivated()) {
					logger.info("Start IMS service: " + services[i].getClass().getName());
				}
				services[i].start();
			}
		}
		
		// Send call manager event
		getCallManager().connectionEvent(true);
		started = true;
		
    }
    
    /**
     * Stop IMS services
     */
    public void stopImsServices() {
    	// Abort all pending sessions
    	abortAllSessions();
    	
    	// Stop each services
    	for(int i=0; i < services.length; i++) {
    		if (services[i].isActivated()) {
				if (logger.isActivated()) {
					logger.info("Stop IMS service: " + services[i].getClass().getName());
				}
    			services[i].stop();
    		}
    	}
    	
		// Send call manager event
		getCallManager().connectionEvent(false);
		started = false;
    }

    /**
     * Check IMS services
     */
    public void checkImsServices() {
    	for(int i=0; i < services.length; i++) {
    		if (services[i].isActivated()) {
				if (logger.isActivated()) {
					logger.info("Check IMS service: " + services[i].getClass().getName());
				}
    			services[i].check();
    		}
    	}
    }

	/**
	 * Returns the call manager
	 * 
	 * @return Call manager
	 */
	public CallManager getCallManager() {
		return callManager;
	}

    /**
     * Returns the terms & conditions service
     * 
     * @return Terms & conditions service
     */
    public TermsConditionsService getTermsConditionsService() {
    	return (TermsConditionsService)services[ImsService.TERMS_SERVICE];
    }

    /**
     * Returns the capability service
     * 
     * @return Capability service
     */
    public CapabilityService getCapabilityService() {
    	return (CapabilityService)services[ImsService.CAPABILITY_SERVICE];
    }
    
    /**
     * Returns the IP call service
     * 
     * @return IP call service
     */
    public IPCallService getIPCallService() {
    	return (IPCallService)services[ImsService.IPCALL_SERVICE];
    }
    
    /**
     * Returns the rich call service
     * 
     * @return Richcall service
     */
    public RichcallService getRichcallService() {
    	return (RichcallService)services[ImsService.RICHCALL_SERVICE];
    }

    /**
     * Returns the presence service
     * 
     * @return Presence service
     */
    public PresenceService getPresenceService() {
    	return (PresenceService)services[ImsService.PRESENCE_SERVICE];
    }
    
    /**
     * Returns the Instant Messaging service
     * 
     * @return Instant Messaging service
     */
    public InstantMessagingService getInstantMessagingService() {
    	return (InstantMessagingService)services[ImsService.IM_SERVICE];
    }
    
    /**
     * Returns the SIP service
     * 
     * @return SIP service
     */
    public SipService getSipService() {
    	return (SipService)services[ImsService.SIP_SERVICE];
    }

    /**
	 * Receive SIP request
	 * 
	 * @param request SIP request
	 */
	public void receiveSipRequest(SipRequest request) {
        // Post the incoming request to the service dispatcher
    	serviceDispatcher.receiveSipRequest(request);
	}
	
	/**
	 * Abort all sessions
	 */
	public void abortAllSessions() {
		if (logger.isActivated()) {
			logger.debug("Abort all pending sessions");
		}
		for (ImsService service : services) {
			service.abortAllSessions(ImsServiceSession.TERMINATION_BY_SYSTEM);
		}
	}
	
    /**
     * Check whether ImsModule instantiation has finished
     *
     * @return true if ImsModule is completely initialized
     */
    public boolean isReady(){
        return isReady;
    }
    
	/**
	 * @return true is device is in roaming
	 */
	public boolean isInRoaming() {
		//return connectionManager.isInRoaming();
		//TODO: move to configure.
		return false;
	}

	public UserProfile getUser() {		
		return currentNetworkInterface.getUserProfile();
	}

	/**
	 * Search the IMS session that corresponds to a given call-ID
	 *  
	 * @param callId Call-ID
	 * @return IMS session
	 */
	ImsServiceSession getImsServiceSession(String callId) {
		for (ImsService service : services) {
			ImsServiceSession session = service.getImsServiceSession(callId);
			if (session != null) {
				return session;
			}
		}
		return null;
	}
	
	public void updateRemoteParty(String number) {
		getCallManager().setRemoteParty(number);
	}

	public boolean isRegisteredAt(String host, int port) {
		return currentNetworkInterface.isRegisteredAt(host, port);
	}

	/**
	 * Request capabilities to a given contact
	 * 
	 * @param contact Contact identifier
	 */
	void requestCapabilities(ContactId contact) {
	    if (getCapabilityService().isServiceStarted()) {
			getCapabilityService().requestContactCapabilities(contact);
		 }
	}

	void resetCapabilities(ContactId contact) {
		getCapabilityService().resetContactCapabilitiesForContentSharing(contact);
	}

	public boolean isCallConnectedWith(ContactId contact) {
		boolean csCall = callManager.isCallConnectedWith(contact);
		boolean ipCall = getIPCallService().isCallConnectedWith(contact);
		return (csCall || ipCall);
	}

	public HttpDigestMd5Authentication getRegisterDigest() {
		return currentNetworkInterface.getRegisterDigest();
	}

	public SessionAuthenticationAgent createAuthenticator() {
		return new SessionAuthenticationAgent(this);
	}
	

	void handleStopService() {
		if (isCoreStarted()) {
			stopImsServices();
			currentNetworkInterface.disconnect(currentNetworkInterface.getType());
		}
	}

	void checkImsService() {
		if (isReady()) {
		    if (isCoreStarted()) {		       		    
		        if (logger.isActivated()) {
		            logger.debug("Already registered to IMS: check IMS services");
		        }
		        checkImsServices();
		    } else { 
		        if (logger.isActivated()) {
		            logger.debug("Already registered to IMS: start IMS services");
		        }
		        startImsServices();
		    }
		} else {
		    if (logger.isActivated()) {
		        logger.debug("Already registered to IMS: IMS services not yet started");
		    }
		}
	}

	void handleRegistered() {
		if (isReady()) {
		    startImsServices();
		}
		
		RtpSource.CNAME = getUser().getPublicUri();
	}

	@Override
	public void onStatusChange(Status status) {
		
		switch (status) {
		case Deinit:
			break;
		case Failure:
			handleStopService();
			break;
		case InProgress:
			checkImsService();
			break;
		case Success:
			handleRegistered();
			break;
		default:
			break;			
		}
		
	}

	@Override
	public void onConfigurationChange() {
		getListener().handleSimHasChanged();
	}

	public void connect(int network) {
		currentNetworkInterface.connect(network);
		

	}
	
	public void disconnect(int network) {
		currentNetworkInterface.disconnect(network);
	}
}
