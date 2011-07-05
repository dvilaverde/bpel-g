//$Header: /Development/AEDevelopment/projects/org.activebpel.rt.axis/src/org/activebpel/rt/axis/ser/AeGYearMonthDeserializer.java,v 1.2 2006/09/07 15:19:54 ewittmann Exp $
/////////////////////////////////////////////////////////////////////////////
//PROPRIETARY RIGHTS STATEMENT
//The contents of this file represent confidential information that is the 
//proprietary property of Active Endpoints, Inc.  Viewing or use of 
//this information is prohibited without the express written consent of 
//Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT 
//is strictly forbidden. Copyright (c) 2002-2006 All rights reserved. 
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.axis.ser; 

import javax.xml.namespace.QName;

import org.activebpel.rt.xml.schema.AeSchemaYearMonth;
import org.apache.axis.encoding.ser.SimpleDeserializer;

/**
 * Deserializer for schema calendar field gYearMonth 
 */
public class AeGYearMonthDeserializer extends SimpleDeserializer
{
   /**
     * 
     */
    private static final long serialVersionUID = 4151994783762419216L;

/**
    * The Deserializer is constructed with the xmlType and javaType
    */
   public AeGYearMonthDeserializer(Class<AeSchemaYearMonth> javaType, QName xmlType)
   {
      super(javaType, xmlType);
   }

}
 