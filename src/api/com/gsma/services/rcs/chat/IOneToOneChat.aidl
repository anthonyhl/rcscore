package com.gsma.services.rcs.chat;

import com.gsma.services.rcs.chat.IChatMessage;
import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.Mcloud;
import com.gsma.services.rcs.Vemoticon;
import com.gsma.services.rcs.contacts.ContactId;

/**
 * One-to-One Chat interface
 */
interface IOneToOneChat {

	ContactId getRemoteContact();

	IChatMessage sendMessage(in String message);

	void sendIsComposingEvent(in boolean status);

	IChatMessage sendMessage2(in Geoloc geoloc);

	void openChat();

    IChatMessage sendMessageExt(in List<ContactId> contacts, in String message);

    IChatMessage sendMcloudMessage(in List<ContactId> contacts, in Mcloud mcloud);

    IChatMessage sendVemoticonMessage(in List<ContactId> contacts, in Vemoticon vemoticon);
}