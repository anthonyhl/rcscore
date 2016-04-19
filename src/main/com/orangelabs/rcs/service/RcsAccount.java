package com.orangelabs.rcs.service;

import static com.google.common.base.Preconditions.checkNotNull;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;

import com.orangelabs.rcs.R;
import com.orangelabs.rcs.utils.logger.Logger;

public final class RcsAccount {
	
    /**
     * Account manager type
     */
    public static final String ACCOUNT_MANAGER_TYPE = "com.orangelabs.rcs";
	
    // service
	private final AccountManager accountManager;
	private final ContentResolver resolver;
	
	// status
	private final Account account;
	private final String accountName;
	private boolean recreated;

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(RcsAccount.class.getName()); 
	
	public RcsAccount(Context context) {
		accountManager = AccountManager.get(context);
		resolver = context.getContentResolver();
		accountName = context.getString(R.string.rcs_core_account_username);
		
		account = create(accountName);
		recreated = account == null;
	}

    /**
     * Get the account for the specified name
     * 
     * @param username The username
     * @return The account
     */
    public Account getAccount(String username) {
    	checkNotNull(username);
    	
        for (Account account : getAccounts()) {
        	if (username.equals(account.name)) {
        		return account;
        	}
        }
        
        return null;
    }
    
    private Account create(String username) {   	
    	Account account = getAccount(username);
    	if (account != null) {    		
    		return account;    		
    	}
    	
    	account = new Account(username, ACCOUNT_MANAGER_TYPE);
    	
    	if (!accountManager.addAccountExplicitly(account, null, null)) {
    		if (logger.isActivated()){
    			logger.error("Unable to create account for " + username);
    		}
    	}
    	return account;  	
    }
    
    public void setSync(boolean enableSync) {
        // Set contacts sync for this account.
        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, enableSync ? 1 : 0);
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, enableSync);
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
    public void createContactGroup(boolean enableSync) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Groups.ACCOUNT_NAME, accountName);
        contentValues.put(Groups.ACCOUNT_TYPE, ACCOUNT_MANAGER_TYPE);
        contentValues.put(Groups.GROUP_VISIBLE, false);

        resolver.insert(Groups.CONTENT_URI, contentValues);
//
//    	ContactsManager.createInstance(context, context.getContentResolver(), localContentResolver);  
//        // Create the "Me" item
//        ContactsManager.getInstance().createMyContact();
    }

    /**
     * Check if sync is enabled.
     * 
     * @param context The context
     * @param username The username
     */
    public boolean isSyncEnabled() {
        return ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY);
    }

    /**
     * Remove all RCS accounts with the exception of the excludeUsername account
     * 
     * @param context The context
     * @param excludeUsername The username for which the account should not be
     *            removed (can be null)
     */
    public void removeRcsAccount() {
        for (Account account : getAccounts()) {
        	accountManager.removeAccount(account, null, null);
        }
    }

	private Account[] getAccounts() {
		return accountManager.getAccountsByType(ACCOUNT_MANAGER_TYPE);
	}

}
