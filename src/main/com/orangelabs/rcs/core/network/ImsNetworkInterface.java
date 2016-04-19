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

package com.orangelabs.rcs.core.network;



import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import org.xbill.DNS.Cache;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import android.net.ConnectivityManager;

import com.orangelabs.rcs.core.registration.ImsError;
import com.orangelabs.rcs.core.registration.RegistrationListener;
import com.orangelabs.rcs.core.registration.RegistrationManager;
import com.orangelabs.rcs.core.registration.RegistrationProcedure;
import com.orangelabs.rcs.core.security.HttpDigestMd5Authentication;
import com.orangelabs.rcs.core.security.cert.KeyStoreManager;
import com.orangelabs.rcs.platform.network.AndroidNetworkFactory;
import com.orangelabs.rcs.platform.network.NetworkFactory;
import com.orangelabs.rcs.protocol.sip.SipEventListener;
import com.orangelabs.rcs.protocol.sip.SipException;
import com.orangelabs.rcs.protocol.sip.UserProfile;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Abstract IMS network interface
 *
 * @author Jean-Marc AUFFRET
 */
public class ImsNetworkInterface implements RegistrationListener{
	
    /**
     * Local IP address given to the network access
     */
	protected String ipAddress = null;
	
	/**
	 * The maximum time in seconds that a negative response will be stored in this DNS Cache.
	 */
	private static int DNS_NEGATIVE_CACHING_TIME = 5;
	
	// Changed by Deutsche Telekom
	private static final String REGEX_IPV4 = "\\b((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}\\b";
    
    // Changed by Deutsche Telekom
    /**
     * Class containing the resolved fields
     */
    public class DnsResolvedFields {
        public String ipAddress = null;
        public int port = -1;

        public DnsResolvedFields(String ipAddress, int port) {
            this.ipAddress = ipAddress;
            this.port = port;
        }
    }
    
	/**
	 * IMS module
	 */
	private SipEventListener sipListener;

	/**
	 * Network interface type
	 */
	private int type;

    /**
     * SIP manager
     */
    private SipManager sip;

	/**
	 * Registration procedure associated to the network interface
	 */
	protected RegistrationProcedure registrationProcedure;

	/**
     * Registration manager
     */
    private RegistrationManager registration;
    
	/**
	 * NAT traversal
	 */
	private boolean natTraversal = false;    
    
	/**
     * NAT public IP address for last registration
     */
	private String natPublicAddress = null;
	
	/**
	 * NAT public UDP port
	 */
	private int natPublicPort = -1;
	
	
	protected LocaleProfile localeProfile;

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

	private RegistrationListener listener;

	private DnsResolvedFields dnsResolvedFields;

	private ExecutorService worker;

    /**
     * Constructor
     *
     * @param sipListener IMS module
     * @param type Network interface type
     * @param proxyAddr IMS proxy address
     * @param proxyPort IMS proxy port
     * @param proxyProtocol IMS proxy protocol
     * @param authentMode IMS authentication mode
     * @param listener registration listener.
     * @param access Network access
     */
	public ImsNetworkInterface(SipEventListener sipListener,
            RegistrationListener listener) {
		this.sipListener = sipListener;
		this.listener = listener;
		
        // Instantiates the SIP manager
        sip = new SipManager(this);
        
    	changeTo(ConnectivityManager.TYPE_MOBILE);
    	
    	worker = Executors.newSingleThreadExecutor();
	}

    /**
     * Is behind a NAT
     *
     * @return Boolean
     */
    public boolean isBehindNat() {
		return natTraversal;
    }
    
    /**
     * Returns last known NAT public address as discovered by UAC. Returns null if unknown, UAC is not registered or
	 * no NAT traversal is detected.
	 * 
	 * @return Last known NAT public address discovered by UAC or null if UAC is not registered
	 */
	private String getNatPublicAddress() {
		return natPublicAddress;
	}

	/**
	 * Returns last known NAT public UDP port as discovered by UAC. Returns -1 if unknown, UAC is not registered or
	 * no NAT traversal is detected.
	 * 
	 * @return Last known NAT public UDP port discovered by UAC or -1 if UAC is not registered
	 */
	private int getNatPublicPort() {
		return natPublicPort;
	}    
	
	/**
     * Returns the registration manager
     *
     * @return Registration manager
     */
	private RegistrationManager getRegistrationManager() {
		return registration;
	}
	
	/**
     * Load the registration procedure associated to the network access
     */
	private void loadRegistrationProcedure() {

		registrationProcedure = localeProfile.createRegistrationProcedure();
		
        // Instantiates the registration manager
        registration = new RegistrationManager(this, registrationProcedure, sip);
	}

	/**
     * Returns the network interface type
     *
     * @return Type (see ConnectivityManager class)
     */
	public int getType() {
		return type;
	}

