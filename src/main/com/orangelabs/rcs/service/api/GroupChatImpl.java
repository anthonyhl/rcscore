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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.GroupDeliveryInfoLog;
import com.gsma.services.rcs.Mcloud;
import com.gsma.services.rcs.RcsCommon.Direction;
import com.gsma.services.rcs.Vemoticon;
import com.gsma.services.rcs.chat.ChatLog;
import com.gsma.services.rcs.chat.ChatLog.Message;
import com.gsma.services.rcs.chat.ChatLog.Message.MimeType;
import com.gsma.services.rcs.chat.GroupChat;
import com.gsma.services.rcs.chat.GroupChat.ReasonCode;
import com.gsma.services.rcs.chat.IChatMessage;
import com.gsma.services.rcs.chat.IGroupChat;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.chat.ChatError;
import com.orangelabs.rcs.core.im.chat.ChatMessage;
import com.orangelabs.rcs.core.im.chat.ChatSession;
import com.orangelabs.rcs.core.im.chat.ChatSessionListener;
import com.orangelabs.rcs.core.im.chat.ChatUtils;
import com.orangelabs.rcs.core.im.chat.GroupChatPersistedStorageAccessor;
import com.orangelabs.rcs.core.im.chat.GroupChatSession;
import com.orangelabs.rcs.core.im.chat.cpim.McloudDocument;
import com.orangelabs.rcs.core.im.chat.event.User;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.messaging.GroupChatStateAndReasonCode;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData.ImSessionStartMode;
import com.orangelabs.rcs.service.broadcaster.IGroupChatEventBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Group chat implementation
 * 
 * @author Jean-Marc AUFFRET
 */
public class GroupChatImpl extends IGroupChat.Stub implements ChatSessionListener {

	private final String mChatId;

	private final IGroupChatEventBroadcaster mBroadcaster;

	private final InstantMessagingService mImService;

	private final GroupChatPersistedStorageAccessor mPersistentStorage;

	private final ChatServiceImpl mChatService;

	private final RcsSettings mRcsSettings;

	private final ContactsManager mContactsManager;

	private final MessagingLog mMessagingLog;

	private boolean mGroupChatRejoinedAsPartOfSendOperation = false;

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
	 * @param chatId Chat Id
	 * @param broadcaster IGroupChatEventBroadcaster
	 * @param imService InstantMessagingService
	 * @param persistentStorage GroupChatPersistedStorageAccessor
	 * @param rcsSettings RcsSettings
	 * @param contactsManager ContactsManager
	 * @param chatService ChatServiceImpl
	 * @param messagingLog MessagingLog
	 */
	public GroupChatImpl(String chatId, IGroupChatEventBroadcaster broadcaster,
			InstantMessagingService imService, GroupChatPersistedStorageAccessor persistentStorage,
			RcsSettings rcsSettings, ContactsManager contactsManager, ChatServiceImpl chatService,
			MessagingLog messagingLog) {
		mChatId = chatId;
		mBroadcaster = broadcaster;
		mImService = imService;
		mPersistentStorage = persistentStorage;
		mChatService = chatService;
		mRcsSettings = rcsSettings;
		mContactsManager = contactsManager;
		mMessagingLog = messagingLog;
	}

	private GroupChatStateAndReasonCode toStateAndReasonCode(ChatError error) {
		int chatError = error.getErrorCode();
		switch (chatError) {
			case ChatError.SESSION_INITIATION_CANCELLED:
			case ChatError.SESSION_INITIATION_DECLINED:
				return new GroupChatStateAndReasonCode(GroupChat.State.REJECTED, GroupChat.ReasonCode.REJECTED_BY_REMOTE);
			case ChatError.SESSION_INITIATION_FAILED:
			case ChatError.SESSION_NOT_FOUND:
			case ChatError.SESSION_RESTART_FAILED:
			case ChatError.SUBSCRIBE_CONFERENCE_FAILED:
			case ChatError.UNEXPECTED_EXCEPTION:
				return new GroupChatStateAndReasonCode(GroupChat.State.FAILED, GroupChat.ReasonCode.FAILED_INITIATION);
			case ChatError.MEDIA_SESSION_BROKEN:
			case ChatError.MEDIA_SESSION_FAILED:
				return new GroupChatStateAndReasonCode(GroupChat.State.ABORTED,
						GroupChat.ReasonCode.ABORTED_BY_SYSTEM);
			default:
				throw new IllegalArgumentException(new StringBuilder(
						"Unknown reason in GroupChatImpl.toStateAndReasonCode; chatError=")
						.append(chatError).append("!").toString());
		}
	}

    private int imdnToMessageFailedReasonCode(ImdnDocument imdn) {
        String notificationType = imdn.getNotificationType();
        if (ImdnDocument.DELIVERY_NOTIFICATION.equals(notificationType)) {
            return ChatLog.Message.ReasonCode.FAILED_DELIVERY;

        } else if (ImdnDocument.DISPLAY_NOTIFICATION.equals(notificationType)) {
            return ChatLog.Message.ReasonCode.FAILED_DISPLAY;
        }
        throw new IllegalArgumentException(new StringBuilder(
                "Received invalid imdn notification type:'").append(notificationType).append("'")
                .toString());
    }

