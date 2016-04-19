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

package com.orangelabs.rcs.service.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.Mcloud;
import com.gsma.services.rcs.RcsCommon.Direction;
import com.gsma.services.rcs.Vemoticon;
import com.gsma.services.rcs.chat.ChatLog.Message;
import com.gsma.services.rcs.chat.ChatLog.Message.MimeType;
import com.gsma.services.rcs.chat.ChatLog.Message.ReasonCode;
import com.gsma.services.rcs.chat.ChatLog;
import com.gsma.services.rcs.chat.IChatMessage;
import com.gsma.services.rcs.chat.IOneToOneChat;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.chat.ChatError;
import com.orangelabs.rcs.core.im.chat.ChatMessage;
import com.orangelabs.rcs.core.im.chat.ChatSessionListener;
import com.orangelabs.rcs.core.im.chat.ChatUtils;
import com.orangelabs.rcs.core.im.chat.ContributionIdGenerator;
import com.orangelabs.rcs.core.im.chat.OneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.PagerModeSession;
import com.orangelabs.rcs.core.im.chat.cpim.McloudDocument;
import com.orangelabs.rcs.core.im.chat.cpim.VemoticonDocument;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData.ImSessionStartMode;
import com.orangelabs.rcs.service.broadcaster.IOneToOneChatEventBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

/**
 * One-to-One Chat implementation
 * 
 * @author Jean-Marc AUFFRET
 */
public class OneToOneChatImpl extends IOneToOneChat.Stub implements ChatSessionListener {

	private final ContactId mContact;

	private final IOneToOneChatEventBroadcaster mBroadcaster;

	private final InstantMessagingService mImService;

	private final MessagingLog mMessagingLog;

	private final ChatServiceImpl mChatService;

	private final RcsSettings mRcsSettings;

	/**
	 * Lock used for synchronization
	 */
	private final Object lock = new Object();

	/**
	 * The logger
	 */
	private final Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Constructor
	 * 
	 * @param contact Remote contact ID
	 * @param broadcaster IChatEventBroadcaster
	 * @param imService InstantMessagingService
	 * @param messagingLog MessagingLog
	 * @param rcsSettings RcsSettings
	 * @param chatService ChatServiceImpl
	 */
	public OneToOneChatImpl(ContactId contact, IOneToOneChatEventBroadcaster broadcaster,
			InstantMessagingService imService, MessagingLog messagingLog,
			RcsSettings rcsSettings, ChatServiceImpl chatService) {
		mContact = contact;
		mBroadcaster = broadcaster;
		mImService = imService;
		mMessagingLog = messagingLog;
		mChatService = chatService;
		mRcsSettings = rcsSettings;
	}

	private int imdnToFailedReasonCode(ImdnDocument imdn) {
		String notificationType = imdn.getNotificationType();
		if (ImdnDocument.DELIVERY_NOTIFICATION.equals(notificationType)) {
			return ReasonCode.FAILED_DELIVERY;

		} else if (ImdnDocument.DISPLAY_NOTIFICATION.equals(notificationType)) {
			return ReasonCode.FAILED_DISPLAY;
		}

		throw new IllegalArgumentException(new StringBuilder(
				"Received invalid imdn notification type:'").append(notificationType).append("'")
				.toString());
	}

	/**
	 * Returns the remote contact identifier
	 * 
	 * @return ContactId
	 */
	public ContactId getRemoteContact() {
		return mContact;
	}

	/**
	 * Add chat message to Db
	 * 
	 * @param msg InstantMessage
	 * @param state state of message
	 */
	private void addOutgoingChatMessage(ChatMessage msg, int state) {
		mMessagingLog.addOutgoingOneToOneChatMessage(msg,
				state, ReasonCode.UNSPECIFIED);
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
		mBroadcaster.broadcastMessageStatusChanged(mContact, apiMimeType, msg.getMessageId(),
				state, ReasonCode.UNSPECIFIED);
	}

	/**
	 * Sends a plain text message
	 * 
	 * @param message Text message
     * @return Chat message
     */
    public IChatMessage sendMessage(String message) {
		if (logger.isActivated()) {
			logger.debug("Send text message.");
		}
		ChatMessage msg = ChatUtils.createTextMessage(mContact, message);
		ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
				mMessagingLog, msg.getMessageId(), msg.getRemoteContact(), message,
				MimeType.TEXT_MESSAGE, mContact.toString(), msg.getDate().getTime(),
				Direction.OUTGOING);

