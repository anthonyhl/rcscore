package com.orangelabs.rcs.service;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;

import com.gsma.services.rcs.RcsService;
import com.orangelabs.rcs.R;
import com.orangelabs.rcs.platform.registry.AndroidRegistryFactory;
import com.orangelabs.rcs.provider.BackupRestoreDb;
import com.orangelabs.rcs.provider.LocalContentResolver;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.IntentUtils;
import com.orangelabs.rcs.utils.StringIgnoreCase;
import com.orangelabs.rcs.utils.logger.Logger;

public class UserAccountManager {

	/**
	 * Current User account
	 */
	private String currentUser;

	/**
	 * Last User account
	 */
	private String lastUser;
	
	private Context ctx;
	
	private LocalContentResolver mLocalContentResolver;
	private RcsSettings settings;
	private ContactsManager contactsManager;
	
	/**
	 * Account has been manualy deleted
	 */
	private static final String REGISTRY_RCS_ACCOUNT_MANUALY_DELETED = "RcsAccountManualyDeleted";

    /**
     * Account manager type
     */
    public static final String ACCOUNT_MANAGER_TYPE = "com.orangelabs.rcs";
	
	/**
	 * The logger
	 */
	final static Logger logger = Logger.getLogger(UserAccountManager.class.getSimpleName());

	public UserAccountManager(Context ctx,
			RcsSettings settings, 
			LocalContentResolver localContentResolver,
			ContactsManager contactsManager) {
		this.ctx = ctx;
		this.mLocalContentResolver = localContentResolver;
		this.settings = settings;
		this.contactsManager = contactsManager;
		
	}

	boolean checkAccount() {    
	    // Read the current and last end user account
	    currentUser = LauncherUtils.getCurrentUserAccount(ctx);
	    lastUser = LauncherUtils.getLastUserAccount(ctx);
	    if (logger.isActivated()) {
	        logger.info("Last user account is " + lastUser);
	        logger.info("Current user account is " + currentUser);
	    }
	
	    // Check the current SIM
	    if (currentUser == null) {
	        if ((lastUser == null)) {
	            // If it's a first launch the IMSI is necessary to initialize the service the first time
	            return false;
	        } else {
	            // Set the user account ID from the last used IMSI
	            currentUser = lastUser;
	        }
	    }
	
	    // On the first launch and if SIM card has changed
	    if (isFirstLaunch()) {
	        // Set new user flag
	    	setNewUserAccount(true);
	    } else if (hasChangedAccount()) {
	    	// keep a maximum of saved accounts
			BackupRestoreDb.cleanBackups(currentUser);
	    	// Backup last account settings
	    	if (lastUser != null) {
	    		if (logger.isActivated()) {
	    			logger.info("Backup " + lastUser);
	    		}
	    		BackupRestoreDb.backupAccount(lastUser);
	    	}
	    	
	        // Reset RCS account 
	        LauncherUtils.resetRcsConfig(ctx, mLocalContentResolver);
	
	        // Restore current account settings
			if (logger.isActivated()) {
				logger.info("Restore " + currentUser);
			}
			BackupRestoreDb.restoreAccount(currentUser);
			// Send service provisioned intent as the configuration settings
			// are now loaded by means of restoring previous values that were backed
			// up during SIM Swap.
			broadcastServiceProvisioned();
	
	        // Activate service if new account
	        settings.setServiceActivationState(true);
	
	        // Set new user flag
	        setNewUserAccount(true);
	    } else {
	        // Set new user flag
	        setNewUserAccount(false);
	    }
	    
	    
	    
	    if (isAccountDisable()) {
	    	return false;
	    }
	    
	    recreateAccount();
	
	    // Save the current end user account
	    LauncherUtils.setLastUserAccount(ctx, currentUser);
	
	    return true;
	}

	void setNewUserAccount(boolean b) {
		getRegistry().setNewUserAccount(b);		
	}



	/**
	 * Is the first RCs is launched ?
	 *
	 * @return true if it's the first time RCS is launched
	 */
	boolean isFirstLaunch() {
	    return (lastUser == null);
	}

	/**
	 * Check if RCS account has changed since the last time we started the service
	 *
	 * @return true if the active account was changed
	 */
	boolean hasChangedAccount() {
		
		return isFirstLaunch() || !StringIgnoreCase.equals(lastUser, currentUser);
	}

	void broadcastServiceProvisioned() {
		Intent serviceProvisioned = new Intent(RcsService.ACTION_SERVICE_PROVISIONED);
		IntentUtils.tryToSetReceiverForegroundFlag(serviceProvisioned);
		ctx.sendBroadcast(serviceProvisioned);
	}
	
    /**
     * Get the account for the specified name
     * 
     * @param context The context
     * @param username The username
     * @return The account
     */
    public Account getAccount(Context context, String username) {
        AccountManager accountManager = AccountManager.get(context);
        Account[] mAccounts = accountManager.getAccountsByType(ACCOUNT_MANAGER_TYPE);
        Account mAccount = null;
        int length = mAccounts.length;
        for (int i = 0; i < length; i++) {
            if (username.equals(mAccounts[i].name)) {
                mAccount = mAccounts[i];
                break;
            }
        }
        return mAccount;
    }

