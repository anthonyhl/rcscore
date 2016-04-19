package com.cmcc.ccs.groupchat;

import java.util.Set;

import android.content.Context;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceException;
import com.gsma.services.rcs.RcsServiceListener;
import com.cmcc.ccs.groupchat.GroupChat;
import com.cmcc.ccs.groupchat.GroupChatListener;

public class GroupChatService extends RcsService {
	
	public static final int INVITED = 0;
	public static final int INITIATED = 1;
	public static final int STARTED = 2;
	public static final int TERMINATED = 3;
	public static final int ABORTED = 4;
	public static final int FAILED = 5;

	public GroupChatService(Context ctx, RcsServiceListener listener) {
		super(ctx, listener);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void connect() {
		// TODO Auto-generated method stub

	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub

	}

	
    /**
     * Initiates a group chat with a group of contact and returns a GroupChat
     * instance. The subject is optional and may be null.
     * 
     * @param contact List of contacts
     * @param subject Subject
     * @param listener Chat event listener
     * @throws RcsServiceException
	 * @throws JoynContactFormatException
     */
    public GroupChat initiateGroupChat(Set<String> contacts, String subject, GroupChatListener listener) 
    		throws RcsServiceException {
				return null;
    }
    
    /**
     * Rejoins an existing group chat from its unique chat ID
     * 
     * @param chatId Chat ID
     * @return Group chat
     * @throws RcsServiceException
     */
    public GroupChat rejoinGroupChat(String chatId) throws RcsServiceException {
		return null;
    }
           
    /**
     * Returns a group chat in progress from its unique ID
     * 
     * @param chatId Chat ID
     * @return Group chat or null if not found
     * @throws RcsServiceException
     */
    public GroupChat getGroupChat(String chatId) throws RcsServiceException {
		return null;

    }
    

    /**
	 * Registers a chat invitation listener
	 * 
	 * @param listener New chat listener
	 * @throws RcsServiceException
	 */
	public void addNewChatListener(NewGroupChatListener listener) throws RcsServiceException {

	}

	/**
	 * Unregisters a chat invitation listener
	 * 
	 * @param listener New chat listener
	 * @throws RcsServiceException
	 */
	public void removeNewChatListener(NewGroupChatListener listener) throws RcsServiceException {

	}    
}
