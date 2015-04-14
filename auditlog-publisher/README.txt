How to run.
------------------

1. Open the build.xml file and change the value of product.home property.

2. run ant command.

3. Copy the org.wso2.carbon.auditlog.publisher-1.0.0.jar file to <IS_HOME>/repository/components/lib

4. Open the log4j.properties file and add the following configuration. (Change BAM url, username,password according to BAM configurations.)

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

 5. Add the AUDIT_LOGFILE1 name

 log4j.logger.AUDIT_LOG=INFO, AUDIT_LOGFILE, AUDIT_LOGFILE1


 6. Start the BAM server first

 7. Start the IS server.

 8. Create the users/roles.



