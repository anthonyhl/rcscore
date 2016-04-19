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

package com.orangelabs.rcs.core.network;

import java.util.ListIterator;
import java.util.Vector;

import javax2.sip.header.ContactHeader;
import javax2.sip.header.Header;
import javax2.sip.header.ViaHeader;
import javax2.sip.header.WarningHeader;
import javax2.sip.message.Request;
import javax2.sip.message.Response;

import com.orangelabs.rcs.core.im.chat.ContributionIdGenerator;
import com.orangelabs.rcs.protocol.sip.KeepAliveManager;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.protocol.sip.SipEventListener;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipInterface;
import com.orangelabs.rcs.protocol.sip.SipMessage;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * SIP manager
 *
 * @author JM. Auffret
 */
public class SipManager {

	/**
	 * SIP timeout for SIP transaction (in seconds)
	 */
	public static int TIMEOUT = 30;

    public SipDialogPath createServiceDialogPath(
            String target, String localParty) {
    	
        return new SipDialogPath(sipstack, sipstack.generateCallId(),
		1, target, localParty, target, sipstack.getServiceRoutePath(), getUserProfile());
    }
    
    public SipDialogPath createServiceDialogPath(String target) {
        return createServiceDialogPath(target, getPublicAddress());
    }
   
    public SipDialogPath createDefaultDialogPath(
            String target, String remoteParty) {
        return new SipDialogPath(sipstack, sipstack.generateCallId(),
                1, target, remoteParty, remoteParty, sipstack.getDefaultRoutePath(), getUserProfile());
    }
    
	private String getPublicAddress() {
		return getUserProfile().getPublicAddress();
	}

	private UserProfile getUserProfile() {
		return userProfile;
	}

    public SipDialogPath createInviteDialogPath(SipRequest invite) {
        // Set the call-id
        String callId = invite.getCallId();

        // Set target
        String target = invite.getContactURI();

        // Set local party
        String localParty = invite.getTo();

        // Set remote party
        String remoteParty = invite.getFrom();

        // Get the CSeq value
        long cseq = invite.getCSeq();

        // Set the route path with the Record-Route
        Vector<String> route = SipUtils.routeProcessing(invite, false);

        // Create a dialog path
        SipDialogPath dialogPath = new SipDialogPath(sipstack,
                callId, cseq, target, localParty, remoteParty,
                route, getUserProfile());

        // Set the INVITE request
        dialogPath.setInvite(invite);

        // Set the remote tag
        dialogPath.setRemoteTag(invite.getFromTag());

        // Set the remote content part
        dialogPath.setRemoteContent(invite.getContent());

        // Set the session timer expire
        dialogPath.setSessionExpireTime(invite.getSessionTimerExpire());

        return dialogPath;
    }
	/**
     * IMS network interface
     */
    public ImsNetworkInterface networkInterface;

    /**
	 * SIP stack
	 */
	private SipInterface sipstack;

	private UserProfile userProfile;

	/**
	 * The logger
	 */
	private Logger logger = Logger.getLogger(SipManager.class.getSimpleName());

	private String localAddress;

	/**
     * Constructor
     *
     * @param parent IMS network interface
     */
	public SipManager(ImsNetworkInterface parent) {
		this.networkInterface = parent;
	}

	public void setSipEventListener(SipEventListener listener) {
		sipstack.addSipEventListener(listener);
	}
	
	public void startKeepAlive() {
		if (RcsSettings.getInstance().isSipKeepAliveEnabled()) {
			sipstack.getKeepAliveManager().start();
		}
	}

	/**
	 * Terminate the manager
	 */
	public void terminate() {
		if (logger.isActivated()) {
			logger.info("Terminate the SIP manager");
		}

		// Close the SIP stack
		if (sipstack != null) {
			closeStack();
		}

		if (logger.isActivated()) {
			logger.info("SIP manager has been terminated");
		}
	}

	/**
     * Initialize the SIP stack
     *
     * @param localAddr Local IP address
	 * @param proxyAddr Outbound proxy address
	 * @param proxyPort Outbound proxy port
	 * @param tcpFallback TCP fallback according to RFC3261 chapter 18.1.1
	 * @param networkType type of network
	 * @param user TODO
	 * @param isSecure Need secure connection or not
     * @return SIP stack
     * @throws SipException
     */
    public synchronized void initStack(String localAddr, String proxyAddr,
    		int proxyPort, String protocol, boolean tcpFallback, int networkType, UserProfile user) throws SipException {
		// Close the stack if necessary
		closeStack();
		userProfile = user;
		localAddress = localAddr;

		// Create the SIP stack
        sipstack = new SipInterface(localAddr, proxyAddr, proxyPort, protocol, tcpFallback, networkType);
		if (logger.isActivated()) {
			logger.info("SIP manager started");
		}
    }

