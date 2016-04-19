/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.orangelabs.rcs.service.broadcaster;

import com.gsma.services.rcs.contacts.ContactId;

/**
 * Interface to perform broadcast events on FileTransferListeners
 */
public interface IOneToOneFileTransferBroadcaster {

	public void broadcastStateChanged(ContactId contact, String transferId, int status,
			int reasonCode);

	public void broadcastProgressUpdate(ContactId contact, String transferId, long currentSize,
			long totalSize);

	public void broadcastInvitation(String fileTransferId);

	public void broadcastResumeFileTransfer(String filetransferId);
}
