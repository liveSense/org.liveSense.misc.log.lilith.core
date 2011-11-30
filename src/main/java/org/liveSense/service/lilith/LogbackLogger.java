package org.liveSense.service.lilith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.slf4j.bridge.SLF4JBridgeHandler;

import de.huxhorn.lilith.api.FileConstants;
import de.huxhorn.lilith.appender.InternalLilithAppender;
import de.huxhorn.lilith.data.access.AccessEvent;
import de.huxhorn.lilith.data.eventsource.EventWrapper;
import de.huxhorn.lilith.data.eventsource.SourceIdentifier;
import de.huxhorn.lilith.data.logging.LoggingEvent;
import de.huxhorn.lilith.engine.AccessFileBufferFactory;
import de.huxhorn.lilith.engine.EventHandler;
import de.huxhorn.lilith.engine.EventSource;
import de.huxhorn.lilith.engine.EventSourceListener;
import de.huxhorn.lilith.engine.FileBufferFactory;
import de.huxhorn.lilith.engine.LogFileFactory;
import de.huxhorn.lilith.engine.LoggingFileBufferFactory;
import de.huxhorn.lilith.engine.SourceManager;
import de.huxhorn.lilith.engine.impl.EventSourceImpl;
import de.huxhorn.lilith.engine.impl.LogFileFactoryImpl;
import de.huxhorn.lilith.engine.impl.sourcemanager.SourceManagerImpl;
import de.huxhorn.lilith.engine.impl.sourceproducer.AccessEventProtobufServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.impl.sourceproducer.LoggingEventProtobufServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.json.sourceproducer.LilithJsonMessageLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.json.sourceproducer.LilithJsonStreamLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.jul.sourceproducer.JulXmlStreamLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.xml.sourceproducer.LilithXmlMessageLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.xml.sourceproducer.LilithXmlStreamLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.eventhandlers.FileDumpEventHandler;
import de.huxhorn.lilith.eventhandlers.FileSplitterEventHandler;
import de.huxhorn.lilith.log4j.producer.Log4jLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.logback.appender.AccessMultiplexSocketAppender;
import de.huxhorn.lilith.logback.appender.ClassicJsonMultiplexSocketAppender;
import de.huxhorn.lilith.logback.appender.ClassicMultiplexSocketAppender;
import de.huxhorn.lilith.logback.appender.ClassicXmlMultiplexSocketAppender;
import de.huxhorn.lilith.logback.appender.ZeroDelimitedClassicJsonMultiplexSocketAppender;
import de.huxhorn.lilith.logback.appender.ZeroDelimitedClassicXmlMultiplexSocketAppender;
import de.huxhorn.lilith.logback.producer.LogbackAccessServerSocketEventSourceProducer;
import de.huxhorn.lilith.logback.producer.LogbackLoggingServerSocketEventSourceProducer;
import de.huxhorn.sulky.buffers.BlockingCircularBuffer;


@Component(immediate=true, metatype=true)
@Properties(value={
		@Property(name=LogbackLogger.PROP_LILITH_LOGRECEIVER, boolValue={true})
})
public class LogbackLogger {

	static Logger log = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
	
	public static final String PROP_LILITH_LOGRECEIVER = "lilith.logreceiver";
	private Boolean lilithLogReceiver = Boolean.FALSE;
	
	@Activate
	protected void activate(ComponentContext componentContext) {
		
		lilithLogReceiver = PropertiesUtil.toBoolean(componentContext.getProperties().get(PROP_LILITH_LOGRECEIVER), Boolean.FALSE );
		
//		log.info("Installing SLF4JBridgeHandler for JUL");
		try {
//			SLF4JBridgeHandler.install();
		} catch (Exception e) {
			log.error("Error installing SLF4JBridgeHandler for Java Util Logger", e);
		}

		if (lilithLogReceiver) {
			log.info("Activating lilith log receiver");
			try {
				activateLilithLogger(componentContext);
			} catch (Exception e) {
				log.error("Error activate Lilith server logger", e);
			}
		}


	}
	
