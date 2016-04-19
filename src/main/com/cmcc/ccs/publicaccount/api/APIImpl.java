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

package com.cmcc.ccs.publicaccount.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.pixmob.httpclient.HttpClient;
import org.pixmob.httpclient.HttpRequest;
import org.pixmob.httpclient.HttpRequestHandler;
import org.pixmob.httpclient.HttpResponse;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.TextUtils;

import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.SimCard;
import com.orangelabs.rcs.utils.logger.Logger;
/**
 * The implemention of IPublicAccountApi
 * TODO
 * <UL>
 *  <LI>test the method {@linkplain APIImpl#setAcceptState(String, int)}</LI>
 *  </UL>
 * @author leizhang
 *
 */
public class APIImpl implements IPublicAccountApi, HttpRequestHandler {
    private static String ACCESSADDRESS = null;
    private boolean DEBUG = false;//the debug flag
    private Logger logger = Logger.getLogger(APIImpl.class.getSimpleName());
    private static Context sContext = null;//the application context
    private static APIImpl sSingleInstance = null;//single instance

    /*make this fileds as global for single connection*/
    private HttpClient mHttpClient = null;

    /*the http body part*/
    private static String sHttpBodyPart = "";

    /*the http body part but with out general info*/
    private static String sHttpBodyPartWithoutInfo = "";

    /*the content type*/
    private final String CONTENTTYPE = "text/xml";

    /*the userid,TODO*/
    private static String sUserID = "";

    /*the callback for async access network*/
    private Handler mHandler = null;

    /*indicates the session is inited*/
    private boolean isInited = false;

    static{
        ACCESSADDRESS = RcsSettings.getInstance().getPublicAccessAddress();
    }
    /**
     * the construct method
     * @param context application context
     * @param handler the callback handler
     */
    private APIImpl(Context context,Handler handler) {
        sContext = context;
        mHttpClient = new HttpClient(sContext);
        initUserID();
        sHttpBodyPart = initHttpBodyPart();
        sHttpBodyPartWithoutInfo = initHttpBodyPartWithoutInfo();
        this.mHandler = handler;
    }

    /**
     * single instance
     * @param context
     * @param handler
     * @return
     */
    public static APIImpl newInstance(Context context,Handler handler){
        if(null != sSingleInstance){
            return sSingleInstance ;
        }

        return sSingleInstance = new APIImpl(context,handler);
    }

    /**
     * create httpHead params,always called when init or reconnect
     * @param request
     * @return
     * @throws Exception
     */
    private HttpRequest createHttpHead(HttpRequest request) throws Exception{
        try {
            SimCard simCard = SimCard.load(sContext);
            String phoneNumber = simCard.getNumber();
            if(TextUtils.isEmpty(phoneNumber)){
                LogI("get phonenumber is null");
                return null;
            }
            //String publicUri = "sip:" + "+8613880103644" + "@" + "bj.ims.mnc460.mcc000.3gppnetwork.org";
            //auth
            HttpDigestMd5Authentication digestMd5Authentication = new HttpDigestMd5Authentication();
            digestMd5Authentication.setNextnonce("");
            digestMd5Authentication.updateNonceParameters();
            digestMd5Authentication.setRealm(simCard.getHomeDomain());
            String authStr = digestMd5Authentication.genDigestAuth("POST", ACCESSADDRESS, "",SettingData.getPublicUrl(phoneNumber), "123456");

            //ident TODO
            request.header(Params.X_3GPP_INTENDED_IDENTITY, "tel:"+phoneNumber)
                   .header(Params.AUTHORIZATION, authStr);
            //request.content((content + publicUri + content_tail).getBytes(), "text/xml");
        } catch (Exception e) {
            throw e;
        }
        return request;
    }

    private void LogI(String msg){
        if(DEBUG)
            if(logger.isActivated()){
                logger.info(msg);
            }
    }

