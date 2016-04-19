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
package com.orangelabs.rcs.protocol.sip;

/**
 * Feature tags
 * 
 * @author jexa7410
 * @author yplo6403
 */
public class FeatureTags {
	
	class Builder { 
		static final String ImageShare = ".gsma-is";
		static final String VideoShare = ".gsma-vs";
		static final String Chat = ".rcse.im";
		static final String Ft = ".rcse.ft";
		static final String FtStAndFw = "rcs.ft";
		static final String FtHttp = "rcs.fthttp";
		static final String SocialPresence = "rcse.sp";
		static final String GeoPush = "rcs.geopush";
		static final String GeoPull = "rcs.geopull";
		static final String GeoPullFt = "rcs.geopullft";

		static final String Oma_Cpm_Msg = ".oma.cpm.msg";
		static final String Oma_Cpm_Largemsg = ".oma.cpm.largemsg";
		static final String MmTel = ".oma.mmtel";
		static final String IpCall = ".ipcall";
		static final String VideoCallOnly = ".ipvideocallonly";
		

		StringBuilder tags;
		
		Builder() {
			tags = new StringBuilder("+g.");
		}
		
		Builder gsma(String name) {
			final String ns = ".gsma.rcs";
			tags.append(ns).append(name);
			return this;
		}
				
		Builder iari(String one, String... other) {
			final String ns = "urn%3Aurn-7%3A3gpp-application.ims.iari"	;
			final String name = ".3gpp.iari-ref=";
			
			combineTags(name, ns, one, other);
			return this;
		}
		
		Builder icsi(String one, String... other) {
			final String ns = "urn%3Aurn-7%3A3gpp-application.ims.icsi"	;
			final String name = ".3gpp.icsi-ref=";
			combineTags(name, ns, one, other);
			return this;
		}
		
		private void combineTags(final String name, final String ns, 
				final String one, String... other) {
			tags.append(name);
			tags.append('\"').append(ns).append(one);
			for (String arg : other) {
				tags.append(',').append(ns).append(arg); 
			}
			tags.append('\"');
		}
		
		String build() {
			return tags.toString();
		}
	}

	/**
	 * OMA IM feature tag
	 */
	public final static String FEATURE_OMA_IM = "+g.oma.sip-im";

	/**
	 * 3GPP video share feature tag
	 */
	public final static String FEATURE_3GPP_VIDEO_SHARE = "+g.3gpp.cs-voice";
	
	/**
     * 3GPP image share feature tag
     */
    public final static String FEATURE_3GPP_IMAGE_SHARE = "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.gsma-is\"";
											
	/**
	 * 3GPP image share feature tag for RCS 2.0
	 */
	public final static String FEATURE_3GPP_IMAGE_SHARE_RCS2 = "+g.3gpp.app_ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.gsma-is\"";
	
	/**
	 * 3GPP location share feature tag
	 */
	public final static String FEATURE_3GPP_LOCATION_SHARE = "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.geopush\"";
	
	/**
	 * RCS-e feature tag prefix
	 */
	public final static String FEATURE_RCSE = "+g.3gpp.iari-ref";
	
	/**
	 * 3GPP feature tag prefix
	 */
	public final static String FEATURE_3GPP = "+g.3gpp.icsi-ref";
	
	/**
	 * RCS-e image share feature tag
	 */
	public final static String FEATURE_RCSE_IMAGE_SHARE = "urn%3Aurn-7%3A3gpp-application.ims.iari.gsma-is";

	/**
	 * RCS-e chat feature tag
	 */
	public final static String FEATURE_RCSE_CHAT = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.im";

	/**
	 * RCS-e file transfer feature tag
	 */
	public final static String FEATURE_RCSE_FT = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.ft";

	/**
	 * RCS-e file transfer over HTTP feature tag
	 */
	public final static String FEATURE_RCSE_FT_HTTP = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp";

	/**
	 * RCS-e presence discovery feature tag
	 */
	public final static String FEATURE_RCSE_PRESENCE_DISCOVERY = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.dp";

	/**
	 * RCS-e social presence feature tag
	 */
	public final static String FEATURE_RCSE_SOCIAL_PRESENCE = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.sp";

	/**
	 * RCS-e geolocation push feature tag
	 */
	public final static String FEATURE_RCSE_GEOLOCATION_PUSH = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.geopush";
	
	/**
	 * RCS-e file transfer thumbnail feature tag
	 */
	public final static String FEATURE_RCSE_FT_THUMBNAIL = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.ftthumb";

	/**
	 * RCS-e file transfer S&F feature tag
	 */
	public final static String FEATURE_RCSE_FT_SF = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.ftstandfw";

	/**
	 * RCS-e group chat S&F feature tag
	 */
	public final static String FEATURE_RCSE_GC_SF = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fullsfgroupchat";
	
	/**
	 * 3GPP IP call feature tag
	 */
	public final static String FEATURE_3GPP_IP_VOICE_CALL = "urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel";
	
	/**
	 * RCS-e IP call feature tag
	 */
	public final static String FEATURE_RCSE_IP_VOICE_CALL = "+g.gsma.rcs.ipcall";
	
	/**
	 * RCS IP video call feature tag
	 */
	public final static String FEATURE_RCSE_IP_VIDEO_CALL = "video";

	/**
	 * RCS-e extension feature tag prefix
	 */
	public final static String FEATURE_RCSE_EXTENSION = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcs";
	
	/**
	 * 3GPP RCS extension feature tag
	 */
	public final static String FEATURE_3GPP_EXTENSION = "urn%3Aurn-7%3A3gpp-service.ims.icsi.gsma.rcs.extension";

	/**
	 * 3GPP RCS service extension
	 */
	public final static String FEATURE_3GPP_SERVICE_EXTENSION = "urn:urn-7:3gpp-service.ims.icsi.gsma.rcs.extension";

	/**
	 * SIP Automata feature tag
	 * 
	 * <pre>
	 * @see RFC 3840 "Indicating User Agent Capabilities in the Session Initiation Protocol (SIP)"
	 * 
	 * The automata tag indicates whether the UA represents an automata (such as a voicemail server, 
	 * conference server, IVR, or recording device) or a human.
	 * </pre>
	 */
	public final static String FEATURE_SIP_AUTOMATA = "automata";


    /**********************************************************************************
     *  tct-stack add for CMCC message modes
     **********************************************************************************/

	/**
     * RCS-e standalone messaging service extension
     */
    public final static String FEATURE_RCSE_STANDALONE_MSG = "urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.msg";

    /**
     * RCS-e standalone messaging service extension
     */
    public final static String FEATURE_RCSE_STANDALONE_LARGEMSG = "urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.largemsg";

    /**
     * RCS-e chat session
     */
    public final static String FEATURE_RCSE_CHAT_SESSION = "urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session";

    /**
     * RCS-e file transfer
     */
    public final static String FEATURE_RCSE_FILETRANSFER = "urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.filetransfer";

    /**
     * RCS-e CMCC Mcloud file tag
     */
    public final static String FEATURE_MCLOUD_FILE="urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.mnc000.mcc460.cloudfile;version=1_0";

    /**
     * RCS-e CMCC vemoticon tag in ft
     */
    public final static String FEATURE_VEMOTICON="urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.mnc000.mcc460.vemoticon;version=1_0";

}