		/* If the IMS is connected at this time then send this message. */
		if (ServerApiUtils.isImsConnected()) {
			sendChatMessage(msg);
		} else {
			/* If the IMS is NOT connected at this time then queue message. */
			addOutgoingChatMessage(msg, Message.Status.Content.QUEUED);
		}
		return new ChatMessageImpl(persistentStorage);
	}

	/**
	 * Sends a geoloc message
	 *
	 * @param geoloc Geoloc
	 * @return ChatMessage
	 */
	public IChatMessage sendMessage2(Geoloc geoloc) {
		if (logger.isActivated()) {
			logger.debug("Send geolocation message.");
		}
		ChatMessage msg = ChatUtils.createGeolocMessage(mContact, geoloc, mImService.getUser().getPublicUri());
		ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
				mMessagingLog, msg.getMessageId(), msg.getRemoteContact(), msg.toString(),
				MimeType.GEOLOC_MESSAGE, mContact.toString(), msg.getDate().getTime(),
				Direction.OUTGOING);

		/* If the IMS is connected at this time then send this message. */
		if (ServerApiUtils.isImsConnected()) {
			sendChatMessage(msg);
		} else {
			/* If the IMS is NOT connected at this time then queue message. */
			addOutgoingChatMessage(msg, Message.Status.Content.QUEUED);
		}
		return new ChatMessageImpl(persistentStorage);
	}

	/**
     * Sends a chat message
     * 
     * @param msg Message
     */
	private void sendChatMessage(final ChatMessage msg) {
		synchronized (lock) {
			boolean loggerActivated = logger.isActivated();
			if (loggerActivated) {
				logger.debug("Send chat message.");
			}
			final OneToOneChatSession session = mImService.getOneToOneChatSession(mContact);
			if (session == null) {
				try {
					if (loggerActivated) {
						logger.debug("Core session is not yet established: initiate a new session to send the message.");
					}
					addOutgoingChatMessage(msg, Message.Status.Content.SENDING);
					final OneToOneChatSession newSession = mImService.initiateOneToOneChatSession(
							mContact, msg);
					new Thread() {
						public void run() {
							newSession.startSession();
						}
					}.start();
					newSession.addListener(this);
					mChatService.addOneToOneChat(mContact, this);
					handleMessageSent(msg.getMessageId(), msg.getMimeType());

				} catch (Exception e) {
					if (logger.isActivated()) {
						logger.error("Can't send a new chat message.", e);
					}
					handleMessageFailedSend(msg.getMessageId(), msg.getMimeType());
				}
			} else {
				if (session.isMediaEstablished()) {
					if (logger.isActivated()) {
						logger.debug("Core session is established: use existing one to send the message.");
					}
					addOutgoingChatMessage(msg, Message.Status.Content.SENDING);
					session.sendChatMessage(msg);
					return;
				}
				addOutgoingChatMessage(msg, Message.Status.Content.QUEUED);
				if (!session.isInitiatedByRemote()) {
					return;
				}
				if (logger.isActivated()) {
					logger.debug("Core chat session is pending: auto accept it.");
				}
				new Thread() {
					public void run() {
						session.acceptSession();
					}
				}.start();
			}
		}
	}

	/**
	 * Sends a displayed delivery report for a given message ID
	 * 
	 * @param contact Contact ID
	 * @param msgId Message ID
	 */
	/* package private */void sendDisplayedDeliveryReport(final ContactId contact,
			final String msgId) {
		try {
			if (logger.isActivated()) {
				logger.debug("Set displayed delivery report for " + msgId);
			}
			final OneToOneChatSession session = mImService.getOneToOneChatSession(contact);
			if (session != null && session.isMediaEstablished()) {
				if (logger.isActivated()) {
					logger.info("Use the original session to send the delivery status for " + msgId);
				}

				new Thread() {
					public void run() {
						session.sendMsrpMessageDeliveryStatus(contact, msgId,
								ImdnDocument.DELIVERY_STATUS_DISPLAYED);
					}
				}.start();
			} else {
				if (logger.isActivated()) {
					logger.info("No suitable session found to send the delivery status for "
							+ msgId + " : use SIP message");
				}
				mImService.getImdnManager().sendMessageDeliveryStatus(contact, msgId,
						ImdnDocument.DELIVERY_STATUS_DISPLAYED);
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Could not send MSRP delivery status", e);
			}
		}
	}

	/**
	 * Sends an is-composing event. The status is set to true when typing a
	 * message, else it is set to false.
	 * 
	 * @param status Is-composing status
	 * @see RcsSettingsData.ImSessionStartMode
	 */
	public void sendIsComposingEvent(final boolean status) {
		final OneToOneChatSession session = mImService.getOneToOneChatSession(mContact);
		if (session == null) {
			if (logger.isActivated()) {
				logger.debug("Unable to send composing event '" + status
						+ "' since oneToOne chat session found with contact '" + mContact
						+ "' does not exist for now");
			}
			return;
		}
		if (session.getDialogPath().isSessionEstablished()) {
			session.sendIsComposingStatus(status);
			return;
		}
		if (!session.isInitiatedByRemote()) {
			return;
		}
		ImSessionStartMode imSessionStartMode = mRcsSettings.getImSessionStartMode();
		switch (imSessionStartMode) {
			case ON_OPENING:
			case ON_COMPOSING:
				if (logger.isActivated()) {
					logger.debug("Core chat session is pending: auto accept it.");
				}
				session.acceptSession();
				break;
			default:
				break;
		}
	}

	/**
	 * open the chat conversation. Note: if it’s an incoming pending chat
	 * session and the parameter IM SESSION START is 0 then the session is
	 * accepted now.
	 * 
	 * @see RcsSettingsData.ImSessionStartMode
	 */
	public void openChat() {
		if (logger.isActivated()) {
			logger.info("Open a 1-1 chat session with " + mContact);
		}
		try {
			final OneToOneChatSession session = mImService.getOneToOneChatSession(mContact);
			if (session == null) {
				/*
				 * If there is no session ongoing right now then we do not need
				 * to open anything right now so we just return here. A sending
				 * of a new message on this one-to-one chat will anyway result
				 * in creating a new session so we do not need to do anything
				 * more here for now.
				 */
				return;
			}
			if (!session.getDialogPath().isSessionEstablished()) {
				ImSessionStartMode imSessionStartMode = mRcsSettings.getImSessionStartMode();
				if (!session.isInitiatedByRemote()) {
					/*
					 * This method needs to accept pending invitation if
					 * IM_SESSION_START_MODE is 0, which is not applicable if
					 * session is remote originated so we return here.
					 */
					return;
				}
				if (ImSessionStartMode.ON_OPENING == imSessionStartMode) {
					if (logger.isActivated()) {
						logger.debug("Core chat session is pending: auto accept it, as IM_SESSION_START mode = 0");
					}
					session.acceptSession();
				}
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			// TODO: Exception handling in CR037
		}
	}

	/*------------------------------- SESSION EVENTS ----------------------------------*/

	/*
	 * (non-Javadoc)
	 * @see
	 * com.orangelabs.rcs.core.service.ImsSessionListener#handleSessionStarted
	 * ()
	 */
	@Override
	public void handleSessionStarted() {
		if (logger.isActivated()) {
			logger.info("Session started");
		}
		// @tct-stack add begin {
		handleSendQueuedChatMessages();
		// } end
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.orangelabs.rcs.core.service.ImsSessionListener#handleSessionAborted
	 * (int)
	 */
	@Override
	public void handleSessionAborted(int reason) {
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Session aborted (reason ").append(reason).append(")")
					.toString());
		}
		synchronized (lock) {
			mChatService.removeOneToOneChat(mContact);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.ImsSessionListener#
	 * handleSessionTerminatedByRemote()
	 */
	@Override
	public void handleSessionTerminatedByRemote() {
		if (logger.isActivated()) {
			logger.info("Session terminated by remote");
		}
		synchronized (lock) {
			mChatService.removeOneToOneChat(mContact);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleReceiveMessage
	 * (com.orangelabs.rcs.core.service.im.chat.ChatMessage, boolean)
	 */
	@Override
	public void handleReceiveMessage(ChatMessage msg, boolean imdnDisplayedRequested) {
		String msgId = msg.getMessageId();
		if (logger.isActivated()) {
			logger.info(new StringBuilder("New IM with messageId '").append(msgId)
					.append("' received from ").append(mContact).append(".").toString());
		}
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
		synchronized (lock) {
			mMessagingLog.addIncomingOneToOneChatMessage(msg, imdnDisplayedRequested);
			mBroadcaster.broadcastMessageReceived(apiMimeType, msgId);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#handleImError
	 * (com.orangelabs.rcs.core.service.im.chat.ChatError)
	 */
	@Override
	public void handleImError(ChatError error) {
		if (logger.isActivated()) {
			logger.info("IM error " + error.getErrorCode());
		}
		synchronized (lock) {
			mChatService.removeOneToOneChat(mContact);

			switch (error.getErrorCode()) {
				case ChatError.SESSION_INITIATION_FAILED:
				case ChatError.SESSION_INITIATION_CANCELLED:
					final OneToOneChatSession session = mImService.getOneToOneChatSession(mContact);
					String msgId = session.getFirstMessage().getMessageId();
					String apiMimeType = mMessagingLog.getMessageMimeType(msgId);
					mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
							Message.Status.Content.FAILED, ReasonCode.FAILED_SEND);
					mBroadcaster.broadcastMessageStatusChanged(mContact, apiMimeType, msgId,
							Message.Status.Content.FAILED, ReasonCode.FAILED_SEND);
					break;
				default:
					break;
			}
		}
	}

	@Override
	public void handleIsComposingEvent(ContactId contact, boolean status) {
		if (logger.isActivated()) {
			logger.info(new StringBuilder("").append(contact)
					.append(" is composing status set to ").append(status).toString());
		}
		synchronized (lock) {
			mBroadcaster.broadcastComposingEvent(contact, status);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleMessageSending(
	 * com.orangelabs.rcs.core.service.im.chat.ChatMessage)
	 */
	@Override
	public void handleMessageSending(ChatMessage msg) {
		String msgId = msg.getMessageId();
		String networkMimeType = msg.getMimeType();
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Message is being sent; msgId=").append(msgId)
					.append("; mimeType").append(networkMimeType).append(".").toString());
		}
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(networkMimeType);
		synchronized (lock) {
			mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
					Message.Status.Content.SENDING, ReasonCode.UNSPECIFIED);
			mBroadcaster.broadcastMessageStatusChanged(mContact, apiMimeType, msgId,
					ChatLog.Message.Status.Content.SENDING, ReasonCode.UNSPECIFIED);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleMessageSent(
	 * com.orangelabs.rcs.core.service.im.chat.ChatMessage)
	 */
	@Override
	public void handleMessageSent(String msgId, String mimeType) {
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Message sent; msgId=").append(msgId).append(".")
					.toString());
		}
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(mimeType);
		synchronized (lock) {
			mMessagingLog.setChatMessageStatusAndReasonCode(msgId, Message.Status.Content.SENT,
					ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastMessageStatusChanged(mContact, apiMimeType, msgId,
					Message.Status.Content.SENT, ReasonCode.UNSPECIFIED);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleMessageFailedSend(
	 * com.orangelabs.rcs.core.service.im.chat.ChatMessage)
	 */
	@Override
	public void handleMessageFailedSend(String msgId, String mimeType) {
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(mimeType);
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Message sent; msgId=").append(msgId).append(".")
					.toString());
		}
		synchronized (lock) {
			mMessagingLog.setChatMessageStatusAndReasonCode(msgId, Message.Status.Content.FAILED,
					ReasonCode.FAILED_SEND);

			mBroadcaster.broadcastMessageStatusChanged(mContact, apiMimeType, msgId,
					Message.Status.Content.FAILED, ReasonCode.FAILED_SEND);
		}
	}

	@Override
	public void handleMessageDeliveryStatus(ContactId contact, ImdnDocument imdn) {
		String msgId = imdn.getMsgId();
		String status = imdn.getStatus();
		if (logger.isActivated()) {
			logger.info(new StringBuilder("New message delivery status for message ").append(msgId)
					.append(", status ").append(status).append(".").toString());
		}
		String mimeType = mMessagingLog.getMessageMimeType(msgId);
		if (ImdnDocument.DELIVERY_STATUS_ERROR.equals(status)
				|| ImdnDocument.DELIVERY_STATUS_FAILED.equals(status)
				|| ImdnDocument.DELIVERY_STATUS_FORBIDDEN.equals(status)) {
			int reasonCode = imdnToFailedReasonCode(imdn);
			synchronized (lock) {
				mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
						Message.Status.Content.FAILED, reasonCode);

				mBroadcaster.broadcastMessageStatusChanged(contact, mimeType, msgId,
						Message.Status.Content.FAILED, reasonCode);
			}

		} else if (ImdnDocument.DELIVERY_STATUS_DELIVERED.equals(status)) {
			synchronized (lock) {
				mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
						Message.Status.Content.DELIVERED, ReasonCode.UNSPECIFIED);

				mBroadcaster.broadcastMessageStatusChanged(contact, mimeType, msgId,
						Message.Status.Content.DELIVERED, ReasonCode.UNSPECIFIED);
			}

		} else if (ImdnDocument.DELIVERY_STATUS_DISPLAYED.equals(status)) {
			synchronized (lock) {
				mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
						Message.Status.Content.DISPLAYED, ReasonCode.UNSPECIFIED);

				mBroadcaster.broadcastMessageStatusChanged(contact, mimeType, msgId,
						Message.Status.Content.DISPLAYED, ReasonCode.UNSPECIFIED);
			}
		}
	}

	@Override
	public void handleSessionRejectedByUser() {
		if (logger.isActivated()) {
			logger.info("Session rejected by user.");
		}
		synchronized (lock) {
			mChatService.removeOneToOneChat(mContact);
		}
	}

	@Override
	public void handleSessionRejectedByTimeout() {
		if (logger.isActivated()) {
			logger.info("Session rejected by time-out.");
		}
		synchronized (lock) {
			mChatService.removeOneToOneChat(mContact);
		}
	}

	@Override
	public void handleSessionRejectedByRemote() {
		if (logger.isActivated()) {
			logger.info("Session rejected by remote.");
		}
		synchronized (lock) {
			mChatService.removeOneToOneChat(mContact);
		}
	}

	@Override
	public void handleConferenceEvent(ContactId contact, String contactDisplayname, String state) {
		/* Not used by one-to-one chat */
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleAddParticipantSuccessful(com.gsma.services.rcs.contact.ContactId)
	 */
	@Override
	public void handleAddParticipantSuccessful(ContactId contact) {
		/* Not used by one-to-one chat */
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleAddParticipantFailed(com.gsma.services.rcs.contact.ContactId,
	 * java.lang.String)
	 */
	@Override
	public void handleAddParticipantFailed(ContactId contact, String reason) {
		/* Not used by one-to-one chat */
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleParticipantStatusChanged
	 * (com.gsma.services.rcs.chat.ParticipantInfo)
	 */
	@Override
	public void handleParticipantStatusChanged(ParticipantInfo participantInfo) {
		/* Not used by one-to-one chat */
	}

	@Override
	public void handleSessionAccepted() {
		/* Not used by one-to-one chat */
	}

	@Override
	public void handleSessionInvited() {
		/* Not used by one-to-one chat */
	}

	@Override
	public void handleSessionAutoAccepted() {
		/* Not used by one-to-one chat */
	}


    /**********************************************************************************
     *  tct-stack add for fix bug: can't send queued chat message in session mode
     **********************************************************************************/

    /**
     * get group chat message from DB
     * 
     * @param state state of message
     * @return Set<ChatMessage> ChatMessages
     */
    private Set<ChatMessage> getOutgoingChatMessages(int state) {
        Set<ChatMessage> chatMessages = new HashSet<ChatMessage>();
        Set<String> messageIds = mMessagingLog.getOneToOneChatMessageIds(mContact, Direction.OUTGOING, state,
                ReasonCode.UNSPECIFIED);
        for (String msgId : messageIds) {
            ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                    mMessagingLog, msgId);
            ChatMessage msg = ChatUtils.createChatMessage(msgId, persistentStorage.getRemoteContact(),
                    persistentStorage.getContent(), persistentStorage.getMimeType(), null, null);
            chatMessages.add(msg);
        }
        return chatMessages;
    }

    /**
     * Try to send queued messages when one to one chat session is established
     */
    private void handleSendQueuedChatMessages() {
        final OneToOneChatSession session = mImService.getOneToOneChatSession(mContact);
        Set<ChatMessage> chatMessages = getOutgoingChatMessages(Message.Status.Content.QUEUED);
        for (ChatMessage msg : chatMessages) {
            if (session.isMediaEstablished()) {
                session.sendChatMessage(msg);
            }
        }
    }


    /**********************************************************************************
     *  tct-stack add for CMCC message modes: 1) pager mode; 2) large mode
     **********************************************************************************/

    /**
     * Unique conversationId <=> contacts map
     */
    private final Map<ContactId, String> mConvIdCache = new HashMap<ContactId, String>();

    /**
     * Get the old conversation id. If no, create a new conversation id
     * 
     * @param contact
     * @return conversation id
     */
    private String getOrCreateConversationId(ContactId contact) {
        if (mConvIdCache.get(contact) == null) {
            String convId = mMessagingLog.getOneToOneChatConversationId(contact);
            if (convId == null) {
                convId = ContributionIdGenerator.getContributionId(contact.toString());
            }
            mConvIdCache.put(contact, convId);
        }
        return mConvIdCache.get(contact);
    }

    /**
     * Sends a plain text message extension
     * 
     * @param contacts ContactId set
     * @param message Text message
     * @return Chat message, remote is null
     */
    public IChatMessage sendMessageExt(List<ContactId> contactsList, String text) {
        if (logger.isActivated()) {
            logger.debug("Send text message extension to " + contactsList);
        }
        if (contactsList == null || contactsList.size() <= 0) {
            return null;
        }

        // Generate the resource list for given participants
        String resourceList = (contactsList.size() > 1) ? ChatUtils.generateChatResourceList(
                new HashSet<ContactId>(contactsList), mImService.getUser().getHomeDomain()) : null;
        ContactId remote = ChatUtils.generateVirtualContactId(contactsList);
        String convId = getOrCreateConversationId(remote);
        ChatMessage msg = ChatUtils.createTextMessageExt(convId, remote, text);

        /* If the IMS is connected at this time then send this message. */
        if (ServerApiUtils.isImsConnected()) {
            if ((resourceList + msg.getContent()).getBytes(UTF8).length > PagerModeSession.MAX_CHAR) {
                sendMessageInLargeMode(remote, msg);
            } else {
                sendMessageInPagerMode(remote, msg);
            }
        } else {
            /* If the IMS is NOT connected at this time then failed message. */
            addOutgoingChatMessage(msg, Message.Status.Content.FAILED);
        }

        ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                mMessagingLog, msg.getMessageId(), msg.getRemoteContact(), text,
                MimeType.TEXT_MESSAGE, convId, msg.getDate().getTime(),
                Direction.OUTGOING);
        return new ChatMessageImpl(persistentStorage);
    }

    /**
     * Sends a mcloud message extension
     * 
     * @param contacts ContactId set
     * @param mcloud Mcloud
     * @return Chat message
     */
    public IChatMessage sendMcloudMessage(List<ContactId> contactsList, Mcloud mcloud) {
        if (logger.isActivated()) {
            logger.debug("Send mcloud message to " + contactsList);
        }
        if (contactsList == null || contactsList.size() <= 0) {
            return null;
        }

        // Generate the resource list for given participants
        String resourceList = (contactsList.size() > 1) ? ChatUtils.generateChatResourceList(
                new HashSet<ContactId>(contactsList), mImService.getUser().getHomeDomain()) : null;
        ContactId remote = ChatUtils.generateVirtualContactId(contactsList);
        String convId = getOrCreateConversationId(remote);
        ChatMessage msg = ChatUtils.createMcloudMessage(convId, remote, mcloud);
        /* If the IMS is connected at this time then send this message. */
        if (ServerApiUtils.isImsConnected()) {
            if ((resourceList + msg.getContent()).getBytes(UTF8).length > PagerModeSession.MAX_CHAR) {
                sendMessageInLargeMode(remote, msg);
            } else {
                sendMessageInPagerMode(remote, msg);
            }
        } else {
            /* If the IMS is NOT connected at this time then failed message. */
            addOutgoingChatMessage(msg, Message.Status.Content.FAILED);
        }

        ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                mMessagingLog, msg.getMessageId(), msg.getRemoteContact(),
                msg.getContent(), McloudDocument.MIME_TYPE, convId, msg.getDate()
                        .getTime(), Direction.OUTGOING);
        return new ChatMessageImpl(persistentStorage);
    }

    /**
     * Sends a vemoticon message
     * 
     * @param contacts ContactId set
     * @param vemoticon Vemoticon
     * @return Chat message
     */
    public IChatMessage sendVemoticonMessage(List<ContactId> contactsList, Vemoticon vemoticon) {
        if (logger.isActivated()) {
            logger.debug("Send vemoticon message to " + contactsList);
        }
        if (contactsList == null || contactsList.size() <= 0) {
            return null;
        }

        // Generate the resource list for given participants
        String resourceList = (contactsList.size() > 1) ? ChatUtils.generateChatResourceList(
                new HashSet<ContactId>(contactsList), mImService.getUser().getHomeDomain()) : null;
        ContactId remote = ChatUtils.generateVirtualContactId(contactsList);
        String convId = getOrCreateConversationId(remote);
        ChatMessage msg = ChatUtils.createVemoticonMessage(convId, remote, vemoticon);
        /* If the IMS is connected at this time then send this message. */
        if (ServerApiUtils.isImsConnected()) {
            if ((resourceList + msg.getContent()).getBytes(UTF8).length > PagerModeSession.MAX_CHAR) {
                sendMessageInLargeMode(remote, msg);
            } else {
                sendMessageInPagerMode(remote, msg);
            }
        } else {
            /* If the IMS is NOT connected at this time then failed message. */
            addOutgoingChatMessage(msg, Message.Status.Content.FAILED);
        }

        ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                mMessagingLog, msg.getMessageId(), msg.getRemoteContact(),
                msg.getContent(), VemoticonDocument.MIME_TYPE, convId, msg.getDate()
                        .getTime(), Direction.OUTGOING);
        return new ChatMessageImpl(persistentStorage);
    }

    /**
     * Sends a plain text message in pager mode
     * 
     * @param contact ContactId
     * @param message Text message
     * @return Chat message
     */
    private void sendMessageInPagerMode(ContactId contact, ChatMessage msg) {
        synchronized (lock) {
            if (logger.isActivated()) {
                logger.debug("Send chat message in pager mode.");
            }

            ChatMessageListener msgListener = new ChatMessageListener(msg);
            try {
                // This kind of msg will sent directly, so first mark as sending
                addOutgoingChatMessage(msg, Message.Status.Content.SENDING);
                final PagerModeSession newSession = mImService.initiateOneToOnePagerModeSession(
                        contact, msg);
                newSession.addListener(msgListener);
                new Thread() {
                    public void run() {
                        newSession.startSession();
                    }
                }.start();
            } catch (Exception e) {
                if (logger.isActivated()) {
                    logger.error("Can't send a new chat message in pager mode.", e);
                }
                msgListener.handleMessageFailedSend(msg.getMessageId(), msg.getMimeType());
            }
        }
    }

    /**
     * Sends a plain text message in large mode
     * 
     * @param contact ContactId
     * @param message Text message
     * @return Chat message
     */
    private void sendMessageInLargeMode(ContactId contact, ChatMessage msg) {
        synchronized (lock) {
            if (logger.isActivated()) {
                logger.debug("Send chat message in large mode.");
            }

            ChatMessageListener msgListener = new ChatMessageListener(msg);
            try {
                // This kind of msg will sent after session established, so first mark as queued.
                // When established, it will be marked as sending first and then sent via MSRP
                addOutgoingChatMessage(msg, Message.Status.Content.SENDING); // don't have queued state
                final OneToOneChatSession newSession = mImService.initiateOneToOneLargeModeSession(
                        contact, msg);
                newSession.addListener(msgListener);
                new Thread() {
                    public void run() {
                        newSession.startSession();
                    }
                }.start();
            } catch (Exception e) {
                if (logger.isActivated()) {
                    logger.error("Can't send a new chat message in large mode.", e);
                }
                msgListener.handleMessageFailedSend(msg.getMessageId(), msg.getMimeType());
            }
        }
    }

    /**
     * ChatMessageListener to detect the message status
     */
    public class ChatMessageListener implements ChatSessionListener {
        /**
         * The chat message which this listener belongs to
         */
        private final ChatMessage iChatMessage;

        /**
         * The logger
         */
        private final Logger iLogger = Logger.getLogger(getClass().getName());

        protected ChatMessageListener(ChatMessage msg) {
            iChatMessage = msg;
        }

        @Override
        public void handleSessionStarted() {
            if (iLogger.isActivated()) {
                iLogger.info("Session started");
            }
        }

        @Override
        public void handleSessionAborted(int reason) {
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("Session aborted (reason ").append(reason).append(")")
                        .toString());
            }
            int msgStatus = mMessagingLog.getMessageStatus(iChatMessage.getMessageId());
            if ((msgStatus == Message.Status.Content.QUEUED) 
                    || (msgStatus == Message.Status.Content.SENDING)) {
                String msgId = iChatMessage.getMessageId();
                String mimeType = iChatMessage.getMimeType();
                handleMessageFailedSend(msgId, mimeType);
            }
        }

        @Override
        public void handleSessionTerminatedByRemote() {
            if (iLogger.isActivated()) {
                iLogger.info("Session terminated by remote");
            }
            int msgStatus = mMessagingLog.getMessageStatus(iChatMessage.getMessageId());
            if ((msgStatus == Message.Status.Content.QUEUED) 
                    || (msgStatus == Message.Status.Content.SENDING)) {
                String msgId = iChatMessage.getMessageId();
                String mimeType = iChatMessage.getMimeType();
                handleMessageFailedSend(msgId, mimeType);
            }
        }

        @Override
        public void handleReceiveMessage(ChatMessage msg, boolean imdnDisplayedRequested) {
            String msgId = msg.getMessageId();
            ContactId contact = msg.getRemoteContact();
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("New IM with messageId '").append(msgId)
                        .append("' received from ").append(contact).append(".").toString());
            }
            String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
            synchronized (lock) {
                mMessagingLog.addIncomingOneToOneChatMessage(msg, imdnDisplayedRequested);
                mBroadcaster.broadcastMessageReceived(apiMimeType, msg.getMessageId());
            }
        }

        @Override
        public void handleImError(ChatError error) {
            if (iLogger.isActivated()) {
                iLogger.info("IM error " + error.getErrorCode());
            }
            synchronized (lock) {
                switch (error.getErrorCode()) {
                    case ChatError.SESSION_INITIATION_FAILED:
                    case ChatError.SESSION_INITIATION_CANCELLED:
                        ContactId contact = iChatMessage.getRemoteContact();
                        String msgId = iChatMessage.getMessageId();
                        String apiMimeType = mMessagingLog.getMessageMimeType(msgId);
                        mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
                                Message.Status.Content.FAILED, ReasonCode.FAILED_SEND);
                        mBroadcaster.broadcastMessageStatusChanged(contact, apiMimeType, msgId,
                                Message.Status.Content.FAILED, ReasonCode.FAILED_SEND);
                        break;
                    default:
                        break;
                }
            }
        }

        @Override
        public void handleIsComposingEvent(ContactId contact, boolean status) {
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("").append(contact)
                        .append(" is composing status set to ").append(status).toString());
            }
            /* Not used by one-to-one chat in two modes:
             * There is no composing message for CMCC */
