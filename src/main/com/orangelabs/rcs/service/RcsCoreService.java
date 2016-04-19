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

package com.orangelabs.rcs.service;

import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;

import com.google.common.base.Preconditions;
import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.R;
import com.orangelabs.rcs.core.CoreListener;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.TerminalInfo;
import com.orangelabs.rcs.core.capability.Capabilities;
import com.orangelabs.rcs.core.content.AudioContent;
import com.orangelabs.rcs.core.content.GeolocContent;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.content.VideoContent;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.im.chat.OneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.TerminatingAdhocGroupChatSession;
import com.orangelabs.rcs.core.im.chat.TerminatingOneToOneChatSession;
import com.orangelabs.rcs.core.im.chat.TerminatingOneToOneLargeModeSession;
import com.orangelabs.rcs.core.im.chat.TerminatingOneToOnePagerModeSession;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.im.chat.standfw.TerminatingStoreAndForwardMsgSession;
import com.orangelabs.rcs.core.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.ipcall.IPCallService;
import com.orangelabs.rcs.core.ipcall.IPCallSession;
import com.orangelabs.rcs.core.registration.ImsError;
import com.orangelabs.rcs.core.richcall.RichcallService;
import com.orangelabs.rcs.core.richcall.geoloc.GeolocTransferSession;
import com.orangelabs.rcs.core.richcall.image.ImageTransferSession;
import com.orangelabs.rcs.core.richcall.video.VideoStreamingSession;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.core.sip.messaging.GenericSipMsrpSession;
import com.orangelabs.rcs.core.sip.streaming.GenericSipRtpSession;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.platform.file.FileFactory;
import com.orangelabs.rcs.protocol.presence.pidf.PidfDocument;
import com.orangelabs.rcs.provider.LocalContentResolver;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.ipcall.IPCallHistory;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData.ConfigurationMode;
import com.orangelabs.rcs.provider.sharing.RichCallHistory;
import com.orangelabs.rcs.provisioning.ProvisionManager;
import com.orangelabs.rcs.provisioning.ProvisioningInfo;
import com.orangelabs.rcs.service.api.ServerApiException;
import com.orangelabs.rcs.settings.SettingsDisplay;
import com.orangelabs.rcs.utils.AppUtils;
import com.orangelabs.rcs.utils.IntentUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * RCS core service. This service offers a flat API to any other process (activities)
 * to access to RCS features. This service is started automatically at device boot.
 * 
 * @author Jean-Marc AUFFRET
 */
public class RcsCoreService extends Service implements CoreListener, NetworkManager.ConnectivityListener {
	/**
	 * Notification ID
	 */
	private final static int SERVICE_NOTIFICATION = 1000;
	
	/**
	 * CPU manager
	 */
	private CpuManager cpuManager = new CpuManager();

    UserAccountManager userActManager;

    RcsServiceImpl serviceImpl;

	/**
	 * The logger
	 */
	public final static Logger logger = Logger.getLogger(RcsCoreService.class.getSimpleName());
	
	RcsSettings settings;
	ContactsManager contactsManager;
	
	NetworkManager networkManager;

    @Override
    public void onCreate() {
    	
    	//device profile
        // Instantiate RcsSettings
        Context ctx = getApplicationContext();   
        Preconditions.checkNotNull(ctx);
        
		// Set application context
		AndroidFactory.setApplicationContext(getApplicationContext());

		// Set the terminal version
		TerminalInfo.setProductVersion(AppUtils.getApplicationVersion(this));
        
        mLocalContentResolver = new LocalContentResolver(ctx.getContentResolver());
        RcsSettings.createInstance(ctx);
        settings = RcsSettings.getInstance();
        
        // Set the logger properties
		Logger.activationFlag = getSettings().isTraceActivated();
		Logger.traceLevel = getSettings().getTraceLevel();
        
		//user profile
        ConfigurationMode mode = getSettings().getConfigurationMode();
    	if (logger.isActivated()) {
    		logger.debug("onCreate ConfigurationMode="+mode);
        }
        // In manual configuration, use a network listener to start RCS core when the data will be ON 
//    	if (ConfigurationMode.MANUAL.equals(mode)) {
//        	registerNetworkStateListener();
//        }
    	
        ContentResolver contentResolver = ctx.getContentResolver();
        ContactsManager.createInstance(ctx, contentResolver, mLocalContentResolver);
        contactsManager = ContactsManager.getInstance(); 
    	
    	userActManager = new UserAccountManager(this, settings, mLocalContentResolver, contactsManager);
    
    	//service profile
        MessagingLog.createInstance(ctx, mLocalContentResolver);
        RichCallHistory.createInstance(mLocalContentResolver);
        IPCallHistory.createInstance(mLocalContentResolver);
        
    	provisionManager = new ProvisionManager(ctx, mLocalContentResolver, settings);
    	
    	networkManager = new NetworkManager(ctx, this);
    	
    	startCore();
    
    }

