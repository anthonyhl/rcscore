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
package com.orangelabs.rcs.core.registration;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import com.orangelabs.rcs.protocol.sip.FeatureTags;
import com.orangelabs.rcs.provider.settings.RcsSettings;

/**
 * Registration utility functions
 * 
 * @author jexa7410
 */
public class RegistrationUtils {
	/**
	 * Get supported feature tags for registration
	 *
	 * @return List of tags
	 */
 	public static String[] getSupportedFeatureTags() {
		List<String> tags = new ArrayList<String>();
		List<String> icsiTags = new ArrayList<String>();
		List<String> iariTags = new ArrayList<String>();
		
		// IM support
		if (RcsSettings.getInstance().isImSessionSupported()) {
			tags.add(FeatureTags.FEATURE_OMA_IM);
		}

		// Video share support
		if (RcsSettings.getInstance().isVideoSharingSupported()) {
			tags.add(FeatureTags.FEATURE_3GPP_VIDEO_SHARE);
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

		// Automata support
		if (RcsSettings.getInstance().isSipAutomata()) {
			tags.add(FeatureTags.FEATURE_SIP_AUTOMATA);
		}
		
		// Image share support
		if (RcsSettings.getInstance().isImageSharingSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_IMAGE_SHARE);
		}
		
		// Geoloc push support
		if (RcsSettings.getInstance().isGeoLocationPushSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_GEOLOCATION_PUSH);
		}

		// File transfer HTTP support
		if (RcsSettings.getInstance().isFileTransferHttpSupported()) {
			iariTags.add(FeatureTags.FEATURE_RCSE_FT_HTTP);
		}
		
		// Extensions
		if (RcsSettings.getInstance().isExtensionsAllowed()) {
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
}
