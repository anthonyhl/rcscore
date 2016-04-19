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

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import com.orangelabs.rcs.utils.logger.Logger;

/**
 * tct-stack
 * 
 * Vemoticon parser
 * 
 */
public class VemoticonParser extends DefaultHandler {
//  Resource-List SAMPLE:
//  <?xml version="1.0" encoding="UTF-8"?>
//      <vemoticon xmlns="http://vemoticon.bj.ims.mnc000.mcc460.3gppnetwork.org/types">
//      <sms>smile</sms> //转换短信或IM文本消息显示的内容
//      <eid>E55A257E5B93CE76AC0F3DE43A3C284D@emotversion1_0.emoji</eid>
//  </vemoticon>

    private StringBuffer accumulator;
    private VemoticonDocument vemoticon = null;

    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
     * @param inputSource Input source
     * @throws Exception
     */
    public VemoticonParser(InputSource inputSource) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(inputSource, this);
    }

    public VemoticonDocument getVemoticon() {
        return vemoticon;
    }

    public void startDocument() {
        if (logger.isActivated()) {
            logger.debug("Start document");
        }
        accumulator = new StringBuffer();
    }

    public void characters(char buffer[], int start, int length) {
        accumulator.append(buffer, start, length);
    }

    public void startElement(String namespaceURL, String localName, String qname, Attributes attr) {
        accumulator.setLength(0);

        if (localName.equals(VemoticonDocument.VEMOTICON_TAG)) {
            vemoticon = new VemoticonDocument();
        }
    }

    public void endElement(String namespaceURL, String localName, String qname) {
        if (localName.equals(VemoticonDocument.VEMOTICON_TAG)) {
            if (logger.isActivated()) {
                logger.debug("Document complete");
            }
        } else if (localName.equals(VemoticonDocument.SMS_TAG)) {
            vemoticon.setSmsText(accumulator.toString());
        } else if (localName.equals(VemoticonDocument.EID_TAG)) {
            vemoticon.setEid(accumulator.toString());
        }
    }

    public void endDocument() {
        if (logger.isActivated()) {
            logger.debug("End document");
        }
    }
}
