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

import java.util.ListIterator;
import java.util.Random;

import javax2.sip.header.ContactHeader;
import javax2.sip.header.ExpiresHeader;
import javax2.sip.header.Header;
import javax2.sip.header.ViaHeader;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.network.SipManager;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.DeviceUtils;
import com.orangelabs.rcs.utils.PeriodicRefresher;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Registration manager (register, re-register, un-register)
 *
 * @author JM. Auffret
 */
public class RegistrationManager extends PeriodicRefresher {
	
	class AutoReg {	
		private int base;
		private int max;
		private Random random;
		private int nbFailures;

		public AutoReg(RcsSettings setting) {
			base = setting.getRegisterRetryBaseTime();
			max = setting.getRegisterRetryMaxTime();
			random = new Random();
			nbFailures = 0;
		}
		
		int getRetryPeriod() {
			//smaller(base * 2^nb, max) * coeff(0.5~1)
			double w = Math.min(max, (base * Math.pow(2, nbFailures)));
			double coeff = (random.nextInt(51) + 50); // Coeff between 50% and 100%
			return (int)(coeff * w);
		}

		public void fail() {
			nbFailures++;
		}
	}
    /**
     * Expire period
     */
    private int expirePeriod;

    /**
     * Dialog path
     */
    private SipDialogPath dialogPath = null;

    /**
     * Supported feature tags
     */
    private String[] featureTags;
    
    /**
     * IMS network interface
     */
    public RegistrationListener networkInterface;
 
    /**
     * Registration procedure
     */
    private RegistrationProcedure registrationProcedure;

    /**
     * Instance ID
     */
    private String instanceId = null;
    
	/**
     * Registration flag
     */
    private boolean registered = false;

    /**
     * Registration pending flag
     */
    private boolean registering = false;

    /**
     * UnRegistration need flag
     */
    private boolean needUnregister = false;

	/**
	 * Number of 401 failures
	 */
	private int nb401Failures = 0;
	
    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

	private SipManager sipManager;

	private int viaRport = -1;

	private String receivedAddress = null;

	private ListIterator<Header> associatedHeader;
	
	private AutoReg autoReg;
    
    /**
     * Constructor
     * 
     * @param listener IMS network interface
     * @param registrationProcedure Registration procedure
     * @param sip TODO
     */
    public RegistrationManager(RegistrationListener listener, RegistrationProcedure registrationProcedure, SipManager sip) {
    	this.networkInterface = listener;
    	this.sipManager = sip;
        this.registrationProcedure = registrationProcedure;
        this.featureTags = RegistrationUtils.getSupportedFeatureTags();
        
		RcsSettings settings = RcsSettings.getInstance();
		this.expirePeriod = settings.getRegisterExpirePeriod();

        if (settings.isGruuSupported()) {
            this.instanceId = DeviceUtils.getInstanceId(AndroidFactory.getApplicationContext());
        }
        
        autoReg = new AutoReg(settings);
    }
	  
    
    /**
     * Is registered
     * 
     * @return Return True if the terminal is registered, else return False
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Restart registration procedure
     */
    public void restart() {
        Thread t = new Thread() {
            /**
             * Processing
             */
            public void run() {
                // Stop the current registration
                stopRegistration();

                // Start a new registration
                register();
            }
        };
        t.start();
    }