    /* (non-Javadoc)
     * @see org.pixmob.httpclient.HttpRequestHandler#onRequest(java.net.HttpURLConnection)
     */
    @Override
    public void onRequest(HttpURLConnection conn) throws Exception {

    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#subscribe(java.lang.String)
     */
    @Override
    public String subscribe(String pa_uuid) {
        LogI("subscribe entry{pa_uuid=["+pa_uuid+"] }");

        if(TextUtils.isEmpty(pa_uuid)){
            LogI("returned,params check failed");
            return null;
        }

        String method = APIDescription.METHOD_ADDSUBSCRIBE;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);

        return result;
    }

    private String excute(String method, String bodyStr, String result) {
        if(!isInited){
            LogI("not inited...");
            Authorization();
        }

        Message message = mHandler.obtainMessage();
        ResultWrap resultWrap = new ResultWrap();
        resultWrap.lastXml = result;
        resultWrap.method_name = method;
        HttpResponse response = null;
        try {
            HttpRequest httpRequest;
            httpRequest =mHttpClient.post(ACCESSADDRESS);
            httpRequest.header("Accept-Encoding", "gzip");
            httpRequest.content(bodyStr.getBytes(), CONTENTTYPE);
            response = httpRequest.execute();
            //resultXml
            result = response.getResponseBody();
            resultWrap.lastXml = result;

            //result
            String[] processedResult = parse(result);
            resultWrap.code = Integer.parseInt(processedResult[0]);
            resultWrap.desc = processedResult[1];
            Map<String, String> data = new HashMap<String, String>();
            data.put("pa_uuid", processedResult[2]);
            resultWrap.otherParam = data;
            data = null;
            processedResult = null;

            LogI(result);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            resultWrap.code = ResultCode._100000;
            resultWrap.desc = "未知错误";
            resultWrap.lastXml = e.getMessage();
        }
        message.obj = resultWrap;
        mHandler.sendMessage(message);
        return result;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#cancelSubscribe(java.lang.String)
     */
    @Override
    public String cancelSubscribe(String pa_uuid) {
        LogI("cancelSubscribe entry{pa_uuid=["+pa_uuid+"] }");

        if(TextUtils.isEmpty(pa_uuid)){
            LogI("returned,params check failed");
            return null;
        }

        String method = IPublicAccountApi.APIDescription.METHOD_CANCELSUBSCRIBE;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);
        return result;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#getPublicList(java.lang.String, int, int, int)
     */
    @Override
    public Set<Parcelable> getPublicList(String keyword, int order, int pageSize, int pageNum) {
        LogI("getPublicList entry{keyword=["+keyword+"] order=["+order+"] pageSize=["+pageSize+"] pageNum=["+pageNum+"]}");

        if(TextUtils.isEmpty(keyword)){
            LogI("returned,params check failed");
            return null;
        }

        String method = IPublicAccountApi.APIDescription.METHOD_GETPUBLICLIST;
        StringBuilder builder = new StringBuilder();
        builder.append("<keyword>"+keyword+"</keyword>");
        builder.append("<order>"+order+"</order>");
        builder.append("<pagesize>"+pageSize+"</pagesize>");
        builder.append("<pagenum>"+pageNum+"</pagenum>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;

        String result = null;
        result = excute(method, bodyStr, result);
        return null;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#getPublicDetail(java.lang.String, java.lang.String)
     */
    @Override
    public Parcelable getPublicDetail(String pa_uuid, String updateTime) {
        LogI("getPublicDetail entry{pa_uuid=["+pa_uuid+"] updateTime=["+updateTime+"] }");

        String method = IPublicAccountApi.APIDescription.METHOD_GETPUBLICDETAIL;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");
        builder.append("<updatetime>"+updateTime+"</updatetime>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);
        return null;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#queryUserSub(int, int, int)
     */
    @Override
    public Set<Parcelable> queryUserSub(int order, int pageSize, int pageNum) {
        LogI("queryUserSub entry{order=["+order+"] pageSize=["+pageSize+"] pageNum=["+pageNum+"]}");

        String method = APIDescription.METHOD_QUERYUSERSUB;
        StringBuilder builder = new StringBuilder();
        builder.append("<order>"+order+"</order>");
        builder.append("<pagesize>"+pageSize+"</pagesize>");
        builder.append("<pagenum>"+pageNum+"</pagenum>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);

        return null;

    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#getPublicMenu(java.lang.String, java.lang.String)
     */
    @Override
    public Parcelable getPublicMenu(String pa_uuid, String menutimeStamp) {
        LogI("getPublicMenu entry{pa_uuid=["+pa_uuid+"] menutimeStamp=["+menutimeStamp+"] }");

        String method = IPublicAccountApi.APIDescription.METHOD_GETPUBLICMENU;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");
        builder.append("<menutimestamp>"+menutimeStamp+"</menutimestamp>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);

        return null;

    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#getPreMessage(java.lang.String, java.lang.String, int, int, int)
     */
    @Override
    public Parcelable getPreMessage(String pa_uuid, String timeStamp, int order, int pageSize,
            int pageNum) {
        LogI("getPreMessage entry{pa_uuid=["+pa_uuid+"] timeStamp=["+timeStamp+"] order=["+order+"] pageSize=["+pageSize+"]}");

        String method = IPublicAccountApi.APIDescription.METHOD_GETPREMESSAGE;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");
//        builder.append("<timestamp>"+"2014-09-26T11:21:00+8:00"+"</timestamp>");
        builder.append("<timestamp>"+timeStamp+"</timestamp>");
        builder.append("<order>"+order+"</order>");
        builder.append("<pagesize>"+pageSize+"</pagesize>");
        builder.append("<pagenum>"+pageNum+"</pagenum>");
        //<number>20</number>
        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);
        return null;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#complainPublic(java.lang.String, int, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public String complainPublic(String pa_uuid, int type, String reason, String data,
            String description) {
        LogI("complainPublic entry{pa_uuid=["+pa_uuid+"] type=["+type+"] data=["+data+"] reason=["+reason+"] description=["+description+"]}");

        String method = IPublicAccountApi.APIDescription.METHOD_COMPLAINPUBLIC;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");
        builder.append("<type>"+type+"</type>");
        builder.append("<reason>"+reason+"</reason>");
        builder.append("<data>"+data+"</data>");
        //<number>20</number>
        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;
        String result = null;
        result = excute(method, bodyStr, result);
        return result;
    }

    /* (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#getPublicRecommend(int, int, int)
     */
    @Override
    public Set<Parcelable> getPublicRecommend(int type, int pageSize, int pageNum) {
        LogI("getPublicRecommend entry{type=["+type+"] pageSize=["+pageSize+"] pageNum=["+pageNum+"] }");

        String method = APIDescription.METHOD_GETPUBLICRECOMMEND;
        StringBuilder builder = new StringBuilder();
        builder.append("<type>"+type+"</type>");
        builder.append("<pagesize>"+pageSize+"</pagesize>");
        builder.append("<pagenum>"+pageNum+"</pagenum>");

        String bodyStr = createHttpBodyString(method, builder.toString());
        builder = null;

        String result = null;
        result = excute(method, bodyStr, result);
        return null;
    }

    /*
     *
     * test not ok
     * FIXME
     * (non-Javadoc)
     * @see com.cmcc.ccs.publicaccount.api.IPublicAccountApi#setAcceptState(java.lang.String, int)
     */
    @Override
    public Parcelable setAcceptState(String pa_uuid, int acceptStatus) {
        return setAcceptStateV2(pa_uuid, acceptStatus);
    }

    @SuppressWarnings("unused")
    private Parcelable setAcceptStateV1(String pa_uuid, int acceptStatus) {
        LogI("setAcceptState entry{pa_uuid=["+pa_uuid+"] acceptStatus=["+acceptStatus+"]}");

        String method = "setacceptstatus";
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");
        builder.append("<acceptStatus>"+acceptStatus+"</acceptStatus>");

        String bodyStr = createHttpBodyStringWithoutInfo(builder.toString());
        builder = null;

        HttpRequest httpRequest;
        try {
            httpRequest =mHttpClient.post(ACCESSADDRESS);
            httpRequest.header("Accept-Encoding", "gzip");
            httpRequest.header("msgname", method);
            httpRequest.header("version", "1.0.0");
            httpRequest.header("userid", sUserID);
            httpRequest.content(bodyStr.getBytes(), CONTENTTYPE);
            HttpResponse response = httpRequest.execute();
            String result = response.getResponseBody();
            System.out.println(result);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private Parcelable setAcceptStateV2(String pa_uuid, int acceptStatus) {
        LogI("setAcceptState entry{pa_uuid=["+pa_uuid+"] acceptStatus=["+acceptStatus+"]}");

        String method = APIDescription.METHOD_SETACCEPTSTATUS;
        StringBuilder builder = new StringBuilder();
        builder.append("<pa_uuid>"+pa_uuid+"</pa_uuid>");
        builder.append("<acceptStatus>"+acceptStatus+"</acceptStatus>");

        String bodyStr = createHttpBodyString(method,builder.toString());
        builder = null;

        String result = null;
        result = excute(method, bodyStr, result);
        return null;
    }

    private static void initUserID(){
        SimCard simCard = SimCard.load(sContext);
        String publicUri = "sip:" + "+8613880103644" + "@" + "bj.ims.mnc460.mcc000.3gppnetwork.org";
        sUserID = publicUri;
    }


    private static String initHttpBodyPart(){
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        builder.append("<body>");
        builder.append("<generalinfo>");
        builder.append("<msgname>%s</msgname>");
        builder.append("<version>1.0.0</version>");
        builder.append("<userid>"+sUserID+"</userid>");
        builder.append("</generalinfo>");
        builder.append("%s");
        builder.append("</body>");
        return builder.toString();
    }

    /**
     * initHttpBodyPartWithoutInfo
     * @return
     */
    private static String initHttpBodyPartWithoutInfo(){
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        builder.append("<body>");
        builder.append("%s");
        builder.append("</body>");
        return builder.toString();
    }
    /**
     * create http request content string
     * @param method
     * @param content
     * @return
     */
    private String createHttpBodyString(String method,String content){
        return String.format(sHttpBodyPart, method,content);
    }
    /**
     * create http request content string
     * @param method
     * @param content
     * @return
     */
    private String createHttpBodyStringWithoutInfo(String content){
        return String.format(sHttpBodyPartWithoutInfo,content);
    }

    @Override
    public void Authorization(){
        try {
            HttpRequest httpRequest = createHttpHead(mHttpClient.post(ACCESSADDRESS));
            if(null == httpRequest ){
                LogI("create httphead null.");
                throw new NullPointerException("create httphead null.");
            }

            HttpResponse httpResponse = httpRequest.execute();
            System.out.println("status code["+httpResponse.getStatusCode()+"]");
            String result = httpResponse.getResponseBody();
            System.out.println("Authorization result["+result+"]");
            isInited = true;
        } catch (Exception e) {
            e.printStackTrace();
            isInited = false;
        }
    }

    /**
     * parse the response string
     * @param inStr
     * @return
     */
    public static String[] parse(String inStr) {
        String[] result = new String[3];
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
                        if ("result".equals(xmlParser.getName())) {
                            result[0] =xmlParser.nextText();
                        }

                        if ("resultdesc".equals(xmlParser.getName())) {
                            result[1] =xmlParser.nextText();
                        }

                        if("pa_uuid".equals(xmlParser.getName())){
                            result[2] = xmlParser.nextText();
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
}
