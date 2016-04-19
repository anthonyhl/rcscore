package com.orangelabs.rcs.service;

import android.content.Intent;
import android.os.IBinder;

import com.cmcc.ccs.publicaccount.IPublicAccountService;
import com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl;
import com.gsma.services.rcs.capability.ICapabilityService;
import com.gsma.services.rcs.chat.IChatService;
import com.gsma.services.rcs.contacts.IContactsService;
import com.gsma.services.rcs.extension.IMultimediaSessionService;
import com.gsma.services.rcs.ft.IFileTransferService;
import com.gsma.services.rcs.gsh.IGeolocSharingService;
import com.gsma.services.rcs.ipcall.IIPCallService;
import com.gsma.services.rcs.ish.IImageSharingService;
import com.gsma.services.rcs.upload.IFileUploadService;
import com.gsma.services.rcs.vsh.IVideoSharingService;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.core.ipcall.IPCallService;
import com.orangelabs.rcs.core.richcall.RichcallService;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.ipcall.IPCallHistory;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.sharing.RichCallHistory;
import com.orangelabs.rcs.service.api.CapabilityServiceImpl;
import com.orangelabs.rcs.service.api.ChatServiceImpl;
import com.orangelabs.rcs.service.api.ContactsServiceImpl;
import com.orangelabs.rcs.service.api.FileTransferServiceImpl;
import com.orangelabs.rcs.service.api.FileUploadServiceImpl;
import com.orangelabs.rcs.service.api.GeolocSharingServiceImpl;
import com.orangelabs.rcs.service.api.IPCallServiceImpl;
import com.orangelabs.rcs.service.api.ImageSharingServiceImpl;
import com.orangelabs.rcs.service.api.MultimediaSessionServiceImpl;
import com.orangelabs.rcs.service.api.ServerApiException;
import com.orangelabs.rcs.service.api.VideoSharingServiceImpl;

public class RcsServiceImpl {
	/**
	 * Contacts API
	 */
	public ContactsServiceImpl contactsApi;
	/**
	 * Capability API
	 */
	public CapabilityServiceImpl capabilityApi;
	/**
	 * Chat API
	 */
	public ChatServiceImpl chatApi;
	/**
	 * File transfer API
	 */
	public FileTransferServiceImpl ftApi;
	/**
	 * Video sharing API
	 */
	public VideoSharingServiceImpl vshApi;
	/**
	 * Image sharing API
	 */
	public ImageSharingServiceImpl ishApi;
	/**
	 * Geoloc sharing API
	 */
	public GeolocSharingServiceImpl gshApi;
	/**
	 * IP call API
	 */
	public IPCallServiceImpl ipcallApi;
	/**
	 * Multimedia session API
	 */
	public MultimediaSessionServiceImpl sessionApi;
	/**
	 * File upload API
	 */
	public FileUploadServiceImpl uploadApi;
	/**
	 * public account service
	 */
	public PublicAccountServiceImpl mPublicAccountServiceImpl;
	
	public RcsServiceImpl(ImsModule core, InstantMessagingService imService,
			RichcallService richCallService, IPCallService ipCallService,
			SipService sipService, MessagingLog messgaingLog,
			RichCallHistory richcallLog, RcsSettings rcsSettings,
			ContactsManager contactsManager) {
		contactsApi = new ContactsServiceImpl(); 
		capabilityApi = new CapabilityServiceImpl();
		chatApi = new ChatServiceImpl(imService, messgaingLog, rcsSettings, contactsManager, core);
		ftApi = new FileTransferServiceImpl(imService, messgaingLog, rcsSettings, contactsManager, core);
		vshApi = new VideoSharingServiceImpl(richCallService, richcallLog, rcsSettings, contactsManager, core);
		ishApi = new ImageSharingServiceImpl(richCallService, richcallLog, rcsSettings, contactsManager);
		gshApi = new GeolocSharingServiceImpl(richCallService, contactsManager, richcallLog);
		ipcallApi = new IPCallServiceImpl(ipCallService, IPCallHistory.getInstance(), contactsManager, rcsSettings);
		sessionApi = new MultimediaSessionServiceImpl(sipService, rcsSettings, contactsManager);
		uploadApi = new FileUploadServiceImpl(imService);
        mPublicAccountServiceImpl = new PublicAccountServiceImpl(imService, messgaingLog, rcsSettings, contactsManager, core, chatApi);

	}

	void close() {
		contactsApi.close();
		capabilityApi.close();
		ftApi.close();
		chatApi.close();
		ishApi.close();
		gshApi.close();
		ipcallApi.close();
		vshApi.close();
	}

	public IBinder onBind(Intent intent) { 	
		
	    if (IContactsService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Contacts service API binding");
			}
	        return contactsApi;
	    } else
	    if (ICapabilityService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Capability service API binding");
			}
	        return capabilityApi;
	    } else
	    if (IFileTransferService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("File transfer service API binding");
			}
	        return ftApi;
	    } else
	    if (IChatService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Chat service API binding");
			}
	        return chatApi;
	    } else
	    if (IVideoSharingService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Video sharing service API binding");
			}
	        return vshApi;
	    } else
	    if (IImageSharingService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Image sharing service API binding");
			}
	        return ishApi;
	    } else
	    if (IGeolocSharingService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Geoloc sharing service API binding");
			}
	        return gshApi;
	    } else
	    if (IIPCallService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("IP call service API binding");
			}
	        return ipcallApi;
	    } else
	    if (IMultimediaSessionService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("Multimedia session API binding");
			}
	        return sessionApi;
	    } else
	    if (IFileUploadService.class.getName().equals(intent.getAction())) {
			if (RcsCoreService.logger.isActivated()) {
				RcsCoreService.logger.debug("File upload service API binding");
			}
	        return uploadApi;
	    } 
	    if (IPublicAccountService.class.getName().equals(intent.getAction())) {
	        if (RcsCoreService.logger.isActivated()) {
	            RcsCoreService.logger.debug("public account service API binding");
	        }
	        return mPublicAccountServiceImpl;
	    } else {
	    	return null;
	    }
	}

	public void handleAutoRejoinGroupChat(RcsCoreService rcsCoreService, String chatId) throws ServerApiException {
		chatApi.handleAutoRejoinGroupChat(chatId);
	}

}