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
 * MCloud class
 * 
 */
public class Mcloud implements Parcelable, Serializable {

    private static final long serialVersionUID = 0L;

    private ContactId contact; // no use??

    private final String fileName;

    private final String fileSize;

    private final String downloadUrl;

    /**
     * Constructor
     * 
     * @param fileName
     * @param fileSize
     * @param downloadUrl
     */
    public Mcloud(String fileName, String fileSize, String downloadUrl) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.downloadUrl = downloadUrl;
    }

    /**
     * Constructor
     * 
     * @param source Parcelable source
     * @hide
     */
    public Mcloud(Parcel source) {
        fileName = source.readString();
        fileSize = source.readString();
        downloadUrl = source.readString();
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
        dest.writeString(fileName);
        dest.writeString(fileSize);
        dest.writeString(downloadUrl);
    }

    /**
     * Parcelable creator.
     * 
     * @hide
     */
    public static final Parcelable.Creator<Mcloud> CREATOR = new Parcelable.Creator<Mcloud>() {
        public Mcloud createFromParcel(Parcel source) {
            return new Mcloud(source);
        }

        public Mcloud[] newArray(int size) {
            return new Mcloud[size];
        }
    };

    public String getFileName() {
        return fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * Returns the MCloud in provider format.
     * 
     * @return String
     */
    @Override
    public String toString() {
        // XXX通过彩云给您发送了一个文件（日程表.xls，大小36KB，下载地址http://abc.com）
        return new StringBuilder(fileName).append(", ").append(fileSize).append(", ").append(downloadUrl).toString();
    }

}
