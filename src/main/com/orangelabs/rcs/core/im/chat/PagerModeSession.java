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

package com.orangelabs.rcs.core.im.chat;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax2.sip.header.RequireHeader;
import javax2.sip.message.Response;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.ImsServiceError;
import com.orangelabs.rcs.core.ImsSessionListener;
import com.orangelabs.rcs.core.SessionAuthenticationAgent;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnManager;
import com.orangelabs.rcs.core.im.chat.standalone.MessagingServiceIdentifier;
import com.orangelabs.rcs.core.network.SipManager;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * tct-stack
 * 
 * Pager mode session
 */
public abstract class PagerModeSession extends Thread {
    /**
     * Boundary tag
     */
    private final static String BOUNDARY_TAG = "boundary1";

    /**
     * IMS service
     */
    private ImsService imsService;

    /**
     * Remote contactId
     */
    private ContactId contact;

    /**
     * Remote contactUri
     */
    private String remoteUri;

    /**
     * Remote display name
     */
    private String remoteDisplayName;
    
    /**
     * List of participants
     */
    private Set<ParticipantInfo> participants;

    /**
     * First msg to send
     */
    private ChatMessage firstMsg;

    /**
     * Rcs settings
     */
    private final RcsSettings rcsSettings;

    /**
     * Dialog path
     */
    private SipDialogPath dialogPath;

    /**
     * Authentication agent
     */
    private SessionAuthenticationAgent authenticationAgent;

    /**
     * Session listeners
     */
    private Vector<ImsSessionListener> listeners = new Vector<ImsSessionListener>();

    /**
     * Ringing period (in seconds)
     */
    private int ringingPeriod = RcsSettings.getInstance().getRingingPeriod();

    /**
     * Conversation id identify all sessions between participants
     */
    private String conversationId;

    /**
     * Contribution id identify one session between participants
     */
    private String contributionId;

    /**
     * Flag to indicate whether it's a OTM originating pager mode session
     */
    private boolean isOTMSession;

    /**
     * Max chars limited in pager mode
     */
    public final static int MAX_CHAR = 900; // FOR CMCC S04

    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(PagerModeSession.class.getSimpleName());

    /**
	 * Constructor
	 * 
	 * @param imsService IMS service
	 * @param contact Remote contact Identifier
	 * @param remoteUri Remote URI
	 */
    public PagerModeSession(ImsService imsService, ContactId contact, String remoteUri,
            Set<ParticipantInfo> participants, ChatMessage firstMsg, RcsSettings rcsSettings) {
        this.imsService = imsService;
        this.contact = contact;
        this.remoteUri = remoteUri;
        this.participants = participants;
        this.firstMsg = firstMsg;
        this.rcsSettings = rcsSettings;
        this.authenticationAgent = new SessionAuthenticationAgent(imsService.getImsModule());
        if (participants.size() > 1) {
            isOTMSession = true;
        } else {
            isOTMSession = false;
        }
    }

    /**
     * Create originating dialog path
     */
    public void createOriginatingDialogPath() {

        dialogPath = getSipManager().createServiceDialogPath(remoteUri);

        // Set the authentication agent in the dialog path
        dialogPath.setAuthenticationAgent(getAuthenticationAgent());

        if (contact != null) {
            try {
                remoteDisplayName = ContactsManager.getInstance().getContactDisplayName(contact);
            } catch (Exception e) {
                // RCS account does not exist
            }
        }
    }

    /**
     * Create terminating dialog path
     * 
     * @param invite Incoming invite
     */
    public void createTerminatingDialogPath(SipRequest message) {
        dialogPath = getSipManager().createMessageDialogPath(message);

        remoteDisplayName = SipUtils.getDisplayNameFromUri(message.getFrom());
    }

    /**
     * Return the IMS service
     * 
     * @return IMS service
     */
    public ImsService getImsService() {
        return imsService;
    }

    /**
     * Returns the SIP manager
     * 
     * @return SIP manager
     */
    public SipManager getSipManager() {
        return getImsService().getImsModule().getSipManager();
    }

    /**
     * Returns the IMDN manager
     *
     * @return IMDN manager
     */
    public ImdnManager getImdnManager() {
        return ((InstantMessagingService)getImsService()).getImdnManager();
    }

    /**
     * Returns the rcs settings
     *
     * @return RcsSettings
     */
    public RcsSettings getRcsSettings() {
        return rcsSettings;
    }

    /**
     * Returns the participants
     *
     * @return participants
     */
    public Set<ParticipantInfo> getParticipants() {
        return participants;
    }

    /**
     * Returns the first chat message
     *
     * @return ChatMessage
     */
    public ChatMessage getFirstMessage() {
        return firstMsg;
    }

	/**
	 * Add a listener for receiving events
	 * 
	 * @param listener Listener
	 */
	public void addListener(ImsSessionListener listener) {
		listeners.add(listener);
	}

