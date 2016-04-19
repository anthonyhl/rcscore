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
package com.orangelabs.rcs.core.im.chat.cpim;

/**
 * tct-stack
 * 
 * Mcloud document
 *
 */
public class McloudDocument {
    /**
     * MIME type
     */
    public static final String MIME_TYPE = "application/cloudfile";

    public static final String MCLOUD_TAG = "cloudfile";

    public static final String FILENAME_TAG = "filename";

    public static final String FILESIZE_TAG = "filesize";

    public static final String DOWNLOADURL_TAG = "downloadurl";

    /**
     * File name
     */
    private String filename = null;

    /**
     * File size
     */
    private String filesize = null;

    /**
     * Download url
     */
    private String downloadurl = null;

    
    /**
     * Constructor
     * 
     */
    public McloudDocument() {
        
    }

    /**
     * Constructor
     * 
     * @param filename
     * @param filesize
     * @param downloadurl
     */
    public McloudDocument(String filename, String filesize, String downloadurl) {
        this.filename = filename;
        this.filesize = filesize;
        this.downloadurl = downloadurl;
    }

    /**
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * @param filename the filename to set
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * @return the filesize
     */
    public String getFilesize() {
        return filesize;
    }

    /**
     * @param filesize the filesize to set
     */
    public void setFilesize(String filesize) {
        this.filesize = filesize;
    }

    /**
     * @return the downloadurl
     */
    public String getDownloadurl() {
        return downloadurl;
    }

    /**
     * @param downloadurl the downloadurl to set
     */
    public void setDownloadurl(String downloadurl) {
        this.downloadurl = downloadurl;
    }

}
