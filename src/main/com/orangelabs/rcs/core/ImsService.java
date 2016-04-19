/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.orangelabs.rcs.core;

import java.util.HashMap;
import java.util.Map;

import javax2.sip.address.SipURI;
import javax2.sip.header.ContactHeader;
import javax2.sip.message.Request;
import javax2.sip.message.Response;

import com.orangelabs.rcs.core.network.SipManager;
import com.orangelabs.rcs.protocol.msrp.MsrpManager;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.NetworkRessourceManager;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Abstract IMS service
 * 
 * @author jexa7410
 */
public abstract class ImsService implements ImsServiceDispatcher.Service {
    /**
     * Terms & conditions service
     */
	public static final int TERMS_SERVICE = 0;

	/**
     * Capability service
     */
	public static final int CAPABILITY_SERVICE = 1;

    /**
     * Instant Messaging service
     */
	public static final int IM_SERVICE = 2;
	
	/**
     * IP call service
     */
	public static final int IPCALL_SERVICE = 3;

    /**
     * Richcall service
     */
	public static final int RICHCALL_SERVICE = 4;

    /**
     * Presence service
     */
	public static final int PRESENCE_SERVICE = 5;

    /**
     * SIP service
     */
	public static final int SIP_SERVICE = 6;
	
	/**
	 * Activation flag
	 */
	private boolean activated = true;

	/**
	 * Service state
	 */
	private boolean started = false;

	/**
	 * IMS module
	 */
	private ImsModule imsModule;

	/**
	 * ImsServiceSessionCache with session dialog path's CallId as key
	 */
	private Map<String, ImsServiceSession> mImsServiceSessionCache = new HashMap<String, ImsServiceSession>();

	/**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(ImsService.class.getSimpleName());

	protected final static class SharingDirection {

		public static final int UNIDIRECTIONAL = 1;

		public static final int BIDIRECTIONAL = 2;
	}

    /**
     * Constructor
     * 
     * @param parent IMS module
     * @param activated Activation flag
     * @throws CoreException
     */
	public ImsService(ImsModule parent, boolean activated) throws CoreException {
		this.imsModule = parent;
		this.activated = activated;
	}

    /**
     * Is service activated
     * 
     * @return Boolean
     */
	public boolean isActivated() {
		return activated;
	}

    /**
     * Change the activation flag of the service
     * 
     * @param activated Activation flag
     */
	public void setActivated(boolean activated) {
		this.activated = activated;
	}

    /**
     * Returns the IMS module
     * 
     * @return IMS module
     */
	public ImsModule getImsModule() {
		return imsModule;
	}

	
	public UserProfile getUser() {
		return imsModule.getUser();
	}

	/*
	 * This method is by choice not synchronized here since the class
	 * extending this base-class will need to handle the synchronization
	 * over a larger scope when calling this method anyway and we would like
	 * to avoid double locks.
	 */
	protected void addImsServiceSession(ImsServiceSession session){
		mImsServiceSessionCache.put(session.getDialogPath().getCallId(), session);
	}

	/*
	 * This method is by choice not synchronized here since the class
	 * extending this base-class will need to handle the synchronization
	 * over a larger scope when calling this method anyway and we would like
	 * to avoid double locks.
	 */
	protected void removeImsServiceSession(ImsServiceSession session){
		mImsServiceSessionCache.remove(session.getDialogPath().getCallId());
	}

	public ImsServiceSession getImsServiceSession(String callId) {
		synchronized (getImsServiceSessionOperationLock()) {
			return mImsServiceSessionCache.get(callId);
		}
	}

	protected Object getImsServiceSessionOperationLock() {
		return mImsServiceSessionCache;
	}

    /**
     * Is service started
     * 
     * @return Boolean
     */
	public boolean isServiceStarted() {
		return started;
	}

    /**
     * Set service state
     * 
     * @param state State
     */
	public void setServiceStarted(boolean state) {
		started = state;
	}

	/**
     * Start the IMS service
     */
	public abstract void start();

    /**
     * Stop the IMS service
     */
	public abstract void stop();

	/**
     * Check the IMS service
     */
	public abstract void check();

