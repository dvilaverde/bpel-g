package org.activebpel.rt.bpel.server.admin.jmx;

import bpelg.services.processes.types.ProcessFilterType;
import bpelg.services.processes.types.ProcessList;
import org.activebpel.rt.AeException;
import org.activebpel.rt.bpel.AeBusinessProcessException;
import org.activebpel.rt.bpel.AePreferences;
import org.activebpel.rt.bpel.coord.AeCoordinationDetail;
import org.activebpel.rt.bpel.def.AeProcessDef;
import org.activebpel.rt.bpel.impl.AeMonitorStatus;
import org.activebpel.rt.bpel.impl.list.*;
import org.activebpel.rt.bpel.impl.queue.AeMessageReceiver;
import org.activebpel.rt.bpel.server.IAeDeploymentProvider;
import org.activebpel.rt.bpel.server.admin.AeBuildInfo;
import org.activebpel.rt.bpel.server.admin.AeEngineStatus;
import org.activebpel.rt.bpel.server.admin.IAeEngineAdministration;
import org.activebpel.rt.bpel.server.engine.AeEngineFactory;
import org.activebpel.rt.bpel.server.engine.IAeProcessLogger;
import org.activebpel.rt.util.AeUtil;
import org.activebpel.rt.xml.AeQName;
import org.apache.commons.codec.binary.Base64;

