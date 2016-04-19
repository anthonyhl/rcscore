/*******************************************************************************
 * Software Name: RCS
 *
 * Copyright (C) 2015 TCL Telecommunication Technology Ltd.
 *
 * Description: TODO
 *
 * Feb 12, 2015
 *
 *Author: lei.zhang9282@icloud.com
 *
 * Modification record:
 *-------------------------------------------------------------------------------------------
 * | Feb 12, 2015 | init it.                                                                     |
 *-------------------------------------------------------------------------------------------
 ******************************************************************************/

package com.cmcc.ccs.publicaccount.api;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;

import com.cmcc.ccs.publicaccount.IPublicAccountChatListener;
import com.cmcc.ccs.publicaccount.IPublicAccountService;
import com.cmcc.ccs.publicaccount.api.IPublicAccountApi.ResultWrap;
import com.cmcc.ccs.publicaccount.provider.IMessageLog;
import com.cmcc.ccs.publicaccount.provider.MessageLogImpl;
import com.gsma.services.rcs.IRcsServiceRegistrationListener;
import com.gsma.services.rcs.chat.IChatMessage;
import com.gsma.services.rcs.chat.IOneToOneChat;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.im.InstantMessagingService;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.messaging.MessagingLog;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.service.api.ChatServiceImpl;
import com.orangelabs.rcs.service.api.ServerApiException;
import com.orangelabs.rcs.service.api.ServerApiUtils;
import com.orangelabs.rcs.service.broadcaster.RcsServiceRegistrationEventBroadcaster;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.DateUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * @author leizhang
 *
 */
public class PublicAccountServiceImpl extends IPublicAccountService.Stub{
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(PublicAccountServiceImpl.class.getSimpleName());

    private final boolean DEBUG = false;

    private final static Executor mDisplayNotificationProcessor = Executors
            .newSingleThreadExecutor();

    private final RcsServiceRegistrationEventBroadcaster mRcsServiceRegistrationEventBroadcaster = new RcsServiceRegistrationEventBroadcaster();

    /**
     * the list of IPublicAccountChatListener
     */
//    private List<IPublicAccountChatListener> listeners = new ArrayList<IPublicAccountChatListener>();
    private PublicAccountEventBroadcaster broadcaster = new PublicAccountEventBroadcaster();
    /**
     * the api
     */
    private IPublicAccountApi mIPublicAccountApi = null;

    private final InstantMessagingService mImService;

    private final MessagingLog mMessagingLog;

    private final RcsSettings mRcsSettings;

    private final ContactsManager mContactsManager;

    private final ImsModule mCore ;