	/**
	 * Remove a listener
	 */
	public void removeListener(ImsSessionListener listener) {
		listeners.remove(listener);
	}
	
	/**
	 * Remove all listeners
	 */
	public void removeListeners() {
		listeners.removeAllElements();
	}

	/**
	 * Returns the event listeners
	 * 
	 * @return Listeners
	 */
	public Vector<ImsSessionListener> getListeners() {
		return listeners;
	}

    /**
     * Start the session in background
     */
    public abstract void startSession();


    /**
     * Returns the remote contactId
     * 
     * @return ContactId
     */
    public ContactId getRemoteContact() {
        return contact;
    }

    /**
     * Returns the remote Uri
     * 
     * @return remoteUri
     */
    public String getRemoteUri() {
        return remoteUri;
    }

    /**
     * Returns display name of the remote contact
     * 
     * @return String
     */
    public String getRemoteDisplayName() {
        return remoteDisplayName;
    }

    /**
     * Set display name of the remote contact
     * 
     * @param String
     */
    public void setRemoteDisplayName(String remoteDisplayName) {
        this.remoteDisplayName = remoteDisplayName;
    }

    /**
     * Get the dialog path of the session
     * 
     * @return Dialog path object
     */
    public SipDialogPath getDialogPath() {
        return dialogPath;
    }

    /**
     * Set the dialog path of the session
     * 
     * @param dialog Dialog path
     */
    public void setDialogPath(SipDialogPath dialog) {
        dialogPath = dialog;
    }

    /**
     * Returns the authentication agent
     * 
     * @return Authentication agent
     */
    public SessionAuthenticationAgent getAuthenticationAgent() {
        return authenticationAgent;
    }

	/**
	 * Returns the response timeout (in seconds) 
	 * 
	 * @return Timeout
	 */
	public int getResponseTimeout() {
		return ringingPeriod + SipManager.TIMEOUT;
	}

    /**
     * Return the conversation ID
     *
     * @return Conversation ID
     */
    public String getConversationID() {
        return conversationId;
    }

    /**
     * Set the conversation ID
     *
     * @param id Conversation ID
     */
    public void setConversationID(String id) {
        this.conversationId = id;
    }

    /**
     * Return the contribution ID
     *
     * @return Contribution ID
     */
    public String getContributionID() {
        return contributionId;
    }

    /**
     * Set the contribution ID
     *
     * @param id Contribution ID
     */
    public void setContributionID(String id) {
        this.contributionId = id;
    }

    public boolean isOTMSession() {
        return isOTMSession;
    }

    /**
     * Create an MESSAGE request
     *
     * @return the MESSAGE request
     * @throws SipException
     */
    public SipRequest createMessage() throws SipException {
        List<String> tags = ChatUtils.generateFeatureTags(getFirstMessage().getMimeType(), ChatUtils.PAGER_MODE_MESSAGE, false);
        String[] featureTags = tags.toArray(new String[tags.size()]);
        try {
            // Create the content type
            String contentTypeStr;
            if (isOTMSession()) {
                contentTypeStr = "multipart/mixed;boundary="+BOUNDARY_TAG;
            } else {
                contentTypeStr = CpimMessage.MIME_TYPE;
            }
            // Create the request
            SipRequest message = getDialogPath().createMessage(null, contentTypeStr,
                    getDialogPath().getLocalContent().getBytes(UTF8));
            // Set feature tags
            SipUtils.setFeatureTags(message, featureTags);
            // Set the P-Preferred-Service header
            message.addHeader(SipUtils.HEADER_P_PREFERRED_SERVICE, MessagingServiceIdentifier.PAGER_MODE);

            // Add a require header
            if (isOTMSession()) {
                message.addHeader(RequireHeader.NAME, "recipient-list-message");
            }

            // Add a conversation ID header
            message.addHeader(SipRequest.HEADER_CONVERSATION_ID, getConversationID());
            // Add a contribution ID header
            message.addHeader(SipRequest.HEADER_CONTRIBUTION_ID, getContributionID());
            return message;
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP MESSAGE message");
        }
    }

