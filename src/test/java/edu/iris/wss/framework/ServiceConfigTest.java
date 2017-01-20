/*******************************************************************************
 * Copyright (c) 2014 IRIS DMC supported by the National Science Foundation.
 *
 * This file is part of the Web Service Shell (WSS).
 *
 * The WSS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * The WSS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * A copy of the GNU Lesser General Public License is available at
 * <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package edu.iris.wss.framework;
/*
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.spi.container.servlet.ServletContainer;*/
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author mike
 */
public class ServiceConfigTest  {

    public static final Logger logger = Logger.getLogger(ServiceConfigTest.class);

    private static final String BASE_HOST = "http://localhost";
    private static final Integer BASE_PORT = 8093;

    // set notional webapp name
    private static final String SOME_CONTEXT = "/tstsegment";

    private static final URI BASE_URI = URI.create(BASE_HOST + ":"
        + BASE_PORT + SOME_CONTEXT);

    public ServiceConfigTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // setup config dir for test environment
        System.setProperty(Util.WSS_OS_CONFIG_DIR,
            "target"
              + File.separator + "test-classes"
              + File.separator + "ServiceConfigTest");

        createTestCfgFile(System.getProperty(Util.WSS_OS_CONFIG_DIR),
              SOME_CONTEXT + "-service.cfg");

        logger.info("*********** starting grizzlyWebServer, BASE_URI: "
            + BASE_URI);

        GrizzlyContainerHelper.setUpServer(BASE_URI, ServiceConfigTest.class.getName(),
              SOME_CONTEXT);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        GrizzlyContainerHelper.tearDownServer(ServiceConfigTest.class.getName());
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGet_wssversion() throws Exception {
        Client c = ClientBuilder.newClient();
        WebTarget webTarget = c.target(BASE_URI);
        System.out.println("************** wT: " + webTarget);
        Response response = webTarget.path("wssversion").request().get();

        assertNotNull(response);
        String testMsg = response.readEntity(String.class);
        assertEquals(200, response.getStatus());
        assertTrue(testMsg.equals(AppConfigurator.wssVersion));
    }

    @Test
    public void testGet_status() throws Exception {
        Client c = ClientBuilder.newClient();
        WebTarget webTarget = c.target(BASE_URI);
        Response response = webTarget.path("wssstatus").request().get();

        String testMsg = response.readEntity(String.class);
        assertEquals(200, response.getStatus());

        // test for some basic known content
//        System.out.println("* -------------------------------------------- testMsg: " + testMsg);
        assertTrue(testMsg.contains("<TD>URL</TD><TD>" + SOME_CONTEXT + "/wssstatus</TD>"));
        assertTrue(testMsg.contains("<TD>Port</TD><TD>" + BASE_PORT + "</TD>"));
    }

    @Test
    public void testGet_CLIENTNAME() throws Exception {
        Client c = ClientBuilder.newClient();
        WebTarget webTarget = c.target(BASE_URI);
        Response response = webTarget.path("query_client_name").request().get();

        String testMsg = response.readEntity(String.class);
        assertEquals(200, response.getStatus());

        // subject to change if BASE_HOST, testMsg allso containes \n
        assertTrue(testMsg.contains("localhost"));
    }