	@Deactivate
	protected void deactivate(ComponentContext componentContext) {
		// Remove producers
		if (lilithLogReceiver) {
			log.info("Deactivating lilith log receiver");

			for (EventSource eventProducer : loggingEventSourceManager.getSources()) {
				log.info("Removing logging producer: "+eventProducer.getSourceIdentifier().getIdentifier()+" - "+eventProducer.getSourceIdentifier().getIdentifier());
				loggingEventSourceManager.removeEventProducer(eventProducer.getSourceIdentifier());
			}
			for (EventSource eventProducer : accessEventSourceManager.getSources()) {
				log.info("Removing access producer: "+eventProducer.getSourceIdentifier().getIdentifier()+" - "+eventProducer.getSourceIdentifier().getIdentifier());
				accessEventSourceManager.removeEventProducer(eventProducer.getSourceIdentifier());
			}
		}
//		log.info("Uninstalling SLF4JBridgeHandler for JUL");
		try {
//			SLF4JBridgeHandler.uninstall();
		} catch (Exception e) {
			log.error("Error uninstalling SLF4JBridgeHandler for Java Util Logger", e);
		}

	}
	
	public static final String LOGGING_FILE_SUBDIRECTORY =  "/logging";
	public static final String ACCESS_FILE_SUBDIRECTORY =   "/access";

	
	private LogFileFactory loggingFileFactory;
	private SourceManager<LoggingEvent> loggingEventSourceManager;
	private FileBufferFactory<LoggingEvent> loggingFileBufferFactory;
	private EventSourceListener<LoggingEvent> loggingSourceListener;

	private LogFileFactory accessFileFactory;
	private SourceManager<AccessEvent> accessEventSourceManager;
	private FileBufferFactory<AccessEvent> accessFileBufferFactory;
	private EventSourceListener<AccessEvent> accessSourceListener;

	private FileDumpEventHandler<LoggingEvent> loggingFileDump;
	private FileDumpEventHandler<AccessEvent> accessFileDump;

	
	private void setLoggingEventSourceManager(SourceManager<LoggingEvent> loggingEventSourceManager)
	{
		if(this.loggingEventSourceManager != null)
		{
			this.loggingEventSourceManager.removeEventSourceListener(loggingSourceListener);
		}
		this.loggingEventSourceManager = loggingEventSourceManager;
		if(this.loggingEventSourceManager != null)
		{
			this.loggingEventSourceManager.addEventSourceListener(loggingSourceListener);

			List<EventSource<LoggingEvent>> sources = this.loggingEventSourceManager.getSources();
			for(EventSource<LoggingEvent> source : sources)
			{
				//loggingEventViewManager.retrieveViewContainer(source);
			}
		}
	}

	public SourceManager<LoggingEvent> getLoggingEventSourceManager()
	{
		return loggingEventSourceManager;
	}

	private void setAccessEventSourceManager(SourceManager<AccessEvent> accessEventSourceManager)
	{
		if(this.accessEventSourceManager != null)
		{
			this.accessEventSourceManager.removeEventSourceListener(accessSourceListener);
		}
		this.accessEventSourceManager = accessEventSourceManager;
		if(this.accessEventSourceManager != null)
		{
			this.accessEventSourceManager.addEventSourceListener(accessSourceListener);

			List<EventSource<AccessEvent>> sources = this.accessEventSourceManager.getSources();
			for(EventSource<AccessEvent> source : sources)
			{
				//accessEventViewManager.retrieveViewContainer(source);
			}
		}
	}

	public SourceManager<AccessEvent> getAccessEventSourceManager()
	{
		return accessEventSourceManager;
	}

	
	public static String getLilithHome(BundleContext bundleContext)
			throws IOException {
		String slingHomePath = bundleContext.getProperty("sling.home");
		File solrHome = new File(slingHomePath, "lilith");
		if (!solrHome.isDirectory()) {
			if (!solrHome.mkdirs()) {
				return "./";
			}
		}
		return solrHome.getAbsolutePath();
	}

