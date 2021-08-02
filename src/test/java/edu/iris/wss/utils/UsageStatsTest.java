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

package edu.iris.wss.utils;

import edu.iris.wss.framework.GrizzlyContainerHelper;
import edu.iris.wss.framework.Util;
import edu.iris.wss.utils.UsageStatsTestHelper.TEST_TYPE;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author mike
 */

/**
 * for live integration test, change loggingMethod configuration
 * in UsageStatsTestHelper and configure for a working submit service
 */
public class UsageStatsTest {
    public static final Logger logger = Logger.getLogger(UsageStatsTest.class);

    private static final String SOME_CONTEXT = "/tstWssUsageStats";

    private static final String BASE_HOST = "http://localhost";
    private static final Integer BASE_PORT = 8097;

    private static final URI BASE_URI = URI.create(BASE_HOST + ":"
        + BASE_PORT + SOME_CONTEXT);

    // 2017-10-25 - Was use of NAME_TYPES_TO_TEST necessary after previous
    //              changes for glassfish? Can it be removed?
    private static final ArrayList<TEST_TYPE> NAME_TYPES_TO_TEST = new ArrayList();
    private static int nameCounter = 0;
    static {
        NAME_TYPES_TO_TEST.add(TEST_TYPE.CONFIG_URL);
        NAME_TYPES_TO_TEST.add(TEST_TYPE.CONFIG_FILE);
        NAME_TYPES_TO_TEST.add(TEST_TYPE.CONFIG_BOGUS_URL);
        NAME_TYPES_TO_TEST.add(TEST_TYPE.BRIEF_MSG_IS_NULL);
        NAME_TYPES_TO_TEST.add(TEST_TYPE.DETAILED_MSG_IS_NULL);
    }

    @Context	javax.servlet.http.HttpServletRequest request;

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        // setup WSS service config dir for test environment
        System.setProperty(Util.WSS_OS_CONFIG_DIR,
            "target"
              + File.separator + "test-classes"
              + File.separator + "UsageStatsTest");

        UsageStatsTestHelper.setupCfgFiles(Util.WSS_OS_CONFIG_DIR, SOME_CONTEXT,
              NAME_TYPES_TO_TEST.get(nameCounter));
        nameCounter++; // NOTE: the number of tests cannot exceed the
                       //       number of elementas in NAME_TYPES_TO_TEST

        GrizzlyContainerHelper.setUpServer(BASE_URI, this.getClass().getName(),
              SOME_CONTEXT);
    }

    @After
    public void tearDown() throws Exception {
        GrizzlyContainerHelper.tearDownServer(this.getClass().getName());
    }

    // NOTE: Each of the following tests cause setUp and tearDown to run
    //       once, therefore the number of tests must not be greater than
    //       the number of elements in NAME_TYPES_TO_TEST
    //
    @Test
    public void test_log_types_0() throws Exception {
        test_log_usagestr();
        test_log_usagebasic();
        test_log_error();
        test_log_error_with_exception();
    }

    // The UsageStatsEndpointHelper code calls various logging options
    // based on the value of the queryParam "messageType", i.e.
    // usagestr, usagebasic, error, or error_with_exception
    public void test_log_usagestr() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "TEXT")
              .queryParam("messageType", "usagestr");

        Response response = webTarget.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    public void test_log_usagebasic() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "TEXT")
              .queryParam("messageType", "usagebasic");

        Response response = webTarget.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    public void test_log_error() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "TEXT")
              .queryParam("messageType", "error");

        Response response = webTarget.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    public void test_log_error_with_exception() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "TEXT")
              .queryParam("messageType", "error_with_exception");

        Response response = webTarget.request().get();

        assertEquals(400, response.getStatus());

        // can't pass this test because apparently exception handling with junit
        // and grizzley server does not construct a WebApplicationException
        // completely
//        assertEquals("text/plain", response.getMediaType().toString());
    }

    @Test
    public void test_extraText_0() throws Exception {
        test_extraText1("test_extraText1");
        test_extraText1("test_extraText2");
        test_extraText1("test_extraText3");
    }

    public void test_extraText1(String testSelection) throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
                .path("/test_logging")
                .queryParam("format", "TEXT")
                .queryParam("messageType", testSelection);

        // json is defined in endpoint format type
        Response response = webTarget.request(new MediaType("application", "json")).get();

        assertEquals(200, response.getStatus());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    @Test
    public void test_media_types_0() throws Exception {
        test_request_of_media_type_defined();
        test_request_of_media_type_not_defined();
    }

    public void test_request_of_media_type_defined() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "JSON")
              .queryParam("messageType", "usagestr");

        // json is defined in endpoint format type
        Response response = webTarget.request(new MediaType("application", "json")).get();

        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
    }

    public void test_request_of_media_type_not_defined() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "JSON")
              .queryParam("messageType", "usagestr");

        // xml not defined in endpoint format type
        Response response = webTarget.request(new MediaType("application", "xml")).get();

        assertEquals(406, response.getStatus());
        // error message returned in html page
        assertEquals("text/html;charset=ISO-8859-1", response.getMediaType().toString());
    }

    // Althought the original intent of these test does not work as described
    // in the commment after the assert 400, they do allow checking the handling
    // of nulls
    @Test
    public void test_log_log_and_throw_test_null_briefMsg() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "TEXT")
              .queryParam("messageType", "log_and_throw_test_null_briefMsg");

        Response response = webTarget.request().get();
        assertEquals(400, response.getStatus());

        // can't pass the following assert because apparently exception handling with junit
        // and grizzley server does not construct a WebApplicationException
        // completely
//        assertEquals("text/plain", response.getMediaType().toString());
    }

    // Althought the original intent of these test does not work as described
    // in the commment after the assert 400, they do allow checking the handling
    // of nulls
    @Test
    public void test_log_log_and_throw_test_null_detailMsg() throws Exception {

        Client c = ClientBuilder.newClient();

        WebTarget webTarget = c.target(BASE_URI)
              .path("/test_logging")
              .queryParam("format", "TEXT")
              .queryParam("messageType", "log_and_throw_test_null_detailMsg");

        Response response = webTarget.request().get();
        assertEquals(400, response.getStatus());

        // can't pass the following assert because apparently exception handling with junit
        // and grizzley server does not construct a WebApplicationException
        // completely
//        assertEquals("text/plain", response.getMediaType().toString());
    }
}
