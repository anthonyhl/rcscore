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

package com.orangelabs.rcs.core.sip;

import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

import javax2.sip.message.Response;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.SessionAuthenticationAgent;
import com.orangelabs.rcs.core.SipIntentManager;
import com.orangelabs.rcs.core.sip.messaging.GenericSipMsrpSession;
import com.orangelabs.rcs.core.sip.messaging.OriginatingSipMsrpSession;
import com.orangelabs.rcs.core.sip.messaging.TerminatingSipMsrpSession;
import com.orangelabs.rcs.core.sip.streaming.GenericSipRtpSession;
import com.orangelabs.rcs.core.sip.streaming.OriginatingSipRtpSession;
import com.orangelabs.rcs.core.sip.streaming.TerminatingSipRtpSession;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * SIP service
 * 
 * @author Jean-Marc AUFFRET
 */
public class SipService extends ImsService {
	/**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(SipService.class.getSimpleName());

	/**
	 * MIME-type for multimedia services
	 */
	public final static String MIME_TYPE = "application/*";

	/**
	 * GenericSipMsrpSessionCache with SessionId as key
	 */
	private Map<String, GenericSipMsrpSession> mGenericSipMsrpSessionCache = new HashMap<String, GenericSipMsrpSession>();

	/**
	 * GenericSipRtpSessionCache with SessionId as key
	 */
	private Map<String, GenericSipRtpSession> mGenericSipRtpSessionCache = new HashMap<String, GenericSipRtpSession>();

	
	/**
	 * SIP intent manager
	 */
	private SipIntentManager intentMgr = new SipIntentManager(); 
	/**
     * Constructor
     * 
     * @param parent IMS module
     * @throws CoreException
     */
	public SipService(ImsModule parent) throws CoreException {
        super(parent, true);
	}

    /**
     * /** Start the IMS service
     */
	public synchronized void start() {
		if (isServiceStarted()) {
			// Already started
			return;
		}
		setServiceStarted(true);
	}

    /**
     * Stop the IMS service
     */
	public synchronized void stop() {
		if (!isServiceStarted()) {
			// Already stopped
			return;
		}
		setServiceStarted(false);
	}

	/**
     * Check the IMS service
     */
	public void check() {
	}

    /**
     * Initiate a MSRP session
     * 
     * @param contact Remote contact Id
     * @param featureTag Feature tag of the service
     * @return SIP session
     */
	public GenericSipMsrpSession initiateMsrpSession(ContactId contact, String featureTag) {
		if (logger.isActivated()) {
			logger.info("Initiate a MSRP session with contact " + contact);
		}
		
		// Create a new session
		OriginatingSipMsrpSession session = new OriginatingSipMsrpSession(this, contact, featureTag);
		
		return session;
	}
	
	@Override
	protected boolean handleInvite(SipRequest request) {
		
		Intent intent = intentMgr.isSipRequestResolved(request);
		if (intent != null) {
			String sdp = request.getSdpContent();
			// Generic SIP session
    		if (isTagPresent(sdp, "msrp")) {
	    		if (logger.isActivated()) {
	    			logger.debug("Generic SIP session invitation with MSRP media");
	    		}
	    		try {
	    			receiveMsrpSessionInvitation(intent, request);
	    		} catch (RcsContactFormatException e) {
	    			if (logger.isActivated()) {
		    			logger.warn("Cannot parse contact");
		    		}
	    		}
    		} else
    		if (isTagPresent(sdp, "rtp")) {
	    		if (logger.isActivated()) {
	    			logger.debug("Generic SIP session invitation with RTP media");
	    		}
	    		try  {
	    			receiveRtpSessionInvitation(intent, request);
	    		} catch (RcsContactFormatException e) {
	    			if (logger.isActivated()) {
		    			logger.warn("Cannot parse contact");
		    		}
	    		}
    		} else {
	    		if (logger.isActivated()) {
	    			logger.debug("Media not supported for a generic SIP session");
	    		}
				sendFinalResponse(request, Response.SESSION_NOT_ACCEPTABLE);
    		}
    		return true;
    	} 
		return false;
	}

    /**
     * Receive a session invitation with MSRP media
     * 
     * @param sessionInvite Resolved intent
     * @param invite Initial invite
     * @throws RcsContactFormatException
     */
	public void receiveMsrpSessionInvitation(Intent sessionInvite, SipRequest invite) throws RcsContactFormatException {

		// Create a new session
		TerminatingSipMsrpSession session = new TerminatingSipMsrpSession(this, invite, sessionInvite);

		getImsModule().getListener().handleSipMsrpSessionInvitation(sessionInvite, session);

		session.startSession();
	}

    /**
     * Initiate a RTP session
     * 
     * @param contact Remote contact
     * @param featureTag Feature tag of the service
     * @return SIP session
     */
	public GenericSipRtpSession initiateRtpSession(ContactId contact, String featureTag) {
		if (logger.isActivated()) {
			logger.info("Initiate a RTP session with contact " + contact);
		}
		
		// Create a new session
		OriginatingSipRtpSession session = new OriginatingSipRtpSession(this, contact, featureTag);
		
		return session;
	}

