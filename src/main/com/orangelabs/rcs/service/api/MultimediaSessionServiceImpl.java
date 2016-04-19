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

import android.content.Intent;
import android.os.IBinder;

import com.gsma.services.rcs.IRcsServiceRegistrationListener;
import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.extension.IMultimediaMessagingSession;
import com.gsma.services.rcs.extension.IMultimediaMessagingSessionListener;
import com.gsma.services.rcs.extension.IMultimediaSessionService;
import com.gsma.services.rcs.extension.IMultimediaStreamingSession;
import com.gsma.services.rcs.extension.IMultimediaStreamingSessionListener;
import com.gsma.services.rcs.extension.MultimediaSession;
import com.gsma.services.rcs.extension.MultimediaSession.ReasonCode;
import com.gsma.services.rcs.extension.MultimediaSessionServiceConfiguration;
import com.gsma.services.rcs.extension.MultimediaStreamingSessionIntent;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.core.sip.messaging.GenericSipMsrpSession;
import com.orangelabs.rcs.core.sip.streaming.GenericSipRtpSession;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.protocol.sip.FeatureTags;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.service.broadcaster.RcsServiceRegistrationEventBroadcaster;
import com.orangelabs.rcs.service.broadcaster.MultimediaMessagingSessionEventBroadcaster;
import com.orangelabs.rcs.service.broadcaster.MultimediaStreamingSessionEventBroadcaster;
import com.orangelabs.rcs.utils.IntentUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Multimedia session API service
 * 
 * @author Jean-Marc AUFFRET
 */
public class MultimediaSessionServiceImpl extends IMultimediaSessionService.Stub {

	private final MultimediaMessagingSessionEventBroadcaster mMultimediaMessagingSessionEventBroadcaster = new MultimediaMessagingSessionEventBroadcaster();

	private final MultimediaStreamingSessionEventBroadcaster mMultimediaStreamingSessionEventBroadcaster = new MultimediaStreamingSessionEventBroadcaster();

	private final RcsServiceRegistrationEventBroadcaster mRcsServiceRegistrationEventBroadcaster = new RcsServiceRegistrationEventBroadcaster();

	private final SipService mSipService;

	private final RcsSettings mRcsSettings;

	private final ContactsManager mContactsManager;

	private final Map<String, IMultimediaMessagingSession> mMultimediaMessagingCache = new HashMap<String, IMultimediaMessagingSession>();

	private final Map<String, IMultimediaStreamingSession> mMultimediaStreamingCache = new HashMap<String, IMultimediaStreamingSession>();

	/**
	 * Lock used for synchronization
	 */
	private final Object lock = new Object();
	
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(MultimediaSessionServiceImpl.class.getSimpleName());

	/**
	 * Constructor
	 * 
	 * @param sipService SipService
	 * @param rcsSettings RcsSettings
	 * @param contactsManager ContactsManager
	 */
	public MultimediaSessionServiceImpl(SipService sipService, RcsSettings rcsSettings,
			ContactsManager contactsManager) {
		if (logger.isActivated()) {
			logger.info("Multimedia session API is loaded");
		}
		mSipService = sipService;
		mRcsSettings = rcsSettings;
		mContactsManager = contactsManager;
	}

	/**
	 * Close API
	 */
	public void close() {
		// Clear list of sessions
		mMultimediaMessagingCache.clear();
		
		if (logger.isActivated()) {
			logger.info("Multimedia session service API is closed");
		}
	}
	
	/**
	 * Add a multimedia messaging in the list
	 * 
	 * @param multimediaMessaging
	 */
	private void addMultimediaMessaging(MultimediaMessagingSessionImpl multimediaMessaging) {
		if (logger.isActivated()) {
			logger.debug("Add a MultimediaMessaging in the list (size=" + mMultimediaMessagingCache.size() + ")");
		}
		
		mMultimediaMessagingCache.put(multimediaMessaging.getSessionId(), multimediaMessaging);
	}

	/**
	 * Remove a multimedia messaging from the list
	 * 
	 * @param sessionId Session ID
	 */
	/* package private */ void removeMultimediaMessaging(String sessionId) {
		if (logger.isActivated()) {
			logger.debug("Remove a MultimediaMessaging from the list (size=" + mMultimediaMessagingCache.size() + ")");
		}
		
		mMultimediaMessagingCache.remove(sessionId);
	}	
	
