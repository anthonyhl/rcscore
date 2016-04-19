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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import com.gsma.services.rcs.RcsCommon.Direction;
import com.gsma.services.rcs.RcsCommon.ReadStatus;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.ft.FileTransfer;
import com.orangelabs.rcs.core.content.ContentManager;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.provider.LocalContentResolver;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Class to interface the ft table
 *
 */
public class FileTransferLog implements IFileTransferLog {
	
	/**
	 * Database URI
	 */
	protected static final Uri CONTENT_URI = com.gsma.services.rcs.ft.FileTransferLog.CONTENT_URI;

	/**
	 * Unique file transfer identifier
	 */
	static final String KEY_FT_ID = com.gsma.services.rcs.ft.FileTransferLog.FT_ID;

	/**
	 * Id of chat
	 */
	static final String KEY_CHAT_ID = com.gsma.services.rcs.ft.FileTransferLog.CHAT_ID;

	/**
	 * Date of the transfer
	 */
	static final String KEY_TIMESTAMP = com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP;
	
	/**
	 * Time when file is sent. If 0 means not sent.
	 */
    static final String KEY_TIMESTAMP_SENT = com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP_SENT;
    
	/**
	 * Time when file is delivered. If 0 means not delivered.
	 */
    static final String KEY_TIMESTAMP_DELIVERED = com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP_DELIVERED;
    
	/**
	 * Time when file is displayed.
	 */
    static final String KEY_TIMESTAMP_DISPLAYED = com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP_DISPLAYED;	

	/**
	 * ContactId formatted number of remote contact or null if the filetransfer
	 * is an outgoing group file transfer.
	 */
	static final String KEY_CONTACT = com.gsma.services.rcs.ft.FileTransferLog.CONTACT;
	
	/**
	 * @see FileTransfer.State for possible states.
	 */
	static final String KEY_STATE = com.gsma.services.rcs.ft.FileTransferLog.STATE;

	/**
	 * Reason code associated with the file transfer state.
	 *
	 * @see FileTransfer.ReasonCode for possible reason codes.
	 */
	static final String KEY_REASON_CODE = com.gsma.services.rcs.ft.FileTransferLog.REASON_CODE;
	
	/**
	 * @see com.gsma.services.rcs.RcsCommon.ReadStatus for the list of status.
	 */
	static final String KEY_READ_STATUS = com.gsma.services.rcs.ft.FileTransferLog.READ_STATUS;

	/**
	 * Multipurpose Internet Mail Extensions (MIME) type of message
	 */
	static final String KEY_MIME_TYPE = com.gsma.services.rcs.ft.FileTransferLog.MIME_TYPE;
	
	/**
	 * URI of the file
	 */
	static final String KEY_FILE = com.gsma.services.rcs.ft.FileTransferLog.FILE;

	/**
	 * Filename
	 */
	static final String KEY_FILENAME = com.gsma.services.rcs.ft.FileTransferLog.FILENAME;
	
	/**
	 * Size transferred in bytes
	 */
	static final String KEY_TRANSFERRED = com.gsma.services.rcs.ft.FileTransferLog.TRANSFERRED;
	
	/**
	 * File size in bytes
	 */
	static final String KEY_FILESIZE = com.gsma.services.rcs.ft.FileTransferLog.FILESIZE;

	/**
	 * Incoming transfer or outgoing transfer
	 *
	 * @see com.gsma.services.rcs.RcsCommon.Direction for the list of directions
	 */
	static final String KEY_DIRECTION = com.gsma.services.rcs.ft.FileTransferLog.DIRECTION;

	/**
	 * Column name KEY_FILEICON : the URI of the file icon
	 */
	static final String KEY_FILEICON =  com.gsma.services.rcs.ft.FileTransferLog.FILEICON;

	/**
	 * URI of the file icon
	 */
	static final String KEY_FILEICON_MIME_TYPE = com.gsma.services.rcs.ft.FileTransferLog.FILEICON_MIME_TYPE;

	static final String KEY_UPLOAD_TID = "upload_tid";

	static final String KEY_DOWNLOAD_URI = "download_uri";

	private static final String SELECTION_FILE_BY_T_ID = new StringBuilder(KEY_UPLOAD_TID).append("=?").toString();

