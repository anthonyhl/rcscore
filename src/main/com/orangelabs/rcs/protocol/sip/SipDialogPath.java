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
import gov2.nist.core.NameValue;
import gov2.nist.javax2.sip.Utils;
import gov2.nist.javax2.sip.header.Subject;

import java.util.ArrayList;
import java.util.Vector;

import javax2.sip.ClientTransaction;
import javax2.sip.Dialog;
import javax2.sip.InvalidArgumentException;
import javax2.sip.address.Address;
import javax2.sip.address.URI;
import javax2.sip.header.AcceptHeader;
import javax2.sip.header.CSeqHeader;
import javax2.sip.header.CallIdHeader;
import javax2.sip.header.ContactHeader;
import javax2.sip.header.ContentDispositionHeader;
import javax2.sip.header.ContentLengthHeader;
import javax2.sip.header.ContentTypeHeader;
import javax2.sip.header.EventHeader;
import javax2.sip.header.ExpiresHeader;
import javax2.sip.header.FromHeader;
import javax2.sip.header.Header;
import javax2.sip.header.MaxForwardsHeader;
import javax2.sip.header.ReasonHeader;
import javax2.sip.header.ReferToHeader;
import javax2.sip.header.RequireHeader;
import javax2.sip.header.RouteHeader;
import javax2.sip.header.SIPIfMatchHeader;
import javax2.sip.header.SupportedHeader;
import javax2.sip.header.ToHeader;
import javax2.sip.header.UserAgentHeader;
import javax2.sip.header.ViaHeader;
import javax2.sip.message.Message;
import javax2.sip.message.Request;
import javax2.sip.message.Response;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.SessionAuthenticationAgent;
import com.orangelabs.rcs.core.SessionTimerManager;
import com.orangelabs.rcs.platform.registry.RegistryFactory;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.logger.Logger;


/**
 * SIP dialog path. A dialog path corresponds to a SIP session, for
 * example from the INVITE to the BYE.
 * 
 * @author JM. Auffret
 */
public class SipDialogPath {
	/**
	 * Last min session expire period key
	 */
	private static final String REGISTRY_MIN_SESSION_EXPIRE_PERIOD = "MinSessionExpirePeriod";

	/**
	 * SIP stack interface
	 */
	private SipInterface stack = null;
	
	/**
	 * Call-Id
	 */
	private String callId = null;

	/**
	 * CSeq number
	 */
	private long cseq = 1;

	/**
	 * Local tag
	 */
	private String localTag = IdGenerator.getIdentifier();

	/**
	 * Remote tag
	 */
	private String remoteTag = null;

	/**
	 * Target
	 */
	private String target = null;

	/**
	 * Local party
	 */
	private String localParty = null;

	/**
	 * Remote party
	 */
	private String remoteParty = null;

	/**
	 * Initial INVITE request
	 */
	private SipRequest invite = null;

	/**
	 * Local content
	 */
	private String localContent = null;

	/**
	 * Remote content
	 */
	private String remoteContent = null;

    /**
     * Remote sip instance
     */
    private String remoteSipInstance = null;

	/**
	 * Route path
	 */
	private Vector<String> route = null;

	/**
	 * Authentication agent
	 */
	private SessionAuthenticationAgent authenticationAgent = null;

	/**
	 * Session expire time 
	 */
	private int sessionExpireTime; 
	
	/**
	 * Flag that indicates if the signalisation is established or not
	 */
	private boolean sigEstablished = false;

	/**
	 * Flag that indicates if the session (sig + media) is established or not
	 */
	private boolean sessionEstablished = false;

	/**
	 * Flag that indicates if the session has been cancelled by the end-user
	 */
	private boolean sessionCancelled = false;

	/**
	 * Flag that indicates if the session has been terminated by the server
	 */
	private boolean sessionTerminated = false;

	/**
	 * Session termination reason code
	 */
	private int sessionTerminationReasonCode = -1;
	
	/**
	 * Session termination reason phrase
	 */
	private String sessionTerminationReasonPhrase = null;
	
    Logger logger = Logger.getLogger(SipDialogPath.class.getName());

	private UserProfile user;

	/**
	 * Constructor
	 * 
	 * @param stack SIP stack interface
	 * @param callId Call-Id
	 * @param cseq CSeq
	 * @param target Target
	 * @param localParty Local party
	 * @param remoteParty Remote party
	 * @param route Route path
	 */
	public SipDialogPath(SipInterface stack,
			String callId,
			long cseq,
			String target,
			String localParty,
			String remoteParty,
			Vector<String> route, UserProfile user) {
		this.stack = stack;
		this.callId = callId;
		this.cseq = cseq;
		this.target = SipUtils.extractUriFromAddress(target);
		this.localParty = localParty;
		this.remoteParty = remoteParty;
		this.route = route;
		this.user = user;
		
    	int defaultExpireTime = RcsSettings.getInstance().getSessionRefreshExpirePeriod();
    	int minExpireValue = RegistryFactory.getFactory().readInteger(REGISTRY_MIN_SESSION_EXPIRE_PERIOD, -1);
    	if ((defaultExpireTime > SessionTimerManager.MIN_EXPIRE_PERIOD) && (minExpireValue != -1) && (defaultExpireTime < minExpireValue)) {
        	this.sessionExpireTime = minExpireValue;
    	} else {
    		this.sessionExpireTime = defaultExpireTime;
    	}
	}