	public void activateLilithLogger(ComponentContext componentContext) throws IOException {
		
		loggingFileFactory = new LogFileFactoryImpl(new File(getLilithHome(componentContext.getBundleContext()), LOGGING_FILE_SUBDIRECTORY));
		accessFileFactory = new LogFileFactoryImpl(new File(getLilithHome(componentContext.getBundleContext()), ACCESS_FILE_SUBDIRECTORY));

		
		Map<String, String> loggingMetaData = new HashMap<String, String>();
		loggingMetaData.put(FileConstants.CONTENT_TYPE_KEY, FileConstants.CONTENT_TYPE_VALUE_LOGGING);
		loggingMetaData.put(FileConstants.CONTENT_FORMAT_KEY, FileConstants.CONTENT_FORMAT_VALUE_PROTOBUF);
		loggingMetaData.put(FileConstants.COMPRESSION_KEY, FileConstants.COMPRESSION_VALUE_GZIP);
		// TODO: configurable format and compressed

		loggingFileBufferFactory = new LoggingFileBufferFactory(loggingFileFactory, loggingMetaData);

		Map<String, String> accessMetaData = new HashMap<String, String>();
		accessMetaData.put(FileConstants.CONTENT_TYPE_KEY, FileConstants.CONTENT_TYPE_VALUE_ACCESS);
		accessMetaData.put(FileConstants.CONTENT_FORMAT_KEY, FileConstants.CONTENT_FORMAT_VALUE_PROTOBUF);
		accessMetaData.put(FileConstants.COMPRESSION_KEY, FileConstants.COMPRESSION_VALUE_GZIP);
		// TODO: configurable format and compressed

		accessFileBufferFactory = new AccessFileBufferFactory(accessFileFactory, accessMetaData);

		
		
		SourceIdentifier globalSourceIdentifier = new SourceIdentifier("global", null);

		loggingFileDump = new FileDumpEventHandler<LoggingEvent>(globalSourceIdentifier, loggingFileBufferFactory);
		accessFileDump = new FileDumpEventHandler<AccessEvent>(globalSourceIdentifier, accessFileBufferFactory);
		accessFileDump.setEnabled(true);
		loggingFileDump.setEnabled(true);
		
		BlockingCircularBuffer<EventWrapper<LoggingEvent>> loggingEventQueue = new BlockingCircularBuffer<EventWrapper<LoggingEvent>>(1000);
		BlockingCircularBuffer<EventWrapper<AccessEvent>> accessEventQueue = new BlockingCircularBuffer<EventWrapper<AccessEvent>>(1000);

		SourceManagerImpl<LoggingEvent> lsm = new SourceManagerImpl<LoggingEvent>(loggingEventQueue);
		// add global view
		EventSource<LoggingEvent> globalLoggingEventSource = new EventSourceImpl<LoggingEvent>(globalSourceIdentifier, loggingFileDump.getBuffer(), true);
		lsm.addSource(globalLoggingEventSource);

		// add internal lilith logging
		EventSource<LoggingEvent> lilithLoggingEventSource = new EventSourceImpl<LoggingEvent>(InternalLilithAppender.getSourceIdentifier(), InternalLilithAppender.getBuffer(), false);
		lsm.addSource(lilithLoggingEventSource);

		setLoggingEventSourceManager(lsm);

		SourceManagerImpl<AccessEvent> asm = new SourceManagerImpl<AccessEvent>(accessEventQueue);
		// add global view
		EventSource<AccessEvent> globalAccessEventSource = new EventSourceImpl<AccessEvent>(globalSourceIdentifier, accessFileDump.getBuffer(), true);
		asm.addSource(globalAccessEventSource);
		setAccessEventSourceManager(asm);

		try
		{
			loggingEventSourceManager.addEventSourceProducer(new LogbackLoggingServerSocketEventSourceProducer(4560));
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			loggingEventSourceManager.addEventSourceProducer(new Log4jLoggingServerSocketEventSourceProducer(4445));
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			LoggingEventProtobufServerSocketEventSourceProducer producer
				= new LoggingEventProtobufServerSocketEventSourceProducer
				(ClassicMultiplexSocketAppender.COMPRESSED_DEFAULT_PORT, true);
			loggingEventSourceManager.addEventSourceProducer(producer);
			// TODO: senderService.addLoggingProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			LoggingEventProtobufServerSocketEventSourceProducer producer
				= new LoggingEventProtobufServerSocketEventSourceProducer
				(ClassicMultiplexSocketAppender.UNCOMPRESSED_DEFAULT_PORT, false);

			loggingEventSourceManager.addEventSourceProducer(producer);
			// TODO: senderService.addLoggingProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}


		try
		{
			LilithXmlMessageLoggingServerSocketEventSourceProducer producer
				= new LilithXmlMessageLoggingServerSocketEventSourceProducer
				(ClassicXmlMultiplexSocketAppender.UNCOMPRESSED_DEFAULT_PORT, false);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			LilithXmlMessageLoggingServerSocketEventSourceProducer producer
				= new LilithXmlMessageLoggingServerSocketEventSourceProducer
				(ClassicXmlMultiplexSocketAppender.COMPRESSED_DEFAULT_PORT, true);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}


		try
		{
			LilithJsonMessageLoggingServerSocketEventSourceProducer producer
				= new LilithJsonMessageLoggingServerSocketEventSourceProducer
				(ClassicJsonMultiplexSocketAppender.UNCOMPRESSED_DEFAULT_PORT, false);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			LilithJsonMessageLoggingServerSocketEventSourceProducer producer
				= new LilithJsonMessageLoggingServerSocketEventSourceProducer
				(ClassicJsonMultiplexSocketAppender.COMPRESSED_DEFAULT_PORT, true);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}
//
		try
		{
			LilithXmlStreamLoggingServerSocketEventSourceProducer producer
				= new LilithXmlStreamLoggingServerSocketEventSourceProducer
				(ZeroDelimitedClassicXmlMultiplexSocketAppender.DEFAULT_PORT);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			LilithJsonStreamLoggingServerSocketEventSourceProducer producer
				= new LilithJsonStreamLoggingServerSocketEventSourceProducer
				(ZeroDelimitedClassicJsonMultiplexSocketAppender.DEFAULT_PORT);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			JulXmlStreamLoggingServerSocketEventSourceProducer producer
				= new JulXmlStreamLoggingServerSocketEventSourceProducer(11020);

			loggingEventSourceManager.addEventSourceProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}
		
		try
		{
			accessEventSourceManager.addEventSourceProducer(new LogbackAccessServerSocketEventSourceProducer(4570));
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}
		try
		{
			AccessEventProtobufServerSocketEventSourceProducer producer
				= new AccessEventProtobufServerSocketEventSourceProducer
				(AccessMultiplexSocketAppender.COMPRESSED_DEFAULT_PORT, true);

			accessEventSourceManager.addEventSourceProducer(producer);
			// TODO: senderService.addAccessProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}

		try
		{
			AccessEventProtobufServerSocketEventSourceProducer producer
				= new AccessEventProtobufServerSocketEventSourceProducer
				(AccessMultiplexSocketAppender.UNCOMPRESSED_DEFAULT_PORT, false);

			accessEventSourceManager.addEventSourceProducer(producer);
			// TODO: senderService.addAccessProducer(producer);
		}
		catch(IOException ex)
		{
			if(log.isWarnEnabled()) log.warn("Exception while creating event producer!", ex);
		}


		FileSplitterEventHandler<LoggingEvent> fileSplitterLoggingEventHandler =
			new FileSplitterEventHandler<LoggingEvent>(loggingFileBufferFactory, loggingEventSourceManager);

		List<EventHandler<LoggingEvent>> loggingHandlers = new ArrayList<EventHandler<LoggingEvent>>();

		loggingHandlers.add(fileSplitterLoggingEventHandler);
		loggingHandlers.add(loggingFileDump);

		// crashes the app using j2se 6
		//if(application.isMac())
		//{
		//	UserNotificationLoggingEventHandler notification = new UserNotificationLoggingEventHandler(application);
		//	loggingHandlers.add(notification);
		//}
		loggingEventSourceManager.setEventHandlers(loggingHandlers);
		loggingEventSourceManager.start();

		List<EventHandler<AccessEvent>> accessHandlers = new ArrayList<EventHandler<AccessEvent>>();

		FileSplitterEventHandler<AccessEvent> fileSplitterAccessEventHandler =
			new FileSplitterEventHandler<AccessEvent>(accessFileBufferFactory, accessEventSourceManager);
		accessHandlers.add(fileSplitterAccessEventHandler);
		accessHandlers.add(accessFileDump);

		accessEventSourceManager.setEventHandlers(accessHandlers);
		accessEventSourceManager.start();

	}
	

	public static void main(String[] args) {

		new LogbackLogger();
		while (1!=2) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.exit(0);
			}
		}

	}
}
