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
package com.orangelabs.rcs.core.sip.streaming;

import static com.orangelabs.rcs.utils.StringUtils.UTF8;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.ImsServiceSession;
import com.orangelabs.rcs.core.sip.GenericSipSession;
import com.orangelabs.rcs.core.sip.SipService;
import com.orangelabs.rcs.core.sip.SipSessionError;
import com.orangelabs.rcs.core.sip.SipSessionListener;
import com.orangelabs.rcs.protocol.rtp.MediaRtpReceiver;
import com.orangelabs.rcs.protocol.rtp.MediaRtpSender;
import com.orangelabs.rcs.protocol.rtp.format.Format;
import com.orangelabs.rcs.protocol.rtp.format.data.DataFormat;
import com.orangelabs.rcs.protocol.rtp.stream.RtpStreamListener;
import com.orangelabs.rcs.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.protocol.sdp.SdpParser;
import com.orangelabs.rcs.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.utils.NetworkRessourceManager;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Generic SIP RTP session 
 * 
 * @author jexa7410
 */
public abstract class GenericSipRtpSession extends GenericSipSession implements RtpStreamListener {
	/**
	 * RTP payload format
	 */
	private DataFormat format = new DataFormat();
	
	
	
	/**
	 * Local RTP port
	 */
	private int localRtpPort = -1;
    
    /**
	 * Data sender
	 */
	private DataSender dataSender = new DataSender();
	
    /**
     * Data receiver
     */
    private DataReceiver dataReceiver = new DataReceiver(this);	
	
	/**
	 * RTP receiver
	 */
	private MediaRtpReceiver rtpReceiver;
	
	/**
	 * RTP sender
	 */
	private MediaRtpSender rtpSender;
	
	/**
	 * Startup flag
	 */
	private boolean started = false;
	
	/**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(GenericSipRtpSession.class.getSimpleName());

    SipService parent;
    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param contact Remote contact Id
	 * @param featureTag Feature tag
	 */
	public GenericSipRtpSession(SipService parent, ContactId contact, String featureTag) {
		super(parent, contact, featureTag);

		this.parent = parent;
		// Get local port
		localRtpPort = NetworkRessourceManager.generateLocalRtpPort();

        // Create the RTP sender & receiver
		rtpReceiver = new MediaRtpReceiver(localRtpPort);
		rtpSender = new MediaRtpSender(format, localRtpPort);
	}

    /**
     * Get local port
     * 
     * @return RTP port
     */
    public int getLocalRtpPort() {
    	return localRtpPort;
    }	
	
	/**
	 * Returns the RTP receiver
	 * 
	 * @return RTP receiver
	 */
	public MediaRtpReceiver getRtpReceiver() {
		return rtpReceiver;
	}	
	
	/**
	 * Returns the RTP sender
	 * 
	 * @return RTP sender
	 */
	public MediaRtpSender getRtpSender() {
		return rtpSender;
	}
	
	/**
	 * Returns the RTP format
	 * 
	 * @return RTP format
	 */
	public Format getRtpFormat() {
		return format;
	}	
	
    /**
     * Generate SDP
     */
    public String generateSdp() {
    	String ntpTime = SipUtils.constructNTPtime(System.currentTimeMillis());
    	String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
    	return "v=0" + SipUtils.CRLF +
            "o=- " + ntpTime + " " + ntpTime + " " + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
            "s=-" + SipUtils.CRLF +
			"c=" + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
            "t=0 0" + SipUtils.CRLF +			
            "m=application " + localRtpPort + " RTP/AVP " + getRtpFormat().getPayload() + SipUtils.CRLF + 
            "a=rtpmap:" + getRtpFormat().getPayload() + " " + getRtpFormat().getCodec() + "/90000" + SipUtils.CRLF + // TODO: hardcoded value for clock rate and codec
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
        MediaDescription mediaApp = parser.getMediaDescription("application");
        String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaApp);
        int remotePort = mediaApp.port;

        // Prepare media
        rtpReceiver.prepareSession(remoteHost, remotePort, dataReceiver, format, this);
    	rtpSender.prepareSession(dataSender, remoteHost, remotePort, rtpReceiver.getInputStream(), this);
    }

    /**
     * Start media session
     * 
     * @throws Exception 
     */
    public void startMediaSession() throws Exception {
    	synchronized(this) {
    		// Start media
	    	rtpReceiver.startSession();
	    	rtpSender.startSession();
	    	
	    	started = true;
    	}
    }

    /**
     * Close media session
     */
    public void closeMediaSession() {
    	synchronized(this) {
	    	started = false;
	    	
    		// Stop media
	    	rtpSender.stopSession();
	    	rtpReceiver.stopSession();
    	}
    }

    /**
     * Sends a payload in real time
     * 
     * @param content Payload content
	 * @return Returns true if sent successfully else returns false
     */
    public boolean sendPlayload(byte[] content) {
    	if (started) {
    		dataSender.addFrame(content, System.currentTimeMillis());
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /**
     * Invoked when the RTP stream was aborted
     */
    public void rtpStreamAborted() {
        if (isSessionInterrupted()) {
            return;
        }

        if (logger.isActivated()) {
            logger.error("Media has failed: network failure");
        }

        // Close the media session
        closeMediaSession();

        // Terminate session
        terminateSession(ImsServiceSession.TERMINATION_BY_SYSTEM);

        // Remove the current session
        removeSession();

        // Notify listeners
        for (int j = 0; j < getListeners().size(); j++) {
            ((SipSessionListener) getListeners().get(j))
                    .handleSessionError(new SipSessionError(SipSessionError.MEDIA_FAILED));
        }
    }
    
    /**
     * Receive media data
     *
     * @param data Data
     */
    public void receiveData(byte[] data) {
        // Notify listeners
        for (int j = 0; j < getListeners().size(); j++) {
            ((SipSessionListener) getListeners().get(j)).handleReceiveData(data);
        }
    }

	@Override
	public void startSession() {
		getImsService().getImsModule().getSipService().addSession(this);
		start();
	}

	@Override
	public void removeSession() {
		getImsService().getImsModule().getSipService().removeSession(this);
	}
	
	private SipService getSipService() {
		return parent;
	}
}
