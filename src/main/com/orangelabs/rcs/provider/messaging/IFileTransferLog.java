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

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.content.MmContent;

import android.database.Cursor;
import android.net.Uri;

import java.util.List;
import java.util.Set;

/**
 * Interface for the ft table
 * 
 * @author LEMORDANT Philippe
 * 
 */
public interface IFileTransferLog {

	/**
	 * Add outgoing file transfer
	 * @param fileTransferId
	 *            File Transfer ID
	 * @param contact
	 *            Contact ID
	 * @param direction
	 *            Direction
	 * @param content
	 *            File content
	 * @param fileIcon
	 *            Fileicon content
	 * @param state
	 *            File transfer state
	 * @param reasonCode
	 *            Reason code
	 */
	public void addFileTransfer(String fileTransferId, ContactId contact, int direction,
			MmContent content, MmContent fileIcon, int state, int reasonCode);

	/**
	 * Add an outgoing File Transfer supported by Group Chat
	 * @param fileTransferId
	 *            the identity of the file transfer
	 * @param chatId
	 *            the identity of the group chat
	 * @param content
	 *            the File content
	 * @param Fileicon
	 *            the fileIcon content
	 * @param state
	 *            File transfer state
	 * @param reasonCode
	 *            Reason code
	 */
	public void addOutgoingGroupFileTransfer(String fileTransferId, String chatId,
			MmContent content, MmContent fileIcon, int state, int reasonCode);

	/**
	 * Add incoming group file transfer
	 * @param fileTransferId
	 *            File transfer ID
	 * @param chatId
	 *            Chat ID
	 * @param contact
	 *            Contact ID
	 * @param content
	 *            File content
	 * @param fileIcon
	 *            Fileicon contentID
	 * @param state
	 *            File transfer state
	 * @param reasonCode
	 *            Reason code
	 */
	public void addIncomingGroupFileTransfer(String fileTransferId, String chatId, ContactId contact, MmContent content,
			MmContent fileIcon, int state, int reasonCode);

	/**
	 * Set file transfer state and reason code
	 * 
	 * @param fileTransferId
	 *            File transfer ID
	 * @param state
	 *            File transfer state
	 * @param reasonCode
	 *            File transfer state reason code
	 */
	public void setFileTransferStateAndReasonCode(String fileTransferId,
			int state, int reasonCode);

	/**
	 * Update file transfer read status
	 * 
	 * @param fileTransferId
	 *            File transfer ID
	 */
	public void markFileTransferAsRead(String fileTransferId);

	/**
	 * Update file transfer download progress
	 * 
	 * @param fileTransferId
	 *            File transfer ID
	 * @param currentSize
	 *            Current size
	 */
	public void setFileTransferProgress(String fileTransferId, long currentSize);

	/**
	 * Set file transfer URI
	 * 
	 * @param fileTransferId
	 *            File transfer ID
	 * @param content
	 *            the MmContent of received file
	 */
	public void setFileTransferred(String fileTransferId, MmContent content);

	/**
	 * Tells if the MessageID corresponds to that of a file transfer
	 * 
	 * @param fileTransferId
	 *            File Transfer Id
	 * @return boolean If there is File Transfer corresponding to msgId
	 */
	public boolean isFileTransfer(String fileTransferId);

	/**
	 * Set file upload TID
	 *
	 * @param fileTransferId
	 *            File transfer ID
	 * @param tId
	 *            TID
	 */
	public void setFileUploadTId(String fileTransferId, String tId);

	/**
	 * Set file download server uri
	 *
	 * @param fileTransferId
	 *            File transfer ID
	 * @param downloadAddress
	 *            Download Address
	 */
	public void setFileDownloadAddress(String fileTransferId, Uri downloadAddress);

	/**
	 * Retrieve file transfers paused by SYSTEM on connection loss
	 */
	public List<FtHttpResume> retrieveFileTransfersPausedBySystem();

	/**
	 * Retrieve resumable file upload
	 *
	 * @param tId Unique Id used while uploading
	 */
	public FtHttpResumeUpload retrieveFtHttpResumeUpload(String tId);

	/**
	 * Get file transfer state from its unique ID
	 * 
	 * @param fileTransferId Unique ID of file transfer
	 * @return State
	 */
	public int getFileTransferState(String fileTransferId);

	/**
	 * Get file transfer reason code from its unique ID
	 * 
	 * @param fileTransferId Unique ID of file transfer
	 * @return reason code of the state
	 */
	public int getFileTransferStateReasonCode(String fileTransferId);

	/**
	 * Get cacheable file transfer data from its unique ID
	 * 
	 * @param fileTransferId
	 * @return Cursor
	 */
	public Cursor getCacheableFileTransferData(String fileTransferId);

	/**
	 * Is group file transfer
	 * 
	 * @param fileTransferId
	 * @return true if it is group file transfer
	 */
	public boolean isGroupFileTransfer(String fileTransferId);


    /**********************************************************************************
     *  tct-stack add for fix bug: don't send queued file transfer
     **********************************************************************************/

    /**
     * Get all file transfers of specific direction & state & reasonCode
     * 
     * @param direction
     *            Direction
     * @param state
     *            File transfer state
     * @param reasonCode
     *            Reason code
     */
    public Set<String> getAllFileTransferIds(int direction, int state, int reasonCode);

    /**
     * Get file transfer
     * @deprecated
     */
    public Set<String> getFileTransferIds(ContactId contact, int direction, int state, int reasonCode);

    /**
     * Get group file transfer
     * 
     * @param chatId
     *            Chat ID
     * @param direction
     *            Direction
     * @param state
     *            File transfer state
     * @param reasonCode
     *            Reason code
     */
    public Set<String> getGroupFileTransferIds(String chatId, int direction, int state, int reasonCode);

    /**
     * Add file transfer
     * 
     * @param fileTransferId
     *            File Transfer ID
     * @param conversationId
     *            Conversation ID
     * @param contact
     *            Contact ID
     * @param direction
     *            Direction
     * @param content
     *            File content
     * @param fileIcon
     *            Fileicon content
     * @param state
     *            File transfer state
     * @param reasonCode
     *            Reason code
     */
    void addFileTransferExt(String fileTransferId, String convId, ContactId contact, int direction, MmContent content,
            MmContent fileIcon, int state, int reasonCode);

}
