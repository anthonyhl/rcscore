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

/**
 * IPublicAccountChatListener
 */
interface IPublicAccountChatListener {

   void onNewPublicAccoutChat(String accountnumber, String msgId);

   void onFollowPublicAccount(String publicaccount,int errType , String statusCode);

   void onUnFollowPublicAccount(String publicaccount , int errType ,String statusCode);

   void onGetInfo(String publicaccount,int errType,String statusCode);

   void onSearch(int errType,String statusCode);

   void onMenuConfigUpdated(String publicaccount,String configInfo,int errType,String statusCode );

   void onReportPublicAccount(String publicaccount, int errType,String statusCode);
}