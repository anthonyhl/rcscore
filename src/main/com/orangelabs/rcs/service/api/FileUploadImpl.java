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

import android.net.Uri;

import com.gsma.services.rcs.upload.FileUpload;
import com.gsma.services.rcs.upload.FileUploadInfo;
import com.gsma.services.rcs.upload.IFileUpload;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.filetransfer.http.FileTransferHttpInfoDocument;
import com.orangelabs.rcs.core.im.filetransfer.http.FileTransferHttpThumbnail;
import com.orangelabs.rcs.core.upload.FileUploadSession;
import com.orangelabs.rcs.core.upload.FileUploadSessionListener;
import com.orangelabs.rcs.service.broadcaster.IFileUploadEventBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * File upload implementation
 * 
 * @author Jean-Marc AUFFRET
 */
public class FileUploadImpl extends IFileUpload.Stub implements FileUploadSessionListener {

	private final String mUploadId;

	private final IFileUploadEventBroadcaster mBroadcaster;

	private final InstantMessagingService mImService;

	private final FileUploadServiceImpl mFileUploadService;

	/**
	 * Upload state	
	 */
	private int state = FileUpload.State.INACTIVE;

	/**
	 * Lock used for synchronisation
	 */
	private final Object lock = new Object();

	/**
	 * The logger
	 */
	private final Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Constructor
	 * 
	 * @param uploadId Unique ID of FileUpload
	 * @param broadcaster Event broadcaster
	 * @param imService InstantMessagingService
	 * @param fileUploadService FileUploadServiceImpl
	 */
	public FileUploadImpl(String uploadId, IFileUploadEventBroadcaster broadcaster,
			InstantMessagingService imService, FileUploadServiceImpl fileUploadService) {
		mUploadId = uploadId;
		mBroadcaster = broadcaster;
		mImService = imService;
		mFileUploadService = fileUploadService;
	}

	/**
	 * Returns the upload ID of the upload
	 * 
	 * @return Upload ID
	 */
	public String getUploadId() {
		return mUploadId;
	}
	
	/**
	 * Returns info related to upload file
	 *
	 * @return Upload info or null if not yet upload or in case of error
	 * @see FileUploadInfo
	 */
	public FileUploadInfo getUploadInfo() {
		FileUploadSession session = mImService.getFileUploadSession(mUploadId);
		if (session == null) {
			/*
			 * TODO: Throw proper exception as part of CR037 as persisted storage not
			 * available for this service!
			 */
			throw new IllegalStateException(
					"Unable to get file upload info since session with upload ID '" + mUploadId
							+ "' not available.");
		}
		FileTransferHttpInfoDocument file = session.getFileInfoDocument();
		FileTransferHttpThumbnail fileicon = file.getFileThumbnail();
		if (fileicon != null) {
			return new FileUploadInfo(file.getFileUri(), file.getTransferValidity(),
					file.getFilename(), file.getFileSize(), file.getFileType(),
					fileicon.getThumbnailUri(), fileicon.getThumbnailValidity(),
					fileicon.getThumbnailSize(), fileicon.getThumbnailType());
		}
		return new FileUploadInfo(file.getFileUri(), file.getTransferValidity(),
				file.getFilename(), file.getFileSize(), file.getFileType(), Uri.EMPTY, 0, 0, "");
	}	
	
	/**
	 * Returns the URI of the file to upload
	 * 
	 * @return Uri
	 */
	public Uri getFile() {
		FileUploadSession session = mImService.getFileUploadSession(mUploadId);
		if (session == null) {
			/*
			 * TODO: Throw proper exception as part of CR037 as persisted storage not
			 * available for this service!
			 */
			throw new IllegalStateException("Unable to get file since session with upload ID '"
					+ mUploadId + "' not available.");
		}
		return session.getContent().getUri();
	}

	/**
	 * Returns the state of the file upload
	 * 
	 * @return State 
	 */
	public int getState() {
		FileUploadSession session = mImService.getFileUploadSession(mUploadId);
		if (session == null) {
			/*
			 * TODO: Throw proper exception as part of CR037 as persisted storage not
			 * available for this service!
			 */
			throw new IllegalStateException("Unable to get state since session with upload ID '"
					+ mUploadId + "' not available.");
		}

		return state;
	}

	/**
	 * Aborts the upload
	 */
	public void abortUpload() {
		if (logger.isActivated()) {
			logger.info("Cancel session");
		}
		final FileUploadSession session = mImService.getFileUploadSession(mUploadId);
		if (session == null) {
			/*
			 * TODO: Throw proper exception as part of CR037 implementation
			 */
			throw new IllegalStateException(
					"Unable to abort file upload since session with upload ID '" + mUploadId
							+ "' not available.");
		}

		// Abort the session
        Thread t = new Thread() {
    		public void run() {
    			session.interrupt();
    		}
    	};
    	t.start();		
	}

    /*------------------------------- SESSION EVENTS ----------------------------------*/
    
    /**
     * Upload started
     */
    public void handleUploadStarted() {
    	synchronized(lock) {
	    	if (logger.isActivated()) {
	    		logger.debug("File upload started");
	    	}
    		state = FileUpload.State.STARTED;

			mBroadcaster.broadcastStateChanged(mUploadId, state);
	    }
    }

	/**
	 * Upload progress
	 * 
	 * @param currentSize Data size transfered 
	 * @param totalSize Total size to be transfered
	 */
    public void handleUploadProgress(long currentSize, long totalSize) {
    	synchronized(lock) {
			mBroadcaster.broadcastProgressUpdate(mUploadId, currentSize, totalSize);
	     }
    }
    
    /**
     * Upload terminated with success
     * 
     * @param info File info document
     */
    public void handleUploadTerminated(FileTransferHttpInfoDocument info) {
    	synchronized(lock) {
	    	if (logger.isActivated()) {
	    		logger.debug("File upload terminated");
	    	}
    		state = FileUpload.State.TRANSFERRED;

			mBroadcaster.broadcastStateChanged(mUploadId, state);
			
			mFileUploadService.removeFileUpload(mUploadId);
	    }
    }

    /**
     * Upload error
     * 
     * @param error Error
     */
    public void handleUploadError(int error) {
    	synchronized(lock) {
	    	if (logger.isActivated()) {
	    		logger.debug("File upload failed");
	    	}
    		state = FileUpload.State.FAILED;

			mBroadcaster.broadcastStateChanged(mUploadId, state);

			mFileUploadService.removeFileUpload(mUploadId);
	    }
    }

    /**
     * Upload aborted
     */
    public void handleUploadAborted() {
    	synchronized(lock) {
	    	if (logger.isActivated()) {
	    		logger.debug("File upload aborted");
	    	}
    		state = FileUpload.State.ABORTED;

    		mBroadcaster.broadcastStateChanged(mUploadId, state);

    		mFileUploadService.removeFileUpload(mUploadId);
	    }
    }

	@Override
	public void handleUploadNotAllowedToSend() {
		if (logger.isActivated()) {
			logger.debug("File upload not allowed");
		}
		synchronized (lock) {
			mBroadcaster.broadcastStateChanged(mUploadId,
					FileUpload.State.FAILED);
			mFileUploadService.removeFileUpload(mUploadId);
		}
	}
}
