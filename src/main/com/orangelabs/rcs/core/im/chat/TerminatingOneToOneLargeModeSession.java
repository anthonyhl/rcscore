package com.orangelabs.rcs.core.im.chat;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import java.io.IOException;
import java.util.Collection;
import java.util.Vector;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.ImsSessionListener;
import com.orangelabs.rcs.core.SessionTimerManager;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.im.filetransfer.FileTransferUtils;
import com.orangelabs.rcs.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.protocol.sdp.SdpParser;
import com.orangelabs.rcs.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * tct-stack
 * 
 * Terminating large mode session
 */
public class TerminatingOneToOneLargeModeSession extends OneToOneChatSession {
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(TerminatingOneToOneLargeModeSession.class
            .getSimpleName());

    /**
     * Constructor
     * 
     * @param parent IMS service
     * @param invite Initial INVITE request
     * @param contact the remote contactId
     * @param rcsSettings RCS settings
     */
    public TerminatingOneToOneLargeModeSession(ImsService parent, SipRequest invite, ContactId contact,
            RcsSettings rcsSettings) {
        super(parent, contact, PhoneUtils.formatContactIdToUri(contact, parent.getUser().getHomeDomain()), ChatUtils
                .getFirstMessageExt(invite), rcsSettings);

        // Create dialog path
        createTerminatingDialogPath(invite);

        // Set contribution ID
        String id = ChatUtils.getContributionId(invite);
        setContributionID(id);

        // Set conversation ID
        String convid = ChatUtils.getConversationId(invite);
        setConversationID(convid);

        if (shouldBeAutoAccepted()) {
            setSessionAccepted();
        }
    }

    /**
     * Check is session should be auto accepted. This method should only be
     * called once per session
     * 
     * @return true if one-to-one chat session should be auto accepted
     */
    private boolean shouldBeAutoAccepted() {
        /*
         * In case the invite contains a http file transfer info the chat
         * session should be auto-accepted so that the file transfer session can
         * be started.
         */
        if (FileTransferUtils.getHttpFTInfo(getDialogPath().getInvite()) != null) {
            return true;
        }

        return RcsSettings.getInstance().isChatAutoAccepted();
    }

    /**
     * Background processing
     */
    public void run() {
        final boolean logActivated = logger.isActivated();
        try {
            if (logActivated) {
                logger.info("Initiate a new 1-1 chat large mode session as terminating");
            }

            // Send message delivery report if requested
            if ((ChatUtils.isImdnDeliveredRequested(getDialogPath().getInvite()))
                    || (ChatUtils.isFileTransferOverHttp(getDialogPath().getInvite()))) {
                // Check notification disposition
                String msgId = ChatUtils.getMessageId(getDialogPath().getInvite());
                if (msgId != null) {
                    try {
                        ContactId remote = ContactUtils.createContactId(getDialogPath()
                                .getRemoteParty());
                        // Send message delivery status via a SIP MESSAGE
                        getImdnManager().sendMessageDeliveryStatusImmediately(remote, msgId,
                                ImdnDocument.DELIVERY_STATUS_DELIVERED,
                                SipUtils.getRemoteInstanceID(getDialogPath().getInvite()));
                    } catch (RcsContactFormatException e) {
                        if (logActivated) {
                            logger.warn("Cannot parse contact " + getDialogPath().getRemoteParty());
                        }
                    }
                }
            }

            Collection<ImsSessionListener> listeners = getListeners();
            /* Check if session should be auto-accepted once */
            if (isSessionAccepted()) {
                if (logActivated) {
                    logger.debug("Received one-to-one chat invitation marked for auto-accept");
                }

                for (ImsSessionListener listener : listeners) {
                    ((ChatSessionListener)listener).handleSessionAutoAccepted();
                }
            } else {
                if (logActivated) {
                    logger.debug("Received one-to-one chat invitation marked for manual accept");
                }

                for (ImsSessionListener listener : listeners) {
                    listener.handleSessionInvited();
                }

                send180Ringing(getDialogPath().getInvite(), getDialogPath().getLocalTag());

                int answer = waitInvitationAnswer();
                switch (answer) {
                    case ImsServiceSession.INVITATION_REJECTED:
                        if (logActivated) {
                            logger.debug("Session has been rejected by user");
                        }

                        removeSession();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionRejectedByUser();
                        }
                        return;

                    case ImsServiceSession.INVITATION_NOT_ANSWERED:
                        if (logActivated) {
                            logger.debug("Session has been rejected on timeout");
                        }

                        // Ringing period timeout
                        send486Busy(getDialogPath().getInvite(), getDialogPath().getLocalTag());

                        removeSession();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionRejectedByTimeout();
                        }
                        return;

                    case ImsServiceSession.INVITATION_CANCELED:
                        if (logActivated) {
                            logger.debug("Session has been rejected by remote");
                        }

                        removeSession();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionRejectedByRemote();
                        }
                        return;

                    case ImsServiceSession.INVITATION_ACCEPTED:
                        setSessionAccepted();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionAccepted();
                        }
                        break;

