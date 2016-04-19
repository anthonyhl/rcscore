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

package com.orangelabs.rcs.protocol.presence;

import static com.orangelabs.rcs.utils.StringUtils.UTF8_STR;

import java.util.Set;

import javax2.sip.header.EventHeader;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.core.AddressBookEventListener;
import com.orangelabs.rcs.core.ContactInfo.RcsStatus;
import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ImsModule;
import com.orangelabs.rcs.core.ImsService;
import com.orangelabs.rcs.core.capability.Capabilities;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.platform.registry.AndroidRegistryFactory;
import com.orangelabs.rcs.protocol.http.HttpResponse;
import com.orangelabs.rcs.protocol.presence.xdm.XdmManager;
import com.orangelabs.rcs.protocol.sip.SipRequest;
import com.orangelabs.rcs.protocol.sip.SipUtils;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.eab.ContactsManagerException;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.DateUtils;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Presence service
 *
 * @author Jean-Marc AUFFRET
 */
public class PresenceService extends ImsService implements AddressBookEventListener{

	private final RcsSettings mRcsSettings;

	private final ContactsManager mContactsManager;

	/**
	 * Permanent state feature
	 */
	public boolean permanentState;

	/**
     * Presence info
     */
    private PresenceInfo presenceInfo = new PresenceInfo();

	/**
	 * Publish manager
	 */
	private PublishManager publisher;

	/**
	 * XDM manager
	 */
	private XdmManager xdm;

	/**
	 * Watcher info subscribe manager
	 */
	private SubscribeManager watcherInfoSubscriber;

	/**
	 * Presence subscribe manager
	 */
	private SubscribeManager presenceSubscriber;

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     *
     * @param parent IMS module
     * @param rcsSettings RcsSettings
     * @param contactsManager ContactsManager
     * @throws CoreException
     */
	public PresenceService(ImsModule parent, RcsSettings rcsSettings,
			ContactsManager contactsManager) throws CoreException {
        super(parent, RcsSettings.getInstance().isSocialPresenceSupported());
        mRcsSettings = rcsSettings;
        mContactsManager = contactsManager;
		// Set presence service options
		this.permanentState = mRcsSettings.isPermanentStateModeActivated();

		// Instantiate the XDM manager
    	xdm = new XdmManager(parent);

    	// Instantiate the publish manager
        publisher = new PublishManager(parent);

    	// Instantiate the subscribe manager for watcher info
    	watcherInfoSubscriber = new WatcherInfoSubscribeManager(parent);

    	// Instantiate the subscribe manager for presence
        presenceSubscriber = new PresenceSubscribeManager(parent);
	}
	
	private String getPublicUri() {
		return getUser().getPublicUri();
	}

	/**
	 * Start the IMS service
	 */
    public synchronized void start() {
		if (isServiceStarted()) {
			// Already started
			return;
		}
		setServiceStarted(true);

		// Listen to address book changes
		getImsModule().getAddressBookManager().addAddressBookListener(this);

		// Restore the last presence info from the contacts database
		presenceInfo = mContactsManager.getMyPresenceInfo();
		if (logger.isActivated()) {
			logger.debug("Last presence info:\n" + presenceInfo.toString());
		}

		// Initialize the XDM interface
		xdm.initialize();

    	// Add me in the granted set if necessary
		Set<ContactId> grantedContacts = xdm.getGrantedContacts();

		ContactId me = getUser().getUsername();

		if (!grantedContacts.contains(me)) {
			if (logger.isActivated()) {
				logger.debug("The enduser is not in the granted set: add it now");
			}
			xdm.addContactToGrantedList(me);
		}

		//TODO: move this to rcssetting;
		boolean newUser = false;
		try {
			AndroidRegistryFactory registry;
			registry = new AndroidRegistryFactory();
			newUser = registry.getNewUserAccount();
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
		// It may be necessary to initiate the address book first launch or account check procedure
        if (newUser) {
			Set<ContactId> blockedContacts = xdm.getBlockedContacts();
			firstLaunchOrAccountChangedCheck(grantedContacts, blockedContacts);
		}

        // Subscribe to watcher-info events
    	if (watcherInfoSubscriber.subscribe()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager is started with success for watcher-info");
            }
		} else {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager can't be started for watcher-info");
        	}
		}