    /**
     * Registration
     * 
     * @return Boolean status
     */
    public synchronized boolean register() {
        registering = true;
        try {
            // Create a dialog path if necessary
            if (dialogPath == null) {
            	// Reset the registration authentication procedure
            	registrationProcedure.init();
            	
            	// Set target
            	String target = "sip:" + registrationProcedure.getHomeDomain();
            	
                // Set remote party
            	String remoteParty = registrationProcedure.getPublicUri();
            	
            	dialogPath = getSipManager().createDefaultDialogPath(
            			target, remoteParty);      
            } else {
    	    	// Increment the Cseq number of the dialog path
    	        dialogPath.incrementCseq();
            }

        	// Reset the number of 401 failures
    		nb401Failures = 0;

    		// Create REGISTER request
            SipRequest register = dialogPath.createRegister(featureTags, RcsSettings.getInstance().getRegisterExpirePeriod(), instanceId);

            // Send REGISTER request
            sendRegister(register);
            
        } catch (Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Registration has failed", e);
        	}
        	handleError(new ImsError(ImsError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }
        registering = false;
        return registered;
    }
    
    /**
     * Stop the registration manager without unregistering from IMS
     */
    public synchronized void stopRegistration() {
    	if (!registered) {
			// Already unregistered
			return;
    	}    	

    	// Stop periodic registration
        stopTimer();

        // Force registration flag to false
        registered = false;

        // Reset dialog path attributes
        resetDialogPath();
        
        onTerminated();
    }


    
    /**
     * Unregistration
     */
    public synchronized void unRegistration() {
        if (registered) {
            doUnRegistration();
        } else if (registering) {
            needUnregister = true;
        }
    }

    /**
     * Unregistration
     */
    private synchronized void doUnRegistration() {
        needUnregister = false;
    	if (!registered) {
			// Already unregistered
			return;
    	}    	
    	
        try {
            // Stop periodic registration
            stopTimer();

            // Increment the Cseq number of the dialog path
            dialogPath.incrementCseq();
            
            // Create REGISTER request with expire 0
            SipRequest register = dialogPath.createRegister(featureTags, 0, instanceId);

            // Send REGISTER request
            sendRegister(register);

        } catch (Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Unregistration has failed", e);
        	}
        }

        // Force registration flag to false
        registered = false;

        // Reset dialog path attributes
        resetDialogPath();
        
        onTerminated();
    }


	void onTerminated() {
		networkInterface.handleRegistrationTerminated();
	}

	/**
	 * Send REGISTER message
	 * 
	 * @param register SIP REGISTER
	 * @throws SipException
	 * @throws CoreException
	 */
	private void sendRegister(SipRequest register) throws SipException, CoreException {
        if (logger.isActivated()) {
        	logger.info("Send REGISTER, expire=" + register.getExpires());
        }

        // Set the security header
        registrationProcedure.writeSecurityHeader(register);

        // Send REGISTER request
        SipTransactionContext ctx = getSipManager().sendSipMessageAndWait(register);

        // Analyze the received response 
        if (ctx.isSipResponse()) {
        	// A response has been received
            if (ctx.getStatusCode() == 200) {
        		// 200 OK
        		if (register.getExpires() != 0) {
        			handle200OK(ctx);
        		} else {
        			handle200OkUnregister(ctx);
        		}
            } else
            if (ctx.getStatusCode() == 302) {
        		// 302 Moved Temporarily
            	handle302MovedTemporarily(ctx);
            } else
            if (ctx.getStatusCode() == 401) {
        		// Increment the number of 401 failures
        		nb401Failures++;

        		// Check number of failures
            	if (nb401Failures < 3) {    		
                	// 401 Unauthorized
                	handle401Unauthorized(ctx);
            	} else { 
                	// We reached 3 successive 401 failures, stop registration retries
            		handleError(new ImsError(ImsError.REGISTRATION_FAILED, "too many 401"));
            	}
            } else
            if (ctx.getStatusCode() == 423) {
            	// 423 Interval Too Brief
            	handle423IntervalTooBrief(ctx);
            } else {
            	// Other error response
    			handleError(new ImsError(ImsError.REGISTRATION_FAILED,
    					ctx.getStatusCode() + " " + ctx.getReasonPhrase()));    					
            }
        } else {
        	// No response received: timeout
        	handleError(new ImsError(ImsError.REGISTRATION_FAILED, "timeout"));
        }
	}    

