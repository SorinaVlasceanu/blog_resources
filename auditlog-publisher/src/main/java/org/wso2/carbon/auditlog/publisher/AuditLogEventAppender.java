/*
 * Copyright 2005,2012 WSO2, Inc. http://www.wso2.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.auditlog.publisher;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.databridge.agent.thrift.exception.AgentException;
import org.wso2.carbon.databridge.agent.thrift.lb.DataPublisherHolder;
import org.wso2.carbon.databridge.agent.thrift.lb.LoadBalancingDataPublisher;
import org.wso2.carbon.databridge.agent.thrift.lb.ReceiverGroup;
import org.wso2.carbon.databridge.agent.thrift.util.DataPublisherUtil;
import org.wso2.carbon.databridge.commons.Attribute;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.databridge.commons.exception.MalformedStreamDefinitionException;
import org.wso2.carbon.logging.appender.StreamData;
import org.wso2.carbon.logging.appender.StreamDefinitionCache;
import org.wso2.carbon.logging.internal.DataHolder;
import org.wso2.carbon.logging.internal.LoggingServiceComponent;
import org.wso2.carbon.logging.util.LoggingConstants;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.logging.TenantAwareLoggingEvent;
import org.wso2.carbon.utils.logging.TenantAwarePatternLayout;
import org.wso2.carbon.utils.logging.handler.TenantDomainSetter;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * This is the Appender to publish Audit logs to BAM server.
 * Following entry should be add to to log4j.properties time.

 log4j.appender.AUDIT_LOGFILE1=org.wso2.carbon.logging.appender.AuditLogEventAppender
 log4j.appender.AUDIT_LOGFILE1.File=${carbon.home}/repository/logs/audit.log
 log4j.appender.AUDIT_LOGFILE1.Append=true
 log4j.appender.AUDIT_LOGFILE1.layout=org.wso2.carbon.utils.logging.TenantAwarePatternLayout
 log4j.appender.AUDIT_LOGFILE1.layout.ConversionPattern=[%d] %P%5p - %x %m %n
 log4j.appender.AUDIT_LOGFILE1.layout.TenantPattern=%U%@%D [%T] [%S]
 log4j.appender.AUDIT_LOGFILE1.threshold=INFO
 log4j.appender.AUDIT_LOGFILE1.url=tcp://localhost:7611
 log4j.appender.AUDIT_LOGFILE1.columnList=%T,%S,%A,%d,%H,%c,%p,%m,%I,%Stacktrace
 log4j.appender.AUDIT_LOGFILE1.userName=admin
 log4j.appender.AUDIT_LOGFILE1.password=admin
 log4j.appender.AUDIT_LOGFILE1.processingLimit=1000
 log4j.appender.AUDIT_LOGFILE1.maxTolerableConsecutiveFailure=20
 log4j.appender.AUDIT_LOGFILE1.trustStorePassword=wso2carbon
 log4j.appender.AUDIT_LOGFILE1.truststorePath=/repository/resources/security/wso2carbon.jks

 */

/**
 * This is a extension to publish audit logs to bam.
 */
public class AuditLogEventAppender extends DailyRollingFileAppender {
    private static final Log log = LogFactory.getLog(AuditLogEventAppender.class);
    private String url;
    private String password;
    private String userName;
    private String columnList;
    private int maxTolerableConsecutiveFailure;
    private int processingLimit;
    private String streamDef;
    private String trustStorePassword;
    private String truststorePath;

    private final List<TenantAwareLoggingEvent> loggingEvents = new CopyOnWriteArrayList<TenantAwareLoggingEvent>();

    public AuditLogEventAppender() throws Exception {
        init();
    }

