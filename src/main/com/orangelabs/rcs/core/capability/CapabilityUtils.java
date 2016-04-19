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
package com.orangelabs.rcs.core.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import android.text.TextUtils;

import com.orangelabs.rcs.core.content.GeolocContent;
import com.orangelabs.rcs.core.richcall.image.ImageTransferSession;
import com.orangelabs.rcs.protocol.rtp.MediaRegistry;
import com.orangelabs.rcs.protocol.rtp.format.video.VideoFormat;
import com.orangelabs.rcs.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.protocol.sdp.SdpParser;
import com.orangelabs.rcs.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.protocol.sdp.SimpleSessionDescription;
import com.orangelabs.rcs.protocol.sdp.SimpleSessionDescription.Media;
import com.orangelabs.rcs.protocol.sip.FeatureTags;
import com.orangelabs.rcs.protocol.sip.SipMessage;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.MimeManager;
import com.orangelabs.rcs.utils.NetworkUtils;
import com.orangelabs.rcs.utils.StringUtils;

/**
 * Capability utility functions
 * 
 * @author jexa7410
 */
public class CapabilityUtils {
	
	/**
	 * Get supported feature tags for capability exchange
	 *
	 * @param richcall Rich call supported
	 * @return List of tags
	 */
 	public static String[] getSupportedFeatureTags(boolean richcall) {
		List<String> tags = new ArrayList<String>();
		List<String> icsiTags = new ArrayList<String>();
		List<String> iariTags = new ArrayList<String>();

		// Video share support
		if (RcsSettings.getInstance().isVideoSharingSupported() && richcall
				&& (NetworkUtils.getNetworkAccessType() >= NetworkUtils.NETWORK_ACCESS_3G)) {
			tags.add(FeatureTags.FEATURE_3GPP_VIDEO_SHARE);
		}

		// Chat support
		if (RcsSettings.getInstance().isImSessionSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_CHAT);
		}

		// FT support
		if (RcsSettings.getInstance().isFileTransferSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_FT);
		}

		// FT over HTTP support
		if (RcsSettings.getInstance().isFileTransferHttpSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_FT_HTTP);
		}

		// Image share support
		if (RcsSettings.getInstance().isImageSharingSupported() && richcall) {
			iariTags.add(FeatureTags.FEATURE_RCSE_IMAGE_SHARE);
		}

		// Presence discovery support
		if (RcsSettings.getInstance().isPresenceDiscoverySupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_PRESENCE_DISCOVERY);
		}

		// Social presence support
		if (RcsSettings.getInstance().isSocialPresenceSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_SOCIAL_PRESENCE);
		}

		// Geolocation push support
		if (RcsSettings.getInstance().isGeoLocationPushSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_GEOLOCATION_PUSH);
		}

		// FT thumbnail support
		if (RcsSettings.getInstance().isFileTransferThumbnailSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_FT_THUMBNAIL);
		}

		// FT S&F support
		if (RcsSettings.getInstance().isFileTransferStoreForwardSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_FT_SF);
		}

		// Group chat S&F support
		if (RcsSettings.getInstance().isGroupChatStoreForwardSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_GC_SF);
		}

		// IP call support
		if (RcsSettings.getInstance().isIPVoiceCallSupported()) {
			tags.add(FeatureTags.FEATURE_RCSE_IP_VOICE_CALL);
		}
		if (RcsSettings.getInstance().isIPVideoCallSupported()) {
			tags.add(FeatureTags.FEATURE_RCSE_IP_VIDEO_CALL);
		}
		if (RcsSettings.getInstance().isIPVoiceCallSupported() || RcsSettings.getInstance().isIPVideoCallSupported()) {
			icsiTags.add(FeatureTags.FEATURE_3GPP_IP_VOICE_CALL);
		}		
		
		// Automata flag
		if (RcsSettings.getInstance().isSipAutomata()) {
			tags.add(FeatureTags.FEATURE_SIP_AUTOMATA);
		}

		// Extensions
		if (RcsSettings.getInstance().isExtensionsAllowed()) {
			for (String extension : RcsSettings.getInstance().getSupportedRcsExtensions()) {
				StringBuilder sb = new StringBuilder(FeatureTags.FEATURE_RCSE_EXTENSION).append(".").append(extension);
				iariTags.add(sb.toString());
			}
			icsiTags.add(FeatureTags.FEATURE_3GPP_EXTENSION);
		}
		
		// Add IARI prefix
		if (!iariTags.isEmpty()) {
            tags.add(FeatureTags.FEATURE_RCSE + "=\"" + TextUtils.join(",", iariTags) + "\"");
		}
		
		// Add ICSI prefix
		if (!icsiTags.isEmpty()) {
            tags.add(FeatureTags.FEATURE_3GPP + "=\"" + TextUtils.join(",", icsiTags) + "\"");
		}
		
 		return tags.toArray(new String[tags.size()]);
	}

    /**
     * Extract features tags
     * 
     * @param msg Message
     * @return Capabilities
     */
    public static Capabilities extractCapabilities(SipMessage msg) {

    	// Analyze feature tags
    	Capabilities capabilities = new Capabilities(); 
    	ArrayList<String> tags = msg.getFeatureTags();
		boolean ipCall_RCSE = false;
		boolean ipCall_3GPP = false;
		
    	for(int i=0; i < tags.size(); i++) {
    		String tag = tags.get(i);
    		if (tag.contains(FeatureTags.FEATURE_3GPP_VIDEO_SHARE)) {
        		// Support video share service
        		capabilities.setVideoSharingSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_IMAGE_SHARE)) {
        		// Support image share service
        		capabilities.setImageSharingSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_CHAT)) {
        		// Support IM service
        		capabilities.setImSessionSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_FT)) {
        		// Support FT service
        		capabilities.setFileTransferSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_FT_HTTP)) {
        		// Support FT over HTTP service
        		capabilities.setFileTransferHttpSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_OMA_IM)) {
        		// Support both IM & FT services
        		capabilities.setImSessionSupport(true);
        		capabilities.setFileTransferSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_PRESENCE_DISCOVERY)) {
        		// Support capability discovery via presence service
        		capabilities.setPresenceDiscoverySupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_SOCIAL_PRESENCE)) {
        		// Support social presence service
        		capabilities.setSocialPresenceSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_GEOLOCATION_PUSH)) {
        		// Support geolocation push service
        		capabilities.setGeolocationPushSupport(true);
        	} else
    		if (tag.contains(FeatureTags.FEATURE_RCSE_FT_THUMBNAIL)) {
    			// Support file transfer thumbnail service
    			capabilities.setFileTransferThumbnailSupport(true);
    		} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_IP_VOICE_CALL)) {
        		// Support IP Call
        		if (ipCall_3GPP) {
        			capabilities.setIPVoiceCallSupport(true);		 	
        		} else {
        			ipCall_RCSE = true;	
        		}
        	} else
        	if (tag.contains(FeatureTags.FEATURE_3GPP_IP_VOICE_CALL)) {
        		// Support IP Call
        		if (ipCall_RCSE) {
        			capabilities.setIPVoiceCallSupport(true);		       	    	
        		} else {
        			ipCall_3GPP = true;	
        		}
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_IP_VIDEO_CALL)) {
            	capabilities.setIPVideoCallSupport(true);		
            } else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_FT_SF)) {
        		// Support FT S&F service
        		capabilities.setFileTransferStoreForwardSupport(true);
        	} else
        	if (tag.contains(FeatureTags.FEATURE_RCSE_GC_SF)) {
        		// Support FT S&F service
        		capabilities.setGroupChatStoreForwardSupport(true);
        	} else
