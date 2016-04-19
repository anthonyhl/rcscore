package com.orangelabs.rcs.core.im.chat;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import java.util.List;

import javax2.sip.header.RequireHeader;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.im.chat.standalone.MessagingServiceIdentifier;
import com.orangelabs.rcs.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.protocol.sip.Multipart;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * @tct-stack
 */
public class OriginatingOneToOneLargeModeSession extends OneToOneChatSession {
    /**
     * Boundary tag
     */
    private final static String BOUNDARY_TAG = "boundary1";

    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
     * @param parent IMS service
     * @param contact Remote contact identifier
     * @param msg First message of the session  to send via MSRP
     * @param rcsSettings RCS settings
     */
    public OriginatingOneToOneLargeModeSession(ImsService parent, ContactId contact, ChatMessage msg,
            RcsSettings rcsSettings) {
        super(parent, contact, ChatUtils.generateRecipientUri(parent, contact),
                ChatUtils.generateParticipantsExt(contact), msg, rcsSettings);
       // Create dialog path
        createOriginatingDialogPath();
        // Set contribution ID
        String id = ContributionIdGenerator.getContributionId(getDialogPath().getCallId());
        setContributionID(id);
        // Set conversation ID for CMCC,
        // FIXME tmply do like this, better to create a thread table for one to one chat begin {
//        String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
//        String convId = ContributionIdGenerator.getContributionId(contact.toString() + "@" + ipAddress);
        setConversationID(msg.getConversationId());
        // } end
    }

