package org.liveSense.service.lilith;

import org.apache.felix.scr.annotations.Component;
import org.apache.sling.commons.log.internal.slf4j.LogConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.access.PatternLayoutEncoder;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;


@Component(immediate=true)
public class LogbackLogger {

	static Logger log = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
	
	public LogbackLogger() {
		System.out.println("Init backlog service");
		
		
		// TODO Auto-generated constructor stub
//		LogConfigManager manager = (LogConfigManager)LoggerFactory.getILoggerFactory();
/*		
		try {
			LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
			RollingFileAppender rfAppender = new RollingFileAppender();
		    rfAppender.setContext(loggerContext);
		    rfAppender.setFile("/Project/testFile.log");
		    
		    FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
		    rollingPolicy.setContext(loggerContext);
		    // rolling policies need to know their parent
		    // it's one of the rare cases, where a sub-component knows about its parent
		    rollingPolicy.setParent(rfAppender);
		    rollingPolicy.setFileNamePattern("/Project/testFile.%i.log.zip");
		    rollingPolicy.start();
		    
		    SizeBasedTriggeringPolicy triggeringPolicy = new SizeBasedTriggeringPolicy();
		    triggeringPolicy.setMaxFileSize("5MB");
		    triggeringPolicy.start();
	
		    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		    encoder.setContext(loggerContext);
		    encoder.setPattern("%-4relative [%thread] %-5level %logger{35} - %msg%n");
		    encoder.start();
	
		    rfAppender.setEncoder(encoder);
		    rfAppender.setRollingPolicy(rollingPolicy);
		    rfAppender.setTriggeringPolicy(triggeringPolicy);
	
		    rfAppender.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	    
	    log.info("teszt");
	  */  
	}
	
	public static void main(String[] args) {
		new LogbackLogger();
	}
	
}
