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

package com.orangelabs.rcs.protocol.sip;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import gov2.nist.javax2.sip.address.SipUri;

import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.logger.Logger;

import javax2.sip.address.URI;
import javax2.sip.header.ContactHeader;
import javax2.sip.header.ContentLengthHeader;
import javax2.sip.header.ContentTypeHeader;
import javax2.sip.header.ExpiresHeader;
import javax2.sip.header.ExtensionHeader;
import javax2.sip.header.Header;
import javax2.sip.header.RequireHeader;
import javax2.sip.header.ToHeader;
import javax2.sip.header.WarningHeader;
import javax2.sip.message.Request;
import javax2.sip.message.Response;

/**
 * SIP request
 * 
 * @author jexa7410
 */
public class SipRequest extends SipMessage {
	
	/**
	 * Contribution ID header
	 */
	public static final String HEADER_CONTRIBUTION_ID = "Contribution-ID";
	
    private static final Logger logger = Logger.getLogger(SipRequest.class.getName());

    /**
     * Regular expression of the SIP header
     *
     */
    final static String REGEXP_EXTRACT_URI = "<(.*)>";
	/**
	 * Pattern to extract Uri from SIP header
	 */
	public final static Pattern PATTERN_EXTRACT_URI = Pattern.compile(REGEXP_EXTRACT_URI);

	/**
	 * Constructor
	 *
	 * @param request SIP stack request
	 */
	public SipRequest(Request request) {
		super(request);
	}

	/**
	 * Return the SIP stack message
	 * 
	 * @return SIP request
	 */
	public Request getStackMessage() {
		return (Request)stackMessage;
	}
	
	/**
	 * Returns the method value
	 * 
	 * @return Method name or null is case of response
	 */
	public String getMethod() {
		return getStackMessage().getMethod();
	}
	
	/**
	 * Return the request URI
	 * 
	 * @return String
	 */
	public String getRequestURIString() {
		return getStackMessage().getRequestURI().toString();
	}
	
	public SipUri getRequestURI() {
		URI uri = getStackMessage().getRequestURI();
		if (uri.isSipURI()) {
			return (SipUri)uri;
		}
		return null;
	}
	
	/**
	 * Return the expires value
	 * 
	 * @return Expire value
	 */
	public int getExpires() {
        ExpiresHeader expires = (ExpiresHeader)getStackMessage().getHeader(ExpiresHeader.NAME);
    	if (expires != null) {
            return expires.getExpires();            
        } else {
        	return -1;
        }
	}

	/**
	 * Get contribution ID
	 *
	 * @return String
	 */
	public String getContributionId() {
		ExtensionHeader contribHeader = (ExtensionHeader)getHeader(SipRequest.HEADER_CONTRIBUTION_ID);
		if (contribHeader != null) {
			return contribHeader.getValue();
		} else {
			return null;
		}
	}

	/**
	 * Is a group chat session invitation
	 *
	 * @param request Request
	 * @return Boolean
	 */
	public boolean isGroupChatInvitation() {
	    ContactHeader contactHeader = (ContactHeader)getHeader(ContactHeader.NAME);
		String param = contactHeader.getParameter("isfocus");
		if (param != null) {
			return true;
		} else {
			return false;
		}
	}

	public String getFirstFeatureTag() {
		return getFeatureTags().get(0);
	}
	