	/**
     * Returns the SIP manager
     *
     * @return SIP manager
     */
    public SipManager getSipManager() {
    	return sip;
    }

    /**
     * Is registered
     *
     * @return Return True if the terminal is registered, else return False
     */
    public boolean isRegistered() {
        return registration.isRegistered();
    }

    /**
     * Get DNS records
     * 
     * @param domain Domain
     * @param resolver Resolver
     * @param type (Type.SRV or Type.NAPTR)
     * @return SRV records or null if no record
     */
    private Record[] getDnsRequest(String domain, ExtendedResolver resolver, int type) {
        try {
            if (logger.isActivated()) {
                if (type == Type.SRV) {
                    logger.debug("DNS SRV lookup for " + domain);
                } else if (type == Type.NAPTR) {
                    logger.debug("DNS NAPTR lookup for " + domain);
                }
            }
            Lookup lookup = new Lookup(domain, type);
            lookup.setResolver(resolver);
			// Default negative cache TTL value is "cache forever". We do not want that.
			Cache cache = Lookup.getDefaultCache(type);
			cache.setMaxNCache(DNS_NEGATIVE_CACHING_TIME);
			lookup.setCache(cache);
            Record[] result = lookup.run();
            int code = lookup.getResult();
            if (code != Lookup.SUCCESSFUL) {
                if (logger.isActivated()) {
                    logger.warn("Lookup error: " + code + "/" + lookup.getErrorString());
                }
            }
            return result;
        } catch(TextParseException e) {
            if (logger.isActivated()) {
                logger.debug("Not a valid DNS name");
            }
            return null;
        } catch(IllegalArgumentException e) {
            if (logger.isActivated()) {
                logger.debug("Not a valid DNS type");
            }
            return null;
        }
    }

    /**
     * Get DNS A record
     * 
     * @param domain Domain
     * @return IP address or null if no record
     */
    private String getDnsA(String domain) {
		try {
			if (logger.isActivated()) {
				logger.debug("DNS A lookup for " + domain);
			}
			return InetAddress.getByName(domain).getHostAddress();
        } catch(UnknownHostException e) {
			if (logger.isActivated()) {
				logger.debug("Unknown host for " + domain);
			}
			return null;
        }
    }
    
    /**
     * Get best DNS SRV record
     * 
     * @param records SRV records
     * @return IP address
     */
	private SRVRecord getBestDnsSRV(Record[] records) {
		SRVRecord result = null;
        for (int i = 0; i < records.length; i++) {
        	SRVRecord srv = (SRVRecord)records[i];
			if (logger.isActivated()) {
				logger.debug("SRV record: " + srv.toString());
			}
			if (result == null) {
				// First record
				result = srv;
			} else {
				// Next record
				if (srv.getPriority() < result.getPriority()) {
					// Lowest priority
					result = srv;
				} else
				if (srv.getPriority() == result.getPriority()) {
					// Highest weight
					if (srv.getWeight() > result.getWeight()) {
						result = srv;
					}
				}
			}
        }
        return result;
	}
	
