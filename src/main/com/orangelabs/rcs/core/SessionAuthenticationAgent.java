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

import javax2.sip.header.ProxyAuthenticateHeader;
import javax2.sip.header.ProxyAuthorizationHeader;

import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * HTTP Digest MD5 authentication agent for sessions
 * 
 * @author JM. Auffret
 */
public class SessionAuthenticationAgent {
	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

	/**
	 * HTTP Digest MD5 agent for session
	 */
	private HttpDigestMd5Authentication digest = new HttpDigestMd5Authentication();

	/**
	 * HTTP Digest MD5 agent for register (nonce caching procedure)
	 */
	private HttpDigestMd5Authentication registerDigest;
	
	private String user;
	private String password;

	/**
	 * Constructor
	 * 
	 * @param imsModule IMS module
	 */
	public SessionAuthenticationAgent(ImsModule imsModule) {
		
		// Re-use the registration authentication (nonce caching)
		registerDigest = imsModule.getRegisterDigest();
		
		user = imsModule.getUser().getPrivateID();
		password = imsModule.getUser().getPassword();
	}

	/**
	 * Set the proxy authorization header on the INVITE request
	 * 
	 * @param request SIP request
	 * @throws CoreException
	 */
	public void setProxyAuthorizationHeader(SipRequest request) throws CoreException {
		if ((digest.getRealm() == null) || (digest.getNextnonce() == null)) {
			return;
		}

		try {
	   		// Update nonce parameters
			digest.updateNonceParameters();
	
			// Calculate response
	   		String response = digest.calculateResponse(user,
	   				password,
	   				request.getMethod(),
	   				request.getRequestURIString(),
					digest.buildNonceCounter(),
					request.getContent());			
	   		
			// Build the Proxy-Authorization header
			String auth = "Digest username=\"" + user + "\"" +
				",uri=\"" + request.getRequestURIString() + "\"" +
				",algorithm=MD5" +
				",realm=\"" + digest.getRealm() + "\"" +
				",nc=" + digest.buildNonceCounter() +
				",nonce=\"" + digest.getNonce() + "\"" +
				",response=\"" + response +	"\"" +
				",cnonce=\"" + digest.getCnonce() + "\"";
			String qop = digest.getQop();
			if (qop != null) {
				auth += ",qop=" + qop;
			}
			
			// Set header in the SIP message 
			request.addHeader(ProxyAuthorizationHeader.NAME, auth);

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create the proxy authorization header", e);
			}
			throw new CoreException("Can't create the proxy authorization header");
		}
	}

	/**
	 * Read parameters of the Proxy-Authenticate header
	 * 
	 * @param response SIP response
	 */
	public void readProxyAuthenticateHeader(SipResponse response) {
		ProxyAuthenticateHeader header = (ProxyAuthenticateHeader)response.getHeader(ProxyAuthenticateHeader.NAME);
		if (header != null) {
	   		// Get domain name
			digest.setRealm(header.getRealm());

			// Get qop
			digest.setQop(header.getQop());
	   		
	   		// New nonce to be used
			digest.setNextnonce(header.getNonce());
		}
	}	

	/**
	 * Set the authorization header on the INVITE request
	 * 
	 * @param request SIP request
	 * @throws CoreException
	 */
	public void setAuthorizationHeader(SipRequest request) throws CoreException {
		try {
			// Re-use the registration authentication (nonce caching)
			if ((registerDigest == null) || (registerDigest.getNextnonce() == null)) {
				return;
			}
			
	   		// Update nonce parameters
			registerDigest.updateNonceParameters();
	
			// Calculate response
	   		String response = registerDigest.calculateResponse(user,
	   				password,
	   				request.getMethod(),
	   				request.getRequestURIString(),
	   				registerDigest.buildNonceCounter(),
					request.getContent());			
	   		
			// Build the Authorization header
			String auth = "Digest username=\"" + user + "\"" +
				",uri=\"" + request.getRequestURIString() + "\"" +
				",algorithm=MD5" +
				",realm=\"" + registerDigest.getRealm() + "\"" +
				",nc=" + registerDigest.buildNonceCounter() +
				",nonce=\"" + registerDigest.getNextnonce() + "\"" +
				",response=\"" + response +	"\"" +
				",cnonce=\"" + registerDigest.getCnonce() + "\"";
			String qop = registerDigest.getQop();
			if (qop != null) {
				auth += ",qop=" + qop;
			}
			
			// Set header in the SIP message 
			request.addHeader(ProxyAuthorizationHeader.NAME, auth);

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create the authorization header", e);
			}
			throw new CoreException("Can't create the authorization header");
		}
    }
}
