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

package com.orangelabs.rcs.protocol.sip;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;
import java.util.ListIterator;
import java.util.Vector;

import javax2.sip.header.ContactHeader;
import javax2.sip.header.ExtensionHeader;
import javax2.sip.header.Header;
import javax2.sip.header.RecordRouteHeader;
import javax2.sip.header.RouteHeader;
import javax2.sip.header.ServerHeader;
import javax2.sip.header.UserAgentHeader;
import javax2.sip.message.Message;
import com.orangelabs.rcs.core.TerminalInfo;
import com.orangelabs.rcs.utils.logger.Logger;
/**
 * SIP message factory
 *
 * @author Jean-Marc AUFFRET
 */
public class SipUtils {
	/**
     * The logger
     */
    static Logger logger = Logger.getLogger(SipUtils.class.getName());

    /**
	 * Create a SIP MESSAGE request with a feature tag
	 *
	 * @param dialog SIP dialog path
	 * @param contentType Content type
	 * @param content Content
	 * @return SIP request
	 * @throws SipException
	 */
	public static SipRequest createMessage(SipDialogPath dialog, String contentType, String content) throws SipException {
		return dialog.createMessage(null, contentType, content.getBytes(UTF8));
	}

    /**
     * Create a SIP INVITE request
     *
     * @param dialog SIP dialog path
     * @param featureTags Feature tags
     * @param multipart Multipart
     * @param boundary Boundary tag
	 * @return SIP request
     * @throws SipException
     */
    public static SipRequest createMultipartInvite(SipDialogPath dialog,
    		String[] featureTags,
			String multipart,
			String boundary)
            throws SipException {
        return dialog.createMultipartInvite(featureTags, featureTags, multipart, boundary);
    }

    /**
	 * Works just like SipResponse createResponse(SipRequest request, String localTag, int code, String warning) except the warning
	 * is always null
	 *
	 * @see #SipResponse createResponse(SipRequest request, String localTag, int code, String warning)
	 */
	public static SipResponse createResponse(SipRequest request, String localTag, int code) throws SipException {
		return request.createResponse(localTag, code, null);
	}

	/**
	 * Create a SIP response for UPDATE request
	 *
	 * @param dialog Dialog path SIP request
	 * @param request SIP request
	 * @return SIP response
	 * @throws SipException
	 */
	public static SipResponse create200OkUpdateResponse(SipDialogPath dialog, SipRequest request) throws SipException {
		return dialog.create200OkUpdateResponse(request);
		
	}
	
	/**
	 * CRLF constant
	 */
	public final static String CRLF = "\r\n";
	
	/**
	 * Content-Transfer-Encoding header
	 */
	public static final String HEADER_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
	
	/**
	 * Accept-Contact header
	 */
	public static final String HEADER_ACCEPT_CONTACT = "Accept-Contact";
	public static final String HEADER_ACCEPT_CONTACT_C = "a";
	
	/**
	 * P-Access-Network-Info header
	 */
	public static final String HEADER_P_ACCESS_NETWORK_INFO = "P-Access-Network-Info";
	
	/**
	 * P-Asserted-Identity header
	 */
	public static final String HEADER_P_ASSERTED_IDENTITY = "P-Asserted-Identity";
	
	/**
	 * P-Preferred-Identity header
	 */
	public static final String HEADER_P_PREFERRED_IDENTITY = "P-Preferred-Identity";
	
	/**
	 * P-Associated-URI header
	 */
	public static final String HEADER_P_ASSOCIATED_URI = "P-Associated-URI";
	
	/**
	 * Service-Route header
	 */
	public static final String HEADER_SERVICE_ROUTE = "Service-Route";
	
	/**
	 * Privacy header
	 */
	public static final String HEADER_PRIVACY = "Privacy";
	
	/**
	 * Refer-Sub header
	 */
	public static final String HEADER_REFER_SUB = "Refer-Sub";
	
	/**
	 * Referred-By header
	 */
	public static final String HEADER_REFERRED_BY = "Referred-By";
	public static final String HEADER_REFERRED_BY_C = "b";
	
	/**
	 * Content-ID header
	 */
	public static final String HEADER_CONTENT_ID = "Content-ID";
	