	public void abortAllSessions(int imsAbortionReason) {
		synchronized (getImsServiceSessionOperationLock()) {
			for (ImsServiceSession session : mImsServiceSessionCache.values()) {
				session.abortSession(imsAbortionReason);
			}
		}
	}

    /**
     * Send an error response to an invitation before to create a service session
     *
     * @param invite Invite request
	 * @param error Error code
     */
    public void sendErrorResponse(SipRequest invite, int error) {
        try {
            if (logger.isActivated()) {
                logger.info("Send error " + error);
            }
            SipResponse resp = SipUtils.createResponse(invite, IdGenerator.getIdentifier(), error);

            // Send response
            getSipManager().sendSipResponse(resp);
        } catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't send error " + error, e);
            }
        }
    }

	public MsrpManager createMsrpManager() {		
		int localMsrpPort = NetworkRessourceManager.generateLocalMsrpPort();
		String localIpAddress = getImsModule().getIpAddress();
		MsrpManager manager = new MsrpManager(localIpAddress, localMsrpPort,this);
		if (getImsModule().isConnectedToWifiAccess()) {
			manager.setSecured(RcsSettings.getInstance().isSecureMsrpOverWifi());
		}
		return manager;
	}

	public SipManager getSipManager() {
		return getImsModule().getSipManager();
	}
	

	public boolean isRegisteredAt(String host, int port) {
		return imsModule.isRegisteredAt(host, port);
	}

	private boolean isMyInstanceId(SipRequest request) {
		return getSipManager().isMyInstanceId(request) 
				&& getSipManager().isMyGruu(request);
	}

	
    /**
     * Send a 100 Trying response to the remote party
     * 
     * @param request SIP request
     */
    private void send100Trying(SipRequest request) {
    	try {
	    	// Send a 100 Trying response
	    	SipResponse trying = SipUtils.createResponse(request, null, 100);
	    	getSipManager().sendSipResponse(trying);
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't send a 100 Trying response");
    		}
    	}
    }
    
    /**
     * Send a final response
     * 
     * @param request SIP request
     * @param code Response code
     */
    public void sendFinalResponse(SipRequest request, int code) {
    	try {
	    	SipResponse resp = SipUtils.createResponse(request, IdGenerator.getIdentifier(), code);
	    	getSipManager().sendSipResponse(resp);
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't send a " + code + " response");
    		}
    	}
    }
    
    /**
     * Send a final response
     * 
     * @param request SIP request
     * @param code Response code
     * @param warning Warning message
     */
    public void sendFinalResponse(SipRequest request, int code, String warning) {
    	try {
	    	SipResponse resp = request.createResponse(IdGenerator.getIdentifier(), code, warning);
	    	getSipManager().sendSipResponse(resp);
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't send a " + code + " response");
    		}
    	}
    }    
	
	public boolean handleRequest(SipRequest request) {
		if (logger.isActivated()) {
			logger.debug("Receive " + request.getMethod() + " request");
		}
		
		// Check the IP address of the request-URI	
		SipURI requestURI = request.getRequestURI();
		
		if (requestURI == null) {
			if (logger.isActivated()) {
				logger.error("Unable to parse request URI " + request.getRequestURIString());
			}
			sendFinalResponse(request, 400);			
			return true;
		}
		
		String host = requestURI.getHost();
		int port = requestURI.getPort();

		boolean isMatchingRegistered = isRegisteredAt(host, port);

		if (!isMatchingRegistered) {		
			// Send a 404 error
			if (logger.isActivated()) {
				logger.debug("Request-URI address and port do not match with registered contact: reject the request");
			}
			sendFinalResponse(request, 404);
			return true;
		}

        // Check SIP instance ID: RCS client supporting the multidevice procedure shall respond to the
        // invite with a 486 BUSY HERE if the identifier value of the "+sip.instance" and 
		// "pub-gruu" tag included in the Accept-Contact header of 
		// that incoming SIP request does not match theirs
        if (isMyInstanceId(request)) {
            // Send 486 Busy Here
			if (logger.isActivated()) {
				logger.debug("SIP instance ID or public-gruu doesn't match: reject the request");
			}
            sendFinalResponse(request, 486);
            return true;
        }
        
		ImsServiceSession session = getImsServiceSession(request.getCallId());
		if (session != null) {
			ContactHeader contactHeader = (ContactHeader)request.getHeader(ContactHeader.NAME);
			if (contactHeader != null) {
				String remoteInstanceId = contactHeader.getParameter(SipUtils.SIP_INSTANCE_PARAM);
				session.getDialogPath().setRemoteSipInstance(remoteInstanceId);
			}
			
			if (request.getMethod().equals(Request.INVITE)) {
				session.receiveReInvite(request);
				return true;
			}
		}
		
		switch (request.getMethod()) {
		case Request.OPTIONS:
			return handleOptions(request);
		case Request.INVITE:
			return handleInvite(request);
		case Request.CANCEL:
			return handleCancel(request);
		case Request.BYE:
			return handleBye(request);
		case Request.ACK:
			return handleAck(request);
		case Request.MESSAGE:
			return handleMessage(request);
		case Request.NOTIFY:
			return handleNotify(request);
		default:
			// Unknown request: : reject the request with a 403 Forbidden
			if (logger.isActivated()) {
				logger.debug("Unknown request " + request.getMethod());
			}
			sendFinalResponse(request, Response.FORBIDDEN);
			return true;
		}
	}


	protected boolean handleUpdate(SipRequest request) {
		ImsServiceSession session = getImsServiceSession(request.getCallId());
    	if (session != null) {
    		session.receiveUpdate(request);
    		return true;
    	}
		return false;
	}

	protected boolean handleNotify(SipRequest request) {
	    try {
	    	// Create 200 OK response
	        SipResponse resp = request.createResponse(200);

	        // Send 200 OK response
	        getSipManager().sendSipResponse(resp);
	    } catch(SipException e) {
        	if (logger.isActivated()) {
        		logger.error("Can't send 200 OK for NOTIFY", e);
        	}
	    }
   
		return false;
	}

	protected boolean handleMessage(SipRequest request) {
		// TODO Auto-generated method stub
		return false;
	}

	protected boolean handleAck(SipRequest request) {
		// TODO Auto-generated method stub
		return false;
	}

	protected boolean handleBye(SipRequest request) {
		
		ImsServiceSession session = getImsServiceSession(request.getCallId());
		// Route request to session
    	if (session != null) {
    		session.receiveBye(request);
    	}
    	
		// Send a 200 OK response
		try {
			if (logger.isActivated()) {
				logger.info("Send 200 OK");
			}
	        SipResponse response = request.createResponse(200);
			getSipManager().sendSipResponse(response);
		} catch(Exception e) {
	       	if (logger.isActivated()) {
	    		logger.error("Can't send 200 OK response", e);
	    	}
		}
		return true;
	}

	protected boolean handleCancel(SipRequest request) {
		
		ImsServiceSession session = getImsServiceSession(request.getCallId());
		// Route request to session
    	if (session != null) {
    		session.receiveCancel(request);
    	}
    	
		// Send a 200 OK
    	try {
	    	if (logger.isActivated()) {
	    		logger.info("Send 200 OK");
	    	}
	        SipResponse cancelResp = request.createResponse(200);
	        getSipManager().sendSipResponse(cancelResp);
		} catch(Exception e) {
	    	if (logger.isActivated()) {
	    		logger.error("Can't send 200 OK response", e);
	    	}
		}
		return true;
	}

	protected boolean handleInvite(SipRequest request) {
		// Send a 100 Trying response
		send100Trying(request);

		// Extract the SDP part
		String sdp = request.getSdpContent();
		if (sdp == null) {
			// No SDP found: reject the invitation with a 606 Not Acceptable
			if (logger.isActivated()) {
				logger.debug("No SDP found: automatically reject");
			}
			sendFinalResponse(request, Response.SESSION_NOT_ACCEPTABLE);
			return true;
		}
		sdp = sdp.toLowerCase();
		
		return false;
	}

	protected boolean handleOptions(SipRequest request) {
		return false;
	}
	
    /**
     * Test a tag is present or not in SIP message
     * 
     * @param message Message or message part
     * @param tag Tag to be searched
     * @return Boolean
     */
    public boolean isTagPresent(String message, String tag) {
    	if ((message != null) && (tag != null) && (message.toLowerCase().indexOf(tag) != -1)) {
    		return true;
    	} else {
    		return false;
    	}
    }
}
