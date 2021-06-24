/*******************************************************************************
 * Copyright (c) 2018 IRIS DMC supported by the National Science Foundation.
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

package edu.iris.wss.endpoints;

import edu.iris.wss.framework.AppConfigurator;
import edu.iris.wss.framework.FileCreaterHelper;
import edu.iris.wss.framework.GrizzlyContainerHelper;
import edu.iris.wss.framework.ParamConfigurator;
import edu.iris.wss.framework.Util;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mike
 */
public class StreamEater2Test {
    public static final String THIS_CLASS_NAME = StreamEater2Test.class.getSimpleName();
    public static final Logger LOGGER = Logger.getLogger(THIS_CLASS_NAME);

    private static final String SERVICE_CONTEXT = "/tstStreamEat2";
    private static final String ENDPOINT_NAME = "query";

    private static final String BASE_HOST = "http://localhost";
    private static final Integer BASE_PORT = 8093;

    private static final URI BASE_URI = URI.create(BASE_HOST + ":"
        + BASE_PORT + SERVICE_CONTEXT);

    public StreamEater2Test() {
    }

    @BeforeClass
    public static void setUpClass() {
        // define WSS config dir for this test
        System.setProperty(Util.WSS_OS_CONFIG_DIR,
            "target"
              + File.separator + "test-classes"
              + File.separator + THIS_CLASS_NAME);
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        String handlerName = Util.createCfgFileName("wsstest", ".py");
        File newFile = new File(handlerName);
        newFile.setExecutable(true);
        handlerName = newFile.getAbsolutePath();

        String newFN = FileCreaterHelper.createFileInWssFolder(SERVICE_CONTEXT,
                AppConfigurator.SERVICE_CFG_NAME_SUFFIX,
                createServiceCfgFile(ENDPOINT_NAME, handlerName),
                false);

        GrizzlyContainerHelper.setUpServer(BASE_URI, this.getClass().getName(),
              SERVICE_CONTEXT);
    }

    @After
    public void tearDown() throws Exception {
        GrizzlyContainerHelper.tearDownServer(this.getClass().getName());
    }

    @Test
    public void test_0() throws Exception {
        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path(ENDPOINT_NAME)
              .queryParam("format", "text");

        Response response = webTarget.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    @Test
    public void test_0_stdout() throws Exception {
        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path(ENDPOINT_NAME)
              .queryParam("force_error", "runtime")
              .queryParam("runtime_error_level", "2");

        Response response = webTarget.request().get();

        assertEquals(500, response.getStatus());
    }

    @Test
    public void test_2() throws Exception {
        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path(ENDPOINT_NAME)
              .queryParam("force_error", "after")
              .queryParam("runtime_error_level", "2");

        Response response = webTarget.request().get();

        assertEquals(200, response.getStatus());
    }

    private static String createServiceCfgFile(String endpointName,
                                               String handlerName) {
        String s = String.join("\n",
                "# ---------------- globals",
                "",
                "appName=" + THIS_CLASS_NAME,
                "version=0.1",
                "",
                "corsEnabled=false",
                "",
                "# LOG4J or RABBIT_ASYNC or USAGE_STATS or USAGE_STATS_AND_RABBIT_ASYNC",
                "loggingMethod=LOG4J",
                "",
                "# If present, an instance of the singleton class will be created at application start",
                "singletonClassName=edu.iris.wss.framework.UnitTestSingleton",
                "",
                "# ----------------  endpoints",
                "",
                endpointName + ".endpointClassName=edu.iris.wss.endpoints.CmdProcessor",
                endpointName + ".handlerProgram=" + handlerName,
                endpointName + ".handlerTimeout=200",
                endpointName + ".handlerWorkingDirectory=/tmp",
                endpointName + ".usageLog",
                endpointName + ".postEnabled=true",
                endpointName + ".logMiniseedExtents = false",
                endpointName + ".use404For204=false",
                endpointName + ".formatTypes = \\",
                "    text: text/plain,\\",
                "    json: application/json, \\",
                "    xml: application/xml, \\",
                "    zip: application/zip, \\",
                "    miniseed: application/vnd.fdsn.mseed, \\",
                "    geocsv: text/plain",
                endpointName + ".formatDispositions=zip: attachment; filename=\"data.zip\"",
                ""
        );

        return s;
    }
}
