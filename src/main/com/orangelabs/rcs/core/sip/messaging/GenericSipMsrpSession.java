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

package com.orangelabs.rcs.core.sip.messaging;

import java.io.ByteArrayInputStream;
import java.util.Vector;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.sip.GenericSipSession;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.core.sip.SipSessionError;
import com.orangelabs.rcs.core.sip.SipSessionListener;
import com.orangelabs.rcs.protocol.msrp.MsrpEventListener;
import com.orangelabs.rcs.protocol.msrp.MsrpManager;
import com.orangelabs.rcs.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.protocol.msrp.MsrpSession.TypeMsrpChunk;
import com.orangelabs.rcs.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.protocol.sdp.SdpParser;
import com.orangelabs.rcs.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.NetworkRessourceManager;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Generic SIP MSRP session 
 * 
 * @author jexa7410
 */
public abstract class GenericSipMsrpSession extends GenericSipSession implements MsrpEventListener {
	/**
	 * MIME type
	 */
	public final static String MIME_TYPE = "text/plain"; 
	private SipService parent;
	
	/**
	 * MSRP manager
	 */
	private MsrpManager msrpMgr;
	
	/**
	 * Max message size
	 */
	private int maxMsgSize = RcsSettings.getInstance().getMaxMsrpLengthForExtensions();

	/**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(GenericSipMsrpSession.class.getSimpleName());

    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param contact Remote contact Id
	 * @param featureTag Feature tag
	 */
	public GenericSipMsrpSession(SipService parent, ContactId contact, String featureTag) {
		super(parent, contact, featureTag);

		this.parent = parent;
        // Create the MSRP manager
		int localMsrpPort = NetworkRessourceManager.generateLocalMsrpPort();
		String localIpAddress = getImsService().getImsModule().getIpAddress();
		msrpMgr = new MsrpManager(localIpAddress, localMsrpPort);
	}

	/**
	 * Returns the max message size
	 * 
	 * @return Max message size
	 */
	public int getMaxMessageSize() {
		return this.maxMsgSize;
	}
	
	/**
	 * Returns the MSRP manager
	 * 
	 * @return MSRP manager
	 */
	public MsrpManager getMsrpMgr() {
		return msrpMgr;
	}

    /**
     * Generate SDP
     * 
     * @param setup Setup mode
     */
    public String generateSdp(String setup) {
        int msrpPort;
        if ("active".equals(setup)) {
        	msrpPort = 9; // See RFC4145, Page 4
        } else {
        	msrpPort = getMsrpMgr().getLocalMsrpPort();
        }
    	
    	String ntpTime = SipUtils.constructNTPtime(System.currentTimeMillis());
    	String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
    	
    	return "v=0" + SipUtils.CRLF +
            "o=- " + ntpTime + " " + ntpTime + " " + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
            "s=-" + SipUtils.CRLF +
			"c=" + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
            "t=0 0" + SipUtils.CRLF +			
            "m=message " + msrpPort + " " + getMsrpMgr().getLocalSocketProtocol() + " *" + SipUtils.CRLF +
            "a=setup:" + setup + SipUtils.CRLF +
            "a=path:" + getMsrpMgr().getLocalMsrpPath() + SipUtils.CRLF +
            "a=max-size:" + getMaxMessageSize() + SipUtils.CRLF +
            "a=accept-types:" + GenericSipMsrpSession.MIME_TYPE + SipUtils.CRLF +
    		"a=sendrecv" + SipUtils.CRLF;
    }    
    
    /**
     * Prepare media session
     * 
     * @throws Exception 
     */
    public void prepareMediaSession() throws Exception {
        // Parse the remote SDP part
        SdpParser parser = new SdpParser(getDialogPath().getRemoteContent().getBytes(
                UTF8));
        Vector<MediaDescription> media = parser.getMediaDescriptions();
        MediaDescription mediaDesc = media.elementAt(0);
        MediaAttribute attr = mediaDesc.getMediaAttribute("path");
        String remoteMsrpPath = attr.getValue();
        String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaDesc);
        int remotePort = mediaDesc.port;

