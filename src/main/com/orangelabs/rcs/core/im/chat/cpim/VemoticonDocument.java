/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.orangelabs.rcs.core.im.chat.cpim;

/**
 * tct-stack
 * 
 * Vemoticon document
 * 
 */
public class VemoticonDocument {
    /**
     * MIME type
     */
    public static final String MIME_TYPE = "application/vemoticon+xml";

    public static final String VEMOTICON_TAG = "vemoticon";

    public static final String SMS_TAG = "sms";

    public static final String EID_TAG = "eid";

    /**
     * Sms text
     */
    private String smsText = null;

    /**
     * Eid
     */
    private String eid = null;

    /**
     * Constructor
     */
    public VemoticonDocument() {

    }

    /**
     * Constructor
     * 
     * @param sms
     * @param eid
     */
    public VemoticonDocument(String sms, String eid) {
        this.smsText = sms;
        this.eid = eid;
    }

    /**
     * @return the smsText
     */
    public String getSmsText() {
        return smsText;
    }

    /**
     * @param smsText the smsText to set
     */
    public void setSmsText(String smsText) {
        this.smsText = smsText;
    }

    /**
     * @return the eid
     */
    public String getEid() {
        return eid;
    }

    /**
     * @param eid the eid to set
     */
    public void setEid(String eid) {
        this.eid = eid;
    }

}
