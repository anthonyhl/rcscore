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
package com.orangelabs.rcs.core.im.filetransfer.http;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.ImsServiceError;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.im.chat.ChatMessage;
import com.orangelabs.rcs.core.im.chat.ChatUtils;
import com.orangelabs.rcs.core.im.chat.OneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingError;
import com.orangelabs.rcs.core.im.filetransfer.FileTransferUtils;
import com.orangelabs.rcs.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.provider.messaging.FtHttpResumeUpload;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Originating file transfer HTTP session
 *
 * @author vfml3370
 */
public class OriginatingHttpFileSharingSession extends HttpFileTransferSession implements HttpUploadTransferEventListener {

	private final ImsModule mCore;

	private final MessagingLog mMessagingLog;

    /**
     * HTTP upload manager
     */
    protected HttpUploadManager uploadManager;

	/**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(OriginatingHttpFileSharingSession.class.getSimpleName());

	/**
	 * Constructor
	 *
	 * @param fileTransferId
	 *            File transfer Id
	 * @param parent
	 *            IMS service
	 * @param content
	 *            Content of file to share
	 * @param contact
	 *            Remote contact identifier
	 * @param fileIconContent
	 *            Content of fileicon
	 * @param tId
	 *            TID of the upload
	 * @param core Core
	 * @param messagingLog MessagingLog
	 */
	public OriginatingHttpFileSharingSession(String fileTransferId, ImsService parent,
			MmContent content, ContactId contact, MmContent fileIcon, String tId, ImsModule core,
			MessagingLog messagingLog) {
		super(parent, content, contact, PhoneUtils.formatContactIdToUri(contact, parent.getUser().getHomeDomain()), fileIcon, null,
				null, fileTransferId);
		mCore = core;
		mMessagingLog = messagingLog;
		if (logger.isActivated()) {
			logger.debug("OriginatingHttpFileSharingSession contact=" + contact);
		}

		uploadManager = new HttpUploadManager(getContent(), fileIcon, this, tId);
	}

	/**
	 * Background processing
	 */
	public void run() {
		try {
	    	if (logger.isActivated()) {
	    		logger.info("Initiate a new HTTP file transfer session as originating");
	    	}
	    	// Upload the file to the HTTP server
            byte[] result = uploadManager.uploadFile();
            sendResultToContact(result);
        } catch(Exception e) {
	    	if (logger.isActivated()) {
	    		logger.error("File transfer has failed", e);
	    	}
        	// Unexpected error
			handleError(new FileSharingError(FileSharingError.UNEXPECTED_EXCEPTION, e.getMessage()));
		}
	}

    protected void sendResultToContact(byte[] result){
		// Check if upload has been cancelled
        if (uploadManager.isCancelled()) {
        	return;
        }

        if (result != null && FileTransferUtils.parseFileTransferHttpDocument(result) != null) {
        	String fileInfo = new String(result, UTF8);
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Upload done with success: ").append(fileInfo)
                        .append(".").toString());
            }

            OneToOneChatSession chatSession = mCore.getInstantMessagingService()
                    .getOneToOneChatSession(getRemoteContact());
            // Note: FileTransferId is always generated to equal the associated msgId of a FileTransfer invitation message.
            String msgId = getFileTransferId();
            if (chatSession != null) {
                if (logger.isActivated()) {
                    logger.debug("Send file transfer info via an existing chat session.");
                }
                if (chatSession.isMediaEstablished()) {
                    setChatSessionID(chatSession.getSessionID());
                    setContributionID(chatSession.getContributionID());
                }
				String mime = CpimMessage.MIME_TYPE;
				String from = ChatUtils.ANOMYNOUS_URI;
				String to = ChatUtils.ANOMYNOUS_URI;
				String content = ChatUtils.buildCpimMessageWithImdn(from, to, 
						msgId, 
						fileInfo, 
						FileTransferHttpInfoDocument.MIME_TYPE, getImsService().getUser().getHomeDomain());
				chatSession.sendDataChunks(IdGenerator.generateMessageID(), content, mime, MsrpSession.TypeMsrpChunk.HttpFileSharing);
			} else {
				if (logger.isActivated()) {
					logger.debug("Send file transfer info via a new chat session.");
				}
				ChatMessage firstMsg = ChatUtils.createFileTransferMessage(getRemoteContact(),
						fileInfo, false, msgId);
				try {
					chatSession = mCore.getInstantMessagingService().initiateOneToOneChatSession(getRemoteContact(), firstMsg);
				} catch (CoreException e) {
					if (logger.isActivated()) {
	                    logger.debug("Couldn't initiate One to one session :"+e);
	                }
                    // Upload error
                    handleError(new FileSharingError(FileSharingError.MEDIA_UPLOAD_FAILED));
					return;
				}
				setChatSessionID(chatSession.getSessionID());
				setContributionID(chatSession.getContributionID());

                chatSession.startSession();
                mCore.getListener().handleOneOneChatSessionInitiation(chatSession);
			}

            // File transfered
            handleFileTransfered();
		} else {
            // Don't call handleError in case of Pause or Cancel
            if (uploadManager.isCancelled() || uploadManager.isPaused()) {
                return;
            }

            if (logger.isActivated()) {
                logger.debug("Upload has failed");
            }
            // Upload error
            handleError(new FileSharingError(FileSharingError.MEDIA_UPLOAD_FAILED));
		}
	}

	@Override
	public void handleError(ImsServiceError error) {
		super.handleError(error);
	}

	@Override
	public void handleFileTransfered() {
		super.handleFileTransfered();
	}

	/**
     * Posts an interrupt request to this Thread
     */
    @Override
    public void interrupt(){
		super.interrupt();

		// Interrupt the upload
		uploadManager.interrupt();
	}

    /**
	 * Pausing the transfer
	 */
	@Override
	public void pauseFileTransfer() {
		fileTransferPaused();
		interruptSession();
		uploadManager.pauseTransferByUser();
	}

	/**
	 * Resuming the transfer
	 */
	@Override
	public void resumeFileTransfer() {
		fileTransferResumed();
		new Thread(new Runnable() {
			public void run() {
				try {
					FtHttpResumeUpload upload = MessagingLog.getInstance().retrieveFtHttpResumeUpload(uploadManager.getTId());
					if (upload != null) {
						sendResultToContact(uploadManager.resumeUpload());
					} else {
						sendResultToContact(null);
					}
				} catch (Exception e) {
                    handleError(new FileSharingError(FileSharingError.MEDIA_UPLOAD_FAILED));
				}
			}
		}).start();
	}

	@Override
	public void uploadStarted() {
		mMessagingLog.setFileUploadTId(getFileTransferId(), uploadManager.getTId());
	}

	public HttpUploadManager getUploadManager() {
		return uploadManager;
	}

	@Override
	public boolean isInitiatedByRemote() {
		return false;
	}
}
