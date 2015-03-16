
# Introduction #
This project provies a fully compliant BPEL4WS 1.1 and WS-BPEL 2.0 engine.
The code here is based on the final release of ActiveBPEL 5.0.2. The goal of
bpel-g is to provide a viable BPEL engine for the open source community, particularly
those in the public sector. The main focus is spec compliance.

# Building #

## Maven ##
bpel-g uses maven. You should be using maven 2.2.1 or later.

### Repo ###
A couple of the projects require artifacts which aren't available on public repositories. The svn repo contains these dependencies and should be added to your local maven proxy if you get build errors.

```
    <repository>
      <releases>
        <enabled>true</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
      <id>bpel-g-repo</id>
      <url>http://bpel-g.googlecode.com/svn/trunk/maven-repository</url>
    </repository>
```

# Database #
The ddl project in bpel-g contains scripts for Oracle, DB2, MySQL, SQL Server and H2. The server will create an embedded H2 database if none is configured at startup. This is the easiest way to get started and provides decent performance.

## Configuring WAR ##
The WAR project has a context that is already configured to reference the database created during the build. Be sure to put the H2 jar within the $CATALINA\_HOME/lib directory.

# Deployments #

## BPR ##
The Business Process Archive (or BPR) is a zip format defined by ActiveVOS and produced by their designer too. Check the [ActiveVOS](http://activevos.com) web site for information on how to construct a BPR or simply use their BPEL designer to create one.

## deploy.xml ##
Check the Apache ODE site for info on how to construct a zip with their deploy.xml metadata file. I won't recreate the docs here but rather mention a few points about how the deploy.xml file is parsed and bpel-g specific behaviors.

### in-memory ###
You can control whether a process is persistent or in-memory only by including the in-memory child element under the process element as shown below.

```
    <process name="test:test">
        <in-memory>true</in-memory>
        <provide partnerLink="testPartnerLinkType">
            <service name="example:test" port="test"/>
        </provide>
    </process>
```

A process that is in-memory only will not be saved to the database. These are in-flight processes that can consist of a single inbound message activity and no alarms. The default setting is false which fully persists processes after they reach a stage where there is no activity running and the process lag timeout setting on the server is exceeded.

### message validation ###
Message level validation can be enabled/disabled on each partner link. This is done with a WS-Policy directive set on the partner link within the deploy.xml as shown below.

```
        <provide partnerLink="testPartnerLinkType">
            <service name="example:test" port="testInvoke">
                <wsp:Policy xmlns:abp="http://schemas.active-endpoints.com/ws/2005/12/policy" 
xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy">
                    <abp:Validation direction="none"/>
                </wsp:Policy>
            </service>
        </provide>
```

If the validation directive is not specified, then the engine level setting determines whether the message is validated.

### custom invoke handler ###
Invoke activities are handled by a SOAP style invoke handler by default. This can be changed in order to take advantage of a custom invoke handler like a Java POJO or Apache Camel Endpoint. The engine registry contains a mapping of well known names to classes that create a invoke handler. The deploy.xml allows you to specify one of these named factories within its wsa:EndpointReference for the partnerlink as shown below.

```
        <invoke partnerLink="plt2">
            <service name="proc:camel-invoke" port="notification">
                <wsa:EndpointReference>
                    <wsa:Address>netty:udp://localhost:60300?sync=false</wsa:Address>
                    <ae:invokeHandler>camel</ae:invokeHandler>
                </wsa:EndpointReference>
            </service>
        </invoke>
```

### service/port ###
The service and port values aren't significant in the deploy.xml. The real addressing details for an endpoint are specified by a wsa:EndpointReference. Currntly, the 2008 version of Addressing is the only one supported. Use the namespace: "http://www.w3.org/2005/08/addressing".

## Process Validation ##
The default behavior during deployment is to validate the bpel, deployment artifacts, and WSDL. It is possible to disable this validation (although not recommended) by including a file named "skip.validation" inside the BPR or service unit. If present, none of the resources within the deployment unit will be validated. This was added to address a situation where deployment speed was essential and the bpel and associated artifacts were all known to be valid.

# Sample Projects #

Summary of the sample projects packaged in the process-tests module.

| **Module** | Description |
|:-----------|:------------|
| camel-invoke | Demonstrates a bpel process that invokes an endpoint through an Apache Camel invoke handler. The protocol for the endpoint is a simple UDP address but the applicationContext.xml file in the project could be used to provide more sophisticated processing if needed |
| camel-receive | Demonstrates routing a message into bpel-g from a camel endpoint via the custom bpel-g camel component. |
| correlation-test | Demonstrates the correlation of messages. Includes a process that responds with a quote and awaits a second call to approve the quote. Also includes a test driver process for this process that invokes it and always responds yes to the quote. This serves to demonstrate a simple web service invoke since BPEL processes are themselves web services. |
| process-with-errors | Contains a number of errors in the BPEL definition as a test that the errors will propagate back to the caller and that the process will fail to deploy. |
| camel-activemq-test| Similar to the camel-receive test. Adapts a BPEL process to receive messages from an ActiveMQ topic. The integration test for this is currently disabled until I restore all of the ActiveMQ dependencies |

The integration-tests module contains test drivers for the above projects. This module lists all of the BPEL modules as a dependency and has a integration tests that deploy the BPEL jars, executes their processes, asserts the results, and un-deploys the jars.

The integration-test module provides some good examples at deploying and undeploying processes through the web service layer. Check out the source in the process-tests/integration-test and support module.