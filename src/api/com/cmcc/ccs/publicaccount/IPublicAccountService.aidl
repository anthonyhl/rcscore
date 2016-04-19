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
 * | Feb 12, 2015 | init it.                                                                 |
 *-------------------------------------------------------------------------------------------
 ******************************************************************************/
package com.cmcc.ccs.publicaccount;
import com.gsma.services.rcs.IRcsServiceRegistrationListener;
import com.cmcc.ccs.publicaccount.IPublicAccountChatListener;
/**
    this interface define the method to access public account server
*/
interface IPublicAccountService {

    boolean isServiceRegistered();

    void addEventListener(IRcsServiceRegistrationListener listener);

    void removeEventListener(IRcsServiceRegistrationListener listener);

    /**
    * set a listener
    */
    void addEventListener1(IPublicAccountChatListener listener);

    void removeEventListener1(IPublicAccountChatListener listener);

    //send msg
    String sendMessage(in String accountnumber, in String message);

    //delete msg
    boolean deleteMessage(in long msgId);

    boolean sendMessage1(in String accountnumber,in int menuID);

    boolean setMessageRead(long msgId);

    void getPublicAccountInfo(String accountnumber);

    void followPublicAccount(String accountnumber);

    void searchPublicAccount(String keyword, int pageno, int order, int pagesize);

    void unfollowPublicAccount(String accountnumber);

    boolean getPublicAccountStatus(String accountnumber);

    void reportPublicAccount(String accountnumber, String content);

    void updateMenuConfig(String accountnumber);
}