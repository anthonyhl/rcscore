
package com.orangelabs.rcs.core.content;

import android.net.Uri;

/**
 * Visit Calendar content
 * 
 * @author fang.wu
 */
public class VCalendarContent extends MmContent {
    /**
     * Constructor
     * 
     * @param vCalendarFile URI
     * @param encoding the mime-type encoding
     * @param size Content size
     * @param fileName The filename
     */
    public VCalendarContent(Uri vCalendarFile, String encoding, long size, String fileName) {
        super(vCalendarFile, encoding, size, fileName);
    }
}
