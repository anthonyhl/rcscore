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
package com.orangelabs.rcs.core.sip.streaming;

import com.orangelabs.rcs.protocol.rtp.media.MediaOutput;
import com.orangelabs.rcs.protocol.rtp.media.MediaSample;

/**
 * Data renderer in charge of receiving data payload and to forward it to
 * the application via the API 
 * 
 * @author Jean-Marc AUFFRET
 */
public class DataReceiver implements MediaOutput {
	/**
	 * Parent
	 */
	private GenericSipRtpSession parent;
	
    /**
     * Constructor
     */
    public DataReceiver(GenericSipRtpSession parent) {
    	this.parent = parent;
    }

    /**
     * Open the renderer
     */
    public void open() {
        // TODO
    }

    /**
     * Close the renderer
     */
    public void close() {
        // TODO
    }

    /**
     * Write a media sample
     *
     * @param sample Sample
     */
    public void writeSample(MediaSample sample) {
    	// Notify API
    	parent.receiveData(sample.getData());
    }
}