	/**
	 * Constructor<br>
	 * Perform a deep copy of the dialogPath
	 * 
	 * @param dialogPath
	 */
	public SipDialogPath(SipDialogPath dialogPath) {
		stack = dialogPath.getSipStack();
		callId = dialogPath.getCallId();
		cseq = dialogPath.getCseq();
		localTag = dialogPath.getLocalTag();
		remoteTag = dialogPath.getRemoteTag();
		target = dialogPath.getTarget();
		localParty = dialogPath.getLocalParty();
		remoteParty = dialogPath.getRemoteParty();
		invite = dialogPath.getInvite();
		localContent = dialogPath.getLocalContent();
		remoteContent = dialogPath.getRemoteContent();
		remoteSipInstance = dialogPath.getRemoteSipInstance();
		route = dialogPath.getRoute();
		authenticationAgent = dialogPath.getAuthenticationAgent();
		sessionExpireTime = dialogPath.getSessionExpireTime();
		sigEstablished = dialogPath.isSigEstablished();
		sessionEstablished = dialogPath.isSessionEstablished();
		sessionCancelled = dialogPath.isSessionCancelled();
		sessionTerminated = dialogPath.isSessionTerminated();
		sessionTerminationReasonCode = dialogPath.getSessionTerminationReasonCode();
		sessionTerminationReasonPhrase = dialogPath.getSessionTerminationReasonPhrase();
	}
	    
	/**
	 * Get the current SIP stack interface
	 * 
	 * @return SIP stack interface
	 */
	public SipInterface getSipStack() {
		return stack;
	}
	
	/**
	 * Get the target of the dialog path
	 * 
	 * @return String
	 */
	public String getTarget() {
		return target;
	}

	/**
	 * Set the target of the dialog path
	 * 
	 * @param tg Target address
	 */
	public void setTarget(String tg) {
		target = tg;
	}

	/**
	 * Get the local party of the dialog path
	 * 
	 * @return String
	 */
	public String getLocalParty() {
		return localParty;
	}

	/**
	 * Get the remote party of the dialog path
	 * 
	 * @return String
	 */
	public String getRemoteParty() {
		return remoteParty;
	}

	/**
	 * Get the local tag of the dialog path
	 * 
	 * @return String
	 */
	public String getLocalTag() {
		return localTag;
	}

	/**
	 * Get the remote tag of the dialog path
	 * 
	 * @return String
	 */
	public String getRemoteTag() {
		return remoteTag;
	}

	/**
	 * Set the remote tag of the dialog path
	 * 
	 * @param tag Remote tag
	 */
	public void setRemoteTag(String tag) {
		remoteTag = tag;
	}

	/**
	 * Get the call-id of the dialog path
	 * 
	 * @return String
	 */
	public String getCallId() {
		return callId;
	}

    /**
     * Set the call-id of the dialog path
     *
     * @return String
     */
    public void setCallId(String callId) {
        this.callId = callId;
    }

	/**
	 * Return the Cseq number of the dialog path
	 * 
	 * @return Cseq number
	 */
	public long getCseq() {
		return cseq;
	}

	/**
	 * Increment the Cseq number of the dialog path
	 */
	public void incrementCseq() {
		cseq++;
		
		// Increment internal stack CSeq if terminating side (NIST stack issue?)
		Dialog dlg = getStackDialog();
		if ((dlg != null) && dlg.isServer()) {
			dlg.incrementLocalSequenceNumber();
		}
	}

	/**
	 * Get the initial INVITE request of the dialog path
	 * 
	 * @return SipRequest INVITE request
	 */
	public SipRequest getInvite() {
		return invite;
	}

	/**
	 * Set the initial INVITE request of the dialog path
	 * 
	 * @param invite INVITE request
	 */
	public void setInvite(SipRequest invite) {
		this.invite = invite;
	}
		
	/**
	 * Returns the local content
	 * 
	 * @return String
	 */
	public String getLocalContent() {
		return localContent;
	}

	/**
	 * Returns the remote content
	 * 
	 * @return String
	 */
	public String getRemoteContent() {
		return remoteContent;
	}

	/**
	 * Sets the local content
	 * 
	 * @param local Local content
	 */
	public void setLocalContent(String local) {
		this.localContent = local;
	}

    /**
     * Returns the remote SIP instance ID
     *
     * @return String
     */
    public String getRemoteSipInstance() {
        return remoteSipInstance;
    }

    /**
     * Sets the remote SIP instance ID
     *
     * @param instanceId SIP instance ID
     */
    public void setRemoteSipInstance(String instanceId) {
        this.remoteSipInstance = instanceId;
    }

	/**
	 * Sets the remote content
	 * 
	 * @param remote Remote content
	 */
	public void setRemoteContent(String remote) {
		this.remoteContent = remote;
	}

	/**
	 * Returns the route path
	 * 
	 * @return Vector of string
	 */
	public Vector<String> getRoute() {
		return route;
	}

	/**
	 * Set the route path
	 * 
	 * @param route New route path
	 */
	public void setRoute(Vector<String> route) {
		this.route = route;
	}
	
	/**
	 * Is session cancelled
	 * 
	 * @return Boolean
	 */
	public boolean isSessionCancelled() {
		return sessionCancelled;
	}
	
	/**
	 * The session has been cancelled
	 */
	public synchronized void sessionCancelled() {
		this.sessionCancelled = true;
	}
	
	/**
	 * Is session established
	 * 
	 * @return Boolean
	 */
	public boolean isSessionEstablished() {
		return sessionEstablished;
	}
	
