/*******************************************************************************
 * Copyright (c) 2021 IRIS DMC supported by the National Science Foundation.
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

import edu.iris.dmc.logging.usage.WSUsageItem;
import edu.iris.usage.Dataitem;
import edu.iris.usage.Dataitem.DataitemBuilder;
import edu.iris.usage.Extra;
import edu.iris.usage.UsageItem;
import edu.iris.usage.util.UsageIO;
import edu.iris.wss.framework.AppConfigurator;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import edu.iris.wss.framework.RequestInfo;
import edu.iris.wss.framework.AppConfigurator.LoggingMethod;
import edu.iris.wss.framework.Util;
import edu.iris.wss.framework.WssSingleton;

public class LoggerUtils {

	public static final Logger logger = Logger.getLogger(LoggerUtils.class);
	public static final Logger log4jUsageLogger = Logger.getLogger("UsageLogger");

    /**
     * Support new clients that need to create a UsageItem object.
     */
    public static void logUsageItemMessage(RequestInfo ri, UsageItem usageItem, String appSuffix,
                                           Long dataSize, Long processTime,
                                           String errorType, Integer httpStatusCode, String extraText) {
        /**
         * for APIs without explicit start, end times, set them to null and
         * let the rules handle it.
         *
         * prepare final version of usageItem content
         */
        UsageItem checkedUsageItem = applyRulesToUsageItem(ri, usageItem, appSuffix,
                dataSize, processTime, null, null,
                errorType, httpStatusCode, extraText);

        reportUsageStatsMessage(checkedUsageItem, ri);

        WSUsageItem wsuRabbitMsg = createWsuRabbitMessage(ri, appSuffix,
                dataSize, processTime,
                errorType, httpStatusCode,
                extraText);

        reportWsuRabbitOrLog4jMessage(Level.INFO, wsuRabbitMsg, ri);
    }

    /**
     * Support existing WSS API and current clients using Util.logUsageMessage
     */
    public static void logUsageMessage(RequestInfo ri, String appSuffix,
                                       Long dataSize, Long processTime,
                                       String errorType, Integer httpStatusCode, String extraText) {
        UsageItem usageItem = createDefaultUsageItem(dataSize);

        logUsageItemMessage(ri, usageItem, appSuffix, dataSize, processTime,
                errorType, httpStatusCode, extraText);
    }

    /**
     * Build UsageItem from the expected JSON string delivered by
     * the handler or create a new usageItem if there are exception.
     */
    public static void logUsageStrMessage(RequestInfo ri, String usageMessage, String appSuffix,
                                       Long dataSize, Long processTime, ZonedDateTime writeStartTime,
                                       ZonedDateTime writeEndTime,
                                       String errorType, Integer httpStatusCode, String extraText,
                                       Level level) {

        UsageItem usageItem = null;
        String jsonSubStr = null;
        try {
            jsonSubStr = usageMessage.substring(
                    usageMessage.indexOf(WssSingleton.USAGESTATS_JSON_START_IDENTIFIER)
                            + WssSingleton.USAGESTATS_JSON_START_IDENTIFIER_LENGTH,
                    usageMessage.indexOf(WssSingleton.USAGESTATS_JSON_END_IDENTIFIER)
            );
            usageItem = UsageIO.read(jsonSubStr);
        } catch (IndexOutOfBoundsException ex) {
            logger.error("Error in one or both JSON identifiers, expecting: "
                    +  WssSingleton.USAGESTATS_JSON_START_IDENTIFIER
                    + "json-string" + WssSingleton.USAGESTATS_JSON_END_IDENTIFIER
                    + "  exception: " + ex
                    + ", input JSON Message: --->" + usageMessage + "<---"
                    + "  found JSON string --->" + jsonSubStr + "<---");
            if (errorType == null) {
                errorType = "Error finding JSON";
            } else {
                errorType = "Error finding JSON and " + errorType;
            }
        } catch (Exception ex) {
            logger.error("Error parsing JSON from usageMessage  exception: " + ex
                    + ", input JSON Message: --->" + usageMessage + "<---"
                    + "  tried to parse as JSON: --->" + jsonSubStr + "<---");
            if (errorType == null) {
                errorType = "Error parsing JSON";
            } else {
                errorType = "Error parsing JSON and " + errorType;
            }
        } finally {
            if (usageItem == null) {
                usageItem = createDefaultUsageItem(dataSize);
            }
        }

        /**
         * do the same call sequence, but WSS is able to provide a
         * start and end time that may bracket the handler created times,
         * so the WSS times can be used to override the handler times
         * depending on the rules.
         */
        UsageItem checkedUsageItem = applyRulesToUsageItem(ri, usageItem, appSuffix,
                dataSize, processTime, writeStartTime, writeEndTime,
                errorType, httpStatusCode, extraText);

        reportUsageStatsMessage(checkedUsageItem, ri);

        WSUsageItem wsuRabbitMsg = createWsuRabbitMessage(ri, appSuffix,
                dataSize, processTime,
                errorType, httpStatusCode,
                extraText);

        reportWsuRabbitOrLog4jMessage(Level.INFO, wsuRabbitMsg, ri);
    }

    /**
     * Create a default object
     */
    private static UsageItem createDefaultUsageItem(Long bytesProcessed) {

        // store total bytes processed as a single Dateitem element.

        Dataitem dataItem = new DataitemBuilder()
                .bytes(bytesProcessed)
                .build();

        UsageItem usageItem = new UsageItem.UsageItemBuilder()
                .version(1.0)
                .dataItems(Arrays.asList(dataItem))
                .build();

        return usageItem;
    }

    /**
     * Try to keep all rules for message creation here.
     *
     * Unless defined otherwise, field assignments should match
     * the creation of rabbit messages
     */
    private static UsageItem applyRulesToUsageItem(RequestInfo ri, UsageItem usageItem, String appSuffix,
                                              Long dataSize, Long processTime,
                                              ZonedDateTime writeStartTime, ZonedDateTime writeEndTime,
                                              String errorType, Integer httpStatusCode, String extraText) {

        if (usageItem == null) {
            logger.error("Error, programmer error, usageItem should not be null at this point!");
            return null;
        } else {
            ZonedDateTime nowTime = ZonedDateTime.now(ZoneId.of("UTC"));

            // inconvenient semantics for what I need to do, which is quality
            // check incoming UsageItem data. When UsageItem has no setters, or
            // the builders have no getters, I do this

            UsageItem.UsageItemBuilder uibuilder = new UsageItem.UsageItemBuilder();
            uibuilder.version(usageItem.getVersion())
                    .requestTime(usageItem.getRequestTime())
                    .completed(usageItem.getCompleted())
                    .address(usageItem.getAddress())
                    ._interface(usageItem.getInterface())
                    .ipaddress(usageItem.getIpaddress())
                    .userident(usageItem.getUserident())
                    //.extra(usageItem.getExtra())  // defer setting until after content check
                    //.dataItem(usageItem.getDataitem())  // defer setting until after content check
            ;

            ZonedDateTime updatedStartTime = writeStartTime;
            if (writeStartTime != null) {
                uibuilder.requestTime(writeStartTime);
            } else {
                updatedStartTime = nowTime;
                if (usageItem.getRequestTime() == null) {
                    uibuilder.requestTime(nowTime);
                }
            }

            ZonedDateTime updatedEndTime = writeStartTime;
            if (writeEndTime != null) {
                uibuilder.completed(writeEndTime);
            } else {
                updatedEndTime = nowTime;
                if (usageItem.getCompleted() == null) {
                    uibuilder.completed(nowTime);
                }
            }

            if (isNullOrEmpty(usageItem.getAddress())
                    || WebUtils.getClientName(ri.request).length() > usageItem.getAddress().length()) {
                uibuilder.address(WebUtils.getClientName(ri.request));
            }

            if (isNullOrEmpty(usageItem.getInterface())) {
                uibuilder._interface(ri.appConfig.getAppName());
            }

            if (isNullOrEmpty(usageItem.getIpaddress())) {
                uibuilder.ipaddress(WebUtils.getClientIp(ri.request));
            }

            if (isNullOrEmpty(usageItem.getUserident())) {
                uibuilder.userident(WebUtils.getAuthenticatedUsername(ri.requestHeaders));
            }

            // Extra
            Extra extra = usageItem.getExtra();
            Extra.ExtraBuilder exb = new Extra.ExtraBuilder();
            if (extra == null) {
                extra = exb.build();
            } else {
                exb.userAgent(extra.getUserAgent())
                        .requestUrl(extra.getRequestUrl())
                        .referer(extra.getReferer())
                        .backendServer(extra.getBackendServer())
                        .parent(extra.getParent())
                        .returnCode(extra.getReturnCode())
                        .message(extra.getMessage())
                        .protocol(extra.getProtocol())
                        .serviceVersion(extra.getServiceVersion())
                ;
            }

            if (isNullOrEmpty(extra.getUserAgent())) {
                exb.userAgent(WebUtils.getUserAgent(ri.request));
            }

            if (isNullOrEmpty(extra.getRequestUrl())) {
                exb.requestUrl(WebUtils.getUrl(ri.request));
            }

            if (isNullOrEmpty(extra.getReferer())) {
                exb.referer(WebUtils.getReferer(ri.request));
            }

            if (isNullOrEmpty(extra.getBackendServer())
                    || WebUtils.getHostname().length() > extra.getBackendServer().length()) {
                exb.backendServer(WebUtils.getHostname());
            }

            // todo - not checking parent

            if (extra.getReturnCode() == null) {
                exb.returnCode(httpStatusCode);
            }

            /*
            // hang on to it for a while, extraProperty in this form may
            // now be in dataitem.extra? but exb.message is probably better,
            // this may only be a WSS feature?
            if ( ! (isNullOrEmpty(extraText))) {
                // Put extraText in additionalProperties - Use the top level elements
                // as keys and put rem                try {
                    Gson gson = new Gson();
                    Map<String, Object> map = new HashMap<String, Object>();
                    map = (Map<String, Object>) gson.fromJson(extraText, map.getClass());
                    for (String key : map.keySet()) {
                        String remaining = gson.toJson(map.get(key));
                        //extra.withAdditionalProperty(key, remaining);
                    }
                } catch (Exception ex) {
                    // Use extraText as-is when it does not convert to JSON
                    //extra.withAdditionalProperty("extraText_from_WSS", extraText);
                }
            }
             */

            if (isNullOrEmpty(extra.getMessage()) && ! (isNullOrEmpty(extraText))) {
                exb.message(extraText);
            }

            if (isNullOrEmpty(extra.getProtocol())) {
                exb.protocol(ri.request.getProtocol() + " " + ri.request.getMethod());
            }

            if (isNullOrEmpty(extra.getServiceVersion())) {
                exb.serviceVersion(ri.appConfig.getAppVersion());
            }

            // dataitem
            List<Dataitem> dataItems = usageItem.getDataitem();
            Dataitem.DataitemBuilder dib = new Dataitem.DataitemBuilder();
            if (dataItems == null) {
                logger.warn("WARN, unexpected null Dataitem list, will try to fix it for"
                        + "  address: " + usageItem.getAddress() + "   interface: " + usageItem.getInterface());

                dib.bytes(dataSize);
                dataItems = Arrays.asList(dib.build());
            }

            // dataItems list must exist here
            // check required fields

            List<Dataitem> checkedDataItems = new ArrayList<>();
            for (Dataitem dataItem : dataItems) {
                dib = new DataitemBuilder();
                dib.bytes(dataItem.getBytes())
                        .datacenter(dataItem.getDatacenter())
                        .product(dataItem.getProduct())
                        .format(dataItem.getFormat())
                        .span(dataItem.getSpan())
                        .extra(dataItem.getExtra())
                        ;

                if (dataItem.getBytes() <= 0) {
                    dib.bytes(dataSize);
                }

                if (isNullOrEmpty(dataItem.getDatacenter())) {
                    dib.datacenter("WSS unknown datacenter");
                }

                if (isNullOrEmpty(dataItem.getProduct())) {
                    dib.product("WSS unknown product");
                }

                if (isNullOrEmpty(dataItem.getFormat())) {
                    try {
                        String requestFormat = ri.getPerRequestFormatTypeKey(ri.getEndpointNameForThisRequest());
                        dib.format(requestFormat);
                    } catch (Exception ex) {
                        dib.format("WSS unknown format");
                        logger.warn("Warning, an exception occurred while getting format, ex: " + ex
                                + "  note: this is unexpected as the original request should have been rejected.");
                    }
                }

                if (dataItem.getSpan() == null) {
                    dib.span(updatedStartTime, updatedEndTime);
                }

                // todo - not checking extra at this time

                checkedDataItems.add(dib.build());
            }

            uibuilder.extra(exb);
            uibuilder.dataItems(checkedDataItems);

            return uibuilder.build();
        }
    }

    /**
     * Create and send usage message. The items with nulls are for
     * Miniseed channel information and are not needed here, but are used
     * when log4j is active.
     *
     * The level passed in, e.g. ERROR for error messages and INFO for
     * messages is used by log4j.
     *
     * appSuffix ignored, it was used for something in JMS implementation
     */
    private static WSUsageItem createWsuRabbitMessage(RequestInfo ri, String appSuffix,
                                                      Long dataSize, Long processTime,
                                                      String errorType, Integer httpStatusCode,
                                                      String extraText) {

        WSUsageItem wsuRabbit = new WSUsageItem();

        wsuRabbit.setMessagetype("usage");

        wsuRabbit.setApplication(    ri.appConfig.getAppName());

        wsuRabbit.setHost(           WebUtils.getHostname());
        wsuRabbit.setAccessDate(     new Date());
        wsuRabbit.setClientName(     WebUtils.getClientName(ri.request));
        wsuRabbit.setClientIp(       WebUtils.getClientIp(ri.request));
        wsuRabbit.setDataSize(       dataSize);
        wsuRabbit.setProcessTimeMsec(processTime);
        // keep null assignments for readability and comparison to wfstat record creation
        wsuRabbit.setNetwork(        null);
        wsuRabbit.setStation(        null);
        wsuRabbit.setChannel(        null);
        wsuRabbit.setLocation(       null);
        wsuRabbit.setQuality(        null);
        wsuRabbit.setStartTime(      null);
        wsuRabbit.setEndTime(        null);
        wsuRabbit.setErrorType(      errorType);
        wsuRabbit.setUserAgent(      WebUtils.getUserAgent(ri.request));
        wsuRabbit.setHttpCode(       httpStatusCode);
        wsuRabbit.setUserName(       WebUtils.getAuthenticatedUsername(ri.requestHeaders));
        wsuRabbit.setExtra(          extraText);

        return wsuRabbit;
    }

    /**
     * Create and send message type "wfstat" for Miniseed channel information,
     * it is determined by media type of a request, and is only called when
     * configuration logMiniseedExtents is true.
     *
     * appSuffix ignored, it was used for something in JMS implementation
     *
     */
	public static void logWfstatMessage(RequestInfo ri,
			String appSuffix, Long dataSize, Long processTime,
			String errorType, Integer httpStatusCode, String extraText,
			String network, String station, String location, String channel, String quality,
			Date startTime, Date endTime) {

        WSUsageItem wsuRabbit = new WSUsageItem();

        wsuRabbit.setMessagetype("wfstat");

        wsuRabbit.setApplication(    ri.appConfig.getAppName());

        wsuRabbit.setHost(           WebUtils.getHostname());
        wsuRabbit.setAccessDate(     new Date());
        wsuRabbit.setClientName(     WebUtils.getClientName(ri.request));
        wsuRabbit.setClientIp(       WebUtils.getClientIp(ri.request));
        wsuRabbit.setDataSize(       dataSize);
        wsuRabbit.setProcessTimeMsec(processTime);
        wsuRabbit.setNetwork(        network);
        wsuRabbit.setStation(        station);
        wsuRabbit.setChannel(        channel);
        wsuRabbit.setLocation(       location);
        wsuRabbit.setQuality(        quality);
        wsuRabbit.setStartTime(      startTime);
        wsuRabbit.setEndTime(        endTime);
        wsuRabbit.setErrorType(      errorType);
        wsuRabbit.setUserAgent(      WebUtils.getUserAgent(ri.request));
        wsuRabbit.setHttpCode(       httpStatusCode);
        wsuRabbit.setUserName(       WebUtils.getAuthenticatedUsername(ri.requestHeaders));
        wsuRabbit.setExtra(          extraText);

        /**
         * there is no requirement report usageItem for this configuration
         */
		reportWsuRabbitOrLog4jMessage(Level.INFO, wsuRabbit, ri);
	}

    private static void reportWsuRabbitOrLog4jMessage(Level level, WSUsageItem wsuRabbit, RequestInfo ri) {

        String log4jmsg = makeUsageLogString(wsuRabbit);

        AppConfigurator.LoggingMethod reportType = ri.appConfig.getLoggingType();

        if (reportType == LoggingMethod.LOG4J) {
            /**
             * Note that log4j uses the rabbit message
             */
            String message = makeUsageLogString(wsuRabbit);

            switch (level.toInt()) {
                case Level.ERROR_INT:
                    log4jUsageLogger.error(message);
                    break;
                case Level.INFO_INT:
                    log4jUsageLogger.info(message);
                    break;
                default:
                    log4jUsageLogger.debug(message);
                    break;
            }

        } else if (reportType == LoggingMethod.RABBIT_ASYNC
                || reportType == LoggingMethod.USAGE_STATS_AND_RABBIT_ASYNC) {
            try {
                WssSingleton.rabbitAsyncPublisher.publish(wsuRabbit);
            } catch (Exception ex) {
                ex.printStackTrace();
                logger.error("Error while publishing via RABBIT_ASYNC ex: " + ex
                        + "  rabbitAsyncPublisher: " + WssSingleton.rabbitAsyncPublisher
                        + "  msg: " + ex.getMessage()
                        + "  application: " + wsuRabbit.getApplication()
                        + "  host: " + wsuRabbit.getHost()
                        + "  client IP: " + wsuRabbit.getClientIp()
                        + "  ErrorType: " + wsuRabbit.getErrorType());

//                logger.error("Error while publishing via RABBIT_ASYNC stack:", ex);
                ex.printStackTrace();
            }
        }
    }

    /**
     * Note that UsageStats needs to be able to run at the same time as
     * rabbit, so it is in a separate if clause. Also the configuration
     * parameter USAGE_STATS_AND_RABBIT_ASYNC must set to get both
     */
    private static void reportUsageStatsMessage(UsageItem usageItem, RequestInfo ri) {

        AppConfigurator.LoggingMethod reportType = ri.appConfig.getLoggingType();

        if (reportType == LoggingMethod.USAGE_STATS
                || reportType == LoggingMethod.USAGE_STATS_AND_RABBIT_ASYNC) {
            try {
                boolean submitStatus = WssSingleton.usageSubmittalService.report(usageItem);
                if (! submitStatus) {
                    logger.error("Error - USAGE_STATS submit was not 204 it was:: " + submitStatus
                            + "  usageService: " + WssSingleton.usageSubmittalService
                            + "  interface: " + usageItem.getInterface()
                            + "  address: " + usageItem.getAddress()
                            + "  ipAddress: " + usageItem.getIpaddress());
                }
            } catch (Exception ex) {
                logger.error("Error while publishing via USAGE_STATS ex: " + ex
                        + "  usageService: " + WssSingleton.usageSubmittalService
                        + "  interface: " + usageItem.getInterface()
                        + "  address: " + usageItem.getAddress()
                        + "  ipAddress: " + usageItem.getIpaddress());
                ex.printStackTrace();
            }
        }
    }

    private static String makeFullAppName(RequestInfo ri, String appSuffix) {
        String fullAppName = ri.appConfig.getAppName();
        if (appSuffix != null) {
            fullAppName += appSuffix;
        }

        return fullAppName;
    }

	public static String makeUsageLogString(WSUsageItem wsu) {

		SimpleDateFormat sdf = new SimpleDateFormat(Util.ISO_8601_ZULU_FORMAT);
        sdf.setTimeZone(Util.UTZ_TZ);

		StringBuffer sb = new StringBuffer();

        // note, keep in the same order as getUsageLogHeader
		append(sb, wsu.getApplication());
		append(sb, wsu.getHost());
        if (wsu.getAccessDate() != null) {
            append(sb, sdf.format(wsu.getAccessDate()));
        } else {
            sb.append("|");
        }
		append(sb, wsu.getClientName());
		append(sb, wsu.getClientIp());
		append(sb, wsu.getDataSize().toString());
		append(sb, wsu.getProcessTimeMsec().toString());

		append(sb, wsu.getErrorType());
		append(sb, wsu.getUserAgent());
		append(sb, Integer.toString(wsu.getHttpCode()));
		append(sb, wsu.getUserName());

		append(sb, wsu.getNetwork());
		append(sb, wsu.getStation());
		append(sb, wsu.getLocation());
		append(sb, wsu.getChannel());
		append(sb, wsu.getQuality());
		if (wsu.getStartTime() != null) {
			append(sb, sdf.format(wsu.getStartTime()));
        } else {
            sb.append("|");
        }
		if (wsu.getEndTime() != null) {
			append(sb, sdf.format(wsu.getEndTime()));
        } else {
            sb.append("|");
        }
		append(sb, wsu.getExtra());
        //append(sb, wsu.getMessagetype());
        // on last one, leave off the delimiter
		if (AppConfigurator.isOkString(wsu.getMessagetype()))
			sb.append(wsu.getMessagetype());

		return sb.toString();
	}

	public static String getUsageLogHeader() {
		StringBuffer sb = new StringBuffer();
		sb.append("# ");
		append(sb, "Application");
		append(sb, "Host Name");
		append(sb, "Access Date");
		append(sb, "Client Name");
		append(sb, "Client IP");
		append(sb, "Data Length");
		append(sb, "Processing Time (ms)");

		append(sb, "Error Type");
		append(sb, "User Agent");
		append(sb, "HTTP Status");
		append(sb, "User");

		append(sb, "Network");
		append(sb, "Station");
		append(sb, "Location");
		append(sb, "Channel");
		append(sb, "Quality");

		append(sb, "Start Time");
		append(sb, "End Time");

		append(sb, "Extra");

        //append(sb, "Message Type");
        // on last one, leave off the delimiter
		if (AppConfigurator.isOkString("Message Type"))
			sb.append("Message Type");

		return sb.toString();
	}

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

	private static void append(StringBuffer sb, String s) {
		if (AppConfigurator.isOkString(s))
			sb.append(s);
		sb.append("|");
	}
}