	/**
	 * Handle 200 0K response 
	 * 
	 * @param ctx SIP transaction context
	 * @throws SipException
	 * @throws CoreException
	 */
	private void handle200OK(SipTransactionContext ctx) throws SipException, CoreException {
        // 200 OK response received
    	if (logger.isActivated()) {
    		logger.info("200 OK response received");
    	}

    	SipResponse resp = ctx.getSipResponse();
    	
        // Set the associated URIs
		associatedHeader = resp.getHeaders(SipUtils.HEADER_P_ASSOCIATED_URI);
		
		// Set the GRUU
		getSipManager().updateGruu(resp, instanceId);
		
    	// If the IP address of the Via header in the 200 OK response to the initial
        // SIP REGISTER request is different than the local IP address then there is a NAT 
    	String localIpAddr = getLocalAddress();
    	ViaHeader respViaHeader = ctx.getSipResponse().getViaHeaders().next();
    	receivedAddress = respViaHeader.getParameter("received");
    	if (!respViaHeader.getHost().equals(localIpAddr) || ((receivedAddress != null) && !receivedAddress.equals(localIpAddr))) {
    		String viaRportStr = respViaHeader.getParameter("rport");
    		viaRport = -1;
    		if (viaRportStr != null) {
	    		try {
	    			viaRport = Integer.parseInt(viaRportStr);
	    		} catch (NumberFormatException e) {
	    			if (logger.isActivated()) {
	    				logger.warn("Non-numeric rport value \"" + viaRportStr + "\"");
	    			} 
	    		}
	    	}	
    	}
		
        // Read the security header
    	registrationProcedure.readSecurityHeader(resp);

        // Retrieve the expire value in the response
        retrieveExpirePeriod(resp);
        registered = true;
        
        // Start the periodic registration
        if (expirePeriod <= 1200 ) {
        	startTimer(expirePeriod, 0.5);
        } else {
        	startTimer(expirePeriod-600);
        }

        // Notify event listener
        onSuccess();

        // Start unregister procedure if necessary
        if (needUnregister) {
            doUnRegistration();
        }
    }


	void onSuccess() {
		networkInterface.handleRegistrationSuccessful();
	}

	String getLocalAddress() {
		return sipManager.getLocalAddress();
	}

	SipManager getSipManager() {
		return sipManager;
	}

	/**
	 * Handle 200 0K response of UNREGISTER
	 * 
	 * @param ctx SIP transaction context
	 */
	private void handle200OkUnregister(SipTransactionContext ctx) {
        // 200 OK response received
        if (logger.isActivated()) {
            logger.info("200 OK response received");
        }
        
    	// Reset the NAT parameters as we are not expecting any more messages
        // for this registration
    	clearNatTraversal();
        onTerminated();
	}
	
	private void clearNatTraversal() {
		receivedAddress = null;
		viaRport = -1;	
	}

	/**
	 * Handle 302 response
	 * 
	 * @param ctx SIP transaction context
	 * @throws SipException
	 * @throws CoreException
	 */
	private void handle302MovedTemporarily(SipTransactionContext ctx) throws SipException, CoreException {
        // 302 Moved Temporarily response received
        if (logger.isActivated()) {
            logger.info("302 Moved Temporarily response received");
        }
        
        // Extract new target URI from Contact header of the received response
		SipResponse resp = ctx.getSipResponse();
        ContactHeader contactHeader = (ContactHeader)resp.getStackMessage().getHeader(ContactHeader.NAME);
		String newUri = contactHeader.getAddress().getURI().toString();
		dialogPath.setTarget(newUri);

		// Increment the Cseq number of the dialog path
		dialogPath.incrementCseq();

		// Create REGISTER request with security token
		if (logger.isActivated()) {
			logger.info("Send REGISTER to new address");
		}
		SipRequest register = dialogPath.createRegister(featureTags, ctx.getTransaction().getRequest().getExpires().getExpires(), instanceId);

		// Send REGISTER request
		sendRegister(register);
	}