	/**
	 * Session-Expires header
	 */
	public static final String HEADER_SESSION_EXPIRES = "Session-Expires";

	/**
	 * Session-Replaces header
	 */
	public static final String HEADER_SESSION_REPLACES = "Session-Replaces";

	/**
	 * SIP instance parameter
	 */
	public static final String SIP_INSTANCE_PARAM = "+sip.instance";
	
	/**
	 * Public GRUU parameter
	 */
	public static final String PUBLIC_GRUU_PARAM = "pub-gruu";
	
	/**
	 * Temp GRUU parameter
	 */
	public static final String TEMP_GRUU_PARAM = "temp-gruu";

	/**
	 * Explicit require tag
	 */
	public static final String EXPLICIT_REQUIRE = "explicit;require";

	/**
	 * Extract the URI part of a SIP address
	 * 
	 * @param addr SIP address
	 * @return URI
	 */
	public static String extractUriFromAddress(String addr) {
		String uri = addr; 
		try {
			int index = addr.indexOf("<");
			if (index != -1) {
				uri = addr.substring(index+1, addr.indexOf(">", index));
			}			
		} catch(Exception e) {
		}
		return uri;
	}
	
	/**
	 * Construct an NTP time from a date in milliseconds
	 *
	 * @param date Date in milliseconds
	 * @return NTP time in string format
	 */
	public static String constructNTPtime(long date) {
		long ntpTime = 2208988800L;
		long startTime = (date / 1000) + ntpTime;
		return String.valueOf(startTime);
	}

	/**
     * Build User Agent value for UAC
     *
     * @return UA value
     */
    public static String userAgentString() {
        String userAgent = "ExttoExt-client/Ext1.0 IM-client/OMA1.0 " + TerminalInfo.getProductInfo();
        return userAgent;
    }

    /**
     * Build User-Agent header
     * 
     * @param Header
     * @throws Exception
     */
	public static Header buildUserAgentHeader() throws Exception {
        Header userAgentHeader = SipInterface.HEADER_FACTORY.createHeader(UserAgentHeader.NAME, userAgentString());
	    return userAgentHeader;
    }
	
    /**
     * Build Server header
     * 
     * @return Header
     * @throws Exception
     */
	public static Header buildServerHeader() throws Exception {
        String userAgent = "ExttoExt-serv/Ext1.0 IM-client/OMA1.0 " + TerminalInfo.getProductInfo();
		return SipInterface.HEADER_FACTORY.createHeader(ServerHeader.NAME, userAgent);
    }
    