	// Changed by Deutsche Telekom
	/**
	 * Get the DNS resolved fields.
	 * 
	 * @return The {@link DnsResolvedFields} object containing the DNS resolved fields.  
	 */
	private DnsResolvedFields getDnsResolvedFields() throws Exception {
        boolean useDns = true;
		if (localeProfile.imsProxyAddr.matches(REGEX_IPV4)) {
        	useDns = false;
        	dnsResolvedFields = new DnsResolvedFields(localeProfile.imsProxyAddr, localeProfile.imsProxyPort);
        
        	if (logger.isActivated()) {
        		logger.warn("IP address found instead of FQDN!");
        	}
        }
		else {
			dnsResolvedFields = new DnsResolvedFields(null, localeProfile.imsProxyPort);
		}
          
        if (useDns) {
            // Set DNS resolver
            ResolverConfig.refresh();
            ExtendedResolver resolver = new ExtendedResolver(); 

            // Resolve the IMS proxy configuration: first try to resolve via
            // a NAPTR query, then a SRV query and finally via A query
            if (logger.isActivated()) {
                logger.debug("Resolve IMS proxy address " + localeProfile.imsProxyAddr);
            }
            
            // DNS NAPTR lookup
            String service = localeProfile.getServiceName();

            boolean resolved = false;
            Record[] naptrRecords = getDnsRequest(localeProfile.imsProxyAddr, resolver, Type.NAPTR);
            if ((naptrRecords != null) && (naptrRecords.length > 0)) {
                // First try with NAPTR
                if (logger.isActivated()) {
                    logger.debug("NAPTR records found: " + naptrRecords.length);
                }
                
                for (int i = 0; i < naptrRecords.length; i++) {
                    NAPTRRecord naptr = (NAPTRRecord)naptrRecords[i];
                    if (logger.isActivated()) {
                        logger.debug("NAPTR record: " + naptr.toString());
                    }
                    if ((naptr != null) && naptr.getService().equalsIgnoreCase(service)) {
                        // DNS SRV lookup
						Record[] srvRecords = getDnsRequest(naptr.getReplacement().toString(), resolver, Type.SRV);
                        if ((srvRecords != null) && (srvRecords.length > 0)) {
                            SRVRecord srvRecord = getBestDnsSRV(srvRecords);
                            dnsResolvedFields.ipAddress = getDnsA(srvRecord.getTarget().toString());
                            dnsResolvedFields.port = srvRecord.getPort();
                        } else {
                            // Direct DNS A lookup
                            dnsResolvedFields.ipAddress = getDnsA(localeProfile.imsProxyAddr);
                        }
                        resolved = true;
                    }
                }
            }

            if (!resolved) {
                // If no NAPTR: direct DNS SRV lookup
                if (logger.isActivated()) {
                    logger.debug("No NAPTR record found: use DNS SRV instead");
                }
                String query;
                if (localeProfile.imsProxyAddr.startsWith("_sip.")) {
                    query = localeProfile.imsProxyAddr;
                } else {
                    query = "_sip._" + localeProfile.imsProxyProtocol.toLowerCase() + "." + localeProfile.imsProxyAddr;
                }
				Record[] srvRecords = getDnsRequest(query, resolver, Type.SRV);
                if ((srvRecords != null) && (srvRecords.length > 0)) {
                    SRVRecord srvRecord = getBestDnsSRV(srvRecords);
                    dnsResolvedFields.ipAddress = getDnsA(srvRecord.getTarget().toString());
                    dnsResolvedFields.port = srvRecord.getPort();
                    resolved = true;
                }

                if (!resolved) {
                    // If not resolved: direct DNS A lookup
                    if (logger.isActivated()) {
                        logger.debug("No SRV record found: use DNS A instead");
                    }
                    dnsResolvedFields.ipAddress = getDnsA(localeProfile.imsProxyAddr);
                }
            }       
        }
        
        if (dnsResolvedFields.ipAddress == null) {
            // Changed by Deutsche Telekom
            // Try to use IMS proxy address as a fallback
            String imsProxyAddrResolved = getDnsA(localeProfile.imsProxyAddr);
            if (imsProxyAddrResolved != null){
                dnsResolvedFields = new DnsResolvedFields(imsProxyAddrResolved, localeProfile.imsProxyPort);
            } else {
                throw new SipException("Proxy IP address not found");
            }
        }
        
        if (logger.isActivated()) {
            logger.debug("SIP outbound proxy configuration: " +
                    dnsResolvedFields.ipAddress + ":" + dnsResolvedFields.port + ";" + localeProfile.imsProxyProtocol);
        }
        

        
        return dnsResolvedFields;
	}

	/**
     * Register to the IMS
     *
     * @param dnsResolvedFields The {@link DnsResolvedFields} object containing the DNS resolved fields.
     * @return Registration result
     */
    public boolean register() {
		if (logger.isActivated()) {
			logger.debug("Register to IMS");
		}
		
		if (!sip.isReady()) {
			return false;
		}
				
    	// Register to IMS
		boolean registered = registration.register();
		if (registered) {
			if (logger.isActivated()) {
				logger.debug("IMS registration successful");
			}

            // Start keep-alive for NAT if activated
            if (isBehindNat()) {
                sip.startKeepAlive();
            }
		} else {
			if (logger.isActivated()) {
				logger.debug("IMS registration has failed");
			}
		}

    	return registered;
    }