	/**
	 * Handle 401 response 
	 * 
	 * @param ctx SIP transaction context
	 * @throws SipException
	 * @throws CoreException
	 */
	private void handle401Unauthorized(SipTransactionContext ctx) throws SipException, CoreException {
		// 401 response received
    	if (logger.isActivated()) {
    		logger.info("401 response received, nbFailures=" + nb401Failures);
    	}

		SipResponse resp = ctx.getSipResponse();

		// Read the security header
		registrationProcedure.readSecurityHeader(resp);

		// Increment the Cseq number of the dialog path
		dialogPath.incrementCseq();

		// Create REGISTER request with security token
		if (logger.isActivated()) {
			logger.info("Send REGISTER with security token");
		}
		SipRequest register = dialogPath.createRegister(featureTags, ctx.getTransaction().getRequest().getExpires().getExpires(), instanceId);

		// Send REGISTER request
		sendRegister(register);
	}	

	/**
	 * Handle 423 response 
	 *
	 * @param ctx SIP transaction context
	 * @throws SipException
	 * @throws CoreException
	 */
	private void handle423IntervalTooBrief(SipTransactionContext ctx) throws SipException, CoreException {
		// 423 response received
    	if (logger.isActivated()) {
    		logger.info("423 response received");
    	}

    	SipResponse resp = ctx.getSipResponse();

		// Increment the Cseq number of the dialog path
		dialogPath.incrementCseq();
		
        // Extract the Min-Expire value
        int minExpire = resp.getMinExpires();
        if (minExpire == -1) {
            if (logger.isActivated()) {
            	logger.error("Can't read the Min-Expires value");
            }
        	handleError(new ImsError(ImsError.UNEXPECTED_EXCEPTION, "No Min-Expires value found"));
        	return;
        }
        
        // Set the expire value
    	expirePeriod = minExpire;
        
        // Create a new REGISTER with the right expire period
        if (logger.isActivated()) {
        	logger.info("Send new REGISTER");
        }
        SipRequest register = dialogPath.createRegister(featureTags, expirePeriod, instanceId);
        
        // Send REGISTER request
        sendRegister(register);
	}	
	
	/**
	 * Handle error response 
	 * 
	 * @param error Error
	 */
	private void handleError(ImsError error) {
        // Error
    	if (logger.isActivated()) {
    		logger.info("Registration has failed: " + error.getErrorCode() + ", reason=" + error.getMessage());
    	}
        registered = false;
        
        // Registration has failed, stop the periodic registration
		stopTimer();
    	
        // Reset dialog path attributes
        resetDialogPath();
        
        onError(error);
	}


	void onError(ImsError error) {
		networkInterface.handleRegistrationFailed(error);
		
		autoReg.fail();
		stopTimer();
		startTimer(autoReg.getRetryPeriod());
	}

	/**
     * Reset the dialog path
     */
    private void resetDialogPath() {
        dialogPath = null;
    }

    /**
     * Retrieve the expire period
     * 
     * @param response SIP response
     */
    private void retrieveExpirePeriod(SipResponse response) {
    	// Extract expire value from Contact header
        ListIterator<Header> contacts = response.getHeaders(ContactHeader.NAME);
	    if (contacts != null) {
	    	while(contacts.hasNext()) {
		    	ContactHeader contact = (ContactHeader)contacts.next();
		    	if (contact.getAddress().getHost().equals(getLocalAddress())) {
			    	int expires = contact.getExpires();
				    if (expires != -1) {
			    		expirePeriod = expires;
			    	}
				    return;
		    	}
	    	}
	    }
	    
        // Extract expire value from Expires header
        ExpiresHeader expiresHeader = (ExpiresHeader)response.getHeader(ExpiresHeader.NAME);
    	if (expiresHeader != null) {
    		int expires = expiresHeader.getExpires();
		    if (expires != -1) {
	    		expirePeriod = expires;
	    	}
        }
    }

	/**
     * Registration processing
     */
    public void periodicProcessing() {
    	
    	if (isRegistered()) {
    		// Make a registration
    		if (logger.isActivated()) {
    			logger.info("Execute re-registration");
    		}
    		register();
    	} else {
    		// retry 
    		register();
    	}
    }

	public String getNatAddress() {
		return receivedAddress;
	}
	
	public int getNatPort() {
		return viaRport;
	}

	public ListIterator<Header> getAssociatedHeader() {
		return associatedHeader;
	}
}
