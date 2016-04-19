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
package com.orangelabs.rcs.core.sip.streaming;

import java.util.Collection;

import android.content.Intent;

import com.gsma.services.rcs.RcsContactFormatException;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.ImsSessionListener;
import com.orangelabs.rcs.core.SessionTimerManager;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.core.sip.SipSessionError;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Terminating SIP RTP session
 * 
 * @author jexa7410
 */
public class TerminatingSipRtpSession extends GenericSipRtpSession {
	/**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(TerminatingSipRtpSession.class.getSimpleName());

    private final Intent mSessionInvite;

    /**
     * Constructor
     * 
	 * @param parent IMS service
	 * @param invite Initial INVITE request
	 * @throws RcsContactFormatException
	 */
	public TerminatingSipRtpSession(SipService parent, SipRequest invite, Intent sessionInvite) throws RcsContactFormatException {
		super(parent, ContactUtils.createContactId(invite.getAssertedIdentity()), invite.getFirstFeatureTag());

		mSessionInvite = sessionInvite;
		// Create dialog path
		createTerminatingDialogPath(invite);
	}
		
	/**
	 * Background processing
	 */
	public void run() {
		try {		
	    	if (logger.isActivated()) {
	    		logger.info("Initiate a new RTP session as terminating");
	    	}

	    	send180Ringing(getDialogPath().getInvite(), getDialogPath().getLocalTag());

            Collection<ImsSessionListener> listeners = getListeners();
            for (ImsSessionListener listener : listeners) {
                listener.handleSessionInvited();
            }

            int answer = waitInvitationAnswer();
            switch (answer) {
                case ImsServiceSession.INVITATION_REJECTED:
                    if (logger.isActivated()) {
                        logger.debug("Session has been rejected by user");
                    }

                    removeSession();

                    for (ImsSessionListener listener : listeners) {
                        listener.handleSessionRejectedByUser();
                    }
                    return;

                case ImsServiceSession.INVITATION_NOT_ANSWERED:
                    if (logger.isActivated()) {
                        logger.debug("Session has been rejected on timeout");
                    }

                    // Ringing period timeout
                    send486Busy(getDialogPath().getInvite(), getDialogPath().getLocalTag());

                    removeSession();

                    for (ImsSessionListener listener : listeners) {
                        listener.handleSessionRejectedByTimeout();
                    }
                    return;

                case ImsServiceSession.INVITATION_CANCELED:
                    if (logger.isActivated()) {
                        logger.debug("Session has been canceled");
                    }

                    removeSession();

                    for (ImsSessionListener listener : listeners) {
                        listener.handleSessionRejectedByRemote();
                    }
                    return;

                case ImsServiceSession.INVITATION_ACCEPTED:
                    setSessionAccepted();

                    for (ImsSessionListener listener : listeners) {
                        listener.handleSessionAccepted();
                    }
                    break;

                default:
                    if (logger.isActivated()) {
                        logger.debug("Unknown invitation answer in run; answer="
                                .concat(String.valueOf(answer)));
                    }
                    return;
            }
			
			// Build SDP part
	    	String sdp = generateSdp();

	    	// Set the local SDP part in the dialog path
	        getDialogPath().setLocalContent(sdp);

	        // Test if the session should be interrupted
            if (isInterrupted()) {
            	if (logger.isActivated()) {
            		logger.debug("Session has been interrupted: end of processing");
            	}
            	return;
            }

	        // Test if the session should be interrupted
            if (isInterrupted()) {
            	if (logger.isActivated()) {
            		logger.debug("Session has been interrupted: end of processing");
            	}
            	return;
            }
            
            // Prepare Media Session
            prepareMediaSession();            

            // Create a 200 OK response
			if (logger.isActivated()) {
				logger.info("Send 200 OK");
			}
			SipResponse resp = create200OKResponse();

            // The signalisation is established
            getDialogPath().sigEstablished();

	        // Send response
	        SipTransactionContext ctx = getSipManager().sendSipMessageAndWait(resp);

			// Analyze the received response 
			if (ctx.isSipAck()) {
				// ACK received
				if (logger.isActivated()) {
					logger.info("ACK request received");
				}
				
				// The session is established
				getDialogPath().sessionEstablished();

	            // Start Media Session
	            startMediaSession();
				
            	// Start session timer
            	if (getSessionTimerManager().isSessionTimerActivated(resp)) {        	
            		getSessionTimerManager().start(SessionTimerManager.UAS_ROLE, getDialogPath().getSessionExpireTime());
            	}

            	// Notify listeners
    	    	for(int j=0; j < getListeners().size(); j++) {
    	    		getListeners().get(j).handleSessionStarted();
    	    	}
			} else {
	    		if (logger.isActivated()) {
	        		logger.debug("No ACK received for INVITE");
	        	}

	    		// No response received: timeout
            	handleError(new SipSessionError(SipSessionError.SESSION_INITIATION_FAILED));
			}
		} catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Session initiation has failed", e);
        	}

        	// Unexpected error
			handleError(new SipSessionError(SipSessionError.UNEXPECTED_EXCEPTION,
					e.getMessage()));
		}
	}

	@Override
	public boolean isInitiatedByRemote() {
		return true;
	}

	public Intent getSessionInvite() {
		return mSessionInvite;
	}
}
