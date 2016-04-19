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

package com.orangelabs.rcs.core.capability;


import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ContactInfo.RcsStatus;
import com.orangelabs.rcs.core.ContactInfo.RegistrationState;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.SessionAuthenticationAgent;
import com.orangelabs.rcs.protocol.presence.PresenceError;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Anonymous fetch procedure which permits to request the capabilities
 * for a given contact thanks to a one shot subscribe.
 * 
 * @author Jean-Marc AUFFRET
 */
public class AnonymousFetchRequestTask {
    /**
     * IMS module
     */
    private ImsModule imsModule;
    
    /**
     * Remote contact
     */
    private ContactId mContact;
    
    /**
     * Dialog path
     */
    private SipDialogPath dialogPath = null;
    
    /**
	 * Authentication agent
	 */
	private SessionAuthenticationAgent authenticationAgent;

	/**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(AnonymousFetchRequestTask.class.getName());

    /**
     * Constructor
     * 
     * @param parent IMS module
     * @param contact Remote contact identifier
     */
    public AnonymousFetchRequestTask(ImsModule parent, ContactId contact) {
        imsModule = parent;
        mContact = contact;
		authenticationAgent = imsModule.createAuthenticator();
    }

	/**
	 * Start task
	 */
	public void start() {
		sendSubscribe();
	}
	
	/**
	 * Send a SUBSCRIBE request
	 */
	private void sendSubscribe() {
    	if (logger.isActivated()) {
    		logger.info("Send SUBSCRIBE request to " + mContact);
    	}

    	try {
	        // Create a dialog path
    		String contactUri = PhoneUtils.formatContactIdToUri(mContact, imsModule.getUser().getHomeDomain());
            // Set local party
        	String localParty = "sip:anonymous@" + imsModule.getUser().getHomeDomain();
    		
    		dialogPath = imsModule.getSipManager().createServiceDialogPath(contactUri, localParty);
            
            // Create a SUBSCRIBE request
        	SipRequest subscribe = dialogPath.createSubscribe();
        	
        	// Send SUBSCRIBE request
	        sendSubscribe(subscribe);
        } catch (Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Subscribe has failed", e);
        	}
        	handleError(new PresenceError(PresenceError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }        
    }

	/**
	 * Send SUBSCRIBE message
	 * 
	 * @param subscribe SIP SUBSCRIBE
	 * @throws Exception
	 */
	private void sendSubscribe(SipRequest subscribe) throws Exception {
        if (logger.isActivated()) {
        	logger.info("Send SUBSCRIBE, expire=" + subscribe.getExpires());
        }

        // Send SUBSCRIBE request
        SipTransactionContext ctx = imsModule.getSipManager().sendSipMessageAndWait(subscribe);

        // Analyze the received response 
        if (ctx.isSipResponse()) {
        	// A response has been received
            if ((ctx.getStatusCode() >= 200) && (ctx.getStatusCode() < 300)) {
            	// 200 OK
    			handle200OK(ctx);
            } else
            if (ctx.getStatusCode() == 407) {
            	// 407 Proxy Authentication Required
            	handle407Authentication(ctx);
            } else
            if (ctx.getStatusCode() == 404) {
            	// User not found
            	handleUserNotFound(ctx);
            } else {
            	// Other error response
    			handleError(new PresenceError(PresenceError.SUBSCRIBE_FAILED,
    					ctx.getStatusCode() + " " + ctx.getReasonPhrase()));    					
            }
        } else {
    		if (logger.isActivated()) {
        		logger.debug("No response received for SUBSCRIBE");
        	}

    		// No response received: timeout
        	handleError(new PresenceError(PresenceError.SUBSCRIBE_FAILED));
        }
	}    

	/**
	 * Handle 200 0K response 
	 * 
	 * @param ctx SIP transaction context
	 */
	private void handle200OK(SipTransactionContext ctx) {
        // 200 OK response received
        if (logger.isActivated()) {
            logger.info("200 OK response received");
        }
	}
	
    /**
	 * Handle 407 response 
	 * 
	 * @param ctx SIP transaction context
	 * @throws Exception
	 */
	private void handle407Authentication(SipTransactionContext ctx) throws Exception {
        // 407 response received
    	if (logger.isActivated()) {
    		logger.info("407 response received");
    	}

    	SipResponse resp = ctx.getSipResponse();

    	// Set the Proxy-Authorization header
    	authenticationAgent.readProxyAuthenticateHeader(resp);

        // Increment the Cseq number of the dialog path
        dialogPath.incrementCseq();

        // Create a second SUBSCRIBE request with the right token
        if (logger.isActivated()) {
        	logger.info("Send second SUBSCRIBE");
        }
    	SipRequest subscribe = dialogPath.createSubscribe();
    	
        // Set the Authorization header
        authenticationAgent.setProxyAuthorizationHeader(subscribe);
    	
        // Send SUBSCRIBE request
    	sendSubscribe(subscribe);
	}	
	
	/**
	 * Handle error response 
	 * 
	 * @param error Error
	 */
	private void handleError(PresenceError error) {
        // On error don't modify the existing capabilities
    	if (logger.isActivated()) {
    		logger.info("Subscribe has failed: " + error.getErrorCode() + ", reason=" + error.getMessage());
    	}

    	// We update the database capabilities time of last request
    	ContactsManager.getInstance().updateCapabilitiesTimeLastRequest(mContact);
	}

	/**
	 * Handle user not found 
	 * 
	 * @param ctx SIP transaction context
	 */
	private void handleUserNotFound(SipTransactionContext ctx) {
        if (logger.isActivated()) {
            logger.info("User not found (" + ctx.getStatusCode() + " error)");
        }
        
		// We update the database with empty capabilities
    	Capabilities capabilities = new Capabilities();
    	ContactsManager.getInstance().setContactCapabilities(mContact, capabilities, RcsStatus.NOT_RCS, RegistrationState.UNKNOWN);
	}
}
