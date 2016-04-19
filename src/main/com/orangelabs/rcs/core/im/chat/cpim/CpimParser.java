/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.orangelabs.rcs.core.im.chat.cpim;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import java.util.StringTokenizer;

/**
 * CPIM parser (see RFC3862)
 *
 * @author jexa7410
 */
public class CpimParser {
	/**
	 * CPIM message
	 */
	private CpimMessage cpim = null;

	/**
	 * Constructor
	 *
	 * @param data Input data
	 * @throws Exception
	 */
    public CpimParser(byte data[]) throws Exception {
        parse(new String(data, UTF8));
	}

	/**
	 * Constructor
	 *
	 * @param data Input data
	 * @throws Exception
	 */
    public CpimParser(String data) throws Exception {
        parse(data);
	}

    /***
     * Returns the CPIM message
     *
     * @return CPIM message
     */
    public CpimMessage getCpimMessage() {
    	return cpim;
    }

    /**
     * Parse message/CPIM document
     *
     * @param data Input data
     * @throws Exception
     */
	private void parse(String data) throws Exception {
		/* CPIM sample:
	    From: MR SANDERS <im:piglet@100akerwood.com>
	    To: Depressed Donkey <im:eeyore@100akerwood.com>
	    DateTime: 2000-12-13T13:40:00-08:00
	    Subject: the weather will be fine today

	    Content-type: text/plain
	    Content-ID: <1234567890@foo.com>

	    Here is the text of my message.
	    */
		try {
			// Read message headers
			CpimMessage.Builder builder = new CpimMessage.Builder();
			
			int begin = 0;
			int end = data.indexOf(CpimMessage.DOUBLE_CRLF, begin);
			String block2 = data.substring(begin, end);
			StringTokenizer lines = new StringTokenizer(block2, CpimMessage.CRLF);
			
			while(lines.hasMoreTokens()) {
				String token = lines.nextToken();
				
				CpimHeader hd = parseHeader(token);
				builder.header(hd.getName(), hd.getValue());
			}

			// Read the MIME-encapsulated content header
			begin = end+4;
			end = data.indexOf(CpimMessage.DOUBLE_CRLF, begin);
			String block3 = data.substring(begin, end);
			lines = new StringTokenizer(block3, CpimMessage.CRLF);

			while(lines.hasMoreTokens()) {
				String token = lines.nextToken();
				
				CpimHeader hd = parseHeader(token);
				builder.contentHeader(hd.getName(), hd.getValue());
			}

			// Read the message content
			begin = end+4;
			builder.body(data.substring(begin));

			// Create the CPIM message
			cpim = builder.build();
		} catch(Exception e) {
			throw new Exception("Bad CPIM message format");
		}
	}

	/**
	 * Parse CPIM header
	 * 
	 * @param data Input data
	 * @return Header
	 * @throws Exception
	 */
	public static CpimHeader parseHeader(String data) throws Exception {
		int index = data.indexOf(":");
		String key = data.substring(0, index);
		String value = data.substring(index+1);
		return new CpimHeader(key.trim(), value.trim());
	}
}
