// $Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel.server/src/org/activebpel/rt/bpel/server/addressing/AePartnerAddressing.java,v 1.31 2008/02/17 21:38:50 mford Exp $
/////////////////////////////////////////////////////////////////////////////
//               PROPRIETARY RIGHTS STATEMENT
// The contents of this file represent confidential information that is the
// proprietary property of Active Endpoints, Inc.  Viewing or use of
// this information is prohibited without the express written consent of
// Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT
// is strictly forbidden. Copyright (c) 2002-2004 All rights reserved.
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.server.addressing;

import bpelg.services.processes.types.ServiceDeployment;
import org.activebpel.rt.AeException;
import org.activebpel.rt.IAeConstants;
import org.activebpel.rt.IAePolicyConstants;
import org.activebpel.rt.bpel.AeBusinessProcessException;
import org.activebpel.rt.bpel.AeWSDLDefHelper;
import org.activebpel.rt.bpel.IAeEndpointReference;
import org.activebpel.rt.bpel.def.AePartnerLinkDef;
import org.activebpel.rt.bpel.impl.AeEndpointReference;
import org.activebpel.rt.bpel.impl.addressing.*;
import org.activebpel.rt.bpel.server.AeMessages;
import org.activebpel.rt.bpel.server.IAeProcessDeployment;
import org.activebpel.rt.bpel.server.deploy.IAePolicyMapper;
import org.activebpel.rt.bpel.server.engine.AeEngineFactory;
import org.activebpel.rt.bpel.urn.IAeURNResolver;
import org.activebpel.rt.util.AeUtil;
import org.activebpel.rt.util.AeXmlUtil;
import org.activebpel.rt.wsdl.def.AeBPELExtendedWSDLDef;
import org.activebpel.wsio.AeWsAddressingException;
import org.activebpel.wsio.IAeWebServiceEndpointReference;
import org.activebpel.wsio.IAeWsAddressingHeaders;
import org.w3c.dom.*;

import javax.inject.Inject;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.xml.namespace.QName;
import javax.xml.rpc.handler.soap.SOAPMessageContext;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import java.util.List;
import java.util.Map;

/**
 * The partner addressing layer is responsible for providing endpoint references
 * for each of the partners that the business process interacts with. The
 * endpoint data either exists as part of the deployment descriptor, or as part of
 * the message context.
 */
public class AePartnerAddressing implements IAePartnerAddressing {
    @Inject
    private IAeURNResolver mURNResolver;

