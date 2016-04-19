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

package com.orangelabs.rcs.core.im;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax2.sip.header.ContactHeader;
import javax2.sip.message.Response;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.chat.GroupChat;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.ft.FileTransfer;
import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.capability.Capabilities;
import com.orangelabs.rcs.core.content.ContentManager;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.im.chat.ChatMessage;
import com.orangelabs.rcs.core.im.chat.ChatSession;
import com.orangelabs.rcs.core.im.chat.ChatUtils;
import com.orangelabs.rcs.core.im.chat.DelayedDisplayNotificationTask;
import com.orangelabs.rcs.core.im.chat.GroupChatAutoRejoinTask;
import com.orangelabs.rcs.core.im.chat.GroupChatInfo;
import com.orangelabs.rcs.core.im.chat.GroupChatSession;
import com.orangelabs.rcs.core.im.chat.OneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.OriginatingAdhocGroupChatSession;
import com.orangelabs.rcs.core.im.chat.OriginatingOneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.OriginatingOneToOneLargeModeSession;
import com.orangelabs.rcs.core.im.chat.OriginatingOneToOnePagerModeSession;
import com.orangelabs.rcs.core.im.chat.ParticipantInfoUtils;
import com.orangelabs.rcs.core.im.chat.RejoinGroupChatSession;
import com.orangelabs.rcs.core.im.chat.RestartGroupChatSession;
import com.orangelabs.rcs.core.im.chat.TerminatingAdhocGroupChatSession;
import com.orangelabs.rcs.core.im.chat.TerminatingOneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.TerminatingOneToOneLargeModeSession;
import com.orangelabs.rcs.core.im.chat.TerminatingOneToOnePagerModeSession;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnManager;
import com.orangelabs.rcs.core.im.chat.standfw.StoreAndForwardManager;
import com.orangelabs.rcs.core.im.chat.standfw.TerminatingStoreAndForwardMsgSession;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingError;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.FileTransferUtils;
import com.orangelabs.rcs.core.im.filetransfer.ImsFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.http.FileTransferHttpInfoDocument;
import com.orangelabs.rcs.core.im.filetransfer.http.FtHttpResumeManager;
import com.orangelabs.rcs.core.im.filetransfer.http.OriginatingHttpFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.http.OriginatingHttpGroupFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.http.TerminatingHttpFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.msrp.OriginatingLargeModeFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.msrp.OriginatingMsrpFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.msrp.TerminatingLargeModeFileSharingSession;
import com.orangelabs.rcs.core.im.filetransfer.msrp.TerminatingMsrpFileSharingSession;
import com.orangelabs.rcs.core.upload.FileUploadSession;
import com.orangelabs.rcs.protocol.sip.FeatureTags;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData.FileTransferProtocol;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Instant messaging services (1-1 chat, group chat and file transfer)
 *
 * @author Jean-Marc AUFFRET
 */
public class InstantMessagingService extends ImsService {

	private final RcsSettings mRcsSettings;

	private final ContactsManager mContactsManager;

	private final MessagingLog mMessagingLog;

	/**
	 * OneToOneChatSessionCache with ContactId as key
	 */
	private Map<ContactId, OneToOneChatSession> mOneToOneChatSessionCache = new HashMap<ContactId, OneToOneChatSession>();

	/**
	 * GroupChatSessionCache with ChatId as key
	 */
	private Map<String, GroupChatSession> mGroupChatSessionCache = new HashMap<String, GroupChatSession>();

	/**
	 * FileSharingSessionCache with FileTransferId as key
	 */
	private Map<String, FileSharingSession> mFileTransferSessionCache = new HashMap<String, FileSharingSession>();

	/**
	 * FileUploadSessionCache with UploadId as key
	 */
	private Map<String, FileUploadSession> mFileUploadSessionCache = new HashMap<String, FileUploadSession>();

	/**
	 * GroupChatConferenceSubscriberCache with Conference subscriber's dialog
	 * path CallId as key
	 */
	private Map<String, GroupChatSession> mGroupChatConferenceSubscriberCache = new HashMap<String, GroupChatSession>();

    /**
     * Chat features tags
     */
    public final static String[] CHAT_FEATURE_TAGS = { FeatureTags.FEATURE_OMA_IM };

    /**
     * File transfer features tags
     */
    public final static String[] FT_FEATURE_TAGS = { FeatureTags.FEATURE_OMA_IM };

	/**
	 * IMDN manager
	 */
	private ImdnManager mImdnMgr;

	private FtHttpResumeManager mResumeManager;

	private GroupChatAutoRejoinTask mGroupChatAutoRejoinTask;

	/**
	 * Store & Forward manager
	 */
	private final StoreAndForwardManager mStoreAndFwdMgr = new StoreAndForwardManager(this);

	/**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(InstantMessagingService.class.getName());


	/**
     * Constructor
     *
     * @param parent IMS module
	 * @param core Core
	 * @param rcsSettings RcsSettings
	 * @param contactsManager ContactsManager
	 * @param messagingLog MessagingLog
	 * @throws CoreException
     */
	public InstantMessagingService(ImsModule parent,
			RcsSettings rcsSettings, ContactsManager contactsManager, MessagingLog messagingLog) throws CoreException {
        super(parent, true);
        mRcsSettings = rcsSettings;
        mContactsManager = contactsManager;
        mMessagingLog = messagingLog;
	}

	private void handleFileTransferInvitationRejected(SipRequest invite, int reasonCode) {
		ContactId contact = ContactUtils.createContactId(invite.getAssertedIdentity());
		MmContent content = ContentManager.createMmContentFromSdp(invite);
		MmContent fileIcon = FileTransferUtils.extractFileIcon(invite);
		getImsModule().getListener()
				.handleFileTransferInvitationRejected(contact, content, fileIcon, reasonCode);
	}

	private void handleGroupChatInvitationRejected(SipRequest invite, int reasonCode) {
		String chatId = invite.getContributionId();
		ContactId contact = ChatUtils.getReferredIdentityAsContactId(invite);
		String subject = ChatUtils.getSubject(invite);
		Set<ParticipantInfo> participants = ChatUtils.getListOfParticipants(invite, getUser().getUsername());
		getImsModule().getListener()
				.handleGroupChatInvitationRejected(chatId, contact, subject, participants, reasonCode);
	}

	/**
	 * Start the IMS service
	 */
	public synchronized void start() {
		if (isServiceStarted()) {
			// Already started
			return;
		}
		setServiceStarted(true);

		// Start IMDN manager
        mImdnMgr = new ImdnManager(this);
		mImdnMgr.start();

		/*
		 * Send delayed displayed notifications for read messages if they were
		 * not sent before already. This only attempts to send report and in
		 * case of failure the report will be sent later as postponed delivery
		 * report
		 */
		new DelayedDisplayNotificationTask(this);
		// Start resuming FT HTTP
		mResumeManager = new FtHttpResumeManager(this);
		/* Auto-rejoin group chats that are still marked as active. */
		mGroupChatAutoRejoinTask = new GroupChatAutoRejoinTask(MessagingLog.getInstance(), getImsModule());
		mGroupChatAutoRejoinTask.start();
	}