	/**
	 * Add a multimedia streaming in the list
	 * 
	 * @param multimediaStreaming
	 */
	private void addMultimediaStreaming(MultimediaStreamingSessionImpl multimediaStreaming) {
		if (logger.isActivated()) {
			logger.debug("Add a MultimediaStreaming in the list (size=" + mMultimediaMessagingCache.size() + ")");
		}
		
		mMultimediaStreamingCache.put(multimediaStreaming.getSessionId(), multimediaStreaming);
	}

	/**
	 * Remove a multimedia streaming from the list
	 * 
	 * @param sessionId Session ID
	 */
	/* package private */ void removeMultimediaStreaming(String sessionId) {
		if (logger.isActivated()) {
			logger.debug("Remove a MultimediaStreaming from the list (size=" + mMultimediaMessagingCache.size() + ")");
		}
		
		mMultimediaStreamingCache.remove(sessionId);
	}	

	/**
     * Returns true if the service is registered to the platform, else returns false
     * 
	 * @return Returns true if registered else returns false
     */
    public boolean isServiceRegistered() {
    	return ServerApiUtils.isImsConnected();
    }

	/**
	 * Registers a listener on service registration events
	 *
	 * @param listener Service registration listener
	 */
	public void addEventListener(IRcsServiceRegistrationListener listener) {
		if (logger.isActivated()) {
			logger.info("Add a service listener");
		}
		synchronized (lock) {
			mRcsServiceRegistrationEventBroadcaster.addEventListener(listener);
		}
	}