    /**
     * @see org.activebpel.rt.bpel.server.addressing.IAePartnerAddressing#getReplyAddressing(org.activebpel.wsio.IAeWsAddressingHeaders, java.lang.String)
     */
    public IAeAddressingHeaders getReplyAddressing(IAeWsAddressingHeaders aSource, String aAction) throws AeBusinessProcessException {
        AeAddressingHeaders outAddrHeaders = new AeAddressingHeaders(aSource.getSourceNamespace());

        // Get the address that was invoked and
        // use it for the From address
        String incommingTo = aSource.getTo();
        String fromAddressURI = incommingTo;
        IAeEndpointReference from = new AeEndpointReference();
        from.setAddress(fromAddressURI);
        from.setSourceNamespace(aSource.getSourceNamespace());
        outAddrHeaders.setFrom(from);

        // Add RelatesTo
        String incommingMessageId = aSource.getMessageId();
        if (!AeUtil.isNullOrEmpty(incommingMessageId)) {
            outAddrHeaders.addRelatesTo(incommingMessageId, outAddrHeaders.getReplyRelationshipName());
        } else {
            // WS-Addressing requires a RelatesTo header if a reply is expected
            if (aSource.getReplyTo() != null)
                throw new IllegalArgumentException(AeMessages.getString("AePartnerAddressing.0")); //$NON-NLS-1$
        }

        //Setting To endpoint
        if (outAddrHeaders.getFaultAction().equals(aAction) && (aSource.getFaultTo() != null)) {
            // For wsa fault action, use the FaultTo address, if any
            IAeEndpointReference endpoint = (IAeEndpointReference) aSource.getFaultTo();
            outAddrHeaders.setRecipient(endpoint);
        } else if (aSource.getReplyTo() != null) {
            // Use reply to, if specified
            IAeEndpointReference endpoint = (IAeEndpointReference) aSource.getReplyTo();
            outAddrHeaders.setRecipient(endpoint);
        } else if (aSource.getFrom() != null) {
            // otherwise, use from if available
            IAeEndpointReference endpoint = (IAeEndpointReference) aSource.getFrom();
            outAddrHeaders.setRecipient(endpoint);
        } else {
            // use anonymous role if no other choice
            outAddrHeaders.setTo(outAddrHeaders.getAnonymousRole());
        }

        try {
            for (Element element : outAddrHeaders.getRecipient().getReferenceProperties()) {
                Element el = AeXmlUtil.cloneElement(element);
                if (el.getNamespaceURI().equals(IAeConstants.WSA_NAMESPACE_URI_2005_08)) {
                    el.setAttributeNS(IAeConstants.WSA_NAMESPACE_URI_2005_08, "wsa:IsReferenceParameter", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                outAddrHeaders.addHeaderElement(el);
            }
        } catch (AeWsAddressingException wse) {
            throw new AeBusinessProcessException(AeMessages.getString("AePartnerAddressing.2"), wse); //$NON-NLS-1$
        }

        // Set the action to the one given
        outAddrHeaders.setAction(aAction);

        return outAddrHeaders;
    }

    /**
     * @see org.activebpel.rt.bpel.server.addressing.IAePartnerAddressing#readFromDeployment(org.w3c.dom.Element)
     */
    public IAeEndpointReference readFromDeployment(Element aElement) throws AeBusinessProcessException {
        AeEndpointReference endpoint = null;

        DocumentFragment frag = aElement.getOwnerDocument().createDocumentFragment();
        for (Node child = aElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE)
                frag.appendChild(child.cloneNode(true));
        }
        endpoint = new AeEndpointReference();
        endpoint.setReferenceData(AeXmlUtil.getFirstSubElement(frag));

        return endpoint;
    }

    /**
     * @see org.activebpel.rt.bpel.server.addressing.IAePartnerAddressing#readFromContext(javax.xml.rpc.handler.soap.SOAPMessageContext)
     */
    public IAeEndpointReference readFromContext(SOAPMessageContext aContext) throws AeBusinessProcessException {
        IAeEndpointReference ref = null;
        try {
            // Get the reply addressing headers
            SOAPHeader header = aContext.getMessage().getSOAPHeader();
            IAeAddressingDeserializer deserializer = AeWsAddressingFactory.getInstance().getDeserializer(IAeConstants.WSA_NAMESPACE_URI);
            IAeAddressingHeaders headers = deserializer.deserializeHeaders(header);
            IAeAddressingHeaders replyAddressing = getReplyAddressing(headers, headers.getAction());

            // Create an endpoint ref
            ref = new AeEndpointReference();
            ref.setReferenceData(replyAddressing.getRecipient());

            return ref;
        } catch (SOAPException e) {
            AeException.logError(e);
        }
        return ref;
    }

    /**
     * Merges 2 endpoints to update the target with information from the source
     *
     * @param aSource endpoint to merge
     * @param aTarget existing partner endpoint.  If this endpoint is not null,
     *                the address and service info is taken from here.
     * @return merged endpoint
     */
    public IAeEndpointReference mergeReplyEndpoint(IAeWebServiceEndpointReference aSource, IAeWebServiceEndpointReference aTarget) throws AeBusinessProcessException {
        if (aSource == null && aTarget == null)
            return null;

        // Use target (if it exists), or brand new from the source
        IAeEndpointReference newReplyTo = new AeEndpointReference();
        if (aTarget != null) {
            newReplyTo.setReferenceData(aTarget);
            if (aSource != null) {
                if (AeUtil.notNullOrEmpty(aSource.getReferenceProperties()) ||
                        AeUtil.notNullOrEmpty(aSource.getReferenceProperties())) {
                    // add reference properties from both endpoints
                    AeAddressingHeaders wsa = new AeAddressingHeaders(newReplyTo.getSourceNamespace());
                    try {
                        wsa.setReferenceProperties(aSource.getReferenceProperties());
                        wsa.setReferenceProperties(aTarget.getReferenceProperties());
                        // calling the update here will have us recursively handle embedded wsa headers buried deeper in the epr
                        newReplyTo = updateEndpointHeaders(wsa, newReplyTo);
                    } catch (AeWsAddressingException wse) {
                        throw new AeBusinessProcessException(wse.getLocalizedMessage(), wse);
                    }
                }
            }
        } else {
            newReplyTo.setReferenceData(aSource);
        }

        // Resolve the URN for the address
        IAeURNResolver resolver = getURNResolver();
        String url = newReplyTo.getAddress();
        newReplyTo.setAddress(resolver.getURL(url));

        return newReplyTo;
    }

    /**
     * @see org.activebpel.rt.bpel.server.addressing.IAePartnerAddressing#updateEndpointHeaders(org.activebpel.rt.bpel.impl.addressing.IAeAddressingHeaders, org.activebpel.wsio.IAeWebServiceEndpointReference)
     */
    public IAeEndpointReference updateEndpointHeaders(IAeAddressingHeaders aWsaHeaders, IAeWebServiceEndpointReference aEndpoint) throws AeBusinessProcessException {
        AeEndpointReference epr = new AeEndpointReference();
        epr.setReferenceData(aEndpoint);

        // resolve URN mappings for To address
        String toAddress = getURNResolver().getURL(epr.getAddress());
        epr.setAddress(toAddress);
        // Set To addressing with the endpoint
        aWsaHeaders.setRecipient(epr);

        // pull the replyTo from the wsa headers
        IAeWebServiceEndpointReference wsaReplyTo = aWsaHeaders.getReplyTo();
        // pull the faultTo from the wsa headers
        IAeWebServiceEndpointReference wsaFaultTo = aWsaHeaders.getFaultTo();

        // Setting the reference properties on the addressing holder
        // ensures that wsa headers embedded in the partner endpoint are handled properly
        // replacing the values in the wsa headers with any info embedded in reference properties
        try {
            aWsaHeaders.addReferenceProperties(epr.getReferenceProperties());
        } catch (AeWsAddressingException wse) {
            throw new AeBusinessProcessException(AeMessages.getString("AePartnerAddressing.1"), wse); //$NON-NLS-1$
        }
        // Clear existing reference properties on our endpoint holder so they don't get added again
        epr.clearReferenceProperties();

        // merge the replyto
        aWsaHeaders.setReplyTo(mergeReplyEndpoint(wsaReplyTo, aWsaHeaders.getReplyTo()));
        // merge the faultto
        aWsaHeaders.setFaultTo(mergeReplyEndpoint(wsaFaultTo, aWsaHeaders.getFaultTo()));

        // Create the addressing headers
        IAeAddressingSerializer ser = AeWsAddressingFactory.getInstance().getSerializer(aWsaHeaders.getSourceNamespace());
        Document headerDoc = ser.serializeToDocument(aWsaHeaders);
        // Add all headers as reference properties on partner link
        if (headerDoc != null) {
            NodeList nodes = headerDoc.getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) nodes.item(i);
                    epr.addReferenceProperty(element);
                }
            }
        }
        return epr;
    }

    /**
     * @see org.activebpel.rt.bpel.server.addressing.IAePartnerAddressing#getMyRoleEndpoint(org.activebpel.rt.bpel.server.IAeProcessDeployment, org.activebpel.rt.bpel.def.AePartnerLinkDef, javax.xml.namespace.QName, java.lang.String)
     */
    public IAeEndpointReference getMyRoleEndpoint(IAeProcessDeployment aProcessDeployment, AePartnerLinkDef aPartnerLink, QName aProcessName, String aConversationId) throws AeBusinessProcessException {
        // Find the service definition for this partner link
        ServiceDeployment service = aProcessDeployment.getServiceInfo(aPartnerLink.getLocationPath());

        if (service == null) {
            // no myrole on partner link, so no implicit replyTo
            return null;
        }

        AeEndpointReference myRoleRef = new AeEndpointReference();
        String url = service.getService();
        myRoleRef.setAddress(url);

        AeBPELExtendedWSDLDef cachedDef = AeWSDLDefHelper.getWSDLDefinitionForPLT(aProcessDeployment, aPartnerLink.getPartnerLinkTypeName());

        if (cachedDef != null) {
            QName serviceName = new QName(cachedDef.getTargetNamespace(), service.getService());
            myRoleRef.setServiceName(serviceName);
            QName portType = aPartnerLink.getMyRolePortType();
            myRoleRef.setPortType(portType);

            Service wsdlService = cachedDef.getServices().get(serviceName);
            if (wsdlService != null) {
                Map ports = wsdlService.getPorts();
                for (Object o : ports.values()) {
                    Port port = (Port) o;
                    if (port.getBinding().getPortType().getQName().equals(portType)) {
                        myRoleRef.setServicePort(port.getName());
                        break;
                    }
                }
            }
        }
        List<Element> policies = service.getAny();
        if (!AeUtil.isNullOrEmpty(policies)) {
            try {
                for (Element policy : policies) {
                    myRoleRef.addPolicyElement(policy);
                }

                // create a correlation header
                if (aConversationId != null) {
                    // get the correlationId property from the mapper
                    Map<String, Object> props = AeEngineFactory.getBean(IAePolicyMapper.class).getCallProperties(policies);
                    QName correlationProperty = (QName) props.get(IAePolicyConstants.TAG_ASSERT_MANAGED_CORRELATION);
                    if (correlationProperty != null) {
                        myRoleRef.addProperty(correlationProperty, aConversationId);
                        //  create a header element for the conversation id
                        QName headerName = IAePolicyConstants.CONVERSATION_ID_HEADER;
                        Document doc = AeXmlUtil.newDocument();
                        Element convHeader = doc.createElementNS(headerName.getNamespaceURI(), headerName.getLocalPart());
                        convHeader.appendChild(doc.createTextNode(aConversationId));
                        myRoleRef.addReferenceProperty(convHeader);
                    }
                }
            } catch (AeException ae) {
                throw new AeBusinessProcessException(ae.getLocalizedMessage(), ae);
            }
        }
        return myRoleRef;
    }

    public IAeURNResolver getURNResolver() {
        return mURNResolver;
    }

    public void setURNResolver(IAeURNResolver aURNResolver) {
        mURNResolver = aURNResolver;
    }
}