    @Override
    public void onDestroy() {
    	
    	stopCore();  	
	    serviceImpl = null;    	
		provisionManager.close();
    }

	/**
     * Start core
     */
    public synchronized void startCore() {
    	

		if (ImsModule.getInstance() != null) {
			// Already started
			return;
		}

        try {
    		if (logger.isActivated()) {
    			logger.debug("Start RCS core service");
    		}
            
            // Instantiate the contactUtils instance (CountryCode is already set)
            com.gsma.services.rcs.contacts.ContactUtils.getInstance(this);
            
            // Create the core
			ImsModule.createCore(this);
            ImsModule core = ImsModule.getInstance();

        	// Instantiate API

            InstantMessagingService imService = core.getInstantMessagingService();
            RichcallService richCallService = core.getRichcallService();
            IPCallService ipCallService = core.getIPCallService();
            SipService sipService = core.getSipService();
            
            MessagingLog messgaingLog = MessagingLog.getInstance();
            RichCallHistory richcallLog = RichCallHistory.getInstance();

            RcsSettings rcsSettings = getSettings();
            ContactsManager contactsManager = ContactsManager.getInstance();
            
            serviceImpl = new RcsServiceImpl(core, imService, richCallService, ipCallService,
					sipService, messgaingLog, richcallLog, rcsSettings,
					contactsManager);          

    		// Terminal version
            if (logger.isActivated()) {
                logger.info("RCS stack release is " + TerminalInfo.getProductVersion());
            }

			// Start the core
			core.startCore();		

			// Create multimedia directory on sdcard
			FileFactory.createDirectory(getSettings().getPhotoRootDirectory());
			FileFactory.createDirectory(getSettings().getVideoRootDirectory());
			FileFactory.createDirectory(getSettings().getFileRootDirectory());
			
			// Init CPU manager
			cpuManager.init();

            // Register account changed event receiver
            if (accountChangedReceiver == null) {
                accountChangedReceiver = new AccountChangedReceiver();

                // Register account changed broadcast receiver after a timeout of 2s (This is not done immediately, as we do not want to catch
                // the removal of the account (creating and removing accounts is done asynchronously). We can reasonably assume that no
                // RCS account deletion will be done by user during this amount of time, as he just started his service.
                Handler handler = new Handler();
                handler.postDelayed(
                        new Runnable() {
                            public void run() {
                                registerReceiver(accountChangedReceiver, new IntentFilter(
                                        "android.accounts.LOGIN_ACCOUNTS_CHANGED"));
                            }},
                        2000);
            }

	        // Show a first notification
	    	addRcsServiceNotification(false, getString(R.string.rcs_core_loaded));

			if (logger.isActivated()) {
				logger.info("RCS core service started with success");
			}
		} catch(Exception e) {
			// Unexpected error
			if (logger.isActivated()) {
				logger.error("Can't instanciate the RCS core service", e);
			}
			
			// Show error in notification bar
	    	addRcsServiceNotification(false, getString(R.string.rcs_core_failed));
	    	
			// Exit service
	    	stopSelf();
		}
    }

	public RcsSettings getSettings() {
		return settings;
	}
    
