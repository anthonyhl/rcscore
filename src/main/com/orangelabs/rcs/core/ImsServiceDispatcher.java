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

package com.orangelabs.rcs.core;

import java.util.ArrayList;
import java.util.List;

import com.orangelabs.rcs.protocol.sip.SipEventListener;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.utils.FifoBuffer;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * IMS service dispatcher
 * 
 * @author jexa7410
 */
public class ImsServiceDispatcher extends Thread implements SipEventListener {
	
	interface Service {
		boolean handleRequest(SipRequest request);
	}
    
    List<Service> services = new ArrayList<>();

    /**
	 * Buffer of messages
	 */
	private FifoBuffer buffer = new FifoBuffer();


	
	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
	 * Constructor
	 * 
	 * @param imsModule IMS module
	 */
	public ImsServiceDispatcher() {
		super("SipDispatcher");
	}
	
	void addService(Service service) {
		services.add(service);
	}
	
    /**
     * Terminate the SIP dispatcher
     */
    public void terminate() {
    	if (logger.isActivated()) {
    		logger.info("Terminate the multi-session manager");
    	}
        buffer.close();
        if (logger.isActivated()) {
        	logger.info("Multi-session manager has been terminated");
        }
    }
    
	/**
	 * Post a SIP request in the buffer
	 * 
     * @param request SIP request
	 */
	public void receiveSipRequest(SipRequest request) {
		buffer.addObject(request);
	}
    
	/**
	 * Background processing
	 */
	public void run() {
		if (logger.isActivated()) {
			logger.info("Start background processing");
		}
		SipRequest request = null; 
		while((request = (SipRequest)buffer.getObject()) != null) {
				dispatch(request);

		}
		if (logger.isActivated()) {
			logger.info("End of background processing");
		}
	}
    
    /**
     * Dispatch the received SIP request
     * 
     * @param request SIP request
     */
    private void dispatch(SipRequest request) {    
        boolean handled = false;
        for (Service agent : services) {       	
        	try {
        		if (agent.handleRequest(request)){
        			handled = true;
        			break;
        		}
        	} catch(Exception e) {
        		if (logger.isActivated()) {
        			logger.error("Unexpected exception", e);
        		}
        	}
        }
        
        if (!handled) {
			if (logger.isActivated()) {
				logger.debug("request disposed:" + request.toString());
			}
        }
    }
    


  
}
