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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.net.Uri;
import android.os.IBinder;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceException;
import com.gsma.services.rcs.upload.FileUploadServiceConfiguration;
import com.gsma.services.rcs.upload.IFileUpload;
import com.gsma.services.rcs.upload.IFileUploadListener;
import com.gsma.services.rcs.upload.IFileUploadService;
import com.orangelabs.rcs.core.content.ContentManager;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.upload.FileUploadSession;
import com.orangelabs.rcs.platform.file.FileDescription;
import com.orangelabs.rcs.platform.file.FileFactory;
import com.orangelabs.rcs.service.broadcaster.FileUploadEventBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * File upload service implementation
 * 
 * @author Jean-Marc AUFFRET
 */
public class FileUploadServiceImpl extends IFileUploadService.Stub {

	private final FileUploadEventBroadcaster mBroadcaster = new FileUploadEventBroadcaster();

	private final InstantMessagingService mImService;

	private final Map<String, IFileUpload> mFileUploadCache = new HashMap<String, IFileUpload>();

	/**
	 * Max file upload size
	 */
	private int maxUploadSize;	
	
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(FileUploadServiceImpl.class.getSimpleName());

	/**
	 * Lock used for synchronization
	 */
	private Object lock = new Object();

	/**
	 * Constructor
	 * 
	 * @param imService InstantMessagingService
	 */
	public FileUploadServiceImpl(InstantMessagingService imService) {
		if (logger.isActivated()) {
			logger.info("File upload service API is loaded");
		}

		// Get configuration
		maxUploadSize = FileSharingSession.getMaxFileSharingSize();
		mImService = imService;
	}

	/**
	 * Close API
	 */
	public void close() {
		mFileUploadCache.clear();
		
		if (logger.isActivated()) {
			logger.info("File upload service API is closed");
		}
	}

	/**
	 * Add a file upload in the list
	 * 
	 * @param filUpload File upload
	 */
	protected void addFileUpload(FileUploadImpl filUpload) {
		if (logger.isActivated()) {
			logger.debug("Add a file upload in the list (size=" + mFileUploadCache.size() + ")");
		}
		
		mFileUploadCache.put(filUpload.getUploadId(), filUpload);
	}

	/**
	 * Remove a file upload from the list
	 * 
	 * @param uploadId Upload ID
	 */
	protected void removeFileUpload(String sessionId) {
		if (logger.isActivated()) {
			logger.debug("Remove a file upload from the list (size=" + mFileUploadCache.size() + ")");
		}
		
		mFileUploadCache.remove(sessionId);
	}

    /**
     * Returns the configuration of the file upload service
     * 
     * @return Configuration
     */
    public FileUploadServiceConfiguration getConfiguration() {
    	return new FileUploadServiceConfiguration(
    			maxUploadSize);
    }    	
	
    /**
     * Uploads a file to the RCS content server. The parameter file contains the URI
     * of the file to be uploaded (for a local or a remote file).
     * 
     * @param file Uri of file to upload
	 * @param fileicon File icon option. If true and if it's an image, a file icon is attached.
	 * @return File upload
     * @throws ServerApiException
     */
    public IFileUpload uploadFile(Uri file, boolean fileicon) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate a file upload session (thumbnail option " + fileicon + ")");
		}

		// Test IMS connection
		ServerApiUtils.testCore();

		try {
			mImService.assertAvailableFileTransferSession("Max file transfer sessions achieved.");

			FileDescription desc = FileFactory.getFactory().getFileDescription(file);
			MmContent content = ContentManager.createMmContent(file, desc.getSize(), desc.getName());

			mImService.assertFileSizeNotExceedingMaxLimit(content.getSize(), "File exceeds max size.");

			final FileUploadSession session = new FileUploadSession(content, fileicon);

			FileUploadImpl fileUpload = new FileUploadImpl(session.getUploadID(),
					mBroadcaster, mImService, this);
			session.addListener(fileUpload);

			session.startSession();
			
			addFileUpload(fileUpload);
			return fileUpload;

		} catch(Exception e) {
			// TODO:Handle Security exception in CR037
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}

    /**
     * Can a file be uploaded now
     * 
     * @return Returns true if a file can be uploaded, else returns false
     * @throws RcsServiceException
     */
    public boolean canUploadFile() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Check if a file can be uploaded");
		}

		// Test IMS connection
		ServerApiUtils.testCore();

		return mImService.isFileTransferSessionAvailable();
    }
    
    /**
     * Returns the list of file uploads in progress
     * 
     * @return List of file uploads
     * @throws ServerApiException
     */
    public List<IBinder> getFileUploads() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get file upload sessions");
		}

		try {
			List<IBinder> fileUploads = new ArrayList<IBinder>(mFileUploadCache.size());
			for (IFileUpload fileUpload : mFileUploadCache.values()) {
				fileUploads.add(fileUpload.asBinder());
			}
			return fileUploads;

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }

    /**
     * Returns a current file upload from its unique ID
     * 
     * @return File upload
     * @throws ServerApiException
     */
	public IFileUpload getFileUpload(String uploadId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get file upload " + uploadId);
		}

		IFileUpload fileUpload = mFileUploadCache.get(uploadId);
		if (fileUpload != null) {
			return fileUpload;
		}
		return new FileUploadImpl(uploadId, mBroadcaster, mImService, this);
	}

	/**
	 * Adds a listener on file upload events
	 * 
	 * @param listener Listener
	 */
	public void addEventListener(IFileUploadListener listener) {
		if (logger.isActivated()) {
			logger.info("Add a file upload event listener");
		}
		synchronized (lock) {
			mBroadcaster.addEventListener(listener);
		}
	}

	/**
	 * Removes a listener on file upload events
	 * 
	 * @param listener Listener
	 */
	public void removeEventListener(IFileUploadListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove a file upload event listener");
		}
		synchronized (lock) {
			mBroadcaster.removeEventListener(listener);
		}
	}

	/**
	 * Returns service version
	 * 
	 * @return Version
	 * @see RcsService.Build.VERSION_CODES
	 * @throws ServerApiException
	 */
	public int getServiceVersion() throws ServerApiException {
		return RcsService.Build.API_VERSION;
	}
}
