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

package com.orangelabs.rcs.provider.messaging;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import com.gsma.services.rcs.RcsCommon.Direction;
import com.gsma.services.rcs.RcsCommon.ReadStatus;
import com.gsma.services.rcs.chat.ChatLog;
import com.gsma.services.rcs.chat.ChatLog.Message.MimeType;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.im.chat.ChatMessage;
import com.orangelabs.rcs.core.im.chat.ChatUtils;
import com.orangelabs.rcs.provider.LocalContentResolver;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Class to interface the message table
 *
 */
public class MessageLog implements IMessageLog {
	

	/**
	 * Id of chat room
	 */
	static final String KEY_CHAT_ID = ChatLog.Message.CHAT_ID;

	/**
	 * ContactId formatted number of remote contact or null if the message is an
	 * outgoing group chat message.
	 */
	static final String KEY_CONTACT = ChatLog.Message.CONTACT;

	/**
	 * Id of the message
	 */
	static final String KEY_MESSAGE_ID = ChatLog.Message.MESSAGE_ID;

	/**
	 * Content of the message (as defined by one of the mimetypes in
	 * ChatLog.Message.Mimetype)
	 */
	static final String KEY_CONTENT = ChatLog.Message.CONTENT;

	/**
	 * Multipurpose Internet Mail Extensions (MIME) type of message
	 */
	static final String KEY_MIME_TYPE = ChatLog.Message.MIME_TYPE;

	/**
	 * Status direction of message.
	 *
	 * @see com.gsma.services.rcs.RcsCommon.Direction for the list of directions
	 */
	static final String KEY_DIRECTION = ChatLog.Message.DIRECTION;	

	/**
	 * @see ChatLog.Message.Status.Content for the list of status.
	 */
	static final String KEY_STATUS = ChatLog.Message.STATUS;

	/**
	 * Reason code associated with the message status.
	 *
	 * @see ChatLog.Message.ReasonCode for the list of reason codes
	 */
	static final String KEY_REASON_CODE = ChatLog.Message.REASON_CODE;

	/**
	 * This is set on the receiver side when the message has been displayed.
	 *
	 * @see com.gsma.services.rcs.RcsCommon.ReadStatus for the list of status.
	 */
	static final String KEY_READ_STATUS = ChatLog.Message.READ_STATUS;

	/**
	 * Time when message inserted
	 */
	static final String KEY_TIMESTAMP = ChatLog.Message.TIMESTAMP;
	
	/**
	 * Time when message sent. If 0 means not sent.
	 */
    static final String KEY_TIMESTAMP_SENT = ChatLog.Message.TIMESTAMP_SENT;
    
	/**
	 * Time when message delivered. If 0 means not delivered
	 */
    static final String KEY_TIMESTAMP_DELIVERED = ChatLog.Message.TIMESTAMP_DELIVERED;
    
	/**
	 * Time when message displayed. If 0 means not displayed.
	 */
    static final String KEY_TIMESTAMP_DISPLAYED = ChatLog.Message.TIMESTAMP_DISPLAYED;

    static final String KEY_CONV_ID = ChatLog.Message.CONV_ID;

    
    private LocalContentResolver mLocalContentResolver;

	private GroupChatLog groupChatLog;

	private GroupDeliveryInfoLog groupChatDeliveryInfoLog;
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(MessageLog.class.getSimpleName());

	private static final String[] PROJECTION_MESSAGE_ID = new String[] { MessageLog.KEY_MESSAGE_ID };

	private static final int FIRST_COLUMN_IDX = 0;

	/**
	 * Constructor
	 *
	 * @param localContentResolver
	 *            Local content resolver
	 * @param groupChatLog
	 * @param groupChatDeliveryInfoLog
	 */
	/* package private */MessageLog(LocalContentResolver localContentResolver, GroupChatLog groupChatLog, GroupDeliveryInfoLog groupChatDeliveryInfoLog) {
		mLocalContentResolver = localContentResolver;
		this.groupChatLog = groupChatLog;
		this.groupChatDeliveryInfoLog = groupChatDeliveryInfoLog;
	}

