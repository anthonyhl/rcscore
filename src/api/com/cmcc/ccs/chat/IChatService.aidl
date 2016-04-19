package com.cmcc.ccs.chat;

import com.gsma.services.rcs.IRcsServiceRegistrationListener;
import com.gsma.services.rcs.chat.IOneToOneChatListener;
import com.gsma.services.rcs.chat.IOneToOneChat;
import com.gsma.services.rcs.chat.IGroupChatListener;
import com.gsma.services.rcs.chat.IGroupChat;
import com.gsma.services.rcs.chat.ChatServiceConfiguration;
import com.cmcc.ccs.chat.ChatMessage;

/**
 * Chat service API
 */
interface IChatService {
	boolean isServiceRegistered();    
	void addServiceRegistrationListener(IRcsServiceRegistrationListener listener);
	void removeServiceRegistrationListener(IRcsServiceRegistrationListener listener); 
	
	long sendMessage(String contact, String message);
	
	ChatMessage getChatMessage(long msgId);
	
	String sendOTMMessage(in List<String> Contacts, String message);
	
	String resendMessage(long msgId);
	
	boolean deleteMessage(long msgId);
	
	boolean setMessageRead(long msgId);
	
	ChatServiceConfiguration getConfiguration();
    
	IOneToOneChat openSingleChat(in String contact, in IOneToOneChatListener listener);   
    
	IOneToOneChat getChat(in String contact);

	List<IBinder> getChats();

	List<IBinder> getGroupChats();
    
	IGroupChat getGroupChat(in String chatId);
	
	int getServiceVersion();
}