    /**
     * Stop the IMS service
     */
	public synchronized void stop() {
		if (!isServiceStarted()) {
			// Already stopped
			return;
		}
		setServiceStarted(false);

		// Stop IMDN manager
		mImdnMgr.terminate();
        mImdnMgr.interrupt();
        if (mResumeManager != null) {
        	mResumeManager.terminate();
        }
        if (mGroupChatAutoRejoinTask.isAlive()) {
        	mGroupChatAutoRejoinTask.interrupt();
        }
	}

	/**
     * Check the IMS service
     */
	public void check() {
	}

	/**
	 * Returns the IMDN manager
	 *
	 * @return IMDN manager
	 */
	public ImdnManager getImdnManager() {
		return mImdnMgr;
	}

	/**
	 * Get Store & Forward manager
	 */
	public StoreAndForwardManager getStoreAndForwardManager() {
		return mStoreAndFwdMgr;
	}

	public void addSession(OneToOneChatSession session) {
		ContactId contact = session.getRemoteContact();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add OneToOneChatSession with contact '")
					.append(contact).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mOneToOneChatSessionCache.put(contact, session);
			addImsServiceSession(session);
		}
	}

	public void removeSession(final OneToOneChatSession session) {
		final ContactId contact = session.getRemoteContact();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove OneToOneChatSession with contact '")
					.append(contact).append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads trying to get that session can finish up before it is
		 * actually removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					mOneToOneChatSessionCache.remove(contact);
					removeImsServiceSession(session);
				}
			}
		}.start();
	}

	public OneToOneChatSession getOneToOneChatSession(ContactId contact) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get OneToOneChatSession with contact '")
					.append(contact).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mOneToOneChatSessionCache.get(contact);
		}
	}

	public void addSession(GroupChatSession session) {
		String chatId = session.getContributionID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add GroupChatSession with chatId '").append(chatId)
					.append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mGroupChatSessionCache.put(chatId, session);
			addImsServiceSession(session);
		}
	}

	public void removeSession(final GroupChatSession session) {
		final String chatId = session.getContributionID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove GroupChatSession with chatId '").append(chatId)
					.append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads trying to get that session can finish up before it is
		 * actually removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					SipDialogPath conferenceSubscriberDialogPath = session
							.getConferenceEventSubscriber().getDialogPath();
					if (conferenceSubscriberDialogPath != null) {
						mGroupChatConferenceSubscriberCache.remove(conferenceSubscriberDialogPath
								.getCallId());
					}
					mGroupChatSessionCache.remove(chatId);
					removeImsServiceSession(session);
				}
			}
		}.start();
	}

	public GroupChatSession getGroupChatSession(String chatId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get GroupChatSession with chatId '").append(chatId)
					.append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mGroupChatSessionCache.get(chatId);
		}
	}

	public void addGroupChatConferenceSubscriber(String callId, GroupChatSession session) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add GroupChatConferenceSubscriber with callId '")
					.append(callId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mGroupChatConferenceSubscriberCache.put(callId, session);
		}
	}

	public void removeGroupChatConferenceSubscriber(final String callId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove GroupChatConferenceSubscriber with callId '")
					.append(callId).append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads trying to get that session can finish up before it is
		 * actually removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					mGroupChatConferenceSubscriberCache.remove(callId);
				}
			}
		}.start();
	}

	public GroupChatSession getGroupChatSessionOfConferenceSubscriber(String callId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get GroupChatSession with ConferenceSunscriber '")
					.append(callId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mGroupChatConferenceSubscriberCache.get(callId);
		}
	}

	public boolean isChatSessionAvailable() {
		synchronized (getImsServiceSessionOperationLock()) {
			/*
			 * maxChatSessions == 0 means that the allowed number of chat
			 * sessions in use is disabled
			 */
			int maxChatSessions = mRcsSettings.getMaxChatSessions();
			if (maxChatSessions == 0) {
				return true;
			}

			return mOneToOneChatSessionCache.size() + mGroupChatSessionCache.size() < maxChatSessions;
		}
	}

	public void assertAvailableChatSession(String errorMessage) throws CoreException {
		if (!isChatSessionAvailable()) {
			if (logger.isActivated()) {
				logger.error(errorMessage);
			}
			/*
			 * TODO : Proper exception handling will be added here as part of
			 * the CR037 implementation
			 */
			throw new CoreException(errorMessage);
		}
	}

	public void addSession(FileSharingSession session) {
		String fileTransferId = session.getFileTransferId();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add FileSharingSession with fileTransfer ID '")
					.append(fileTransferId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mFileTransferSessionCache.put(fileTransferId, session);
			/*
			 * Only FileSharingSessions of type ImsFileSharingSession has a
			 * dialog path. Hence add only those type of sessions to the
			 * ImsServiceSession cache.
			 */
			if (session instanceof ImsFileSharingSession) {
				addImsServiceSession(session);
			}
		}
	}

	public void removeSession(final FileSharingSession session) {
		final String fileTransferId = session.getFileTransferId();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove FileSharingSession with fileTransfer ID '")
					.append(fileTransferId).append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads trying to get that session can finish up before it is
		 * actually removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					mFileTransferSessionCache.remove(fileTransferId);
					/*
					 * Only FileSharingSessions of type ImsFileSharingSession
					 * has a dialog path. Hence it is possible to remove only
					 * those type of sessions to the ImsServiceSession cache.
					 */
					if (session instanceof ImsFileSharingSession) {
						removeImsServiceSession(session);
					}
				}
			}
		}.start();
	}

	public FileSharingSession getFileSharingSession(String fileTransferId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get FileSharingSession with fileTransfer ID '")
					.append(fileTransferId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mFileTransferSessionCache.get(fileTransferId);
		}
	}

	public void addSession(FileUploadSession session) {
		String uploadId = session.getUploadID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Add FileUploadSession with upload ID '")
					.append(uploadId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			mFileUploadSessionCache.put(uploadId, session);
		}
	}

	public void removeSession(final FileUploadSession session) {
		final String uploadId = session.getUploadID();
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Remove FileUploadSession with upload ID '")
					.append(uploadId).append("'").toString());
		}
		/*
		 * Performing remove session operation on a new thread so that ongoing
		 * threads trying to get that session can finish up before it is
		 * actually removed
		 */
		new Thread() {
			@Override
			public void run() {
				synchronized (getImsServiceSessionOperationLock()) {
					mFileUploadSessionCache.remove(uploadId);
				}
			}
		}.start();
	}

	public FileUploadSession getFileUploadSession(String uploadId) {
		if (logger.isActivated()) {
			logger.debug(new StringBuilder("Get FileUploadSession with upload ID '")
					.append(uploadId).append("'").toString());
		}
		synchronized (getImsServiceSessionOperationLock()) {
			return mFileUploadSessionCache.get(uploadId);
		}
	}

	public boolean isFileTransferSessionAvailable() {
		synchronized (getImsServiceSessionOperationLock()) {
			/*
			 * maxFtSessions == 0 means that the checking of allowed number of
			 * file transfer sessions in use is disabled
			 */
			int maxFileTransferSessions = mRcsSettings.getMaxFileTransferSessions();
			if (maxFileTransferSessions == 0) {
				return true;
			}

			return mFileTransferSessionCache.size() + mFileUploadSessionCache.size() < maxFileTransferSessions;
		}
	}

	public void assertAvailableFileTransferSession(String errorMessage) throws CoreException {
		if (!isFileTransferSessionAvailable()) {
			if (logger.isActivated()) {
				logger.error(errorMessage);
			}
			/*
			 * TODO : Proper exception handling will be added here as part of
			 * the CR037 implementation
			 */
			throw new CoreException(errorMessage);
		}
	}

	public void assertFileSizeNotExceedingMaxLimit(long size, String errorMessage) throws CoreException {
		/*
		 * maxFtSize == 0 means that the checking of allowed number of file
		 * transfer size in use is disabled
		 */
		int maxFileTransferSize = mRcsSettings.getMaxFileTransferSize();
		if (maxFileTransferSize > 0 && size > maxFileTransferSize) {
			if (logger.isActivated()) {
				logger.error(errorMessage);
			}
			/*
			 * TODO : Proper exception handling will be added here as part of
			 * the CR037 implementation
			 */
			throw new CoreException(errorMessage);
		}
	}

	/**
	 * Checks if max number of concurrent outgoing file transfer sessions
	 * reached
	 *
	 * @return boolean
	 */
	public boolean isMaxConcurrentOutgoingFileTransfersReached() {
		int nrOfConcurrentOutgoingFileTransferSessions = 0;
		synchronized (getImsServiceSessionOperationLock()) {
			for (FileSharingSession session : mFileTransferSessionCache.values()) {
				if (!session.isInitiatedByRemote()) {
					nrOfConcurrentOutgoingFileTransferSessions++;
				}
			}
			/*
			 * maxConcurrentOutgoingFilrTransferSessions == 0 means that the
			 * checking of allowed concurrent number of outgoing file transfers
			 * in use is disabled
			 */
			int maxConcurrentOutgoingFileTransferSessions = mRcsSettings
					.getMaxConcurrentOutgoingFileTransferSessions();
			if (maxConcurrentOutgoingFileTransferSessions == 0) {
				return false;
			}
			if (nrOfConcurrentOutgoingFileTransferSessions >= maxConcurrentOutgoingFileTransferSessions) {
				return true;
			}
			nrOfConcurrentOutgoingFileTransferSessions += mFileUploadSessionCache.size();
			return nrOfConcurrentOutgoingFileTransferSessions >= maxConcurrentOutgoingFileTransferSessions;
		}
	}

	/**
	 * Initiate a file transfer session
	 * @param fileTransferId
	 *            File transfer Id
	 * @param contact
	 *            Remote contact identifier
	 * @param content
	 *            Content of file to sent
	 * @param fileIcon
	 *            Content of fileicon
	 *
	 * @return File transfer session
	 */
	public FileSharingSession initiateFileTransferSession(String fileTransferId, ContactId contact,
			MmContent content, MmContent fileIcon) {
		if (logger.isActivated()) {
			logger.info("Initiate a file transfer session with contact " + contact + ", file " + content.toString());
		}

		boolean isFToHttpSupportedByRemote = false;
		Capabilities remoteCapability = mContactsManager.getContactCapabilities(contact);
		if (remoteCapability != null) {
			isFToHttpSupportedByRemote = remoteCapability.isFileTransferHttpSupported();
		}

		Capabilities myCapability = mRcsSettings.getMyCapabilities();
		if (isFToHttpSupportedByRemote && myCapability.isFileTransferHttpSupported()) {
			if (FileTransferProtocol.HTTP.equals(mRcsSettings.getFtProtocol())) {
				return new OriginatingHttpFileSharingSession(fileTransferId, this, content,
						contact, fileIcon, UUID.randomUUID().toString(), getImsModule(), mMessagingLog);
			}
		}

		if (remoteCapability != null && remoteCapability.isFileTransferThumbnailSupported()) {
			fileIcon = null;
		}
		return new OriginatingMsrpFileSharingSession(fileTransferId, this, content, contact,
				fileIcon);
	}

	/**
	 * Initiate a group file transfer session
	 * @param fileTransferId
	 *            File transfer Id
	 * @param contacts
	 *            Set of remote contacts
	 * @param content
	 *            The file content to be sent
	 * @param fileIcon
	 *            Content of fileicon
	 * @param groupChatId
	 *            Chat contribution ID
	 * @param groupChatSessionId
	 *            GroupChatSession Id
	 *
	 * @return File transfer session
	 * @throws CoreException
	 */
	public FileSharingSession initiateGroupFileTransferSession(String fileTransferId, Set<ParticipantInfo> participants, MmContent content,
			MmContent fileIcon, String groupChatId, String groupChatSessionId) throws CoreException {
		if (logger.isActivated()) {
			logger.info("Send file " + content.toString() + " to " + participants.size() + " contacts");
		}

		Capabilities myCapability = mRcsSettings.getMyCapabilities();
		if (!myCapability.isFileTransferHttpSupported()) {
			throw new CoreException("Group file transfer not supported.");
		}

		FileSharingSession session = new OriginatingHttpGroupFileSharingSession(fileTransferId,
				this, content, fileIcon, getUser().getImConferenceUri(),
				participants, groupChatSessionId, groupChatId, UUID.randomUUID().toString(), getImsModule());

		return session;
	}
	
	@Override
	protected boolean handleInvite(SipRequest request) {
		if (super.handleInvite(request)) {
			return true;
		}
		
		String sdp = request.getSdpContent();
		
		if (ChatUtils.isFileTransferService(request)) {
            receiveMsrpFileTransferInvitation(request);
            return true;
		}
		
		else if (isTagPresent(sdp, "msrp") &&
				request.isFeatureTagPresent(FeatureTags.FEATURE_OMA_IM) &&
				isTagPresent(sdp, "file-selector")) {
			// File transfer
			if (RcsSettings.getInstance().isFileTransferSupported()) {
				if (logger.isActivated()) {
					logger.debug("File transfer invitation");
				}
				// @tct-stack add begin for ZTE ft p-asset-identify is cpm.msg{
				if (ChatUtils.isPagerModeMessagingService(request)) {
					receiveMsrpFileTransferInvitation(request);
				} else
					// } end
					receiveFileTransferInvitation(request);
			} else {
				// Service not supported: reject the invitation with a 603 Decline
				if (logger.isActivated()) {
					logger.debug("File transfer service not supported: automatically reject");
				}
				sendFinalResponse(request, Response.DECLINE);
			}
			return true;
		}
		else if (isTagPresent(sdp, "msrp") &&
				(request.isFeatureTagPresent(FeatureTags.FEATURE_OMA_IM)
						|| request.isFeatureTagPresent(FeatureTags.FEATURE_RCSE_CHAT_SESSION))) {
			// IM service
			if (!RcsSettings.getInstance().isImSessionSupported()) {
				// Service not supported: reject the invitation with a 603 Decline
				if (logger.isActivated()) {
					logger.debug("IM service not supported: automatically reject");
				}
				sendFinalResponse(request, Response.DECLINE);
				return true;
			}

			if (ChatUtils.isFileTransferOverHttp(request)) {
				FileTransferHttpInfoDocument ftHttpInfo = FileTransferUtils.getHttpFTInfo(request);
				if (ftHttpInfo != null) {
					// HTTP file transfer invitation
					if (SipUtils.getReferredByHeader(request) != null) {
						if (logger.isActivated()) {
							logger.debug("Single S&F file transfer over HTTP invitation");
						}
						receiveStoredAndForwardOneToOneHttpFileTranferInvitation(request, ftHttpInfo);
					} else {
						if (logger.isActivated()) {
							logger.debug("Single file transfer over HTTP invitation");
						}
						receiveOneToOneHttpFileTranferInvitation(request, ftHttpInfo);
					}
				} else {
					// TODO : else return error to Originating side
					// Malformed xml for FToHTTP: automatically reject with a 606 Not Acceptable
					if (logger.isActivated()) {
						logger.debug("Malformed xml for FToHTTP: automatically reject");
					}
					sendFinalResponse(request, Response.SESSION_NOT_ACCEPTABLE);
				}
				return true;
			} else {
				if (request.getAssertedIdentity().contains(StoreAndForwardManager.SERVICE_URI) &&
						(!request.getContentType().contains("multipart"))) {
					// Store & Forward push notifs session
					if (logger.isActivated()) {
						logger.debug("Store & Forward push notifications");
					}
					receiveStoredAndForwardPushNotifications(request);
				} else
					if (request.isGroupChatInvitation()) {
						// Ad-hoc group chat session
						if (logger.isActivated()) {
							logger.debug("Ad-hoc group chat session invitation");
						}
						receiveAdhocGroupChatSession(request);
					} else	if (SipUtils.getReferredByHeader(request) != null) {
						// Store & Forward push messages session
						if (logger.isActivated()) {
							logger.debug("Store & Forward push messages session");
						}
						receiveStoredAndForwardPushMessages(request);
						// @tct-stack add begin {
					} else	if (ChatUtils.isLargeMessageModeMessagingService(request)) {
						receiveOne2OneChatLargeModeSession(request);
						// } end
					} else {
						// 1-1 chat session
						if (logger.isActivated()) {
							logger.debug("1-1 chat session invitation");
						}
						receiveOne2OneChatSession(request);
					}
				return true;
			}
		}

		return false;
	}

	/**
     * Receive a file transfer invitation
     *
     * @param invite Initial invite
     */
	public void receiveFileTransferInvitation(SipRequest invite) {
		if (logger.isActivated()) {
    		logger.info("Receive a file transfer session invitation");
    	}

		try {
			// Test if the contact is blocked
			ContactId remote = ContactUtils.createContactId(invite.getAssertedIdentity());
			if (mContactsManager.isFtBlockedForContact(remote)) {
				if (logger.isActivated()) {
					logger.debug("Contact " + remote + " is blocked: automatically reject the file transfer invitation");
				}

				handleFileTransferInvitationRejected(invite, FileTransfer.ReasonCode.REJECTED_SPAM);

				// Send a 603 Decline response
				sendErrorResponse(invite, Response.DECLINE);
				return;
			}

			// Test number of sessions
			if (!isFileTransferSessionAvailable()) {
				if (logger.isActivated()) {
					logger.debug("The max number of file transfer sessions is achieved: reject the invitation");
				}

				handleFileTransferInvitationRejected(invite,
						FileTransfer.ReasonCode.REJECTED_MAX_FILE_TRANSFERS);

				// Send a 603 Decline response
				sendErrorResponse(invite, Response.DECLINE);
				return;
			}

			// Create a new session
			FileSharingSession session = new TerminatingMsrpFileSharingSession(this, invite);

			getImsModule().getListener().handleFileTransferInvitation(session, false, remote, session.getRemoteDisplayName());

			session.startSession();

		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.warn("Cannot parse contact from FT invitation");
			}
		}
	}

    /**
     * Initiate a one-to-one chat session
     *
     * @param contact Remote contact identifier
     * @param firstMsg First message
     * @return IM session
     * @throws CoreException
     */
	public OneToOneChatSession initiateOneToOneChatSession(ContactId contact, ChatMessage firstMsg)
			throws CoreException {
		if (logger.isActivated()) {
			logger.info(new StringBuilder("Initiate 1-1 chat session with ").append(contact)
					.append(".").toString());
		}
		assertAvailableChatSession("Max chat sessions achieved");

		OriginatingOneToOneChatSession session = new OriginatingOneToOneChatSession(this, contact,
				firstMsg, mRcsSettings);
		return session;
	}

    /**
     * Receive a one-to-one chat session invitation
     *
     * @param invite Initial invite
     */
    public void receiveOne2OneChatSession(SipRequest invite) {
		if (logger.isActivated()){
			logger.info("Receive a 1-1 chat session invitation");
		}
		try {
			ContactId remote = ChatUtils.getReferredIdentityAsContactId(invite);
			ChatMessage firstMsg = ChatUtils.getFirstMessage(invite);

			// Test if the contact is blocked
			if (mContactsManager.isImBlockedForContact(remote)) {
				if (logger.isActivated()) {
					logger.debug("Contact " + remote + " is blocked: automatically reject the chat invitation");
				}

				// Save the message in the spam folder
				if (firstMsg != null && !mMessagingLog.isMessagePersisted(firstMsg.getMessageId())) {
					mMessagingLog.addOneToOneSpamMessage(firstMsg);
				}

				// Send message delivery report if requested
				if (ChatUtils.isImdnDeliveredRequested(invite)) {
					// Check notification disposition
					String msgId = ChatUtils.getMessageId(invite);
					if (msgId != null) {
						String remoteInstanceId = null;
						ContactHeader inviteContactHeader = (ContactHeader) invite.getHeader(ContactHeader.NAME);
						if (inviteContactHeader != null) {
							remoteInstanceId = inviteContactHeader.getParameter(SipUtils.SIP_INSTANCE_PARAM);
						}
						// Send message delivery status via a SIP MESSAGE
						getImdnManager().sendMessageDeliveryStatusImmediately(remote, msgId,
								ImdnDocument.DELIVERY_STATUS_DELIVERED, remoteInstanceId);
					}
				}

				// Send a 486 Busy response
				sendErrorResponse(invite, Response.BUSY_HERE);
				return;
			}

			/*
			 * Save the message if it was not already persisted in the DB. We
			 * don't have to reject the session if the message was a duplicate
			 * one as the session rejection/keeping will be handled in
			 * TerminatingOneToOneChatSession.startSession() in an uniform way
			 * as according to the defined race conditions in the specification
			 * document.
			 */
			if (firstMsg != null && !mMessagingLog.isMessagePersisted(firstMsg.getMessageId())) {
				mMessagingLog.addIncomingOneToOneChatMessage(firstMsg,
						ChatUtils.isImdnDisplayedRequested(invite));
			}

			// Test number of sessions
			if (!isChatSessionAvailable()) {
				if (logger.isActivated()) {
					logger.debug("The max number of chat sessions is achieved: reject the invitation");
				}

				// Send a 486 Busy response
				sendErrorResponse(invite, Response.BUSY_HERE);
				return;
			}

			// Create a new session
			TerminatingOneToOneChatSession session = new TerminatingOneToOneChatSession(this,
					invite, remote, mRcsSettings);

			getImsModule().getListener().handleOneOneChatSessionInvitation(session);

			session.startSession();

		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.error( "Cannot parse remote contact");
			}
		}
    }

	/**
	 * Initiate an ad-hoc group chat session
	 *
	 * @param contacts
	 *            List of contact identifiers
	 * @param subject
	 *            Subject
	 * @return IM session
	 * @throws CoreException
	 */
	public ChatSession initiateAdhocGroupChatSession(List<ContactId> contacts, String subject) throws CoreException {
		if (logger.isActivated()) {
			logger.info("Initiate an ad-hoc group chat session");
		}

		assertAvailableChatSession("Max number of chat sessions reached");

		Set<ParticipantInfo> participants = ParticipantInfoUtils
				.getParticipantInfos(contacts);

		// Create a new session
		OriginatingAdhocGroupChatSession session = new OriginatingAdhocGroupChatSession(this,
				getUser().getImConferenceUri(), subject, participants, mRcsSettings);

		return session;
	}

    /**
     * Receive ad-hoc group chat session invitation
     *
     * @param invite Initial invite
     */
	public void receiveAdhocGroupChatSession(SipRequest invite) {
		if (logger.isActivated()) {
			logger.info("Receive an ad-hoc group chat session invitation");
		}
		ContactId contact = null;
		String remoteUri = null;

		try {
			contact = ChatUtils.getReferredIdentityAsContactId(invite);
			// Test if the contact is blocked
			if (mContactsManager.isImBlockedForContact(contact)) {
				if (logger.isActivated()) {
					logger.debug("Contact " + contact + " is blocked: automatically reject the chat invitation");
				}

				handleGroupChatInvitationRejected(invite, GroupChat.ReasonCode.REJECTED_SPAM);

				// Send a 486 Busy response
				sendErrorResponse(invite, Response.BUSY_HERE);
				return;
			}
		} catch (RcsContactFormatException e) {
			// GC invitation is out of the blue (i.e. Store & Forward)
			remoteUri = ChatUtils.getReferredIdentityAsContactUri(invite);
			if (logger.isActivated()) {
				logger.info("Receive a forward GC invitation from "+remoteUri);
			}
		}
		Set<ParticipantInfo> participants = ChatUtils.getListOfParticipants(invite, getUser().getUsername());

		// Test number of sessions
		if (!isChatSessionAvailable()) {
			if (logger.isActivated()) {
				logger.debug("The max number of chat sessions is achieved: reject the invitation");
			}

			handleGroupChatInvitationRejected(invite, GroupChat.ReasonCode.REJECTED_MAX_CHATS);

			// Send a 486 Busy response
			sendErrorResponse(invite, Response.BUSY_HERE);
			return;
		}

		// Create a new session
		TerminatingAdhocGroupChatSession session = new TerminatingAdhocGroupChatSession(this,
				invite, contact, remoteUri, participants, mRcsSettings);

		/*--
		 * 6.3.3.1 Leaving a Group Chat that is idle
		 * In case the user expresses their desire to leave the Group Chat while it is inactive, the device will not offer the user
		 * the possibility any more to enter new messages and restart the chat and automatically decline the first incoming INVITE
		 * request for the chat with a SIP 603 DECLINE response. Subsequent INVITE requests should not be rejected as they may be
		 * received when the user is added again to the Chat by one of the participants.
		 */
		boolean reject = mMessagingLog.isGroupChatNextInviteRejected(session.getContributionID());
		if (reject) {
			if (logger.isActivated()) {
				logger.debug("Chat Id " + session.getContributionID()
						+ " is declined since previously terminated by user while disconnected");
			}
			// Send a 603 Decline response
			sendErrorResponse(invite, Response.DECLINE);
			mMessagingLog.acceptGroupChatNextInvitation(session.getContributionID());
			return;
		}

		getImsModule().getListener().handleAdhocGroupChatSessionInvitation(session);

		session.startSession();
    }

    /**
     * Rejoin a group chat session
     *
     * @param chatId Chat ID
     * @return IM session
     * @throws CoreException
     */
    public ChatSession rejoinGroupChatSession(String chatId) throws CoreException {
		if (logger.isActivated()) {
			logger.info("Rejoin group chat session");
		}

		assertAvailableChatSession("Max chat sessions reached");

		// Get the group chat info from database
		GroupChatInfo groupChat = mMessagingLog.getGroupChatInfo(chatId);
		if (groupChat == null) {
			if (logger.isActivated()) {
				logger.warn("Group chat " + chatId + " can't be rejoined: conversation not found");
			}
			throw new CoreException("Group chat conversation not found in database");
		}
		if (groupChat.getRejoinId() == null) {
			if (logger.isActivated()) {
				logger.warn("Group chat " + chatId + " can't be rejoined: rejoin ID not found");
			}
			throw new CoreException("Rejoin ID not found in database");
		}

		Set<ParticipantInfo> participants = groupChat.getParticipants(); // Added by Deutsche Telekom AG
		if (participants.size() == 0) {
			if (logger.isActivated()) {
				logger.warn("Group chat " + chatId + " can't be rejoined: participants not found");
			}
			throw new CoreException("Group chat participants not found in database");
		}

		// Create a new session
		if (logger.isActivated()) {
			logger.debug("Rejoin group chat: " + groupChat.toString());
		}

		return new RejoinGroupChatSession(this, groupChat, mRcsSettings);
    }

    /**
     * Restart a group chat session
     *
     * @param chatId Chat ID
     * @return IM session
     * @throws CoreException
     */
    public ChatSession restartGroupChatSession(String chatId) throws CoreException {
		if (logger.isActivated()) {
			logger.info("Restart group chat session");
		}

		assertAvailableChatSession("Max chat sessions reached");

		// Get the group chat info from database
		GroupChatInfo groupChat = mMessagingLog.getGroupChatInfo(chatId);
		if (groupChat == null) {
			if (logger.isActivated()) {
				logger.warn("Group chat " + chatId + " can't be restarted: conversation not found");
			}
			throw new CoreException("Group chat conversation not found in database");
		}

		// TODO check whether participants of GroupChatInfo cannot be used instead

		// Get the connected participants from database
		Set<ParticipantInfo> participants = mMessagingLog.getGroupChatConnectedParticipants(chatId);

		if (participants.size() == 0) {
			if (logger.isActivated()) {
				logger.warn("Group chat " + chatId + " can't be restarted: participants not found");
			}
			throw new CoreException("Group chat participants not found in database");
		}

		// Create a new session
		if (logger.isActivated()) {
			logger.debug("Restart group chat: " + groupChat.toString());
		}

		return new RestartGroupChatSession(this, getImsModule().getUser().getImConferenceUri(), groupChat.getSubject(),
				participants, chatId, mRcsSettings);
    }

    /**
     * Receive a conference notification
     *
     * @param notify Received notify
     */
	public void receiveConferenceNotification(SipRequest notify) {
		GroupChatSession session = getGroupChatSessionOfConferenceSubscriber(notify.getCallId());
		if (session != null) {
			session.getConferenceEventSubscriber().receiveNotification(notify);
		}
	}
	
	@Override
	protected boolean handleMessage(SipRequest request) {
		 if (ChatUtils.isPagerModeMessagingService(request)) {
             // message session service
             receiveOne2OneChatPagerModeSession(request);
             return true;
         } else	if (ChatUtils.isImdnService(request)) {
	    		// IMDN service
			receiveMessageDeliveryStatus(request);
			return true;
 		}
		 return false;
	}

	/**
     * Receive a message delivery status
     *
     * @param message Received message
     */
    public void receiveMessageDeliveryStatus(SipRequest message) {
		// Send a 200 OK response
		try {
			if (logger.isActivated()) {
				logger.info("Send 200 OK");
			}
	        SipResponse response = SipUtils.createResponse(message,
	        		IdGenerator.getIdentifier(), 200);
			getSipManager().sendSipResponse(response);
		} catch(Exception e) {
	       	if (logger.isActivated()) {
	    		logger.error("Can't send 200 OK response", e);
	    	}
	       	return;
		}

		try {
			// Parse received message
			ImdnDocument imdn = ChatUtils.parseCpimDeliveryReport(message.getContent());
			if (imdn == null) {
				return;
			}

			ContactId contact = ContactUtils.createContactId(message.getAssertedIdentity());
			String msgId = imdn.getMsgId();
			// Note: FileTransferId is always generated to equal the
			// associated msgId of a FileTransfer invitation message.
			String fileTransferId = msgId;

			// Check if message delivery of a file transfer
			boolean isFileTransfer = mMessagingLog.isFileTransfer(fileTransferId);
			if (isFileTransfer) {
				// Notify the file delivery outside of the chat session
				receiveFileDeliveryStatus(contact, imdn);
			} else {
				// Get session associated to the contact
				OneToOneChatSession session = getOneToOneChatSession(contact);
				if (session != null) {
					// Notify the message delivery from the chat session
					session.handleMessageDeliveryStatus(contact, imdn);
				} else {
					// Notify the message delivery outside of the chat session
					getImsModule().getListener().handleMessageDeliveryStatus(contact, imdn);
				}
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.warn("Cannot parse message delivery status");
			}
		}
    }

    /**
     * Receive 1-1 file delivery status
     * @param contact Contact identifier
     * @param imdn Imdn document
     */
    public void receiveFileDeliveryStatus(ContactId contact, ImdnDocument imdn){
        // Notify the file delivery outside of the chat session
        getImsModule().getListener().handleFileDeliveryStatus(contact, imdn);
    }

	/**
	 * Receive group file delivery status
	 *
	 * @param chatId Chat Id
	 * @param contact Contact identifier
	 * @param ImdnDocument imdn Imdn document
	 */
	public void receiveGroupFileDeliveryStatus(String chatId, ContactId contact, ImdnDocument imdn) {
		getImsModule().getListener()
				.handleGroupFileDeliveryStatus(chatId, contact, imdn);
	}

    /**
     * Receive S&F push messages
     *
     * @param invite Received invite
     */
    public void receiveStoredAndForwardPushMessages(SipRequest invite) {
    	if (logger.isActivated()) {
			logger.debug("Receive S&F push messages invitation");
		}
		ContactId remote;
		try {
			remote = ChatUtils.getReferredIdentityAsContactId(invite);
		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.error("Cannot parse remote contact");
			}
			return;
		}
		ChatMessage firstMsg = ChatUtils.getFirstMessage(invite);

    	// Test if the contact is blocked
	    if (mContactsManager.isImBlockedForContact(remote)) {
			if (logger.isActivated()) {
				logger.debug("Contact " + remote + " is blocked: automatically reject the S&F invitation");
			}

			// Send a 486 Busy response
			sendErrorResponse(invite, 486);
			return;
	    }

		/*
		 * Save the message if it was not already persisted in the DB. We
		 * don't have to reject the session if the message was a duplicate
		 * one as the session rejection/keeping will be handled in
		 * TerminatingOneToOneChatSession.startSession() in an uniform way
		 * as according to the defined race conditions in the specification
		 * document.
		 */
		if (firstMsg != null && !mMessagingLog.isMessagePersisted(firstMsg.getMessageId())) {
			mMessagingLog.addIncomingOneToOneChatMessage(firstMsg,
					ChatUtils.isImdnDisplayedRequested(invite));
		}

		// Create a new session
    	getStoreAndForwardManager().receiveStoredMessages(invite, remote, mRcsSettings);
    }

    /**
     * Receive S&F push notifications
     *
     * @param invite Received invite
     */
    public void receiveStoredAndForwardPushNotifications(SipRequest invite) {
    	if (logger.isActivated()) {
			logger.debug("Receive S&F push notifications invitation");
		}
    	ContactId remote;
		try {
			remote = ChatUtils.getReferredIdentityAsContactId(invite);
		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.error("Cannot parse remote contact");
			}
			return;
		}
    	// Test if the contact is blocked
	    if (mContactsManager.isImBlockedForContact(remote)) {
			if (logger.isActivated()) {
				logger.debug("Contact " + remote + " is blocked: automatically reject the S&F invitation");
			}

			// Send a 486 Busy response
			sendErrorResponse(invite, 486);
			return;
	    }

		// Create a new session
    	getStoreAndForwardManager().receiveStoredNotifications(invite,remote, null);
    }

    /**
     * Receive HTTP file transfer invitation
     *
     * @param invite Received invite
     * @param ftinfo File transfer info document
     */
	public void receiveOneToOneHttpFileTranferInvitation(SipRequest invite, FileTransferHttpInfoDocument ftinfo) {
		if (logger.isActivated()){
			logger.info("Receive a single HTTP file transfer invitation");
		}

		try {
			ContactId remote = ChatUtils.getReferredIdentityAsContactId(invite);
			// Test if the contact is blocked
			if (mContactsManager.isFtBlockedForContact(remote)) {
				if (logger.isActivated()) {
					logger.debug("Contact " + remote + " is blocked, automatically reject the HTTP File transfer");
				}

				handleFileTransferInvitationRejected(invite, FileTransfer.ReasonCode.REJECTED_SPAM);
				// Send a 603 Decline response
				sendErrorResponse(invite, Response.DECLINE);
				return;
			}

			// Test number of sessions
			if (!isFileTransferSessionAvailable()) {
				if (logger.isActivated()) {
					logger.debug("The max number of FT sessions is achieved, reject the HTTP File transfer");
				}

				handleFileTransferInvitationRejected(invite, FileTransfer.ReasonCode.REJECTED_MAX_FILE_TRANSFERS);
				// Send a 603 Decline response
				sendErrorResponse(invite, 603);
				return;
			}

			// Reject if file is too big or size exceeds device storage capacity. This control should be done
			// on UI. It is done after end user accepts invitation to enable prior handling by the application.
			FileSharingError error = FileSharingSession.isFileCapacityAcceptable(ftinfo.getFileSize());
			if (error != null) {
				// Send a 603 Decline response
				sendErrorResponse(invite, 603);
				int errorCode = error.getErrorCode();
				switch (errorCode) {
					case FileSharingError.MEDIA_SIZE_TOO_BIG:
						handleFileTransferInvitationRejected(invite,
								FileTransfer.ReasonCode.REJECTED_MAX_SIZE);
					case FileSharingError.NOT_ENOUGH_STORAGE_SPACE:
						handleFileTransferInvitationRejected(invite,
								FileTransfer.ReasonCode.REJECTED_LOW_SPACE);
					default:
						if (logger.isActivated()) {
							logger.error("Encountered unexpected error while receiving HTTP file transfer invitation"
									+ errorCode);
						}
				}
				return;
			}

			// Create and start a chat session
			TerminatingOneToOneChatSession oneToOneChatSession = new TerminatingOneToOneChatSession(
					this, invite, remote, mRcsSettings);
			oneToOneChatSession.startSession();

			// Create and start a new HTTP file transfer session
			TerminatingHttpFileSharingSession httpFiletransferSession = new TerminatingHttpFileSharingSession(this,
					oneToOneChatSession, ftinfo, ChatUtils.getMessageId(invite), oneToOneChatSession.getRemoteContact(),
					oneToOneChatSession.getRemoteDisplayName());

			getImsModule().getListener().handleOneToOneFileTransferInvitation(httpFiletransferSession, oneToOneChatSession);

			httpFiletransferSession.startSession();

		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.error( "receiveHttpFileTranferInvitation: cannot parse remote contact");
			}
		}
	}

	/**
     * Receive S&F HTTP file transfer invitation
     *
     * @param invite Received invite
     * @param ftinfo File transfer info document
     */
    public void receiveStoredAndForwardOneToOneHttpFileTranferInvitation(SipRequest invite, FileTransferHttpInfoDocument ftinfo) {
        if (logger.isActivated()) {
            logger.info("Receive a single S&F HTTP file transfer invitation");
        }
        ContactId remote;
		try {
			remote = ChatUtils.getReferredIdentityAsContactId(invite);
		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.error("receiveStoredAndForwardHttpFileTranferInvitation: cannot parse remote contact");
			}
			return;
		}

        // Create and start a chat session
        TerminatingStoreAndForwardMsgSession one2oneChatSession = new TerminatingStoreAndForwardMsgSession(
                this, invite, remote, mRcsSettings);
        one2oneChatSession.startSession();

        // Auto reject if file too big
        if (isFileSizeExceeded(ftinfo.getFileSize())) {
            if (logger.isActivated()) {
                logger.debug("File is too big, reject file transfer invitation");
            }

            // Send a 403 Decline response
            //TODO add warning header "xxx Size exceeded"
            one2oneChatSession.sendErrorResponse(invite, one2oneChatSession.getDialogPath().getLocalTag(), 403);

            // Close session
            one2oneChatSession.handleError(new FileSharingError(FileSharingError.MEDIA_SIZE_TOO_BIG));
            return;
        }

        // Create and start a new HTTP file transfer session
		TerminatingHttpFileSharingSession httpFiletransferSession = new TerminatingHttpFileSharingSession(this, one2oneChatSession,
				ftinfo, ChatUtils.getMessageId(invite), one2oneChatSession.getRemoteContact(),
				one2oneChatSession.getRemoteDisplayName());

		getImsModule().getListener().handleOneToOneFileTransferInvitation(httpFiletransferSession, one2oneChatSession);

        httpFiletransferSession.startSession();
    }

    /**
     * Check whether file size exceeds the limit
     *
     * @param size of file
     * @return {@code true} if file size limit is exceeded, otherwise {@code false}
     */
    private boolean isFileSizeExceeded(long size) {
        // Auto reject if file too big
        int maxSize = FileSharingSession.getMaxFileSharingSize();
        if (maxSize > 0 && size > maxSize) {
            return true;
        }

        return false;
    }


    /**********************************************************************************
     *  tct-stack add for fix bug: don't send queued file transfers
     **********************************************************************************/

    /**
     * receiver a group chat session established event
     *
     * @param chatId Chat ID
     */
    public void receiveGroupChatSessionEstablishedEvent(String chatId) {
        if (logger.isActivated()) {
            logger.info("receive a group chat session established event, chatId=" + chatId);
        }
        getImsModule().getListener().handleSendGroupQueuedFileTransfer(chatId);
    }


    /**********************************************************************************
     *  tct-stack add for CMCC message modes: 1) pager mode; 2) large mode
     **********************************************************************************/

    /**
     * Initiate a one-to-one pager mode session
     * 
     * @param contact Remote contact identifier
     * @param firstMsg First message
     * @return IM session
     * @throws CoreException
     */
    public OriginatingOneToOnePagerModeSession initiateOneToOnePagerModeSession(ContactId contact,
            ChatMessage firstMsg) throws CoreException {
        if (logger.isActivated()) {
            logger.info(new StringBuilder("Initiate 1-1 chat pager mode session with ").append(contact)
                    .append(".").toString());
        }

        OriginatingOneToOnePagerModeSession session = new OriginatingOneToOnePagerModeSession(this, contact,
                firstMsg, mRcsSettings);
        return session;
    }

    /**
     * Initiate a one-to-one large mode session
     * 
     * @param contact Remote contact identifier
     * @param firstMsg First message
     * @return IM session
     * @throws CoreException
     */
    public OneToOneChatSession initiateOneToOneLargeModeSession(ContactId contact, ChatMessage firstMsg)
            throws CoreException {
        if (logger.isActivated()) {
            logger.info(new StringBuilder("Initiate 1-1 chat large mode session with ").append(contact)
                    .append(".").toString());
        }
        // max chat session is not used in CMCC, otherwise we should count the large mode sessions
//        assertAvailableChatSession("Max chat sessions achieved");

        OriginatingOneToOneLargeModeSession session = new OriginatingOneToOneLargeModeSession(this, contact,
                firstMsg, mRcsSettings);
        return session;
    }

    /**
     * Receive a one-to-one chat pager mode message
     *
     * @param message SIP MESSAGE
     */
    public void receiveOne2OneChatPagerModeSession(SipRequest message) {
        if (logger.isActivated()){
            logger.info("Receive a 1-1 chat pager mode session");
        }
        try {
            ContactId remote = ChatUtils.getReferredIdentityAsContactId(message);

            // Test if the contact is blocked
            if (mContactsManager.isImBlockedForContact(remote)) {
                if (logger.isActivated()) {
                    logger.debug("Contact " + remote + " is blocked: automatically reject the chat invitation");
                }

                // Send a 406 not acceptable response, FIXME is it ok for SIP MESSAGE reject response??
                sendErrorResponse(message, Response.NOT_ACCEPTABLE);
                return;
            }

            // Create a new session
            TerminatingOneToOnePagerModeSession session = new TerminatingOneToOnePagerModeSession(this,
                    message, remote, mRcsSettings);

            getImsModule().getListener().handleOneOneChatPagerModeInvitation(session);

            session.startSession();

        } catch (RcsContactFormatException e) {
            if (logger.isActivated()) {
                logger.error( "Cannot parse remote contact");
            }
        }
    }

    /**
     * Receive a one-to-one chat session invitation
     *
     * @param invite Initial invite
     */
    public void receiveOne2OneChatLargeModeSession(SipRequest invite) {
        if (logger.isActivated()){
            logger.info("Receive a 1-1 chat large mode session invite");
        }
        try {
            ContactId remote = ChatUtils.getReferredIdentityAsContactId(invite);
//            ChatMessage firstMsg = ChatUtils.getFirstMessage(invite);

            // Test if the contact is blocked
            if (mContactsManager.isImBlockedForContact(remote)) {
                if (logger.isActivated()) {
                    logger.debug("Contact " + remote + " is blocked: automatically reject the chat invitation");
                }
                // Send a 486 Busy response
                sendErrorResponse(invite, Response.BUSY_HERE);
                return;
            }

            // Test number of sessions
            if (!isChatSessionAvailable()) {
                if (logger.isActivated()) {
                    logger.debug("The max number of chat sessions is achieved: reject the invitation");
                }
                // Send a 486 Busy response
                sendErrorResponse(invite, Response.BUSY_HERE);
                return;
            }

            // Create a new session
            TerminatingOneToOneLargeModeSession session = new TerminatingOneToOneLargeModeSession(this,
                    invite, remote, mRcsSettings);

            getImsModule().getListener().handleOneOneChatLargeModeInvitation(session);

            session.startSession();

        } catch (RcsContactFormatException e) {
            if (logger.isActivated()) {
                logger.error( "Cannot parse remote contact");
            }
        }
    }

    /**
     * Initiate a file transfer session
     * 
     * @param conversationId
     *            Conversation Id
     * @param fileTransferId
     *            File transfer Id
     * @param contact
     *            Remote contact identifier
     * @param content
     *            Content of file to sent
     * @param fileIcon
     *            Content of fileicon
     *
     * @return File transfer session
     */
    public FileSharingSession initiateMsrpFileTransferSession(String convId, String fileTransferId, ContactId contact,
            MmContent content, MmContent fileIcon) {
        if (logger.isActivated()) {
            logger.info("Initiate a file transfer session with contact " + contact + ", file " + content.toString());
        }

        return new OriginatingLargeModeFileSharingSession(fileTransferId, convId, this, content, contact,
                fileIcon);
    }

    /**
     * Receive a file transfer invitation
     *
     * @param invite Initial invite
     */
    public void receiveMsrpFileTransferInvitation(SipRequest invite) {
        if (logger.isActivated()) {
            logger.info("Receive a msrp file transfer session invitation");
        }

        try {
            // Test if the contact is blocked
            ContactId remote = ContactUtils.createContactId(invite.getAssertedIdentity());
            if (mContactsManager.isFtBlockedForContact(remote)) {
                if (logger.isActivated()) {
                    logger.debug("Contact " + remote + " is blocked: automatically reject the file transfer invitation");
                }

                handleFileTransferInvitationRejected(invite, FileTransfer.ReasonCode.REJECTED_SPAM);

                // Send a 603 Decline response
                sendErrorResponse(invite, Response.DECLINE);
                return;
            }

            // Test number of sessions
            if (!isFileTransferSessionAvailable()) {
                if (logger.isActivated()) {
                    logger.debug("The max number of file transfer sessions is achieved: reject the invitation");
                }

                handleFileTransferInvitationRejected(invite,
                        FileTransfer.ReasonCode.REJECTED_MAX_FILE_TRANSFERS);

                // Send a 603 Decline response
                sendErrorResponse(invite, Response.DECLINE);
                return;
            }

            // Create a new session
            FileSharingSession session = new TerminatingLargeModeFileSharingSession(this, invite);

            String convId = session.getConversationID();
            // TODO need to ensure whether group chat file transfer via msrp
            boolean isGroup = mMessagingLog.isGroupChatConversation(convId);
            getImsModule().getListener().handleMsrpFileTransferInvitation(session, isGroup, remote, session.getRemoteDisplayName());

            session.startSession();

        } catch (RcsContactFormatException e) {
            if (logger.isActivated()) {
                logger.warn("Cannot parse contact from FT invitation");
            }
        }
    }

}