// TODO    		if (tag.contains(FeatureTags.FEATURE_RCSE_EXTENSION + ".ext") ||
// TODO   				tag.contains(FeatureTags.FEATURE_RCSE_EXTENSION + ".mnc")) {
    		if (tag.contains(FeatureTags.FEATURE_RCSE_EXTENSION)) {
    			// Support an RCS extension
				capabilities.addSupportedExtension(extractServiceId(tag));
			} else
			if (tag.contains(FeatureTags.FEATURE_SIP_AUTOMATA)) {
				capabilities.setSipAutomata(true);
			}
    	}
    	
    	// Analyze SDP part
    	byte[] content = msg.getContentBytes();
		if (content != null) {
	    	SdpParser parser = new SdpParser(content);

	    	// Get supported video codecs
	    	Vector<MediaDescription> mediaVideo = parser.getMediaDescriptions("video");
	    	Vector<String> videoCodecs = new Vector<String>();
			for (int i=0; i < mediaVideo.size(); i++) {
				MediaDescription desc = mediaVideo.get(i);
	    		MediaAttribute attr = desc.getMediaAttribute("rtpmap");
				if (attr !=  null) {
    	            String rtpmap = attr.getValue();
    	            String encoding = rtpmap.substring(rtpmap.indexOf(desc.payload)+desc.payload.length()+1);
    	            String codec = encoding.toLowerCase().trim();
    	            int index = encoding.indexOf("/");
    				if (index != -1) {
    					codec = encoding.substring(0, index);
    				}
    	    		if (MediaRegistry.isCodecSupported(codec)) {
    	    			videoCodecs.add(codec);
    	    		}    			
    			}
			}
			if (videoCodecs.size() == 0) {
				// No video codec supported between me and the remote contact
				capabilities.setVideoSharingSupport(false);
			}
    	
	    	// Check supported image formats
	    	Vector<MediaDescription> mediaImage = parser.getMediaDescriptions("message");
	    	Vector<String> imgFormats = new Vector<String>();
			for (int i=0; i < mediaImage.size(); i++) {
				MediaDescription desc = mediaImage.get(i);
	    		MediaAttribute attr = desc.getMediaAttribute("accept-types");
				if (attr != null) {
    	            String[] types = attr.getValue().split(" ");
    	            for(int j = 0; j < types.length; j++) {
    	            	String fmt = types[j];
    	            	if ((fmt != null) && MimeManager.getInstance().isMimeTypeSupported(fmt)) { // Changed by Deutsche Telekom AG
    	            		imgFormats.addElement(fmt);
    	            	}
    	    		}
				}
			}
			if (imgFormats.size() == 0) {
				// No image format supported between me and the remote contact
				capabilities.setImageSharingSupport(false);
			}
		}
        capabilities.setTimestampOfLastRefresh(System.currentTimeMillis());
        capabilities.setTimestampOfLastRequest(System.currentTimeMillis());
    	return capabilities;
    }
       
    /**
     * Build supported SDP part
     * 
     * @param ipAddress Local IP address
	 * @param richcall Rich call supported
	 * @return SDP
     */
    public static String buildSdp(String ipAddress, boolean richcall) {
    	
		
    	SimpleSessionDescription o = new SimpleSessionDescription(
    			System.currentTimeMillis(), ipAddress);
    	
//    	Media m = o.newMedia("message", localPort, 1, protocol);
//    	m.setAttribute("accept-type", acceptTypes);
//    	m.setAttribute("accept-wrapped-types", wrapperTypes);	
//    	m.setAttribute("file-transfer-id", transferId);      
//    	m.setAttribute("file-disposition", disposition);
//    	

    	
    	String sdp = null;
		if (richcall) {
	        boolean video = RcsSettings.getInstance().isVideoSharingSupported() 
	        		&& NetworkUtils.getNetworkAccessType() >= NetworkUtils.NETWORK_ACCESS_3G;
	        boolean image = RcsSettings.getInstance().isImageSharingSupported();
	        boolean geoloc =  RcsSettings.getInstance().isGeoLocationPushSupported();
	        if (video || image) {
				// Changed by Deutsche Telekom
	        	String mimeTypes = null;
	        	String protocol = null;
	        	String selector = null;
	        	int maxSize = 0;
	        	String media = null;
	        	
		    	// Add video config
		        if (video) {
		        	// Get supported video formats
			    	Media m = o.newMedia("video",  0, 1, "RTP/AVP");
			    	
					for (VideoFormat videoFormat : MediaRegistry.getSupportedVideoFormats()) {
				    	m.setRtpPayload(videoFormat.getPayload(), videoFormat.getCodec(), null);
					}
			    	
//					Vector<VideoFormat> videoFormats = MediaRegistry.getSupportedVideoFormats();
//					StringBuilder videoSharingConfig = new StringBuilder();
//					
//					for (VideoFormat videoFormat : videoFormats) {
//				    	m.setRtpPayload(videoFormat.getPayload(), videoFormat.getCodec(), null);	    	
//						videoSharingConfig.append("m=video 0 RTP/AVP " + videoFormat.getPayload() + SipUtils.CRLF);
//						videoSharingConfig.append("a=rtpmap:" + videoFormat.getPayload() + " " + videoFormat.getCodec() + SipUtils.CRLF);
//					}
//					
//	                // Changed by Deutsche Telekom
//			    	media = videoSharingConfig.toString();
		        }
	        
				// Add image and geoloc config
		        if (image || geoloc) {
					StringBuilder supportedTransferFormats = new StringBuilder();

					// Get supported image formats
		        	// Changed by Deutsche Telekom
					Set<String> imageMimeTypes = MimeManager.getInstance().getSupportedImageMimeTypes();
					for (String imageMimeType : imageMimeTypes) {
						supportedTransferFormats.append(imageMimeType + " ");
					}
					
					// Get supported geoloc
		        	if (geoloc) {
		        		supportedTransferFormats.append(GeolocContent.ENCODING);		        		
		        	}
 	
					// Changed by Deutsche Telekom
					mimeTypes = supportedTransferFormats.toString().trim();
					protocol = SdpUtils.MSRP_PROTOCOL;
					selector = "";
			    	maxSize = ImageTransferSession.getMaxImageSharingSize();
			    	
			    	Media m = o.newMedia("message", 0, 1, protocol);
					
					if (geoloc) {
						m.setFormat(GeolocContent.ENCODING, null);		        		
		        	}

					m.setAttribute("accept-types", mimeTypes);
					m.setAttribute("selector", "");
					m.setAttribute("max-size", String.valueOf(maxSize));
		        }

		        // Changed by Deutsche Telekom
		        sdp = SdpUtils.buildCapabilitySDP(ipAddress, protocol, mimeTypes, selector, media, maxSize);
	        }
		}
		return sdp;
    }
    
	/**
	 * Extract service ID from feature tag extension
	 * 
	 * @param featureTag Feature tag
	 * @return Service ID
	 */
	public static String extractServiceId(String featureTag) { 
		String serviceId;
		try {
			String[] values = featureTag.split("=");
			String value =  StringUtils.removeQuotes(values[1]);
			serviceId = value.substring(FeatureTags.FEATURE_RCSE_EXTENSION.length()+1, value.length());
		} catch(Exception e) {
			serviceId = null;
		}
		return serviceId;
	}    
}