    /**
     * Stop core
     */
    public synchronized void stopCore() {
		if (ImsModule.getInstance() == null) {
			// Already stopped
			return;
		}
		
        // Unregister account changed broadcast receiver
	    if (accountChangedReceiver != null) {
	        try {
	        	unregisterReceiver(accountChangedReceiver);
	        	accountChangedReceiver = null;
	        } catch (IllegalArgumentException e) {
	        	// Nothing to do
	        }
	    }
		
		if (logger.isActivated()) {
			logger.debug("Stop RCS core service");
		}

    	// Close APIs
	    serviceImpl.close();

    	// Terminate the core in background
		ImsModule.terminateCore();

		// Close CPU manager
		cpuManager.close();

		if (logger.isActivated()) {
			logger.info("RCS core service stopped with success");
		}
		
		stopSelf();
    }

	@Override
	public IBinder onBind(Intent intent) {
		//try load 
		start();
		
		if (serviceImpl == null) return null;
		return serviceImpl.onBind(intent);
	}
    
    /**
     * Add RCS service notification
     * 
     * @param state Service state (ON|OFF)
     * @param label Label
     */
	public void addRcsServiceNotification(boolean state, String label) {
    	// Create notification
    	Intent intent = new Intent(getApplicationContext(), SettingsDisplay.class);
    	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
		int iconId; 
		if (state) {
			iconId  = R.drawable.rcs_core_notif_on_icon;
		} else {
			iconId  = R.drawable.rcs_core_notif_off_icon; 
		}
//        Notification notif = new Notification(iconId, "", System.currentTimeMillis());
//        notif.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_FOREGROUND_SERVICE;
//        notif.setLatestEventInfo(getApplicationContext(),
//        		getString(R.string.rcs_core_rcs_notification_title),
//        		label, contentIntent);
        
         Notification notif = new Notification.Builder(getApplicationContext())
         .setAutoCancel(false)
         .setContentTitle(getString(R.string.rcs_core_rcs_notification_title))
         .setContentText(label)
         .setContentIntent(contentIntent)
         .setSmallIcon(iconId)
         .build();
        
        // Send notification
		NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(SERVICE_NOTIFICATION);
		notificationManager.notify(SERVICE_NOTIFICATION, notif);
    }
	
    
    /*---------------------------- CORE EVENTS ---------------------------*/