    // create a config file to test against on a target test path
    private static void createTestCfgFile(String filePath, String fileName)
          throws FileNotFoundException, IOException {

        File testFile = new File(filePath + File.separator + fileName);
        OutputStream os = new FileOutputStream(testFile);

        StringBuilder sb = new StringBuilder();

        sb.append("# ---------------- globals").append("\n");
        sb.append("\n");
        sb.append("appName=ServiceConfigTest-").append(SOME_CONTEXT.substring(1)).append("\n");
        sb.append("version=default-0.123").append("\n");
        sb.append("\n");
        sb.append("# CORS is enabled by default, set to false to disable CORS processing").append("\n");
        sb.append("#corsEnabled=false").append("\n");
        sb.append("\n");
        sb.append("##rootServiceDoc=http://service/fdsnwsbeta/dataselect/docs/1/root/").append("\n");
        sb.append("rootServiceDoc=file:///Users/tomcat/tomcat-8092-7.0.54/dataselect_config1/dataselect-root.html").append("\n");
        sb.append("\n");
        sb.append("# Override the default 100msec SIGKILL delay (from SIGTERM signal)").append("\n");
        sb.append("sigkillDelay=200").append("\n");
        sb.append("\n");
        sb.append("# If present, an instance of the singleton class will be created at application start").append("\n");
        sb.append("singletonClassName=edu.iris.wss.provider.TestSingleton").append("\n");
        sb.append("\n");
        sb.append("# LOG4J or JMS").append("\n");
        sb.append("loggingMethod=LOG4J").append("\n");
        sb.append("\n");
        sb.append("# ----------------  endpoints").append("\n");
        sb.append("\n");
        sb.append("queryEP.endpointClassName=edu.iris.wss.endpoints.CmdProcessor").append("\n");

        // determine full file path within this test environment
        File file = new File(filePath + File.separator + "sleep_handle2.sh");
        file.setExecutable(true);
        sb.append("queryEP.handlerProgram=").append(file.getAbsolutePath()).append("\n");

        sb.append("queryEP.handlerWorkingDirectory=/tmp").append("\n");
        sb.append("\n");
        sb.append("# Timeout in seconds for command line implementation.  Pertains to initial and ongoing waits.").append("\n");
        sb.append("queryEP.handlerTimeout=40").append("\n");
        sb.append("\n");
        sb.append("queryEP.formatTypes = \\").append("\n");
        sb.append("    miniseed: application/vnd.fdsn.mseed,\\").append("\n");
        sb.append("    mseed: application/vnd.fdsn.mseed,\\").append("\n");
        sb.append("    text: text/plain,\\").append("\n");
        sb.append("    json: application/json, \\").append("\n");
        sb.append("    texttree: text/plain,\\").append("\n");
        sb.append("    xml: application/xml").append("\n");
        sb.append("\n");
        sb.append("# usageLog is true by default, set this to false to disable usage logging").append("\n");
        sb.append("##queryEP.usageLog=false").append("\n");
        sb.append("\n");
        sb.append("# Disable or remove this to disable POST processing").append("\n");
        sb.append("queryEP.postEnabled=true").append("\n");
        sb.append("\n");
        sb.append("# Enable this to return HTTP 404 in lieu of 204, NO CONTENT").append("\n");
        sb.append("queryEP.use404For204=true").append("\n");
        sb.append("\n");
        sb.append("\n");
        sb.append("# ----------------  endpoints").append("\n");
        sb.append("\n");
        sb.append("queryEP.endpointClassName=edu.iris.wss.endpoints.CmdProcessor").append("\n");

        // determine full file path within this test environment
        file = new File(filePath + File.separator + "echo_CLIENTNAME.sh");
        file.setExecutable(true);
        sb.append("query_client_name.handlerProgram=").append(file.getAbsolutePath()).append("\n");

        sb.append("query_client_name.handlerWorkingDirectory=/tmp").append("\n");
        sb.append("\n");
        sb.append("# Timeout in seconds for command line implementation.  Pertains to initial and ongoing waits.").append("\n");
        sb.append("query_client_name.handlerTimeout=40").append("\n");
        sb.append("\n");
        sb.append("query_client_name.formatTypes = \\").append("\n");
        sb.append("    text: text/plain,\\").append("\n");
        sb.append("    json: application/json, \\").append("\n");
        sb.append("    texttree: text/plain,\\").append("\n");
        sb.append("    xml: application/xml").append("\n");
        sb.append("\n");
        sb.append("# Enable this to return HTTP 404 in lieu of 204, NO CONTENT").append("\n");
        sb.append("query_client_name.use404For204=true").append("\n");
        sb.append("\n");

        os.write(sb.toString().getBytes());
    }
}
