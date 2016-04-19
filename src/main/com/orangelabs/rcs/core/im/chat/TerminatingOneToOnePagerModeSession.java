package com.orangelabs.rcs.core.im.chat;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * tct-stack
 * 
 * Terminating pager mode session
 */
public class TerminatingOneToOnePagerModeSession extends PagerModeSession {
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(TerminatingOneToOneChatSession.class
            .getSimpleName());

    /**
     * Constructor
     * 
     * @param parent IMS service
     * @param message Initial MESSAGE request
     * @param contact the remote contactId
     * @param rcsSettings RCS settings
     */
    public TerminatingOneToOnePagerModeSession(ImsService parent, SipRequest message, ContactId contact,
            RcsSettings rcsSettings) {
        super(parent, contact, ChatUtils.generateRecipientUri(parent, contact),
                ChatUtils.generateParticipantsExt(contact), ChatUtils.getFirstMessageExt(message), rcsSettings);
        // Create dialog path
        createTerminatingDialogPath(message);

        // Below codes currently no use in pager mode
//        // Set contribution ID
//        String id = message.getContributionId();
//        setContributionID(id);
//        // Set conversation ID
//        String convId = message.getConversationId();
//        setConversationID(convId);
    }

    /**
     * Background processing
     */
    public void run() {
        final boolean logActivated = logger.isActivated();
        try {
            if (logActivated) {
                logger.info("Initiate a new 1-1 chat pager mode virtual session as terminating");
            }
            // 1st> send 200 ok response
            send200OkReponse();
            // 2st> send delivery report
            sendMessageDeliveryReport();
        } catch (Exception e) {
            if (logActivated) {
                logger.error("Session initiation has failed", e);
            }

            // Unexpected error
            handleError(new ChatError(ChatError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }
    }

    @Override
    public void startSession() {
        start();
    }

}
