package org.activebpel.services.jaxws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.xml.transform.stream.StreamSource;

import junit.framework.Assert;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import bpelg.services.deploy.types.UndeploymentRequest;
import bpelg.services.urnresolver.types.AddMappingRequest;

/**
 * This test adapts a request-response BPEL process into an asynchronous exchange with UDP bindings.
 * 
 * @author markford
 */
public class AeCamelIntegrationTest extends Assert {
    AeProcessFixture pfix = new AeProcessFixture();

    @Before
    public void setUp() throws Exception {
        
        // initialize the urn resolver with the props required for the AE bpr's
        pfix.getResolver().addMapping(new AddMappingRequest().withName(
                "urn:x-vos:loancompany").withValue(
                "http://localhost:" + pfix.getCatalinaPort() + "/bpel-g/services/${urn.4}"));

        // create the zip for the camel test
        JarOutputStream jout = new JarOutputStream(new FileOutputStream("target/camel-project.zip"));
        jout.putNextEntry(new ZipEntry("META-INF/applicationContext.xml"));
        jout.write(IOUtils.toByteArray(new FileInputStream("src/test/resources/camel-project/META-INF/applicationContext.xml")));
        jout.closeEntry();

        jout.flush();
        jout.close();
        
        // deploy the zip
        pfix.deployAll(new File("target/camel-project.zip"));
        // deploy the bpr's
        pfix.deploySingle(new File("src/test/resources/loanProcessCompleted.bpr"));
        pfix.deploySingle(new File("src/test/resources/loanApproval.bpr"));
        pfix.deploySingle(new File("src/test/resources/riskAssessment.bpr"));
    }
    
    @After
    public void tearDown() throws Exception {
        // undeploy
        pfix.getDeployer().undeploy(new UndeploymentRequest().withDeploymentContainerId("camel-project.zip"));
        pfix.getDeployer().undeploy(new UndeploymentRequest().withDeploymentContainerId("loanProcessCompleted.bpr"));
        pfix.getDeployer().undeploy(new UndeploymentRequest().withDeploymentContainerId("loanApproval.bpr"));
        pfix.getDeployer().undeploy(new UndeploymentRequest().withDeploymentContainerId("riskAssessment.bpr"));
    }

    @Test
    public void deploy() throws Exception {

        // setup camel context
        // this context is used to listen for the callback from the BPEL process when the loan is approved.
        // the callback address here must match the address in the camel route we deploy with the application
        // context above
        DefaultCamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("netty:udp://localhost:60300?sync=false").to("mock:sink");
            }
        });
        
        // Let the mock endpoint know that it should receive a single message
        MockEndpoint mock = (MockEndpoint) context.getEndpoint("mock:sink");
        mock.setExpectedMessageCount(1);
        context.start();

        // prepare payload
        String body = IOUtils.toString(new FileInputStream("src/test/resources/credInfo_Smith15001.xml"));

        // send message via udp
        // use our same context here to get a ref to an endpoint that targets the input for the process
        Endpoint ep = context.getEndpoint("netty:udp://localhost:60200?sync=false");
        Exchange e = ep.createExchange();
        e.setPattern(ExchangePattern.InOnly);
        e.getIn().setBody(body);
        Producer p = ep.createProducer();
        p.start();
        p.process(e);
        
        // assert response is received - hopefully it'll complete w/in 20 seconds
        MockEndpoint.assertIsSatisfied(20, TimeUnit.SECONDS, mock);
        
        // assert response payload
        String response = (String) mock.getExchanges().get(0).getIn().getBody();
        assertNotNull(response);

        // our expected node
        Document expectedNode = (Document) pfix.toNode(new StreamSource(new FileInputStream(
        "src/test/resources/expected-response.xml")));

        // assert that we got what we expected
        pfix.assertXMLIgnorePrefix("failed to match", expectedNode, 
                pfix.toNode(new StreamSource(new StringReader(response))));
    }
}