	/**
	 * Build P-Access-Network-info
	 * 
	 * @param info Access info
	 * @return Header
	 * @throws Exception
	 */
    private static Header buildAccessNetworkInfo(String info) throws Exception {
		Header accessInfo = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_ACCESS_NETWORK_INFO, info);
		return accessInfo;
    }
    
    /**
     * Extract a parameter from an input text
     * 
     * @param input Input text
     * @param param Parameter name
     * @param defaultValue Default value
     * @return Returns the parameter value or a default value in case of error
     */
    public static String extractParameter(String input, String param, String defaultValue) {
    	try {
	    	int begin = input.indexOf(param) + param.length();
	    	if (begin != -1) {
	    		int end = input.indexOf(" ", begin); // The end is by default the next space encountered
	    		if (input.charAt(begin) == '\"'){
	    			// The exception is when the first character of the param is a "
	    			// In this case, the end is the next " character, not the blank one
	    			begin++; // we remove also the first quote
	    			end = input.indexOf("\"",begin); // do not take last doubleQuote
	    		}
		    	if (end == -1) {
		    		return input.substring(begin);
		    	} else {
		    		return input.substring(begin, end);
		    	}
	    	}
			return defaultValue;
    	} catch(Exception e) {
    		return defaultValue;
    	}
    }

    /**
	 * Generate a list of route headers. The record route of the incoming message
	 * is used to generate the corresponding route header.
	 * 
	 * @param msg SIP message
	 * @param invert Invert or not the route list
	 * @return List of route headers as string
	 */
	public static Vector<String> routeProcessing(SipMessage msg, boolean invert) {
		Vector<String> result = new Vector<String>(); 
		ListIterator<Header> list = msg.getHeaders(RecordRouteHeader.NAME); 
		if (list == null) {
			// No route available
			return null;
		}

        while(list.hasNext()) {
        	RecordRouteHeader record = (RecordRouteHeader)list.next();
            RouteHeader route = SipInterface.HEADER_FACTORY.createRouteHeader(record.getAddress());
            if (invert) {
            	result.insertElementAt(route.getAddress().toString(), 0);
            } else {
            	result.addElement(route.getAddress().toString());
            }
		}

		return result;
	}
	
    /**
     * Set feature tags to a message
     * 
     * @param message SIP message
     * @param tags Table of tags
     * @throws Exception
     */
    public static void setFeatureTags(SipMessage message, String[] tags) throws Exception {
    	setFeatureTags(message.getStackMessage(), tags);
    }
    
    /**
     * Set feature tags to a message
     * 
     * @param message SIP stack message
     * @param tags Table of tags
     * @throws Exception
     */
    public static void setFeatureTags(Message message, String[] tags) throws Exception {
    	setFeatureTags(message, tags, tags);
    }
    
    /**
     * Set feature tags to a message
     * 
     * @param message SIP stack message
     * @param contactTags List of tags for Contact header
     * @param acceptContactTags List of tags for Accept-Contact header
     * @throws Exception
     */
    public static void setFeatureTags(Message message, String[] contactTags, String[] acceptContactTags) throws Exception {
        setContactFeatureTags(message, contactTags);
        setAcceptContactFeatureTags(message, acceptContactTags);
    }
    
    /**
     * Set feature tags to Accept-Contact header
     * 
     * @param message SIP stack message
     * @param tags List of tags
     * @throws Exception
     */
    public static void setAcceptContactFeatureTags(Message message, String[] tags) throws Exception {
    	if ((tags == null) || (tags.length == 0)) {
    		return;
    	}
    	
    	// Update Contact header
    	StringBuilder acceptTags = new StringBuilder("*");
    	for(int i=0; i < tags.length; i++) {
            acceptTags.append(";" + tags[i]);
    	}
    	
    	// Update Accept-Contact header
		Header header = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_ACCEPT_CONTACT, acceptTags.toString());
		message.addHeader(header);
    }

    /**
     * Set feature tags to Contact header
     * 
     * @param message SIP stack message
     * @param tags List of tags
     * @throws Exception
     */
    public static void setContactFeatureTags(Message message, String[] tags) throws Exception {
        if ((tags == null) || (tags.length == 0)) {
            return;
        }
        
        // Update Contact header
        ContactHeader contact = (ContactHeader)message.getHeader(ContactHeader.NAME);
        for(int i=0; i < tags.length; i++) {
            if (contact != null) {
                contact.setParameter(tags[i], null);
            }
        }
    }

    /**
     * Get the Referred-By header
     * 
	 * @param message SIP message
     * @return String value or null if not exist
     */
    public static String getReferredByHeader(SipMessage message) {
		// Read Referred-By header
		ExtensionHeader referredByHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_REFERRED_BY);
		if (referredByHeader == null) {
			// Check contracted form
			referredByHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_REFERRED_BY_C);
			if (referredByHeader == null) {
				// Try to extract manually the header in the message
				// TODO: to be removed when bug fix corrected in native NIST stack
				String msg = message.getStackMessage().toString();
				int index = msg.indexOf(SipUtils.CRLF + "b:");
				if (index != -1) {
					try {
						int begin = index+4;
						int end = msg.indexOf(SipUtils.CRLF, index+2);
						return msg.substring(begin, end).trim();
					} catch(Exception e) {
						return null;
					}
				} else {
					return null;
				}
			} else {
				return referredByHeader.getValue();
			}
		} else {
			return referredByHeader.getValue();
		}
    }

    /**
     * Get remote SIP instance ID
     * 
	 * @param message SIP message
     * @return ID or null
     */
    public static String getRemoteInstanceID(SipMessage message) {
        String instanceId = null;
        ContactHeader contactHeader = (ContactHeader)message.getHeader(ContactHeader.NAME);
        if (contactHeader != null) {
            instanceId = contactHeader.getParameter(SIP_INSTANCE_PARAM);
        }
    	return instanceId;
    }
    
    
    /**
     * Get SIP instance ID of an incoming message
     * 
	 * @param message SIP message
     * @return ID or null
     */
    public static String getInstanceID(SipMessage message) {
	    String instanceId = null;
	    ExtensionHeader acceptHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_ACCEPT_CONTACT);
	    if (acceptHeader == null) {
	        // Check contracted form
	        acceptHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_ACCEPT_CONTACT_C);
	    }
	    if (acceptHeader != null) {
	        String[] pnames = acceptHeader.getValue().split(";");
	        if (pnames.length > 1) {
	            // Start at index 1 to bypass the address
	            for (int i = 1; i < pnames.length; i++) {
	                if (pnames[i].startsWith(SipUtils.SIP_INSTANCE_PARAM)){
	                    instanceId = pnames[i].substring(SipUtils.SIP_INSTANCE_PARAM.length()+1, pnames[i].length());
	                    break;
	                }
	            }
	        }
	    }
	    return instanceId;
    }
    
    /**
     * Get public GRUU
     * 
	 * @param message SIP message
     * @return GRUU or null
     */
    public static String getPublicGruu(SipMessage message) {
	    String publicGruu = null;
	    ExtensionHeader acceptHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_ACCEPT_CONTACT);
	    if (acceptHeader == null) {
	        // Check contracted form
	        acceptHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_ACCEPT_CONTACT_C);
	    }
	    if (acceptHeader != null) {
	        String[] pnames = acceptHeader.getValue().split(";");
	        if (pnames.length > 1) {
	            // Start at index 1 to bypass the address
	            for (int i = 1; i < pnames.length; i++) {
	                if (pnames[i].startsWith(SipUtils.PUBLIC_GRUU_PARAM)){
	                	publicGruu = pnames[i].substring(SipUtils.PUBLIC_GRUU_PARAM.length()+1, pnames[i].length());
	                    break;
	                }
	            }
	        }
	    }
	    return publicGruu;
    }
    
    /**
     * Set remote SIP instance ID of a message
     * 
	 * @param message SIP message
     * @param instanceId SIP instance ID
	 * @throws Exception
     */
    public static void setRemoteInstanceID(Message message, String instanceId) throws Exception {
	    if (instanceId != null) {
	        ExtensionHeader acceptHeader = (ExtensionHeader)message.getHeader(SipUtils.HEADER_ACCEPT_CONTACT);
	        if (acceptHeader != null) {
	        	// Update existing header with SIP instance
		        acceptHeader.setValue(acceptHeader.getValue() + ";" +
	        		SipUtils.SIP_INSTANCE_PARAM + "=\"" + instanceId + "\"");
	        } else {
	        	// Add header with SIP instance
	            Header header = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_ACCEPT_CONTACT, "*;" +
            		SipUtils.SIP_INSTANCE_PARAM + "=\"" + instanceId + "\"");
	            message.addHeader(header);
	        }
	    }
    }
    
    /**
     * Get display name from URI
     * 
     * @param uri URI
     * @return Display name or null
     */
    public static String getDisplayNameFromUri(String uri) {
		if (uri == null) {
			return null;
		}
		try {
			int index0 = uri.indexOf("\"");
			if (index0 != -1) {
				int index1 = uri.indexOf("\"", index0+1);
				if (index1 > 0) {
					return uri.substring(index0+1, index1);
				}
			}			
			return null;
		} catch(Exception e) {
			return null;
		}
    }


    /**********************************************************************************
     *  tct-stack add for CMCC message modes
     **********************************************************************************/

    /**
     * P-Preferred-Identity header
     */
    public static final String HEADER_P_PREFERRED_SERVICE = "P-Asserted-Service"; //"P-Preferred-Service"; // CMCC server set in P-Asserted-Service

    /**
     * Get the P-Referred-Service header
     * 
     * @param message SIP message
     * @return String value or null if not exist
     */
    public static String getPReferredServiceHeader(SipMessage message) {
        // Read P-Referred-Service header
        ExtensionHeader header = (ExtensionHeader) message.getHeader(SipUtils.HEADER_P_PREFERRED_SERVICE);
        if (header != null) {
            return header.getValue();
        } else {
            return null;
        }
    }

}
