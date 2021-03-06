//$Header: /Development/AEDevelopment/projects/org.activebpel.rt/src/org/activebpel/rt/xml/schema/AeSchemaBase64Binary.java,v 1.3 2006/10/12 20:04:51 EWittmann Exp $
/////////////////////////////////////////////////////////////////////////////
//PROPRIETARY RIGHTS STATEMENT
//The contents of this file represent confidential information that is the
//proprietary property of Active Endpoints, Inc.  Viewing or use of
//this information is prohibited without the express written consent of
//Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
//is strictly forbidden. Copyright (c) 2002-2006 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.xml.schema;

import org.activebpel.rt.AeMessages;
import org.apache.commons.codec.binary.Base64;

/**
 * Wrapper object for xsd:base64Binary simple data type. This ensures that Axis serializes the data correctly
 */
public class AeSchemaBase64Binary extends AeSchemaWrappedStringType {
    /**
     * Parsed-out binary data.
     */
    private byte[] mBinaryData;

    /**
     * Ctor accepts the base64 binary value.
     *
     * @param aBase64BinaryString
     */
    public AeSchemaBase64Binary(String aBase64BinaryString) {
        super(aBase64BinaryString);

        parseBase64String(aBase64BinaryString);
    }

    /**
     * Parses the base64 string into its binary form.
     *
     * @param aBase64BinaryString
     */
    protected void parseBase64String(String aBase64BinaryString) {
        byte[] data = null;
        // Decode the string here.  Keep it null if the decode fails.
        try {
            data = Base64.decodeBase64(aBase64BinaryString);
        } catch (Throwable ex) {
        }

        if (data == null || data.length == 0)
            throw new AeSchemaTypeParseException(AeMessages.getString("AeSchemaBase64Binary.ParseFailureError")); //$NON-NLS-1$

        setBinaryData(data);
    }

    /**
     * @see org.activebpel.rt.xml.schema.IAeSchemaType#accept(org.activebpel.rt.xml.schema.IAeSchemaTypeVisitor)
     */
    public void accept(IAeSchemaTypeVisitor aVisitor) {
        aVisitor.visit(this);
    }

    /**
     * @return Returns the binaryData.
     */
    public byte[] getBinaryData() {
        return mBinaryData;
    }

    /**
     * @param aBinaryData The binaryData to set.
     */
    protected void setBinaryData(byte[] aBinaryData) {
        mBinaryData = aBinaryData;
    }
}