    /**
     * Create the RCS account if it does not already exist
     * 
     * @param context The context
     * @param localContentResolver Local content resolver
     * @param username The username
     * @param enableSync true to enable synchronization
     * @param showUngroupedContacts true to show ungrouped contacts
     */
    public void createRcsAccount(String username, boolean enableSync) {    	
        // Save the account info into the AccountManager if needed
        Account mAccount = getAccount(ctx, username);
        if (mAccount == null) {
            mAccount = new Account(username, ACCOUNT_MANAGER_TYPE);
            AccountManager accountManager = AccountManager.get(ctx);
            boolean resource = accountManager.addAccountExplicitly(mAccount, null, null);
            if (!resource) {
            	if (logger.isActivated()){
            		logger.error("Unable to create account for " + username);
            	}
                return;
            }
        }

        // Set contacts sync for this account.
        if (enableSync){
        	ContentResolver.setIsSyncable(mAccount, ContactsContract.AUTHORITY, 1);
        }
        ContentResolver.setSyncAutomatically(mAccount, ContactsContract.AUTHORITY, enableSync);

        ContentValues contentValues = new ContentValues();
        contentValues.put(Groups.ACCOUNT_NAME, username);
        contentValues.put(Groups.ACCOUNT_TYPE, ACCOUNT_MANAGER_TYPE);
        contentValues.put(Groups.GROUP_VISIBLE, false);

        ctx.getContentResolver().insert(Groups.CONTENT_URI, contentValues);

        // Create the "Me" item
        ContactsManager.getInstance().createMyContact();
    }

    /**
     * Check if sync is enabled.
     * 
     * @param context The context
     * @param username The username
     */
    public boolean isSyncEnabled(String username) {
        Account mAccount = getAccount(ctx, username);
        if (mAccount == null) {
            return false;
        }
        return ContentResolver.getSyncAutomatically(mAccount, ContactsContract.AUTHORITY);
    }

    /**
     * Remove all RCS accounts with the exception of the excludeUsername account
     * 
     * @param ctx The context
     * @param excludeUsername The username for which the account should not be
     *            removed (can be null)
     */
    public void removeRcsAccount(String excludeUsername) {
        AccountManager accountManager = AccountManager.get(ctx);
        Account[] mAccounts = accountManager.getAccountsByType(ACCOUNT_MANAGER_TYPE);
        int length = mAccounts.length;
        for (int i = 0; i < length; i++) {
            if (!mAccounts[i].name.equals(excludeUsername)) {
                accountManager.removeAccount(mAccounts[i], null, null);
            }
        }
    }
	

	public boolean isAccountDisable() {
		Account account = getAccount(ctx,
                ctx.getString(R.string.rcs_core_account_username));
		return account == null && AccountChangedReceiver.isAccountResetByEndUser();
	}
	
	public void recreateAccount() {
        // Check if the RCS account exists
        Account account = getAccount(ctx,
                ctx.getString(R.string.rcs_core_account_username));
        if (account == null) {
        	
            // No account exists 
            if (logger.isActivated()) {
                logger.debug("The RCS account does not exist");
            }
            if (isAccountResetByEndUser()) {
                // It was manually destroyed by the user
                if (logger.isActivated()) {
                    logger.debug("It was manually destroyed by the user, we do not recreate it");
                }
            } else {
                if (logger.isActivated()) {
                    logger.debug("Recreate a new RCS account");
                }
                createRcsAccount(ctx.getString(R.string.rcs_core_account_username), true);
            }
        } else {
            // Account exists: checks if it has changed
            if (lastUser != null && !StringIgnoreCase.equals(lastUser, currentUser)) {
                // Account has changed (i.e. new SIM card): delete the current account and create a new one
                if (logger.isActivated()) {
                    logger.debug("Deleting the old RCS account for " + lastUser);
                }

                contactsManager.deleteRCSEntries();
                removeRcsAccount(null);
    
                if (logger.isActivated()) {
                    logger.debug("Creating a new RCS account for " + currentUser);
                }
                createRcsAccount(
                        ctx.getString(R.string.rcs_core_account_username), true);
            }
        }
	}
	
	/**
	 * Is user account reset by end user
	 * 
	 * @return Boolean
	 */
	public boolean isAccountResetByEndUser() {
		return getRegistry().readBoolean(REGISTRY_RCS_ACCOUNT_MANUALY_DELETED, false);
	}

    /**
     * Set user account reset by end user
     * 
     * @param Boolean
     */
    public void setAccountResetByEndUser(boolean value) {
    	getRegistry().writeBoolean(REGISTRY_RCS_ACCOUNT_MANUALY_DELETED, value);
    }
    
	private AndroidRegistryFactory getRegistry() {
		return (AndroidRegistryFactory)AndroidRegistryFactory.getFactory();
	}    
}