	private static final String SELECTION_BY_PAUSED_BY_SYSTEM = new StringBuilder(
			KEY_STATE).append("=").append(FileTransfer.State.PAUSED)
			.append(" AND ").append(KEY_REASON_CODE).append("=")
			.append(FileTransfer.ReasonCode.PAUSED_BY_SYSTEM).toString();

	private static final String SELECTION_BY_EQUAL_CHAT_ID_AND_CONTACT = new StringBuilder(
			KEY_FT_ID).append("=? AND ")
			.append(KEY_CHAT_ID).append("=").append(KEY_CONTACT)
			.toString();

	private static final String ORDER_BY_TIMESTAMP_ASC = MessageLog.KEY_TIMESTAMP.concat(" ASC");

	private static final int FIRST_COLUMN_IDX = 0;

	private final LocalContentResolver mLocalContentResolver;

	private final GroupChatLog mGroupChatLog;
	private final GroupDeliveryInfoLog mGroupChatDeliveryInfoLog;
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(FileTransferLog.class.getSimpleName());

	/**
	 * Constructor
	 *
	 * @param localContentResolver
	 *            Local content resolver
	 * @param groupChatLog
	 *            Group chat log
	 * @param groupChatDeliveryInfoLog
	 *            Group chat delivery info log
	 */
	/* package private */FileTransferLog(LocalContentResolver localContentResolver, GroupChatLog groupChatLog,
			GroupDeliveryInfoLog groupChatDeliveryInfoLog) {
		mLocalContentResolver = localContentResolver;
		mGroupChatLog = groupChatLog;
		mGroupChatDeliveryInfoLog = groupChatDeliveryInfoLog;
	}