	private int sessionAbortedReasonToReasonCode(int reason) {
		switch (reason) {
			case ImsServiceSession.TERMINATION_BY_SYSTEM:
			case ImsServiceSession.TERMINATION_BY_TIMEOUT:
				return GroupChat.ReasonCode.ABORTED_BY_SYSTEM;
			case ImsServiceSession.TERMINATION_BY_USER:
				return GroupChat.ReasonCode.ABORTED_BY_USER;
			default:
				throw new IllegalArgumentException(
						"Unknown reason in GroupChatImpl.sessionAbortedReasonToReasonCode; reason="
								+ reason + "!");
		}
	}

	private void handleSessionRejected(int reasonCode) {
		setRejoinedAsPartOfSendOperation(false);
		synchronized (lock) {
			mChatService.removeGroupChat(mChatId);

			mPersistentStorage.setStateAndReasonCode(GroupChat.State.REJECTED, reasonCode);

			mBroadcaster.broadcastStateChanged(mChatId,
					GroupChat.State.REJECTED, reasonCode);
		}
	}

    private void handleMessageDeliveryStatusDelivered(ContactId contact, String msgId) {
        String mimeType = mMessagingLog.getMessageMimeType(msgId);
        synchronized (lock) {
            mPersistentStorage.setDeliveryInfoStatusAndReasonCode(msgId, contact,
                    GroupDeliveryInfoLog.Status.DELIVERED,
                    GroupDeliveryInfoLog.ReasonCode.UNSPECIFIED);
            mBroadcaster.broadcastMessageGroupDeliveryInfoChanged(mChatId, contact,
                    mimeType, msgId, GroupDeliveryInfoLog.Status.DELIVERED,
                    GroupDeliveryInfoLog.ReasonCode.UNSPECIFIED);
            if (mPersistentStorage.isDeliveredToAllRecipients(msgId)) {
                mPersistentStorage.setMessageStatusAndReasonCode(msgId,
                        ChatLog.Message.Status.Content.DELIVERED,
                        ChatLog.Message.ReasonCode.UNSPECIFIED);
                mBroadcaster.broadcastMessageStatusChanged(mChatId, msgId, mimeType,
                        ChatLog.Message.Status.Content.DELIVERED,
                        ChatLog.Message.ReasonCode.UNSPECIFIED);
            }
        }
    }

    private void handleMessageDeliveryStatusDisplayed(ContactId contact, String msgId) {
        String mimeType = mMessagingLog.getMessageMimeType(msgId);
        synchronized (lock) {
            mPersistentStorage.setDeliveryInfoStatusAndReasonCode(msgId, contact,
                    GroupDeliveryInfoLog.Status.DISPLAYED,
                    GroupDeliveryInfoLog.ReasonCode.UNSPECIFIED);
            mBroadcaster.broadcastMessageGroupDeliveryInfoChanged(mChatId, contact,
                    mimeType, msgId, GroupDeliveryInfoLog.Status.DISPLAYED,
                    GroupDeliveryInfoLog.ReasonCode.UNSPECIFIED);
            if (mPersistentStorage.isDisplayedByAllRecipients(msgId)) {
                mPersistentStorage.setMessageStatusAndReasonCode(msgId,
                        ChatLog.Message.Status.Content.DISPLAYED,
                        ChatLog.Message.ReasonCode.UNSPECIFIED);
                mBroadcaster.broadcastMessageStatusChanged(mChatId, msgId, mimeType,
                        ChatLog.Message.Status.Content.DISPLAYED,
                        ChatLog.Message.ReasonCode.UNSPECIFIED);
            }
        }
    }

    private void handleMessageDeliveryStatusFailed(ContactId contact, String msgId, int reasonCode) {
        String mimeType = mMessagingLog.getMessageMimeType(msgId);
        synchronized (lock) {
            if (ChatLog.Message.ReasonCode.FAILED_DELIVERY == reasonCode) {
                mPersistentStorage.setDeliveryInfoStatusAndReasonCode(msgId, contact,
                        GroupDeliveryInfoLog.Status.FAILED,
                        GroupDeliveryInfoLog.ReasonCode.FAILED_DELIVERY);
                mBroadcaster.broadcastMessageGroupDeliveryInfoChanged(mChatId, contact,
                        mimeType, msgId, GroupDeliveryInfoLog.Status.FAILED,
                        GroupDeliveryInfoLog.ReasonCode.FAILED_DELIVERY);
            } else {
                mPersistentStorage.setDeliveryInfoStatusAndReasonCode(msgId, contact,
                        GroupDeliveryInfoLog.Status.FAILED,
                        GroupDeliveryInfoLog.ReasonCode.FAILED_DISPLAY);
                mBroadcaster.broadcastMessageGroupDeliveryInfoChanged(mChatId, contact,
                        mimeType, msgId, GroupDeliveryInfoLog.Status.FAILED,
                        GroupDeliveryInfoLog.ReasonCode.FAILED_DISPLAY);
            }
        }
    }

	/**
	 * Get chat ID
	 * 
	 * @return Chat ID
	 */
	public String getChatId() {
		return mChatId;
	}
	
