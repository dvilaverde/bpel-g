# Deprecated #

<font color='red'><b>Please note that the information below only applies to the 5.1 release of bpel-g. Future releases (and the trunk) have dropped support for ServiceMix. The projects are still in the source tree but are not part of the build and are getting out of date.</b>
</font>


---


# Introduction #

bpel-g has a JBI packaging which can be deployed to [Apache ServiceMix 3.3.1](http://servicemix.apache.org/home.html). In this packaging, bpel-g provides a service engine component for BPEL orchestrations.

![http://bpel-g.googlecode.com/svn/trunk/bpel-g/docs/ServiceMix3.png](http://bpel-g.googlecode.com/svn/trunk/bpel-g/docs/ServiceMix3.png)

# Service Unit packaging #

## pom.xml ##
The example pom.xml below is from the bpelg-bpel-su that's part of the bpel-g project.
```
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>bpelg-test</groupId>
    <artifactId>bpelg-bpel-su</artifactId>
    <version>0.8-SNAPSHOT</version>
    <packaging>jbi-service-unit</packaging>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.servicemix.tooling</groupId>
                <artifactId>jbi-maven-plugin</artifactId>
                <version>4.0</version>
                <extensions>true</extensions>
            </plugin> 
        </plugins>
    </build>

    <properties>
        <componentName>bpel-g-jbi</componentName>
    </properties>

</project>
```

## deploy.xml ##
The deploy.xml file format is borrowed from the Apache ODE project. It provides a simple structure for defining the bindings for the partner link's myRole and partnerRole endpoints.

The deploy.xml file must contain one entry for each of the BPEL files you are deploying along with bindings for the myRole and partnerRole services. If a partnerRole service is left unbound then it is assumed to be a dynamically assigned endpoint reference and will not be initialized with any value. The myRole binding will manifest as **internal** endpoints in ServiceMix.

The example deploy.xml below is from the bpelg-bpel-su that's part of the bpel-g project.
```
<?xml version="1.0" encoding="UTF-8"?>
<deploy xmlns="http://www.apache.org/ode/schemas/dd/2007/03"
    xmlns:test="urn:bpelg:test"
    xmlns:example="http://www.example.org/test/">
    
    <process name="test:test">
        <provide partnerLink="testPartnerLinkType">
            <service name="example:test" port="test"/>
        </provide>
    </process>

    <process name="test:testInvoke">
        <provide partnerLink="testPartnerLinkType">
            <service name="example:test" port="testInvoke"/>
        </provide>
        <invoke partnerLink="testPartnerLinkType2">
            <service name="example:test2" port="testInvokeBpelReceiver"/>
        </invoke>
    </process>

    <process name="test:testInvokeBpelReceiver">
        <provide partnerLink="testPartnerLinkType2">
            <service name="example:test2" port="testInvokeBpelReceiver"/>
        </provide>
    </process>
</deploy>
```

## SU layout ##
```
example-su
 |- src
 |   \- main
 |       \- resources
 |          |- example.bpel
 |          |- example.xsl
 |          |- deploy.xml
 |          \- wsdl
 |             |- example.wsdl
 |             |- other.wsdl
 |          \- xsd
 |             |- myschema.xsd
 |- pom.xml
```

## Imports ##
  * Imports from the wsdl's to the or other wsdl's xsd's MUST be relative.
  * imports from the bpel's to the wsdl's or xsd's SHOULD be relative
  * references to xsl files via bpel:doXSLTransform() need to be prefixed with "project:/" + $ServiceUnitName. In the example above, a reference to example.xsl would be done as follows: bpel:doXSLTransform( 'project:/example-su/example.xsl', ...)

# Creating external endpoints #
If you want to invoke your BPEL as a SOAP/HTTP service, you'll need to provide an appropriate binding within your assembly. See the example that's included in the source to see how to construct an assembly that includes a servicemix-http binding. Remember, bpel-g **will not** automatically generate an external binding component for you. It's up to you to decide how the service is accessible from outside the JBI bus.

# Database Configuration #
$SERVICEMIX\_HOME/conf/jndi.xml needs to have an entry for the DataSource. The following xml snippet should be pasted within the util:map block:

```

  <util:map id="jndiEntries">

    <!-- existing entries -->

    <entry key="java:comp/env/jdbc/ActiveBPELDB">
            <bean class="org.h2.jdbcx.JdbcDataSource">
                <property name="URL" value="jdbc:h2:~/bpelg-h2-db;MVCC=TRUE"/>
                <property name="user" value="sa"/>
                <property name="password" value="sa"/>
            </bean>
        </entry>
  </util:map>
```

Also need to put the H2 jar in $SERVICEMIX\_HOME/lib

### Embedded Database ###
If you don't map a DataSource to bpel-g's JNDI context as shown above, then it will create an embedded database in the component's install directory. Keep in mind that this database will be rebuilt if you install new versions of the component so it's not a good solution for keeping your process instances across updates.