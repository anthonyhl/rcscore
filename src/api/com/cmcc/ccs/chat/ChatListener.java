package com.cmcc.ccs.chat;

public abstract class ChatListener {

	void onNewChatMessage(String Contact, ChatMessage message) {
		
	}
	
	void onReportMessageDelivered(long msgId) {
		
	}
	
	void onReportMessageFailed(long msgId, int errtype, String statusCode) {
		
	}
}
