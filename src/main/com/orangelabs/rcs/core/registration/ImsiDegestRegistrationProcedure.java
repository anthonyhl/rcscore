package com.orangelabs.rcs.core.registration;

import javax2.sip.header.AuthenticationInfoHeader;
import javax2.sip.header.AuthorizationHeader;
import javax2.sip.header.WWWAuthenticateHeader;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipResponse;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.utils.SimCard;
import com.orangelabs.rcs.utils.logger.Logger;

public class ImsiDegestRegistrationProcedure implements RegistrationProcedure {

	private SimCard sim;
	/**
	 * HTTP Digest MD5 agent
	 */
	private HttpDigestMd5Authentication digest = null;
	
	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());
    
    //ImsModule imsModule;
    UserProfile user;

    /**
	 * Constructor
     * @param module TODO
	 */
	public ImsiDegestRegistrationProcedure(UserProfile user) {
		//imsModule = module;
		this.user = user;
	}

	/**
	 * Initialize procedure
	 */
	@Override
	public void init() {
		digest = new HttpDigestMd5Authentication();
		sim = SimCard.load(AndroidFactory.getApplicationContext());
	}
	
	/**
	 * Returns the home domain name
	 * 
	 * @return Domain name
	 */
	@Override
	public String getHomeDomain() {
		return getUser().getHomeDomain();
	}

	private UserProfile getUser() {
		return user;
	}

	/**
	 * Returns the public URI or IMPU for registration
	 * 
	 * @return Public URI
	 */
	@Override
	public String getPublicUri() {
		return "sip:" + sim.getImsi() + "@" + sim.getHomeDomain();
		//return "sip:" + "13980079174" + "@" + sim.getHomeDomain();
	}
	
	public String getPrivateUri() {
		return sim.getImsi() + "@" + sim.getHomeDomain();
		//return "sip:" + "13980079174" + "@" + sim.getHomeDomain();
	}
	

	/**
	 * Write security header to REGISTER request
	 * 
	 * @param request Request
	 * @throws CoreException
	 */
	@Override
	public void writeSecurityHeader(SipRequest request) throws CoreException {
		if (digest == null || digest.getNextnonce() == null) {
			return;
		}

		try {
            // Get Realm
            if (digest.getRealm() == null) {
                digest.setRealm(getUser().getRealm());
            }
            
            digest.updateNonceParameters();
			
			// Set header in the SIP message 
			request.addHeader(AuthorizationHeader.NAME, 
					digest.genDigestAuth(request.getMethod(),
							request.getRequestURIString(),
							request.getContent(), 
							getPrivateUri(),
							getUser().getPassword()));
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create the authorization header", e);
			}
			throw new CoreException("Can't write the security header");
		}
    }
	   

	/**
	 * Read security header from REGISTER response
	 * 
	 * @param response SIP response
	 * @throws CoreException
	 */
	@Override
	public void readSecurityHeader(SipResponse response) throws CoreException {
		if (digest == null) {
			return;
		}

		WWWAuthenticateHeader wwwHeader = (WWWAuthenticateHeader)response.getHeader(WWWAuthenticateHeader.NAME);
		AuthenticationInfoHeader infoHeader =  (AuthenticationInfoHeader)response.getHeader(AuthenticationInfoHeader.NAME);

		if (wwwHeader != null) {
			// Retrieve data from the header WWW-Authenticate (401 response)
			try {
				// Get domain name
				digest.setRealm(wwwHeader.getRealm());
	
				// Get opaque parameter
		   		digest.setOpaque(wwwHeader.getOpaque());

		   		// Get qop
		   		digest.setQop(wwwHeader.getQop());
		   		
		   		// Get nonce to be used
		   		digest.setNextnonce(wwwHeader.getNonce());
			} catch(Exception e) {
				if (logger.isActivated()) {
					logger.error("Can't read the WWW-Authenticate header", e);
				}
				throw new CoreException("Can't read the security header");
			}
		} else
		if (infoHeader != null) {
			// Retrieve data from the header Authentication-Info (200 OK response)
			try {
				// Check if 200 OK really included Authentication-Info: nextnonce=""
				if ( infoHeader.getNextNonce() != null ) { 
					// Get nextnonce to be used
			   		digest.setNextnonce(infoHeader.getNextNonce());
				}
			} catch(Exception e) {
				if (logger.isActivated()) {
					logger.error("Can't read the authentication-info header", e);
				}
				throw new CoreException("Can't read the security header");
			}
		}
	}
	
	/**
	 * Returns HTTP digest
	 * 
	 * @return HTTP digest
	 */
	public HttpDigestMd5Authentication getHttpDigest() {
		return digest;
	}
}