	/**
	 * Close the SIP stack
	 */
	private synchronized void closeStack() {
		if (sipstack == null) {
			// Already closed
			return;
		}

		try {
			// Close the SIP stack
			sipstack.close();
			sipstack = null;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't close SIP stack properly", e);
			}
		}
	}

	/**
     * Send a SIP message and create a context to wait a response
     *
     * @param message SIP message
     * @return Transaction context
     * @throws SipException
     */
    public SipTransactionContext sendSipMessageAndWait(SipMessage message) throws SipException {
    	return sendSipMessageAndWait(message, SipManager.TIMEOUT);
	}
    
	/**
	 * Send a SIP message and create a context to wait for response
	 * 
	 * @param message
	 * @param timeout
	 * @param callback
	 *            callback to handle provisional response
	 * @return SIP transaction context
	 * @throws SipException
	 */
	public SipTransactionContext sendSipMessageAndWait(SipMessage message, int timeout, SipTransactionContext.INotifySipProvisionalResponse callback)
			throws SipException {
        if (sipstack == null) {
			throw new SipException("Stack not initialized");
        }
		SipTransactionContext ctx = sipstack.sendSipMessageAndWait(message, callback);

		// wait the response
		ctx.waitResponse(timeout);

		if (!(message instanceof SipRequest) || !ctx.isSipResponse()) {
			// Return the transaction context
			return ctx;
			
		}
		String method = ((SipRequest) message).getMethod();
		SipResponse response = ctx.getSipResponse();
		if (response == null) {
			return ctx;
			
		}
		// Analyze the received response
		if (!Request.REGISTER.equals(method)) {
			// Check if not registered and warning header
			if (isNotRegistered(ctx)) {
				// Launch new registration
				networkInterface.restartRegister();

				if (callback == null) {
					throw new SipException("Not registered");					
				}
			}
		}
		if (!Request.INVITE.equals(method) && !Request.REGISTER.equals(method)) {
			return ctx;
			
		}
		
		KeepAliveManager keepAliveManager = sipstack.getKeepAliveManager();
		if (keepAliveManager == null) {
			return ctx;			
		}
		
		// Message is a response to INVITE or REGISTER: analyze "keep" flag of "Via" header
		int viaKeep = -1;
		String keepStr = null;
		ListIterator<ViaHeader> iterator = response.getViaHeaders();
		if (iterator.hasNext()) {
			ViaHeader respViaHeader = iterator.next();	
			keepStr = respViaHeader.getParameter("keep");
			viaKeep = toInteger(keepStr, -1);
		}

		// If "keep" value is invalid or not present, set keep alive period to default value
		if (viaKeep <= 0) {
			if (logger.isActivated())
				logger.warn("Non positive keep value \"" + keepStr + "\"");
			RcsSettings rcsSettings = RcsSettings.getInstance();
			keepAliveManager.setPeriod(rcsSettings.getSipKeepAlivePeriod());
		}
		else {
			keepAliveManager.setPeriod(viaKeep);
		}

		// Return the transaction context
		return ctx;
    }
	
	private int toInteger(String value, int defaultValue) {
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		
		int result;
		
		try {
			result = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
		
		return result;
	}

	public boolean isNotRegistered(SipTransactionContext ctx) {
		SipResponse response = ctx.getSipResponse();
		WarningHeader warn = (WarningHeader) response.getHeader(WarningHeader.NAME);
		return Response.FORBIDDEN == ctx.getStatusCode() && warn == null;
	}
    
    /**
     * Send a SIP message and create a context to wait a response
     *
     * @param message SIP message
     * @param timeout SIP timeout
     * @return Transaction context
     * @throws SipException
     */
    public SipTransactionContext sendSipMessageAndWait(SipMessage message, int timeout) throws SipException {
        return sendSipMessageAndWait(message, timeout, null);
	}


	/**
     * Send a SIP response
     *
     * @param response SIP response
     * @throws SipException
     */
	public void sendSipResponse(SipResponse response) throws SipException {
		if (sipstack != null) {
			sipstack.sendSipResponse(response);
		} else {
			throw new SipException("Stack not initialized");
		}
	}

    /**
     * Send a subsequent SIP request
     *
     * @param dialog Dialog path
     * @param request Request
     * @throws SipException
     */
	public SipTransactionContext sendSubsequentRequest(SipDialogPath dialog, SipRequest request) throws SipException {
		return sendSubsequentRequest(dialog, request, SipManager.TIMEOUT);
	}
	
	/**
     * Send a subsequent SIP request
     *
     * @param dialog Dialog path
     * @param request Request
     * @param timeout SIP timeout
     * @throws SipException
     */
	public SipTransactionContext sendSubsequentRequest(SipDialogPath dialog, SipRequest request, int timeout) throws SipException {
		if (sipstack != null) {
		    SipTransactionContext ctx = sipstack.sendSubsequentRequest(dialog, request);

            // wait the response
            ctx.waitResponse(timeout);

            // Analyze the received response
            if (ctx.isSipResponse()) {

                if (isNotRegistered(ctx)) {
                    // Launch new registration
                    networkInterface.restartRegister();

                    // Throw not registered exception 
                    throw new SipException("Not registered");
                }
            }
            return ctx;
		} else {
			throw new SipException("Stack not initialized");
		}
    }

	public void updateGruu(SipResponse resp, String instanceId) {
		// Set the GRUU
	    sipstack.setInstanceId(instanceId); 
	    
	    ListIterator<Header> contacts = resp.getHeaders(ContactHeader.NAME);
	   
	    while(contacts.hasNext()) {
	        ContactHeader contact = (ContactHeader)contacts.next();
	        String contactInstanceId = contact.getParameter(SipUtils.SIP_INSTANCE_PARAM);
	        if ((contactInstanceId != null) && (instanceId != null) && (instanceId.contains(contactInstanceId))) {
	            String pubGruu = contact.getParameter(SipUtils.PUBLIC_GRUU_PARAM);
	            sipstack.setPublicGruu(pubGruu);          
	            String tempGruu = contact.getParameter(SipUtils.TEMP_GRUU_PARAM);
	            sipstack.setTemporaryGruu(tempGruu);
	        }
	    }
	    
	    // Set the service route path
	    ListIterator<Header> routes = resp.getHeaders(SipUtils.HEADER_SERVICE_ROUTE);
	    sipstack.setServiceRoutePath(routes);
	}
	
	public boolean isMyInstanceId(SipRequest request) {
		String instanceId = SipUtils.getInstanceID(request);
		return (instanceId != null) && !instanceId.contains(sipstack.getInstanceId());
	}
	
	public boolean isMyGruu(SipRequest request) {
		String publicGruu = SipUtils.getPublicGruu(request);
        return (publicGruu != null) && !publicGruu.contains(sipstack.getPublicGruu());
	}
	
	public boolean isRegisterTo(String ipAddress, int port) {
		if (sipstack != null) {
            if (logger.isActivated()) {
                logger.debug("Registration state has changed: sip stack not initialized yet.");
            }
            return false;
        } else if (sipstack.getOutboundProxyAddr().equals(ipAddress)) {
            if (logger.isActivated()) {
                logger.debug("Registration state has changed: proxy ip address has changed (old: "
                		+ sipstack.getOutboundProxyAddr()
                        + " - new: " + ipAddress + ").");
            }
            return false;
        } else if (sipstack.getOutboundProxyPort() == port) {
            if (logger.isActivated()) {
                logger.debug("Registration state has changed: proxy port has changed (old: "
                		+ sipstack.getOutboundProxyPort() + " - new: "
                        + port + ").");
            }
            return false;
        }
        return true;
	}
	
	public String generateCallId() {
		return sipstack.generateCallId();
	}

	public String getContributionId() {
		return ContributionIdGenerator.getContributionId(sipstack.generateCallId());
	}

	public void send200OkOptionsResponse(SipRequest options,
			String[] featureTags, String sdp) throws Exception {
		
		SipResponse resp = options.create200OkOptionsResponse(sipstack.getContact(), featureTags, sdp);

		// Send 200 OK response
		sendSipResponse(resp);
	}


    /**********************************************************************************
     *  tct-stack add for CMCC message modes: pager mode message
     **********************************************************************************/

    public SipDialogPath createMessageDialogPath(SipRequest message) {
        // Set the call-id
        String callId = message.getCallId();

        // Set target
        String target = message.getContactURI();

        // Set local party
        String localParty = message.getTo();

        // Set remote party
        String remoteParty = message.getFrom();

        // Get the CSeq value
        long cseq = message.getCSeq();

        // Set the route path with the Record-Route
        Vector<String> route = SipUtils.routeProcessing(message, false);

        // Create a dialog path
        SipDialogPath dialogPath = new SipDialogPath(sipstack,
                callId, cseq, target, localParty, remoteParty,
                route, getUserProfile());

        // Set the message request
        dialogPath.setMessage(message);

        // Set the remote tag
        dialogPath.setRemoteTag(message.getFromTag());

        // Set the remote content part
        dialogPath.setRemoteContent(message.getContent());

        return dialogPath;
    }

	public String getLocalAddress() {
		return localAddress;
	}

	public boolean isReady() {
		return sipstack != null;
	}

}
