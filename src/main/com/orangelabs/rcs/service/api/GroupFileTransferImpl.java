/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.orangelabs.rcs.service.api;

import javax2.sip.message.Response;
import android.net.Uri;

import com.gsma.services.rcs.RcsCommon.Direction;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.ft.FileTransfer;
import com.gsma.services.rcs.ft.FileTransfer.ReasonCode;
import com.gsma.services.rcs.ft.IFileTransfer;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingError;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingSessionListener;
import com.orangelabs.rcs.core.im.filetransfer.FileTransferPersistedStorageAccessor;
import com.orangelabs.rcs.core.im.filetransfer.http.HttpFileTransferSession;
import com.orangelabs.rcs.core.im.filetransfer.http.HttpTransferState;
import com.orangelabs.rcs.provider.messaging.FileTransferStateAndReasonCode;
import com.orangelabs.rcs.service.broadcaster.IGroupFileTransferBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * File transfer implementation
 */
public class GroupFileTransferImpl extends IFileTransfer.Stub implements FileSharingSessionListener {

	private final String mFileTransferId;

	private final IGroupFileTransferBroadcaster mBroadcaster;

	private final InstantMessagingService mImService;

	private final FileTransferPersistedStorageAccessor mPersistentStorage;

	private final FileTransferServiceImpl mFileTransferService;

	private String mChatId;

	/**
	 * Lock used for synchronization
	 */
	private final Object lock = new Object();

	/**
	 * The logger
	 */
	private final static Logger logger = Logger.getLogger(GroupFileTransferImpl.class
			.getSimpleName());


	/**
	 * Constructor
	 * 
	 * @param transferId Transfer ID
	 * @param broadcaster IGroupFileTransferBroadcaster
	 * @param imService InstantMessagingService
	 * @param storageAccessor FileTransferPersistedStorageAccessor
	 * @param fileTransferService FileTransferServiceImpl
	 */
	public GroupFileTransferImpl(String transferId, IGroupFileTransferBroadcaster broadcaster,
			InstantMessagingService imService,
			FileTransferPersistedStorageAccessor storageAccessor, FileTransferServiceImpl fileTransferService) {
		mFileTransferId = transferId;
		mBroadcaster = broadcaster;
		mImService = imService;
		mPersistentStorage = storageAccessor;
		mFileTransferService = fileTransferService;
	}

	/**
	 * Constructor
	 * 
	 * @param transferId Transfer ID
	 * @param chatId Chat Id
	 * @param broadcaster IGroupFileTransferBroadcaster
	 * @param imService InstantMessagingService
	 * @param storageAccessor FileTransferPersistedStorageAccessor
	 * @param fileTransferService FileTransferServiceImpl
	 */
	public GroupFileTransferImpl(String transferId, String chatId,
			IGroupFileTransferBroadcaster broadcaster, InstantMessagingService imService,
			FileTransferPersistedStorageAccessor storageAccessor, FileTransferServiceImpl fileTransferService) {
		this(transferId, broadcaster, imService, storageAccessor, fileTransferService);
		mChatId = chatId;
	}

	/**
	 * Returns the chat ID of the group chat
	 *
	 * @return Chat ID
	 */
	public String getChatId() {
		if (mChatId != null) {
			return mChatId;
		}
		return mPersistentStorage.getChatId();
	}

	/**
	 * Returns the file transfer ID of the file transfer
	 *
	 * @return Transfer ID
	 */
	public String getTransferId() {
		return mFileTransferId;
	}

