/*******************************************************************************
 * Software Name: RCS
 *
 * Copyright (C) 2015 TCL Telecommunication Technology Ltd.
 *
 * Description: TODO
 *
 * Feb 10, 2015
 *
 *Author: lei.zhang9282@icloud.com
 *
 * Modification record:
 *-------------------------------------------------------------------------------------------
 * | Feb 10, 2015 | init it.                                                                     |
 *-------------------------------------------------------------------------------------------
 ******************************************************************************/

package com.cmcc.ccs.publicaccount.provider;

import java.util.Set;

import android.database.Cursor;
import android.net.Uri;

import com.cmcc.ccs.publicaccount.model.AccountModel;

/**
 * @author leizhang
 *
 */
public interface IMessageLog {

    /**
     * insert a record
     * @param accountModel
     */
    public void insert(AccountModel accountModel,Uri uri);

    /**
     * insert more record
     * @param accountModels
     */
    public void insert(Set<AccountModel> accountModels,Uri uri);

    /**
     * delete a record
     * @param id
     */
    public void delete(String id);

    /**
     * search
     * @param keyword
     * @param pageno
     * @param orderCols
     * @param pagesize
     * @return
     */
    public Cursor searchPublicAccount(String keyword,int pageno,String orderCols,int pagesize);

    /**
     * unfollow
     * @param accountnumber
     */
    public void unfollowPublicAccount(String accountnumber);

    /**
     * follow
     * @param accountnumber
     */
    public void followPublicAccount(String accountnumber);

    /**
     * get status
     * @param accountnumber
     * @return
     */
    public boolean getPublicAccountStatus(String accountnumber);

    /**
     * save info
     * @param pa_uuid
     * @param body
     * @return
     */
    public boolean saveInfo(String pa_uuid,String body);

    /**
     * save menu
     * @param pa_uuid
     * @param content
     * @return
     */
    public boolean saveMenu(String pa_uuid,String content);

    public boolean saveSearch(String body);
}
