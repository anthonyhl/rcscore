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

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.gsma.services.rcs.chat.ChatLog;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.im.chat.imdn.ImdnUtils;
import com.orangelabs.rcs.utils.Base64;
import com.orangelabs.rcs.utils.DateUtils;
import com.orangelabs.rcs.utils.StringIgnoreCase;

/**
 * CPIM message
 * 
 * @author jexa7410
 */
public class CpimMessage {
	/**
	 * MIME type
	 */
	public static final String MIME_TYPE = "message/cpim";
	
	/**
	 * Header "Content-type"
	 */
	public static final String HEADER_CONTENT_TYPE = "Content-type";
	public static final String HEADER_CONTENT_TYPE2 = "Content-Type";

	/**
	 * Header "From"
	 */
	public static final String HEADER_FROM = "From";

	/**
	 * Header "To"
	 */
	public static final String HEADER_TO = "To";

	/**
	 * Header "cc"
	 */
	public static final String HEADER_CC = "cc";

	/**
	 * Header "DateTime"
	 */
	public static final String HEADER_DATETIME = "DateTime";

	/**
	 * Header "Subject"
	 */
	public static final String HEADER_SUBJECT = "Subject";

	/**
	 * Header "NS"
	 */
	public static final String HEADER_NS = "NS";

	/**
	 * Header "Content-length"
	 */
	public static final String HEADER_CONTENT_LENGTH = "Content-length";
	
	/**
	 * Header "Require"
	 */
	public static final String HEADER_REQUIRE = "Require";
	
	/**
	 * Header "Content-Disposition"
	 */
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	
	/**
	 * Message content
	 */
	private String msgContent = null;
	
	/**
	 * MIME headers
	 */
	private HashMap<String, String> headers = new HashMap<String, String>();
	
	/**
	 * MIME content headers
	 */
	private HashMap<String, String> contentHeaders = new HashMap<String, String>();



	/**
	 * CRLF constant
	 */
	static final String CRLF = "\r\n";

	private static final String COLON = ": ";
	
	/**
	 * Double CRLF constant
	 */
	static final String DOUBLE_CRLF = CRLF + CRLF;
	
	/**
	 * Constructor
	 * 
	 * @param headers MIME headers
	 * @param contentHeaders MIME content headers
	 * @param msgContent Content
	 */
	public CpimMessage(HashMap<String, String> headers, HashMap<String, String> contentHeaders, String msgContent) {
		this.headers = headers;
		this.contentHeaders = contentHeaders;
		this.msgContent = msgContent;
	}
	
    /**
     * Returns content type
     * 
     * @return Content type
     */
    public String getContentType() {
    	String type = contentHeaders.get(CpimMessage.HEADER_CONTENT_TYPE);
    	if (type == null) {
    		return contentHeaders.get(CpimMessage.HEADER_CONTENT_TYPE2);
    	} else {
    		return type;
    	}
    }
    
    /**
     * Returns MIME header
     * 
     * @param name Header name
     * @return Header value
     */
    public String getHeader(String name) {
		return headers.get(name);
	}
	
    /**
     * Returns MIME content header
     * 
     * @param name Header name
     * @return Header value
     */
    public String getContentHeader(String name) {
		return contentHeaders.get(name);
	}

    /**
     * Returns message content
     * 
     * @return Content
     */
    public String getMessageContent() {
		return msgContent;
	}

    /**
     * Returns message date
     * 
     * @return Date
     */
    public Date getMessageDate() {
    	String header = getHeader(CpimMessage.HEADER_DATETIME);
    	if (header != null) {
    		return new Date(DateUtils.decodeDate(header));
    	} else {
    		return null;
    	}
	}
    

    
    public static class Builder {
    	private static final String HEADER_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
		
    	/**
    	 * MIME headers
    	 */
    	private HashMap<String, String> headers = new HashMap<String, String>();
    	
    	/**
    	 * MIME content headers
    	 */
    	private HashMap<String, String> contentHeaders = new HashMap<String, String>();
    	byte[] msgContent = null;
    	
    	Builder() {
    		
    	}
    	
    	public Builder(String from, String to) {
    		header(HEADER_FROM, from);
    		header(HEADER_TO, to);
    		header(HEADER_DATETIME, DateUtils.currentTimeMillis());
    	}
    	
    	public Builder header(String name, String value) {
    		headers.put(name, value);
    		return this;
    	}
    	
    	public Builder contentHeader(String name, String value) {
    		contentHeaders.put(name, value);
    		return this;
    	}
    	