    /**
     * Send an instant message (SIP MESSAGE)
     * 
     * @param contact Contact
     * @param featureTag Feature tag of the service
     * @param content Content
     * @return True if successful else returns false
     */
    public void sendMessage(SipRequest message) {
        try {
            if (logger.isActivated()) {
                logger.debug("Send first MESSAGE: Send instant message to " + getRemoteContact());
            }

            // Send MESSAGE request
            SipTransactionContext ctx = getImsService().getImsModule().getSipManager().sendSipMessageAndWait(message);
            // Analyze the received response 
            if (ctx.isSipResponse()) {
                // A response has been received
                switch (ctx.getStatusCode()) {
                    case Response.OK:
                    case Response.ACCEPTED:
                        // 200 OK
                        handle200OK(ctx.getSipResponse());
                        break;
                    case Response.PROXY_AUTHENTICATION_REQUIRED:
                        // 407 Proxy Authentication Required
                        handle407Authentication(ctx.getSipResponse());
                        break;
                    case Response.NOT_ACCEPTABLE:
                    default:
                        if (logger.isActivated()) {
                            logger.info("Send instant message has failed: " + ctx.getStatusCode()
                                    + " response received");
                        }
                        handleError(new ChatError(ChatError.SESSION_INITIATION_FAILED,
                                "StatusCode="+ctx.getStatusCode()));
                        break;
                }
            }
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't send MESSAGE request", e);
            }
            handleError(new ImsServiceError(ImsServiceError.UNEXPECTED_EXCEPTION,
                    e.getMessage()));
        }
    }

    /**
     * Send a 200 ok response to remote
     * @throws SipException
     */
    public void send200OkReponse() throws SipException {
        if (logger.isActivated()) {
            logger.info("Send 200 ok response");
        }
        SipResponse response = SipUtils.createResponse(getDialogPath().getMessage(),
                IdGenerator.getIdentifier(), 200);
        getSipManager().sendSipResponse(response);
        for (int i = 0; i < getListeners().size(); i++) {
            ((ChatSessionListener) getListeners().get(i)).handleReceiveMessage(getFirstMessage(),
                    ChatUtils.isImdnDisplayedRequested(getDialogPath().getMessage()));
        }
    }

    /**
//     * @deprecated for CMCC, this behavior has been done by server
     * 
     * Send a delivery report of the pager mode message to the remote
     */
    public void sendMessageDeliveryReport() {
        if (logger.isActivated()) {
            logger.info("Send message delivery report");
        }
        // Send message delivery report if requested
        if (ChatUtils.isImdnDeliveredRequested(getDialogPath().getMessage())) {
            // Check notification disposition
            String msgId = ChatUtils.getMessageId(getDialogPath().getMessage());
            if (msgId != null) {
                try {
                    ContactId remote = ContactUtils.createContactId(getDialogPath()
                            .getRemoteParty());
                    // Send message delivery status via a SIP MESSAGE
                    getImdnManager().sendMessageDeliveryStatusImmediately(remote, msgId,
                            ImdnDocument.DELIVERY_STATUS_DELIVERED,
                            SipUtils.getRemoteInstanceID(getDialogPath().getMessage()));
                } catch (RcsContactFormatException e) {
                    if (logger.isActivated()) {
                        logger.warn("Cannot parse contact " + getDialogPath().getRemoteParty());
                    }
                }
            }
        }
    }

    /**
     * Handle 200 0K response
     * 
     * @param resp 200 OK response
     */
    public void handle200OK(SipResponse resp) {
        try {
            if (logger.isActivated()) {
                logger.info("200 OK response received");
            }

            // Notify listeners
            ChatMessage chatMessage = getFirstMessage();
            for (int i = 0; i < getListeners().size(); i++) {
                ((ChatSessionListener) getListeners().get(i)).handleMessageSent(chatMessage.getMessageId(),
                        ChatUtils.networkMimeTypeToApiMimeType(chatMessage.getMimeType()));
            }
        } catch (Exception e) {
            // Unexpected error
            if (logger.isActivated()) {
                logger.error("Session initiation has failed", e);
            }
            handleError(new ImsServiceError(ImsServiceError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }
    }

    /**
     * Handle 407 Proxy Authentication Required
     *
     * @param resp 407 response
     */
    public void handle407Authentication(SipResponse resp) {
        try {
            if (logger.isActivated()) {
                logger.info("407 response received, Send second MESSAGE");
            }

            // Set the remote tag
            getDialogPath().setRemoteTag(resp.getToTag());

            // Update the authentication agent
            getAuthenticationAgent().readProxyAuthenticateHeader(resp);

            // Increment the Cseq number of the dialog path
            getDialogPath().incrementCseq();

            // Create a second MESSAGE request with the right token
            SipRequest message = createMessage();

            // Set initial request in the dialog path
            getDialogPath().setMessage(message);

            // Set the Proxy-Authorization header
            getAuthenticationAgent().setProxyAuthorizationHeader(message);

            // Send sip message
            sendMessage(message);
        } catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Pager mode session initiation has failed", e);
            }
            // Unexpected error
            handleError(new ChatError(ChatError.SESSION_INITIATION_FAILED, e.getMessage()));
        }
    }

    /**
     * Handle Error 
     *
     * @param error ImsServiceError
     */
    public void handleError(ImsServiceError error) {
        if (logger.isActivated()) {
            logger.info("Session error: " + error.getErrorCode() + ", reason=" + error.getMessage());
        }

        for (int i = 0; i < getListeners().size(); i++) {
            ((ChatSessionListener) getListeners().get(i)).handleImError(new ChatError(error));
        }
    };

}
