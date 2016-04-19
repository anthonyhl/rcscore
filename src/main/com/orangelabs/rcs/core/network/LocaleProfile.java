package com.orangelabs.rcs.core.network;

import javax2.sip.ListeningPoint;

import com.orangelabs.rcs.core.registration.GibaRegistrationProcedure;
import com.orangelabs.rcs.core.registration.HttpDigestRegistrationProcedure;
import com.orangelabs.rcs.core.registration.ImsiDegestRegistrationProcedure;
import com.orangelabs.rcs.core.registration.RegistrationProcedure;
import com.orangelabs.rcs.core.userprofile.GibaUserProfileInterface;
import com.orangelabs.rcs.core.userprofile.SettingsUserProfileInterface;
import com.orangelabs.rcs.core.userprofile.UserProfileInterface;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData.AuthenticationProcedure;

public class LocaleProfile {
	/**
	 * IMS proxy protocol
	 */
	public String imsProxyProtocol;
	/**
	 * IMS proxy address
	 */
	public String imsProxyAddr;
	/**
	 * IMS proxy port
	 */
	public int imsProxyPort;
	/**
	 * TCP fallback according to RFC3261 chapter 18.1.1
	 */
	public boolean tcpFallback;
	/**
	 * IMS authentication mode associated to the network interface
	 */
	protected AuthenticationProcedure imsAuthentMode;
	
	private UserProfile user;

	public LocaleProfile(String proxyAddr, int proxyPort, String proxyProtocol,  AuthenticationProcedure authentMode) {
        this.imsProxyAddr = proxyAddr;
        this.imsProxyPort = proxyPort;
        this.imsProxyProtocol = proxyProtocol;
		this.imsAuthentMode = authentMode;
		if (proxyProtocol.equalsIgnoreCase(ListeningPoint.UDP))
			this.tcpFallback = RcsSettings.getInstance().isTcpFallback();
	}
	
	public static LocaleProfile mobile(RcsSettings rcsSettings) { 
		return new LocaleProfile(
				rcsSettings.getImsProxyAddrForMobile(),
				rcsSettings.getImsProxyPortForMobile(),
				rcsSettings.getSipDefaultProtocolForMobile(),
				rcsSettings.getImsAuthenticationProcedureForMobile());
    }
    
	public static LocaleProfile  wifi(RcsSettings rcsSettings){
		return new LocaleProfile(
						rcsSettings.getImsProxyAddrForWifi(),
						rcsSettings.getImsProxyPortForWifi(),
						rcsSettings.getSipDefaultProtocolForWifi(),
						rcsSettings.getImsAuthenticationProcedureForWifi());
	}
	
	RegistrationProcedure createRegistrationProcedure() {		
		UserProfile userProfile = getUserProfile();
		switch (imsAuthentMode) {
		case GIBA:
			/*if (logger.isActivated()) {
				logger.debug("Load GIBA authentication procedure");
			}*/
			return new GibaRegistrationProcedure(userProfile);
			
		case DIGEST:
			/*if (logger.isActivated()) {
				logger.debug("Load HTTP Digest authentication procedure");
			}*/
			return new HttpDigestRegistrationProcedure(userProfile);
		case IMSI_DIGEST:
			/*if (logger.isActivated()) {
				logger.debug("Load IMSI Digest authentication procedure");
			}*/
			return new ImsiDegestRegistrationProcedure(userProfile);
		}
		return null;
	}
	
	/**
     * Returns the user profile associated to the network access
     *
     * @return User profile
     */
	public UserProfile getUserProfile() {
		if (user == null) {
			UserProfileInterface intf;
			switch (imsAuthentMode) {
			case GIBA:
//				if (logger.isActivated()) {
//					logger.debug("Load user profile derived from IMSI (GIBA)");
//				}
				intf = new GibaUserProfileInterface();
				break;
			case DIGEST:
			default:
//				if (logger.isActivated()) {
//					logger.debug("Load user profile from RCS settings database");
//				}
				intf = new SettingsUserProfileInterface();
				break;
			}
			user = intf.read();
		}
    	
    	return user;   	
	}

	String getServiceName() throws SipException {
		String service;
		if (imsProxyProtocol.equalsIgnoreCase(ListeningPoint.UDP)) {
		    service = "SIP+D2U";
		} else
		if (imsProxyProtocol.equalsIgnoreCase(ListeningPoint.TCP)) {
		    service = "SIP+D2T";
		} else
		if (imsProxyProtocol.equalsIgnoreCase(ListeningPoint.TLS)) {
		    service = "SIPS+D2T";
		} else {
		    throw new SipException("Unkown SIP protocol");
		}
		return service;
	}

	/**
	 * Is network interface configured
	 *
	 * @param imsNetworkInterface TODO
	 * @return Boolean
	 */
	public boolean isInterfaceConfigured() {
		return (imsProxyAddr != null) && (imsProxyAddr.length() > 0);
	}
}