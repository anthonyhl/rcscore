package com.cmcc.ccs.chat;

import java.util.Date;
import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;

public class ChatMessage implements Parcelable{


	public String getContact() {
		return null;
	}
	
	public Set<String> getContacts() {
		return null;
	}
	
	public String getId() {
		return null;
	}
	
	public Date getReceiptDate() {
		return null;
	}
	
	public String getMessage() {
		return null;
	}
	
	public ChatMessage(Parcel source) {
		// TODO Auto-generated constructor stub
	}


	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub
		
	}
	
	public static final Parcelable.Creator<ChatMessage> CREATOR =
			new Parcelable.Creator<ChatMessage>() {
		public ChatMessage createFromParcel(Parcel source) {
			return new ChatMessage(source);
		}

		public ChatMessage[] newArray(int size) {
			return new ChatMessage[size];
		}
	};
}
