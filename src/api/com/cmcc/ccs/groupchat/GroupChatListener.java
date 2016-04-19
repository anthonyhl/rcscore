package com.cmcc.ccs.groupchat;

import com.cmcc.ccs.chat.ChatMessage;
import com.cmcc.ccs.groupchat.GroupChat;

public abstract class GroupChatListener {
	/**
	 * Callback called when the session is well established and messages
	 * may be exchanged with the group of participants
	 */
	public abstract void onSessionStarted();
	
	/**
	 * Callback called when the session has been aborted or terminated
	 */
	public abstract void onSessionAborted(int errType, String statusCode);

	/**
	 * Callback called when the session has failed
	 * 
	 * @param error Error
	 * @see GroupChat.Error
	 */
	public abstract void onSessionError(int error);
	
	/**
	 * Callback called when a new message has been received
	 * 
	 * @param message New chat message
	 * @see ChatMessage
	 */
	public abstract void onNewMessage(ChatMessage message);

	/**
	 * Callback called when a message has been delivered to the remote
	 * 
	 * @param msgId Message ID
	 */
	public abstract void onReportMessageDelivered(String msgId);

	
	/**
	 * Callback called when a message has failed to be delivered to the remote
	 * 
	 * @param msgId Message ID
	 */
	public abstract void onReportMessageFailed(String msgId, int errType, String statusCode);

	/**
	 * Callback called when a new participant has joined the group chat
	 *  
	 * @param contact Contact
	 * @param contactDisplayname Contact displayname
	 */
	public abstract void onParticipantJoined(String contact);
	
	/**
	 * Callback called when a participant has left voluntary the group chat
	 *  
	 * @param contact Contact
	 */
	public abstract void onParticipantLeft(String contact);
	
	public abstract void onChairmenChanged(String contact, int errType, String statusCode);
	
	public abstract void onMeRemoved();
	
	public abstract void onPortraitUpdate(int errType, String statusCode);
	
	public abstract void onInviteParticipants (int errType, String statusCode);
	
	public abstract void onRemoveParticipants (int errType, String statusCode);
	
	public abstract void onQuitConversation (int errType, String statusCode);
}
