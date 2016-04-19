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

package com.orangelabs.rcs.core.richcall.video;

import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.vsh.IVideoPlayer;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.content.LiveVideoContent;

/**
 * Originating live video content sharing session (streaming)
 *
 * @author Jean-Marc AUFFRET
 */
public class OriginatingLiveVideoStreamingSession extends OriginatingVideoStreamingSession {
    /**
     * Constructor
     *
     * @param parent IMS service
     * @param player Media player
     * @param content Content to be shared
     * @param contact Remote contact Id
     */
    public OriginatingLiveVideoStreamingSession(ImsService parent, IVideoPlayer player,
            LiveVideoContent content, ContactId contact) {
        super(parent, player, content, contact);
    }
}