    	public Builder body(String rawMessage, String type, String encoding) {
    		if (type == null) {
    			type = "text/plain";
    		}
    		
//    		contentHeader(HEADER_CONTENT_TYPE, type + "; charset=utf-8");
    		// tct-stack add begin {
    		if (StringIgnoreCase.equals(ChatLog.Message.MimeType.TEXT_MESSAGE, type)) {
    		    contentHeader(HEADER_CONTENT_TYPE, type + "; charset=utf-8");
    		} else if (StringIgnoreCase.equals(ChatLog.Message.MimeType.GEOLOC_MESSAGE, type)) {
                contentHeader(HEADER_CONTENT_TYPE, type);
            } else if (StringIgnoreCase.equals(McloudDocument.MIME_TYPE, type)) {
    		    contentHeader(HEADER_CONTENT_TYPE, type);
    		    encoding = Base64.ENCODING_TYPE;
    		} else if (StringIgnoreCase.equals(VemoticonDocument.MIME_TYPE, type)) {
    		    contentHeader(HEADER_CONTENT_TYPE, type);
    		    encoding = Base64.ENCODING_TYPE;
    		}
    		// } end
    		
    		if (StringIgnoreCase.equals("text/plain", type)) {
    			encoding = Base64.ENCODING_TYPE;
    		}
    		
    		if (StringIgnoreCase.equals(Base64.ENCODING_TYPE, encoding)){
    			contentHeader(HEADER_CONTENT_TRANSFER_ENCODING, encoding);
    			msgContent = Base64.encodeBase64(rawMessage.getBytes(UTF8));       		
    		}
    		else {
    			msgContent = rawMessage.getBytes(UTF8);
    		}
    		contentHeader(HEADER_CONTENT_LENGTH, Long.toString(msgContent.length));    		
    		return this;
    	}
    	
    	private String contentHeader(String key) {
    		return contentHeaders.get(key);
    	}
    	
    	public Builder body(String rawMessage) {
    		msgContent = rawMessage.getBytes(UTF8);
    		return this;
    	}
    	
    	public Builder imdnReport(String message) {
    		contentHeader(HEADER_CONTENT_DISPOSITION, ImdnDocument.NOTIFICATION);
    		return body(message, ImdnDocument.MIME_TYPE	, null);
    	}
    	    	
    	public CpimMessage build() {
    		
    		byte[] content = msgContent;
    		if (StringIgnoreCase.equals(Base64.ENCODING_TYPE, 
    				contentHeader(HEADER_CONTENT_TRANSFER_ENCODING))) {
    			content = Base64.decodeBase64(msgContent);
    		}

    		return new CpimMessage(headers, contentHeaders, new String(content, UTF8));
    	}
    	
    	public Builder imdn(String messageId) {
    		headers.put(HEADER_NS, ImdnDocument.IMDN_NAMESPACE);
    		headers.put(ImdnUtils.HEADER_IMDN_MSG_ID, messageId);
    		
    		headers.put(ImdnUtils.HEADER_IMDN_DISPO_NOTIF, 
    				new StringBuilder(ImdnDocument.POSITIVE_DELIVERY)
    					.append(", ").append(ImdnDocument.DISPLAY).toString());
    		return this;
    	}
    		
    	public byte[] getBytes() {
    		Output out = getCommonPart();
    		return out.getBytes(msgContent);
    	}
    	
    	public String getMessage() {
    		Output out = getCommonPart();
    		return out.getMessage(msgContent);
    	}

		Output getCommonPart() {
			Output out = new Output();
    		for (Map.Entry<String,String> pair : headers.entrySet()) {
    			out.header(pair.getKey(), pair.getValue());
    		}
    		out.newline();
    		
    		for (Map.Entry<String,String> pair : contentHeaders.entrySet()) {
    			out.header(pair.getKey(), pair.getValue());
    		}
    		out.newline();
			return out;
		}
    }



	private static class Output {
		StringBuilder msg = new StringBuilder();
		
		Output header(String name, String value) {
			msg.append(name).append(COLON).append(value).append(CRLF);	
			return this;
		}
		
		private byte[] combine(byte[] content) throws IOException {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				bos.write(msg.toString().getBytes(UTF8));
				bos.write(content);
				return bos.toByteArray();
			}
			finally{
				bos.close();   			
			}
		}
		
		String getMessage(byte[] content) {
			return msg.append(new String(content, UTF8)).toString();
		}
		
		byte[] getBytes(byte[] content) {   			
			try {
				return combine(content);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		Output newline() {
			msg.append(CRLF);
			return this;
		}
	}
}
