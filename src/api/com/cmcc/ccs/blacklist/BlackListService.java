package com.cmcc.ccs.blacklist;

import android.content.Context;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.RcsServiceListener;

public class BlackListService extends RcsService {

	public BlackListService(Context ctx, RcsServiceListener listener) {
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
	
	public boolean addBlackNumber(String contact, String name) {
		return false;	
	}
	
	public boolean removeBlackNumber(String contact) {
		return false;
	}

}
