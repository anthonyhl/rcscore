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

package com.orangelabs.rcs.core.richcall;

import android.net.Uri;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.PhoneUtils;

/**
 * Content sharing session
 * 
 * @author jexa7410
 */
public abstract class ContentSharingSession extends ImsServiceSession {
	/**
	 * Content to be shared
	 */
	private MmContent content;
	
    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param content Content to be shared
	 * @param contact Remote contactId
	 */
	public ContentSharingSession(ImsService parent, MmContent content, ContactId contact) {
		super(parent, contact, PhoneUtils.formatContactIdToUri(contact, parent.getUser().getHomeDomain()));
		
		this.content = content;
	}
	
	/**
	 * Returns the content
	 * 
	 * @return Content 
	 */
	public MmContent getContent() {
		return content;
	}
	
	/**
	 * Set the content
	 * 
	 * @param content Content  
	 */
	public void setContent(MmContent content) {
		this.content = content;
	}

	/**
	 * Returns the "file-selector" attribute
	 * 
	 * @return String
	 */
	public String getFileSelectorAttribute() {
		return "name:\"" + content.getName() + "\"" + 
			" type:" + content.getEncoding() +
			" size:" + content.getSize();
	}
	
	/**
	 * Returns the "file-location" attribute
	 * 
	 * @return Uri
	 */
	public Uri getFileLocationAttribute() {
		Uri file = content.getUri();
		if ((file != null) && file.getScheme().startsWith("http")) {
			return file;
		} else {
			return null;
		}
	}
	
	/**
	 * Returns the "file-transfer-id" attribute
	 * 
	 * @return String
	 */
	public String getFileTransferId() {
		return "CSh" + IdGenerator.generateMessageID();
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