    /**
	 * Notify registration status to API
	 * 
	 * @param status Status
	 */
	private void notifyRegistrationStatusToApi(boolean status) {
		if (serviceImpl.capabilityApi != null) {
			serviceImpl.capabilityApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.chatApi != null) {
			serviceImpl.chatApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.ftApi != null) {
			serviceImpl.ftApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.vshApi != null) {
			serviceImpl.vshApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.ishApi != null) {
			serviceImpl.ishApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.gshApi != null) {
			serviceImpl.gshApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.ipcallApi != null) {
			serviceImpl.ipcallApi.notifyRegistrationEvent(status);
		}
		if (serviceImpl.sessionApi != null) {
			serviceImpl.sessionApi.notifyRegistrationEvent(status);
		}
	}    
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleCoreLayerStarted()
     */
    public void handleCoreLayerStarted() {
		if (logger.isActivated()) {
			logger.debug("Handle event core started");
		}

		// Display a notification
		addRcsServiceNotification(false, getString(R.string.rcs_core_started));

		// Send service up intent
		Intent serviceUp = new Intent(RcsService.ACTION_SERVICE_UP);
		IntentUtils.tryToSetReceiverForegroundFlag(serviceUp);
		getApplicationContext().sendBroadcast(serviceUp);
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleCoreLayerStopped()
     */
    public void handleCoreLayerStopped() {
        // Display a notification
        if (logger.isActivated()) {
            logger.debug("Handle event core terminated");
        }
        addRcsServiceNotification(false, getString(R.string.rcs_core_stopped));
    }
    
	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleRegistrationSuccessful()
	 */
	public void handleRegistrationSuccessful() {
		if (logger.isActivated()) {
			logger.debug("Handle event registration ok");
		}
		
		// Display a notification
		addRcsServiceNotification(true, getString(R.string.rcs_core_ims_connected));
		
		// Notify APIs
		notifyRegistrationStatusToApi(true);
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleRegistrationFailed(com.orangelabs.rcs.core.ImsError)
	 */
	public void handleRegistrationFailed(ImsError error) {
		if (logger.isActivated()) {
			logger.debug("Handle event registration failed");
		}

		// Display a notification
		addRcsServiceNotification(false, getString(R.string.rcs_core_ims_connection_failed));

		// Notify APIs
		notifyRegistrationStatusToApi(false);
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleRegistrationTerminated()
	 */
	public void handleRegistrationTerminated() {
        if (logger.isActivated()) {
            logger.debug("Handle event registration terminated");
        }

        if (getImsConnectionManager().isDisconnectedByBattery()) {
            // Display a notification
            addRcsServiceNotification(false, getString(R.string.rcs_core_ims_battery_disconnected));
        } else {
            // Display a notification
        	addRcsServiceNotification(false, getString(R.string.rcs_core_ims_disconnected));
        }
        
		// Notify APIs
		notifyRegistrationStatusToApi(false);
	}

    private NetworkManager getImsConnectionManager() {
		
		return networkManager;
	}

	/* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handlePresenceSharingNotification(java.lang.String, java.lang.String, java.lang.String)
     */
    public void handlePresenceSharingNotification(ContactId contact, String status, String reason) {
		if (logger.isActivated()) {
			logger.debug("Handle event presence sharing notification for " + contact + " (" + status + ":" + reason + ")");
		}
		// Not used
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handlePresenceInfoNotification(java.lang.String, com.orangelabs.rcs.core.service.presence.pidf.PidfDocument)
     */
    public void handlePresenceInfoNotification(ContactId contact, PidfDocument presence) {
    	if (logger.isActivated()) {
			logger.debug("Handle event presence info notification for " + contact);
		}
		// Not used
	}
    
    public void handleCapabilitiesNotification(ContactId contact, Capabilities capabilities) {
    	if (logger.isActivated()) {
			logger.debug("Handle capabilities update notification for " + contact + " (" + capabilities.toString() + ")");
		}

		// Notify API
		serviceImpl.capabilityApi.receiveCapabilities(contact, capabilities);
    }
    
    public void handlePresenceSharingInvitation(ContactId contact) {
		if (logger.isActivated()) {
			logger.debug("Handle event presence sharing invitation");
		}
		// Not used
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleContentSharingTransferInvitation(com.orangelabs.rcs.core.service.richcall.image.ImageTransferSession)
     */
    public void handleContentSharingTransferInvitation(ImageTransferSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event content sharing transfer invitation");
		}

		// Broadcast the invitation
		serviceImpl.ishApi.receiveImageSharingInvitation(session);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleContentSharingTransferInvitation(com.orangelabs.rcs.core.service.richcall.geoloc.GeolocTransferSession)
     */
    public void handleContentSharingTransferInvitation(GeolocTransferSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event content sharing transfer invitation");
		}

		// Broadcast the invitation
		serviceImpl.gshApi.receiveGeolocSharingInvitation(session);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleContentSharingStreamingInvitation(com.orangelabs.rcs.core.service.richcall.video.VideoStreamingSession)
     */
    public void handleContentSharingStreamingInvitation(VideoStreamingSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event content sharing streaming invitation");
		}

		// Broadcast the invitation
		serviceImpl.vshApi.receiveVideoSharingInvitation(session);
    }
	
    @Override
	public void handleFileTransferInvitation(FileSharingSession fileSharingSession, boolean isGroup, ContactId contact,
			String displayName) {
		if (logger.isActivated()) {
			logger.debug("Handle event file transfer invitation");
		}

    	// Broadcast the invitation
		serviceImpl.ftApi.receiveFileTransferInvitation(fileSharingSession, isGroup, contact, displayName);
	}
    
    @Override
	public void handleOneToOneFileTransferInvitation(FileSharingSession fileSharingSession, OneToOneChatSession oneToOneChatSession) {
		if (logger.isActivated()) {
			logger.debug("Handle event file transfer invitation");
		}
		
    	// Broadcast the invitation
		serviceImpl.ftApi.receiveFileTransferInvitation(fileSharingSession, false, oneToOneChatSession.getRemoteContact(),
				oneToOneChatSession.getRemoteDisplayName());
	}

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleIncomingFileTransferResuming(com.orangelabs.rcs.core.service.im.filetransfer.FileSharingSession, boolean, java.lang.String, java.lang.String)
     */
    public void handleIncomingFileTransferResuming(FileSharingSession session, boolean isGroup, String chatSessionId, String chatId) {
        if (logger.isActivated()) {
            logger.debug("Handle event incoming file transfer resuming");
        }

        // Broadcast the invitation
        serviceImpl.ftApi.resumeIncomingFileTransfer(session, isGroup, chatSessionId, chatId);
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleOutgoingFileTransferResuming(com.orangelabs.rcs.core.service.im.filetransfer.FileSharingSession, boolean)
     */
    public void handleOutgoingFileTransferResuming(FileSharingSession session, boolean isGroup) {
        if (logger.isActivated()) {
            logger.debug("Handle event outgoing file transfer resuming");
        }

        // Broadcast the invitation
        serviceImpl.ftApi.resumeOutgoingFileTransfer(session, isGroup);
    }

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleOneOneChatSessionInvitation(com.orangelabs.rcs.core.service.im.chat.TerminatingOne2OneChatSession)
	 */
	public void handleOneOneChatSessionInvitation(TerminatingOneToOneChatSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive 1-1 chat session invitation");
		}
		
    	// Broadcast the invitation
		serviceImpl.chatApi.receiveOneOneChatInvitation(session);
    }

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleAdhocGroupChatSessionInvitation(com.orangelabs.rcs.core.service.im.chat.TerminatingAdhocGroupChatSession)
	 */
	public void handleAdhocGroupChatSessionInvitation(TerminatingAdhocGroupChatSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive ad-hoc group chat session invitation");
		}

    	// Broadcast the invitation
		serviceImpl.chatApi.receiveGroupChatInvitation(session);
	}
	
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleStoreAndForwardMsgSessionInvitation(com.orangelabs.rcs.core.service.im.chat.standfw.TerminatingStoreAndForwardMsgSession)
     */
    public void handleStoreAndForwardMsgSessionInvitation(TerminatingStoreAndForwardMsgSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event S&F messages session invitation");
		}
		
    	// Broadcast the invitation
		serviceImpl.chatApi.receiveOneOneChatInvitation(session);
    }
    
    public void handleMessageDeliveryStatus(ContactId contact, ImdnDocument imdn) {
		if (logger.isActivated()) {
			logger.debug("Handle message delivery status");
		}
    	
		serviceImpl.chatApi.receiveMessageDeliveryStatus(contact, imdn);
    }
    
    public void handleFileDeliveryStatus(ContactId contact, ImdnDocument imdn) {
    	 if (logger.isActivated()) {
        	 logger.debug("Handle file delivery status: fileTransferId=" + imdn.getMsgId()
        			 + " notification_type=" + imdn.getNotificationType() + " status="
        			 + imdn.getStatus() + " contact=" + contact);
         }

        serviceImpl.ftApi.handleFileDeliveryStatus(imdn,  contact);
    }

	public void handleGroupFileDeliveryStatus(String chatId, ContactId contact, ImdnDocument imdn) {
		if (logger.isActivated()) {
			logger.debug("Handle group file delivery status: fileTransferId=" + imdn.getMsgId()
					+ " notification_type=" + imdn.getNotificationType() + " status="
					+ imdn.getStatus() + " contact=" + contact);
		}

		serviceImpl.ftApi.handleGroupFileDeliveryStatus(chatId, imdn, contact);
	}

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleSipSessionInvitation(android.content.Intent, com.orangelabs.rcs.core.service.sip.GenericSipSession)
     */
    public void handleSipMsrpSessionInvitation(Intent intent, GenericSipMsrpSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive SIP MSRP session invitation");
		}
		
		// Broadcast the invitation
		serviceImpl.sessionApi.receiveSipMsrpSessionInvitation(intent, session);
    }    
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleSipSessionInvitation(android.content.Intent, com.orangelabs.rcs.core.service.sip.GenericSipSession)
     */
    public void handleSipRtpSessionInvitation(Intent intent, GenericSipRtpSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive SIP RTP session invitation");
		}
		
		// Broadcast the invitation
		serviceImpl.sessionApi.receiveSipRtpSessionInvitation(intent, session);
    }    

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleUserConfirmationRequest(java.lang.String, java.lang.String, java.lang.String, boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int)
     */
    public void handleUserConfirmationRequest(ContactId remote, String id,
    		String type, boolean pin, String subject, String text,
    		String acceptButtonLabel, String rejectButtonLabel, int timeout) {
        if (logger.isActivated()) {
			logger.debug("Handle event user terms confirmation request");
		}

		// Nothing to do here
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleUserConfirmationAck(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void handleUserConfirmationAck(ContactId remote, String id, String status, String subject, String text) {
		if (logger.isActivated()) {
			logger.debug("Handle event user terms confirmation ack");
		}

		// Nothing to do here
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleUserNotification(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void handleUserNotification(ContactId remote, String id, String subject, String text, String okButtonLabel) {
        if (logger.isActivated()) {
            logger.debug("Handle event user terms notification");
        }

		// Nothing to do here
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleSimHasChanged()
     */
    public void handleSimHasChanged() {
        if (logger.isActivated()) {
            logger.debug("Handle SIM has changed");
        }

		// Restart the RCS service
        LauncherUtils.stopRcsService(getApplicationContext());
        LauncherUtils.launchRcsService(getApplicationContext(), true, false);
    }

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleIPCallInvitation(com.orangelabs.rcs.core.service.ipcall.IPCallSession)
	 */
	public void handleIPCallInvitation(IPCallSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event IP call invitation");
		}
		
		// Broadcast the invitation
		serviceImpl.ipcallApi.receiveIPCallInvitation(session);
	}

	@Override
	public void tryToDispatchAllPendingDisplayNotifications() {
		serviceImpl.chatApi.tryToDispatchAllPendingDisplayNotifications();
	}

	@Override
	public void handleFileTransferInvitationRejected(ContactId contact, MmContent content,
			MmContent fileicon, int reasonCode) {
		serviceImpl.ftApi.addAndBroadcastFileTransferInvitationRejected(contact, content, fileicon, reasonCode);
	}

	@Override
	public void handleGroupChatInvitationRejected(String chatId, ContactId contact, String subject,
			Set<ParticipantInfo> participants, int reasonCode) {
		serviceImpl.chatApi.addAndBroadcastGroupChatInvitationRejected(chatId, contact, subject, participants, reasonCode);
	}

	@Override
	public void handleImageSharingInvitationRejected(ContactId contact, MmContent content,
			int reasonCode) {
		serviceImpl.ishApi.addAndBroadcastImageSharingInvitationRejected(contact, content, reasonCode);
	}

	@Override
	public void handleVideoSharingInvitationRejected(ContactId contact, VideoContent content,
			int reasonCode) {
		serviceImpl.vshApi.addAndBroadcastVideoSharingInvitationRejected(contact, content, reasonCode);
	}

	@Override
	public void handleGeolocSharingInvitationRejected(ContactId contact, GeolocContent content,
			int reasonCode) {
		serviceImpl.gshApi.addAndBroadcastGeolocSharingInvitationRejected(contact, content, reasonCode);
	}

	@Override
	public void handleIPCallInvitationRejected(ContactId contact, AudioContent audioContent,
			VideoContent videoContent, int reasonCode) {
		serviceImpl.ipcallApi.addAndBroadcastIPCallInvitationRejected(contact, audioContent, videoContent, reasonCode);
	}

	public void handleOneOneChatSessionInitiation(OneToOneChatSession session) {
		serviceImpl.chatApi.handleOneToOneChatSessionInitiation(session);
	}

	@Override
	public void handleRejoinGroupChatAsPartOfSendOperation(String chatId) throws ServerApiException {
		serviceImpl.chatApi.handleRejoinGroupChatAsPartOfSendOperation(chatId);
	}

	public void handleAutoRejoinGroupChat(String chatId) throws ServerApiException {
		serviceImpl.handleAutoRejoinGroupChat(this, chatId);
	}


    /**********************************************************************************
     * tct-stack add for fix bug: don't send queued file transfers
     **********************************************************************************/

    public void handleSendGroupQueuedFileTransfer(String chatId) {
        if (logger.isActivated()) {
            logger.debug("handleSendGroupQueuedFileTransfer chatId=" + chatId);
        }

        // Broadcast the operation
        serviceImpl.ftApi.handleSendGroupQueuedFileTransfer(chatId);
    }


    /**********************************************************************************
     *  tct-stack add for CMCC message modes: 1) pager mode; 2) large mode
     **********************************************************************************/

    @Override
    public void handleOneOneChatPagerModeInvitation(TerminatingOneToOnePagerModeSession session) {
        if (logger.isActivated()) {
            logger.debug("Handle event receive 1-1 pager mode message invitation");
        }

        // Broadcast the invitation
        serviceImpl.chatApi.receiveOneOneChatPagerModeInvitation(session);
    }

    @Override
    public void handleOneOneChatLargeModeInvitation(TerminatingOneToOneLargeModeSession session) {
        if (logger.isActivated()) {
            logger.debug("Handle event receive 1-1 chat large mode message invitation");
        }

        // Broadcast the invitation
        serviceImpl.chatApi.receiveOneOneChatLargeModeInvitation(session);
    };

    @Override
    public void handleMsrpFileTransferInvitation(FileSharingSession fileSharingSession, boolean isGroup, ContactId contact,
            String displayName) {
        if (logger.isActivated()) {
            logger.debug("Handle event msrp file transfer invitation");
        }

        // Broadcast the invitation
        serviceImpl.ftApi.receiveMsrpFileTransferInvitation(fileSharingSession, isGroup, contact, displayName);
    
    }


    public LocalContentResolver mLocalContentResolver;

    private static final String INTENT_KEY_BOOT = "boot";
    private static final String INTENT_KEY_USER = "user";

	private static final String ACTION_START = "org.gsma.service.rcs.action_start";

	private static final String ACTION_START_CORE = "org.gsma.service.rcs.action_start_core";

	private static final String ACTION_STOP_CORE = "org.gsma.service.rcs.action_stop_core";

	private static final String ACTION_START_PROVISIONING = "org.gsma.service.rcs.action_start_provisioning";


    @Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
    	if (intent == null || intent.getAction() == null) {
    		return START_NOT_STICKY;
    	}
    	
		if (logger.isActivated()) {
			logger.debug("Start RCS service with command " + intent.getAction());
		}
		
		if (intent.getAction().equals(ACTION_START)) {		
			// Check boot
			if (intent != null) {
				provisionManager.boot = intent.getBooleanExtra(INTENT_KEY_BOOT, false);
				provisionManager.user = intent.getBooleanExtra(INTENT_KEY_USER, false);
			}
			
			start();

		} else if (intent.getAction().equals(ACTION_START_CORE)) {			

			ImsModule.getInstance().connect(networkManager.getNetwork());
		
		} else if (intent.getAction().equals(ACTION_STOP_CORE)) {
			
			ImsModule.getInstance().disconnect(networkManager.getNetwork());
			
		} else if (intent.getAction().equals(ACTION_START_PROVISIONING)) {
			
			boolean first = false;
			boolean user = false;
			if (intent != null) {
				first = intent.getBooleanExtra(FIRST_KEY, false);
				user = intent.getBooleanExtra(USER_KEY, false);
			}
			provisionManager.startProvision(first, user);			
		}

		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

    /**
     * Launch the RCS service.
     *
     * @param boot indicates if RCS is launched from the device boot
     * @param user indicates if RCS is launched from the user interface
     */
	private void launchRcsService(boolean boot, boolean user) {
		ConfigurationMode mode = getSettings().getConfigurationMode();

		if (logger.isActivated())
			logger.debug("Launch RCS service: HTTPS=" + mode + ", boot=" + boot + ", user=" + user);

		if (ConfigurationMode.AUTO.equals(mode)) {
			// HTTPS auto config
			String version = getSettings().getProvisioningVersion();
			// Check the last provisioning version
			if (ProvisioningInfo.Version.RESETED_NOQUERY.equals(version)) {
				// (-1) : RCS service is permanently disabled. SIM change is required
				if (userActManager.hasChangedAccount()) {
					// Start provisioning as a first launch
					startHttpsProvisioningService(getApplicationContext(), true, user);
				} else {
					if (logger.isActivated()) {
						logger.debug("Provisioning is blocked with this account");
					}
				}
			} else {
				if (userActManager.isFirstLaunch() || userActManager.hasChangedAccount()) {
					// First launch: start the auto config service with special tag
					startHttpsProvisioningService(getApplicationContext(), true, user);
				} else {
					if (ProvisioningInfo.Version.DISABLED_NOQUERY.equals(version)) {
						// -2 : RCS client and configuration query is disabled
						if (user) {
							// Only start query if requested by user action
							startHttpsProvisioningService(getApplicationContext(), false, user);
						}
					} else {
						// Start or restart the HTTP provisioning service
						startHttpsProvisioningService(getApplicationContext(), false, user);
						if (ProvisioningInfo.Version.DISABLED_DORMANT.equals(version)) {
							// -3 : RCS client is disabled but configuration query is not
						} else {
							// Start the RCS core service
							LauncherUtils.launchRcsCoreService(getApplicationContext());
						}
					}
				}
			}
		} else {
			// No auto config: directly start the RCS core service
			LauncherUtils.launchRcsCoreService(getApplicationContext());
		}
	}

    void start() {
		if (logger.isActivated())
			logger.debug("Launch RCS service (boot=" + provisionManager.boot + ") (user="+provisionManager.user+")");
		
		if (userActManager.checkAccount()) {
			launchRcsService(provisionManager.boot, provisionManager.user);
		} else {
			// User account can't be initialized (no radio to read IMSI, .etc)
			if (logger.isActivated()) {
				logger.error("Can't create the user account");
			}
			// Exit service
			stopSelf();
		}
	}

	/**
	 * Launch the RCS start service
	 * 
	 * @param context
	 * @param boot
	 *            start RCS service upon boot
	 * @param user
	 *            start RCS service upon user action
	 */
	static void LaunchRcsStartService(Context context, boolean boot, boolean user) {
		
		Intent intent = new Intent(context, RcsCoreService.class);
		intent.setAction(ACTION_START);
		intent.putExtra(INTENT_KEY_BOOT, boot);
		intent.putExtra(INTENT_KEY_USER, user);
		context.startService(intent);
	}
	
    /**
     * Intent key - Provisioning requested after (re)boot
     */
    public static final String FIRST_KEY = "first";

    /**
     * Intent key - Provisioning requested by user
     */
    public static final String USER_KEY = "user";

    public ProvisionManager provisionManager;

	/**
	 * Account changed broadcast receiver
	 */
	public AccountChangedReceiver accountChangedReceiver;

	/**
	 * Start the HTTPs provisioning service
	 * 
	 * @param context
	 * @param firstLaunch
	 *            first launch after (re)boot
	 * @param userLaunch
	 *            launch is requested by user action
	 */
	public static void startHttpsProvisioningService(Context context , boolean firstLaunch, boolean userLaunch) {
		// Start Https provisioning service
		Intent provisioningIntent = new Intent(context, RcsCoreService.class);
		provisioningIntent.setAction(ACTION_START_PROVISIONING);
		provisioningIntent.putExtra(FIRST_KEY, firstLaunch);
		provisioningIntent.putExtra(USER_KEY, userLaunch);
		context.startService(provisioningIntent);
	}

	public static void stopRcsCoreService(Context context) {
//		Intent intent = new Intent(context, RcsCoreService.class);
//		intent.setAction(ACTION_STOP_CORE);
//		context.startService(intent);
		context.stopService(new Intent(context, RcsCoreService.class));
	}
	
	public static void startRcsCoreService(Context context) {
		Intent intent = new Intent(context, RcsCoreService.class);
		intent.setAction(ACTION_START_CORE);
		context.startService(intent);		
	}

	@Override
	public void onConnected(int network) {
		ImsModule.getInstance().connect(network);
	}

	@Override
	public void onDisconnected(int network) {
		ImsModule.getInstance().disconnect(network);
	}
}