	/**
	 * Session is established
	 */
	public synchronized void sessionEstablished() {
		this.sessionEstablished = true;
	}
	
	/**
	 * Is session terminated
	 * 
	 * @return Boolean
	 */
	public boolean isSessionTerminated() {
		return sessionTerminated;
	}
	
	/**
	 * Session is terminated
	 */
	public synchronized void sessionTerminated() {
		this.sessionTerminated = true;
		this.sessionTerminationReasonCode = -1;
		this.sessionTerminationReasonPhrase = null;
	}
	
	/**
	 * Session is terminated with a specific reason code
	 * 
	 * @param reason Reason code
	 * @param phrase Reason phrase
	 */
	public synchronized void sessionTerminated(int code, String phrase) {
		this.sessionTerminated = true;
		this.sessionTerminationReasonCode = code;
		this.sessionTerminationReasonPhrase = phrase;
	}

	/**
	 * Get session termination reason code
	 * 
	 * @return Reason code
	 */
	public int getSessionTerminationReasonCode() {
		return sessionTerminationReasonCode;
	}

	/**
	 * Get session termination reason phrase
	 * 
	 * @return Reason phrase
	 */
	public String getSessionTerminationReasonPhrase() {
		return sessionTerminationReasonPhrase;
	}

	/**
	 * Is signalisation established with success
	 * 
	 * @return Boolean
	 */
	public boolean isSigEstablished() {
		return sigEstablished;
	}
	
	/**
	 * Signalisation is established with success
	 */
	public synchronized void sigEstablished() {
		this.sigEstablished = true;
	}

	/**
	 * Set the session authentication agent
	 * 
	 * @param agent Authentication agent
	 */
	public void setAuthenticationAgent(SessionAuthenticationAgent agent) {
		this.authenticationAgent = agent;
	}
	
	/**
	 * Returns the session authentication agent
	 * 
	 * @return Authentication agent
	 */
	public SessionAuthenticationAgent getAuthenticationAgent() {
		return authenticationAgent;
	}

	/**
	 * Returns the session expire value
	 * 
	 * @return Session expire time in seconds
	 */
	public int getSessionExpireTime() {
		return sessionExpireTime;
	}

	/**
	 * Set the session expire value
	 * 
	 * @param sessionExpireTime Session expire time in seconds
	 */
	public void setSessionExpireTime(int sessionExpireTime) {
		this.sessionExpireTime = sessionExpireTime;
	}
	
	/**
	 * Set the min session expire value
	 * 
	 * @param sessionExpireTime Session expire time in seconds
	 */
	public void setMinSessionExpireTime(int sessionExpireTime) {
		RegistryFactory.getFactory().writeInteger(REGISTRY_MIN_SESSION_EXPIRE_PERIOD, sessionExpireTime);		
	}
	
	/**
	 * Get stack dialog
	 * 
	 * @return Dialog or null
	 */
	public Dialog getStackDialog() {
		if (invite != null) {
			return invite.getStackTransaction().getDialog();
		} else {
			return null;
		}
	}

	public void sendSipAck() throws SipException {
		if (stack != null) {
			stack.sendSipAck(this);
		} else {
			throw new SipException("Stack not initialized");
		}
		
	}

	public void sendSipBye() throws SipException {
		if (stack != null) {
			stack.sendSipBye(this);
		} else {
			throw new SipException("Stack not initialized");
		}
		
	}

	public void sendSipCancel() throws SipException {
		if (stack != null) {
			stack.sendSipCancel(this);
		} else {
			throw new SipException("Stack not initialized");
		}
		
	}

	public SipRequest createRegister(String[] featureTags, int expirePeriod,
			String instanceId) throws SipException {  	
    	try {
	        // Set request line header
	        URI requestURI = SipInterface.ADDR_FACTORY.createURI(this .getTarget());

	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());

	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.REGISTER);