	/**
     * Receive a session invitation with RTP media
     * 
     * @param sessionInvite Resolved intent
     * @param invite Initial invite
     * @throws RcsContactFormatException
     */
	public void receiveRtpSessionInvitation(Intent sessionInvite, SipRequest invite) throws RcsContactFormatException {
		// Create a new session
		TerminatingSipRtpSession session = new TerminatingSipRtpSession(this, invite, sessionInvite);

		getImsModule().getListener().handleSipRtpSessionInvitation(sessionInvite, session);

		session.startSession();
	}

	public void addSession(GenericSipMsrpSession session) {
		final String sessionId = session.getSessionID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add GenericSipMsrpSession with sessionId '")
					.append(sessionId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mGenericSipMsrpSessionCache.put(sessionId, session);
			addImsServiceSession(session);
		}
	}

	public void removeSession(final GenericSipMsrpSession session) {
		final String sessionId = session.getSessionID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove GenericSipMsrpSession with sessionId '")
					.append(sessionId).append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads accessing that session can finish up before it is actually
		 * removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					mGenericSipMsrpSessionCache.remove(sessionId);
					removeImsServiceSession(session);
				}
			}
		}.start();
	}

	public GenericSipMsrpSession getGenericSipMsrpSession(String sessionId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get GenericSipMsrpSession with sessionId '")
					.append(sessionId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mGenericSipMsrpSessionCache.get(sessionId);
		}
	}

	public void addSession(GenericSipRtpSession session) {
		final String sessionId = session.getSessionID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add GenericSipRtpSession with sessionId '")
					.append(sessionId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mGenericSipRtpSessionCache.put(sessionId, session);
			addImsServiceSession(session);
		}
	}

	public void removeSession(final GenericSipRtpSession session) {
		final String sessionId = session.getSessionID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove GenericSipRtpSession with sessionId '")
					.append(sessionId).append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads accessing that session can finish up before it is actually
		 * removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					mGenericSipRtpSessionCache.remove(sessionId);
					removeImsServiceSession(session);
				}
			}
		}.start();
	}

	public GenericSipRtpSession getGenericSipRtpSession(String sessionId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get GenericSipRtpSession with sessionId '")
					.append(sessionId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mGenericSipRtpSessionCache.get(sessionId);
		}
	}

	/**
	 * Send an instant message (SIP MESSAGE)
	 * 
     * @param contact Contact
	 * @param featureTag Feature tag of the service
     * @param content Content
	 * @return True if successful else returns false
	 */
	public boolean sendInstantMessage(String contact, String featureTag, byte[] content) {
		boolean result = false;
		try {
			if (logger.isActivated()) {
       			logger.debug("Send instant message to " + contact);
       		}
			
		    // Create authentication agent 
       		SessionAuthenticationAgent authenticationAgent = new SessionAuthenticationAgent(getImsModule());
       		
        	String contactUri = PhoneUtils.formatNumberToSipUri(contact, getUser().getHomeDomain());
       		SipDialogPath dialogPath = getSipManager().createServiceDialogPath(contactUri);
       		      	        	
	        // Create MESSAGE request
        	if (logger.isActivated()) {
        		logger.info("Send first MESSAGE");
        	}
	        SipRequest msg = dialogPath.createMessage(featureTag, SipService.MIME_TYPE, content);
	        
	        // Send MESSAGE request
	        SipTransactionContext ctx = getSipManager().sendSipMessageAndWait(msg);
	
	        // Analyze received message
            if (ctx.getStatusCode() == 407) {
                // 407 response received
            	if (logger.isActivated()) {
            		logger.info("407 response received");
            	}

    	        // Set the Proxy-Authorization header
            	authenticationAgent.readProxyAuthenticateHeader(ctx.getSipResponse());

                // Increment the Cseq number of the dialog path
                dialogPath.incrementCseq();

                // Create a second MESSAGE request with the right token
                if (logger.isActivated()) {
                	logger.info("Send second MESSAGE");
                }
    	        msg = dialogPath.createMessage(featureTag, SipService.MIME_TYPE, content);

    	        // Set the Authorization header
    	        authenticationAgent.setProxyAuthorizationHeader(msg);
                
                // Send MESSAGE request
    	        ctx = getImsModule().getSipManager().sendSipMessageAndWait(msg);

                // Analyze received message
                if ((ctx.getStatusCode() == 200) || (ctx.getStatusCode() == 202)) {
                    // 200 OK response
                	if (logger.isActivated()) {
                		logger.info("20x OK response received");
                	}
                	result = true;
                } else {
                    // Error
                	if (logger.isActivated()) {
                		logger.info("Send instant message has failed: " + ctx.getStatusCode()
    	                    + " response received");
                	}
                }
            } else
            if ((ctx.getStatusCode() == 200) || (ctx.getStatusCode() == 202)) {
	            // 200 OK received
            	if (logger.isActivated()) {
            		logger.info("20x OK response received");
            	}
            	result = true;
	        } else {
	            // Error responses
            	if (logger.isActivated()) {
            		logger.info("Send instant message has failed: " + ctx.getStatusCode()
	                    + " response received");
            	}
	        }
        } catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Can't send MESSAGE request", e);
        	}
        }
        return result;
	}
}
