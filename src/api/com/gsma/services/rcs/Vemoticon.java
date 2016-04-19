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

package com.gsma.services.rcs;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

import com.gsma.services.rcs.contacts.ContactId;

/**
 * tct-stack
 * 
 * Vemoticon class
 * 
 */
public class Vemoticon implements Parcelable, Serializable {

    private static final long serialVersionUID = 0L;

    private ContactId contact; // no use??

    private final String smsText;

    private final String eid;

    /**
     * Constructor
     * 
     * @param smsText
     * @param eid
     */
    public Vemoticon(String smsText, String eid) {
        this.smsText = smsText;
        this.eid = eid;
    }

    /**
     * Constructor
     * 
     * @param source Parcelable source
     * @hide
     */
    public Vemoticon(Parcel source) {
        smsText = source.readString();
        eid = source.readString();
    }

    /**
     * Describe the kinds of special objects contained in this Parcelable's
     * marshalled representation.
     * 
     * @return Integer
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * Write parcelable object.
     * 
     * @param dest The Parcel in which the object should be written
     * @param flags Additional flags about how the object should be written
     * @hide
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(smsText);
        dest.writeString(eid);
    }

    /**
     * Parcelable creator.
     * 
     * @hide
     */
    public static final Parcelable.Creator<Vemoticon> CREATOR = new Parcelable.Creator<Vemoticon>() {
        public Vemoticon createFromParcel(Parcel source) {
            return new Vemoticon(source);
        }

        public Vemoticon[] newArray(int size) {
            return new Vemoticon[size];
        }
    };

    public String getSmsText() {
        return smsText;
    }

    public String getEid() {
        return eid;
    }

    /**
     * Returns the Vemoticon in provider format.
     * 
     * @return String
     */
    @Override
    public String toString() {
        return new StringBuilder(smsText).append(", ").append(eid).toString();
    }

}