		// Subscribe to presence events
    	if (presenceSubscriber.subscribe()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager is started with success for presence");
            }
		} else {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager can't be started for presence");
        	}
		}

    	// Publish initial presence info
    	String xml;
    	if (permanentState) {
    		xml = buildPartialPresenceInfoDocument(presenceInfo);
    	} else {
    		xml = buildPresenceInfoDocument(presenceInfo);
    	}
    	if (publisher.publish(xml)) {
        	if (logger.isActivated()) {
        		logger.debug("Publish manager is started with success");
        	}
		} else {
        	if (logger.isActivated()) {
        		logger.debug("Publish manager can't be started");
        	}
		}

    	// Force a presence check
    	handleAddressBookHasChanged();
	}

    /**
     * First launch or account changed check <br>
     * Check done at first launch of the service on the phone after install of
     * the application or when the user account changed <br>
     * We create a new contact with the adequate state for each RCS number in
     * the XDM lists that is not already existing on the phone
     *
     * @param list of granted contacts
     * @param list of blocked contacts
     */
	private void firstLaunchOrAccountChangedCheck(Set<ContactId> grantedContacts, Set<ContactId> blockedContacts){
		if (logger.isActivated()){
			logger.debug("First launch or account change check procedure");
		}

		// Flush the address book provider
		mContactsManager.flushContactProvider();

		ContactId me = null;
		try {
			me = ContactUtils.createContactId(getPublicUri());
		} catch (RcsContactFormatException e) {
			if (logger.isActivated()) {
        		logger.error("Cannot parse user contact "+getPublicUri());
        	}
		}
		// Treat the buddy list
		for (ContactId contact : grantedContacts) {
			if (me != null && !contact.equals(me)) {
				// For each RCS granted contact, except me
				if (!ContactUtils.isNumberInAddressBook(contact)) {
					// If it is not present in the address book
					if (logger.isActivated()) {
						logger.debug("The RCS number " + contact + " was not found in the address book: add it");
					}

					// => We create the entry in the regular address book
					try {
						ContactUtils.createRcsContactIfNeeded(AndroidFactory.getApplicationContext(), contact);
					} catch (Exception e) {
						if (logger.isActivated()) {
							logger.error("Something went wrong when creating contact " + contact, e);
						}
					}
				}

				// Add the contact to the rich address book provider
				mContactsManager.modifyRcsContactInProvider(contact, RcsStatus.PENDING_OUT);
			}
		}

		// Treat the blocked contact list
		for (ContactId contact : blockedContacts) {
			// For each RCS blocked contact
			if (!ContactUtils.isNumberInAddressBook(contact)) {
				// If it is not present in the address book
				if (logger.isActivated()) {
					logger.debug("The RCS number " + contact + " was not found in the address book: add it");
				}

				// => We create the entry in the regular address book
				try {
					ContactUtils.createRcsContactIfNeeded(AndroidFactory.getApplicationContext(), contact);
				} catch (Exception e) {
					if (logger.isActivated()) {
						logger.error("Something went wrong when creating contact " + contact, e);
					}
				}
				// Set the presence sharing status to blocked
				try {
					mContactsManager.blockContact(contact);
				} catch (ContactsManagerException e) {
					if (logger.isActivated()) {
						logger.error("Something went wrong when blocking contact " + contact, e);
					}
				}

				// Add the contact to the rich address book provider
				mContactsManager.modifyRcsContactInProvider(contact, RcsStatus.BLOCKED);
			}
		}
	}

	/**
     * Stop the IMS service
     */
	public synchronized void stop() {
		if (!isServiceStarted()) {
			// Already stopped
			return;
		}
		setServiceStarted(false);

		// Stop listening to address book changes
		getImsModule().getAddressBookManager().removeAddressBookListener(this);

		if (!permanentState) {
            // If not permanent state mode: publish a last presence info before
            // to quit
			if ((getImsModule() != null) &&
					getImsModule().isRegistered() &&
				publisher.isPublished()) {
				String xml = buildPresenceInfoDocument(presenceInfo);
		    	publisher.publish(xml);
			}
    	}

    	// Stop publish
    	publisher.terminate();

    	// Stop subscriptions
    	watcherInfoSubscriber.terminate();
    	presenceSubscriber.terminate();
	}

	/**
     * Check the IMS service
     */
	public void check() {
    	if (logger.isActivated()) {
    		logger.debug("Check presence service");
    	}

        // Check subscribe manager status for watcher-info events
		if (!watcherInfoSubscriber.isSubscribed()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager not yet started for watcher-info");
        	}

        	if (watcherInfoSubscriber.subscribe()) {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager is started with success for watcher-info");
                }
			} else {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager can't be started for watcher-info");
	        	}
			}
		}

		// Check subscribe manager status for presence events
		if (!presenceSubscriber.isSubscribed()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager not yet started for presence");
        	}

        	if (presenceSubscriber.subscribe()) {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager is started with success for presence");
                }
			} else {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager can't be started for presence");
	        	}
			}
		}
	}

	/**
     * Is permanent state procedure
     *
     * @return Boolean
     */
	public boolean isPermanentState() {
		return permanentState;
	}

	/**
     * Set the presence info
     *
     * @param info Presence info
     */
	public void setPresenceInfo(PresenceInfo info) {
		presenceInfo = info;
	}

	/**
     * Returns the presence info
     *
     * @return Presence info
     */
	public PresenceInfo getPresenceInfo() {
		return presenceInfo;
	}

	/**
     * Returns the publish manager
     *
     * @return Publish manager
     */
    public PublishManager getPublishManager() {
        return publisher;
    }

	/**
     * Returns the watcher-info subscribe manager
     *
     * @return Subscribe manager
     */
	public SubscribeManager getWatcherInfoSubscriber() {
		return watcherInfoSubscriber;
	}

    /**
     * Returns the presence subscribe manager
     *
     * @return Subscribe manager
     */
	public SubscribeManager getPresenceSubscriber() {
		return presenceSubscriber;
	}

    /**
     * Returns the XDM manager
     *
     * @return XDM manager
     */
    public XdmManager getXdmManager() {
        return xdm;
    }

	/**
     * Build boolean status value
     *
     * @param state Boolean state
     * @return String
     */
	private String buildBooleanStatus(boolean state) {
		if (state) {
			return "open";
		} else {
			return "closed";
		}
	}

    /**
     * Build capabilities document
     *
     * @param timestamp Timestamp
     * @param capabilities Capabilities
     * @return Document
     */
    private String buildCapabilities(String timestamp, Capabilities capabilities) {
    	return
    	    "<tuple id=\"t1\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isFileTransferSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_FT + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
	    	"<tuple id=\"t2\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isImageSharingSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_IMAGE_SHARE + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
			"<tuple id=\"t3\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isVideoSharingSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_VIDEO_SHARE + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
			"<tuple id=\"t4\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isImSessionSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_CHAT + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
			"<tuple id=\"t5\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isCsVideoSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_CS_VIDEO + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF;
    }



    /**
     * Build geoloc document
     *
     * @param timestamp Timestamp
     * @param geolocInfo Geoloc info
     * @return Document
     */
    private String buildGeoloc(String timestamp, Geoloc geolocInfo) {
    	String document = "";
    	if (geolocInfo != null) {
    		document +=
    			 "<tuple id=\"g1\">" + SipUtils.CRLF +
			     "  <status><basic>open</basic></status>" + SipUtils.CRLF +
			     "   <gp:geopriv>" + SipUtils.CRLF +
			     "    <gp:location-info><gml:location>" + SipUtils.CRLF +
			     "        <gml:Point srsDimension=\"3\"><gml:pos>" + geolocInfo.getLatitude() + " " +
			     				geolocInfo.getLongitude() + " " +
			     				geolocInfo.getAltitude() + "</gml:pos>" + SipUtils.CRLF +
			     "        </gml:Point></gml:location>" + SipUtils.CRLF +
			     "    </gp:location-info>" + SipUtils.CRLF +
			     "    <gp:method>GPS</gp:method>" + SipUtils.CRLF +
			     "   </gp:geopriv>"+SipUtils.CRLF +
				 "  <contact>" + getPublicUri() + "</contact>" + SipUtils.CRLF +
				 "  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			     "</tuple>" + SipUtils.CRLF;
    	}
    	return document;
    }

	/**
	 * Build person info document
	 *
	 * @param info Presence info
	 * @return Document
	 */
	private String buildPersonInfo(PresenceInfo info) {
		StringBuilder document = new StringBuilder("  <op:overriding-willingness>")
				.append(SipUtils.CRLF).append("    <op:basic>").append(info.getPresenceStatus())
				.append("</op:basic>").append(SipUtils.CRLF)
				.append("  </op:overriding-willingness>").append(SipUtils.CRLF);

		FavoriteLink favoriteLink = info.getFavoriteLink();
		if ((favoriteLink != null) && (favoriteLink.getLink() != null)) {
			document.append("  <ci:homepage>")
					.append(StringUtils.encodeXML(favoriteLink.getLink())).append("</ci:homepage>")
					.append(SipUtils.CRLF);
		}

		PhotoIcon photoIcon = info.getPhotoIcon();
		String eTag = photoIcon.getEtag();
		if ((photoIcon != null) && (eTag != null)) {
			document.append("  <rpid:status-icon opd:etag=\"").append(eTag)
					.append("\" opd:fsize=\"").append(photoIcon.getSize())
					.append("\" opd:contenttype=\"").append(photoIcon.getType())
					.append("\" opd:resolution=\"").append(photoIcon.getResolution()).append("\">")
					.append(xdm.getEndUserPhotoIconUrl()).append("</rpid:status-icon>")
					.append(SipUtils.CRLF);
		}

		String freetext = info.getFreetext();
		if (freetext != null) {
			document.append("  <pdm:note>").append(StringUtils.encodeXML(freetext))
					.append("</pdm:note>").append(SipUtils.CRLF);
		}

		return document.toString();
	}

	/**
	 * Build presence info document (RCS 1.0)
	 *
	 * @param info Presence info
	 * @return Document
	 */
	private String buildPresenceInfoDocument(PresenceInfo info) {
		String document = new StringBuilder("<?xml version=\"1.0\" encoding=\"")
				.append(UTF8_STR).append("\"?>").append(SipUtils.CRLF)
				.append("<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"")
				.append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
				.append(" xmlns:opd=\"urn:oma:xml:pde:pidf:ext\"")
				.append(" xmlns:pdm=\"urn:ietf:params:xml:ns:pidf:data-model\"")
				.append(" xmlns:ci=\"urn:ietf:params:xml:ns:pidf:cipid\"")
				.append(" xmlns:rpid=\"urn:ietf:params:xml:ns:pidf:rpid\"")
				.append(" xmlns:gp=\"urn:ietf:params:xml:ns:pidf:geopriv10\"")
				.append(" xmlns:gml=\"urn:opengis:specification:gml:schema-xsd:feature:v3.0\"")
				.append(" entity=\"").append(getPublicUri())
				.append("\">").append(SipUtils.CRLF).toString();

    	// Encode timestamp
    	String timestamp = DateUtils.encodeDate(info.getTimestamp());

    	// Build capabilities
    	document += buildCapabilities(timestamp, mRcsSettings.getMyCapabilities());

		// Build geoloc
    	document += buildGeoloc(timestamp, info.getGeoloc());

    	// Build person info
    	document += "<pdm:person id=\"p1\">" + SipUtils.CRLF +
					buildPersonInfo(info) +
    				"  <pdm:timestamp>" + timestamp + "</pdm:timestamp>" + SipUtils.CRLF +
				    "</pdm:person>" + SipUtils.CRLF;

    	// Add last header
    	document += "</presence>" + SipUtils.CRLF;

        return document;
    }

	/**
	 * Build partial presence info document (all presence info except permanent
	 * state info)
	 *
	 * @param info Presence info
	 * @return Document
	 */
	private String buildPartialPresenceInfoDocument(PresenceInfo info) {
		String document = new StringBuilder("<?xml version=\"1.0\" encoding=\"")
				.append(UTF8_STR).append("\"?>").append(SipUtils.CRLF)
				.append("<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"")
				.append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
				.append(" xmlns:opd=\"urn:oma:xml:pde:pidf:ext\"")
				.append(" xmlns:pdm=\"urn:ietf:params:xml:ns:pidf:data-model\"")
				.append(" xmlns:ci=\"urn:ietf:params:xml:ns:pidf:cipid\"")
				.append(" xmlns:rpid=\"urn:ietf:params:xml:ns:pidf:rpid\"")
				.append(" xmlns:gp=\"urn:ietf:params:xml:ns:pidf:geopriv10\"")
				.append(" xmlns:gml=\"urn:opengis:specification:gml:schema-xsd:feature:v3.0\"")
				.append(" entity=\"").append(getPublicUri())
				.append("\">").append(SipUtils.CRLF).toString();

    	// Encode timestamp
    	String timestamp = DateUtils.encodeDate(info.getTimestamp());

    	// Build capabilities
    	document += buildCapabilities(timestamp, mRcsSettings.getMyCapabilities());

		// Build geoloc
    	document += buildGeoloc(timestamp, info.getGeoloc());

    	// Add last header
    	document += "</presence>" + SipUtils.CRLF;

        return document;
    }

	/**
	 * Build permanent presence info document (RCS R2.0)
	 *
	 * @param info Presence info
	 * @return Document
	 */
	private String buildPermanentPresenceInfoDocument(PresenceInfo info) {
		String document = new StringBuilder("<?xml version=\"1.0\" encoding=\"")
				.append(UTF8_STR).append("\"?>").append(SipUtils.CRLF)
				.append("<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"")
				.append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
				.append(" xmlns:opd=\"urn:oma:xml:pde:pidf:ext\"")
				.append(" xmlns:pdm=\"urn:ietf:params:xml:ns:pidf:data-model\"")
				.append(" xmlns:ci=\"urn:ietf:params:xml:ns:pidf:cipid\"")
				.append(" xmlns:rpid=\"urn:ietf:params:xml:ns:pidf:rpid\"").append(" entity=\"")
				.append(getPublicUri()).append("\">")
				.append(SipUtils.CRLF).toString();

    	// Encode timestamp
    	String timestamp = DateUtils.encodeDate(info.getTimestamp());

    	// Build person info (freetext, favorite link and photo-icon)
    	document += "<pdm:person id=\"p1\">" + SipUtils.CRLF +
					buildPersonInfo(info) +
    				"  <pdm:timestamp>" + timestamp + "</pdm:timestamp>" + SipUtils.CRLF +
				    "</pdm:person>" + SipUtils.CRLF;

    	// Add last header
	    document += "</presence>" + SipUtils.CRLF;

        return document;
    }

    /**
     * Update photo-icon
     *
     * @param photoIcon Photo-icon
     * @return Boolean result
     */
    private boolean updatePhotoIcon(PhotoIcon photoIcon) {
    	boolean result = false;

    	// Photo-icon management
    	PhotoIcon currentPhoto = presenceInfo.getPhotoIcon();
    	if ((photoIcon != null) && (photoIcon.getEtag() == null)) {
    		// Test photo icon size
    		int maxSize = mRcsSettings.getMaxPhotoIconSize()*1024;
        	if ((maxSize != 0) && (photoIcon.getSize() > maxSize)) {
    			if (logger.isActivated()) {
    				logger.debug("Max photo size achieved");
    			}
    			return false;
            }

    		// Upload the new photo-icon
    		if (logger.isActivated()) {
    			logger.info("Upload the photo-icon");
    		}
    		result = uploadPhotoIcon(photoIcon);
    	} else
    	if ((photoIcon == null) && (currentPhoto != null)) {
    		// Delete the current photo-icon
    		if (logger.isActivated()) {
    			logger.info("Delete the photo-icon");
    		}
    		result = deletePhotoIcon();
    	} else {
    		// Nothing to do
    		result = true;
    	}

    	return result;
    }

    /**
     * Publish presence info
     *
     * @param info Presence info
     * @returns Returns true if the presence info has been publish with success,
     *          else returns false
     */
    public boolean publishPresenceInfo(PresenceInfo info) {
    	boolean result = false;

    	// Photo-icon management
    	result = updatePhotoIcon(info.getPhotoIcon());
    	if (!result) {
    		// Can't update the photo-icon in the XDM server
    		return result;
    	}

    	// Reset timestamp
    	info.resetTimestamp();

		// Publish presence info
    	if (permanentState) {
    		// Permanent state procedure: publish the new presence info via XCAP
    		if (logger.isActivated()) {
    			logger.info("Publish presence info via XDM request (permanent state)");
    		}
    		String xml = buildPermanentPresenceInfoDocument(info);
    		HttpResponse response = xdm.setPresenceInfo(xml);
            if ((response != null) && response.isSuccessfullResponse()) {
    			result = true;
    		} else {
    			result = false;
    		}
    	} else {
    		// SIP procedure: publish the new presence info via SIP
    		if (logger.isActivated()) {
    			logger.info("Publish presence info via SIP request");
    		}
    		String xml = buildPresenceInfoDocument(info);
			result = publisher.publish(xml);
    	}

		// If server updated with success then update contact info cache
    	if (result) {
    		presenceInfo = info;
		}

    	return result;
    }

    /**
     * Upload photo icon
     *
     * @param photo Photo icon
     * @returns Boolean result
     */
	public boolean uploadPhotoIcon(PhotoIcon photo) {
		// Upload the photo to the XDM server
		HttpResponse response = xdm.uploadEndUserPhoto(photo);
		if ((response != null) && response.isSuccessfullResponse()) {
			// Extract the Etag value in the 200 OK response
			String etag = response.getHeader("Etag");
			if (etag != null) {
				// Removed quotes
				etag = StringUtils.removeQuotes(etag);
			} else {
				etag = "" + System.currentTimeMillis();
			}

			// Set the Etag of the photo-icon
			photo.setEtag(etag);

			return true;
		} else {
			return false;
		}
	}

    /**
     * Delete photo icon
     *
     * @returns Boolean result
     */
	public boolean deletePhotoIcon(){
		// Delete the photo from the XDM server
		HttpResponse response = xdm.deleteEndUserPhoto();
		if ((response != null) && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
	}

	/**
     * Invite a contact to share its presence
     *
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
    public boolean inviteContactToSharePresence(ContactId contact) {
		// Remove contact from the blocked contacts list
		xdm.removeContactFromBlockedList(contact);

		// Remove contact from the revoked contacts list
		xdm.removeContactFromRevokedList(contact);

		// Add contact in the granted contacts list
		HttpResponse response = xdm.addContactToGrantedList(contact);
        if ((response != null) && response.isSuccessfullResponse()) {
			return true;
		} else {
			return false;
		}
    }

    /**
     * Revoke a shared contact
     *
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
    public boolean revokeSharedContact(ContactId contact){
		// Add contact in the revoked contacts list
		HttpResponse response = xdm.addContactToRevokedList(contact);
		if ((response == null) || (!response.isSuccessfullResponse())) {
			return false;
		}

		// Remove contact from the granted contacts list
		response = xdm.removeContactFromGrantedList(contact);
        if ((response != null)
                && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
    }

    /**
     * Remove a revoked contact
     *
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
	public boolean removeRevokedContact(ContactId contact) {
		// Remove contact from the revoked contacts list
		HttpResponse response = xdm.removeContactFromRevokedList(contact);
        if ((response != null)
                && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
	}

    /**
     * Remove a blocked contact
     *
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
	public boolean removeBlockedContact(ContactId contact) {
		// Remove contact from the blocked contacts list
		HttpResponse response = xdm.removeContactFromBlockedList(contact);
        if ((response != null)
                && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Address book content has changed
	 */
	public void handleAddressBookHasChanged() {
		// If a contact used to be in a RCS relationship with us but is not in the address book any more, we may have to remove or
		// unblock it
		// Get a list of all RCS numbers
		Set<ContactId> rcsNumbers = mContactsManager.getRcsContactsWithSocialPresence();
		// For each RCS number
		for (ContactId contact : rcsNumbers) {
			if (!ContactUtils.isNumberInAddressBook(contact)) {
				// If it is not present in the address book
				if (logger.isActivated()) {
					logger.debug("The RCS number " + contact + " was not found in the address book any more.");
				}

				if (mContactsManager.isNumberShared(contact)
						|| mContactsManager.isNumberInvited(contact)) {
					// Active or Invited
					if (logger.isActivated()) {
						logger.debug(contact + " is either active or invited");
						logger.debug("We remove it from the buddy list");
					}
					// We revoke it
					boolean result = revokeSharedContact(contact);
					if (result) {
						// The contact should be automatically unrevoked after a given timeout. Here the
						// timeout period is 0, so the contact can receive invitations again now
						result = removeRevokedContact(contact);
						if (result) {
							// Remove entry from rich address book provider
							mContactsManager.modifyRcsContactInProvider(contact, RcsStatus.RCS_CAPABLE);
						} else {
							if (logger.isActivated()) {
								logger.error("Something went wrong when revoking shared contact");
							}
						}
					}
				} else if (mContactsManager.isNumberBlocked(contact)) {
					// Blocked
					if (logger.isActivated()) {
						logger.debug(contact + " is blocked");
						logger.debug("We remove it from the blocked list");
					}
					// We unblock it
					boolean result = removeBlockedContact(contact);
					if (result) {
						// Remove entry from rich address book provider
						mContactsManager.modifyRcsContactInProvider(contact, RcsStatus.RCS_CAPABLE);
					} else {
						if (logger.isActivated()) {
							logger.error("Something went wrong when removing blocked contact");
						}
					}
				} else {
					if (mContactsManager.isNumberWilling(contact)) {
						// Willing
						if (logger.isActivated()) {
							logger.debug(contact + " is willing");
							logger.debug("Nothing to do");
						}
					} else {
						if (mContactsManager.isNumberCancelled(contact)) {
							// Cancelled
							if (logger.isActivated()) {
								logger.debug(contact + " is cancelled");
								logger.debug("We remove it from rich address book provider");
							}
							// Remove entry from rich address book provider
							mContactsManager.modifyRcsContactInProvider(contact, RcsStatus.RCS_CAPABLE);
						}
					}
				}
			}
		}
	}
	
	@Override
	protected boolean handleNotify(SipRequest notify) {
		
		super.handleNotify(notify);
		
	    // Get the event type
	    EventHeader eventHeader = (EventHeader)notify.getHeader(EventHeader.NAME);
	    if (eventHeader == null) {
        	if (logger.isActivated()) {
        		logger.debug("Unknown notification event type");
        	}
	    	return false;
	    }
	    
	    // Dispatch the notification to the corresponding service
	    if (eventHeader.getEventType().equalsIgnoreCase("presence.winfo")) {
	    	// Presence service
	    	if (RcsSettings.getInstance().isSocialPresenceSupported()) {
	    		getWatcherInfoSubscriber().receiveNotification(notify);
	    	}
	    	return true;
	    } else
	    if (eventHeader.getEventType().equalsIgnoreCase("presence")) {
	    	if (notify.getTo().indexOf("anonymous") == -1) {		    	
		    	// Presence service
	    		getPresenceSubscriber().receiveNotification(notify);
	    		return true;
	    	}
	    } 
	    return false;
	}
}
