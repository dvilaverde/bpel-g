// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel.server/src/org/activebpel/rt/bpel/server/engine/storage/AePersistentAttachmentManager.java,v 1.5 2007/05/24 01:06:18 KRoe Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2007 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.server.engine.storage;

import org.activebpel.rt.bpel.impl.AeFileAttachmentManager;
import org.activebpel.rt.bpel.impl.attachment.IAeAttachmentStorage;
import org.activebpel.rt.bpel.server.AeMessages;
import org.activebpel.rt.bpel.server.engine.storage.attachment.AeCompositeAttachmentStorage;

import javax.inject.Inject;

/**
 * Implements a persistent attachment manager.
 */
public class AePersistentAttachmentManager extends AeFileAttachmentManager {
    /**
     * The composite storage object.
     */
    private IAeAttachmentStorage mCompositeStorage;

    /**
     * The default persistent storage object.
     */
    private IAeAttachmentStorage mPersistentStorage;

    @Inject
    private IAeStorageFactory mStorageFactory;

    /**
     * Returns the persistent (database) storage implementation.
     */
    public IAeAttachmentStorage getPersistentStorage()
            throws AeStorageException {
        if (mPersistentStorage == null) {
            mPersistentStorage = getStorageFactory().getAttachmentStorage();

            if (mPersistentStorage == null) {
                throw new AeStorageException(
                        AeMessages
                                .getString("AePersistentAttachmentManager.ERROR_GettingStorage")); //$NON-NLS-1$
            }
        }

        return mPersistentStorage;
    }

    /**
     * Overrides method to return the composite storage implementation.
     *
     * @see org.activebpel.rt.bpel.impl.AeAbstractAttachmentManager#getStorage()
     */
    public IAeAttachmentStorage getStorage() throws AeStorageException {
        if (mCompositeStorage == null) {
            mCompositeStorage = new AeCompositeAttachmentStorage(
                    getPersistentStorage(), getFileStorage());
        }

        return mCompositeStorage;
    }

    public IAeStorageFactory getStorageFactory() {
        return mStorageFactory;
    }

    public void setStorageFactory(IAeStorageFactory aStorageFactory) {
        mStorageFactory = aStorageFactory;
    }
}
