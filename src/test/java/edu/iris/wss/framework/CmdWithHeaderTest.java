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
import edu.iris.wss.utils.WebUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.servlet.GrizzlyWebContainerFactory;
import org.glassfish.jersey.servlet.ServletProperties;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author mike
 */
public class CmdWithHeaderTest  {

    public static final Logger logger = Logger.getLogger(CmdWithHeaderTest.class);

    private static final String BASE_HOST = "http://localhost";
    private static final Integer BASE_PORT = 8093;
    
    // set notional webapp name
//    private static final String SOME_CONTEXT = "/testservice/dataselect/1";
    private static final String SOME_CONTEXT = "/tstcmd";
    
    private static final URI BASE_URI = URI.create(BASE_HOST + ":"
        + BASE_PORT + SOME_CONTEXT);

    private static HttpServer server;
    
    public CmdWithHeaderTest() {
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        // setup config dir for test environment
        System.setProperty(WebUtils.wssConfigDirSignature,
            "src"
              + File.separator + "test"
              + File.separator + "resources"
              + File.separator + "ServiceConfigTest");

        logger.info("*********** starting grizzlyWebServer, BASE_URI: "
            + BASE_URI);
        
        Map<String, String> initParams = new HashMap<>();
        initParams.put(
            ServletProperties.JAXRS_APPLICATION_CLASS,
            MyApplication.class.getName());

        logger.info("*** starting grizzly container with parameters: " + initParams);
        System.out.println("********** start GrizzlyWebContainerFactory");

        server = GrizzlyWebContainerFactory.create(BASE_URI, initParams);
      
        server.start();
        System.out.println("********** started GrizzlyWebServer, class: "
            + CmdWithHeaderTest.class.getName());
        System.out.println("********** started GrizzlyWebServer, config: "
            + server.getServerConfiguration());

        // for manual test of server, uncomment this code then mvn clean install
//        System.out.println("***** Application started, try: " + BASE_URI);
//        System.out.println("***** control-c to stop its...");
//        System.in.read();
    }
    
    @AfterClass
    public static void tearDownClass() {
        System.out.println("********** stopping grizzlyWebServer, class: "
            + CmdWithHeaderTest.class.getName());
        logger.info("*********** stopping grizzlyWebServer");
        server.shutdownNow();
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void test_getJsondata() throws Exception {
        Client c = ClientBuilder.newClient();
        WebTarget webTarget = c.target(BASE_URI);
        Response response = webTarget.path("/jsonproxy").request().get();
        assertEquals(200, response.getStatus());
        assertEquals(response.getMediaType().toString(), "application/json");

        String testMsg = response.readEntity(String.class);
        assertTrue(testMsg.contains("data from specified test file"));
    }
    
    @Test
    public void test_getCmd1() throws Exception {
        Client c = ClientBuilder.newClient();
        WebTarget webTarget = c.target(BASE_URI);
        Response response = webTarget.path("/queryEP").request().get();
        System.out.println("* ---------------------------- response: " + response);
        System.out.println("* ---------------------------- user.dir: "
              + System.getProperty("user.dir"));
//        assertEquals(200, response.getStatus());
//        assertEquals(response.getMediaType().toString(), "application/json");

        String testMsg = response.readEntity(String.class);
        System.out.println("* ---------------------------- testMsg: " + testMsg);
  //      assertTrue(testMsg.contains("data from specified test file"));
    }
}