    /**
     * the chat service
     */
    private ChatServiceImpl mChatServiceImpl = null;
    private Handler mHandler = new Handler(){
        public void handleMessage(android.os.Message msg) {
            /*
             *only process server response
             */
            if(null == msg.obj || !(msg.obj instanceof IPublicAccountApi.ResultWrap)){
                return;
            }

            ResultWrap data = (ResultWrap) msg.obj;
            final String action = data.method_name;
            IMessageLog iMessageLog = MessageLogImpl.newInstance(AndroidFactory.getApplicationContext());
            switch (action) {
                case IPublicAccountApi.APIDescription.METHOD_ADDSUBSCRIBE:
                    //write data
                    String pa_uuid = data.otherParam.get("pa_uuid");
                    iMessageLog.followPublicAccount(pa_uuid);
                    broadcaster.onFollowPublicAccount(pa_uuid, data.code, data.desc);
                    break;
                case IPublicAccountApi.APIDescription.METHOD_CANCELSUBSCRIBE:
                    pa_uuid = data.otherParam.get("pa_uuid");
                    iMessageLog.unfollowPublicAccount(pa_uuid);
                    broadcaster.onUnFollowPublicAccount(pa_uuid, data.code, data.desc);
                    break;
                case IPublicAccountApi.APIDescription.METHOD_COMPLAINPUBLIC:
                    broadcaster.onReportPublicAccount(data.otherParam.get("pa_uuid"), data.code, data.desc);
                    break;
                case IPublicAccountApi.APIDescription.METHOD_GETPREMESSAGE:

                    break;
                case IPublicAccountApi.APIDescription.METHOD_GETPUBLICDETAIL:
                    pa_uuid = data.otherParam.get("pa_uuid");
                    iMessageLog.saveInfo(pa_uuid,data.lastXml);
                    broadcaster.onGetInfo(pa_uuid, data.code, data.desc);
                    break;
                case IPublicAccountApi.APIDescription.METHOD_GETPUBLICLIST:
                    iMessageLog.saveSearch(data.lastXml);
                    broadcaster.onSearch(data.code, data.desc);
                    break;
                case IPublicAccountApi.APIDescription.METHOD_GETPUBLICMENU:
                    pa_uuid = data.otherParam.get("pa_uuid");
                    iMessageLog.saveMenu(pa_uuid, data.lastXml);
                    broadcaster.onMenuConfigUpdated(pa_uuid,data.lastXml, data.code, data.desc);
                    break;
                case IPublicAccountApi.APIDescription.METHOD_GETPUBLICRECOMMEND:
                    //TODO
                    break;
                case IPublicAccountApi.APIDescription.METHOD_QUERYUSERSUB:
                    //TODO
                    break;
                case IPublicAccountApi.APIDescription.METHOD_SETACCEPTSTATUS:
                    //TODO
                    break;
                default:
                    break;
            }
        };
    };
    /**
     * Lock used for synchronization
     */
    private final Object lock = new Object();
    /**
     * Constructor
     *
     * @param imService InstantMessagingService
     * @param messagingLog MessagingLog
     * @param rcsSettings RcsSettings
     * @param contactsManager ContactsManager
     * @param core Core
     */
    public PublicAccountServiceImpl(InstantMessagingService imService, MessagingLog messagingLog,
            RcsSettings rcsSettings, ContactsManager contactsManager, ImsModule core,ChatServiceImpl chatServiceImpl) {
        if (logger.isActivated()) {
            logger.info("Chat service API is loaded");
        }
        mImService = imService;
        mMessagingLog = messagingLog;
        mRcsSettings = rcsSettings;
        mContactsManager = contactsManager;
        mCore = core;
        mChatServiceImpl = chatServiceImpl;
        mIPublicAccountApi = APIImpl.newInstance(AndroidFactory.getApplicationContext(),mHandler);
        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.Authorization();
            }
        }).start();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#addEventListener(com.cmcc.ccs.publicaccount.IPublicAccountChatListener)
     */
    @Override
    public void addEventListener1(IPublicAccountChatListener listener) throws RemoteException {
        LogI("addEventListener entry");

        synchronized (lock) {
           broadcaster.addPublicAccountEventListener(listener);
        }
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#removeEventListener(com.cmcc.ccs.publicaccount.IPublicAccountChatListener)
     */
    @Override
    public void removeEventListener1(IPublicAccountChatListener listener) throws RemoteException {
        LogI("removeEventListener entry");

        synchronized (lock) {
            broadcaster.removePublicAccountEventListener(listener);
        }
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#sendMessage(java.lang.String, java.lang.String)
     */
    @Override
    public String sendMessage(String accountnumber, String message) throws RemoteException {
        LogI("sendMessage entry{accountnumber=["+accountnumber+"] message=["+message+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return null;
        }

        IOneToOneChat oneoneChat = null;
        oneoneChat = mChatServiceImpl.getOneToOneChat(ContactUtils.createContactId(accountnumber));
        IChatMessage chatMessage =  oneoneChat.sendMessage(message);
        return chatMessage.getId();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#deleteMessage(long)
     */
    @Override
    public boolean deleteMessage(long msgId) throws RemoteException {
        LogI("deleteMessage entry{msgId=["+msgId+"]}");

        if( msgId < 1){
            return false;
        }

        //TODO
        return false;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#sendMessage1(java.lang.String, int)
     */
    @Override
    public boolean sendMessage1(String accountnumber, int menuID) throws RemoteException {
        LogI("sendMessage1 entry{accountnumber=["+accountnumber+"] menuID=["+menuID+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return false;
        }

        IOneToOneChat oneoneChat = null;
        oneoneChat = mChatServiceImpl.getOneToOneChat(ContactUtils.createContactId(accountnumber));
        //FIXME///////
        oneoneChat.sendMessage(menuID+"");

        return true;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#setMessageRead(long)
     */
    @Override
    public boolean setMessageRead(long msgId) throws RemoteException {
        LogI("setMessageRead entry{msgId=["+msgId+"]}");

        if(msgId < 1){
            return false;
        }

        mChatServiceImpl.markMessageAsRead(String.valueOf(msgId));
        return true;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#getPublicAccountInfo(java.lang.String)
     */
    @Override
    public void getPublicAccountInfo(final String accountnumber) throws RemoteException {
        LogI("getPublicAccountInfo entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.getPublicDetail(accountnumber, DateUtils.getUTCStringForCMCC());
            }
        }).start();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#followPublicAccount(java.lang.String)
     */
    @Override
    public void followPublicAccount(final String accountnumber) throws RemoteException {
        LogI("followPublicAccount entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.subscribe(accountnumber);
            }
        }).start();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#searchPublicAccount(java.lang.String, int, int, int)
     */
    @Override
    public void searchPublicAccount(final String keyword,final int pageno,final int order,final int pagesize)
            throws RemoteException {
        LogI("searchPublicAccount entry{keyword=["+keyword+"] pageno=["+pageno+"] order=["+order+"] pagesize=["+pagesize+"]}");

        if(TextUtils.isEmpty(keyword) || pageno < 1 || pagesize < 1){
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.getPublicList(keyword, order, pagesize, pageno);
            }
        }).start();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#unfollowPublicAccount(java.lang.String)
     */
    @Override
    public void unfollowPublicAccount(final String accountnumber) throws RemoteException {
        LogI("unfollowPublicAccount entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.cancelSubscribe(accountnumber);
            }
        }).start();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#getPublicAccountStatus(java.lang.String)
     */
    @Override
    public boolean getPublicAccountStatus(String accountnumber) throws RemoteException {
        LogI("getPublicAccountStatus entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return false;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#reportPublicAccount(java.lang.String, java.lang.String)
     */
    @Override
    public void reportPublicAccount(final String accountnumber,final String content) throws RemoteException {
        LogI("reportPublicAccount entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.complainPublic(accountnumber, IPublicAccountApi.ComplainType.TYPE_CONTENT, "", content, "");
            }
        }).start();
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#updateMenuConfig(java.lang.String)
     */
    @Override
    public void updateMenuConfig(final String accountnumber) throws RemoteException {
        LogI("updateMenuConfig entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mIPublicAccountApi.getPublicMenu(accountnumber, DateUtils.getUTCStringForCMCC());
            }
        }).start();
    }

    private void LogI(String msg){
        if (DEBUG) {
            if (logger.isActivated()) {
                logger.info(msg);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#addEventListener(com.gsma.services.rcs.IRcsServiceRegistrationListener)
     */
    @Override
    public void addEventListener(IRcsServiceRegistrationListener listener) throws RemoteException {
       LogI("Add a service listener");

//        synchronized (lock) {
            mRcsServiceRegistrationEventBroadcaster.addEventListener(listener);
//        }
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.IPublicAccountService#removeEventListener(com.gsma.services.rcs.IRcsServiceRegistrationListener)
     */
    @Override
    public void removeEventListener(IRcsServiceRegistrationListener listener)
            throws RemoteException {
        LogI("Remove a service listener");

        synchronized (lock) {
            mRcsServiceRegistrationEventBroadcaster.removeEventListener(listener);
        }
    }

    /**
     * Returns true if the service is registered to the platform, else returns false
     *
     * @return Returns true if registered else returns false
     */
    @Override
    public boolean isServiceRegistered() {
        return ServerApiUtils.isImsConnected();
    }

    /**
     * Close API
     */
    public void close() {
        if (logger.isActivated()) {
            logger.info("Chat service API is closed");
        }
    }

    public class PublicAccountEventBroadcaster{
        private final RemoteCallbackList<IPublicAccountChatListener> mPublicAccountListeners = new RemoteCallbackList<IPublicAccountChatListener>();

        /**
         *
         */
        public PublicAccountEventBroadcaster() {
            // TODO Auto-generated constructor stub
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onNewPublicAccoutChat(java.lang.String, java.lang.String)
         */
        public void onNewPublicAccoutChat(String accountnumber, String msgId) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onNewPublicAccoutChat(
                            accountnumber, msgId);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onFollowPublicAccount(java.lang.String, int, java.lang.String)
         */
        public void onFollowPublicAccount(String publicaccount, int errType, String statusCode) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onFollowPublicAccount(
                            publicaccount, errType, statusCode);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onUnFollowPublicAccount(java.lang.String, int, java.lang.String)
         */
        public void onUnFollowPublicAccount(String publicaccount, int errType, String statusCode) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onUnFollowPublicAccount(
                            publicaccount, errType, statusCode);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onGetInfo(java.lang.String, int, java.lang.String)
         */
        public void onGetInfo(String publicaccount, int errType, String statusCode) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onGetInfo(publicaccount, errType,
                            statusCode);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onSearch(int, java.lang.String)
         */
        public void onSearch(int errType, String statusCode) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onSearch(errType, statusCode);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onMenuConfigUpdated(java.lang.String, java.lang.String, int, java.lang.String)
         */
        public void onMenuConfigUpdated(String publicaccount, String configInfo, int errType,
                String statusCode) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onMenuConfigUpdated(publicaccount,
                            configInfo, errType, statusCode);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        /*
         * (non-Javadoc)
         * @see
         * com.cmcc.ccs.publicaccount.api.PublicAccountServiceImpl.IPublicAccountEventBroadcaster
         * #onReportPublicAccount(java.lang.String, int, java.lang.String)
         */
        public void onReportPublicAccount(String publicaccount, int errType, String statusCode) {
            final int N = mPublicAccountListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mPublicAccountListeners.getBroadcastItem(i).onReportPublicAccount(
                            publicaccount, errType, statusCode);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Can't notify listener.", e);
                    }
                }
            }
            mPublicAccountListeners.finishBroadcast();
        }

        public void addPublicAccountEventListener(IPublicAccountChatListener listener)
                throws ServerApiException {
            mPublicAccountListeners.register(listener);
        }

        public void removePublicAccountEventListener(IPublicAccountChatListener listener)
                throws ServerApiException {
            mPublicAccountListeners.unregister(listener);
        }
    }
}
