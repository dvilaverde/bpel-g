// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel.server/src/org/activebpel/rt/bpel/server/deploy/AeCatalogDeployer.java,v 1.3 2006/08/16 14:20:56 ckeller Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2004 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.server.deploy;

import org.activebpel.rt.AeException;
import org.activebpel.rt.bpel.server.AeMessages;
import org.activebpel.rt.bpel.server.catalog.*;
import org.activebpel.rt.bpel.server.engine.AeEngineFactory;
import org.activebpel.rt.bpel.server.logging.IAeDeploymentLogger;
import org.activebpel.rt.util.AeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IAeCatalogDeployer impl.
 */
public class AeCatalogDeployer implements IAeDeploymentHandler {

    @Override
    public void deploy(IAeDeploymentContainer aContainer, IAeDeploymentLogger aLogger)
            throws AeException {
        AeCatalogDeploymentLogger logger = new AeCatalogDeploymentLogger();
        AeEngineFactory.getBean(IAeCatalog.class).addCatalogListener(logger);
        try {
            AeCatalogMappings catalog = new AeCatalogMappings(aContainer);
            IAeCatalogMapping[] mappingEntries = createCatalogMappings(catalog,
                    aContainer);
            AeEngineFactory.getBean(IAeCatalog.class).addCatalogEntries(
                    aContainer.getDeploymentId(), mappingEntries, catalog.replaceExistingResource());
        } finally {
            AeEngineFactory.getBean(IAeCatalog.class).removeCatalogListener(logger);
        }
    }

    @Override
    public void undeploy(IAeDeploymentContainer aContainer) throws AeException {
        AeEngineFactory.getBean(IAeCatalog.class).remove(aContainer.getDeploymentId());
    }

    /**
     * Create the <code>IAeCatalogMapping</code> impl.
     *
     * @param aCatalog
     * @param aContext
     */
    protected IAeCatalogMapping[] createCatalogMappings(AeCatalogMappings aCatalog,
                                                        IAeDeploymentContext aContext) {
        return aCatalog.getResources().values().toArray(
                new IAeCatalogMapping[aCatalog.getResources().values().size()]);
    }
}

/**
 * Listens for and logs catalog deploment entries.
 */
class AeCatalogDeploymentLogger implements IAeCatalogListener {

    private static final Logger log = LoggerFactory.getLogger(AeCatalogDeploymentLogger.class);

    /**
     * @see org.activebpel.rt.bpel.server.catalog.IAeCatalogListener#onDeployment(org.activebpel.rt.bpel.server.catalog.AeCatalogEvent)
     */
    public void onDeployment(AeCatalogEvent aEvent) {
        Object[] objs = {AeUtil.getShortNameForLocation(aEvent.getLocationHint()),
                aEvent.getLocationHint()};
        log.info(AeMessages.format("AeCatalogDeployer.ADDED_RESOURCE", objs)); //$NON-NLS-1$
    }

    /**
     * @see org.activebpel.rt.bpel.server.catalog.IAeCatalogListener#onDuplicateDeployment(org.activebpel.rt.bpel.server.catalog.AeCatalogEvent)
     */
    public void onDuplicateDeployment(AeCatalogEvent aEvent) {
        Object[] objs = {AeUtil.getShortNameForLocation(aEvent.getLocationHint()),
                aEvent.getLocationHint()};
        if (aEvent.isReplacement())
            log.info(AeMessages.format("AeCatalogDeployer.REPLACED_RESOURCE", objs)); //$NON-NLS-1$
        else
            log.info(AeMessages.format("AeCatalogDeployer.EXISTING_RESOURCE", objs)); //$NON-NLS-1$
    }

    /**
     * @see org.activebpel.rt.bpel.server.catalog.IAeCatalogListener#onRemoval(org.activebpel.rt.bpel.server.catalog.AeCatalogEvent)
     */
    public void onRemoval(AeCatalogEvent aEvent) {
        Object[] objs = {AeUtil.getShortNameForLocation(aEvent.getLocationHint()),
                aEvent.getLocationHint()};
        log.info(AeMessages.format("AeCatalogDeployer.REMOVED_RESOURCE", objs)); //$NON-NLS-1$
    }
}