import javax.xml.namespace.QName;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class AeEngineManagementAdapter implements IAeEngineManagementMXBean {

    private final IAeEngineAdministration mAdmin;

    public AeEngineManagementAdapter(IAeEngineAdministration aAdmin) {
        mAdmin = aAdmin;
    }

    public List<AeMessageReceiverBean> getMessageReceivers(long aProcessId, String aPartnerLinkName, String aPortTypeNamespace, String aPortTypeLocalPart,
                                                           String aOperation, int aMaxReturn, int aListStart) {
        AeMessageReceiverFilter filter = new AeMessageReceiverFilter();
        filter.setProcessId(aProcessId);
        filter.setPartnerLinkName(aPartnerLinkName);
        if (AeUtil.notNullOrEmpty(aPortTypeNamespace) && AeUtil.notNullOrEmpty(aPortTypeLocalPart))
            filter.setPortType(new QName(aPortTypeNamespace, aPortTypeLocalPart));
        filter.setOperation(aOperation);
        filter.setMaxReturn(aMaxReturn);
        filter.setListStart(aListStart);
        AeMessageReceiverListResult messageReceivers = mAdmin.getMessageReceivers(filter);
        List<AeMessageReceiverBean> receivers = new ArrayList<>();
        for (AeMessageReceiver receiver : messageReceivers.getResults()) {
            Map<AeQName, String> correlation = null;
            if (receiver.isCorrelated()) {
                correlation = new HashMap<>();
                for (Map.Entry<QName, String> entry : receiver.getCorrelation().entrySet()) {
                    correlation.put(new AeQName(entry.getKey()), entry.getValue());
                }
            }
            receivers.add(new AeMessageReceiverBean(receiver.getProcessId(),
                    receiver.getPartnerLinkName(),
                    new AeQName(receiver.getPortType()),
                    receiver.getOperation(),
                    correlation,
                    receiver.getMessageReceiverPathId()));
        }
        return receivers;
    }

    public List<AeAlarmExt> getAlarms(long aProcessId, Date aAlarmFilterStart, Date aAlarmFilterEnd,
                                      String aProcessNamespace, String aProcessLocalPart, int aMaxReturn, int aListStart) {
        AeAlarmFilter filter = new AeAlarmFilter();
        filter.setProcessId(aProcessId);
        filter.setAlarmFilterStart(aAlarmFilterStart);
        filter.setAlarmFilterEnd(aAlarmFilterEnd);
        if (AeUtil.notNullOrEmpty(aProcessNamespace) && AeUtil.notNullOrEmpty(aProcessLocalPart))
            filter.setProcessName(new QName(aProcessNamespace, aProcessLocalPart));
        filter.setMaxReturn(aMaxReturn);
        filter.setListStart(aListStart);

        return mAdmin.getAlarms(filter).getResults();
    }

    public AeBuildInfo[] getBuildInfo() {
        return mAdmin.getBuildInfo();
    }

    public String getEngineErrorInfo() {
        return mAdmin.getEngineErrorInfo();
    }

    public AeEngineStatus getEngineState() {
        return mAdmin.getEngineState();
    }

    public AeMonitorStatus getMonitorStatus() {
        return mAdmin.getMonitorStatus();
    }

    public String getProcessLog(long aProcessId) {
        return mAdmin.getProcessLog(aProcessId);
    }

    public AeProcessLogPart getProcessLogPart(long aProcessId, int aPart) throws Exception {

        AeProcessLogPart part = new AeProcessLogPart();
        part.setPart(aPart);

        // get a reader onto the log
        Reader reader = AeEngineFactory.getBean(IAeProcessLogger.class).getFullLog(aProcessId);

        skipAndRead(part, reader, AeProcessLogPart.PART_SIZE);
        return part;
    }

    /**
     * Skips to where we want to be in the reader and starts reading til the buffer is filled.
     *
     * @param aPart
     * @param aReader
     * @param aPartSize
     * @throws IOException
     */
    protected static void skipAndRead(AeProcessLogPart aPart, Reader aReader, int aPartSize)
            throws IOException {
        aPart.setLog(null);
        try (BufferedReader br = new BufferedReader(aReader)) {
            // skip to where we want to be in the log
            int skipCount = aPart.getPart() * aPartSize;
            long skipped = br.skip(skipCount);

            // if we don't skip to that point, then there's nothing to read here
            if (skipped != skipCount) {
                aPart.setMoreAvailable(false);
            } else {
                // fill the buffer for the part
                char[] buffer = new char[aPartSize];
                int read = br.read(buffer);
                if (read > 0) {
                    aPart.setLog(new String(buffer, 0, read));
                    // signal that there's more to read if we didn't fill the buffer
                    aPart.setMoreAvailable(read == buffer.length);
                } else {
                    aPart.setMoreAvailable(false);
                }
            }
        }
    }

    public Date getStartDate() {
        return mAdmin.getStartDate();
    }

    public String getProcessState(long aPid) throws AeBusinessProcessException {
        return mAdmin.getProcessState(aPid);
    }

    public String getLocationPathById(long aProcessId, int aLocationId) throws AeBusinessProcessException {
        return mAdmin.getLocationPathById(aProcessId, aLocationId);
    }

    public String getVariable(long aPid, String aVariablePath) throws AeBusinessProcessException {
        return mAdmin.getVariable(aPid, aVariablePath);
    }

    public boolean isInternalWorkManager() {
        return mAdmin.isInternalWorkManager();
    }

    public boolean isRunning() {
        return mAdmin.isRunning();
    }

    public void start() throws AeException {
        mAdmin.start();
    }

    public void stop() throws AeBusinessProcessException {
        mAdmin.stop();
    }

    public AeCoordinationDetail getCoordinatorForProcessId(long aChildProcessId) throws AeException {
        return mAdmin.getCoordinatorForProcessId(aChildProcessId);
    }

    public List<AeCoordinationDetail> getParticipantForProcessId(long aParentProcessId) throws AeException {
        return mAdmin.getParticipantForProcessId(aParentProcessId);
    }

    public long getCacheMisses() {
        return mAdmin.getCatalogAdmin().getCacheStatistics().getCacheMisses();
    }

    public long getCacheHits() {
        return mAdmin.getCatalogAdmin().getCacheStatistics().getCacheHits();
    }

    public AeCatalogItemDetail getCatalogItemDetail(String aLocationHint) {
        return mAdmin.getCatalogAdmin().getCatalogItemDetail(aLocationHint);
    }

    public List<AeCatalogItem> getCatalogListing(String aTypeURI, String aResource, String aNamespace, int aMaxReturn, int aListStart) {
        AeCatalogListingFilter filter = new AeCatalogListingFilter();
        filter.setTypeURI(aTypeURI);
        filter.setResource(aResource);
        filter.setNamespace(aNamespace);
        filter.setMaxReturn(aMaxReturn);
        filter.setListStart(aListStart);
        return mAdmin.getCatalogAdmin().getCatalogListing(filter).getResults();
    }

    public int getCatalogCacheSize() {
        return AePreferences.getResourceCacheSize();
    }

    public String getEngineDescription() {
        return "bpel-g";
    }

    public boolean isProcessRestartEnabled() {
        return AePreferences.isRestartEnabled();
    }

    public int getAlarmMaxWorkCount() {
        return AePreferences.getAlarmMaxCount();
    }

    public int getProcessWorkCount() {
        return AePreferences.getProcessWorkCount();
    }

    public int getThreadPoolMax() {
        return AePreferences.getThreadPoolMax();
    }

    public int getThreadPoolMin() {
        return AePreferences.getThreadPoolMin();
    }

    public long getUnmatchedCorrelatedReceiveTimeoutMillis() {
        return AePreferences.getUnmatchedCorrelatedReceiveTimeoutMillis();
    }

    public int getWebServiceInvokeTimeout() {
        return AePreferences.getSendTimeout();
    }

    public int getWebServiceReceiveTimeout() {
        return AePreferences.getReceiveTimeout();
    }

    public boolean isAllowCreateXPath() {
        return AePreferences.isAllowCreateXpath();
    }

    public boolean isAllowEmptyQuerySelection() {
        return AePreferences.isAllowEmptyQuerySelection();
    }

    public boolean isResourceReplaceEnabled() {
        return AePreferences.isResourceReplaceEnabled();
    }

    public boolean isValidateServiceMessages() {
        return AePreferences.isValidateServiceMessages();
    }

    public void setAlarmMaxWorkCount(int aValue) {
        AePreferences.setAlarmMaxCount(aValue);
    }

    public void setAllowCreateXPath(boolean aAllowedCreateXPath) {
        AePreferences.setAllowCreateXpath(aAllowedCreateXPath);
    }

    public void setAllowEmptyQuerySelection(boolean aAllowedEmptyQuerySelection) {
        AePreferences.setAllowEmptyQuerySelection(aAllowedEmptyQuerySelection);
    }

    public void setCatalogCacheSize(int aSize) {
        AePreferences.setResourceCacheSize(aSize);
    }

    public void setProcessWorkCount(int aValue) {
        AePreferences.setProcessWorkCount(aValue);
    }

    public void setResourceReplaceEnabled(boolean aEnabled) {
        AePreferences.setResourceReplaceEnabled(aEnabled);
    }

    public void setThreadPoolMax(int aValue) {
        AePreferences.setThreadPoolMax(aValue);
    }

    public void setThreadPoolMin(int aValue) {
        AePreferences.setThreadPoolMin(aValue);
    }

    public void setUnmatchedCorrelatedReceiveTimeoutMillis(long aTimeout) {
        AePreferences.setUnmatchedCorrelatedReceiveTimeoutMillis(aTimeout);
    }

    public void setValidateServiceMessages(boolean aValidateServiceMessages) {
        AePreferences.setValidateServiceMessages(aValidateServiceMessages);
    }

    public void setWebServiceInvokeTimeout(int aTimeout) {
        AePreferences.setSendTimeout(aTimeout);
    }

    public void setWebServiceReceiveTimeout(int aTimeout) {
        AePreferences.setReceiveTimeout(aTimeout);
    }

    public int getProcessCount(ProcessFilterType aFilter)
            throws AeBusinessProcessException {
        return mAdmin.getProcessCount(aFilter);
    }

    public AeProcessListResultBean getProcessList(ProcessFilterType aFilter) throws AeBusinessProcessException {
        ProcessList processList = mAdmin.getProcessList(aFilter);
        return new AeProcessListResultBean(processList.getTotalRowCount(), processList.getProcessInstanceDetail(), processList.isComplete());
    }

    public String getCompiledProcessDef(long aProcessId, AeQName aName) throws AeBusinessProcessException {
        AeProcessDef def = null;
        if (aProcessId <= 0) {
            def = AeEngineFactory.getBean(IAeDeploymentProvider.class).findCurrentDeployment(aName.toQName()).getProcessDef();
        } else {
            def = AeEngineFactory.getBean(IAeDeploymentProvider.class).findDeploymentPlan(aProcessId, aName.toQName()).getProcessDef();
        }
        byte[] b = AeUtil.serializeObject(def);
        String s = Base64.encodeBase64String(b);
        return s;
    }

    public boolean isRestartable(long aPid) {
        try {
            return AeEngineFactory.getEngine().isRestartable(aPid);
        } catch (AeBusinessProcessException e) {
            e.printStackTrace();
            return false;
        }
    }
}
