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


import gov2.nist.javax2.sip.header.ims.PPreferredServiceHeader;

import javax2.sip.header.ExtensionHeader;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsServiceError;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.capability.CapabilityUtils;
import com.orangelabs.rcs.protocol.sip.FeatureTags;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipInterface;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Abstract generic SIP session 
 * 
 * @author jexa7410
 */
public abstract class GenericSipSession extends ImsServiceSession {
	/**
	 * Feature tag
	 */
	private String featureTag;

	/**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(GenericSipSession.class.getSimpleName());
    
    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param contact Remote contactId
	 * @param featureTag Feature tag
	 */
	public GenericSipSession(SipService parent, ContactId contact, String featureTag) {
		super(parent, contact, PhoneUtils.formatContactIdToUri(contact, parent.getUser().getHomeDomain()));
		
		// Set the service feature tag
		this.featureTag = featureTag;
	}
	
	/**
	 * Returns feature tag of the service
	 * 
	 * @return Feature tag
	 */
	public String getFeatureTag() {
		return featureTag;
	}
	
	/**
	 * Returns the service ID
	 * 
	 * @return Service ID
	 */
	public String getServiceId() {
		return CapabilityUtils.extractServiceId(featureTag);
	}
	
    /**
     * Create an INVITE request
     *
     * @return Request
     * @throws SipException
     */
    public SipRequest createInvite() throws SipException {
        String ext = FeatureTags.FEATURE_3GPP + "=\"" + FeatureTags.FEATURE_3GPP_EXTENSION + "\"";
        SipRequest invite = getDialogPath().createInvite(new String [] {
			getFeatureTag(),
			ext
		}, new String [] {
			getFeatureTag(),
			ext,
			SipUtils.EXPLICIT_REQUIRE	        			
		}, getDialogPath().getLocalContent());

        try {
	    	ExtensionHeader header =  (ExtensionHeader)SipInterface.HEADER_FACTORY.createHeader(PPreferredServiceHeader.NAME,
	    			FeatureTags.FEATURE_3GPP_SERVICE_EXTENSION);
	    	invite.getStackMessage().addHeader(header);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't add SIP header", e);
			}
		}
    	
        return invite;
    }
    
    /**
     * Create 200 OK response
     *
     * @return Response
     * @throws SipException
     */
    public SipResponse create200OKResponse() throws SipException {
        String ext = FeatureTags.FEATURE_3GPP + "=\"" + FeatureTags.FEATURE_3GPP_EXTENSION + "\"";
		SipResponse resp = getDialogPath().create200OkInviteResponse(new String [] {
			getFeatureTag(),
			ext
		}, new String [] {
			getFeatureTag(),
			ext,
			SipUtils.EXPLICIT_REQUIRE	        			
		}, getDialogPath().getLocalContent()	);
		return resp;
    }
    
    /**
     * Prepare media session
     * 
     * @throws Exception 
     */
    public abstract void prepareMediaSession() throws Exception;
    
    /**
     * Start media session
     * 
     * @throws Exception 
     */
    public abstract void startMediaSession() throws Exception;

    /**
     * Close media session
     */
    public abstract void closeMediaSession();
    
    /**
     * Handle error
     * 
     * @param error Error
     */
    public void handleError(ImsServiceError error) {
        if (isSessionInterrupted()) {
        	return;
        }
        
        // Error
        if (logger.isActivated()) {
            logger.info("Session error: " + error.getErrorCode() + ", reason=" + error.getMessage());
        }

        // Close media session
        closeMediaSession();

        // Remove the current session
        removeSession();

        // Notify listeners
        for (int j = 0; j < getListeners().size(); j++) {
            ((SipSessionListener) getListeners().get(j))
                    .handleSessionError(new SipSessionError(error));
        }
    }
    
    @Override
	public void receiveBye(SipRequest bye) {
		super.receiveBye(bye);
		
		// Request capabilities to the remote
	    getImsService().getImsModule().getCapabilityService().requestContactCapabilities(getRemoteContact());
	}
	
    @Override
    public void receiveCancel(SipRequest cancel) {      
    	super.receiveCancel(cancel);
        
		// Request capabilities to the remote
	    getImsService().getImsModule().getCapabilityService().requestContactCapabilities(getRemoteContact());
	}
}
