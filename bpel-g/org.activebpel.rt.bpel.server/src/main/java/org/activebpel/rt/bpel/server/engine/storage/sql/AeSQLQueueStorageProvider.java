// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel.server/src/org/activebpel/rt/bpel/server/engine/storage/sql/AeSQLQueueStorageProvider.java,v 1.13 2008/02/17 21:38:46 mford Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2007 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.server.engine.storage.sql;

import org.activebpel.rt.AeException;
import org.activebpel.rt.bpel.def.AePartnerLinkOpKey;
import org.activebpel.rt.bpel.impl.list.*;
import org.activebpel.rt.bpel.impl.queue.AeAlarm;
import org.activebpel.rt.bpel.impl.queue.AeInboundReceive;
import org.activebpel.rt.bpel.impl.queue.AeMessageReceiver;
import org.activebpel.rt.bpel.server.AeMessages;
import org.activebpel.rt.bpel.server.engine.recovery.journal.AeAlarmJournalEntry;
import org.activebpel.rt.bpel.server.engine.recovery.journal.AeInboundReceiveJournalEntry;
import org.activebpel.rt.bpel.server.engine.storage.*;
import org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider;
import org.activebpel.rt.bpel.server.engine.storage.providers.IAeStorageConnection;
import org.activebpel.rt.bpel.server.engine.storage.sql.filters.AeSQLAlarmFilter;
import org.activebpel.rt.bpel.server.engine.storage.sql.filters.AeSQLReceiverFilter;
import org.activebpel.rt.bpel.server.engine.storage.sql.handlers.AeAlarmListHandler;
import org.activebpel.rt.bpel.server.engine.storage.sql.handlers.AeAlarmListQueryHandler;
import org.activebpel.rt.bpel.server.engine.storage.sql.handlers.AeMessageReceiverHandler;
import org.activebpel.rt.bpel.server.engine.storage.sql.handlers.AeMessageReceiverListHandler;
import org.activebpel.rt.util.AeUtil;
import org.apache.commons.dbutils.ResultSetHandler;

import javax.inject.Inject;
import javax.xml.namespace.QName;
import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This is a SQL Queue Storage provider (an implementation of IAeQueueStorageDelegate).
 */
public class AeSQLQueueStorageProvider extends AeAbstractSQLStorageProvider implements IAeQueueStorageProvider {
    /**
     * The SQL statement prefix for all SQL statements used in this class.
     */
    public static final String SQLSTATEMENT_PREFIX = "QueueStorage."; //$NON-NLS-1$

    /**
     * A SQL Result Set Handler that returns a list of Message Receivers.
     */
    private static final AeMessageReceiverListHandler MESSAGE_RECEIVER_LIST_HANDLER = new AeMessageReceiverListHandler();
    /**
     * A SQL Result Set Handler that returns a single Message Receivers.
     */
    private static final AeMessageReceiverHandler MESSAGE_RECEIVER_HANDLER = new AeMessageReceiverHandler();
    /**
     * A SQL Result Set Handler that returns a list of Alarms.
     */
    private static final ResultSetHandler<List<AePersistedAlarm>> ALARM_LIST_HANDLER = new AeAlarmListQueryHandler();

    /**
     * The number of times to retry deadlocked transactions.
     */
    private static final int DEADLOCK_TRY_COUNT = 5;

    private AeCounter mCounter;
    private AeCounter mHashCollisionCounter;

    /**
     * The journal storage.
     */
    @Inject
    private AeSQLJournalStorage mJournalStorage;

    /**
     * Constructs a SQL Queue storage delegate given a SQL config object.
     */
    public AeSQLQueueStorageProvider() {
        setPrefix(SQLSTATEMENT_PREFIX);
    }

