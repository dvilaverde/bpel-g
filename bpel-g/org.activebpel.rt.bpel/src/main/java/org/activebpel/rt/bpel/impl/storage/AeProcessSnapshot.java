// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel/src/org/activebpel/rt/bpel/impl/storage/AeProcessSnapshot.java,v 1.6.22.1 2008/04/21 16:09:44 ppatruni Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2004 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.impl.storage;

import org.activebpel.rt.bpel.AeBusinessProcessException;
import org.activebpel.rt.bpel.AeMessages;
import org.activebpel.rt.bpel.IAeVariable;
import org.activebpel.rt.bpel.impl.AeBusinessProcess;
import org.activebpel.rt.bpel.impl.AeVariableDeserializer;
import org.activebpel.rt.bpel.impl.activity.support.AeCorrelationSet;
import org.activebpel.rt.bpel.impl.fastdom.AeFastDocument;
import org.activebpel.rt.bpel.impl.visitors.AeProcessSnapshotVisitor;
import org.w3c.dom.Document;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implements a process snapshot that contain all variables and correlation
 * sets that are live for normal or compensation processing.
 */
public class AeProcessSnapshot implements IAeProcessSnapshot {
    /**
     * The process for this snapshot.
     */
    private AeBusinessProcess mProcess;

    /**
     * Maps location paths to 2nd-level maps that map version numbers to
     * correlation sets.
     */
    private Map<String, Map<Integer, AeCorrelationSet>> mCorrelationSetLocationPathsMap;

    /**
     * The set of correlation sets in the snapshot.
     */
    private Set mCorrelationSets;

    /**
     * Maps location paths to 2nd-level maps that map version numbers to
     * variables.
     */
    private Map<String, Map<Integer, IAeVariable>> mVariableLocationPathsMap;

    /**
     * The set of variables in the snapshot.
     */
    private Set mVariables;

    /**
     * The <code>Set</code> of pending <code>AeActivityInvokeImpl</code> instances.
     */
    private Set mPendingInvokes;

    /**
     * Constructor.
     *
     * @param aProcess
     */
    public AeProcessSnapshot(AeBusinessProcess aProcess) throws AeBusinessProcessException {
        setProcess(aProcess);
    }

