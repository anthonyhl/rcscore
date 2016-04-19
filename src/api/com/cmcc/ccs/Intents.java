package com.cmcc.ccs;

import android.content.Intent;

public final class Intents {
	public static final String MESSAGE_ACTION = "com.cmcc.ccs.action.MESSAGE";
	
	private static Intent newIntent() {
		Intent sendIntent = new Intent();
		sendIntent.setAction("com.cmcc.ccs.action.MESSAGE");		
		return sendIntent;
	}
	
	public static Intent newMessage(String text) {
		Intent sendIntent = newIntent();
		sendIntent.putExtra(Intent.EXTRA_TEXT, text);
		sendIntent.setType("text/plaint");
		return sendIntent;
	}
	
	public static Intent newImage(String content) {
		Intent sendIntent = newIntent();
		sendIntent.putExtra(Intent.EXTRA_TEXT, content);
		sendIntent.setType("image/jpg");
		return sendIntent;
	}
	
	
}
