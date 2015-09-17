/*******************************************************************************
 * Copyright (c) 2013 IRIS DMC supported by the National Science Foundation.
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

import edu.iris.wss.utils.WebUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;

public class AppConfigurator {
	public static final Logger logger = Logger.getLogger(AppConfigurator.class);

	public static final String wssVersion = "mastr_v2x-SNAPSHOT";

	public static final String wssDigestRealmnameSignature = "wss.digest.realmname";

	private static final String defaultConfigFileName = "META-INF/service.cfg";
	private static final String userParamConfigSuffix = "-service.cfg";
    
    // this particular string is purposely matched is an error indicator
    // for timeout on miniseed data, although, unless changed, it will
    // also be used for writeNormal
    public static final String miniseedStreamInterruptionIndicator =
            "000000##ERROR#######ERROR##STREAMERROR##STREAMERROR#STREAMERROR\n" +
            "This data stream was interrupted and is likely incomplete.     \n" +
            "#STREAMERROR##STREAMERROR##STREAMERROR##STREAMERROR#STREAMERROR\n" +
            "#STREAMERROR##STREAMERROR##STREAMERROR##STREAMERROR#STREAMERROR\n";

	private Boolean isLoaded = false;
	private Boolean isValid = false;
    
    public AppConfigurator() {
        // set default type
        outputTypes.put("BINARY", "application/octet-stream");
    }

    public String formatOutputTypes() {
        return formatOutputTypes(outputTypes);
    }

    private static String formatOutputTypes(Map<String, String> outputTypes) {
        StringBuilder s = new StringBuilder();
        s.append("outputTypes = ");
        
        Iterator<String> keyIt = outputTypes.keySet().iterator();
        while(keyIt.hasNext()) {
            String key = keyIt.next();
            s.append(key).append(": ").append(outputTypes.get(key));
            if (keyIt.hasNext()) {
                s.append(", ");
            }
        }
        
        return s.toString();
    }

    // An enum of the types supported internally. This is used in the code to
    // identify places in which the external typeKeys specified in the
    // service.cfg file must aggree with the respective items in this enum.
    // An operator must have typeKeys "miniseed" to enable access
    // to respective code.
    //
    // BINARY is defined as the default
    //
    // miniseed is an alias for mseed - to be consistent with FDSN standards
    //
	public static enum InternalTypes {
		MSEED, MINISEED, BINARY
	};
    
	private final InternalTypes outputType = InternalTypes.BINARY;
    private String defaultOutputTypeKey = "BINARY";
    public String defaultOutputTypeKey() {
		return defaultOutputTypeKey;
	}
    private Map<String, String> outputTypes = new HashMap<>();

	public static enum LoggingType {
		LOG4J, JMS
	};

	private String rootServiceDoc;
	private String wadlPath;
    private String swaggerV2URL;

	private String workingDirectory = "/";
	private String handlerProgram;
	private String catalogsHandlerProgram;
	private String contributorsHandlerProgram;
	private String countsHandlerProgram;

	private String appName;
	private String version;

	private Boolean usageLog = true;
	private Boolean postEnabled = false;
	private Boolean use404For204 = false;
	private Boolean corsEnabled = true;

	private Integer sigkillDelay = 100; // 100 msec delay from SIGTERM to SIGKILL

	private Integer timeoutSeconds = null;

	private LoggingType loggingType = LoggingType.LOG4J;

	private String jndiUrl = null;

	private String irisEndpointClassName = null;
	private String singletonClassName = null;

	// Either a handler program or a Streaming Output Class is required.

	public String getHandlerProgram() {
		return handlerProgram;
	}

	public void setHandlerProgram(String s) {
		handlerProgram = s;
	}

	public String getIrisEndpointClassName() {
		return irisEndpointClassName;
	}

	public void setIrisEndpointClassName(String s) {
		irisEndpointClassName = s;
	}

	// Required configuration entries. Failure to find these will result in an
	// exception.

	public String getAppName() {
		return appName;
	}

	public void setAppName(String s) {
		appName = s;
	}

	public String getWssVersion() {
		return wssVersion;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String s) {
		version = s;
	}

    public boolean isConfiguredForTypeKey(String outputTypeKey) throws Exception {
        return outputTypes.containsKey(outputTypeKey);
	}

    public String getMediaType(String outputTypeKey) throws Exception {
        // Note: do the same operation on outputTypeKey as the setter, e.g. trim
        //       and toUpperCase
        String mediaType = outputTypes.get(outputTypeKey.trim().toUpperCase());
        if (mediaType == null) {
            throw new Exception("WebserviceShell getOutputTypes, no Content-Type"
                    + " found for outputType: " + outputTypeKey);
        }
		return mediaType;
	}
    
	public void setOutputTypes(String s) throws Exception {
        if (!isOkString(s)) {
			throw new Exception("Missing outputTypes, at least one pair must"
                    + " be set");
        }
        
        String[] pairs = s.split(java.util.regex.Pattern.quote(","));

        int count = 0;
        for (String pair : pairs) {
            String[] oneKV = pair.split(java.util.regex.Pattern.quote(":"));
            if (oneKV.length != 2) {
                throw new Exception(
                        "WebserviceShell setOutputTypes is expecting 2 items in"
                        + " a comma separated list of pairs of output type"
                        + " and HTTP Content-Type,"
                        + " instead item count: " + oneKV.length
                        + (oneKV.length == 1 ? "  first item: " + oneKV[0] : "")
                        + "  input: " + s);
            }

            String key = oneKV[0].trim().toUpperCase();
            outputTypes.put(key, oneKV[1].trim());
            
            // the first item in the list shall be the new default
            count++;
            if (count == 1) {
                defaultOutputTypeKey = key;
            }
        }
    }

	public LoggingType getLoggingType() {
		return loggingType;
	}

	public void setLoggingType(LoggingType e) {
		loggingType = e;
	}

	public void setLoggingType(String s) throws Exception {
		try {
			this.loggingType = LoggingType.valueOf(s.toUpperCase());
		} catch (Exception e) {
			throw new Exception("Unrecognized logging type: " + s);
		}
	}

	public String getWorkingDirectory() {
		return workingDirectory;
	}

	public void setWorkingDirectory(String s) {
		workingDirectory = s;
	}

	public Boolean getUsageLog() {
		return usageLog;
	}

	public void setUsageLog(Boolean b) {
		usageLog = b;
	}

	public Boolean getPostEnabled() {
		return postEnabled;
	}

	public void setPostEnabled(Boolean b) {
		postEnabled = b;
	}

	public Boolean getUse404For204() {
		return use404For204;
	}

	public void setUse404For204(Boolean b) {
		use404For204 = b;
	}
	
	public Boolean getCorsEnabled() {
		return corsEnabled;
	}

	public void setCorsEnabled(Boolean b) {
		corsEnabled = b;
	}

	public Integer getSigkillDelay() {
		return sigkillDelay;
	}

	public void setSigkillDelay(Integer i) {
		sigkillDelay = i;
	}

	// Not required. Might be defaulted elsewhere.

	public String getRootServiceDoc() {
		return rootServiceDoc;
	}

	public void setRootServiceDoc(String s) {
		rootServiceDoc = s;
	}
	
	public String getWadlPath() {
		return wadlPath;
	}

	public void setWadlPath(String s) {
		wadlPath = s;
	}

	public String getSwaggerV2URL() {
		return swaggerV2URL;
	}

	public void setSwaggerV2URL(String s) {
		swaggerV2URL = s;
	}

	public Integer getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(Integer i) {
		timeoutSeconds = i;
	}

	public String getSingletonClassName() {
		return singletonClassName;
	}

	public void setSingletonClassName(String s) {
		singletonClassName = s;
	}

	public String getJndiUrl() {
		return jndiUrl;
	}

	public void setJndiUrl(String s) {
		jndiUrl = s;
	}

	// Other Getters. Not defaulted

	public String getCatalogsHandlerProgram() {
		return catalogsHandlerProgram;
	}

	public void setCatalogsHandlerProgram(String s) {
		catalogsHandlerProgram = s;
	}

	public String getContributorsHandlerProgram() {
		return contributorsHandlerProgram;
	}

	public void setContributorsHandlerProgram(String s) {
		contributorsHandlerProgram = s;
	}

	public String getCountsHandlerProgram() {
		return countsHandlerProgram;
	}

	public void setCountsHandlerProgram(String s) {
		countsHandlerProgram = s;
	}

	
	public Boolean isValid() {
		return isValid;
	}

	public void loadConfigFile(String configBase, ServletContext context)
			throws Exception {

		// Depending on the way the servlet context starts, this can be called
		// multiple
		// times via SingletonWrapper class.
		if (isLoaded)
			return;
		isLoaded = true;

		Properties configurationProps = new Properties();
		Boolean userConfig = false;

		// Now try to read a user config file from the location specified by the
		// wssConfigDir property concatenated with the web application name
		// (last part
		// of context path), e.g. 'station' or 'webserviceshell'
		String configFileName = null;

        String wssConfigDir = System.getProperty(WebUtils.wssConfigDirSignature);
        
        String warnMsg1 = "***** check for system property "
              + WebUtils.wssConfigDirSignature
              + ", value found: " + wssConfigDir;
        String warnMsg2 = "***** or check webapp name on cfg files, value found: "
            + configBase;
        
        if (isOkString(wssConfigDir) && isOkString(configBase)) {
            if (!wssConfigDir.endsWith("/")) {
                wssConfigDir += "/";
            }

            configFileName = wssConfigDir + configBase
                + userParamConfigSuffix;
            logger.info("Attempting to load application configuration file from: "
                + configFileName);

            try {
                configurationProps.load(new FileInputStream(configFileName));
                userConfig = true;
            } catch (IOException ex) {
                logger.warn("***** could not read service cfg file: " + configFileName);
                logger.warn("***** ignoring exception: " + ex);
                logger.warn(warnMsg1);
                logger.warn(warnMsg2);
            }
        } else {
            logger.warn("***** unexpected configuration for service cfg file");
            logger.warn(warnMsg1);
            logger.warn(warnMsg2);
        }

		// If no user config was successfully loaded, load the default config file
        // Exception at this point should propagate up.
        if (!userConfig) {
            InputStream inStream = this.getClass().getClassLoader()
                .getResourceAsStream(defaultConfigFileName);
            if (inStream == null) {
                throw new Exception("Default configuration file was not"
                    + " found for name: " + defaultConfigFileName);
            }
            logger.info("Attempting to load default application"
                + " configuration from here: " + defaultConfigFileName);

            configurationProps.load(inStream);
            logger.info("Default application properties loaded, file: "
                + defaultConfigFileName);
        }

		// Only allow one of handler program or streaming output class
		String handlerStr = configurationProps.getProperty("handlerProgram");
		String soStr = configurationProps
				.getProperty("irisEndpointClassName");

		if (!isOkString(handlerStr) && !isOkString(soStr))
			throw new Exception("Missing handler program configuration");

		if (isOkString(handlerStr) && isOkString(soStr))
			throw new Exception(
					"Handler program _AND_ irisEndpoint class specified.  Only one allowed.");

		if (isOkString(handlerStr))
			this.handlerProgram = handlerStr;

		if (isOkString(soStr))
			this.irisEndpointClassName = soStr;
		// ------------------------------------------------------

		String configStr;

		configStr = configurationProps.getProperty("appName");
		if (isOkString(configStr))
			this.appName = configStr;
		else
			throw new Exception("Missing appName configuration");

		configStr = configurationProps.getProperty("version");
		if (isOkString(configStr))
			this.version = configStr;
		else
			throw new Exception("Missing version configuration");

		// ------------------------------------------------------------------
		configStr = configurationProps.getProperty("rootServiceDoc");
		if (isOkString(configStr))
			this.rootServiceDoc = configStr;

		configStr = configurationProps.getProperty("wadlPath");
		if (isOkString(configStr))
			this.wadlPath = configStr;
		
		configStr = configurationProps.getProperty("swaggerV2URL");
		if (isOkString(configStr)) {
			this.swaggerV2URL = configStr;
        }
		
		configStr = configurationProps.getProperty("outputTypes");
		if (isOkString(configStr))
			this.setOutputTypes(configStr);

		configStr = configurationProps.getProperty("loggingMethod");
		if (isOkString(configStr))
			this.setLoggingType(configStr);

		configStr = configurationProps.getProperty("handlerTimeout");
		if (isOkString(configStr))
			this.timeoutSeconds = Integer.parseInt(configStr);

		configStr = configurationProps.getProperty("usageLog");
		if (isOkString(configStr))
			this.usageLog = Boolean.parseBoolean(configStr);

		configStr = configurationProps.getProperty("postEnabled");
		if (isOkString(configStr))
			this.postEnabled = Boolean.parseBoolean(configStr);

		configStr = configurationProps.getProperty("use404For204");
		if (isOkString(configStr))
			this.use404For204 = Boolean.parseBoolean(configStr);

		configStr = configurationProps.getProperty("corsEnabled");
		if (isOkString(configStr))
			this.corsEnabled = Boolean.parseBoolean(configStr);
		
		configStr = configurationProps.getProperty("sigkillDelay");
		if (isOkString(configStr))
			this.sigkillDelay = Integer.parseInt(configStr);

		configStr = configurationProps.getProperty("jndiUrl");
		if (isOkString(configStr))
			this.jndiUrl = configStr;

		configStr = configurationProps.getProperty("singletonClassName");
		if (isOkString(configStr))
			this.singletonClassName = configStr;

		configStr = configurationProps.getProperty("catalogsHandlerProgram");
		if (isOkString(configStr))
			this.catalogsHandlerProgram = configStr;

		configStr = configurationProps.getProperty("contributorsHandlerProgram");
		if (isOkString(configStr))
			this.contributorsHandlerProgram = configStr;
		
		configStr = configurationProps.getProperty("countsHandlerProgram");
		if (isOkString(configStr))
			this.countsHandlerProgram = configStr;

		// Load the configuration for the working directory and substitute
		// System properties and environment properties.
		configStr = configurationProps.getProperty("handlerWorkingDirectory");
		if (isOkString(configStr)) {

			if (!configStr.matches("/.*|.*\\$\\{.*\\}.*")) {
				this.workingDirectory = configStr;
			} else {
				Properties props = System.getProperties();
				for (Object key : props.keySet()) {
					this.workingDirectory = configStr.replaceAll("\\$\\{" + key
							+ "\\}", props.getProperty(key.toString()));
				}
				Map<String, String> map = System.getenv();
				for (String key : map.keySet()) {
					this.workingDirectory = configStr.replaceAll("\\$\\{" + key
							+ "\\}", map.get(key));
				}
			}

			// If the working directory is and absolute path then just use it
			// If it's relative, then reference it to the servlet context.
			if (!this.workingDirectory.matches("/.*")) {
				this.workingDirectory = context
						.getRealPath(this.workingDirectory);
			}

			File f = new File(this.workingDirectory);
			if (!f.exists())
				throw new Exception("Working Directory: "
						+ this.workingDirectory + " does not exist");

			if (!f.canWrite() || !f.canRead())
				throw new Exception(
						"Improper permissions on working Directory: "
								+ this.workingDirectory);
		}

		// Finished w/o problems.
		this.isValid = true;
		logger.info(this.toString());
	}

	private static boolean isOkString(String s) {
		return ((s != null) && !s.isEmpty());
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("WSS Service Configuration" + "\n");

		sb.append(strAppend("WSS Version") + wssVersion + "\n");

		sb.append(strAppend("Root Service Doc") + rootServiceDoc + "\n");
		sb.append(strAppend("WADL Path") + wadlPath + "\n");
		sb.append(strAppend("Swagger V2 URL") + swaggerV2URL + "\n");

		sb.append(strAppend("Application Name") + appName + "\n");
		sb.append(strAppend("Version") + version + "\n");

		sb.append(strAppend("Handler Working Directory") + workingDirectory
				+ "\n");
		sb.append(strAppend("Handler Program") + handlerProgram + "\n");
		sb.append(strAppend("Handler Timeout") + timeoutSeconds + "\n");

		sb.append(strAppend("Catalog Handler Program") + catalogsHandlerProgram
				+ "\n");
		sb.append(strAppend("Contributor Handler Program")
				+ contributorsHandlerProgram + "\n");

		sb.append(strAppend("Usage Log") + usageLog + "\n");
		sb.append(strAppend("Post Enabled") + postEnabled + "\n");
		sb.append(strAppend("Use 404 for 204") + use404For204 + "\n");

		sb.append(strAppend("Default Output Type Key") + defaultOutputTypeKey + "\n");

		sb.append(strAppend("Output Types") + formatOutputTypes(outputTypes) + "\n");

		sb.append(strAppend("Logging Method") + loggingType + "\n");

		if (jndiUrl != null)
			sb.append(strAppend("JNDI URL") + jndiUrl + "\n");

		if (singletonClassName != null)
			sb.append(strAppend("Singleton ClassName") + singletonClassName
					+ "\n");

		if (irisEndpointClassName != null)
			sb.append(strAppend("Streaming Output Class ")
					+ irisEndpointClassName + "\n");

		return sb.toString();
	}

	private final int colSize = 30;

	private String strAppend(String s) {
		int len = s.length();
		for (int i = 0; i < colSize - len; i++) {
			s += " ";
		}
		return s;
	}

	public String toHtmlString() {
		StringBuilder sb = new StringBuilder();
		sb.append("<TABLE border=2 style='width: 600px'>");
		sb.append("<col style='width: 30%' />");

		sb.append("<TR><TH colspan=\"2\" >" + "WSS Service Configuration"
				+ "</TH></TR>");

		sb.append("<TR><TD>" + "WSS Version" + "</TD><TD>" + wssVersion
				+ "</TD></TR>");

		sb.append("<TR><TD>" + "Root Service Doc" + "</TD><TD>"
				+ rootServiceDoc + "</TD></TR>");
		sb.append("<TR><TD>" + "WADL Path" + "</TD><TD>"
				+ wadlPath + "</TD></TR>");
		sb.append("<TR><TD>" + "Swagger V2 URL" + "</TD><TD>"
				+ swaggerV2URL + "</TD></TR>");

		sb.append("<TR><TD>" + "Application Name" + "</TD><TD>" + appName
				+ "</TD></TR>");
		sb.append("<TR><TD>" + "Version" + "</TD><TD>" + version + "</TD></TR>");

		sb.append("<TR><TD>" + "Handler Working Directory" + "</TD><TD>"
				+ workingDirectory + "</TD></TR>");
		sb.append("<TR><TD>" + "Handler Program" + "</TD><TD>" + handlerProgram
				+ "</TD></TR>");
		sb.append("<TR><TD>" + "Handler Timeout" + "</TD><TD>" + timeoutSeconds
				+ "</TD></TR>");

		sb.append("<TR><TD>" + "Catalogs Handler Program" + "</TD><TD>"
				+ catalogsHandlerProgram + "</TD></TR>");
		sb.append("<TR><TD>" + "Contributors Handler Program" + "</TD><TD>"
				+ contributorsHandlerProgram + "</TD></TR>");
		sb.append("<TR><TD>" + "Counts Handler Program" + "</TD><TD>"
				+ countsHandlerProgram + "</TD></TR>");
		sb.append("<TR><TD>" + "Usage Log" + "</TD><TD>" + usageLog
				+ "</TD></TR>");
		sb.append("<TR><TD>" + "Post Enabled" + "</TD><TD>" + postEnabled
				+ "</TD></TR>");
		sb.append("<TR><TD>" + "Use 404 for 204" + "</TD><TD>" + use404For204
				+ "</TD></TR>");
		sb.append("<TR><TD>" + "CORS Enabled" + "</TD><TD>" + corsEnabled
				+ "</TD></TR>");

		sb.append("<TR><TD>" + "Default Output Type Key" + "</TD><TD>"
                + defaultOutputTypeKey + "</TD></TR>");
        
		sb.append("<TR><TD>").append("Output Types").append("</TD><TD>")
                .append(formatOutputTypes(outputTypes)).append("</TD></TR>");

		sb.append("<TR><TD>" + "Logging Method" + "</TD><TD>" + loggingType
				+ "</TD></TR>");

		if (jndiUrl != null)
			sb.append("<TR><TD>" + "JNDI URL" + "</TD><TD>" + jndiUrl
					+ "</TD></TR>");

		if (singletonClassName != null)
			sb.append("<TR><TD>" + "Singleton ClassName" + "</TD><TD>"
					+ singletonClassName + "</TD></TR>");

		if (irisEndpointClassName != null)
			sb.append("<TR><TD>" + "Streaming Output Class " + "</TD><TD>"
					+ irisEndpointClassName + "</TD></TR>");

		sb.append("</TABLE>");

		return sb.toString();
	}
}
