/*
 * Copyright (C) 2014 Sony Mobile Communications AB.
 * All rights, including trade secret rights, reserved.
 */

package com.orangelabs.rcs.core.im.chat;

import com.orangelabs.rcs.core.im.InstantMessagingService;

/**
 * Delayed Display Notification Manager tries to dispatch all pending display
 * notifications
 */
public class DelayedDisplayNotificationTask {

	public DelayedDisplayNotificationTask(InstantMessagingService instantMessagingService) {
		instantMessagingService.getImsModule().getListener()
				.tryToDispatchAllPendingDisplayNotifications();
	}
}