	        // Set the From header
	        Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
	        FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress,
	        		IdGenerator.getIdentifier());

	        // Set the To header
	        Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
	        ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, null);

			// Insert "keep" flag to Via header (RFC6223 "Indication of Support for Keep-Alive")
			ArrayList<ViaHeader> viaHeaders = this.getSipStack().getViaHeaders();
			if (viaHeaders != null && !viaHeaders.isEmpty()) {
				ViaHeader viaHeader = viaHeaders.get(0);
				viaHeader.setParameter(new NameValue("keep", null, true));
			}

	        // Create the request
	        Request register = SipInterface.MSG_FACTORY.createRequest(requestURI,
	                Request.REGISTER,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					SipDialogPath.buildMaxForwardsHeader());

	        // Set Contact header
	        ContactHeader contact = this.getSipStack().getLocalContact();
	        if (instanceId != null) {
	        	contact.setParameter(SipUtils.SIP_INSTANCE_PARAM, instanceId);
	        }
	        register.addHeader(contact);

	        // Set Supported header
	        String supported;
	        if (instanceId != null) {
	        	supported = "path, gruu";
	        } else {
	        	supported = "path";
	        }
	        SupportedHeader supportedHeader = SipInterface.HEADER_FACTORY.createSupportedHeader(supported);
	        register.addHeader(supportedHeader);

            // Set feature tags
            SipUtils.setContactFeatureTags(register, featureTags);

            // Set Allow header
	        SipDialogPath.buildAllowHeader(register);

	        // Set the Route header
        	Vector<String> route = this.getSipStack().getDefaultRoutePath();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	register.addHeader(routeHeader);
	        }

	        // Set the Expires header
	        ExpiresHeader expHeader = SipInterface.HEADER_FACTORY.createExpiresHeader(expirePeriod);
	        register.addHeader(expHeader);

	        // Set User-Agent header
	        register.addHeader(SipUtils.buildUserAgentHeader());

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)register.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(register);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP REGISTER message");
		}
	}
	
	public String getPreferredUri() {
		return user.getPreferredUri();
	}

	public SipRequest createSubscribe(int expirePeriod) throws SipException {
		
    	try {
	        // Set request line header
	        URI requestURI = SipInterface.ADDR_FACTORY.createURI(this.getTarget());

	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());

	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.SUBSCRIBE);

	        // Set the From header
	        Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
	        FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress, this.getLocalTag());

	        // Set the To header
	        Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
	        ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, this.getRemoteTag());

	        // Create the request
	        Request subscribe = SipInterface.MSG_FACTORY.createRequest(requestURI,
	                Request.SUBSCRIBE,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					this.getSipStack().getViaHeaders(),
					SipDialogPath.buildMaxForwardsHeader());

	        // Set the Route header
	        Vector<String> route = this.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	subscribe.addHeader(routeHeader);
	        }

	        // Set the Expires header
	        ExpiresHeader expHeader = SipInterface.HEADER_FACTORY.createExpiresHeader(expirePeriod);
	        subscribe.addHeader(expHeader);

	        // Set User-Agent header
	        subscribe.addHeader(SipUtils.buildUserAgentHeader());

	        // Set Contact header
	        subscribe.addHeader(this.getSipStack().getContact());

	        // Set Allow header
	        SipDialogPath.buildAllowHeader(subscribe);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)subscribe.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(subscribe);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP SUBSCRIBE message");
		}
	}

	/**
	 * Create a SUBSCRIBE request
	 * 
	 * @return SIP request
	 * @throws SipException
	 * @throws CoreException
	 */
	public SipRequest createSubscribe() throws SipException, CoreException {
		SipRequest subscribe = createSubscribe(0);
		
		// Set the Privacy header
		subscribe.addHeader(SipUtils.HEADER_PRIVACY, "id");
		
		// Set the Event header
		subscribe.addHeader(EventHeader.NAME, "presence");
	
		// Set the Accept header
		subscribe.addHeader(AcceptHeader.NAME, "application/pidf+xml");
		
		return subscribe;
	}

	public SipRequest createMessage(String featureTag, String contentType,
			byte[] content) throws SipException {

		try {
	        // Set request line header
	        URI requestURI = SipInterface.ADDR_FACTORY.createURI(this.getTarget());

	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());

	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.MESSAGE);

	        // Set the From header
	        Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
	        FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress, this.getLocalTag());

	        // Set the To header
	        Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
	        ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, this.getRemoteTag());

	        // Create the request
	        Request message = SipInterface.MSG_FACTORY.createRequest(requestURI,
	                Request.MESSAGE,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					this.getSipStack().getViaHeaders(),
					SipDialogPath.buildMaxForwardsHeader());

	        // Set the Route header
	        Vector<String> route = this.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	message.addHeader(routeHeader);
	        }

	        // Set the P-Preferred-Identity header
	        if (getPreferredUri() != null) {
	        	Header prefHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, getPreferredUri());
	        	message.addHeader(prefHeader);
	        }

	        // Set Contact header
			message.addHeader(this.getSipStack().getContact());

	        // Set User-Agent header
	        message.addHeader(SipUtils.buildUserAgentHeader());

	        // Set feature tags
	        if (featureTag != null) {
	        	SipUtils.setFeatureTags(message, new String [] { featureTag });
	        }

	        // Set the message content
	        String[] type = contentType.split("/");
			ContentTypeHeader contentTypeHeader = SipInterface.HEADER_FACTORY.createContentTypeHeader(type[0], type[1]);
	        message.setContent(content, contentTypeHeader);

	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY.createContentLengthHeader(content.length);
			message.setContentLength(contentLengthHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)message.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(message, this.getRemoteSipInstance());

            return new SipRequest(message);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP MESSAGE message");
		}
	}

	public SipRequest createPublish(int expirePeriod, String entityTag,
			String sdp) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipInterface.ADDR_FACTORY.createURI(this.getTarget());

	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());

	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.PUBLISH);

	        // Set the From header
	        Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
	        FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress, this.getLocalTag());

	        // Set the To header
	        Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
	        ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, this.getRemoteTag());

	        // Create the request
	        Request publish = SipInterface.MSG_FACTORY.createRequest(requestURI,
	                Request.PUBLISH,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					this.getSipStack().getViaHeaders(),
					SipDialogPath.buildMaxForwardsHeader());

	        // Set the Route header
	        Vector<String> route = this.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	publish.addHeader(routeHeader);
	        }

	        // Set the Expires header
	        ExpiresHeader expHeader = SipInterface.HEADER_FACTORY.createExpiresHeader(expirePeriod);
	        publish.addHeader(expHeader);

        	// Set the SIP-If-Match header
	        if (entityTag != null) {
	        	Header sipIfMatchHeader = SipInterface.HEADER_FACTORY.createHeader(SIPIfMatchHeader.NAME, entityTag);
	        	publish.addHeader(sipIfMatchHeader);
	        }

	        // Set User-Agent header
	        publish.addHeader(SipUtils.buildUserAgentHeader());

	    	// Set the Event header
	    	publish.addHeader(SipInterface.HEADER_FACTORY.createHeader(EventHeader.NAME, "presence"));

	        // Set the message content
	    	if (sdp != null) {
	    		ContentTypeHeader contentTypeHeader = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "pidf+xml");
	    		publish.setContent(sdp, contentTypeHeader);
	    	}

    		// Set the message content length
	    	int length = sdp == null ? 0 : sdp.getBytes(UTF8).length;
    		ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY.createContentLengthHeader(length);
    		publish.setContentLength(contentLengthHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)publish.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(publish);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP PUBLISH message");
		}
	}

	public SipRequest createInvite(String[] featureTags, String[] acceptTags,
			String content, ContentTypeHeader contentType) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipInterface.ADDR_FACTORY.createURI(this.getTarget());

	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());

	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.INVITE);

	        // Set the From header
	        Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
	        FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress, this.getLocalTag());

	        // Set the To header
	        Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
	        ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, null);

	        // Insert "keep" flag to Via header (RFC6223 "Indication of Support for Keep-Alive")
			ArrayList<ViaHeader> viaHeaders = this.getSipStack().getViaHeaders();
			if (viaHeaders != null && !viaHeaders.isEmpty()) {
				ViaHeader viaHeader = viaHeaders.get(0);
				viaHeader.setParameter(new NameValue("keep", null, true));
			}

	        // Create the request
	        Request invite = SipInterface.MSG_FACTORY.createRequest(requestURI,
	                Request.INVITE,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					SipDialogPath.buildMaxForwardsHeader());

	        // Set Contact header
	        invite.addHeader(this.getSipStack().getContact());

	        // Set feature tags
	        SipUtils.setFeatureTags(invite, featureTags, acceptTags);

            // Set Allow header
	        SipDialogPath.buildAllowHeader(invite);

			// Set the Route header
	        Vector<String> route = this.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	invite.addHeader(routeHeader);
	        }

	        // Set the P-Preferred-Identity header
	        if (getPreferredUri() != null) {
				Header prefHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, getPreferredUri());
				invite.addHeader(prefHeader);
	        }

			// Set User-Agent header
	        invite.addHeader(SipUtils.buildUserAgentHeader());

			// Add session timer management
			if (this.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
		        // Set the Supported header
				Header supportedHeader = SipInterface.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
				invite.addHeader(supportedHeader);

				// Set Session-Timer headers
				Header sessionExpiresHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
						""+this.getSessionExpireTime());
				invite.addHeader(sessionExpiresHeader);
			}

			// Set the message content
	        invite.setContent(content, contentType);

	        // Set the content length
			ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY
					.createContentLengthHeader(content.getBytes(UTF8).length);
			invite.setContentLength(contentLengthHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)invite.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(invite);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP INVITE message");
		}
	}

	public SipResponse create200OkInviteResponse(String[] featureTags,
			String[] acceptContactTags, String sdp) throws SipException {
		try {
			// Create the response
			Response response = SipInterface.MSG_FACTORY.createResponse(200, (Request)this.getInvite().getStackMessage());

			// Set the local tag
			ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
			to.setTag(this.getLocalTag());

	        // Set Contact header
	        response.addHeader(this.getSipStack().getContact());

	        // Set feature tags
 	        SipUtils.setFeatureTags(response, featureTags, acceptContactTags);

            // Set Allow header
	        SipDialogPath.buildAllowHeader(response);

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());

			// Add session timer management
			if (this.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
				// Set the Require header
		    	Header requireHeader = SipInterface.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
				response.addHeader(requireHeader);

				// Set Session-Timer header
				Header sessionExpiresHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
						this.getSessionExpireTime() + ";refresher=" + this.getInvite().getSessionTimerRefresher());
				response.addHeader(sessionExpiresHeader);
			}

	        // Set the message content
			ContentTypeHeader contentTypeHeader = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
			response.setContent(sdp, contentTypeHeader);

	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY
					.createContentLengthHeader(sdp.getBytes(UTF8).length);
			response.setContentLength(contentLengthHeader);

			SipResponse resp = new SipResponse(response);
			resp.setStackTransaction(this.getInvite().getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	public SipRequest createAck() throws SipException {
		try {
            Request ack = null;

            // Set request line header
            URI requestURI = SipInterface.ADDR_FACTORY.createURI(this .getTarget());

            // Set Call-Id header
            CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());

            // Set the CSeq header
            CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.ACK);

            // Set the From header
            Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
            FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress, this.getLocalTag());

            // Set the To header
            Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
            ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, this.getRemoteTag());

            // Set the Via branch
            ArrayList<ViaHeader> vias = this.getSipStack().getViaHeaders();
            vias.get(0).setBranch(Utils.getInstance().generateBranchId());

            // Create the ACK request
            ack = SipInterface.MSG_FACTORY.createRequest(requestURI,
                    Request.ACK,
                    callIdHeader,
                    cseqHeader,
                    fromHeader,
                    toHeader,
                    vias,
                    SipDialogPath.buildMaxForwardsHeader());


            // Set the Route header
            Vector<String> route = this.getRoute();
            for(int i=0; i < route.size(); i++) {
                Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
                ack.addHeader(routeHeader);
            }

            // Set Contact header
            ack.addHeader(this.getSipStack().getContact());

            // Set User-Agent header
            ack.addHeader(SipUtils.buildUserAgentHeader());

            // Set Allow header
            SipDialogPath.buildAllowHeader(ack);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)ack.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(ack);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP ACK message");
		}
	}

	public SipRequest createOptions(String[] featureTags) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipInterface.ADDR_FACTORY.createURI(this.getTarget());
	
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipInterface.HEADER_FACTORY.createCallIdHeader(this.getCallId());
	
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), Request.OPTIONS);
	
	        // Set the From header
	        Address fromAddress = SipInterface.ADDR_FACTORY.createAddress(this.getLocalParty());
	        FromHeader fromHeader = SipInterface.HEADER_FACTORY.createFromHeader(fromAddress, this.getLocalTag());
	
	        // Set the To header
	        Address toAddress = SipInterface.ADDR_FACTORY.createAddress(this.getRemoteParty());
	        ToHeader toHeader = SipInterface.HEADER_FACTORY.createToHeader(toAddress, null);
	
			// Create the request
	        Request options = SipInterface.MSG_FACTORY.createRequest(requestURI,
	                Request.OPTIONS,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					this.getSipStack().getViaHeaders(),
					buildMaxForwardsHeader());
	
			// Set Contact header
	        options.addHeader(this.getSipStack().getContact());
	
	        // Set Accept header
	    	Header acceptHeader = SipInterface.HEADER_FACTORY.createHeader(AcceptHeader.NAME, "application/sdp");
			options.addHeader(acceptHeader);
	
			// Set feature tags
	        SipUtils.setFeatureTags(options, featureTags);
	
	        // Set Allow header
	        SipDialogPath.buildAllowHeader(options);
	
	        // Set the Route header
	        Vector<String> route = this.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipInterface.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	options.addHeader(routeHeader);
	        }
	
	        // Set the P-Preferred-Identity header
	        if (getPreferredUri() != null) {
	        	Header prefHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, getPreferredUri());
	        	options.addHeader(prefHeader);
	        }
	
			// Set User-Agent header
	        options.addHeader(SipUtils.buildUserAgentHeader());
	
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)options.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
	
	        return new SipRequest(options);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP OPTIONS message");
		}
	}

	/**
	 * Build Max-Forwards header
	 * 
	 * @return Header
	 * @throws InvalidArgumentException
	 */
	public static MaxForwardsHeader buildMaxForwardsHeader() throws InvalidArgumentException {
		return SipInterface.HEADER_FACTORY.createMaxForwardsHeader(70);	
	}

	public SipRequest createInvite(String[] featureTags, String[] acceptTags,
			String sdp) throws SipException {
    	try {
			// Create the content type
			ContentTypeHeader contentType = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "sdp");

	        // Create the request
			return createInvite(featureTags, acceptTags, sdp, contentType);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP INVITE message");
		}
	}

	public SipRequest createMultipartInvite(String[] featureTags,
			String[] acceptTags, String multipart, String boundary) throws SipException {
    	
		try {
			// Create the content type
			ContentTypeHeader contentType = SipInterface.HEADER_FACTORY.createContentTypeHeader("multipart", "mixed");
			contentType.setParameter("boundary", boundary);

	        // Create the request
			return createInvite(featureTags, acceptTags, multipart, contentType);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP INVITE message");
		}
	}

	public SipRequest createBye() throws SipException {

		try {
			// Create the request
			Request bye = getStackDialog().createRequest(Request.BYE);

			// Set termination reason
			int reasonCode = getSessionTerminationReasonCode();
			if (reasonCode != -1) {
				ReasonHeader reasonHeader = SipInterface.HEADER_FACTORY.createReasonHeader("SIP",
						reasonCode, getSessionTerminationReasonPhrase());
				bye.addHeader(reasonHeader);
			}

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)bye.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(bye);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP BYE message");
		}
	}

	public SipRequest createCancel() throws SipException {
		
		try {
	        // Create the request
		    ClientTransaction transaction = (ClientTransaction)getInvite().getStackTransaction();
		    Request cancel = transaction.createCancel();

			// Set termination reason
			int reasonCode = getSessionTerminationReasonCode();
			if (reasonCode != -1) {
				ReasonHeader reasonHeader = SipInterface.HEADER_FACTORY.createReasonHeader("SIP",
						reasonCode, getSessionTerminationReasonPhrase());
				cancel.addHeader(reasonHeader);
			}

			// Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)cancel.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

			return new SipRequest(cancel);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP BYE message");
		}
	}

	public SipRequest createRefer(String toContact, String subject,
			String contributionId) throws SipException {
		
    	try {
			// Create the request
		    Request refer = getStackDialog().createRequest(Request.REFER);

            // Set feature tags
	        String[] tags = {FeatureTags.FEATURE_OMA_IM};
            SipUtils.setFeatureTags(refer, tags);

	        // Set Refer-To header
	        Header referTo = SipInterface.HEADER_FACTORY.createHeader(ReferToHeader.NAME, toContact);
	        refer.addHeader(referTo);

			// Set Refer-Sub header
	        Header referSub = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_REFER_SUB, "false");
	        refer.addHeader(referSub);

	        // Set the P-Preferred-Identity header
	        if (getPreferredUri() != null) {
	        	Header prefHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, getPreferredUri());
	        	refer.addHeader(prefHeader);
	        }

	        // Set Subject header
            if (subject != null) {
                Header sub = SipInterface.HEADER_FACTORY.createHeader(Subject.NAME, subject);
                refer.addHeader(sub);
            }

			// Set Contribution-ID header
			Header cid = SipInterface.HEADER_FACTORY.createHeader(SipRequest.HEADER_CONTRIBUTION_ID, contributionId);
	        refer.addHeader(cid);

			// Set User-Agent header
	        refer.addHeader(SipUtils.buildUserAgentHeader());

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)refer.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(refer, getRemoteSipInstance());

            return new SipRequest(refer);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP REFER message");
		}
	}

	public SipRequest createReferResource(String resourceList, String subject,
			String contributionId) throws SipException {
		try {
			// Create the request
		    Request refer = getStackDialog().createRequest(Request.REFER);

	        // Generate a list URI
			String listID = "Id_" + System.currentTimeMillis();

            // Set feature tags
	        String[] tags = {FeatureTags.FEATURE_OMA_IM};
            SipUtils.setFeatureTags(refer, tags);

	        // Set Require header
            Header require = SipInterface.HEADER_FACTORY.createHeader(RequireHeader.NAME, "multiple-refer");
            refer.addHeader(require);
            require = SipInterface.HEADER_FACTORY.createHeader(RequireHeader.NAME, "norefersub");
            refer.addHeader(require);

	        // Set Refer-To header
	        Header referTo = SipInterface.HEADER_FACTORY.createHeader(ReferToHeader.NAME,
	        		"<cid:" + listID + "@" + user.getHomeDomain() + ">");
	        refer.addHeader(referTo);

			// Set Refer-Sub header
	        Header referSub = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_REFER_SUB, "false");
	        refer.addHeader(referSub);

	        // Set the P-Preferred-Identity header
	        if (getPreferredUri() != null) {
	        	Header prefHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, getPreferredUri());
	        	refer.addHeader(prefHeader);
	        }

	        // Set Subject header
			Header s = SipInterface.HEADER_FACTORY.createHeader(Subject.NAME, subject);
			refer.addHeader(s);

			// Set Contribution-ID header
			Header cid = SipInterface.HEADER_FACTORY.createHeader(SipRequest.HEADER_CONTRIBUTION_ID, contributionId);
	        refer.addHeader(cid);

			// Set User-Agent header
	        refer.addHeader(SipUtils.buildUserAgentHeader());

	        // Set the Content-ID header
			Header contentIdHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_CONTENT_ID,
					"<" + listID + "@" + user.getHomeDomain() + ">");
			refer.addHeader(contentIdHeader);


			// Set the message content
			ContentTypeHeader contentTypeHeader = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "resource-lists+xml");
			refer.setContent(resourceList, contentTypeHeader);

	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY
					.createContentLengthHeader(resourceList.getBytes(UTF8).length);
			refer.setContentLength(contentLengthHeader);

			// Set the Content-Disposition header
	        Header contentDispoHeader = SipInterface.HEADER_FACTORY.createHeader(ContentDispositionHeader.NAME, "recipient-list");
	        refer.addHeader(contentDispoHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)refer.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(refer, getRemoteSipInstance());

            return new SipRequest(refer);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP REFER message");
		}
	}

	public SipRequest createReInvite() throws SipException {
    	try {
            // Build the request
            Request reInvite = this.getStackDialog().createRequest(Request.INVITE);
            SipRequest firstInvite = this.getInvite();

            // Set feature tags
            reInvite.removeHeader(ContactHeader.NAME);
            reInvite.addHeader(firstInvite.getHeader(ContactHeader.NAME));
            reInvite.removeHeader(SipUtils.HEADER_ACCEPT_CONTACT);
			reInvite.addHeader(firstInvite.getHeader(SipUtils.HEADER_ACCEPT_CONTACT));

            // Set Allow header
            SipDialogPath.buildAllowHeader(reInvite);

            // Set the Route header
            reInvite.addHeader(firstInvite.getHeader(RouteHeader.NAME));

            // Set the P-Preferred-Identity header
            reInvite.addHeader(firstInvite.getHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY));

            // Set User-Agent header
            reInvite.addHeader(firstInvite.getHeader(UserAgentHeader.NAME));

            // Add session timer management
            if (this.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
                // Set the Supported header
                Header supportedHeader = SipInterface.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
                reInvite.addHeader(supportedHeader);

                // Set Session-Timer headers
                Header sessionExpiresHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
                        ""+this.getSessionExpireTime());
                reInvite.addHeader(sessionExpiresHeader);
            }

            // Set "rport" (RFC3581)
            ViaHeader viaHeader = (ViaHeader)reInvite.getHeader(ViaHeader.NAME);
            viaHeader.setRPort();

            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(firstInvite.getStackMessage(), this.getRemoteSipInstance());

            return new SipRequest(reInvite);
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP RE-INVITE message");
        }
	}

	public SipRequest createReInvite(String[] featureTags, String content) throws SipException {
    	try {
            // Build the request
    		String invite = Request.INVITE;
			Request reInvite = this.getStackDialog().createRequest(invite);
            SipRequest firstInvite = this.getInvite();

           	// Set the CSeq header
	        CSeqHeader cseqHeader = SipInterface.HEADER_FACTORY.createCSeqHeader(this.getCseq(), invite);
            reInvite.removeHeader(CSeqHeader.NAME);
            reInvite.addHeader(cseqHeader);

            // Set Contact header
            reInvite.removeHeader(ContactHeader.NAME);
            reInvite.removeHeader(SipUtils.HEADER_ACCEPT_CONTACT);
	        reInvite.addHeader(this.getSipStack().getContact());

			// Set feature tags
			SipUtils.setFeatureTags(reInvite, featureTags);

            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(firstInvite.getStackMessage(), this.getRemoteSipInstance());

            // Set Allow header
            SipDialogPath.buildAllowHeader(reInvite);

            // Set the Route header
            if (reInvite.getHeader(RouteHeader.NAME) == null && firstInvite.getHeader(RouteHeader.NAME) != null) {
                reInvite.addHeader(firstInvite.getHeader(RouteHeader.NAME));
            }

            // Set the P-Preferred-Identity header
            if (firstInvite.getHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY) != null){
            	reInvite.addHeader(firstInvite.getHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY));
            }
            else if (getPreferredUri() != null) {
	        	Header prefHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, getPreferredUri());
	        	reInvite.addHeader(prefHeader);
	        }

            // Set User-Agent header
            reInvite.addHeader(firstInvite.getHeader(UserAgentHeader.NAME));

            // Add session timer management
            if (this.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
                // Set the Supported header
                Header supportedHeader = SipInterface.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
                reInvite.addHeader(supportedHeader);

                // Set Session-Timer headers
                Header sessionExpiresHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
                        Integer.toString(this.getSessionExpireTime()));
                reInvite.addHeader(sessionExpiresHeader);
            }

            // Set "rport" (RFC3581)
            ViaHeader viaHeader = (ViaHeader)reInvite.getHeader(ViaHeader.NAME);
            viaHeader.setRPort();

            // Create the content type and set content
            ContentTypeHeader contentType = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
            reInvite.setContent(content, contentType);

     		// Set the content length
            ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY
                    .createContentLengthHeader(content.getBytes(UTF8).length);
            reInvite.setContentLength(contentLengthHeader);
            return new SipRequest(reInvite);
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP RE-INVITE message");
        }

	}
	
	public SipResponse create200OkReInviteResponse(SipRequest request,
			String[] featureTags, String content) throws SipException {
    	try {
			// Create the response
			Response response = SipInterface.MSG_FACTORY.createResponse(200, (Request)request.getStackMessage());

			// Set the local tag
			ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
			to.setTag(this.getLocalTag());

	        // Set Contact header
	        ContactHeader contact = this.getSipStack().getContact();
			response.addHeader(contact);

	        // Set feature tags
	        SipUtils.setFeatureTags(response, featureTags);

            // Set Allow header
	        SipDialogPath.buildAllowHeader(response);

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());

			// Add session timer management
			if (this.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
				// Set the Require header
		    	Header requireHeader = SipInterface.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
				response.addHeader(requireHeader);

				// Set Session-Timer header
				Header sessionExpiresHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
						this.getSessionExpireTime() + ";refresher=" + this.getInvite().getSessionTimerRefresher());
				response.addHeader(sessionExpiresHeader);
			}

	        // Set the message content
			ContentTypeHeader contentTypeHeader = SipInterface.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
			response.setContent(content, contentTypeHeader);

	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipInterface.HEADER_FACTORY
					.createContentLengthHeader(content.getBytes(UTF8).length);
			response.setContentLength(contentLengthHeader);

			SipResponse resp = new SipResponse(response);
			resp.setStackTransaction(request.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	public SipRequest createUpdate() throws SipException {
		try {
			// Create the request
		    Request update = getStackDialog().createRequest(Request.UPDATE);

	        // Set the Supported header
			Header supportedHeader = SipInterface.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
			update.addHeader(supportedHeader);

			// Add Session-Timer header
			Header sessionExpiresHeader = SipInterface.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES, ""+getSessionExpireTime());
			update.addHeader(sessionExpiresHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)update.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(update);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP UPDATE message");
		}
	}

	public SipResponse create200OkUpdateResponse(SipRequest request) throws SipException {
		try {
			// Create the response
			Response response = SipInterface.MSG_FACTORY.createResponse(200, (Request)request.getStackMessage());

	        // Set Contact header
	        response.addHeader(getSipStack().getContact());

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());

	        // Set the Require header
			Header requireHeader = SipInterface.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
			response.addHeader(requireHeader);

			// Add Session-Timer header
			Header sessionExpiresHeader = request.getHeader(SipUtils.HEADER_SESSION_EXPIRES);
			if (sessionExpiresHeader != null) {
				response.addHeader(sessionExpiresHeader);
			}

			SipResponse resp = new SipResponse(response);
			resp.setStackTransaction(request.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	/**
	 * Build Allow header
	 * 
	 * @param msg SIP message
	 * @throws Exception
	 */
	static void buildAllowHeader(Message msg) throws Exception {
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.INVITE));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.UPDATE));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.ACK));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.CANCEL));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.BYE));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.NOTIFY));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.OPTIONS));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.MESSAGE));
		msg.addHeader(SipInterface.HEADER_FACTORY.createAllowHeader(Request.REFER));
	}


    /**********************************************************************************
     * tct-stack add for CMCC message modes
     **********************************************************************************/

    /**
     * Initial MESSAGE request
     */
    private SipRequest message = null;

    /**
     * Set the initial MESSAGE request of the dialog path
     * 
     * @param invite MESSAGE request
     */
    public void setMessage(SipRequest message) {
        this.message = message;
    }

    /**
     * Get the initial MESSAGE request of the dialog path
     * 
     * @return SipRequest MESSAGE request
     */
    public SipRequest getMessage() {
        return message;
    }

}
