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
 * Mcloud parser
 * 
 */
public class McloudParser extends DefaultHandler {
//  MCloud content sample:
//  <?xml version="1.0" encoding="UTF-8"?>
//      <cloudfile xmlns="http://cloudfile.cmcc.com/types">
//      <filename>日程表.xls</filename>
//      <filesize>36KB</filesize>
//      <downloadurl>http://abc.com</downloadurl>
//  </cloudfile>

    private StringBuffer accumulator;
    private McloudDocument mcloud = null;

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
    public McloudParser(InputSource inputSource) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(inputSource, this);
    }

    public McloudDocument getMcloud() {
        return mcloud;
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

        if (localName.equals(McloudDocument.MCLOUD_TAG)) {
            mcloud = new McloudDocument();
        }
    }

    public void endElement(String namespaceURL, String localName, String qname) {
        if (localName.equals(McloudDocument.MCLOUD_TAG)) {
            if (logger.isActivated()) {
                logger.debug("Document complete");
            }
        } else if (localName.equals(McloudDocument.FILENAME_TAG)) {
            mcloud.setFilename(accumulator.toString());
        } else if (localName.equals(McloudDocument.FILESIZE_TAG)) {
            mcloud.setFilesize(accumulator.toString());
        } else if (localName.equals(McloudDocument.DOWNLOADURL_TAG)) {
            mcloud.setDownloadurl(accumulator.toString());
        }
    }

    public void endDocument() {
        if (logger.isActivated()) {
            logger.debug("End document");
        }
    }
}
