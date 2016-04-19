package com.orangelabs.rcs.core.registration;


public interface RegistrationListener {

	public abstract void handleRegistrationTerminated();

	public abstract void handleRegistrationFailed(ImsError error);

	public abstract void handleRegistrationSuccessful();

}