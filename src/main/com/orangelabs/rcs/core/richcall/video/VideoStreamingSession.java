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

package com.orangelabs.rcs.core.richcall.video;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.vsh.IVideoPlayer;
import com.gsma.services.rcs.vsh.IVideoRenderer;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.ImsServiceError;
import com.orangelabs.rcs.core.content.MmContent;
import com.orangelabs.rcs.core.richcall.ContentSharingError;
import com.orangelabs.rcs.core.richcall.ContentSharingSession;
import com.orangelabs.rcs.core.richcall.RichcallService;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Video sharing streaming session
 * 
 * @author Jean-Marc AUFFRET
 */
public abstract class VideoStreamingSession extends ContentSharingSession {
	/**
	 * Video width
	 */
	private int videoWidth = -1;
	
	/**
	 * Video height
	 */
	private int videoHeight = -1;

	/**
	 * Video renderer
	 */
	private IVideoRenderer renderer;

    /**
     * Video renderer
     */
    private IVideoPlayer player;

    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(VideoStreamingSession.class.getSimpleName());

	/**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param content Content to be shared
	 * @param contact Remote contact Id
	 */
	public VideoStreamingSession(ImsService parent, MmContent content, ContactId contact) {
		super(parent, content, contact);
	}

	/**
	 * Get the video width
	 * 
	 * @return Width
	 */
	public int getVideoWidth() {
		return videoWidth;
	}

	/**
	 * Get the video height
	 * 
	 * @return Height
	 */
	public int getVideoHeight() {
		return videoHeight;
	}

	/**
	 * Get the video renderer
	 * 
	 * @return Renderer
	 */
	public IVideoRenderer getVideoRenderer() {
		return renderer;
	}
	
	/**
	 * Set the video renderer
	 * 
	 * @param renderer Renderer
	 */
	public void setVideoRenderer(IVideoRenderer renderer) {
		this.renderer = renderer;
	}

    /**
     * Get the video player
     * 
     * @return Player
     */
    public IVideoPlayer getVideoPlayer() {
        return player;
    }

    /**
     * Set the video player
     *
     * @param Player
     */
    public void setVideoPlayer(IVideoPlayer player) {
        this.player = player;
    }

    /**
     * Create an INVITE request
     *
     * @return the INVITE request
     * @throws SipException 
     */
    public SipRequest createInvite() throws SipException {
        String[] featureTags = RichcallService.FEATURE_TAGS_VIDEO_SHARE;
		return getDialogPath().createInvite(featureTags, featureTags, getDialogPath().getLocalContent());
    }

    /**
     * Handle error
     *
     * @param error Error
     */
    public void handleError(ImsServiceError error) {
        if (isSessionInterrupted()) {
            return;
        }

        // Error
        if (logger.isActivated()) {
            logger.info("Session error: " + error.getErrorCode() + ", reason="
                    + error.getMessage());
        }

        // Close media session
        closeMediaSession();

        // Remove the current session
        removeSession();

        try {
			ContactId remote = ContactUtils.createContactId(getDialogPath().getRemoteParty());
			// Request capabilities to the remote
	        getImsService().getImsModule().getCapabilityService().requestContactCapabilities(remote);
		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
				logger.warn("Cannot parse contact "+getDialogPath().getRemoteParty());
			}
		}

        // Notify listeners
        for (int i = 0; i < getListeners().size(); i++) {
            ((VideoStreamingSessionListener) getListeners().get(i))
                    .handleSharingError(new ContentSharingError(error));
        }
    }

	@Override
	public void startSession() {
		getImsService().getImsModule().getRichcallService().addSession(this);
		start();
	}

	@Override
	public void removeSession() {
		getImsService().getImsModule().getRichcallService().removeSession(this);
	}
}
