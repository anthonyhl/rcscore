
package com.cmcc.ccs.publicaccount;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.IInterface;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceException;
import com.gsma.services.rcs.RcsServiceListener;
import com.gsma.services.rcs.RcsServiceNotAvailableException;

public class PublicAccountService extends RcsService {
    /**
     * uri of account info,always used to obtain the list
     */
    public static final Uri CONTENT_URI = Uri.parse("content://com.cmcc.ccs.public_account_info/list");
    /**
     * uri of search,always used to obtain the list when search
     */
    public static final Uri CONTENT_URI_SEARCH = Uri.parse("content://com.cmcc.ccs.public_account_search/list");
    /**
     * uri of public account
     */
    public static final Uri CONTENT_URI_ACCOUNT = Uri.parse("content://com.cmcc.ccs.public_account");
    /**
     * uri used to get the account detail,normal state
     */
    public static final Uri CONTENT_URI_ACCOUNT_DETAIL = Uri.parse("content://com.cmcc.ccs.public_account_info/detail");
    /**
     * uri used to get the account detail,search state
     */
    public static final Uri CONTENT_URI_SEARCH_DETAIL = Uri.parse("content://com.cmcc.ccs.public_account_search/detail");

    private IPublicAccountService api = null;
    public static final class Error {
        public static final int TIMEOUT = 1;
        public static final int UNKNOWN = 2;
        public static final int INTERNAL = 3;
        public static final int OUTOFSIZE = 4;
    }
    public static final class Columns {
        public static final String CHAT_ID = "chat_id";
        public static final String ACCOUNT = "account";
        public static final String NAME = "name";
        public static final String PORTRAIT = "portrait";
        public static final String STATE = "state";
        public static final String BRIEF_INTRODUCTION = "brief_introduction";
        public static final String CONFIG = "config";
    }

    /**
     * Service connection
     */
    private ServiceConnection apiConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            setApi(IPublicAccountService.Stub.asInterface(service));
            if (serviceListener != null) {
                serviceListener.onServiceConnected();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            setApi(null);
            if (serviceListener != null) {
                serviceListener.onServiceDisconnected(com.gsma.services.rcs.RcsService.Error.CONNECTION_LOST);
            }
        }
    };

    public PublicAccountService(Context ctx, RcsServiceListener listener) {
        super(ctx, listener);
        // TODO Auto-generated constructor stub
    }
    @Override
    public void connect() {
        bindService(IPublicAccountService.class, apiConnection);
    }

    @Override
    public void disconnect() {
        try {
            ctx.unbindService(apiConnection);
        } catch(IllegalArgumentException e) {
            // Nothing to do
        }
    }

    /* (non-Javadoc)
     * @see com.gsma.services.rcs.RcsService#setApi(android.os.IInterface)
     */
    @Override
    protected void setApi(IInterface api) {
        // TODO Auto-generated method stub
        super.setApi(api);

        this.api = (IPublicAccountService)api;
    }
    /**
     * Registers a chat invitation listener
     *
     * @param listener New chat listener
     * @throws RcsServiceException
     */
    public void addEventListener(PublicAccountChatListener listener) throws RcsServiceException {
        if (api != null) {
            try {
                api.addEventListener1(listener);
            } catch (Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    /**
     * Unregisters a chat invitation listener
     *
     * @param listener New chat listener
     * @throws RcsServiceException
     */
    public void removeEventListener(PublicAccountChatListener listener) throws RcsServiceException {
        if (api != null) {
            try {
                api.removeEventListener1(listener);
            } catch (Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public boolean deleteMessage(long msgId) throws RcsServiceException {
        if (api != null) {
            try {
               return api.deleteMessage(msgId);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public boolean sendMessage(String accountnumber, int menuID) throws RcsServiceException {
        if (api != null) {
            try {
               return api.sendMessage1(accountnumber, menuID);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public boolean setMessageRead(long msgId) throws RcsServiceException {
        if (api != null) {
            try {
               return api.setMessageRead(msgId);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public void getPublicAccountInfo(String accountnumber) throws RcsServiceException {
        if (api != null) {
            try {
                api.getPublicAccountInfo(accountnumber);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public void followPublicAccount(String accountnumber) throws RcsServiceException {
        if (api != null) {
            try {
                api.unfollowPublicAccount(accountnumber);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public void searchPublicAccount(String keyword, int pageno, int order, int pagesize) throws RcsServiceException {
        if (api != null) {
            try {
                api.searchPublicAccount(keyword, pageno, order, pagesize);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public void unfollowPublicAccount(String accountnumber) throws RcsServiceException {
        if (api != null) {
            try {
                api.unfollowPublicAccount(accountnumber);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public boolean getPublicAccountStatus(String accountnumber) throws RcsServiceException {
        if (api != null) {
            try {
                return api.getPublicAccountStatus(accountnumber);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public void reportPublicAccount(String accountnumber, String content) throws RcsServiceException {
        if (api != null) {
            try {
                api.reportPublicAccount(accountnumber, content);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    public void updateMenuConfig(String accountnumber) throws RcsServiceException {
        if (api != null) {
            try {
                api.updateMenuConfig(accountnumber);
            } catch(Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }
}