	/**
	 * Returns the remote contact
	 *
	 * @return Contact
	 */
	public ContactId getRemoteContact() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getRemoteContact();
		}
		return session.getRemoteContact();
	}

	/**
	 * Returns the complete filename including the path of the file to be
	 * transferred
	 *
	 * @return Filename
	 */
	public String getFileName() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getFileName();
		}
		return session.getContent().getName();
	}

	/**
	 * Returns the Uri of the file to be transferred
	 *
	 * @return Uri
	 */
	public Uri getFile() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getFile();
		}
		return session.getContent().getUri();
	}

	/**
	 * Returns the size of the file to be transferred
	 *
	 * @return Size in bytes
	 */
	public long getFileSize() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getFileSize();
		}
		return session.getContent().getSize();
	}

	/**
	 * Returns the MIME type of the file to be transferred
	 *
	 * @return Type
	 */
	public String getMimeType() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getMimeType();
		}
		return session.getContent().getEncoding();
	}

	/**
	 * Returns the Uri of the file icon
	 * 
	 * @return Uri
	 */
	public Uri getFileIcon() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getFileIcon();
		}
		MmContent fileIcon = session.getContent();
		return fileIcon != null ? fileIcon.getUri() : null;
	}

	/**
	 * Returns the state of the file transfer
	 * 
	 * @return State
	 */
	public int getState() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getState();
		}
		int state = ((HttpFileTransferSession)session).getSessionState();
		if (HttpTransferState.ESTABLISHED == state) {
			if (isSessionPaused()) {
				return FileTransfer.State.PAUSED;
			}
			return FileTransfer.State.STARTED;
		} else if (session.isInitiatedByRemote()) {
			if (session.isSessionAccepted()) {
				return FileTransfer.State.ACCEPTING;
			}
			return FileTransfer.State.INVITED;
		}
		return FileTransfer.State.INITIATING;
	}

	/**
	 * Returns the reason code of the state of the file transfer
	 *
	 * @return ReasonCode
	 */
	public int getReasonCode() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getReasonCode();
		}
		if (isSessionPaused()) {
			/*
			 * If session is paused and still established it must have been
			 * paused by user
			 */
			return ReasonCode.PAUSED_BY_USER;
		}
		return ReasonCode.UNSPECIFIED;
	}

	/**
	 * Returns the direction of the transfer (incoming or outgoing)
	 *
	 * @return Direction
	 * @see Direction
	 */
	public int getDirection() {
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			return mPersistentStorage.getDirection();
		}
		if (session.isInitiatedByRemote()) {
			return Direction.INCOMING;
		}
		return Direction.OUTGOING;
	}

	/**
	 * Accepts file transfer invitation
	 */
	public void acceptInvitation() {
		if (logger.isActivated()) {
			logger.info("Accept session invitation");
		}
		final FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException("Session with file transfer ID '" + mFileTransferId
					+ "' not available.");
		}
		// Accept invitation
		new Thread() {
			public void run() {
				session.acceptSession();
			}
		}.start();
	}

	/**
	 * Rejects file transfer invitation
	 */
	public void rejectInvitation() {
		if (logger.isActivated()) {
			logger.info("Reject session invitation");
		}
		final FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException("Session with file transfer ID '" + mFileTransferId
					+ "' not available.");
		}
		// Reject invitation
		new Thread() {
			public void run() {
				session.rejectSession(Response.DECLINE);
			}
		}.start();
	}

	/**
	 * Aborts the file transfer
	 */
	public void abortTransfer() {
		if (logger.isActivated()) {
			logger.info("Cancel session");
		}
		final FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException("Session with file transfer ID '" + mFileTransferId
					+ "' not available.");
		}
		if (session.isFileTransfered()) {
			// File already transferred and session automatically closed after
			// transfer
			return;
		}
		// Abort the session
		new Thread() {
			public void run() {
				session.abortSession(ImsServiceSession.TERMINATION_BY_USER);
			}
		}.start();
	}

	/**
	 * Is HTTP transfer
	 *
	 * @return Boolean
	 */
	public boolean isHttpTransfer() {
		/* Group file transfer is always a HTTP file transfer */
		return true;
	}

	/**
	 * Pauses the file transfer (only for HTTP transfer)
	 */
	public void pauseTransfer() {
		FileSharingSession session = mImService
				.getFileSharingSession(mFileTransferId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException(
					"Unable to pause transfer since session with file transfer ID '"
							+ mFileTransferId + "' not available.");
		}
		if (logger.isActivated()) {
			logger.info("Pause session");
		}
		((HttpFileTransferSession)session).pauseFileTransfer();
	}

	/**
	 * Is session paused (only for HTTP transfer)
	 */
	private boolean isSessionPaused() {
		FileSharingSession session = mImService
				.getFileSharingSession(mFileTransferId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException(
					"Unable to check if transfer is paused since session with file transfer ID '"
							+ mFileTransferId + "' not available.");
		}
		return ((HttpFileTransferSession)session).isFileTransferPaused();
	}

	/**
	 * Resume the session (only for HTTP transfer)
	 */
	public void resumeTransfer() {
		FileSharingSession session = mImService
				.getFileSharingSession(mFileTransferId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException(
					"Unable to resume transfer since session with file transfer ID '"
							+ mFileTransferId + "' not available.");
		}
		boolean fileSharingSessionPaused = isSessionPaused();
		if (logger.isActivated()) {
			logger.info("Resuming session paused=" + fileSharingSessionPaused);
		}

		if (!fileSharingSessionPaused) {
			if (logger.isActivated()) {
				logger.info("Resuming can only be used on a paused HTTP transfer");
			}
			return;
		}
		((HttpFileTransferSession)session).resumeFileTransfer();
	}

	/*------------------------------- SESSION EVENTS ----------------------------------*/

	/**
	 * Session is started
	 */
	public void handleSessionStarted() {
		if (logger.isActivated()) {
			logger.info("Session started");
		}
		synchronized (lock) {
			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.STARTED,
					ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.STARTED, ReasonCode.UNSPECIFIED);
		}
	}

	private int sessionAbortedToReasonCode(int sessionAbortedReason) {
		switch (sessionAbortedReason) {
			case ImsServiceSession.TERMINATION_BY_TIMEOUT:
			case ImsServiceSession.TERMINATION_BY_SYSTEM:
				return ReasonCode.ABORTED_BY_SYSTEM;
			case ImsServiceSession.TERMINATION_BY_USER:
				return ReasonCode.ABORTED_BY_USER;
			default:
				throw new IllegalArgumentException(
						"Unknown reason in GroupFileTransferImpl.sessionAbortedToReasonCode; sessionAbortedReason="
								+ sessionAbortedReason + "!");
		}
	}

	private FileTransferStateAndReasonCode toStateAndReasonCode(FileSharingError error) {
		int fileSharingError = error.getErrorCode();
		switch (fileSharingError) {
			case FileSharingError.SESSION_INITIATION_DECLINED:
			case FileSharingError.SESSION_INITIATION_CANCELLED:
				return new FileTransferStateAndReasonCode(FileTransfer.State.REJECTED,
						ReasonCode.REJECTED_BY_REMOTE);
			case FileSharingError.MEDIA_SAVING_FAILED:
				return new FileTransferStateAndReasonCode(FileTransfer.State.FAILED,
						ReasonCode.FAILED_SAVING);
			case FileSharingError.MEDIA_SIZE_TOO_BIG:
				return new FileTransferStateAndReasonCode(FileTransfer.State.REJECTED,
						ReasonCode.REJECTED_MAX_SIZE);
			case FileSharingError.MEDIA_TRANSFER_FAILED:
			case FileSharingError.MEDIA_UPLOAD_FAILED:
			case FileSharingError.MEDIA_DOWNLOAD_FAILED:
				return new FileTransferStateAndReasonCode(FileTransfer.State.FAILED,
						ReasonCode.FAILED_DATA_TRANSFER);
			case FileSharingError.NO_CHAT_SESSION:
			case FileSharingError.SESSION_INITIATION_FAILED:
				return new FileTransferStateAndReasonCode(FileTransfer.State.FAILED,
						ReasonCode.FAILED_INITIATION);
			case FileSharingError.NOT_ENOUGH_STORAGE_SPACE:
				return new FileTransferStateAndReasonCode(FileTransfer.State.REJECTED,
						ReasonCode.REJECTED_LOW_SPACE);
			default:
				throw new IllegalArgumentException(
						new StringBuilder(
								"Unknown reason in GroupFileTransferImpl.toStateAndReasonCode; fileSharingError=")
								.append(fileSharingError).append("!").toString());
		}
	}

	private void handleSessionRejected(int reasonCode) {
		if (logger.isActivated()) {
			logger.info("Session rejected; reasonCode=" + reasonCode + ".");
		}
		synchronized (lock) {
			mFileTransferService.removeFileTransfer(mFileTransferId);

			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.REJECTED,
					reasonCode);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.REJECTED, reasonCode);
		}
	}

	/**
	 * Session has been aborted
	 * 
	 * @param reason Termination reason
	 */
	public void handleSessionAborted(int reason) {
		if (logger.isActivated()) {
			logger.info("Session aborted (reason " + reason + ")");
		}
		int reasonCode = sessionAbortedToReasonCode(reason);
		synchronized (lock) {
			mFileTransferService.removeFileTransfer(mFileTransferId);

			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.ABORTED,
					reasonCode);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.ABORTED, reasonCode);
		}
	}

	/**
	 * Session has been terminated by remote
	 */
	public void handleSessionTerminatedByRemote() {
		if (logger.isActivated()) {
			logger.info("Session terminated by remote");
		}
		synchronized (lock) {
			mFileTransferService.removeFileTransfer(mFileTransferId);
			FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
			if (session != null && !session.isFileTransfered()) {
				mPersistentStorage.setStateAndReasonCode(FileTransfer.State.ABORTED,
					ReasonCode.ABORTED_BY_REMOTE);
				mBroadcaster.broadcastStateChanged(mChatId, mFileTransferId,
					FileTransfer.State.ABORTED, ReasonCode.ABORTED_BY_REMOTE);
			}	
		}
	}

	/**
	 * File transfer error
	 * 
	 * @param error Error
	 */
	public void handleTransferError(FileSharingError error) {
		if (logger.isActivated()) {
			logger.info("Sharing error " + error.getErrorCode());
		}
		FileTransferStateAndReasonCode stateAndReasonCode = toStateAndReasonCode(error);
		int state = stateAndReasonCode.getState();
		int reasonCode = stateAndReasonCode.getReasonCode();
		synchronized (lock) {
			mFileTransferService.removeFileTransfer(mFileTransferId);

			mPersistentStorage.setStateAndReasonCode(state, reasonCode);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, state, reasonCode);
		}
	}

	/**
	 * File transfer progress
	 * 
	 * @param currentSize Data size transferred
	 * @param totalSize Total size to be transferred
	 */
	public void handleTransferProgress(long currentSize, long totalSize) {
		synchronized (lock) {
			mPersistentStorage.setProgress(currentSize);

			mBroadcaster.broadcastProgressUpdate(mChatId, mFileTransferId,
					currentSize, totalSize);
		}
	}

	/**
	 * File transfer not allowed to send
	 */
	@Override
	public void handleTransferNotAllowedToSend() {
		synchronized (lock) {
			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.FAILED,
					ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.FAILED,
					ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);
		}
	}

	/**
	 * File has been transfered
	 * 
	 * @param content MmContent associated to the received file
	 */
	public void handleFileTransfered(MmContent content) {
		if (logger.isActivated()) {
			logger.info("Content transferred");
		}
		synchronized (lock) {
			mFileTransferService.removeFileTransfer(mFileTransferId);

			mPersistentStorage.setTransferred(content);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.TRANSFERRED, ReasonCode.UNSPECIFIED);
		}
	}

	/**
	 * File transfer has been paused by user
	 */
	@Override
	public void handleFileTransferPausedByUser() {
		if (logger.isActivated()) {
			logger.info("Transfer paused by user");
		}
		synchronized (lock) {
			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.PAUSED,
					FileTransfer.ReasonCode.PAUSED_BY_USER);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.PAUSED, FileTransfer.ReasonCode.PAUSED_BY_USER);
		}
	}

	/**
	 * File transfer has been paused by system
	 */
	@Override
	public void handleFileTransferPausedBySystem() {
		if (logger.isActivated()) {
			logger.info("Transfer paused by system");
		}
		synchronized (lock) {
			mFileTransferService.removeFileTransfer(mFileTransferId);

			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.PAUSED,
					FileTransfer.ReasonCode.PAUSED_BY_SYSTEM);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.PAUSED, FileTransfer.ReasonCode.PAUSED_BY_SYSTEM);
		}
	}

	/**
	 * File transfer has been resumed
	 */
	public void handleFileTransferResumed() {
		if (logger.isActivated()) {
			logger.info("Transfer resumed");
		}
		synchronized (lock) {
			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.STARTED,
					ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.STARTED, ReasonCode.UNSPECIFIED);
		}
	}

	@Override
	public void handleSessionAccepted() {
		if (logger.isActivated()) {
			logger.info("Accepting transfer");
		}
		synchronized (lock) {
			mPersistentStorage.setStateAndReasonCode(FileTransfer.State.ACCEPTING,
					ReasonCode.UNSPECIFIED);

			mBroadcaster.broadcastStateChanged(mChatId,
					mFileTransferId, FileTransfer.State.ACCEPTING, ReasonCode.UNSPECIFIED);
		}
	}

	@Override
	public void handleSessionRejectedByUser() {
		handleSessionRejected(ReasonCode.REJECTED_BY_USER);
	}

	@Override
	public void handleSessionRejectedByTimeout() {
		handleSessionRejected(ReasonCode.REJECTED_TIME_OUT);
	}

	@Override
	public void handleSessionRejectedByRemote() {
		handleSessionRejected(ReasonCode.REJECTED_BY_REMOTE);
	}

	@Override
	public void handleSessionInvited() {
		if (logger.isActivated()) {
			logger.info("Invited to group file transfer session");
		}
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		synchronized (lock) {
			mPersistentStorage.addIncomingGroupFileTransfer(mChatId, getRemoteContact(),
					session.getContent(), session.getFileicon(), FileTransfer.State.INVITED,
					ReasonCode.UNSPECIFIED);
		}

		mBroadcaster.broadcastInvitation(mFileTransferId);
	}

	@Override
	public void handleSessionAutoAccepted() {
		if (logger.isActivated()) {
			logger.info("Session auto accepted");
		}
		FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
		synchronized (lock) {
			mPersistentStorage.addIncomingGroupFileTransfer(mChatId, getRemoteContact(),
					session.getContent(), session.getFileicon(), FileTransfer.State.ACCEPTING,
					ReasonCode.UNSPECIFIED);
		}

		mBroadcaster.broadcastInvitation(mFileTransferId);
	}
}
