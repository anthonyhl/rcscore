package com.orangelabs.rcs.core.im.chat.standalone;

/**
 * @tct-stack; RCS SPEC 5.2   3.2.4.1.3
 * 
 * Pager Mode: “urn:urn-7:3gpp-service.ims.icsi.oma.cpm.msg”
 * Large Message Mode: “urn:urn-7:3gpp-service.ims.icsi.oma.cpm.largemsg”
 * Deferred Delivery: “urn:urn-7:3gpp-service.ims.icsi.oma.cpm.deferred”
 */
public class MessagingServiceIdentifier {

    /**
     * P-Preferred-Identity header to indicate Messaging Service
     */
    public static final String HEADER_P_PREFERRED_SERVICE = "P-Preferred-Service";

    public static final String PAGER_MODE = "urn:urn-7:3gpp-service.ims.icsi.oma.cpm.msg";
    public static final String LARGE_MESSAGE_MODE = "urn:urn-7:3gpp-service.ims.icsi.oma.cpm.largemsg";
    public static final String SESSION_MODE = "urn:urn-7:3gpp-service.ims.icsi.oma.cpm.session";
    public static final String FILE_TRANSFER = "urn:urn-7:3gpp-service.ims.icsi.oma.cpm.filetransfer";
    public static final String DEFERED_DELIVERY = "urn:urn-7:3gpp-service.ims.icsi.oma.cpm.deferred";

}
