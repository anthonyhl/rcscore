package com.cmcc.ccs.groupchat;

import java.util.Set;

import com.gsma.services.rcs.RcsServiceException;

public class GroupChat {
	
	
	public boolean deleteMessage(long msgId) {
		return false;
	}
	
	public String getChatId() {
		return null;
	}
	
	public String getSubject() {
		return null;
	}
	
	public Set<String> getParticipants() {
		return null;		
	}
	
	public String getChairman() {
		return null;
		
	}
	
	public void setChairman(String contact) {
		
	}
	
	public void modifySubject(String subject) {
		
	}
	
	public int getState() {
		return 0;
	}
	
	public void acceptInvitation() {
		
	}
	
	public void rejectInvitation() {
		
	}
	
	public String sendMessage(String message){
		return null;
	}
	
	public void addParticipants(Set<String> participants) {
		
	}
	
	public void removeParticipants (Set<String> participants) {
		
	}
	
	public void quitConversation() {
		
	}
	
	public boolean isChairmen() {
		return false;
	}
	
	public void getPortrait(Set<String> participants) {
		
	}
	
	public void abortConversation(){
		
	}
	
	public boolean setMessageRead(long msgId) {
		return false;
	}
	
	public boolean setMessageFavorite(long msgId) {
		return false;
	}
	
    /**
	 * Registers a chat invitation listener
	 * 
	 * @param listener New chat listener
	 * @throws RcsServiceException
	 */
	public void addEventListener(GroupChatListener listener) throws RcsServiceException {
		
	}

	/**
	 * Unregisters a chat invitation listener
	 * 
	 * @param listener New chat listener
	 * @throws RcsServiceException
	 */
	public void removeEventListener(GroupChatListener listener) throws RcsServiceException {
		
	} 
}
