/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.orangelabs.rcs.service;

import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

import com.orangelabs.rcs.R;
import com.orangelabs.rcs.provider.LocalContentResolver;

/**
 * Setup RCS account activity
 */
public class SetupRcsAccount extends android.accounts.AccountAuthenticatorActivity {

	private LocalContentResolver mLocalContentResolver;

	public void onCreate(Bundle icicle){
		super.onCreate(icicle);
		Context ctx = getApplicationContext();
		mLocalContentResolver = new LocalContentResolver(ctx.getContentResolver());
		AuthenticationService.createRcsAccount(this, mLocalContentResolver,
				getString(R.string.rcs_core_account_username), true);
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			Bundle result = new Bundle();
			result.putString(AccountManager.KEY_ACCOUNT_NAME, getString(R.string.rcs_core_account_username));
			result.putString(AccountManager.KEY_ACCOUNT_TYPE, AuthenticationService.ACCOUNT_MANAGER_TYPE);
			
			setAccountAuthenticatorResult(result);

			// Start the service
            LauncherUtils.launchRcsService(ctx, false, false);
		}
		finish();
	}
	
}
