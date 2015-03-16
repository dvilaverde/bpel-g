# Apache Camel #

[Apache Camel](http://camel.apache.org/) is a popular open source Java implementation of the [Enterprise Integration Patterns](http://www.enterpriseintegrationpatterns.com/toc.html). The 5.2 release for bpel-g includes the core dependencies from Camel in order to provide endpoint abstraction, mediation, or transformation tasks.

# Camel Invoke Handler #
A custom invoke handler supports the integration of camel endpoints within a process. The camel invoke handler is targeted by using the uri scheme "camel" for the custom invoke handler. This scheme is set in the deployment context for the process whether it's in a BPR file or ODE style deploy.xml.

For example:

```
   <wsa:EndpointReference>
      <wsa:Address>vm:endpointname?optional-params</wsa:Address>
   </wsa:EndpointReference>
```

Note: the current invoke handler only has access to the camel context that was defined in its spring application [context](SpringDeploymentHandler.md). The example above shows the use of the vm endpoint.

# Camel Component #
bpel-g includes a camel component that binds the uri scheme "bpel" in order to route messages into the engine and also to undeploy processes. Other commands will be added as needed.

## invoking a process ##

Routes can invoke a process by providing the partnerLink and process QName. These values are passed in as message headers and can therefore be set statically through the context or dynamically with expressions.

```

    <camelContext id="jms-test" xmlns="http://camel.apache.org/schema/spring">
        <route>
            <!-- Routes always start with a from. -->
            <from uri="activemq:queue:bpel-inbound"/>
            <setHeader headerName="partnerLink">
                <constant>LoanProcess</constant>
            </setHeader>
            <setHeader headerName="processNamespace">
                <constant>http://docs.active-endpoints.com/sample/bpel/loanprocess/2008/02/loanProcessCompleted.bpel</constant>
            </setHeader>
            <setHeader headerName="processLocalPart">
                <constant>loanProcessCompleted</constant>
            </setHeader>
            
            <!-- Not much to configure on the component itself since all of the inputs are headers -->
            <to uri="bpel:invoke"/>
            
            <convertBodyTo type="java.lang.String"/>
            
            <!-- dumping the contents of the current message out for debugging only -->
            <to uri="stream:out"/>
            <!-- sends the output message back to our mock endpoint listening on the outbound queue -->
            <to uri="activemq:queue:bpel-outbound"/>
        </route>
    </camelContext>
```

## undeploying a process ##

The undeploy command undeploys all of the processes within the specified container. The name of the container is passed into the component via the containerId message header. This allows for the static or dynamic configuration of the component via a fixed endpoint.

The motivation for the undeploy command was a use case where processes were short lived and required undeploying as soon as the process completed executing. This was accomplished with a simple route triggered off of a process completion event and the custom camel component. It's not clear how useful this functionality will be in the wild.