    /**
     * Returns the correlation set with the specified location path and version
     * number.
     */
    protected AeCorrelationSet getCorrelationSet(String aLocationPath, int aVersionNumber) {
        Map<Integer, AeCorrelationSet> versionNumbersMap = getCorrelationSetLocationPathsMap().get(aLocationPath);
        return (versionNumbersMap == null) ? null : versionNumbersMap.get(aVersionNumber);
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#getCorrelationSetLocationPaths()
     */
    public Set<String> getCorrelationSetLocationPaths() {
        return getCorrelationSetLocationPathsMap().keySet();
    }

    /**
     * Returns a <code>Map</code> from location paths to 2nd-level maps that map
     * version numbers to correlation sets.
     */
    protected Map<String, Map<Integer, AeCorrelationSet>> getCorrelationSetLocationPathsMap() {
        if (mCorrelationSetLocationPathsMap == null) {
            Map<String, Map<Integer, AeCorrelationSet>> locationPathsMap = new HashMap<>();

            // Iterate through all correlation sets in the snapshot.
            for (Object o : getCorrelationSets()) {
                AeCorrelationSet correlationSet = (AeCorrelationSet) o;
                String locationPath = correlationSet.getLocationPath();

                // If the location path is not yet in the location path map, then
                // add a new 2nd-level map for the location path.
                Map<Integer, AeCorrelationSet> versionNumbersMap = locationPathsMap.get(locationPath);
                if (versionNumbersMap == null) {
                    versionNumbersMap = new HashMap<>();
                    locationPathsMap.put(locationPath, versionNumbersMap);
                }

                // Add the correlation set to the 2nd-level map.
                int versionNumber = correlationSet.getVersionNumber();
                versionNumbersMap.put(versionNumber, correlationSet);
            }

            mCorrelationSetLocationPathsMap = locationPathsMap;
        }

        return mCorrelationSetLocationPathsMap;
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#getCorrelationSetVersionNumbers(java.lang.String)
     */
    public Set<Integer> getCorrelationSetVersionNumbers(String aLocationPath) {
        Map<Integer, AeCorrelationSet> versionNumbersMap = getCorrelationSetLocationPathsMap().get(aLocationPath);
        return (versionNumbersMap == null) ? Collections.<Integer>emptySet() : versionNumbersMap.keySet();
    }

    /**
     * Returns the <code>Set</code> of correlation sets in the snapshot.
     */
    protected Set getCorrelationSets() {
        return mCorrelationSets;
    }

    /**
     * Returns the <code>Set</code> of pending <code>AeActivityInvokeImpl</code> instances.
     */
    public Set getPendingInvokes() {
        return mPendingInvokes;
    }

    /**
     * Returns the process for this snapshot.
     */
    protected AeBusinessProcess getProcess() {
        return mProcess;
    }

    /**
     * Returns the variable with the specified location path and version number.
     *
     * @throws AeBusinessProcessException if there is no such variable.
     */
    public IAeVariable getVariable(String aLocationPath, int aVersionNumber) throws AeBusinessProcessException {
        Map<Integer, IAeVariable> versionNumbersMap = getVariableLocationPathsMap().get(aLocationPath);
        if (versionNumbersMap == null) {
            throw new AeBusinessProcessException(AeMessages.getString("AeProcessSnapshot.ERROR_0") + getProcess().getProcessId() + ": " + aLocationPath); //$NON-NLS-1$ //$NON-NLS-2$
        }

        IAeVariable variable = versionNumbersMap.get(aVersionNumber);
        if (variable == null) {
            throw new AeBusinessProcessException(AeMessages.getString("AeProcessSnapshot.ERROR_0") + getProcess().getProcessId() + ": " + aLocationPath + ", " + aVersionNumber); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        return variable;
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#getVariableLocationPaths()
     */
    public Set getVariableLocationPaths() {
        return getVariableLocationPathsMap().keySet();
    }

    /**
     * Returns a <code>Map</code> from location paths to 2nd-level maps that map
     * version numbers to variables.
     */
    protected Map<String, Map<Integer, IAeVariable>> getVariableLocationPathsMap() {
        if (mVariableLocationPathsMap == null) {
            Map<String, Map<Integer, IAeVariable>> locationPathsMap = new HashMap<>();

            // Iterate through all variables in the snapshot.
            for (Object o : getVariables()) {
                IAeVariable variable = (IAeVariable) o;
                String locationPath = variable.getLocationPath();

                // If the location path is not yet in the location path map, then
                // add a new 2nd-level map for the location path.
                Map<Integer, IAeVariable> versionNumbersMap = locationPathsMap.get(locationPath);
                if (versionNumbersMap == null) {
                    versionNumbersMap = new HashMap<>();
                    locationPathsMap.put(locationPath, versionNumbersMap);
                }

                // Add the variable to the 2nd-level map.
                int versionNumber = variable.getVersionNumber();
                versionNumbersMap.put(versionNumber, variable);
            }

            mVariableLocationPathsMap = locationPathsMap;
        }

        return mVariableLocationPathsMap;
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#getVariableVersionNumbers(java.lang.String)
     */
    public Set<Integer> getVariableVersionNumbers(String aLocationPath) {
        Map<Integer, IAeVariable> versionNumbersMap = getVariableLocationPathsMap().get(aLocationPath);
        return (versionNumbersMap == null) ? Collections.<Integer>emptySet() : versionNumbersMap.keySet();
    }

    /**
     * Returns the <code>Set</code> of variables in the snapshot.
     */
    protected Set getVariables() {
        return mVariables;
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#serializeCorrelationSet(java.lang.String, int)
     */
    public AeFastDocument serializeCorrelationSet(String aLocationPath, int aVersionNumber) throws AeBusinessProcessException {
        // TODO (KR) Implement serializeCorrelationSet in order to store correlation sets in the database.
        throw new UnsupportedOperationException();
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#serializeProcess(boolean)
     */
    public AeFastDocument serializeProcess(boolean aForPersistence) throws AeBusinessProcessException {
        return getProcess().fastSerializeState(aForPersistence);
    }

    /**
     * Serializes the variable
     *
     * @param variable
     * @throws AeBusinessProcessException
     */
    public AeFastDocument serializeVariable(IAeVariable variable) throws AeBusinessProcessException {
        return getProcess().fastSerializeVariable(variable);
    }

    /**
     * Sets the process to use for this snapshot.
     *
     * @param aProcess
     * @throws AeBusinessProcessException
     */
    public void setProcess(AeBusinessProcess aProcess) throws AeBusinessProcessException {
        mProcess = aProcess;

        // Use a visitor to collect all correlation sets and variables for the
        // snapshot.
        AeProcessSnapshotVisitor visitor = new AeProcessSnapshotVisitor();
        getProcess().accept(visitor);

        mCorrelationSets = visitor.getCorrelationSets();
        mVariables = visitor.getVariables();
        mPendingInvokes = visitor.getPendingInvokes();
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#setCorrelationSetData(java.lang.String, int, org.w3c.dom.Document)
     */
    public void setCorrelationSetData(String aLocationPath, int aVersionNumber, Document aDocument) throws AeBusinessProcessException {
        // TODO (KR) Implement setCorrelationSetData in order to restore correlation sets from the database.
        throw new UnsupportedOperationException();
    }

    /**
     * @see org.activebpel.rt.bpel.impl.storage.IAeProcessSnapshot#setVariableData(java.lang.String, int, org.w3c.dom.Document)
     */
    public void setVariableData(String aLocationPath, int aVersionNumber, Document aDocument) throws AeBusinessProcessException {
        IAeVariable variable = getVariable(aLocationPath, aVersionNumber);
        AeVariableDeserializer deserializer = new AeVariableDeserializer(getProcess().getEngine());
        deserializer.setVariable(variable);
        deserializer.restoreVariable(aDocument);
    }
}
