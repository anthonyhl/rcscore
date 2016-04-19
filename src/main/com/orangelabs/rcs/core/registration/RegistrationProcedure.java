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

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;

/**
 * Abstract registration procedure
 * 
 * @author jexa7410
 */
public interface RegistrationProcedure {
	/**
	 * Initialize procedure
	 */
	public void init(); 

	/**
	 * Returns the home domain name
	 * 
	 * @return Domain name
	 */
	public String getHomeDomain(); 
	
	/**
	 * Returns the public URI or IMPU for registration
	 * 
	 * @return Public URI
	 */
	public String getPublicUri();
	
	/**
	 * Write the security header to REGISTER request
	 * 
	 * @param request Request
	 * @throws CoreException
	 */
	public void writeSecurityHeader(SipRequest request) throws CoreException;

	/**
	 * Read the security header from REGISTER response
	 * 
	 * @param response Response
	 * @throws CoreException
	 */
	public void readSecurityHeader(SipResponse response) throws CoreException;

	public HttpDigestMd5Authentication getHttpDigest();
}
