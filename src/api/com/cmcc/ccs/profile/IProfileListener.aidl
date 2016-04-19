package com.cmcc.ccs.profile;

interface IProfileListener {
	void onUpdateProfile(in int resultType);
	void onGetProfile(in int resultType);
}