/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications AB.
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
 * NOTE: This file has been modified by Sony Mobile Communications AB.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.orangelabs.rcs.core.richcall.geoloc;

import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.ImsServiceError;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.richcall.ContentSharingError;
import com.orangelabs.rcs.core.richcall.ContentSharingSession;
import com.orangelabs.rcs.core.richcall.RichcallService;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Geoloc sharing transfer session
 * 
 * @author jexa7410
 */
public abstract class GeolocTransferSession extends ContentSharingSession {
	/**
	 * Default SO_TIMEOUT value (in seconds)
	 */
	public final static int DEFAULT_SO_TIMEOUT = 30;
	
	/**
	 * Geoloc transfered
	 */
	private boolean geolocTransfered = false;

	/**
	 * Geoloc info
	 */
	private Geoloc mGeoloc;
	
	/**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(GeolocTransferSession.class.getSimpleName());

    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param content Content to be shared
	 * @param contact Remote contact Id
	 */
	public GeolocTransferSession(ImsService parent, MmContent content, ContactId contact) {
		super(parent, content, contact);
	}
	
	/**
	 * Set geoloc
	 * 
	 * @param geoloc Geoloc
	 */
	public void setGeoloc(Geoloc geoloc) {
		mGeoloc = geoloc;
	}
	
	/**
	 * Get geoloc
	 * 
	 * @return Geoloc
	 */
	public Geoloc getGeoloc() {
		return mGeoloc;
	}
	
	/**
	 * Geoloc has been transfered
	 */
	public void geolocTransfered() {
		this.geolocTransfered = true;
	}
	
	/**
	 * Is geoloc transfered
	 * 
	 * @retrurn Boolean
	 */
	public boolean isGeolocTransfered() {
		return geolocTransfered; 
	}

    /**
     * Create an INVITE request
     *
     * @return the INVITE request
     * @throws SipException 
     */
    public SipRequest createInvite() throws SipException {
        String[] featureTags = RichcallService.FEATURE_TAGS_GEOLOC_SHARE;
		return getDialogPath().createInvite(featureTags, featureTags, getDialogPath().getLocalContent());
    }
    
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

        // Close MSRP session
        closeMediaSession();

        // Remove the current session
        removeSession();

        // Notify listeners
        for(int j=0; j < getListeners().size(); j++) {
            ((GeolocTransferSessionListener)getListeners().get(j)).handleSharingError(new ContentSharingError(error));
        }
    }

	@Override
	public void startSession() {
		getImsService().getImsModule().getRichcallService().addSession(this);
		start();
	}

	@Override
	public void removeSession() {
		getImsService().getImsModule().getRichcallService().removeSession(this);
	}
}