    public void init() throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        scheduler.scheduleWithFixedDelay(new AuditLogPublisherTask(), 10, 10, TimeUnit.MILLISECONDS);
        String log4jPath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator + "conf" +
                File.separator + "log4j.properties";
        File log4jProperties = new File(log4jPath);
        FileInputStream fileInputStream;
        if (log4jProperties.exists()) {

            try {
                fileInputStream = new FileInputStream(log4jProperties);
                Properties properties = new Properties();
                properties.load(fileInputStream);
                SecretResolver secretResolver = SecretResolverFactory.create(properties);
            } catch (FileNotFoundException e) {
                throw new Exception("Can't fine the the log4j.properties file under the " + log4jPath);
            } catch (IOException e) {
                throw new Exception("Failed to load propertied from " + log4jPath);
            }

        }
    }

    private String getCurrentServerName() {
        return ServerConfiguration.getInstance().getFirstProperty("ServerKey");
    }

    private String getCurrentDate() {
        Date now = new Date();
        DateFormat formatter = new SimpleDateFormat(LoggingConstants.DATE_FORMATTER);
        String formattedDate = formatter.format(now);
        return formattedDate.replace("-", ".");
    }

    private String getStackTrace(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString().trim();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getColumnList() {
        return columnList;
    }

    public void setColumnList(String columnList) {
        this.columnList = columnList;
    }

    public int getMaxTolerableConsecutiveFailure() {
        return maxTolerableConsecutiveFailure;
    }

    public void setMaxTolerableConsecutiveFailure(int maxTolerableConsecutiveFailure) {
        this.maxTolerableConsecutiveFailure = maxTolerableConsecutiveFailure;
    }

    public int getProcessingLimit() {
        return processingLimit;
    }

    public void setProcessingLimit(int processingLimit) {
        this.processingLimit = processingLimit;
    }

    public String getStreamDef() {
        return streamDef;
    }

    public void setStreamDef(String streamDef) {
        this.streamDef = streamDef;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    @Override
    protected void subAppend(LoggingEvent event) {
        try {
            Logger logger = Logger.getLogger(event.getLoggerName());
            TenantAwareLoggingEvent tenantEvent;
            if (event.getThrowableInformation() != null) {
                tenantEvent = new TenantAwareLoggingEvent(event.fqnOfCategoryClass, logger,
                        event.timeStamp, event.getLevel(), event.getMessage(),
                        event.getThrowableInformation().getThrowable());
            } else {
                tenantEvent = new TenantAwareLoggingEvent(event.fqnOfCategoryClass, logger,
                        event.timeStamp, event.getLevel(), event.getMessage(), null);
            }
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            int tenantId = carbonContext.getTenantId();
            if (tenantId == MultitenantConstants.INVALID_TENANT_ID) {
                String tenantDomain = TenantDomainSetter.getTenantDomain();
                if (tenantDomain != null && !tenantDomain.equals("")) {
                    try {
                        tenantId = getTenantIdForDomain(tenantDomain);
                    } catch (UserStoreException e) {
                        //Ignore this exception.
                        if (log.isDebugEnabled()) {
                            log.debug("Failed to obtain the tenant id");
                        }
                    }
                }
            }
            tenantEvent.setTenantId(String.valueOf(tenantId));
            String serviceName = TenantDomainSetter.getServiceName();
            String appName = carbonContext.getApplicationName();
            if (appName != null) {
                tenantEvent.setServiceName(carbonContext.getApplicationName());
            } else if (serviceName != null) {
                tenantEvent.setServiceName(serviceName);
            } else {
                tenantEvent.setServiceName("");
            }
            loggingEvents.add(tenantEvent);
        } finally {
            super.subAppend(event);
        }
    }

    private int getTenantIdForDomain(String tenantDomain) throws UserStoreException {
        int tenantId;
        TenantManager tenantManager = LoggingServiceComponent.getTenantManager();
        if (tenantDomain == null || tenantDomain.equals("")) {
            tenantId = MultitenantConstants.SUPER_TENANT_ID;
        } else {
            tenantId = tenantManager.getTenantId(tenantDomain);
        }
        return tenantId;
    }

    private final class AuditLogPublisherTask implements Runnable {
        private LoadBalancingDataPublisher loadBalancingDataPublisher = null;
        private int numOfConsecutiveFailures;

        @Override
        public void run() {
            try {
                for (int i = 0; i < loggingEvents.size(); i++) {
                    TenantAwareLoggingEvent tenantAwareLoggingEvent = loggingEvents.get(i);
                    if (i >= processingLimit) {
                        return;
                    }
                    publishLogEvent(tenantAwareLoggingEvent);
                    loggingEvents.remove(i);
                }
            } catch (Throwable t) {
                if(log.isDebugEnabled()){
                    log.debug("LogEventAppender Cannot publish log events");
                }
                numOfConsecutiveFailures++;
                if (numOfConsecutiveFailures >= getMaxTolerableConsecutiveFailure()) {
                    if (log.isDebugEnabled()) {
                        log.debug("WARN: Number of consecutive log publishing failures reached the threshold of " +
                                getMaxTolerableConsecutiveFailure() + ". Purging log event array. Some logs will be lost.");
                    }
                    loggingEvents.clear();
                    numOfConsecutiveFailures = 0;
                }
            }

        }

        private void publishLogEvent(TenantAwareLoggingEvent event) {

            String streamId;
            String streamName;
            String tenantId = event.getTenantId();

            if (tenantId.equals(String.valueOf(MultitenantConstants.INVALID_TENANT_ID))
                    || tenantId.equals(String.valueOf(MultitenantConstants.SUPER_TENANT_ID))) {
                tenantId = "0";
            }
            String serverKey = getCurrentServerName();
            String currDateStr = getCurrentDate();
            if (loadBalancingDataPublisher == null) {
                String path = CarbonUtils.getCarbonHome() + truststorePath;
                System.setProperty("javax.net.ssl.trustStore", path);
                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
                ArrayList<ReceiverGroup> allReceiverGroups = new ArrayList<ReceiverGroup>();
                ArrayList<String> receiverGroupUrls = DataPublisherUtil.getReceiverGroups(url);
                for (String aReceiverGroupURL : receiverGroupUrls) {
                    ArrayList<DataPublisherHolder> dataPublisherHolders = new ArrayList<DataPublisherHolder>();
                    boolean isFailOver = isFailOver(aReceiverGroupURL);
                    String[] getBamServerUrls = getBamServerUrls(aReceiverGroupURL, isFailOver);
                    for (String aUrl : getBamServerUrls) {
                        DataPublisherHolder aNode = new DataPublisherHolder(
                                null, aUrl.trim(), userName, password);
                        dataPublisherHolders.add(aNode);
                    }
                    ReceiverGroup group = new ReceiverGroup(
                            dataPublisherHolders, isFailOver);
                    allReceiverGroups.add(group);
                }
                loadBalancingDataPublisher = new LoadBalancingDataPublisher(allReceiverGroups);
            }
            StreamData data;
            try {
                data = StreamDefinitionCache.getStream(tenantId);
            } catch (ExecutionException e) {
                //Ignore the exception
                if (log.isDebugEnabled()) {
                    log.debug("Failed to get stream");
                }
                return;
            }

            if (currDateStr.equals(data.getDate())) {
                streamId = data.getStreamId();
                streamName = data.getStreamDefName();
            } else {
                if ((streamDef == null)
                        || streamDef.equals("$tenantId_$serverkey_$date")) {
                    streamName = "audit_log" + "." +  serverKey;
                } else {
                    streamName = streamDef;
                }
                StreamDefinition streamDefinition;
                try {
                    streamDefinition = new StreamDefinition(streamName,
                            LoggingConstants.DEFAULT_VERSION);
                } catch (MalformedStreamDefinitionException e) {
                    //Ignore
                    if (log.isDebugEnabled()) {
                        log.debug("MalformedStreamDefinition found");
                    }
                    return;
                }
                streamDefinition.setNickName("Logs");
                streamDefinition.setDescription("Logging Event Stream definition");
                List<Attribute> eventData = new ArrayList<Attribute>();
                eventData.add(new Attribute(LoggingConstants.TENANT_ID,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.SERVER_NAME,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.APP_NAME,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.LOG_TIME,
                        AttributeType.LONG));
                eventData.add(new Attribute(LoggingConstants.PRIORITY,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.MESSAGE,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.LOGGER,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.IP,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.INSTANCE,
                        AttributeType.STRING));
                eventData.add(new Attribute(LoggingConstants.STACKTRACE,
                        AttributeType.STRING));
                eventData.add(new Attribute("initiator",
                        AttributeType.STRING));
                eventData.add(new Attribute("action",
                        AttributeType.STRING));
                eventData.add(new Attribute("target",
                        AttributeType.STRING));
                eventData.add(new Attribute("result",
                        AttributeType.STRING));
                eventData.add(new Attribute("uuid",
                        AttributeType.STRING));

                streamDefinition.setPayloadData(eventData);
                List<Attribute> metaData = new ArrayList<Attribute>();
                metaData.add(new Attribute("clientType", AttributeType.STRING));
                streamDefinition.setMetaData(metaData);
                streamId = streamDefinition.getStreamId();
                if (!loadBalancingDataPublisher.isStreamDefinitionAdded(streamDefinition)) {
                    loadBalancingDataPublisher.addStreamDefinition(streamDefinition);
                }

                StreamDefinitionCache.putStream(tenantId, streamId, currDateStr, streamName);
            }
            List<String> patterns = Arrays.asList(columnList.split(","));
            String uuid = String.valueOf(UUID.randomUUID());
            String tenantID = "";
            String serverName = "";
            String appName = "";
            String logTime = "";
            String logger = "";
            String priority = "";
            String message = "";
            String stackTrace = "";
            String ip = "";
            String instance = "";
            String initiator = "";
            String action = "";
            String target = "";
            String result = "";


            for (String pattern : patterns) {
                TenantAwarePatternLayout patternLayout = new TenantAwarePatternLayout((pattern));
                if ((pattern).equals("%T")) {
                    tenantID = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%S")) {
                    serverName = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%A")) {
                    appName = patternLayout.format(event);
                    if (appName == null || appName.equals("")) {
                        appName = "";
                    }
                    continue;
                }
                if ((pattern).equals("%d")) {

                    logTime = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%c")) {
                    logger = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%p")) {
                    priority = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%m")) {
                    message = patternLayout.format(event);

                    String[] parts = message.split("\\|");
                    for (String messagePart : parts) {

                        if (messagePart.contains("Initiator :")) {
                            initiator = messagePart.split("Initiator :")[1];
                        }
                        if (messagePart.contains("Action :")) {
                            action = messagePart.split("Action :")[1];
                        }

                        if (messagePart.contains("Target :")) {
                            target = messagePart.split("Target :")[1];
                        }
                        if (messagePart.contains("Result :")) {
                            result = messagePart.split("Result :")[1];
                        }

                    }


                    continue;
                }
                if ((pattern).equals("%H")) {
                    ip = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%I")) {
                    instance = patternLayout.format(event);
                    continue;
                }
                if ((pattern).equals("%Stacktrace")) {
                    if (event.getThrowableInformation() != null) {
                        stackTrace = getStackTrace(event.getThrowableInformation().getThrowable());
                    } else {
                        stackTrace = "";
                    }
                }
            }
            Date date;
            DateFormat formatter;
            formatter = new SimpleDateFormat(
                    LoggingConstants.DATE_TIME_FORMATTER);
            try {
                date = formatter.parse(logTime);
            } catch (ParseException e) {
                //Ignore the exception
                if (log.isDebugEnabled()) {
                    log.debug("Failed to parse the date format");
                }
                return;
            }
            if (!"".equals(tenantID) && !"".equals(serverName) && !"".equals(logTime) && !"".equals(action)) {
                if (!streamId.isEmpty()) {
//                    if (DataHolder.getInstance().getAgent() != null) {
                        try {
                            loadBalancingDataPublisher.publish(streamName, "1.0.0",
                                    System.currentTimeMillis(),
                                    new Object[]{"external"}, null,
                                    new Object[]{tenantID, serverName, appName,
                                            date.getTime(), priority, message,
                                            logger, ip, instance, stackTrace, initiator, action, target, result, uuid});
                        } catch (AgentException e) {
                            //Ignore Exception
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to publish the event");
                            }
//                        }
                    }

                }
            }
        }

        private String[] getBamServerUrls(String aReceiverGroupURL, boolean failOver) {
            if (failOver) {
                return aReceiverGroupURL.split("\\|");
            } else {
                return aReceiverGroupURL.split(",");
            }
        }

        private boolean isFailOver(String aReceiverGroupURL) {
            return aReceiverGroupURL.contains("|");
        }
    }
}