//            synchronized (lock) {
//                mBroadcaster.broadcastComposingEvent(contact, status);// no use for CMCC
//            }
        }

        @Override
        public void handleMessageSending(ChatMessage msg) {
            ContactId contact = msg.getRemoteContact();
            String msgId = msg.getMessageId();
            String networkMimeType = msg.getMimeType();
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("Message is being sent; msgId=").append(msgId)
                        .append("; mimeType").append(networkMimeType).append(".").toString());
            }
            String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(networkMimeType);
            synchronized (lock) {
                mMessagingLog.setChatMessageStatusAndReasonCode(msgId,
                        Message.Status.Content.SENDING, ReasonCode.UNSPECIFIED);
                mBroadcaster.broadcastMessageStatusChanged(contact, apiMimeType, msgId,
                        ChatLog.Message.Status.Content.SENDING, ReasonCode.UNSPECIFIED);
            }
        }

        @Override
        public void handleMessageSent(String msgId, String mimeType) {
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("Message sent; msgId=").append(msgId).append(".")
                        .toString());
            }
            ContactId contact = iChatMessage.getRemoteContact();
            String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(mimeType);
            synchronized (lock) {
                mMessagingLog.setChatMessageStatusAndReasonCode(msgId, Message.Status.Content.SENT,
                        ReasonCode.UNSPECIFIED);

                mBroadcaster.broadcastMessageStatusChanged(contact, apiMimeType, msgId,
                        Message.Status.Content.SENT, ReasonCode.UNSPECIFIED);
            }
        }

        @Override
        public void handleMessageFailedSend(String msgId, String mimeType) {
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("Message failed send; msgId=").append(msgId).append(".")
                        .toString());
            }
            ContactId contact = iChatMessage.getRemoteContact();
            String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(mimeType);
            synchronized (lock) {
                mMessagingLog.setChatMessageStatusAndReasonCode(msgId, Message.Status.Content.FAILED,
                        ReasonCode.FAILED_SEND);

                mBroadcaster.broadcastMessageStatusChanged(contact, apiMimeType, msgId,
                        Message.Status.Content.FAILED, ReasonCode.FAILED_SEND);
            }
        }

        @Override
        public void handleMessageDeliveryStatus(ContactId contact, ImdnDocument imdn) {
            String msgId = imdn.getMsgId();
            String status = imdn.getStatus();
            if (iLogger.isActivated()) {
                iLogger.info(new StringBuilder("New message delivery status for message ").append(msgId)
                        .append(", status ").append(status).append(".").toString());
            }
            /* Not used by one-to-one chat in two modes:
             * There is no chat session stored in InstantMessagingService for these two modes */
        }

        @Override
        public void handleSessionRejectedByUser() {
            if (iLogger.isActivated()) {
                iLogger.info("Session rejected by user.");
            }
            /* Not used by one-to-one chat in two modes:
             * 1. large mode msg not sent before session established 2. pager mode no true session */
        }

        @Override
        public void handleSessionRejectedByTimeout() {
            if (iLogger.isActivated()) {
                iLogger.info("Session rejected by time-out.");
            }
            /* Not used by one-to-one chat in two modes:
             * 1. large mode msg not sent before session established 2. pager mode no true session */
        }

        @Override
        public void handleSessionRejectedByRemote() {
            if (iLogger.isActivated()) {
                iLogger.info("Session rejected by remote.");
            }
            /* Not used by one-to-one chat in two modes:
             * 1. large mode msg not sent before session established 2. pager mode no true session */
        }

        @Override
        public void handleConferenceEvent(ContactId contact, String contactDisplayname, String state) {
            /* Not used by one-to-one chat */
        }

        @Override
        public void handleAddParticipantSuccessful(ContactId contact) {
            /* Not used by one-to-one chat */
        }

        @Override
        public void handleAddParticipantFailed(ContactId contact, String reason) {
            /* Not used by one-to-one chat */
        }

        @Override
        public void handleParticipantStatusChanged(ParticipantInfo participantInfo) {
            /* Not used by one-to-one chat */
        }

        @Override
        public void handleSessionAccepted() {
            /* Not used by one-to-one chat */
        }

        @Override
        public void handleSessionInvited() {
            /* Not used by one-to-one chat */
        }

        @Override
        public void handleSessionAutoAccepted() {
            /* Not used by one-to-one chat */
        }
    }

}