	public SipResponse createResponse(int code) throws SipException {
		try {
			// Create the response
			Response response = SipInterface.MSG_FACTORY.createResponse(code, 
					(Request)this.getStackMessage());
			SipResponse resp = new SipResponse(response);
			resp.setStackTransaction(this.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	public SipResponse createResponse(String localTag, int code, String warning) throws SipException {
		try {
			// Create the response
			Response response = SipInterface.MSG_FACTORY.createResponse(code, 
					(Request) getStackMessage());

			// Set the local tag
			if (localTag != null) {
				ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
				to.setTag(localTag);
			}
			if (warning != null) {
				WarningHeader warningHeader = SipInterface.HEADER_FACTORY.createWarningHeader("SIP", 403, warning);
				response.addHeader(warningHeader);
			}
			SipResponse resp = new SipResponse(response);
			resp.setStackTransaction(getStackTransaction());
			return resp;
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message: ", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	public SipResponse create200OkOptionsResponse(ContactHeader contact,
			String[] featureTags, String sdp) throws SipException {
		try {
			// Create the response
			Response response = SipInterface.MSG_FACTORY.createResponse(200, (Request)getStackMessage());

	        // Set the local tag
			ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
			to.setTag(IdGenerator.getIdentifier());

	        // Set Contact header
	        response.addHeader(contact);

	        // Set feature tags
            SipUtils.setFeatureTags(response, featureTags);

	        // Set Allow header
	        SipDialogPath.buildAllowHeader(response);

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());

			// Set the content part if available
			if (sdp != null) {
			    // Set the content type header
				ContentTypeHeader contentTypeHeader = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
				response.setContent(sdp, contentTypeHeader);

			    // Set the content length header
				ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY
						.createContentLengthHeader(sdp.getBytes(UTF8).length);
				response.setContentLength(contentLengthHeader);
			}

			SipResponse resp = new SipResponse(response);
			resp.setStackTransaction(getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	public SipResponse create200OkReInviteResponse(ContactHeader contact) throws SipException {
        
    	try {
            // Create the response
            Response response = SipInterface.MSG_FACTORY.createResponse(200, (Request)getStackMessage());

            // Set Contact header

			response.addHeader(contact);

            // Set the Server header
            response.addHeader(SipUtils.buildServerHeader());

            // Set the Require header
            Header requireHeader = SipInterface.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
            response.addHeader(requireHeader);

            // Add Session-Timer header
            Header sessionExpiresHeader = getHeader(SipUtils.HEADER_SESSION_EXPIRES);
            if (sessionExpiresHeader != null) {
            	response.addHeader(sessionExpiresHeader);
            }

            SipResponse resp = new SipResponse(response);
            resp.setStackTransaction(getStackTransaction());
            return resp;
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP response");
        }
	}

	public String getAssertedIdentityHeader() {
		
		ListIterator<Header> list = getHeaders(SipUtils.HEADER_P_ASSERTED_IDENTITY);
		if (list != null) {
			// There is at most 2 P-Asserted-Identity headers, one with tel uri and one with sip uri
			// We give preference to the tel uri if both are present, if not we return the first one
			String assertedHeader1 = null;
			if (list.hasNext()) {
				// Get value of the first header
				assertedHeader1 = ((ExtensionHeader) list.next()).getValue();
				if (assertedHeader1.contains("tel:")) {
					return assertedHeader1;
				}
				
				if (list.hasNext()) {
					// Get value of the second header (it may not be present)
					String assertedHeader2 = ((ExtensionHeader) list.next()).getValue();
					if (assertedHeader2.contains("tel:")) {
						return assertedHeader2;
					}
				}
				// In case there is no tel uri, return the value of the first header
				return assertedHeader1;
			}
		}
		return null;
	}

	/**
	 * get URI from SIP identity header
	 * 
	 * @param header
	 *            the SIP header
	 * @return the Uri
	 */
	public static String extractUriFromSipHeader(String header) {
		if (header != null) {
			Matcher matcher = SipRequest.PATTERN_EXTRACT_URI.matcher(header);
			if (matcher.find()) {
				return matcher.group(1);
				
			}
		}
		return header;
	}

	public String getAssertedIdentity() {
		String assertedIdentityHeader = getAssertedIdentityHeader();
		if (assertedIdentityHeader != null) {
			return SipRequest.extractUriFromSipHeader(assertedIdentityHeader);
		}
		// No P-AssertedIdentity header, we take the value in the FROM uri
		return SipRequest.extractUriFromSipHeader(getFromUri());
	}


    /**********************************************************************************
     *  tct-stack add for CMCC message modes: 1) pager mode; 2) large mode
     **********************************************************************************/

    /**
     * Contribution ID header
     */
    public static final String HEADER_CONVERSATION_ID = "Conversation-ID";

    /**
     * Get Conversation ID
     * 
     * @return String
     */
    public String getConversationId() {
        ExtensionHeader convHeader = (ExtensionHeader) getHeader(SipRequest.HEADER_CONVERSATION_ID);
        if (convHeader != null) {
            return convHeader.getValue();
        } else {
            return null;
        }
    }

}