    /**
     * Returns next available queued receive object id.
     *
     * @throws AeStorageException
     */
    protected long getNextQueuedReceiveId() throws AeStorageException {
        return getCounter().getNextValue();
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#storeReceiveObject(org.activebpel.rt.bpel.impl.queue.AeMessageReceiver)
     */
    public void storeReceiveObject(AeMessageReceiver aReceiveObject) throws AeStorageException {
        long receiveId = getNextQueuedReceiveId();

        Object[] params = new Object[]
                {
                        receiveId,
                        aReceiveObject.getProcessId(),
                        aReceiveObject.getMessageReceiverPathId(),
                        aReceiveObject.getOperation(),
                        aReceiveObject.getPartnerLinkOperationKey().getPartnerLinkName(),
                        AeUtil.getSafeString(aReceiveObject.getPortType().getNamespaceURI()),
                        aReceiveObject.getPortType().getLocalPart(),
                        AeStorageUtil.getCorrelationProperties(aReceiveObject),
                        AeStorageUtil.getReceiveMatchHash(aReceiveObject),
                        AeStorageUtil.getReceiveCorrelatesHash(aReceiveObject),
                        aReceiveObject.getGroupId(),
                        aReceiveObject.getPartnerLinkOperationKey().getPartnerLinkId(),
                        AeDbUtils.convertBooleanToInt(aReceiveObject.isConcurrent())
                };
        update(IAeQueueSQLKeys.INSERT_QUEUED_RECEIVE, params);
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#storeAlarm(long, int, int, int, java.util.Date)
     */
    public void storeAlarm(long aProcessId, int aLocationPathId, int aGroupId, int aAlarmId, Date aDeadline)
            throws AeStorageException {
        Object[] params = new Object[]{
                aProcessId,
                aLocationPathId,
                new Timestamp(aDeadline.getTime()),
                aDeadline.getTime(),
                aGroupId,
                aAlarmId
        };

        update(IAeQueueSQLKeys.INSERT_ALARM, params);
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#removeReceiveObjectsInGroup(long, int, int, org.activebpel.rt.bpel.server.engine.storage.providers.IAeStorageConnection)
     */
    public int removeReceiveObjectsInGroup(long aProcessId, int aGroupId, int aLocationPathId,
                                           IAeStorageConnection aConnection) throws AeStorageException {
        Connection connection = getSQLConnection(aConnection);

        Object[] params = {aProcessId, aGroupId};
        int count = update(connection, IAeQueueSQLKeys.DELETE_QUEUED_RECEIVES_BY_GROUP, params);

        // Now, for backwards compatibility, remove a single queued receive if none were found in the
        // above delete.
        if (count == 0 && aLocationPathId != -1) {
            Object[] params2 = {aProcessId, aLocationPathId};
            count = update(connection, IAeQueueSQLKeys.DELETE_QUEUED_RECEIVES_BY_LOCID, params2);
        }

        return count;
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#removeReceiveObjectById(int)
     */
    public boolean removeReceiveObjectById(int aQueuedReceiveId) throws AeStorageException {
        // Next, delete the queued receive from the QueuedReceive table.
        return update(IAeQueueSQLKeys.DELETE_QUEUED_RECEIVE_BYID, aQueuedReceiveId) == 1;
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#removeAlarm(long, int, int, org.activebpel.rt.bpel.server.engine.storage.providers.IAeStorageConnection)
     */
    public boolean removeAlarm(long aProcessId, int aLocationPathId, int aAlarmId, IAeStorageConnection aConnection)
            throws AeStorageException {
        Object[] params = new Object[]{aProcessId, aLocationPathId, aAlarmId};
        return update(getSQLConnection(aConnection), IAeQueueSQLKeys.DELETE_ALARM, params) == 1;
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#removeAlarmsInGroup(long,
     *      int, org.activebpel.rt.bpel.server.engine.storage.providers.IAeStorageConnection)
     */
    public int removeAlarmsInGroup(long aProcessId, int aGroupId, IAeStorageConnection aConnection)
            throws AeStorageException {
        Object[] params = new Object[]{aProcessId, aGroupId};
        return update(getSQLConnection(aConnection), IAeQueueSQLKeys.DELETE_ALARMS_IN_GROUP, params);
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#getReceiveObject(long,
     *      int)
     */
    public AePersistedMessageReceiver getReceiveObject(long aProcessId, int aMessageReceiverPathId)
            throws AeStorageException {
        return query(IAeQueueSQLKeys.GET_QUEUED_RECEIVE, MESSAGE_RECEIVER_HANDLER, aProcessId, aMessageReceiverPathId);
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#getReceives(int, int)
     */
    public List<AePersistedMessageReceiver> getReceives(int aMatchHash, int aCorrelatesHash) throws AeStorageException {
        for (int tries = 0; true; ) {
            try {
                // Stop looping when successful.
                return query(IAeQueueSQLKeys.GET_CORRELATED_RECEIVES, MESSAGE_RECEIVER_LIST_HANDLER, aMatchHash, aCorrelatesHash);
            } catch (AeStorageException e) {
                // Abort if this is not an SQL exception or we've already tried
                // getDeadlockTryCount() times.
                if (!(e.getCause() instanceof SQLException) || (++tries >= getDeadlockTryCount())) {
                    throw e;
                }

                AeException.logError(null, AeMessages.getString("AeSQLQueueStorage.DEADLOCK_RETRY")); //$NON-NLS-1$
            }
        }
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#getQueuedMessageReceivers(org.activebpel.rt.bpel.impl.list.AeMessageReceiverFilter)
     */
    public AeMessageReceiverListResult getQueuedMessageReceivers(AeMessageReceiverFilter aFilter)
            throws AeStorageException {
        AeSQLReceiverFilter filter = createSQLFilter(aFilter);
        String sql = filter.getSelectStatement();
        return getFilteredReceives(sql, aFilter, filter.getParams());
    }

    /**
     * Creates the filter wrapper used to format the sql statement.
     *
     * @param aFilter The selection criteria.
     */
    protected AeSQLReceiverFilter createSQLFilter(AeMessageReceiverFilter aFilter) throws AeStorageException {
        return new AeSQLReceiverFilter(aFilter, getSQLConfig());
    }


    /**
     * Extracts the data from the ResultSet and returns an AeSQLMessageReceiver.
     *
     * @param aResultSet
     * @throws SQLException
     */
    public static AePersistedMessageReceiver readSQLMessageReceiver(ResultSet aResultSet) throws SQLException {
        AePersistedMessageReceiver rval = null;
        int queuedReceiveId = aResultSet.getInt(IAeQueueColumns.QUEUED_RECEIVE_ID);
        long processId = aResultSet.getLong(IAeQueueColumns.PROCESS_ID);
        int locationPathId = aResultSet.getInt(IAeQueueColumns.LOCATION_PATH_ID);
        String operation = aResultSet.getString(IAeQueueColumns.OPERATION);
        String plinkName = aResultSet.getString(IAeQueueColumns.PARTNER_LINK_NAME);
        // TODO (MF) I want to drop the port type from this table
        String portTypeNamespace = aResultSet.getString(IAeQueueColumns.PORT_TYPE_NAMESPACE);
        String portTypeLocalPart = aResultSet.getString(IAeQueueColumns.PORT_TYPE_LOCALPART);
        Reader corrPropsReader = aResultSet.getClob(IAeQueueColumns.CORRELATION_PROPERTIES).getCharacterStream();
        int groupId = aResultSet.getInt(IAeQueueColumns.GROUP_ID);
        int partnerLinkId = aResultSet.getInt(IAeQueueColumns.PARTNER_LINK_ID);
        AePartnerLinkOpKey plDefKey = new AePartnerLinkOpKey(plinkName, partnerLinkId, operation);

        QName processName = new QName(aResultSet.getString(IAeProcessColumns.PROCESS_NAMESPACE), aResultSet.getString(IAeProcessColumns.PROCESS_NAME));
        boolean allowsConcurrency = AeDbUtils.convertIntToBoolean(aResultSet.getInt(IAeQueueColumns.ALLOWS_CONCURRENCY));

        try {
            QName portType = new QName(portTypeNamespace, portTypeLocalPart);
            Map<QName, String> info = AeStorageUtil.deserializeCorrelationProperties(corrPropsReader);
            rval =
                    new AePersistedMessageReceiver(
                            queuedReceiveId,
                            processId,
                            processName,
                            plDefKey,
                            portType,
                            info,
                            locationPathId,
                            groupId,
                            allowsConcurrency);
        } catch (Exception e) {
            AeException.logError(e, AeMessages.getString("AeSQLQueueStorage.ERROR_13")); //$NON-NLS-1$
        }
        return rval;
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#getAlarms()
     */
    public List<AePersistedAlarm> getAlarms() throws AeStorageException {
        return query(IAeQueueSQLKeys.GET_ALARMS, ALARM_LIST_HANDLER);
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#getAlarms(org.activebpel.rt.bpel.impl.list.AeAlarmFilter)
     */
    public AeAlarmListResult<? extends AeAlarm> getAlarms(AeAlarmFilter aFilter) throws AeStorageException {
        AeSQLAlarmFilter filter = createSQLAlarmFilter(aFilter);
        String sql = filter.getSelectStatement();
        return getFilteredAlarms(sql, aFilter, filter.getParams());
    }

    /**
     * Creates the filter wrapper used to format the sql statement.
     *
     * @param aFilter The selection criteria.
     */
    protected AeSQLAlarmFilter createSQLAlarmFilter(AeAlarmFilter aFilter) throws AeStorageException {
        return new AeSQLAlarmFilter(aFilter, getSQLConfig());
    }

    /**
     * Generic convenience method for getting a list of alarms from the database,
     * given the SQL query and parameters.
     *
     * @param aSQLQuery The SQL query that will return a list of receives.
     * @param aParams   The parameters to the SQL query.
     * @return The AeAlarmListResult.
     * @throws AeStorageException
     */
    protected AeAlarmListResult<? extends AeAlarm> getFilteredAlarms(String aSQLQuery, AeAlarmFilter aFilter, Object... aParams)
            throws AeStorageException {

        try (Connection connection = getConnection()) {
            AeAlarmListHandler handler = new AeAlarmListHandler(aFilter);
            List<AeAlarmExt> matches = getQueryRunner().query(connection, aSQLQuery, handler, aParams);
            return new AeAlarmListResult<>(handler.getRowCount(), matches);
        } catch (SQLException ex) {
            throw new AeStorageException(ex);
        }
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#journalInboundReceive(long, int, org.activebpel.rt.bpel.impl.queue.AeInboundReceive, org.activebpel.rt.bpel.server.engine.storage.providers.IAeStorageConnection)
     */
    public long journalInboundReceive(long aProcessId, int aLocationId, AeInboundReceive aInboundReceive,
                                      IAeStorageConnection aConnection) throws AeStorageException {
        return getJournalStorage().writeJournalEntry(aProcessId,
                new AeInboundReceiveJournalEntry(aLocationId, aInboundReceive),
                getSQLConnection(aConnection));
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#journalAlarm(long,
     *      int, int, int, org.activebpel.rt.bpel.server.engine.storage.providers.IAeStorageConnection)
     */
    public long journalAlarm(long aProcessId, int aGroupId, int aLocationPathId, int aAlarmId, IAeStorageConnection aConnection)
            throws AeStorageException {
        return getJournalStorage().writeJournalEntry(aProcessId,
                new AeAlarmJournalEntry(aLocationPathId, aGroupId, aAlarmId), getSQLConnection(aConnection));
    }

    /**
     * @see org.activebpel.rt.bpel.server.engine.storage.providers.IAeQueueStorageProvider#incrementHashCollisionCounter()
     */
    public void incrementHashCollisionCounter() {
        try {
            getHashCollisionCounter().getNextValue();
        } catch (Exception e) {
            AeException.logError(e, AeMessages.getString("AeSQLQueueStorage.ERROR_11")); //$NON-NLS-1$
        }
    }

    /**
     * Generic convenience method for getting a list of receives from the database,
     * given the SQL query and parameters.
     *
     * @param aSQLQuery The SQL query that will return a list of receives.
     * @param aParams   The parameters to the SQL query.
     * @return The AeMessageReceiverListResult.
     * @throws AeStorageException
     */
    protected AeMessageReceiverListResult getFilteredReceives(String aSQLQuery, AeMessageReceiverFilter aFilter, Object... aParams) throws AeStorageException {
        try (Connection connection = getConnection()) {
            AeMessageReceiverListHandler handler = new AeMessageReceiverListHandler(aFilter);
            List<AePersistedMessageReceiver> matches = getQueryRunner().query(connection, aSQLQuery, handler, aParams);
            AeMessageReceiver[] receivers = matches.toArray(new AeMessageReceiver[matches.size()]);
            return new AeMessageReceiverListResult(handler.getRowCount(), receivers);
        } catch (SQLException ex) {
            throw new AeStorageException(ex);
        }
    }

    /**
     * @return Returns the journalStorage.
     */
    public AeSQLJournalStorage getJournalStorage() {
        return mJournalStorage;
    }

    /**
     * @param aJournalStorage The journalStorage to set.
     */
    public void setJournalStorage(AeSQLJournalStorage aJournalStorage) {
        mJournalStorage = aJournalStorage;
    }

    /**
     * Returns number of times to retry deadlocked transactions.
     */
    protected int getDeadlockTryCount() {
        return DEADLOCK_TRY_COUNT;
    }

    public AeCounter getCounter() {
        return mCounter;
    }

    public void setCounter(AeCounter aCounter) {
        mCounter = aCounter;
    }

    public AeCounter getHashCollisionCounter() {
        return mHashCollisionCounter;
    }

    public void setHashCollisionCounter(AeCounter aHashCollisionCounter) {
        mHashCollisionCounter = aHashCollisionCounter;
    }
}