	/**
	 * Unregisters a listener on service registration events
	 *
	 * @param listener Service registration listener
	 */
	public void removeEventListener(IRcsServiceRegistrationListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove a service listener");
		}
		synchronized (lock) {
			mRcsServiceRegistrationEventBroadcaster.removeEventListener(listener);
		}
	}

	/**
	 * Receive registration event
	 *
	 * @param state Registration state
	 */
	public void notifyRegistrationEvent(boolean state) {
		// Notify listeners
		synchronized (lock) {
			if (state) {
				mRcsServiceRegistrationEventBroadcaster.broadcastServiceRegistered();
			} else {
				mRcsServiceRegistrationEventBroadcaster.broadcastServiceUnRegistered();
			}
		}
	}

	/**
	 * Receive a new SIP session invitation with MRSP media
	 * 
     * @param intent Resolved intent
     * @param session SIP session
	 */
	public void receiveSipMsrpSessionInvitation(Intent msrpSessionInvite, GenericSipMsrpSession session) {
		// Add session in the list
		MultimediaMessagingSessionImpl multimediaMessaging = new MultimediaMessagingSessionImpl(
				session.getSessionID(), mMultimediaMessagingSessionEventBroadcaster,
				mSipService, this);
		session.addListener(multimediaMessaging);
		addMultimediaMessaging(multimediaMessaging);
		
		// Update displayName of remote contact
		mContactsManager.setContactDisplayName(session.getRemoteContact(), session.getRemoteDisplayName());
	}
	
	/**
	 * Receive a new SIP session invitation with RTP media
	 * 
     * @param intent Resolved intent
     * @param session SIP session
	 */
	public void receiveSipRtpSessionInvitation(Intent rtpSessionInvite, GenericSipRtpSession session) {
		MultimediaStreamingSessionImpl multimediaStreaming = new MultimediaStreamingSessionImpl(
				session.getSessionID(), mMultimediaStreamingSessionEventBroadcaster,
				mSipService, this);
		session.addListener(multimediaStreaming);
		addMultimediaStreaming(multimediaStreaming);
		
		// Update displayName of remote contact
		mContactsManager.setContactDisplayName(session.getRemoteContact(), session.getRemoteDisplayName());

		// Broadcast intent related to the received invitation
		IntentUtils.tryToSetExcludeStoppedPackagesFlag(rtpSessionInvite);
		IntentUtils.tryToSetReceiverForegroundFlag(rtpSessionInvite);
		rtpSessionInvite.putExtra(MultimediaStreamingSessionIntent.EXTRA_SESSION_ID,
				session.getSessionID());
		
		// Broadcast intent related to the received invitation
		AndroidFactory.getApplicationContext().sendBroadcast(rtpSessionInvite);
	}

    /**
     * Returns the configuration of the multimedia session service
     * 
     * @return Configuration
     */
	public MultimediaSessionServiceConfiguration getConfiguration() {
		return new MultimediaSessionServiceConfiguration(
				mRcsSettings.getMaxMsrpLengthForExtensions());
	}  
    
	/**
     * Initiates a new session for real time messaging with a remote contact and for a given
     * service extension. The messages are exchanged in real time during the session and may
     * be from any type. The parameter contact supports the following formats: MSISDN in
     * national or international format, SIP address, SIP-URI or Tel-URI. If the format of the
     * contact is not supported an exception is thrown.
     * 
     * @param serviceId Service ID
     * @param contact Contact ID
     * @return Multimedia messaging session
	 * @throws ServerApiException
     */
    public IMultimediaMessagingSession initiateMessagingSession(String serviceId, ContactId contact) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate a multimedia messaging session with " + contact);
		}

		// Test IMS connection
		ServerApiUtils.testIms();

		// Test security extension
		ServerApiUtils.testApiExtensionPermission(serviceId);

		try {
			// Initiate a new session
			String featureTag = FeatureTags.FEATURE_RCSE + "=\"" + FeatureTags.FEATURE_RCSE_EXTENSION + "." + serviceId + "\"";
			final GenericSipMsrpSession session = mSipService.initiateMsrpSession(contact, featureTag);
			
			// Add session listener
			MultimediaMessagingSessionImpl multiMediaMessaging = new MultimediaMessagingSessionImpl(
					session.getSessionID(), mMultimediaMessagingSessionEventBroadcaster,
					mSipService, this);
			session.addListener(multiMediaMessaging);
			mMultimediaMessagingSessionEventBroadcaster.broadcastStateChanged(
					contact, session.getSessionID(), MultimediaSession.State.INITIATING,
					ReasonCode.UNSPECIFIED);

			addMultimediaMessaging(multiMediaMessaging);

			// Start the session
	        new Thread() {
	    		public void run() {
	    			session.startSession();
	    		}
	    	}.start();
			return multiMediaMessaging;

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}

    /**
     * Returns a current messaging session from its unique session ID
     * 
     * @return Multimedia messaging session
     * @throws ServerApiException
     */
	public IMultimediaMessagingSession getMessagingSession(String sessionId)
			throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get multimedia messaging " + sessionId);
		}

		IMultimediaMessagingSession multimediaMessaging = mMultimediaMessagingCache.get(sessionId);
		if (multimediaMessaging != null) {
			return multimediaMessaging;
		}
		return new MultimediaMessagingSessionImpl(sessionId,
				mMultimediaMessagingSessionEventBroadcaster, mSipService, this);
	}


    /**
     * Returns the list of messaging sessions associated to a given service ID
     * 
     * @param serviceId Service ID
     * @return List of sessions
     * @throws ServerApiException
     */
    public List<IBinder> getMessagingSessions(String serviceId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get multimedia messaging sessions for service " + serviceId);
		}

		try {
			List<IBinder> multimediaMessagingSessions = new ArrayList<IBinder>();
			for (IMultimediaMessagingSession multimediaMessagingSession : mMultimediaMessagingCache
					.values()) {
				// Filter on the service ID
				if (multimediaMessagingSession.getServiceId().contains(serviceId)) {
					multimediaMessagingSessions.add(multimediaMessagingSession.asBinder());
				}
			}
			return multimediaMessagingSessions;

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}

    /**
     * Initiates a new session for real time streaming with a remote contact and for a given
     * service extension. The payload are exchanged in real time during the session and may be
     * from any type. The parameter contact supports the following formats: MSISDN in national or
     * international format, SIP address, SIP-URI or Tel-URI. If the format of the contact is
     * not supported an exception is thrown.
     * 
     * @param serviceId Service ID
     * @param contact Contact ID
     * @return Multimedia streaming session
	 * @throws ServerApiException
     */
    public IMultimediaStreamingSession initiateStreamingSession(String serviceId, ContactId contact) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate a multimedia streaming session with " + contact);
		}

		// Test IMS connection
		ServerApiUtils.testIms();
		
		// Test security extension
		ServerApiUtils.testApiExtensionPermission(serviceId);

		try {
			// Initiate a new session
			String featureTag = FeatureTags.FEATURE_RCSE + "=\"" + FeatureTags.FEATURE_RCSE_EXTENSION + "." + serviceId + "\"";
			final GenericSipRtpSession session = mSipService.initiateRtpSession(contact, featureTag);
			
			MultimediaStreamingSessionImpl multimediaStreaming = new MultimediaStreamingSessionImpl(
					session.getSessionID(), mMultimediaStreamingSessionEventBroadcaster,
					mSipService, this);
			session.addListener(multimediaStreaming);
			mMultimediaStreamingSessionEventBroadcaster.broadcastStateChanged(
					contact, session.getSessionID(), MultimediaSession.State.INITIATING,
					ReasonCode.UNSPECIFIED);

			addMultimediaStreaming(multimediaStreaming);

			// Start the session
	        new Thread() {
	    		public void run() {
	    			session.startSession();
	    		}
	    	}.start();
			return multimediaStreaming;

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}

	}

    /**
     * Returns a current streaming session from its unique session ID
     * 
     * @return Multimedia streaming session or null if not found
     * @throws ServerApiException
     */
	public IMultimediaStreamingSession getStreamingSession(String sessionId)
			throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get multimedia streaming " + sessionId);
		}

		IMultimediaStreamingSession multimediaStreaming = mMultimediaStreamingCache.get(sessionId);
		if (multimediaStreaming != null) {
			return multimediaStreaming;
		}
		return new MultimediaStreamingSessionImpl(sessionId,
				mMultimediaStreamingSessionEventBroadcaster, mSipService, this);
	}

    /**
     * Returns the list of streaming sessions associated to a given service ID
     * 
     * @param serviceId Service ID
     * @return List of sessions
     * @throws ServerApiException
     */
    public List<IBinder> getStreamingSessions(String serviceId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get multimedia streaming sessions for service " + serviceId);
		}

		try {
			List<IBinder> multimediaStreamingSessions = new ArrayList<IBinder>();
			for (IMultimediaStreamingSession multimediaStreamingSession : mMultimediaStreamingCache
					.values()) {
				if (multimediaStreamingSession.getServiceId().contains(serviceId)) {
					multimediaStreamingSessions.add(multimediaStreamingSession.asBinder());
				}
			}
			return multimediaStreamingSessions;

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
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

	/**
	 * Adds a listener on multimedia messaging session events
	 *
	 * @param listener Session event listener
	 */
	public void addEventListener2(IMultimediaMessagingSessionListener listener) {
		if (logger.isActivated()) {
			logger.info("Add an event listener");
		}

		synchronized (lock) {
			mMultimediaMessagingSessionEventBroadcaster.addMultimediaMessagingEventListener(listener);
		}
	}

	/**
	 * Removes a listener on multimedia messaging session events
	 *
	 * @param listener Session event listener
	 */
	public void removeEventListener2(IMultimediaMessagingSessionListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove an event listener");
		}
		synchronized (lock) {
			mMultimediaMessagingSessionEventBroadcaster.removeMultimediaMessagingEventListener(listener);
		}
	}

	/**
	 * Adds a listener on multimedia streaming session events
	 *
	 * @param listener Session event listener
	 */
	public void addEventListener3(IMultimediaStreamingSessionListener listener) {
		if (logger.isActivated()) {
			logger.info("Add an event listener");
		}
		synchronized (lock) {
			mMultimediaStreamingSessionEventBroadcaster.addMultimediaStreamingEventListener(listener);
		}
	}

	/**
	 * Removes a listener on multimedia streaming session events
	 *
	 * @param listener Session event listener
	 */
	public void removeEventListener3(IMultimediaStreamingSessionListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove an event listener");
		}

		synchronized (lock) {
			mMultimediaStreamingSessionEventBroadcaster.removeMultimediaStreamingEventListener(listener);
		}
	}
}