	/**
	 * Get remote contact identifier
	 * 
	 * @return ContactId
	 */
	public ContactId getRemoteContact() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return mPersistentStorage.getRemoteContact();
		}
		return session.getRemoteContact();
	}
	
	/**
	 * Returns the direction of the group chat (incoming or outgoing)
	 * 
	 * @return Direction
	 */
	public int getDirection() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return mPersistentStorage.getDirection();
		}
		if (session.isInitiatedByRemote()) {
			return Direction.INCOMING;
		}
		return Direction.OUTGOING;
	}

	/**
	 * Returns the state of the group chat
	 * 
	 * @return State
	 */
	public int getState() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return mPersistentStorage.getState();
		}
		SipDialogPath dialogPath = session.getDialogPath();
		if (dialogPath != null && dialogPath.isSessionEstablished()) {
				return GroupChat.State.STARTED;

		} else if (session.isInitiatedByRemote()) {
			if (session.isSessionAccepted()) {
				return GroupChat.State.ACCEPTING;
			}
			return GroupChat.State.INVITED;
		}
		return GroupChat.State.INITIATING;
	}

	/**
	 * Returns the reason code of the state of the group chat
	 *
	 * @return ReasonCode
	 */
	public int getReasonCode() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return mPersistentStorage.getReasonCode();
		}
		return ReasonCode.UNSPECIFIED;
	}
	
	/**
	 * Is Store & Forward
	 * 
	 * @return Boolean
	 */
	public boolean isStoreAndForward() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			/*
			 * no session means always not "store and forward" as we do not persist
			 * this information.
			 */
			return false;
		}
		return session.isStoreAndForward();
	}
	
	/**
	 * Get subject associated to the session
	 * 
	 * @return String
	 */
	public String getSubject() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return mPersistentStorage.getSubject();
		}
		return session.getSubject();
	}

	/**
	 * Quits a group chat conversation. The conversation will continue between
	 * other participants if there are enough participants.
	 */
	public void leave() {
		final GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null || !ServerApiUtils.isImsConnected()) {
			/*
			 * Quitting group chat that is inactive/ not available due to
			 * network drop should reject the next group chat invitation that is
			 * received
			 */
			mPersistentStorage.setStateAndReasonCode(GroupChat.State.ABORTED,
					GroupChat.ReasonCode.ABORTED_BY_USER);
			mPersistentStorage.setRejectNextGroupChatNextInvitation();
			return;
		}

		if (logger.isActivated()) {
			logger.info("Cancel session");
		}

		// Abort the session
        new Thread() {
    		public void run() {
    			session.abortSession(ImsServiceSession.TERMINATION_BY_USER);
    		}
    	}.start();
	}
	
	/**
	 * Returns the list of participants. A participant is identified
	 * by its MSISDN in national or international format, SIP address, SIP-URI or Tel-URI.
	 * 
	 * @return List of participants
	 */
	public List<ParticipantInfo> getParticipants() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return new ArrayList<ParticipantInfo>(
					mPersistentStorage.getParticipants());
		}

		return new ArrayList<ParticipantInfo>(session.getParticipants());
	}
	
	/**
	 * Returns the max number of participants for a group chat from the group
	 * chat info subscription (this value overrides the provisioning parameter)
	 * 
	 * @return Number
	 */
	public int getMaxParticipants() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			return mPersistentStorage.getMaxParticipants();
		}
		return session.getMaxParticipants();
	}

	/**
	 * Calculate the number of participants who did not decline or left the Group chat.
	 * 
	 * @param setOfParticipant
	 *            the set of participant information
	 * @return the number of participants who did not decline or left the Group chat.
	 */
	private static int getNumberOfParticipants(final Set<ParticipantInfo> participants) {
		int result = 0;
		for (ParticipantInfo participant : participants) {
			switch (participant.getStatus()) {
			case ParticipantInfo.Status.DEPARTED:
			case ParticipantInfo.Status.DECLINED:
				break;
			default:
				result++;
			}
		}
		return result;
	}
	
	/**
	 * Adds participants to a group chat
	 * 
	 * @param participants Set of participants
	 */
	public void addParticipants(final List<ContactId> participants) {
		final GroupChatSession session = mImService.getGroupChatSession(mChatId);
		// tct-stack add begin {
        if (session == null) {
            try {
                setRejoinedAsPartOfSendOperation(true);
                rejoinGroupChat();
                if (participants.size() > 0) {
                    mPendingParticipants = participants;
                }
                return;
            } catch (ServerApiException e) {
                throw new IllegalStateException(
                        "Unable to add participants since rejoin group chat session failed");
            }
        } else {
            mPendingParticipants.clear();
        }
		// } end
		
		if (logger.isActivated()) {
			StringBuilder listOfParticipants = new StringBuilder("Add ");
			for (ContactId contactId : participants) {
				listOfParticipants.append(contactId.toString()).append(" ");
			}
			listOfParticipants.append("participants to the session");
			logger.info(listOfParticipants.toString());
		}

		int maxParticipants = session.getMaxParticipants() - 1;
		// PDD 6.3.5.9 Adding participants to a Group Chat (Clarification)
		// For the maximum user count, the joyn client shall take into
		// account both the active and inactive users,
		// but not those that have explicitly left or declined the Chat.
		int nrOfConnectedParticipants = getNumberOfParticipants(session.getConnectedParticipants());
		if (nrOfConnectedParticipants < maxParticipants) {
			// Add a list of participants to the session
			new Thread() {
				public void run() {
					session.addParticipants(new HashSet<ContactId>(participants));
				}
			}.start();
		} else {
			// Max participants achieved
			for (ContactId participant : participants) {
				handleAddParticipantFailed(participant, "Maximum number of participants reached");
			}
		}
	}

	/**
	 * Add group chat message to Db
	 * @param msg InstantMessage
	 * @param state state of message
	 */
	private void addOutgoingGroupChatMessage(ChatMessage msg, int state) {
		mPersistentStorage.addGroupChatMessage(msg, Direction.OUTGOING, state,
				ReasonCode.UNSPECIFIED);
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
		mBroadcaster.broadcastMessageStatusChanged(mChatId, apiMimeType, msg.getMessageId(),
				state, ReasonCode.UNSPECIFIED);
	}

	/**
	 * Actual send operation of message performed
	 * @param msg Chat message
	 */
	private void sendChatMessage(final ChatMessage msg) {
		final GroupChatSession groupChatSession = mImService.getGroupChatSession(mChatId);
		if (groupChatSession == null) {
			/*
			 * If groupChatSession is not established, queue message and try to
			 * rejoin group chat session
			 */
			addOutgoingGroupChatMessage(msg, Message.Status.Content.QUEUED);
			try {
				setRejoinedAsPartOfSendOperation(true);
				rejoinGroupChat();
				/*
				 * Observe that the queued message above will be dequeued on the
				 * trigger of established rejoined group chat and so the
				 * sendChatMessage method is finished here for now
				 */
				return;

			} catch (ServerApiException e) {
				/*
				 * Failed to rejoin group chat session. Ignoring this exception
				 * because we want to try again later.
				 */
				return;
			}
		}
		SipDialogPath chatSessionDialogPath = groupChatSession.getDialogPath();
		if (chatSessionDialogPath.isSessionEstablished()) {
			addOutgoingGroupChatMessage(msg, Message.Status.Content.SENDING);
			groupChatSession.sendChatMessage(msg);
			return;
		}
		addOutgoingGroupChatMessage(msg, Message.Status.Content.QUEUED);
		if (!groupChatSession.isInitiatedByRemote()) {
			return;
		}
		if (logger.isActivated()) {
			logger.debug("Core chat session is pending: auto accept it.");
		}
		new Thread() {
			public void run() {
				groupChatSession.acceptSession();
			}
		}.start();
	}

	/**
	 * Sends a text message to the group
	 * 
	 * @param text Message
	 * @return Chat message
	 */
	public IChatMessage sendMessage(final String text) {
		ChatMessage msg = ChatUtils.createTextMessage(null, text);
		ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
				mMessagingLog, msg.getMessageId(), msg.getRemoteContact(), text,
				MimeType.TEXT_MESSAGE, mChatId, msg.getDate().getTime(), Direction.OUTGOING);

		/* If the IMS is connected at this time then send this message. */
		if (ServerApiUtils.isImsConnected()) {
			sendChatMessage(msg);
		} else {
			/* If the IMS is NOT connected at this time then queue message. */
			addOutgoingGroupChatMessage(msg, Message.Status.Content.QUEUED);
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
		ChatMessage geolocMsg = ChatUtils.createGeolocMessage(null, geoloc, mImService.getUser().getPublicUri());
		ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
				mMessagingLog, geolocMsg.getMessageId(), geolocMsg.getRemoteContact(),
				geolocMsg.toString(), MimeType.GEOLOC_MESSAGE, mChatId, geolocMsg.getDate()
						.getTime(), Direction.OUTGOING);

		/* If the IMS is connected at this time then send this message. */
		if (ServerApiUtils.isImsConnected()) {
			sendChatMessage(geolocMsg);
		} else {
			/* If the IMS is NOT connected at this time then queue message. */
			addOutgoingGroupChatMessage(geolocMsg, Message.Status.Content.QUEUED);
		}
		return new ChatMessageImpl(persistentStorage);
	}

    /**
	 * Sends an is-composing event. The status is set to true when typing
	 * a message, else it is set to false.
	 * @see RcsSettingsData.ImSessionStartMode
	 * 
	 * @param status Is-composing status
	 */
	public void sendIsComposingEvent(final boolean status) {
		final GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session == null) {
			if (logger.isActivated()) {
				logger.debug("Unable to send composing event '" + status
						+ "' since group chat session found with chatId '" + mChatId
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
	 * Rejoins an existing group chat from its unique chat ID
	 *
	 * @return Group chat
	 * @throws ServerApiException
	 */
	public IGroupChat rejoinGroupChat() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Rejoin group chat session related to the conversation " + mChatId);
		}

		ServerApiUtils.testIms();

		try {
			final ChatSession session = mImService.rejoinGroupChatSession(mChatId);

			new Thread() {
				public void run() {
					session.startSession();
				}
			}.start();
			session.addListener(this);
			mChatService.addGroupChat(this);
			return this;

		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}

	/**
	 * Restarts a previous group chat from its unique chat ID
	 *
	 * @return Group chat
	 * @throws ServerApiException
	 */
	public IGroupChat restartGroupChat() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Restart group chat session related to the conversation " + mChatId);
		}

		ServerApiUtils.testIms();

		try {
			final ChatSession session = mImService.restartGroupChatSession(mChatId);

			new Thread() {
				public void run() {
					session.startSession();
				}
			}.start();
			session.addListener(this);
			mChatService.addGroupChat(this);
			return this;

		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
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
			logger.info("Open a group chat session with chatId " + mChatId);
		}
		try {
			final GroupChatSession session = mImService.getGroupChatSession(mChatId);
			if (session == null) {
				/*
				 * If there is no session ongoing right now then we do not need
				 * to open anything right now so we just return here. A sending
				 * of a new message on this group chat will anyway result in a
				 * rejoin attempt if this group chat has not been left by choice
				 * so we do not need to do anything more here for now.
				 */
				return;
			}
			if (session.getDialogPath().isSessionEstablished()) {
				return;
			}
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
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			// TODO: Exception handling in CR037
		}
	}

	/**
	 * Try to restart group chat session on failure of restart
	 *
	 */
	private void handleGroupChatRejoinAsPartOfSendOperationFailed() {
		try {
			restartGroupChat();

		} catch (ServerApiException e) {
			// failed to restart group chat session. Ignoring this
			// exception because we want to try again later.
		}
	}

	/**
	 * @param enable
	 */
	public void setRejoinedAsPartOfSendOperation(boolean enable) {
		mGroupChatRejoinedAsPartOfSendOperation = enable;
	}

    /*------------------------------- SESSION EVENTS ----------------------------------*/

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.service.ImsSessionListener#handleSessionStarted()
     */
    public void handleSessionStarted() {
    	if (logger.isActivated()) {
			logger.info(new StringBuilder("Session status ").append(GroupChat.State.STARTED)
					.toString());
		}
		setRejoinedAsPartOfSendOperation(false);
		synchronized (lock) {
			GroupChatSession session = mImService.getGroupChatSession(mChatId);
			mPersistentStorage.setRejoinId(session.getImSessionIdentity());

			mBroadcaster.broadcastStateChanged(mChatId, GroupChat.State.STARTED,
					GroupChat.ReasonCode.UNSPECIFIED);
		}
		handleSendQueuedGroupChatMessages();
		handleAddPendingParticipantsWhenSessionEstablished();
        mImService.receiveGroupChatSessionEstablishedEvent(mChatId);
    }
    
	/*
	 * (non-Javadoc)
	 * @see
	 * com.orangelabs.rcs.core.service.ImsSessionListener#handleSessionAborted
	 * (int)
	 */
	public void handleSessionAborted(int reason) {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session != null && session.isPendingForRemoval()) {
			/*
			 * If there is an ongoing group chat session with same chatId, this
			 * session has to be silently aborted so after aborting the session we
			 * make sure to not call the rest of this method that would otherwise
			 * abort the "current" session also and the GroupChat as a whole which
			 * is of course not the intention here
			 */
			if (logger.isActivated()) {
				logger.info(new StringBuilder("Session marked pending for removal status ")
						.append(GroupChat.State.ABORTED).append(" reason ").append(reason)
						.toString());
			}
			return;
		}
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Session status ").append(GroupChat.State.ABORTED)
					.append(" reason ").append(reason).toString());
		}
		setRejoinedAsPartOfSendOperation(false);
		synchronized (lock) {
			mChatService.removeGroupChat(mChatId);

			int reasonCode = sessionAbortedReasonToReasonCode(reason);
			if (ImsServiceSession.TERMINATION_BY_SYSTEM == reason) {
				/*
				 * This error is caused because of a network drop so the group
				 * chat is not set to ABORTED state in this case as it will be
				 * auto-rejoined when network connection is regained
				 */
			} else {
				mPersistentStorage.setStateAndReasonCode(GroupChat.State.ABORTED, reasonCode);
				mBroadcaster.broadcastStateChanged(mChatId, GroupChat.State.ABORTED, reasonCode);
			}
		}
	}
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.service.ImsSessionListener#handleSessionTerminatedByRemote()
     */
	public void handleSessionTerminatedByRemote() {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		if (session != null && session.isPendingForRemoval()) {
			/*
			 * If there is an ongoing group chat session with same chatId, this
			 * session has to be silently aborted so after aborting the session
			 * we make sure to not call the rest of this method that would
			 * otherwise abort the "current" session also and the GroupChat as a
			 * whole which is of course not the intention here
			 */
			if (logger.isActivated()) {
				logger.info(new StringBuilder("Session marked pending for removal status ")
						.append(GroupChat.State.ABORTED).append(" reason ")
						.append(GroupChat.ReasonCode.ABORTED_BY_REMOTE).toString());
			}
			return;
		}
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Session status ").append(GroupChat.State.ABORTED)
					.append(" reason ").append(GroupChat.ReasonCode.ABORTED_BY_REMOTE).toString());
		}
		setRejoinedAsPartOfSendOperation(false);
		synchronized (lock) {
			mChatService.removeGroupChat(mChatId);

			mPersistentStorage.setStateAndReasonCode(GroupChat.State.ABORTED,
					GroupChat.ReasonCode.ABORTED_BY_REMOTE);

			mBroadcaster.broadcastStateChanged(mChatId,
					GroupChat.State.ABORTED, GroupChat.ReasonCode.ABORTED_BY_REMOTE);
		}
	}
    
    /*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleReceiveMessage
	 * (com.orangelabs.rcs.core.service.im.chat.ChatMessage, boolean)
	 */
	public void handleReceiveMessage(ChatMessage msg, boolean imdnDisplayedRequested) {
		String msgId = msg.getMessageId();
		if (logger.isActivated()) {
			logger.info(new StringBuilder("New IM with messageId '").append(msgId)
					.append("' received.").toString());
		}
		synchronized (lock) {
			mPersistentStorage.addGroupChatMessage(msg, Direction.INCOMING,
					ChatLog.Message.Status.Content.RECEIVED, ChatLog.Message.ReasonCode.UNSPECIFIED);
			mContactsManager.setContactDisplayName(msg.getRemoteContact(),
					msg.getDisplayName());

			String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg
					.getMimeType());
			mBroadcaster.broadcastMessageReceived(apiMimeType, msgId);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#handleImError
	 * (com.orangelabs.rcs.core.service.im.chat.ChatError)
	 */
	public void handleImError(ChatError error) {
		GroupChatSession session = mImService.getGroupChatSession(mChatId);
		int chatErrorCode = error.getErrorCode();
		if (session != null && session.isPendingForRemoval()) {
			/*
			 * If there is an ongoing group chat session with same chatId, this
			 * session has to be silently aborted so after aborting the session we
			 * make sure to not call the rest of this method that would otherwise
			 * abort the "current" session also and the GroupChat as a whole which
			 * is of course not the intention here
			 */
			if (logger.isActivated()) {
				logger.info(new StringBuilder("Session marked pending for removal - Error ")
						.append(chatErrorCode).toString());
			}
			return;
		}
		if (logger.isActivated()) {
			logger.info(new StringBuilder("IM error ").append(chatErrorCode).toString());
		}
		setRejoinedAsPartOfSendOperation(false);
		synchronized (lock) {
			mChatService.removeGroupChat(mChatId);

			if (ChatError.SESSION_NOT_FOUND == chatErrorCode) {
				if (mGroupChatRejoinedAsPartOfSendOperation) {
					handleGroupChatRejoinAsPartOfSendOperationFailed();
				}
				return;
			}

			GroupChatStateAndReasonCode stateAndReasonCode = toStateAndReasonCode(error);
			int state = stateAndReasonCode.getState();
			int reasonCode = stateAndReasonCode.getReasonCode();
			if (ChatError.MEDIA_SESSION_FAILED == chatErrorCode) {
				/*
				 * This error is caused because of a network drop so the group
				 * chat is not set to ABORTED state in this case as it will be
				 * auto-rejoined when network connection is regained
				 */
			} else {
				mPersistentStorage.setStateAndReasonCode(state, reasonCode);
				mBroadcaster.broadcastStateChanged(mChatId, state, reasonCode);
			}
		}
	}

	@Override
	public void handleIsComposingEvent(ContactId contact, boolean status) {
		if (logger.isActivated()) {
			logger.info(new StringBuilder().append(contact).append(" is composing status set to ")
					.append(status).toString());
		}
    	synchronized(lock) {
			// Notify event listeners
			mBroadcaster.broadcastComposingEvent(mChatId, contact, status);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleMessageSending
	 * (com.orangelabs.rcs.core.service.im.chat.ChatMessage)
	 */
	@Override
	public void handleMessageSending(ChatMessage msg) {
		String msgId = msg.getMessageId();
		String networkMimeType = msg.getMimeType();
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Message is being sent; msgId=").append(msgId)
					.append("networkMimeType=").append(networkMimeType).append(".").append(".").toString());
		}
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(networkMimeType);
		synchronized (lock) {
			mPersistentStorage.setMessageStatusAndReasonCode(msgId,
					ChatLog.Message.Status.Content.SENDING, ChatLog.Message.ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastMessageStatusChanged(mChatId, apiMimeType, msgId,
					ChatLog.Message.Status.Content.SENDING, ReasonCode.UNSPECIFIED);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleMessageFailedSend
	 * (com.orangelabs.rcs.core.service.im.chat.ChatMessage)
	 */
	@Override
	public void handleMessageFailedSend(String msgId, String mimeType) {
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Message sending failed; msgId=").append(msgId)
					.append("mimeType=").append(mimeType).append(".").toString());
		}

		synchronized (lock) {
			mPersistentStorage.setMessageStatusAndReasonCode(msgId,
					ChatLog.Message.Status.Content.FAILED, ChatLog.Message.ReasonCode.FAILED_SEND);

			mBroadcaster.broadcastMessageStatusChanged(getChatId(), mimeType, msgId,
					ChatLog.Message.Status.Content.FAILED, ChatLog.Message.ReasonCode.FAILED_SEND);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#
	 * handleMessageSent(com.orangelabs.rcs.core.service.im.chat.ChatMessage)
	 */
	@Override
	public void handleMessageSent(String msgId, String mimeType) {
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Text message sent; msgId=").append(msgId)
					.append("mimeType=").append(mimeType).append(".").toString());
		}

		synchronized (lock) {
			mPersistentStorage.setMessageStatusAndReasonCode(msgId,
					ChatLog.Message.Status.Content.SENT, ChatLog.Message.ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastMessageStatusChanged(getChatId(), mimeType, msgId,
					ChatLog.Message.Status.Content.SENT, ChatLog.Message.ReasonCode.UNSPECIFIED);
		}
	}

	@Override
    public void handleConferenceEvent(ContactId contact, String contactDisplayname, String state) {
    	if (logger.isActivated()) {
			logger.info("New conference event " + state + " for " + contact);
		}
    	synchronized(lock) {
			if (User.STATE_CONNECTED.equals(state)) {
				mPersistentStorage.addGroupChatEvent(mChatId,
						contact, Message.Status.System.JOINED);
				mBroadcaster.broadcastParticipantInfoStatusChanged(mChatId,
						new ParticipantInfo(contact, ParticipantInfo.Status.CONNECTED));

			} else if (User.STATE_DISCONNECTED.equals(state)) {
				mPersistentStorage.addGroupChatEvent(mChatId,
						contact, Message.Status.System.DISCONNECTED);

				mBroadcaster.broadcastParticipantInfoStatusChanged(mChatId,
						new ParticipantInfo(contact, ParticipantInfo.Status.DISCONNECTED));

			} else if (User.STATE_DEPARTED.equals(state)) {
				mPersistentStorage.addGroupChatEvent(mChatId,
						contact, Message.Status.System.GONE);

				mBroadcaster.broadcastParticipantInfoStatusChanged(mChatId,
						new ParticipantInfo(contact, ParticipantInfo.Status.DEPARTED));
			}
	    }
    }

    @Override
    public void handleMessageDeliveryStatus(ContactId contact, ImdnDocument imdn) {
        String status = imdn.getStatus();
        String msgId = imdn.getMsgId();
        if (logger.isActivated()) {
            logger.info(new StringBuilder("Handling message delivery status; contact=")
                    .append(contact).append(", msgId=").append(msgId).append(", status=")
                    .append(status).append(", notificationType=")
                    .append(imdn.getNotificationType()).toString());
        }
        if (ImdnDocument.DELIVERY_STATUS_DELIVERED.equals(status)) {
            handleMessageDeliveryStatusDelivered(contact, msgId);
        } else if (ImdnDocument.DELIVERY_STATUS_DISPLAYED.equals(status)) {
            handleMessageDeliveryStatusDisplayed(contact, msgId);
        } else if (ImdnDocument.DELIVERY_STATUS_ERROR.equals(status)
                || ImdnDocument.DELIVERY_STATUS_FAILED.equals(status)
                || ImdnDocument.DELIVERY_STATUS_FORBIDDEN.equals(status)) {
            int reasonCode = imdnToMessageFailedReasonCode(imdn);
            handleMessageDeliveryStatusFailed(contact, msgId, reasonCode);
        }
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#handleAddParticipantSuccessful(com.gsma.services.rcs.contact.ContactId)
     */
	public void handleAddParticipantSuccessful(ContactId contact) {
		if (logger.isActivated()) {
			logger.info("Add participant request is successful");
		}
		synchronized (lock) {
			mBroadcaster.broadcastParticipantInfoStatusChanged(mChatId,
					new ParticipantInfo(contact, ParticipantInfo.Status.CONNECTED));
		}
	}

    /**
     * Request to add participant has failed
     *
     * @param contact Contact ID
     * @param reason Error reason
     */
	public void handleAddParticipantFailed(ContactId contact, String reason) {
		if (logger.isActivated()) {
			logger.info("Add participant request has failed " + reason);
		}
		synchronized (lock) {
			mBroadcaster.broadcastParticipantInfoStatusChanged(mChatId,
					new ParticipantInfo(contact, ParticipantInfo.Status.FAILED));
		}
	}

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.service.im.chat.ChatSessionListener#handleParticipantStatusChanged(com.gsma.services.rcs.chat.ParticipantInfo)
     */
	public void handleParticipantStatusChanged(ParticipantInfo participantInfo) {
		if (logger.isActivated()) {
			logger.info("handleParticipantStatusChanged " + participantInfo);
		}
		synchronized (lock) {
			mBroadcaster.broadcastParticipantInfoStatusChanged(mChatId,
					participantInfo);
		}
	}

	@Override
	public void handleSessionAccepted() {
		if (logger.isActivated()) {
			logger.info("Accepting group chat session");
		}
		synchronized (lock) {
			mPersistentStorage.setStateAndReasonCode(GroupChat.State.ACCEPTING,
					GroupChat.ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastStateChanged(mChatId,
					GroupChat.State.ACCEPTING, GroupChat.ReasonCode.UNSPECIFIED);
		}
	}

	@Override
	public void handleSessionRejectedByUser() {
		if (logger.isActivated()) {
			logger.info("Session rejected by user");
		}
		handleSessionRejected(GroupChat.ReasonCode.REJECTED_BY_USER);
	}

	@Override
	public void handleSessionRejectedByTimeout() {
		if (logger.isActivated()) {
			logger.info("Session rejected by time out");
		}
		handleSessionRejected(GroupChat.ReasonCode.REJECTED_TIME_OUT);
	}

	@Override
	public void handleSessionRejectedByRemote() {
		if (logger.isActivated()) {
			logger.info("Session rejected by time out");
		}
		handleSessionRejected(GroupChat.ReasonCode.REJECTED_BY_REMOTE);
	}

	@Override
	public void handleSessionInvited() {
		if (logger.isActivated()) {
			logger.info("Invited to group chat session");
		}
		synchronized (lock) {
			GroupChatSession session = mImService.getGroupChatSession(mChatId);
			mPersistentStorage.addGroupChat( session.getRemoteContact(), getSubject(), session.getParticipants(),
					GroupChat.State.INVITED, ReasonCode.UNSPECIFIED, Direction.INCOMING);
		}

		mBroadcaster.broadcastInvitation(mChatId);
	}

	@Override
	public void handleSessionAutoAccepted() {
		if (logger.isActivated()) {
			logger.info("Session auto accepted");
		}
		synchronized (lock) {
			GroupChatSession session = mImService.getGroupChatSession(mChatId);
			mPersistentStorage.addGroupChat( session.getRemoteContact(), getSubject(), session.getParticipants(),
					GroupChat.State.ACCEPTING, ReasonCode.UNSPECIFIED, Direction.INCOMING);
		}

		mBroadcaster.broadcastInvitation(mChatId);
	}


    /**********************************************************************************
     *  tct-stack add for fix bug: don't send queued chat && rejoin when adding participants
     **********************************************************************************/

    /**
     * get group chat message from Db
     *
     * @param state state of message
     * @return Set<ChatMessage> ChatMessages
     */
    private Set<ChatMessage> getOutgoingGroupChatMessages(int state) {
        Set<ChatMessage> chatMessages = new HashSet<ChatMessage>();
        Set<String> messageIds = mPersistentStorage.getGroupChatMessageIds(Direction.OUTGOING, state, ReasonCode.UNSPECIFIED);
        for (String msgId : messageIds) {
            ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                    mMessagingLog, msgId);
            ChatMessage msg = ChatUtils.createChatMessage(msgId, null, persistentStorage.getContent(),
                    persistentStorage.getMimeType(), null, null);
           chatMessages.add(msg);
        }
        return chatMessages;
    }

    /**
     * Try to send queued messages when group chat session is established
     */
    private void handleSendQueuedGroupChatMessages() {
        GroupChatSession groupChatSession = mImService.getGroupChatSession(mChatId);
        SipDialogPath chatSessionDialogPath = groupChatSession.getDialogPath();
        if (chatSessionDialogPath.isSessionEstablished()) {
            Set<ChatMessage> chatMessages = getOutgoingGroupChatMessages(Message.Status.Content.QUEUED);
            for (ChatMessage msg : chatMessages) {
                groupChatSession.sendChatMessage(msg);
            }
        }
    }

    /**
     * Pending participants to add for group session is not established
     */
    private List<ContactId> mPendingParticipants = new ArrayList<ContactId>();

    /**
     * Try to send queued messages when group chat session is established
     */
    private void handleAddPendingParticipantsWhenSessionEstablished() {
        if (mPendingParticipants.size() > 0) {
            List<ContactId> participants = new ArrayList<ContactId>();
            for (ContactId contact : mPendingParticipants) {
                participants.add(contact);
            }
            addParticipants(participants);
            mPendingParticipants.clear();
        }
    }

    /**********************************************************************************
     *  tct-stack add for CMCC Mcloud/Vemoticon message
     **********************************************************************************/

    /**
     * Sends a mcloud message
     * 
     * @param mcloud Mcloud
     * @return ChatMessage
     */
    public IChatMessage sendMcloudMessage(Mcloud mcloud) {
        ChatMessage mcloudMsg = ChatUtils.createMcloudMessage(mChatId, null, mcloud);
        ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                mMessagingLog, mcloudMsg.getMessageId(), mcloudMsg.getRemoteContact(),
                mcloudMsg.getContent(), McloudDocument.MIME_TYPE, mChatId, mcloudMsg.getDate()
                        .getTime(), Direction.OUTGOING);

        /* If the IMS is connected at this time then send this message. */
        if (ServerApiUtils.isImsConnected()) {
            sendChatMessage(mcloudMsg);
        } else {
            /* If the IMS is NOT connected at this time then queue message. */
            addOutgoingGroupChatMessage(mcloudMsg, Message.Status.Content.QUEUED);
        }
        return new ChatMessageImpl(persistentStorage);
    }

    /**
     * Sends a vemoticon message
     * 
     * @param vemoticon Vemoticon
     * @return ChatMessage
     */
    public IChatMessage sendVemoticonMessage(Vemoticon vemoticon) {
        ChatMessage vemoticonMsg = ChatUtils.createVemoticonMessage(mChatId, null, vemoticon);
        ChatMessagePersistedStorageAccessor persistentStorage = new ChatMessagePersistedStorageAccessor(
                mMessagingLog, vemoticonMsg.getMessageId(), vemoticonMsg.getRemoteContact(),
                vemoticonMsg.getContent(), McloudDocument.MIME_TYPE, mChatId, vemoticonMsg.getDate()
                        .getTime(), Direction.OUTGOING);

        /* If the IMS is connected at this time then send this message. */
        if (ServerApiUtils.isImsConnected()) {
            sendChatMessage(vemoticonMsg);
        } else {
            /* If the IMS is NOT connected at this time then queue message. */
            addOutgoingGroupChatMessage(vemoticonMsg, Message.Status.Content.QUEUED);
        }
        return new ChatMessageImpl(persistentStorage);
    }

}
