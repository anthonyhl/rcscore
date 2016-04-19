package com.orangelabs.rcs.core.im.chat;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.protocol.sip.Multipart;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * tct-stack
 * 
 * Originating pager mode session
 */
public class OriginatingOneToOnePagerModeSession extends PagerModeSession {
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
     * @param msg First message of the session to send via SIP MESSAGE
     * @param rcsSettings RCS settings
     */
    public OriginatingOneToOnePagerModeSession(ImsService parent, ContactId contact, ChatMessage msg,
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
//        String convId = ContributionIdGenerator.getContributionId(contact.toString());
        setConversationID(msg.getConversationId());
        // } end
    }

    /**
     * Background processing
     */
    public void run() {
        try {
            if (logger.isActivated()) {
                logger.info("Initiate a new 1-1 chat pager mode session as originating");
            }

            /* -------------------- Build a multipart content --------------------*/
            ChatMessage chatMessage = getFirstMessage();
            // Build CPIM part
            String cpim;
            String from = ChatUtils.ANOMYNOUS_URI;
            String to = ChatUtils.ANOMYNOUS_URI;
            boolean useImdn = getImdnManager().isImdnActivated();
            if (useImdn) {
                // Send message in CPIM + IMDN
                cpim = ChatUtils.buildCpimMessageWithImdn(from, to, chatMessage.getMessageId(),
                        chatMessage.getContent(), chatMessage.getMimeType(),
                        getImsService().getUser().getHomeDomain());
            } else {
                // Send message in CPIM
                cpim = ChatUtils.buildCpimMessage(from, to, chatMessage.getContent(),
                        chatMessage.getMimeType(), getImsService().getUser().getHomeDomain());
            }

            // Build multipart for CMCC
            // @formatter:off
            if (isOTMSession()) {
                // Generate the resource list for given participants
                String resourceList = ChatUtils.generateChatResourceList(
                        ParticipantInfoUtils.getContacts(getParticipants())
                                , getImsService().getUser().getHomeDomain());
                String multipart = new StringBuilder(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(SipUtils.CRLF)
                .append("Content-Type: application/resource-lists+xml").append(SipUtils.CRLF)
                .append("Content-Length: ").append(resourceList.getBytes(UTF8).length).append(SipUtils.CRLF)
                .append("Content-Disposition: recipient-list").append(SipUtils.CRLF)
                .append(SipUtils.CRLF)
                .append(resourceList).append(SipUtils.CRLF)
                .append(SipUtils.CRLF)
                .append(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(SipUtils.CRLF)
                .append("Content-Type: ").append(CpimMessage.MIME_TYPE).append(SipUtils.CRLF)
                .append("Content-Length: ").append(cpim.getBytes(UTF8).length).append(SipUtils.CRLF)
                .append(SipUtils.CRLF)
                .append(cpim).append(SipUtils.CRLF)
                .append(Multipart.BOUNDARY_DELIMITER).append(BOUNDARY_TAG).append(Multipart.BOUNDARY_DELIMITER).toString();
                // Set the local multi part in the dialog path
                getDialogPath().setLocalContent(multipart);
            } else {
                // Set the local cpim part in the dialog path
                getDialogPath().setLocalContent(cpim);
            }
            // @formatter:on

            // Create a sip message
            SipRequest message = createMessage();

            // Set the Authorization header
            getAuthenticationAgent().setAuthorizationHeader(message);

            // Set initial request in the dialog path
            getDialogPath().setMessage(message);

            // Send SIP MESSAGE request
            sendMessage(message);
        } catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Session initiation has failed", e);
            }

            // Unexpected error
            handleError(new ChatError(ChatError.SESSION_INITIATION_FAILED, e.getMessage()));
        }
    }

    @Override
    public void startSession() {
        start();
    }

}