	private void addIncomingOneToOneMessage(ChatMessage msg, int status,
			int reasonCode) {
		ContactId contact = msg.getRemoteContact();
		String msgId = msg.getMessageId();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add incoming chat message: contact=")
					.append(contact).append(", msg=").append(msgId).append(", status=")
					.append(status).append(", reasonCode=").append(reasonCode).append(".")
					.toString());
		}

		ContentValues values = new ContentValues();
		values.put(MessageLog.KEY_CONV_ID, msg.getConversationId());// @tct-stack modified
		values.put(MessageLog.KEY_CHAT_ID, contact.toString());
		values.put(MessageLog.KEY_MESSAGE_ID, msgId);
		values.put(MessageLog.KEY_CONTACT, contact.toString());
		values.put(MessageLog.KEY_DIRECTION, Direction.INCOMING);
		values.put(MessageLog.KEY_READ_STATUS, ReadStatus.UNREAD);
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
		values.put(MessageLog.KEY_MIME_TYPE, apiMimeType);
		values.put(MessageLog.KEY_CONTENT, ChatUtils.networkContentToPersistedContent(apiMimeType,
				msg.getContent()));

		values.put(MessageLog.KEY_TIMESTAMP, msg.getDate().getTime());
		values.put(MessageLog.KEY_TIMESTAMP_SENT, 0);
		values.put(MessageLog.KEY_TIMESTAMP_DELIVERED, 0);
		values.put(MessageLog.KEY_TIMESTAMP_DISPLAYED, 0);

		values.put(MessageLog.KEY_STATUS, status);
		values.put(MessageLog.KEY_REASON_CODE, reasonCode);
		mLocalContentResolver.insert(ChatLog.Message.CONTENT_URI, values);
	}

	/**
	 * Add outgoing one-to-one chat message
	 *
	 * @param msg Chat message
	 * @param status Status
	 * @param reasonCode Reason code
	 */
	@Override
	public void addOutgoingOneToOneChatMessage(ChatMessage msg, int status,
			int reasonCode) {
		ContactId contact = msg.getRemoteContact();
		String msgId = msg.getMessageId();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add outgoing chat message: contact=")
					.append(contact).append(", msg=").append(msgId).append(", status=")
					.append(status).append(", reasonCode=").append(reasonCode).append(".")
					.toString());
		}
		ContentValues values = new ContentValues();
		values.put(MessageLog.KEY_CONV_ID, msg.getConversationId());// @tct-stack modified
		values.put(MessageLog.KEY_CHAT_ID, contact.toString());
		values.put(MessageLog.KEY_MESSAGE_ID, msgId);
		values.put(MessageLog.KEY_CONTACT, contact.toString());
		values.put(MessageLog.KEY_DIRECTION, Direction.OUTGOING);
		values.put(MessageLog.KEY_READ_STATUS, ReadStatus.UNREAD);
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
		values.put(MessageLog.KEY_MIME_TYPE, apiMimeType);
		values.put(MessageLog.KEY_CONTENT,
				ChatUtils.networkContentToPersistedContent(apiMimeType, msg.getContent()));

		values.put(MessageLog.KEY_TIMESTAMP, msg.getDate().getTime());
		values.put(MessageLog.KEY_TIMESTAMP_SENT, msg.getDate().getTime());
		values.put(MessageLog.KEY_TIMESTAMP_DELIVERED, 0);
		values.put(MessageLog.KEY_TIMESTAMP_DISPLAYED, 0);

		values.put(MessageLog.KEY_STATUS, status);
		values.put(MessageLog.KEY_REASON_CODE, reasonCode);
		mLocalContentResolver.insert(ChatLog.Message.CONTENT_URI, values);
	}

	@Override
	public void addOneToOneSpamMessage(ChatMessage msg) {
		addIncomingOneToOneMessage(msg, ChatLog.Message.Status.Content.REJECTED,
				ChatLog.Message.ReasonCode.REJECTED_SPAM);
	}

	/**
	 * Add incoming one-to-one chat message
	 *
	 * @param msg Chat message
	 * @param imdnDisplayedRequested Indicates whether IMDN display was requested
	 */
	@Override
	public void addIncomingOneToOneChatMessage(ChatMessage msg, boolean imdnDisplayedRequested) {
		if (imdnDisplayedRequested) {
			addIncomingOneToOneMessage(msg,
					ChatLog.Message.Status.Content.DISPLAY_REPORT_REQUESTED,
					ChatLog.Message.ReasonCode.UNSPECIFIED);

		} else {
			addIncomingOneToOneMessage(msg, ChatLog.Message.Status.Content.RECEIVED,
					ChatLog.Message.ReasonCode.UNSPECIFIED);
		}
	}

	/**
	 * Add group chat message
	 *
	 * @param chatId Chat ID
	 * @param msg Chat message
	 * @param msg direction Direction
	 * @param status Status
	 * @param reasonCode Reason code
	 */
	@Override
	public void addGroupChatMessage(String chatId, ChatMessage msg, int direction, int status, int reasonCode) {
		String msgId = msg.getMessageId();
		ContactId contact = msg.getRemoteContact() ;
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add group chat message; chatId=").append(chatId)
					.append(", msg=").append(msgId).append(", dir=").append(direction)
					.append(", contact=").append(contact).append(".").toString());
		}
		ContentValues values = new ContentValues();
		values.put(MessageLog.KEY_CHAT_ID, chatId);
		values.put(MessageLog.KEY_MESSAGE_ID, msgId);
		if (contact != null) {
			values.put(MessageLog.KEY_CONTACT, contact.toString());
		}
		values.put(MessageLog.KEY_DIRECTION, direction);
		values.put(MessageLog.KEY_READ_STATUS, ReadStatus.UNREAD);
		values.put(MessageLog.KEY_STATUS, status);
		values.put(MessageLog.KEY_REASON_CODE, reasonCode);
		String apiMimeType = ChatUtils.networkMimeTypeToApiMimeType(msg.getMimeType());
		values.put(MessageLog.KEY_MIME_TYPE, apiMimeType);
		values.put(MessageLog.KEY_CONTENT,
				ChatUtils.networkContentToPersistedContent(apiMimeType, msg.getContent()));

		if (direction == Direction.INCOMING) {
			// Receive message
			values.put(MessageLog.KEY_TIMESTAMP, msg.getDate().getTime());
			values.put(MessageLog.KEY_TIMESTAMP_SENT, 0);
			values.put(MessageLog.KEY_TIMESTAMP_DELIVERED, 0);
			values.put(MessageLog.KEY_TIMESTAMP_DISPLAYED, 0);
		} else {
			// Send message
			values.put(MessageLog.KEY_TIMESTAMP, msg.getDate().getTime());
			values.put(MessageLog.KEY_TIMESTAMP_SENT, msg.getDate().getTime());
			values.put(MessageLog.KEY_TIMESTAMP_DELIVERED, 0);
			values.put(MessageLog.KEY_TIMESTAMP_DISPLAYED, 0);
		}
		mLocalContentResolver.insert(ChatLog.Message.CONTENT_URI, values);

		if (direction == Direction.OUTGOING) {
			try {
				int deliveryStatus = com.gsma.services.rcs.GroupDeliveryInfoLog.Status.NOT_DELIVERED;
				if (RcsSettings.getInstance().isAlbatrosRelease()) {
					deliveryStatus = com.gsma.services.rcs.GroupDeliveryInfoLog.Status.UNSUPPORTED;
				}
				Set<ParticipantInfo> participants = groupChatLog.getGroupChatConnectedParticipants(chatId);
				for (ParticipantInfo participant : participants) {
					groupChatDeliveryInfoLog.addGroupChatDeliveryInfoEntry(chatId,
							participant.getContact(), msgId, deliveryStatus,
							com.gsma.services.rcs.GroupDeliveryInfoLog.ReasonCode.UNSPECIFIED);
				}
			} catch (Exception e) {
				mLocalContentResolver.delete(Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, msgId), null, null);
				mLocalContentResolver.delete(Uri.withAppendedPath(GroupDeliveryInfoData.CONTENT_URI, msgId), null, null);
				/* TODO: Throw exception */
				if (logger.isActivated()) {
					logger.warn("Group chat message with msgId '" + msgId + "' could not be added to database!");
				}
			}
		}
	}

	@Override
	public void addGroupChatEvent(String chatId, ContactId contact, int status) {
		if (logger.isActivated()) {
			logger.debug("Add group chat system message: chatID=" + chatId + ", contact=" + contact + ", status=" + status);
		}
		ContentValues values = new ContentValues();
		values.put(MessageLog.KEY_CHAT_ID, chatId);
		if (contact != null) {
			values.put(MessageLog.KEY_CONTACT, contact.toString());
		}
		values.put(MessageLog.KEY_MESSAGE_ID, IdGenerator.generateMessageID());
		values.put(MessageLog.KEY_MIME_TYPE, MimeType.GROUPCHAT_EVENT);
		values.put(MessageLog.KEY_STATUS, status);
		values.put(MessageLog.KEY_REASON_CODE, ChatLog.Message.ReasonCode.UNSPECIFIED);
		values.put(MessageLog.KEY_DIRECTION, Direction.IRRELEVANT);
		values.put(GroupChatLog.KEY_TIMESTAMP, Calendar.getInstance().getTimeInMillis());
		values.put(MessageLog.KEY_READ_STATUS, ReadStatus.UNREAD);
		values.put(MessageLog.KEY_TIMESTAMP_SENT, 0);
		values.put(MessageLog.KEY_TIMESTAMP_DELIVERED, 0);
		values.put(MessageLog.KEY_TIMESTAMP_DISPLAYED, 0);
		mLocalContentResolver.insert(ChatLog.Message.CONTENT_URI, values);
	}

	@Override
	public void markMessageAsRead(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Marking chat message as read: msgID=").append(msgId).toString());
		}
		ContentValues values = new ContentValues();
		values.put(MessageLog.KEY_READ_STATUS, ReadStatus.READ);
		values.put(MessageLog.KEY_TIMESTAMP_DISPLAYED, Calendar.getInstance().getTimeInMillis());

		if (mLocalContentResolver.update(Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, msgId), values, null, null) < 1) {
			/* TODO: Throw exception */
			if (logger.isActivated()) {
				logger.warn("There was no message with msgId '" + msgId + "' to mark as read.");
			}
		}
	}

	@Override
	public void setChatMessageStatusAndReasonCode(String msgId, int status, int reasonCode) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Update chat message: msgID=").append(msgId)
					.append(", status=").append(status).append("reasonCode=").append(reasonCode)
					.toString());
		}
		ContentValues values = new ContentValues();
		values.put(MessageLog.KEY_STATUS, status);
		values.put(MessageLog.KEY_REASON_CODE, reasonCode);
		if (status == ChatLog.Message.Status.Content.DELIVERED) {
			values.put(MessageLog.KEY_TIMESTAMP_DELIVERED, Calendar.getInstance()
					.getTimeInMillis());
		}

		if (mLocalContentResolver.update(Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, msgId), values, null, null) < 1) {
			/* TODO: Throw exception */
			if (logger.isActivated()) {
				logger.warn("There was no message with msgId '" + msgId + "' to update status for.");
			}
		}
	}

	@Override
	public void markIncomingChatMessageAsReceived(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Mark incoming chat message status as received for msgID=").append(msgId).toString());
		}
		setChatMessageStatusAndReasonCode(msgId, ChatLog.Message.Status.Content.RECEIVED, ChatLog.Message.ReasonCode.UNSPECIFIED);
	}

	@Override
	public boolean isMessagePersisted(String msgId) {
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, msgId), PROJECTION_MESSAGE_ID, null, null, null);
			return cursor.moveToFirst();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private Cursor getMessageData(String columnName, String msgId) throws SQLException {
		String[] projection = new String[] {
			columnName
		};
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(
					Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, msgId), projection, null,
					null, null);
			if (cursor.moveToFirst()) {
				return cursor;
			}

			throw new SQLException("No row returned while querying for message data with msgId : "
					+ msgId);

		} catch (RuntimeException e) {
			if (cursor != null) {
				cursor.close();
			}
			throw e;
		}
	}

	private int getDataAsInt(Cursor cursor) {
		try {
			return cursor.getInt(FIRST_COLUMN_IDX);

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private long getDataAsLong(Cursor cursor) {
		try {
			return cursor.getLong(FIRST_COLUMN_IDX);

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private String getDataAsString(Cursor cursor) {
		try {
			return cursor.getString(FIRST_COLUMN_IDX);

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public boolean isMessageRead(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Is message read for ").append(msgId).toString());
		}
		return (getDataAsInt(getMessageData(MessageLog.KEY_READ_STATUS, msgId)) == 1);
	}

	@Override
	public long getMessageSentTimestamp(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get message sent timestamp for ").append(msgId)
					.toString());
		}
		return getDataAsLong(getMessageData(MessageLog.KEY_TIMESTAMP_SENT, msgId));
	}

	@Override
	public long getMessageDeliveredTimestamp(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get message delivered timestamp for ").append(msgId)
					.toString());
		}
		return getDataAsLong(getMessageData(MessageLog.KEY_TIMESTAMP_DELIVERED, msgId));
	}

	@Override
	public long getMessageDisplayedTimestamp(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get message displayed timestamp for ").append(msgId)
					.toString());
		}
		return getDataAsLong(getMessageData(MessageLog.KEY_TIMESTAMP_DISPLAYED, msgId));
	}

	@Override
	public int getMessageStatus(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get message status for ").append(msgId).toString());
		}
		return getDataAsInt(getMessageData(MessageLog.KEY_STATUS, msgId));
	}

	@Override
	public int getMessageReasonCode(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get message reason code for ").append(msgId).toString());
		}
		return getDataAsInt(getMessageData(MessageLog.KEY_REASON_CODE, msgId));
	}

	@Override
	public String getMessageMimeType(String msgId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get message MIME-type for ").append(msgId).toString());
		}
		return getDataAsString(getMessageData(MessageLog.KEY_MIME_TYPE, msgId));
	}

	@Override
	public Cursor getCacheableChatMessageData(String msgId) {
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(
					Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, msgId), null, null, null,
					null);
			if (cursor.moveToFirst()) {
				return cursor;
			}

			throw new SQLException("No row returned while querying for message data with msgId : "
					+ msgId);

		} catch (RuntimeException e) {
			if (cursor != null) {
				cursor.close();
			}
			throw e;
		}
	}


    /**********************************************************************************
     * tct-stack add for fix bug: don't send queued message in session mode
     **********************************************************************************/

    /**
     * Get specific chat message ids
     * 
     * @param chatId
     * @param direction
     * @param status
     * @param reasonCode
     * @return Set<String> MessageIds
     */
    private Set<String> getSpecificChatMessageIds(String chatId, int direction, int status, int reasonCode) {
        Set<String> messageIds = new HashSet<String>();
        String[] projection = new String[] { KEY_MESSAGE_ID };
        String selection = new StringBuilder(KEY_CHAT_ID).append("=").append("'").append(chatId).append("'")
                .append(" AND ").append(KEY_DIRECTION).append("=").append(direction)
                .append(" AND ").append(KEY_STATUS).append("=").append(status)
                .append(" AND ").append(KEY_REASON_CODE).append("=").append(reasonCode).toString();

        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(ChatLog.Message.CONTENT_URI, projection, selection, null, null);
            if (cursor.moveToFirst()) {
                do {
                    String msgId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MESSAGE_ID));
                    messageIds.add(msgId);
                } while (cursor.moveToNext());
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return messageIds;
    }

    /**
     * Get one to one chat message ids
     * 
     * @param contact
     * @param direction
     * @param status
     * @param reasonCode
     * @return Set<String> MessageIds
     */
    @Override
    public Set<String> getOneToOneChatMessageIds(ContactId contact, int direction, int status, int reasonCode) {
        // Currently, chat_id of OneToOneChat is contact: ChatLog.Message.CHAT_ID
        return getSpecificChatMessageIds(contact.toString(), direction, status, reasonCode);
    }

    /**
     * Get group chat message ids
     * 
     * @param chatId
     * @param direction
     * @param status
     * @param reasonCode
     * @return Set<String> MessageIds
     */
    public Set<String> getGroupChatMessageIds(String chatId, int direction, int status, int reasonCode) {
        return getSpecificChatMessageIds(chatId, direction, status, reasonCode);
    }

    @Override
    public String getOneToOneChatConversationId(ContactId contact) {
        String[] projection = new String[] { KEY_CONV_ID };
        String selection = new StringBuilder(KEY_CONTACT).append("=")
                .append("'").append(contact).append("'")
                .append(" AND ").append(KEY_CHAT_ID).append("=")
                .append("'").append(contact).append("'").toString();

        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(ChatLog.Message.THREAD_URI, projection, selection, null, null);
            if (cursor.moveToFirst()) {
                String convId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CONV_ID));
                return convId;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

}
