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

package com.gsma.services.rcs.contacts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.IInterface;
import android.provider.ContactsContract;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceException;
import com.gsma.services.rcs.RcsServiceListener;
import com.gsma.services.rcs.RcsServiceNotAvailableException;

/**
 * Contacts service offers additional methods to manage RCS info in the local
 * address book.
 *
 * The parameter contact in the API supports the following formats: MSISDN in
 * national or international format, SIP address, SIP-URI or Tel-URI.
 *
 * @author Jean-Marc AUFFRET
 */
public class ContactsService extends RcsService {
    /**
     * API
     */
    private IContactsService api = null;

    /**
     * Constructor
     *
     * @param ctx Application context
     * @param listener Service listener
     */
    public ContactsService(Context ctx, RcsServiceListener listener) {
        super(ctx, listener);
    }

    /**
     * Connects to the API
     */
    public void connect() {
        bindService(IContactsService.class, apiConnection);
    }

    /**
     * Disconnects from the API
     */
    public void disconnect() {
        try {
            ctx.unbindService(apiConnection);
        } catch (IllegalArgumentException e) {
            // Nothing to do
        }
    }

    /**
     * Set API interface
     *
     * @param api API interface
     */
    protected void setApi(IInterface api) {
        super.setApi(api);

        this.api = (IContactsService)api;
    }

    /**
     * Service connection
     */
    private ServiceConnection apiConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            setApi(IContactsService.Stub.asInterface(service));
            if (serviceListener != null) {
                serviceListener.onServiceConnected();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            setApi(null);
            if (serviceListener != null) {
                serviceListener.onServiceDisconnected(Error.CONNECTION_LOST);
            }
        }
    };

    /**
     * Returns the rcs contact infos from its contact ID (i.e. MSISDN)
     *
     * @param contact Contact ID
     * @return RcsContact
     * @throws RcsServiceException
     * @see RcsContact
     */
    public RcsContact getRcsContact(ContactId contact) throws RcsServiceException {
        if (api != null) {
            try {
                return api.getRcsContact(contact);
            } catch (Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    /**
     * Returns the list of rcs contacts
     *
     * @return List of contacts
     * @throws RcsServiceException
     * @see RcsContact
     */
    public Set<RcsContact> getRcsContacts() throws RcsServiceException {
        if (api != null) {
            try {
                Set<RcsContact> result = new HashSet<RcsContact>();
                List<RcsContact> contacts = api.getRcsContacts();
                for (int i = 0; i < contacts.size(); i++) {
                    RcsContact contact = contacts.get(i);
                    result.add(contact);
                }
                return result;
            } catch (Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    /**
     * Returns the list of online contacts (i.e. registered)
     *
     * @return List of contacts
     * @throws RcsServiceException
     * @see RcsContact
     */
    public Set<RcsContact> getRcsContactsOnline() throws RcsServiceException {
        if (api != null) {
            try {
                Set<RcsContact> result = new HashSet<RcsContact>();
                result.addAll(api.getRcsContactsOnline());
                return result;
            } catch (Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    /**
     * Returns the list of contacts supporting a given extension or service ID
     *
     * @param serviceId Service ID
     * @return List of contacts
     * @throws RcsServiceException
     * @see RcsContact
     */
    public Set<RcsContact> getRcsContactsSupporting(String serviceId) throws RcsServiceException {
        if (api != null) {
            try {
                Set<RcsContact> result = new HashSet<RcsContact>();
                result.addAll(api.getRcsContactsSupporting(serviceId));
                return result;
            } catch (Exception e) {
                throw new RcsServiceException(e.getMessage());
            }
        } else {
            throw new RcsServiceNotAvailableException();
        }
    }

    /**
     * Returns the vCard of a contact. The method returns the complete filename
     * including the path of the visit card. The filename has the file extension
     * ".vcf" and is generated from the native address book vCard URI (see
     * Android SDK attribute ContactsContract.Contacts.CONTENT_VCARD_URI which
     * returns the referenced contact formatted as a vCard when opened through
     * openAssetFileDescriptor(Uri, String)).
     *
     * @param ctx Application context
     * @param contactUri Contact URI of the contact in the native address book
     * @return Filename of vCard
     * @throws RcsServiceException
     */
    public static String getVCard(Context ctx, Uri contactUri) throws RcsServiceException {
        Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query(contactUri, null, null, null, null);
            int displayNameColIdx = cursor
                    .getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int lookupKeyColIdx = cursor
                    .getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY);
            if (!cursor.moveToFirst()) {
                return null;
            }
            String lookupKey = cursor.getString(lookupKeyColIdx);
            Uri vCardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI,
                    lookupKey);
            AssetFileDescriptor fd = ctx.getContentResolver()
                    .openAssetFileDescriptor(vCardUri, "r");

            FileInputStream fis = fd.createInputStream();
            byte[] vCardData = new byte[(int)fd.getDeclaredLength()];
            fis.read(vCardData);

            String name = cursor.getString(displayNameColIdx);
            String fileName = new StringBuilder(Environment.getExternalStorageDirectory()
                    .toString()).append(File.separator).append(name).append(".vcf").toString();
            File vCardFile = new File(fileName);
            if (vCardFile.exists()) {
                vCardFile.delete();
            }

            FileOutputStream fos = new FileOutputStream(vCardFile, true);
            fos.write(vCardData);
            fos.close();

            return fileName;

        } catch (IOException e) {
            throw new RcsServiceException(e.getMessage());

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
