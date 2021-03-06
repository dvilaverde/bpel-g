// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpeladmin.war/src/org/activebpel/rt/bpeladmin/war/web/AeAbstractAdminBean.java,v 1.6 2008/02/17 21:43:06 mford Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2004 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpeladmin.war.web;

import org.activebpel.rt.bpel.server.admin.jmx.IAeEngineManagementMXBean;
import org.activebpel.rt.bpeladmin.war.AeEngineManagementFactory;
import org.activebpel.rt.util.AeUtil;
import org.activebpel.rt.war.tags.IAeErrorAwareBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO (PJ) remove get and set statusDetail/Error methods and use addMessage/getMessageList instead. Impacts JSPs.

/**
 * Base class to provide access to <code>IAeEngineAdministration</code>.
 */
public class AeAbstractAdminBean implements IAeErrorAwareBean {
    /**
     * maps property names to their error values
     */
    private Map<String, String> mPropertyErrors;
    /**
     * Indicates whether the status message is actually an error message
     */
    private boolean mErrorDetail;
    /**
     * List of error or status detail messages.
     */
    private final List<String> mMessageList = new ArrayList<>();

    /**
     * Adds a error or detail message.
     *
     * @param aMessage
     */
    protected void addMessage(String aMessage) {
        if (AeUtil.notNullOrEmpty(aMessage)) {
            mMessageList.add(aMessage);
        }
    }

    /**
     * @return Returns a error or details message list as a single string.
     */
    protected String getMessages() {
        List list = getMessageList();
        if (list.size() == 0) {
            return ""; //$NON-NLS-1$
        } else if (list.size() == 1) {
            return (String) list.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (Object aList : list) {
            sb.append((String) aList);
            sb.append("<br/>\n"); //$NON-NLS-1$
        }
        return sb.toString();
    }

    /**
     * Returns message list.
     */
    public List getMessageList() {
        return mMessageList;
    }

    /**
     * Returns <code>true</code> if and only if the status detail is not empty.
     */
    public boolean isStatusDetailAvailable() {
        return AeUtil.notNullOrEmpty(getStatusDetail());
    }

    /**
     * @return Returns the statusDetail.
     */
    public String getStatusDetail() {
        return getMessages();
    }

    /**
     * @param aStatusDetail The statusDetail to set.
     */
    public void setStatusDetail(String aStatusDetail) {
        addMessage(aStatusDetail);
    }

    /**
     * Appender for the status detail.
     *
     * @param aDetail
     */
    public void addStatusDetail(String aDetail) {
        addMessage(aDetail);
    }

    /**
     * Sets the error detail flag.
     *
     * @param aBool
     */
    protected void setErrorDetail(boolean aBool) {
        mErrorDetail = true;
    }

    /**
     * Getter for the error detail.
     */
    public boolean isErrorDetail() {
        return mErrorDetail;
    }

    protected IAeEngineManagementMXBean getAdmin() {
        return AeEngineManagementFactory.getBean();
    }

    /**
     * @see org.activebpel.rt.war.tags.IAeErrorAwareBean#addError(java.lang.String, java.lang.String, java.lang.String)
     */
    public void addError(String aPropertyName, String aValue, String aErrorMessage) {
        getPropertyErrors().put(aPropertyName, aValue);
        setErrorDetail(true);
        addMessage(aErrorMessage);
    }

    /**
     * @see org.activebpel.rt.war.tags.IAeErrorAwareBean#getErrorValue(java.lang.String)
     */
    public String getErrorValue(String aPropertyName) {
        if (hasPropertyErrors()) {
            return getPropertyErrors().get(aPropertyName);
        }
        return null;
    }

    /**
     * Returns true if there are property errors
     */
    protected boolean hasPropertyErrors() {
        return mPropertyErrors != null;
    }

    /**
     * @return Returns the propertyErrors.
     */
    protected Map<String, String> getPropertyErrors() {
        if (mPropertyErrors == null) {
            mPropertyErrors = new HashMap<>();
        }
        return mPropertyErrors;
    }

    /**
     * @param aPropertyErrors The propertyErrors to set.
     */
    protected void setPropertyErrors(Map<String, String> aPropertyErrors) {
        mPropertyErrors = aPropertyErrors;
    }
}
