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

import edu.iris.dmc.logging.usage.WSUsageItem;
import edu.iris.usage.Extra;
import edu.iris.usage.UsageItem;
import edu.iris.usage.util.UsageIO;
import edu.iris.wss.framework.AppConfigurator;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;

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
     * Create and send usage message. The items with nulls are for
     * Miniseed channel information and are not needed here.
     *
     * The level passed in, e.g. ERROR for error messages and INFO for
     * messages is used by log4j.
     *
     * For values set to null, expecting the logging system to leave those
     * respective fields out of the delivered message
     */
    public static void logUsageMessage(RequestInfo ri, String usageMessage, String appSuffix,
            Long dataSize, Long processTime, ZonedDateTime writeStartTime, ZonedDateTime writeEndTime,
            String errorType, Integer httpStatusCode, String extraText,
            Level level) {

        WSUsageItem wsuRabbit = new WSUsageItem();

        wsuRabbit.setMessagetype("usage");

        // note: application is now a simple pass through, it no longer
        //       appends a suffix, as in old code, e.g.
        //       wui.setApplication(makeFullAppName(ri, appSuffix));
        wsuRabbit.setApplication(    ri.appConfig.getAppName());
        // however, until feature is not needed, make the old application
        // name available (for JMS)
        String olderJMSApplciationName = makeFullAppName(ri, appSuffix);

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
            logger.error("Error one or both indices for start: " +  WssSingleton.USAGESTATS_JSON_START_IDENTIFIER
                    + "  or end: " + WssSingleton.USAGESTATS_JSON_END_IDENTIFIER
                    + "  ex: " + ex
                    + "  usageMessage: --->" + usageMessage + "<---"
                    + "  JSON substring: --->" + jsonSubStr + "<---");
        } catch (Exception ex) {
            logger.error("Error parsing JSON from usageMessage ex: " + ex
                    + "  usageMessage: --->" + usageMessage + "<---"
                    + "  JSON substring: --->" + jsonSubStr + "<---");
        } finally {
            if (null == usageItem) {
                usageItem = UsageItem.builder().build().withVersion(1.0);
            }
        }

		if (null != usageItem) {
            usageItem.setRequestTime(writeStartTime);
            usageItem.setCompleted(writeEndTime);

            usageItem.setAddress(WebUtils.getHostname());
            usageItem.setInterface(ri.appConfig.getAppName());
            usageItem.setIpaddress(WebUtils.getClientIp(ri.request));
            usageItem.setUserident(WebUtils.getAuthenticatedUsername(ri.requestHeaders));

            Extra extra = usageItem.getExtra();
            if (extra == null) {
                extra = new Extra();
                usageItem.setExtra(extra);
            }

            if (null == extra.getBackendServer() || extra.getBackendServer().isEmpty()) {
                extra.setBackendServer(WebUtils.getHostname());
            }

            if (null == extra.getMessage() || extra.getMessage().isEmpty()) {
                extra.setMessage(extraText);
            }

            // todo ?
            // extra.setReferer(referer);
            // extra.setRequestUrl(requestUrl);
            // extra.setServiceVersion(serviceVersion);

            // todo - always replace?
            extra.setUserAgent(WebUtils.getUserAgent(ri.request));
            extra.setReturnCode(httpStatusCode);
		}

		wsuRabbit.setHost(           WebUtils.getHostname());
        wsuRabbit.setAccessDate(     new Date());
        wsuRabbit.setClientName(     WebUtils.getClientName(ri.request));
        wsuRabbit.setClientIp(       WebUtils.getClientIp(ri.request));
        wsuRabbit.setDataSize(       dataSize);
        wsuRabbit.setProcessTimeMsec(processTime);
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

		logWssUsageMessage(level, usageItem, wsuRabbit, ri, olderJMSApplciationName);
	}

    /**
     * Create and send message for Miniseed channel information, it is
     * determined by media type of a request or default configuration.
     *
     * sets message type to wfstat as defined for downstream consumers
     *
     * appSuffix ignored
     *
     */
	public static void logWfstatMessage(RequestInfo ri,
			String appSuffix, Long dataSize, Long processTime,
			String errorType, Integer httpStatusCode, String extraText,
			String network, String station, String location, String channel, String quality,
			Date startTime, Date endTime) {

        WSUsageItem wsuRabbit = new WSUsageItem();

        wsuRabbit.setMessagetype("wfstat");

        // note: application is now a simple pass through, it no longer
        //       appends a suffix, as in old code, e.g.
        //       wui.setApplication(makeFullAppName(ri, appSuffix));
        wsuRabbit.setApplication(    ri.appConfig.getAppName());
        // however, until feature is not needed, make the old application
        // name available (for JMS)
        String olderJMSApplciationName = makeFullAppName(ri, appSuffix);

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

		logWssUsageMessage(Level.INFO, null, wsuRabbit, ri, olderJMSApplciationName);
	}

	private static void logWssUsageMessage(Level level, UsageItem usageItem, WSUsageItem wsuRabbit,
          RequestInfo ri, String olderJMSApplciationName) {
		AppConfigurator.LoggingMethod loggingType = ri.appConfig.getLoggingType();

		if (loggingType == LoggingMethod.LOG4J) {
            String msg = makeUsageLogString(wsuRabbit);

			switch (level.toInt()) {
			case Level.ERROR_INT:
				log4jUsageLogger.error(msg);
				break;
			case Level.INFO_INT:
				log4jUsageLogger.info(msg);
				break;
			default:
				log4jUsageLogger.debug(msg);
				break;
			}


		} else if (loggingType == LoggingMethod.RABBIT_ASYNC
				|| loggingType == LoggingMethod.USAGE_STATS_AND_RABBIT_ASYNC) {
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

		} else if (loggingType == LoggingMethod.USAGE_STATS) {
			// todo - change to noop at some point, use for validation only 2021-06-21
			String msg = makeUsageLogString(wsuRabbit);

			switch (level.toInt()) {
				case Level.ERROR_INT:
					log4jUsageLogger.error(msg);
					break;
				case Level.INFO_INT:
					log4jUsageLogger.info(msg);
					break;
				default:
					log4jUsageLogger.debug(msg);
					break;
			}

		} else {
            logger.error("Error, unexpected loggingMethod configuration value: "
                    + loggingType + "  msg: " + makeUsageLogString(wsuRabbit));
        }

		if (loggingType == LoggingMethod.USAGE_STATS
				|| loggingType == LoggingMethod.USAGE_STATS_AND_RABBIT_ASYNC) {
			try {
				if (null == usageItem) {
					// noop - must remain to ignore call from logWfstatMessage, which
                    //        is called when miniseed extents configuration is set true
					return;
				}

				int submitStatus = WssSingleton.usageSubmittalService.report(usageItem);
				if (204 != submitStatus) {
                    logger.error("Error - USAGE_STATS submit was not 204 it was:: " + submitStatus
                            + "  usageService: " + WssSingleton.usageSubmittalService
                            + "  interface: " + usageItem.getInterface()
                            + "  address: " + usageItem.getAddress()
                            + "  ipAddress: " + usageItem.getIpaddress()
                            + "  dataItem: " + usageItem.getDataitem());
                }
			} catch (Exception ex) {
				logger.error("Error while publishing via USAGE_STATS ex: " + ex
						+ "  usageService: " + WssSingleton.usageSubmittalService
						+ "  interface: " + usageItem.getInterface()
						+ "  address: " + usageItem.getAddress()
						+ "  ipAddress: " + usageItem.getIpaddress()
						+ "  dataItem: " + usageItem.getDataitem());
				ex.printStackTrace();
			}
		}
	}

    public static String makeFullAppName(RequestInfo ri, String appSuffix) {
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

	private static void append(StringBuffer sb, String s) {
		if (AppConfigurator.isOkString(s))
			sb.append(s);
		sb.append("|");
	}
}