                    default:
                        if (logActivated) {
                            logger.debug("Unknown invitation answer in run; answer=".concat(String
                                    .valueOf(answer)));
                        }
                        break;
                }
            }

            // Parse the remote SDP part
            String remoteSdp = getDialogPath().getInvite().getSdpContent();
            SdpParser parser = new SdpParser(remoteSdp.getBytes(UTF8));
            Vector<MediaDescription> media = parser.getMediaDescriptions();
            MediaDescription mediaDesc = media.elementAt(0);
            MediaAttribute attr1 = mediaDesc.getMediaAttribute("path");
            String remotePath = attr1.getValue();
            String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaDesc);
            int remotePort = mediaDesc.port;

            // Changed by Deutsche Telekom
            String fingerprint = SdpUtils.extractFingerprint(parser, mediaDesc);

            // Extract the "setup" parameter
            String remoteSetup = "passive";
            MediaAttribute attr2 = mediaDesc.getMediaAttribute("setup");
            if (attr2 != null) {
                remoteSetup = attr2.getValue();
            }
            if (logActivated) {
                logger.debug("Remote setup attribute is " + remoteSetup);
            }

            // Set setup mode
            String localSetup = createSetupAnswer(remoteSetup);
            if (logActivated) {
                logger.debug("Local setup attribute is " + localSetup);
            }

            // Set local port
            int localMsrpPort;
            if (localSetup.equals("active")) {
                localMsrpPort = 9; // See RFC4145, Page 4
            } else {
                localMsrpPort = getMsrpMgr().getLocalMsrpPort();
            }

            // Build SDP part
            // String ntpTime =
            // SipUtils.constructNTPtime(System.currentTimeMillis());
            String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
            String sdp = SdpUtils.buildChatSDP(ipAddress, localMsrpPort, getMsrpMgr()
                    .getLocalSocketProtocol(), getAcceptTypes(), getWrappedTypes(), localSetup,
                    getMsrpMgr().getLocalMsrpPath(), getSdpDirection());

            // Set the local SDP part in the dialog path
            getDialogPath().setLocalContent(sdp);

            // Test if the session should be interrupted
            if (isInterrupted()) {
                if (logActivated) {
                    logger.debug("Session has been interrupted: end of processing");
                }
                return;
            }

            // Create the MSRP server session
            if (localSetup.equals("passive")) {
                // Passive mode: client wait a connection
                MsrpSession session = getMsrpMgr().createMsrpServerSession(remotePath, this);
                session.setFailureReportOption(false);
                session.setSuccessReportOption(false);

                // Open the connection
                Thread thread = new Thread() {
                    public void run() {
                        try {
                            // Open the MSRP session
                            getMsrpMgr().openMsrpSession();

                            // Even if local setup is passive, an empty chunk
                            // must be sent to open the NAT
                            // and so enable the active endpoint to initiate a
                            // MSRP connection.
                            sendEmptyDataChunk();
                        } catch (IOException e) {
                            if (logActivated) {
                                logger.error("Can't create the MSRP server session", e);
                            }
                        }
                    }
                };
                thread.start();
            }

            // Create a 200 OK response
            if (logActivated) {
                logger.info("Send 200 OK");
            }
            String[] featureTags = getFeatureTags();
            SipResponse resp = getDialogPath().create200OkInviteResponse(featureTags, featureTags, sdp);

            // The signalisation is established
            getDialogPath().sigEstablished();

            // Send response
            SipTransactionContext ctx = getImsService().getImsModule().getSipManager()
                    .sendSipMessageAndWait(resp);

            // Analyze the received response
            if (ctx.isSipAck()) {
                // ACK received
                if (logActivated) {
                    logger.info("ACK request received");
                }

                // The session is established
                getDialogPath().sessionEstablished();

                // Create the MSRP client session
                if (localSetup.equals("active")) {
                    // Active mode: client should connect
                    MsrpSession session = getMsrpMgr().createMsrpClientSession(remoteHost,
                            remotePort, remotePath, this, fingerprint);
                    session.setFailureReportOption(false);
                    session.setSuccessReportOption(false);

                    // Open the MSRP session
                    getMsrpMgr().openMsrpSession();

                    // Send an empty packet
                    sendEmptyDataChunk();
                }

                for (ImsSessionListener listener : listeners) {
                    listener.handleSessionStarted();
                }

                // Start session timer
                if (getSessionTimerManager().isSessionTimerActivated(resp)) {
                    getSessionTimerManager().start(SessionTimerManager.UAS_ROLE,
                            getDialogPath().getSessionExpireTime());
                }

                // Start the activity manager
                getActivityManager().start();

            } else {
                if (logActivated) {
                    logger.debug("No ACK received for INVITE");
                }

                // No response received: timeout
                handleError(new ChatError(ChatError.SESSION_INITIATION_FAILED));
            }
        } catch (Exception e) {
            if (logActivated) {
                logger.error("Session initiation has failed", e);
            }

            // Unexpected error
            handleError(new ChatError(ChatError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }
    }

    // Changed by Deutsche Telekom
    @Override
    public String getSdpDirection() {
        return SdpUtils.DIRECTION_SENDRECV;
    }

    @Override
    public boolean isInitiatedByRemote() {
        return true;
    }

    @Override
    public void startSession() {
        final boolean logActivated = logger.isActivated();
        ContactId contact = getRemoteContact();
        if (logActivated) {
            logger.debug("Start OneToOneChatSession with '" + contact + "' as terminating");
        }
        start();
    }

    @Override
    public void removeSession() {
//        getImsService().getImsModule().getInstantMessagingService().removeSession(this);
    }
}
