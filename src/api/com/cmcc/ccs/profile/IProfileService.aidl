package com.cmcc.ccs.profile;

import com.gsma.services.rcs.IRcsServiceRegistrationListener;
import com.cmcc.ccs.profile.IProfileListener;

/**
 * Profile service API
 */
interface IProfileService {

	boolean isServiceRegistered();
	void addServiceRegistrationListener(IRcsServiceRegistrationListener listener);
	void removeServiceRegistrationListener(IRcsServiceRegistrationListener listener); 

	void setProfileInfo(in Map profile);
	void getProfileInfo();
	
	void addProfileListener(IProfileListener listener);
    
    void removeProfileListener(IProfileListener listener);
}
