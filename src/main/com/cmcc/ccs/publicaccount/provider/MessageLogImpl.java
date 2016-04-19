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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.cmcc.ccs.publicaccount.model.AccountModel;
import com.orangelabs.rcs.provider.LocalContentResolver;
import com.orangelabs.rcs.provider.messaging.MessageLog;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * @author leizhang
 *
 */
public class MessageLogImpl implements IMessageLog {
    private final boolean DEBUG = false;
    private static final Logger logger = Logger.getLogger(MessageLog.class.getSimpleName());
    private LocalContentResolver mLocalContentResolver;
    private static IMessageLog messageLog = null;

    public static IMessageLog newInstance(Context context){
        if(null != messageLog){
            return messageLog;
        }

        return messageLog = new MessageLogImpl(new LocalContentResolver(context.getContentResolver()));
    }
    /**
     *
     */
    private MessageLogImpl(LocalContentResolver localContentResolver) {
        this.mLocalContentResolver = localContentResolver;
    }
    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#insert(com.cmcc.ccs.publicaccount.model.AccountModel)
     */
    @Override
    public void insert(AccountModel accountModel,Uri uri) {
        LogI("insert entry{"+accountModel.toString()+"}");

        ContentValues values = new ContentValues();
        values.put(MessageProvider.Columns._ID,accountModel.getid());
        values.put(MessageProvider.Columns.ACCOUNT,accountModel.getAccount());
        values.put(MessageProvider.Columns.BRIEF_INTRODUCTION,accountModel.getBrief_introduction());
        values.put(MessageProvider.Columns.CONFIG,accountModel.getConfig());
        values.put(MessageProvider.Columns.ISFOLLOW,accountModel.getIsfollow());
        values.put(MessageProvider.Columns.NAME,accountModel.getName());
        values.put(MessageProvider.Columns.PORTRAIT,accountModel.getPortrait());
        values.put(MessageProvider.Columns.STATE,accountModel.getState());

        mLocalContentResolver.insert(MessageProvider.CONTENT_URI_SEARCH, values);
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#insert(java.util.Set)
     */
    @Override
    public void insert(Set<AccountModel> accountModels,Uri uri) {
        LogI("insert entry{"+accountModels.toString()+"}");
        if(null == accountModels || accountModels.isEmpty()){
            return;
        }

        for (AccountModel accountModel : accountModels) {
            insert(accountModel,uri);
        }
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#delete(java.lang.String)
     */
    @Override
    public void delete(String id) {
        LogI("delete entry {id=["+id+"]}");

        if(TextUtils.isEmpty(id)){
            return;
        }

        mLocalContentResolver.delete(MessageProvider.CONTENT_URI, MessageProvider.Columns.ACCOUNT+"=?", new String[]{id});
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#searchPublicAccount(java.lang.String, int, int, int)
     */
    @Override
    public Cursor searchPublicAccount(String keyword, int pageno, String order, int pagesize) {
        LogI("searchPublicAccount entry{keyword=["+keyword+"] pageno=["+pageno+"] order=["+order+"] pagesize=["+pagesize+"]}");

        if(TextUtils.isEmpty(keyword) || pageno < 0 || pageno < 1){
            return null;
        }

        String selection = new StringBuilder()
                .append("account like '%").append(keyword).append("%'")
                .append("").toString();

        String sortOrder = TextUtils.isEmpty(order) ? "" :order;
        String limit = new StringBuilder()
                .append(" limit ")
                .append(pagesize)
                .append(" offset ")
                .append((pageno - 1) * pagesize).toString();
        return  mLocalContentResolver.query(MessageProvider.CONTENT_URI, null, selection, null, sortOrder+limit);
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#unfollowPublicAccount(java.lang.String)
     */
    @Override
    public void unfollowPublicAccount(String accountnumber) {
        LogI("unfollowPublicAccount entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MessageProvider.Columns.ISFOLLOW, 0);
        mLocalContentResolver.update(MessageProvider.CONTENT_URI, values,MessageProvider.Columns.ACCOUNT+"=?", new String[]{accountnumber});
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#followPublicAccount(java.lang.String)
     */
    @Override
    public void followPublicAccount(String accountnumber) {
        LogI("followPublicAccount entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MessageProvider.Columns.ISFOLLOW, 1);
        mLocalContentResolver.update(MessageProvider.CONTENT_URI, values,MessageProvider.Columns.ACCOUNT+"=?", new String[]{accountnumber});
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#getPublicAccountStatus(java.lang.String)
     */
    @Override
    public boolean getPublicAccountStatus(String accountnumber) {
        LogI("followPublicAccount entry{accountnumber=["+accountnumber+"]}");

        if(TextUtils.isEmpty(accountnumber)){
            throw new IllegalArgumentException("params is empty");
        }

        Cursor cursor = mLocalContentResolver.query(MessageProvider.CONTENT_URI, null,MessageProvider.Columns.ACCOUNT+"=?", new String[]{accountnumber}, null);
        if(null == cursor || cursor.isClosed()){
            return false;
        }

        if(cursor.moveToFirst()){
            int isFollow = cursor.getInt(cursor.getColumnIndex(MessageProvider.Columns.ISFOLLOW));
            return isFollow == 1 ? true : false;
        }

        return false;
    }

    private void LogI(String msg){
        if(DEBUG){
           if(logger.isActivated())
               logger.debug(msg);
        }
    }
    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#saveInfo(java.lang.String, java.lang.String)
     */
    @Override
    public boolean saveInfo(String pa_uuid, String body) {
        LogI("saveInfo entry{accountnumber=["+pa_uuid+"] body=["+body+"]}");

        if(TextUtils.isEmpty(pa_uuid)){
            return false;
        }

        Map<String, String> parseStr = parse(body);
        ContentValues values = new ContentValues();
        values.put(MessageProvider.Columns.BRIEF_INTRODUCTION, parseStr.get("intro"));
        values.put(MessageProvider.Columns.NAME, parseStr.get("name"));
        values.put(MessageProvider.Columns.PORTRAIT, parseStr.get("logo"));
        values.put(MessageProvider.Columns.STATE, parseStr.get("activestatus"));
        values.put(MessageProvider.Columns.IDTYPE, parseStr.get("idtype"));
        values.put(MessageProvider.Columns.RECOMMEND_LEVEL, parseStr.get("recommendlevel"));
        values.put(MessageProvider.Columns.TYPE, parseStr.get("type"));
        values.put(MessageProvider.Columns.UPDATETIME, parseStr.get("updatetime"));
        values.put(MessageProvider.Columns.MENUTYPE, parseStr.get("menutype"));
        values.put(MessageProvider.Columns.MENUTIMESTAMP, parseStr.get("menutimestamp"));
        values.put(MessageProvider.Columns.ACCEPTSTATUS, parseStr.get("acceptstatus"));
        values.put(MessageProvider.Columns.ISFOLLOW, parseStr.get("subscribestatus"));
        values.put(MessageProvider.Columns.TEL, parseStr.get("tel"));
        values.put(MessageProvider.Columns.EMAIL, parseStr.get("email"));
        values.put(MessageProvider.Columns.ZIP, parseStr.get("zip"));
        values.put(MessageProvider.Columns.ADDR, parseStr.get("addr"));
        values.put(MessageProvider.Columns.FILED, parseStr.get("field"));
        values.put(MessageProvider.Columns.QRCODE, parseStr.get("qrcode"));
        //update table account and tmep account
        mLocalContentResolver.update(MessageProvider.CONTENT_URI, values,MessageProvider.Columns.ACCOUNT+"=?", new String[]{pa_uuid});
        mLocalContentResolver.update(MessageProvider.CONTENT_URI_SEARCH, values,MessageProvider.Columns.ACCOUNT+"=?", new String[]{pa_uuid});
        parseStr = null;
        values = null;
        return true;
    }
    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#saveMenu(java.lang.String, java.lang.String)
     */
    @Override
    public boolean saveMenu(String pa_uuid, String content) {
        LogI("followPublicAccount entry{accountnumber=["+pa_uuid+"] content=["+content+"]}");

        if(TextUtils.isEmpty(pa_uuid)){
            return false;
        }

        ContentValues values = new ContentValues();
        values.put(MessageProvider.Columns.CONFIG, 1);
        mLocalContentResolver.update(MessageProvider.CONTENT_URI, values,MessageProvider.Columns.ACCOUNT+"=?", new String[]{pa_uuid});
        return true;
    }

    public static Map<String, String> parse(String inStr) {
        Map<String, String> result = new HashMap<String, String>();
        XmlPullParserFactory xmlPullParseFacotry = null;
        XmlPullParser xmlParser = null;
        try {
            xmlPullParseFacotry = XmlPullParserFactory.newInstance();
            xmlParser = xmlPullParseFacotry.newPullParser();
            xmlParser.setInput(new ByteArrayInputStream(inStr.getBytes()), "utf-8");
            int eventType = xmlParser.getEventType();
            while (XmlPullParser.END_DOCUMENT != eventType) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:

                        break;
                    case XmlPullParser.START_TAG:
                        //pa_uuid
                        if ("pa_uuid".equals(xmlParser.getName())) {
                            result.put("pa_uuid", xmlParser.nextText());
                        }

                        //name
                        if ("name".equals(xmlParser.getName())) {
                            result.put("name", xmlParser.nextText());
                        }

                        //company
                        if ("company".equals(xmlParser.getName())) {
                            result.put("company", xmlParser.nextText());
                        }

                        //idtype
                        if ("idtype".equals(xmlParser.getName())) {
                            result.put("idtype", xmlParser.nextText());
                        }

                        //intro
                        if ("intro".equals(xmlParser.getName())) {
                            result.put("intro", xmlParser.nextText());
                        }

                        //type
                        if ("type".equals(xmlParser.getName())) {
                            result.put("type", xmlParser.nextText());
                        }

                        //recommendlevel
                        if ("recommendlevel".equals(xmlParser.getName())) {
                            result.put("recommendlevel", xmlParser.nextText());
                        }

                        //updatetime
                        if ("updatetime".equals(xmlParser.getName())) {
                            result.put("updatetime", xmlParser.nextText());
                        }

                        //menutype
                        if ("menutype".equals(xmlParser.getName())) {
                            result.put("menutype", xmlParser.nextText());
                        }

                        //menutimestamp
                        if ("menutimestamp".equals(xmlParser.getName())) {
                            result.put("menutimestamp", xmlParser.nextText());
                        }

                        //subscribestatus
                        if ("subscribestatus".equals(xmlParser.getName())) {
                            result.put("subscribestatus", xmlParser.nextText());
                        }

                        //acceptstatus
                        if ("acceptstatus".equals(xmlParser.getName())) {
                            result.put("acceptstatus", xmlParser.nextText());
                        }

                        //activestatus
                        if ("activestatus".equals(xmlParser.getName())) {
                            result.put("activestatus", xmlParser.nextText());
                        }

                        //tel
                        if ("tel".equals(xmlParser.getName())) {
                            result.put("tel", xmlParser.nextText());
                        }

                        //email
                        if ("email".equals(xmlParser.getName())) {
                            result.put("email", xmlParser.nextText());
                        }

                        //zip
                        if ("zip".equals(xmlParser.getName())) {
                            result.put("zip", xmlParser.nextText());
                        }

                        //addr
                        if ("addr".equals(xmlParser.getName())) {
                            result.put("addr", xmlParser.nextText());
                        }

                        //field
                        if ("field".equals(xmlParser.getName())) {
                            result.put("field", xmlParser.nextText());
                        }

                        //logo
                        if ("logo".equals(xmlParser.getName())) {
                            result.put("logo", xmlParser.nextText());
                        }

                        //qrcode
                        if ("qrcode".equals(xmlParser.getName())) {
                            result.put("qrcode", xmlParser.nextText());
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                }
                eventType = xmlParser.next();
            }
        } catch (XmlPullParserException e) {
            System.out.println("DetailInfo.parse()" + e.getMessage());
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }
    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.provider.IMessageLog#saveSearch(java.lang.String)
     */
    @Override
    public boolean saveSearch(String body) {
        if(TextUtils.isEmpty(body)){
            return false;
        }
        mLocalContentResolver.delete(MessageProvider.CONTENT_URI_SEARCH,null, null);

        insert(parsePublicList(body),MessageProvider.CONTENT_URI_SEARCH);
        return true;
    }

    private Set<AccountModel> parsePublicList(String inStr){
        Set<AccountModel> result = new HashSet<AccountModel>();
        XmlPullParserFactory xmlPullParseFacotry = null;
        XmlPullParser xmlParser = null;
        try {
            xmlPullParseFacotry = XmlPullParserFactory.newInstance();
            xmlParser = xmlPullParseFacotry.newPullParser();
            AccountModel tempModel = null;
            xmlParser.setInput(new ByteArrayInputStream(inStr.getBytes()), "utf-8");
            int eventType = xmlParser.getEventType();
            while (XmlPullParser.END_DOCUMENT != eventType) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:

                        break;
                    case XmlPullParser.START_TAG:
                        if("publicaccounts".equals(xmlParser.getName())){
                            tempModel = new AccountModel();
                        }

                        //pa_uuid
                        if ("pa_uuid".equals(xmlParser.getName())) {
                            tempModel.setAccount(xmlParser.nextText());
                            tempModel.set_id(tempModel.getAccount());
                        }

                        if ("name".equals(xmlParser.getName())) {
                            tempModel.setName(xmlParser.nextText());
                        }

                        if ("idtype".equals(xmlParser.getName())) {
                            try {
                                tempModel.setIdType(Integer.parseInt(xmlParser.nextText()));
                            } catch (NumberFormatException e) {
                                tempModel.setIdType(0);
                                e.printStackTrace();
                            }
                        }

                        if ("intro".equals(xmlParser.getName())) {
                            tempModel.setBrief_introduction(xmlParser.nextText());
                        }

                        if ("recommendlevel".equals(xmlParser.getName())) {
                            try {
                                tempModel.setRecommend_level(Integer.parseInt(xmlParser.nextText()));
                            } catch (NumberFormatException e) {
                                tempModel.setRecommend_level(1);
                                e.printStackTrace();
                            }
                        }

                        if ("logo".equals(xmlParser.getName())) {
                            tempModel.setPortrait(xmlParser.nextText());
                        }

                        if ("subscribestatus".equals(xmlParser.getName())) {
                            tempModel.setIsfollow(Integer.parseInt(xmlParser.nextText()));
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if("publicaccounts".equals(xmlParser.getName())){
                            result.add(tempModel);
                        }
                        break;
                }
                eventType = xmlParser.next();
            }
        } catch (XmlPullParserException e) {
            System.out.println("DetailInfo.parse()" + e.getMessage());
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return result;

    }
}
