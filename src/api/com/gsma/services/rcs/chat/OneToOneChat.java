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

package com.gsma.services.rcs.chat;

import java.util.ArrayList;
import java.util.Set;

import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.Mcloud;
import com.gsma.services.rcs.RcsServiceException;
import com.gsma.services.rcs.Vemoticon;
import com.gsma.services.rcs.contacts.ContactId;

/**
 * One-to-One Chat
 * 
 * @author Jean-Marc AUFFRET
 */
public class OneToOneChat {

	/**
	 * Chat interface
	 */
	private final IOneToOneChat mOneToOneChatInf;

	/**
	 * Constructor
	 * 
	 * @param chatIntf Chat interface
	 */
	/* package private */ OneToOneChat(IOneToOneChat chatIntf) {
		mOneToOneChatInf = chatIntf;
	}

	/**
	 * Returns the remote contact
	 * 
	 * @return ContactId
	 * @throws RcsServiceException
	 */
	public ContactId getRemoteContact() throws RcsServiceException {
		try {
			return mOneToOneChatInf.getRemoteContact();
		} catch (Exception e) {
			throw new RcsServiceException(e.getMessage());
		}
	}

	/**
	 * Sends a chat message
	 * 
	 * @param message Message
	 * @return Chat message
	 * @throws RcsServiceException
	 */
	public ChatMessage sendMessage(String message) throws RcsServiceException {
		try {
			return new ChatMessage(mOneToOneChatInf.sendMessage(message));
		} catch (Exception e) {
			throw new RcsServiceException(e.getMessage());
		}
	}

	/**
	 * Sends a geoloc message
	 * 
	 * @param geoloc Geoloc info
	 * @return Chat message
	 * @throws RcsServiceException
	 */
	public ChatMessage sendMessage(Geoloc geoloc) throws RcsServiceException {
		try {
			return new ChatMessage(mOneToOneChatInf.sendMessage2(geoloc));
		} catch (Exception e) {
			throw new RcsServiceException(e.getMessage());
		}
	}

	/**
	 * Sends an Is-composing event. The status is set to true when typing a
	 * message, else it is set to false.
	 * 
	 * @param status Is-composing status
	 * @throws RcsServiceException
	 */
	public void sendIsComposingEvent(boolean status) throws RcsServiceException {
		try {
			mOneToOneChatInf.sendIsComposingEvent(status);
		} catch (Exception e) {
			throw new RcsServiceException(e.getMessage());
		}
	}

	/**
	 * open the chat conversation.<br>
	 * Note: if it is an incoming pending chat session and the parameter IM SESSION START is 0 then the session is accepted now.
	 * 
	 * @throws RcsServiceException
	 */
	public void openChat() throws RcsServiceException {
		try {
			mOneToOneChatInf.openChat();
		} catch (Exception e) {
			throw new RcsServiceException(e.getMessage());
		}
	}


    /**********************************************************************************
     *  tct-stack add for CMCC message modes
     **********************************************************************************/

	/**
     * Sends a chat message extension
     * 
     * @param contacts ContactIds
     * @param message Message
     * @return Chat message
     * @throws RcsServiceException
     */
    public ChatMessage sendMessageExt(Set<ContactId> contacts, String message) throws RcsServiceException {
        try {
            return new ChatMessage(mOneToOneChatInf.sendMessageExt(new ArrayList<ContactId>(contacts), message));
        } catch (Exception e) {
            throw new RcsServiceException(e.getMessage());
        }
    }

    /**
     * Sends a mcloud message
     * 
     * @param contacts ContactIds
     * @param mcloud Mcloud
     * @return Chat message
     * @throws RcsServiceException
     */
    public ChatMessage sendMessageExt(Set<ContactId> contacts, Mcloud mcloud) throws RcsServiceException {
        try {
            return new ChatMessage(mOneToOneChatInf.sendMcloudMessage(new ArrayList<ContactId>(contacts), mcloud));
        } catch (Exception e) {
            throw new RcsServiceException(e.getMessage());
        }
    }

    /**
     * Sends a vemoticon message
     * 
     * @param contacts ContactIds
     * @param vemoticon Vemoticon
     * @return Chat message
     * @throws RcsServiceException
     */
    public ChatMessage sendMessageExt(Set<ContactId> contacts, Vemoticon vemoticon) throws RcsServiceException {
        try {
            return new ChatMessage(mOneToOneChatInf.sendVemoticonMessage(new ArrayList<ContactId>(contacts), vemoticon));
        } catch (Exception e) {
            throw new RcsServiceException(e.getMessage());
        }
    }

}
