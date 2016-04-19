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

package com.orangelabs.rcs.core.registration;

import java.util.ListIterator;

import javax2.sip.address.Address;
import javax2.sip.address.SipURI;
import javax2.sip.address.URI;
import javax2.sip.header.ExtensionHeader;
import javax2.sip.header.Header;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.protocol.sip.SipInterface;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.SimCard;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * GIBA or early-IMS registration procedure
 * 
 * @author jexa7410
 */
public class GibaRegistrationProcedure implements RegistrationProcedure {
	
	SimCard sim;
	
    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());
    UserProfile user;

    /**
	 * Constructor
	 */
	public GibaRegistrationProcedure(UserProfile user) {
		this.user = user;
	}
	
	/**
	 * Initialize procedure
	 */
	@Override
	public void init() {
		sim = SimCard.load(AndroidFactory.getApplicationContext());
	}
	
	/**
	 * Returns the home domain name
	 * 
	 * @return Domain name
	 */
	@Override
	public String getHomeDomain() {
		return sim.getHomeDomain();
	}
	
	/**
	 * Returns the public URI or IMPU for registration
	 * 
	 * @return Public URI
	 */
	@Override
	public String getPublicUri() {
		// Derived IMPU from IMSI: <IMSI>@mnc<MNC>.mcc<MCC>.3gppnetwork.org
		return "sip:" + sim.getImsi() + "@" + sim.getHomeDomain();
	}

	/**
	 * Write the security header to REGISTER request
	 * 
	 * @param request Request
	 */
	@Override
	public void writeSecurityHeader(SipRequest request) {
		// Nothing to do here
	}

	/**
	 * Read the security header from REGISTER response
	 * 
	 * @param response Response
	 * @throws CoreException
	 */
	@Override
	public void readSecurityHeader(SipResponse response) throws CoreException {
		try {
			// Read the associated-URI from the 200 OK response
			ListIterator<Header> list = response.getHeaders(SipUtils.HEADER_P_ASSOCIATED_URI);
			SipURI sipUri = null;
			while(list.hasNext()) { 
				ExtensionHeader associatedHeader = (ExtensionHeader)list.next();
				Address sipAddr = SipInterface.ADDR_FACTORY.createAddress(associatedHeader.getValue());
				URI uri = sipAddr.getURI();
				if (uri instanceof SipURI) {
					// SIP-URI
					sipUri = (SipURI)sipAddr.getURI();
				}			
			}
			if (sipUri == null)  {
				throw new CoreException("No SIP-URI found in the P-Associated-URI header");
			}
			
			// Update the user profile
			getUser().setUsername(ContactUtils.createContactId(sipUri.getUser()));
			getUser().setHomeDomain(sipUri.getHost());
			getUser().setXdmServerLogin("sip:" + sipUri.getUser() + "@" + sipUri.getHost());
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't read a SIP-URI from the P-Associated-URI header", e);
			}
			throw new CoreException("Bad P-Associated-URI header");
		}
	}

	private UserProfile getUser() {
		return user;
	}

	public HttpDigestMd5Authentication getHttpDigest() {
		return null;
	}
}