	void init() {
			
		try {
	        dnsResolvedFields = getDnsResolvedFields();
		    
		    String ip = probeLocalIpAddress(dnsResolvedFields.ipAddress, dnsResolvedFields.port);
	        
		    if (ip == null) {
		    	return ;
		    }
		    
	        if (logger.isActivated()) {
	            logger.debug("local ip: " + ip);
	        }
	        
		    if (StringUtils.equals(ip, ipAddress)) {
		    	registration.unRegistration();
		    	return ;
		    }
		    
		    ipAddress = ip;
		    
			loadRegistrationProcedure();	
			
			updateClientCertificate();
			
			// Initialize the SIP stack
			// Changed by Deutsche Telekom
            sip.initStack(ipAddress,
            		dnsResolvedFields.ipAddress, dnsResolvedFields.port,
            		localeProfile.imsProxyProtocol, localeProfile.tcpFallback, getType(),
            		localeProfile.getUserProfile());
	    	sip.setSipEventListener(sipListener);

		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't instanciate the SIP stack", e);
			}

		}
	}

	/**
     * Unregister from the IMS
     */
    public void unregister() {
		if (logger.isActivated()) {
			logger.debug("Unregister from IMS");
		}

		// Unregister from IMS
		registration.unRegistration();

    	// Close the SIP stack
    	sip.terminate();
    }

	/**
     * Registration terminated
     */
    private void registrationTerminated() {
		if (logger.isActivated()) {
			logger.debug("Registration has been terminated");
		}

		// Stop registration
		registration.stopRegistration();

		// Close the SIP stack
    	sip.terminate();
    }
    
	public final String getIpAddress() {
		return ipAddress;
	}
	
	/**
     * Connect to the network access
     * 
     * @param ipAddress Local IP address
     */
    public void connect(final int network) {
    	
    	worker
    		.execute(
    			new Runnable () {
    				public void run() {
    					changeTo(network);    	

    					try {
    						init();
    					} catch (Exception e) {
    						e.printStackTrace();
    					}

    					if (logger.isActivated()) {
    						logger.info("Network access connected (" + ipAddress + ")");
    					}
    					
    	            	updateClientCertificate();

    					register();
    				}


    			});

    }
    
	private void changeTo(int network) {
		type = network;
		if (type == ConnectivityManager.TYPE_MOBILE) {
			localeProfile = LocaleProfile.mobile(RcsSettings.getInstance());
		}
		else {
			localeProfile = LocaleProfile.wifi(RcsSettings.getInstance());
		}
		
		loadRegistrationProcedure();
	}
	
	void updateClientCertificate() {
		AndroidNetworkFactory factory = (AndroidNetworkFactory)NetworkFactory.getFactory();
    	KeyStoreManager keys = factory.getKeyStore();
    	
    	if (type == ConnectivityManager.TYPE_WIFI) {
    		keys.updateClientCertificate(ipAddress, getUserProfile());
    	}
	}

	/**
     * Disconnect from the network access
     */
    public void disconnect(int network) {  
    	
    	if (type != network) {
        	if (logger.isActivated()) {
        		logger.info("disconnection miss match" + network);
        	}
    		return ;
    	}
    	
    	if (logger.isActivated()) {
    		logger.info("Network access disconnected");
    	}
		// Registration terminated 
		registrationTerminated();
	
    	ipAddress = null;
    }

	public boolean isRegisteredAt(String host, int port) {
		
		// First check if the request URI matches with the local interface address
		boolean isMatchingRegistered = ipAddress.equals(host);
		
		// If no matching, perhaps we are behind a NAT
		if ((!isMatchingRegistered) && isBehindNat()) {
			// We are behind NAT: check if the request URI contains the previously
			// discovered public IP address and port number
			String natPublicIpAddress = getNatPublicAddress();
			int natPublicUdpPort = getNatPublicPort();
			if ((natPublicUdpPort != -1) && (natPublicIpAddress != null)) {
				
				isMatchingRegistered = natPublicIpAddress.equals(host) && (natPublicUdpPort == port);
			} else {
				// NAT traversal and unknown public address/port
				isMatchingRegistered = false;
			}
		}
		return isMatchingRegistered;
	}

	public HttpDigestMd5Authentication getRegisterDigest() {
		return registrationProcedure.getHttpDigest();
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.network.RegistrationListener#handleRegistrationTerminated()
	 */
	@Override
	public void handleRegistrationTerminated() {
		
		clearNatTraversal();
		// Notify event listener
	    listener.handleRegistrationTerminated();
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.network.RegistrationListener#handleRegistrationFailed(com.orangelabs.rcs.core.ImsError)
	 */
	@Override
	public void handleRegistrationFailed(ImsError error) {
		
		clearNatTraversal();
		// Notify event listener
	    listener.handleRegistrationFailed(error);
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.network.RegistrationListener#handleRegistrationSuccessful()
	 */
	@Override
	public void handleRegistrationSuccessful() {
		
		localeProfile.getUserProfile().setAssociatedUri(getRegistrationManager().getAssociatedHeader());
		
		natTraversal = getRegistrationManager().getNatAddress() != null;
		natPublicAddress = getRegistrationManager().getNatAddress();
		natPublicPort = getRegistrationManager().getNatPort();
		
		if (logger.isActivated()) {
			logger.debug("NAT public interface detected: " + natPublicAddress + ":" + natPublicPort);
		}

		listener.handleRegistrationSuccessful();
	}

	private void clearNatTraversal() {
		natTraversal = false;
		this.natPublicAddress = null;
		this.natPublicPort = -1;
	}

	public void restartRegister() {
		registration.restart();
	}
	
	private String probeLocalIpAddress(String remote, int port) {		
		try {
	        DatagramSocket socket = new DatagramSocket();

			try {
				socket.connect(InetAddress.getByName(remote), port);
				if (socket.isConnected()) {
					return socket.getLocalAddress().getHostAddress();
				}
			} finally {
				socket.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public UserProfile getUserProfile() {
		return localeProfile.getUserProfile();
	}
}
