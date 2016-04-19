
package com.cmcc.ccs.publicaccount;

import com.cmcc.ccs.publicaccount.IPublicAccountChatListener;

public abstract class PublicAccountChatListener extends IPublicAccountChatListener.Stub {

    public abstract void onNewPublicAccoutChat(String accountnumber, String msgId);

    public abstract void onFollowPublicAccount(String publicaccount, int errType, String statusCode);

    public abstract void onUnFollowPublicAccount(String publicaccount, int errType,
            String statusCode);

    public abstract void onGetInfo(String publicaccount, int errType, String statusCode);

    public abstract void onSearch(int errType, String statusCode);

    public abstract void onMenuConfigUpdated(String publicaccount,
            String configInfo, int errType, String statusCode);

    public abstract void onReportPublicAccount(String publicaccount, int errType, String statusCode);
}
