/*******************************************************************************
 * Software Name: RCS
 *
 * Copyright (C) 2015 TCL Telecommunication Technology Ltd.
 *
 * Description: TODO
 *
 * Mar 4, 2015
 *
 *Author: lei.zhang9282@icloud.com
 *
 * Modification record:
 *-------------------------------------------------------------------------------------------
 * | Mar 4, 2015 | init it.                                                                     |
 *-------------------------------------------------------------------------------------------
 ******************************************************************************/

package com.cmcc.ccs.publicaccount.api;

/**
 * @author leizhang
 *
 */
public class SettingData {
    /** the ims domain,see <b>S02-8.1</b>
     */
    public static final String IMS_DOMAIN = "bj.ims.mnc460.mcc000.3gppnetwork.org";

    /**
     * get public url with phone number
     * @param phoneNumber the phone number with national code
     * @return
     */
    public static String getPublicUrl(String phoneNumber){
        return "sip:" + phoneNumber + "@" + IMS_DOMAIN;
    }

}
