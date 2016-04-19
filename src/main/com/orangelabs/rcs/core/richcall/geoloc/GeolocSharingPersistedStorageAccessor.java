/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.orangelabs.rcs.core.richcall.geoloc;

import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.chat.ChatLog.Message.MimeType;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.gsh.GeolocSharingLog;
import com.orangelabs.rcs.provider.sharing.RichCallHistory;
import com.orangelabs.rcs.utils.ContactUtils;

import android.database.Cursor;

/**
 * GeolocSharingPersistedStorageAccessor helps in retrieving persisted data
 * related to a geoloc sharing from the persisted storage. It can utilize
 * caching for such data that will not be changed after creation of the geoloc
 * sharing to speed up consecutive access.
 */
public class GeolocSharingPersistedStorageAccessor {

    private final String mSharingId;

    private final RichCallHistory mRichCallLog;

    private ContactId mContact;

    private Geoloc mGeoloc;

    /**
     * TODO: Change type to enum in CR031 implementation
     */
    private Integer mDirection;

    public GeolocSharingPersistedStorageAccessor(String sharingId, RichCallHistory richCallHistory) {
        mSharingId = sharingId;
        mRichCallLog = richCallHistory;
    }

    public GeolocSharingPersistedStorageAccessor(String sharingId, ContactId contact,
            Geoloc geoloc, int direction, RichCallHistory richCallHistory) {
        mSharingId = sharingId;
        mContact = contact;
        mGeoloc = geoloc;
        mDirection = direction;
        mRichCallLog = richCallHistory;
    }

    private void cacheData() {
        Cursor cursor = null;
        try {
            cursor = mRichCallLog.getCacheableGeolocSharingData(mSharingId);
            String contact = cursor
                    .getString(cursor.getColumnIndexOrThrow(GeolocSharingLog.CONTACT));
            if (contact != null) {
                mContact = ContactUtils.createContactId(contact);
            }
            mDirection = cursor.getInt(cursor.getColumnIndexOrThrow(GeolocSharingLog.DIRECTION));
            String geoloc = cursor.getString(cursor.getColumnIndexOrThrow(GeolocSharingLog.CONTENT));
            if (geoloc != null) {
                mGeoloc = new Geoloc(geoloc);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public ContactId getRemoteContact() {
        /*
         * Utilizing cache here as contact can't be changed in persistent
         * storage after entry insertion anyway so no need to query for it
         * multiple times.
         */
        if (mContact == null) {
            cacheData();
        }
        return mContact;
    }

    public Geoloc getGeoloc() {
        /*
         * Utilizing cache here as geoloc can't be changed in persistent storage
         * after geoloc has been set anyway so no need to query for it multiple
         * times.
         */
        if (mGeoloc == null) {
            cacheData();
        }
        return mGeoloc;
    }

    public String getMimeType() {
        return MimeType.GEOLOC_MESSAGE;
    }

    public int getState() {
        return mRichCallLog.getGeolocSharingState(mSharingId);
    }

    public int getReasonCode() {
        return mRichCallLog.getGeolocSharingStateReasonCode(mSharingId);
    }

    public int getDirection() {
        /*
         * Utilizing cache here as direction can't be changed in persistent
         * storage after entry insertion anyway so no need to query for it
         * multiple times.
         */
        if (mDirection == null) {
            cacheData();
        }
        return mDirection;
    }

    public void setStateAndReasonCode(int state, int reasonCode) {
        mRichCallLog.setGeolocSharingStateAndReasonCode(mSharingId, state, reasonCode);
    }

    public void setTransferred(Geoloc geoloc) {
        mRichCallLog.setGeolocSharingTransferred(mSharingId, geoloc);
    }

    public void addIncomingGeolocSharing(ContactId contact, int state, int reasonCode) {
        mRichCallLog.addIncomingGeolocSharing(mContact, mSharingId, state, reasonCode);
    }
}