        // Create the MSRP session
        MsrpSession session = getMsrpMgr().createMsrpClientSession(remoteHost, remotePort, remoteMsrpPath, this, null);
        session.setFailureReportOption(true);
        session.setSuccessReportOption(false);
    }

    /**
     * Start media session
     * 
     * @throws Exception 
     */
    public void startMediaSession() throws Exception {
        // Open the MSRP session
        getMsrpMgr().openMsrpSession();
    }

    /**
     * Close media session
     */
    public void closeMediaSession() {
    	if (msrpMgr != null) {
    		msrpMgr.closeSession();
			if (logger.isActivated()) {
				logger.debug("MSRP session has been closed");
			}
    	}
    }
    
    /**
     * Sends a message in real time
     * 
     * @param content Message content
	 * @return Returns true if sent successfully else returns false
     */
    public boolean sendMessage(byte[] content) {
		try {
			ByteArrayInputStream stream = new ByteArrayInputStream(content); 
	    	String msgId = IdGenerator.getIdentifier().replace('_', '-');
			msrpMgr.sendChunks(stream, msgId, SipService.MIME_TYPE, content.length, TypeMsrpChunk.Unknown);
			return true;
		} catch(Exception e) {
			// Error
	   		if (logger.isActivated()) {
	   			logger.error("Problem while sending data chunks", e);
	   		}
			return false;
		}
    }	    
    
	/**
	 * Data has been transfered
	 * 
	 * @param msgId Message ID
	 */
	public void msrpDataTransfered(String msgId) {
    	if (logger.isActivated()) {
    		logger.info("Data transfered");
    	}
	}
	
	/**
	 * Data transfer has been received
	 * 
	 * @param msgId Message ID
	 * @param data Received data
	 * @param mimeType Data mime-type 
	 */
	public void msrpDataReceived(String msgId, byte[] data, String mimeType) {
    	if (logger.isActivated()) {
    		logger.info("Data received (type " + mimeType + ")");
    	}
    	
    	if ((data == null) || (data.length == 0)) {
    		// By-pass empty data
        	if (logger.isActivated()) {
        		logger.debug("By-pass received empty data");
        	}
    		return;
    	}

        // Notify listeners
    	for(int i=0; i < getListeners().size(); i++) {
            ((SipSessionListener)getListeners().get(i)).handleReceiveData(data);
        }
	}
    
	/**
	 * Data transfer in progress
	 * 
	 * @param currentSize Current transfered size in bytes
	 * @param totalSize Total size in bytes
	 */
	public void msrpTransferProgress(long currentSize, long totalSize) {
		// Not used here
	}

    /**
     * Data transfer in progress
     *
     * @param currentSize Current transfered size in bytes
     * @param totalSize Total size in bytes
     * @param data received data chunk
     * @return true if data are processed and can be delete in cache. If false, so data were stored in
     *         MsrpSession cache until msrpDataReceived is called.
     */
    public boolean msrpTransferProgress(long currentSize, long totalSize, byte[] data) {
		// Not used here
        return false;
    }


	/**
	 * Data transfer has been aborted
	 */
	public void msrpTransferAborted() {
		// Not used here
	}	

    /**
     * Data transfer error
     *
     * @param msgId Message ID
     * @param error Error code
     * @param typeMsrpChunk Type of MSRP chunk
     */
    public void msrpTransferError(String msgId, String error, TypeMsrpChunk typeMsrpChunk) {
		if (isSessionInterrupted()) {
			return;
		}
		
		if (logger.isActivated()) {
            logger.info("Data transfer error " + error);
        }

        // Notify listeners
        for(int i=0; i < getListeners().size(); i++) {
            ((SipSessionListener)getListeners().get(i)).handleSessionError(new SipSessionError(SipSessionError.MEDIA_FAILED, error));
        }
    }

	@Override
	public void startSession() {
		getSipService().addSession(this);
		start();
	}

	@Override
	public void removeSession() {
		getSipService().removeSession(this);
	}
	

	private SipService getSipService() {
		return parent;
	}

}
