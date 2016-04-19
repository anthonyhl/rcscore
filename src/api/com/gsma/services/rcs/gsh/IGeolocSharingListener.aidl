package com.gsma.services.rcs.gsh;

import com.gsma.services.rcs.contacts.ContactId;

/**
 * Callback methods for geoloc sharing events
 */
interface IGeolocSharingListener {

	void onStateChanged(in ContactId contact, in String sharingId, in int state, in int reasonCode);

	void onProgressUpdate(in ContactId contact, in String sharingId, in long currentSize, in long totalSize);
}