	@Override
	public void addFileTransfer(String fileTransferId, ContactId contact, int direction,
			MmContent content, MmContent fileIcon, int state, int reasonCode) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add file transfer entry: fileTransferId=")
					.append(fileTransferId).append(", contact=").append(contact)
					.append(", filename=").append(content.getName()).append(", size=")
					.append(content.getSize()).append(", MIME=").append(content.getEncoding())
					.append(", state=").append(state).append(", reasonCode=").append(reasonCode).toString());
		}
		ContentValues values = new ContentValues();
		values.put(KEY_FT_ID, fileTransferId);
		values.put(KEY_CHAT_ID, contact.toString());
		values.put(KEY_CONTACT, contact.toString());
		values.put(KEY_FILE, content.getUri().toString());
		values.put(KEY_FILENAME, content.getName());
		values.put(KEY_MIME_TYPE, content.getEncoding());
		values.put(KEY_DIRECTION, direction);
		values.put(KEY_TRANSFERRED, 0);
		values.put(KEY_FILESIZE, content.getSize());
		if (fileIcon != null) {
			values.put(KEY_FILEICON, fileIcon.getUri().toString());
		}

		long date = Calendar.getInstance().getTimeInMillis();
		values.put(KEY_READ_STATUS, ReadStatus.UNREAD);
		values.put(KEY_STATE, state);
		values.put(KEY_REASON_CODE, reasonCode);
		if (direction == Direction.INCOMING) {
			// Receive file
			values.put(KEY_TIMESTAMP, date);
			values.put(KEY_TIMESTAMP_SENT, 0);
			values.put(KEY_TIMESTAMP_DELIVERED, 0);
			values.put(KEY_TIMESTAMP_DISPLAYED, 0);
		} else {
			// Send file
			values.put(KEY_TIMESTAMP, date);
			values.put(KEY_TIMESTAMP_SENT, date);
			values.put(KEY_TIMESTAMP_DELIVERED, 0);
			values.put(KEY_TIMESTAMP_DISPLAYED, 0);
		}
		mLocalContentResolver.insert(CONTENT_URI, values);
	}

	@Override
	public void addOutgoingGroupFileTransfer(String fileTransferId, String chatId,
			MmContent content, MmContent thumbnail, int state, int reasonCode) {
		if (logger.isActivated()) {
			logger.debug("addOutgoingGroupFileTransfer: fileTransferId=" + fileTransferId + ", chatId=" + chatId + " filename="
					+ content.getName() + ", size=" + content.getSize() + ", MIME=" + content.getEncoding());
		}
		ContentValues values = new ContentValues();
		values.put(KEY_FT_ID, fileTransferId);
		values.put("conv_id", chatId);// tct-stack add tmply
		values.put(KEY_CHAT_ID, chatId);
		values.put(KEY_FILE, content.getUri().toString());
		values.put(KEY_FILENAME, content.getName());
		values.put(KEY_MIME_TYPE, content.getEncoding());
		values.put(KEY_DIRECTION, Direction.OUTGOING);
		values.put(KEY_TRANSFERRED, 0);
		values.put(KEY_FILESIZE, content.getSize());
		long date = Calendar.getInstance().getTimeInMillis();
		values.put(MessageLog.KEY_READ_STATUS, ReadStatus.UNREAD);
		// Send file
		values.put(KEY_TIMESTAMP, date);
		values.put(KEY_TIMESTAMP_SENT, date);
		values.put(KEY_TIMESTAMP_DELIVERED, 0);
		values.put(KEY_TIMESTAMP_DISPLAYED, 0);
		values.put(KEY_STATE, state);
		values.put(KEY_REASON_CODE, reasonCode);
		if (thumbnail != null) {
			values.put(KEY_FILEICON, thumbnail.getUri().toString());
		}
		mLocalContentResolver.insert(CONTENT_URI, values);

		try {
			Set<ParticipantInfo> participants = mGroupChatLog.getGroupChatConnectedParticipants(chatId);
			for (ParticipantInfo participant : participants) {
				mGroupChatDeliveryInfoLog.addGroupChatDeliveryInfoEntry(chatId,
						participant.getContact(), fileTransferId,
						com.gsma.services.rcs.GroupDeliveryInfoLog.Status.NOT_DELIVERED,
						com.gsma.services.rcs.GroupDeliveryInfoLog.ReasonCode.UNSPECIFIED);
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Group file transfer with fileTransferId '" + fileTransferId + "' could not be added to database!", e);
			}
			mLocalContentResolver.delete(Uri.withAppendedPath(CONTENT_URI, fileTransferId), null, null);
			mLocalContentResolver.delete(Uri.withAppendedPath(GroupDeliveryInfoData.CONTENT_URI, fileTransferId), null, null);
			/* TODO: Throw exception */
		}
	}

	@Override
	public void addIncomingGroupFileTransfer(String fileTransferId, String chatId,
			ContactId contact, MmContent content, MmContent fileIcon, int state, int reasonCode) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add incoming file transfer entry: fileTransferId=")
					.append(fileTransferId).append(", chatId=").append(chatId).append(", contact=")
					.append(contact).append(", filename=").append(content.getName())
					.append(", size=").append(content.getSize()).append(", MIME=")
					.append(content.getEncoding()).append(", state=").append(state)
					.append(", reasonCode=").append(reasonCode).toString());
		}
		ContentValues values = new ContentValues();
		values.put(KEY_FT_ID, fileTransferId);
		values.put(KEY_CHAT_ID, chatId);
		values.put(KEY_FILE, content.getUri().toString());
		values.put(KEY_CONTACT, contact.toString());
		values.put(KEY_FILENAME, content.getName());
		values.put(KEY_MIME_TYPE, content.getEncoding());
		values.put(KEY_DIRECTION, Direction.INCOMING);
		values.put(KEY_TRANSFERRED, 0);
		values.put(KEY_FILESIZE, content.getSize());
		values.put(KEY_READ_STATUS, ReadStatus.UNREAD);
		values.put(KEY_STATE, state);
		values.put(KEY_REASON_CODE, reasonCode);

		long date = Calendar.getInstance().getTimeInMillis();
		values.put(KEY_TIMESTAMP, date);
		values.put(KEY_TIMESTAMP_SENT, 0);
		values.put(KEY_TIMESTAMP_DELIVERED, 0);
		values.put(KEY_TIMESTAMP_DISPLAYED, 0);
		if (fileIcon != null) {
			values.put(KEY_FILEICON, fileIcon.getUri().toString());
		}

		mLocalContentResolver.insert(CONTENT_URI, values);
	}

	@Override
	public void setFileTransferStateAndReasonCode(String fileTransferId, int state,
			int reasonCode) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("updateFileTransferStatus: fileTransferId=")
					.append(fileTransferId).append(", state=").append(state)
					.append(", reasonCode=").append(reasonCode).toString());
		}

		ContentValues values = new ContentValues();
		values.put(KEY_STATE, state);
		values.put(KEY_REASON_CODE, reasonCode);
		if (state == FileTransfer.State.DELIVERED) {
			values.put(KEY_TIMESTAMP_DELIVERED, Calendar.getInstance()
					.getTimeInMillis());
		} else if (state == FileTransfer.State.DISPLAYED) {
			values.put(KEY_TIMESTAMP_DISPLAYED, Calendar.getInstance()
					.getTimeInMillis());
		}
		mLocalContentResolver.update(Uri.withAppendedPath(CONTENT_URI, fileTransferId), values, null,
				null);
	}

	@Override
	public void markFileTransferAsRead(String fileTransferId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("markFileTransferAsRead  (fileTransferId=").append(fileTransferId).append(")")
					.toString());
		}
		ContentValues values = new ContentValues();
		values.put(KEY_READ_STATUS, ReadStatus.READ);
		if (mLocalContentResolver.update(Uri.withAppendedPath(CONTENT_URI, fileTransferId), values,
				null, null) < 1) {
			/* TODO: Throw exception */
			if (logger.isActivated()) {
				logger.warn("There was no file with fileTransferId '" + fileTransferId + "' to mark as read.");
			}
		}
	}

	@Override
	public void setFileTransferProgress(String fileTransferId, long currentSize) {
		ContentValues values = new ContentValues();
		values.put(KEY_TRANSFERRED, currentSize);
		mLocalContentResolver.update(Uri.withAppendedPath(CONTENT_URI, fileTransferId), values, null,
				null);
	}

	@Override
	public void setFileTransferred(String fileTransferId, MmContent content) {
		if (logger.isActivated()) {
			logger.debug("updateFileTransferUri (fileTransferId=" + fileTransferId + ") (uri=" + content.getUri() + ")");
		}
		ContentValues values = new ContentValues();
		values.put(KEY_STATE, FileTransfer.State.TRANSFERRED);
		values.put(KEY_REASON_CODE, FileTransfer.ReasonCode.UNSPECIFIED);
		values.put(KEY_TRANSFERRED, content.getSize());
		mLocalContentResolver.update(Uri.withAppendedPath(CONTENT_URI, fileTransferId), values, null,
				null);
	}

	@Override
	public boolean isFileTransfer(String fileTransferId) {
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(Uri.withAppendedPath(CONTENT_URI, fileTransferId),
					new String[] {
						KEY_FT_ID
				}, null, null, null);

			return cursor.moveToFirst();
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Exception occured while determing if it is file transfer", e);
			}
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public void setFileUploadTId(String fileTransferId, String tId) {
		if (logger.isActivated()) {
			logger.debug("updateFileUploadTId (tId=" + tId + ") (fileTransferId=" + fileTransferId
					+ ")");
		}
		ContentValues values = new ContentValues();
		values.put(KEY_UPLOAD_TID, tId);
		mLocalContentResolver.update(Uri.withAppendedPath(CONTENT_URI, fileTransferId), values, null,
				null);
	}

	@Override
	public void setFileDownloadAddress(String fileTransferId, Uri downloadAddress) {
		if (logger.isActivated()) {
			logger.debug("updateFileDownloadAddress (downloadAddress=" + downloadAddress
					+ ") (fileTransferId=" + fileTransferId + ")");
		}
		ContentValues values = new ContentValues();
		values.put(KEY_DOWNLOAD_URI, downloadAddress.toString());
		mLocalContentResolver.update(Uri.withAppendedPath(CONTENT_URI, fileTransferId), values, null,
				null);
	}

	@Override
	public List<FtHttpResume> retrieveFileTransfersPausedBySystem() {
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(CONTENT_URI, null, SELECTION_BY_PAUSED_BY_SYSTEM,
					null, ORDER_BY_TIMESTAMP_ASC);
			if (!cursor.moveToFirst()) {
				return new ArrayList<FtHttpResume>();
			}

			int sizeColumnIdx = cursor.getColumnIndexOrThrow(KEY_TRANSFERRED);
			int mimeTypeColumnIdx = cursor.getColumnIndexOrThrow(KEY_MIME_TYPE);
			int contactColumnIdx = cursor.getColumnIndexOrThrow(KEY_CONTACT);
			int chatIdColumnIdx = cursor.getColumnIndexOrThrow(KEY_CHAT_ID);
			int fileColumnIdx = cursor.getColumnIndexOrThrow(KEY_FILE);
			int fileNameColumnIdx = cursor.getColumnIndexOrThrow(KEY_FILENAME);
			int directionColumnIdx = cursor.getColumnIndexOrThrow(KEY_DIRECTION);
			int fileTransferIdColumnIdx = cursor.getColumnIndexOrThrow(KEY_FT_ID);
			int fileIconColumnIdx = cursor.getColumnIndexOrThrow(KEY_FILEICON);
			int downloadServerAddressColumnIdx = cursor
					.getColumnIndexOrThrow(KEY_DOWNLOAD_URI);
			int tIdColumnIdx = cursor.getColumnIndexOrThrow(KEY_UPLOAD_TID);

			List<FtHttpResume> fileTransfers = new ArrayList<FtHttpResume>();
			do {
				long size = cursor.getLong(sizeColumnIdx);
				String mimeType = cursor.getString(mimeTypeColumnIdx);
				String fileTransferId = cursor.getString(fileTransferIdColumnIdx);
				ContactId contact = null;
				String phoneNumber = cursor.getString(contactColumnIdx);
				try {
					contact = ContactUtils.createContactId(phoneNumber);
				} catch (Exception e) {
					if (logger.isActivated()) {
						logger.error("Cannot parse contact '" + phoneNumber
								+ "' for file transfer with transfer ID '" + fileTransferId + "'");
					}
					continue;
				}
				String chatId = cursor.getString(chatIdColumnIdx);
				String file = cursor.getString(fileColumnIdx);
				String fileName = cursor.getString(fileNameColumnIdx);
				int direction = cursor.getInt(directionColumnIdx);
				String fileIcon = cursor.getString(fileIconColumnIdx);
				boolean isGroup = !contact.toString().equals(chatId);
				if (direction == Direction.INCOMING) {
					String downloadServerAddress = cursor.getString(downloadServerAddressColumnIdx);
					MmContent content = ContentManager.createMmContent(Uri.parse(file), size,
							fileName);
					Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
					fileTransfers.add(new FtHttpResumeDownload(Uri.parse(downloadServerAddress),
							Uri.parse(file), fileIconUri, content, contact, chatId, fileTransferId,
							isGroup));
				} else {
					String tId = cursor.getString(tIdColumnIdx);
					MmContent content = ContentManager.createMmContentFromMime(Uri.parse(file),
							mimeType, size, fileName);
					Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
					fileTransfers.add(new FtHttpResumeUpload(content, fileIconUri, tId, contact,
							chatId, fileTransferId, isGroup));
				}
			} while (cursor.moveToNext());
			return fileTransfers;

		} catch (SQLException e) {
			if (logger.isActivated()) {
				logger.error("Unable to retrieve resumable file transfers!", e);
			}
			return new ArrayList<FtHttpResume>();

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public FtHttpResumeUpload retrieveFtHttpResumeUpload(String tId) {
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(CONTENT_URI, null, SELECTION_FILE_BY_T_ID, new String[] {
				tId
			}, null);

			if (!cursor.moveToFirst()) {
				return null;
			}
			String fileName = cursor.getString(cursor
					.getColumnIndexOrThrow(KEY_FILENAME));
			long size = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TRANSFERRED));
			String mimeType = cursor.getString(cursor
					.getColumnIndexOrThrow(KEY_MIME_TYPE));
			String fileTransferId = cursor.getString(cursor
					.getColumnIndexOrThrow(KEY_FT_ID));
			ContactId contact = null;
			String phoneNumber = cursor.getString(cursor
					.getColumnIndexOrThrow(KEY_CONTACT));
			try {
				contact = ContactUtils.createContactId(phoneNumber);
			} catch (Exception e) {
				if (logger.isActivated()) {
					logger.error("Cannot parse contact '" + phoneNumber
							+ "' for file transfer with transfer ID '" + fileTransferId + "'");
				}
				return null;

			}
			String chatId = cursor.getString(cursor
					.getColumnIndexOrThrow(KEY_CHAT_ID));
			String file = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE));
			String fileIcon = cursor.getString(cursor
					.getColumnIndexOrThrow(KEY_FILEICON));
			boolean isGroup = !contact.toString().equals(chatId);
			MmContent content = ContentManager.createMmContentFromMime(Uri.parse(file), mimeType,
					size, fileName);
			Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
			return new FtHttpResumeUpload(content, fileIconUri, tId, contact, chatId,
					fileTransferId, isGroup);

		} catch (SQLException e) {
			if (logger.isActivated()) {
				logger.error(e.getMessage(), e);
			}
			return null;

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.provider.messaging.IFileTransferLog#
	 * getFileTransferData(java.lang.String, java.lang.String)
	 */
	private Cursor getFileTransferData(String columnName, String fileTransferId) throws SQLException {
		String[] projection = {
			columnName
		};
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(
					Uri.withAppendedPath(CONTENT_URI, fileTransferId), projection,
					null, null, null);
			if (cursor.moveToFirst()) {
				return cursor;
			}

			throw new SQLException(
					"No row returned while querying for file transfer data with fileTransferId : "
							+ fileTransferId);

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

	/*
	 * (non-Javadoc)
	 * @see
	 * com.orangelabs.rcs.provider.messaging.IFileTransferLog#getFileTransferState
	 * (java.lang.String)
	 */
	public int getFileTransferState(String fileTransferId) {
		if (logger.isActivated()) {
			logger.debug("Get file transfer state for ".concat(fileTransferId));
		}
		return getDataAsInt(getFileTransferData(KEY_STATE, fileTransferId));
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.provider.messaging.IFileTransferLog#
	 * getFileTransferStateReasonCode(java.lang.String)
	 */
	public int getFileTransferStateReasonCode(String fileTransferId) {
		if (logger.isActivated()) {
			logger.debug("Get file transfer reason code for ".concat(fileTransferId));
		}
		return getDataAsInt(getFileTransferData(KEY_REASON_CODE, fileTransferId));
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.provider.messaging.IFileTransferLog#
	 * isGroupFileTransfer(java.lang.String)
	 */
	public boolean isGroupFileTransfer(String fileTransferId) {
		String[] projection = new String[] {
			KEY_FT_ID
		};
		String[] selArgs = new String[] {
			fileTransferId
		};
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(CONTENT_URI, projection,
					SELECTION_BY_EQUAL_CHAT_ID_AND_CONTACT, selArgs, null); 
			/*
			 * For a one-to-one file transfer, value of chatID is equal to the
			 * value of contact
			 */
			return !cursor.moveToFirst();

		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.orangelabs.rcs.provider.messaging.IFileTransferLog#
	 * getCacheableFileTransferData(java.lang.String)
	 */
	public Cursor getCacheableFileTransferData(String fileTransferId) throws SQLException {
		Cursor cursor = null;
		try {
			cursor = mLocalContentResolver.query(
					Uri.withAppendedPath(CONTENT_URI, fileTransferId), null,
					null, null, null);
			if (cursor.moveToFirst()) {
				return cursor;
			}

			throw new SQLException(
					"No row returned while querying for file transfer data with fileTransferId : "
							+ fileTransferId);

		} catch (RuntimeException e) {
			if (cursor != null) {
				cursor.close();
			}
			throw e;
		}
	}


    /**********************************************************************************
     *  tct-stack add for fix bug: don't send queued file transfer
     **********************************************************************************/

    /**
     * Get specific status file transfer ids
     * 
     * @param chatId
     * @param direction
     * @param state
     * @param reasonCode
     * @return
     */
    private Set<String> getSpecificFileTransferIds(String chatId, int direction, int state, int reasonCode) {
        Set<String> transferIds = new HashSet<String>();
        String[] projection = new String[] { KEY_FT_ID };
        String selection = new StringBuilder(KEY_CHAT_ID).append("=").append("'").append(chatId).append("'")
                .append(" AND ").append(KEY_DIRECTION).append("=").append(direction)
                .append(" AND ").append(KEY_STATE).append("=").append(state)
                .append(" AND ").append(KEY_REASON_CODE).append("=").append(reasonCode).toString();

        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(CONTENT_URI, projection, selection, null, null);
            if (cursor.moveToFirst()) {
                do {
                    String transferId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FT_ID));
                    transferIds.add(transferId);
                } while (cursor.moveToNext());
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return transferIds;
    }

    /**
     * Get all the file transfer ids of the specific direction & state & reasonCode
     * 
     * @param direction
     * @param state
     * @param reasonCode
     * @return message id set
     */
    @Override
    public Set<String> getAllFileTransferIds(int direction, int state, int reasonCode) {
        Set<String> transferIds = new HashSet<String>();
        String[] projection = new String[] { KEY_FT_ID };
        String selection = new StringBuilder(KEY_DIRECTION).append("=").append(direction)
                .append(" AND ").append(KEY_STATE).append("=").append(state)
                .append(" AND ").append(KEY_REASON_CODE).append("=").append(reasonCode).toString();

        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(CONTENT_URI, projection, selection, null, null);
            if (cursor.moveToFirst()) {
                do {
                    String transferId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FT_ID));
                    transferIds.add(transferId);
                } while (cursor.moveToNext());
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return transferIds;
    }

    /**
     * Get all the group file transfer ids of the specific direction & state & reasonCode
     * 
     * @param chatId
     * @param direction
     * @param state
     * @param reasonCode
     * @return message id set
     */
    @Override
    public Set<String> getGroupFileTransferIds(String chatId, int direction, int state, int reasonCode) {
        return getSpecificFileTransferIds(chatId, direction, state, reasonCode);
    }

    @Override
    public Set<String> getFileTransferIds(ContactId contact, int direction, int state, int reasonCode) {
        // TODO Auto-generated method stub
        return null;
    }


    /**********************************************************************************
     *  tct-stack add for CMCC message modes
     **********************************************************************************/

    /**
     * For one to one chat file transfer extension
     * 
     * 1st. if it's the first msg of this thread, the convId is null, 
     */
    @Override
    public void addFileTransferExt(String fileTransferId, String convId, ContactId contact, int direction,
            MmContent content, MmContent fileIcon, int state, int reasonCode) {
        if (logger.isActivated()) {
            logger.debug(new StringBuilder("Add file transfer extension entry: fileTransferId=")
                    .append(fileTransferId).append(", conversationId=").append(convId)
                    .append(", contact=").append(contact)
                    .append(", filename=").append(content.getName()).append(", size=")
                    .append(content.getSize()).append(", MIME=").append(content.getEncoding())
                    .append(", state=").append(state).append(", reasonCode=").append(reasonCode).toString());
        }
        ContentValues values = new ContentValues();
        values.put(KEY_FT_ID, fileTransferId);
        values.put("conv_id", convId);//tmply TODO
        values.put(KEY_CHAT_ID, contact.toString());
        values.put(KEY_CONTACT, contact.toString());
        values.put(KEY_FILE, content.getUri().toString());
        values.put(KEY_FILENAME, content.getName());
        values.put(KEY_MIME_TYPE, content.getEncoding());
        values.put(KEY_DIRECTION, direction);
        values.put(KEY_TRANSFERRED, 0);
        values.put(KEY_FILESIZE, content.getSize());
        if (fileIcon != null) {
            values.put(KEY_FILEICON, fileIcon.getUri().toString());
        }

        long date = Calendar.getInstance().getTimeInMillis();
        values.put(KEY_READ_STATUS, ReadStatus.UNREAD);
        values.put(KEY_STATE, state);
        values.put(KEY_REASON_CODE, reasonCode);
        if (direction == Direction.INCOMING) {
            // Receive file
            values.put(KEY_TIMESTAMP, date);
            values.put(KEY_TIMESTAMP_SENT, 0);
            values.put(KEY_TIMESTAMP_DELIVERED, 0);
            values.put(KEY_TIMESTAMP_DISPLAYED, 0);
        } else {
            // Send file
            values.put(KEY_TIMESTAMP, date);
            values.put(KEY_TIMESTAMP_SENT, date);
            values.put(KEY_TIMESTAMP_DELIVERED, 0);
            values.put(KEY_TIMESTAMP_DISPLAYED, 0);
        }
        mLocalContentResolver.insert(CONTENT_URI, values);
    }

}