    /**
     * Background processing
     */
    public void run() {
        try {
            if (logger.isActivated()) {
                logger.info("Initiate a new 1-1 chat large mode session as originating");
            }

            /* -------------------- Build a multipart content --------------------*/
            // Set setup mode
            String localSetup = createSetupOffer();
            if (logger.isActivated()) {
                logger.debug("Local setup attribute is " + localSetup);
            }

            // Set local port
            int localMsrpPort;
            if ("active".equals(localSetup)) {
                localMsrpPort = 9; // See RFC4145, Page 4
            } else {
                localMsrpPort = getMsrpMgr().getLocalMsrpPort();
            }

            // Build SDP part
            // String ntpTime = SipUtils.constructNTPtime(System.currentTimeMillis());
            String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
            String sdp = SdpUtils.buildChatSDP(ipAddress, localMsrpPort, getMsrpMgr()
                    .getLocalSocketProtocol(), getAcceptTypes(), getWrappedTypes(), localSetup,
                    getMsrpMgr().getLocalMsrpPath(), getSdpDirection());

            // Build multipart for CMCC
            // @formatter:off
            if (getParticipants().size() > 1) {
                // Generate the resource list for given participants
                String resourceList = ChatUtils.generateChatResourceList(
                        ParticipantInfoUtils.getContacts(getParticipants())
                                , getImsService().getUser().getHomeDomain());
                String multipart = new StringBuilder(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(SipUtils.CRLF)
                        .append("Content-Type: application/sdp").append(SipUtils.CRLF)
                        .append("Content-Length: ").append(sdp.getBytes(UTF8).length).append(SipUtils.CRLF)
                        .append(SipUtils.CRLF)
                        .append(sdp)
                        .append(SipUtils.CRLF)
                        .append(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(SipUtils.CRLF)
                        .append("Content-Type: application/resource-lists+xml").append(SipUtils.CRLF)
                        .append("Content-Length: ").append(resourceList.getBytes(UTF8).length).append(SipUtils.CRLF)
                        .append("Content-Disposition: recipient-list").append(SipUtils.CRLF)
                        .append(SipUtils.CRLF)
                        .append(resourceList).append(SipUtils.CRLF)
//                        .append(SipUtils.CRLF)
//                        .append(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(SipUtils.CRLF)
//                        .append("Content-Type: ").append(CpimMessage.MIME_TYPE).append(SipUtils.CRLF)
//                        .append("Content-Length: ").append(cpim.getBytes(UTF8).length).append(SipUtils.CRLF)
//                        .append(SipUtils.CRLF)
//                        .append(cpim).append(SipUtils.CRLF)
                        .append(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(Multipart.BOUNDARY_DELIMITER).toString();
                getDialogPath().setLocalContent(multipart);
            } else {
                // Set the local SDP part in the dialog path
                getDialogPath().setLocalContent(sdp);
            }
            // @formatter:on

            SipRequest invite = createInvite();

            // Set the Authorization header
            getAuthenticationAgent().setAuthorizationHeader(invite);

            // Set initial request in the dialog path
            getDialogPath().setInvite(invite);

            // Send INVITE request
            sendInvite(invite);

        } catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Session initiation has failed", e);
            }

            // Unexpected error
            handleError(new ChatError(ChatError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }
    }

    /**
     * Create an INVITE request
     * 
     * @return the INVITE request
     * @throws SipException
     */
    public SipRequest createInvite() throws SipException {
        List<String> tags = ChatUtils.generateFeatureTags(getFirstMessage().getMimeType(), ChatUtils.LARGE_MODE_MESSAGE, false);
        String[] featureTags = tags.toArray(new String[tags.size()]);
        SipRequest invite;
        if (getParticipants().size() > 1) {
            invite = getDialogPath().createMultipartInvite(featureTags /* getFeatureTags() */,
                    featureTags /* getAcceptContactTags() */, getDialogPath().getLocalContent(), BOUNDARY_TAG);
        } else {
            invite = getDialogPath().createInvite(featureTags, featureTags, getDialogPath().getLocalContent());
        }
        // Add a require header
        if (getParticipants().size() > 1) {
            invite.addHeader(RequireHeader.NAME, "recipient-list-invite");
        }
        // Set the P-Preferred-Service header
        invite.addHeader(SipUtils.HEADER_P_PREFERRED_SERVICE, MessagingServiceIdentifier.LARGE_MESSAGE_MODE);
        // Add a conversation ID header
        invite.addHeader(SipRequest.HEADER_CONVERSATION_ID, getConversationID());
        // Add a contribution ID header
        invite.addHeader(SipRequest.HEADER_CONTRIBUTION_ID, getContributionID());

        return invite;
    }

    /**
     * Handle 200 0K response 
     *
     * @param resp 200 OK response
     */
    public void handle200OK(SipResponse resp) {
        super.handle200OK(resp);
        // The session is established, to send chat message via MSRP
        if (getDialogPath().isSessionEstablished()) {
            sendChatMessage(getFirstMessage());
        } else {
            if (logger.isActivated()) {
                logger.error("Session is not established, failed to send message");
            }
            // Notify listeners
            String msgId = getFirstMessage().getMessageId();
            String mimeType = getFirstMessage().getMimeType();
            for (int i = 0; i < getListeners().size(); i++) {
                ((ChatSessionListener) getListeners().get(i)).handleMessageFailedSend(msgId, mimeType);
            }
        }
    }

    /**
     * Prepare media session
     * 
     * @throws Exception
     */
    public void prepareMediaSession() throws Exception {
        // Changed by Deutsche Telekom
        // Get the remote SDP part
        byte[] sdp = getDialogPath().getRemoteContent().getBytes(UTF8);
        
        // Changed by Deutsche Telekom
        // Create the MSRP session
        MsrpSession session = getMsrpMgr().createMsrpSession(sdp, this);
        
        session.setFailureReportOption(true);// to ensure the report, modified for large message mode
        session.setSuccessReportOption(false);
    }

    /**
     * Data has been transfered
     *
     * @param msgId Message ID
     */
    public void msrpDataTransfered(String msgId) {
        super.msrpDataTransfered(msgId);

        // CMCC large mode, abort session when data transfered
        if (logger.isActivated()) {
            logger.info("terminate the session for msg sent completed");
        }

        closeMediaSession();
        terminateSession(TERMINATION_BY_USER);
        removeSession();
    }

    // Changed by Deutsche Telekom
    @Override
    public String getSdpDirection() {
        return SdpUtils.DIRECTION_SENDRECV;
    }

    @Override
    public boolean isInitiatedByRemote() {
        return false;
    }

    @Override
    public void startSession() {
        final boolean logActivated = logger.isActivated();
        ContactId contact = getRemoteContact();
        if (logActivated) {
            logger.debug("Start OneToOneChatSession with '" + contact + "' as originating");
        }
        start();
    }

    @Override
    public void removeSession() {
    }
}
