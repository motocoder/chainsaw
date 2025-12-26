/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.log4j.net;

import llc.berserkr.common.payload.util.CleanupManager;
import llc.berserkr.common.util.JacksonUtil;
import org.apache.log4j.chainsaw.logevents.ChainsawLoggingEventBuilder;
import org.apache.log4j.chainsaw.receiver.ChainsawReceiverSkeleton;
import org.apache.log4j.net.payload.LogEvent;
import org.apache.log4j.net.payload.PayloadReceiverCleanupSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * XMLSocketReceiver receives a remote logging event via XML on a configured
 * socket and "posts" it to a LoggerRepository as if the event were
 * generated locally. This class is designed to receive events from
 * the XMLSocketAppender class (or classes that send compatible events).
 * <p>
 * This receiver supports log files created using log4j's XMLLayout, as well as java.util.logging
 * XMLFormatter (via the org.apache.log4j.spi.Decoder interface).
 * <p>
 * By default, log4j's XMLLayout is supported (no need to specify a decoder in that case).
 * <p>
 * To configure this receiver to support java.util.logging's XMLFormatter, specify a 'decoder' param
 * of org.apache.log4j.xml.UtilLoggingXMLDecoder.
 * <p>
 * Once the event has been "posted", it will be handled by the
 * appenders currently configured in the LoggerRespository.
 *
 * @author Mark Womack
 * @author Scott Deboy &lt;sdeboy@apache.org&gt;
 */
public class PayloadProxyReceiver extends ChainsawReceiverSkeleton implements BerserkrBased {
    private static final Logger logger = LoggerFactory.getLogger(PayloadProxyReceiver.class);

    // default to log4j xml decoder
    protected String decoder = "org.apache.log4j.xml.XMLDecoder";

    private CleanupManager<PayloadReceiverCleanupSession> cleanup;
    private String password;
    private String guid;

    {

        this.addPropertyChangeListener("password", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setPassword(evt.getNewValue().toString());
            }
        });

        this.addPropertyChangeListener("guid", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setGuid(evt.getNewValue().toString());
            }
        });

    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public void setPassword(String newValue) {
        this.password = newValue;
    }

    /**
     * The MulticastDNS zone advertised by an XMLSocketReceiver
     */
    public static final String ZONE = "_log4j_xml_tcpaccept_receiver.local.";

    /*
     * Log4j doesn't provide an XMLSocketAppender, but the MulticastDNS zone that should be advertised by one is:
     * _log4j_xml_tcpconnect_appender.local.
     */


    public String getDecoder() {
        return decoder;
    }

    /**
     * Specify the class name implementing org.apache.log4j.spi.Decoder that can process the file.
     */
    public void setDecoder(String decoder) {
        this.decoder = decoder;
    }

    /**
     * Starts the XMLSocketReceiver with the current options.
     */
    public void activateOptions() {
        start();
    }

    /**
     * Called when the receiver should be stopped. Closes the
     * server socket and all of the open sockets.
     */
    @Override
    public synchronized void shutdown() {

        doShutdown();
    }

    /**
     * Does the actual shutting down by closing the server socket
     * and any connected sockets that have been created.
     */
    private synchronized void doShutdown() {

        logger.debug("{} doShutdown called", getName());

        // close the server socket
        closeServerSocket();
    }

    /**
     * Closes the server socket, if created.
     */
    private void closeServerSocket() {
        logger.debug("{} closing server socket", getName());

        try {
            if(cleanup != null) {
                cleanup.shutdown();
            }
        } catch (Exception e) {
            // ignore for now
        }

        cleanup = null;
    }

    @Override
    public synchronized void start() {

        logger.debug("Starting receiver");

        /**
         * Ensure we start fresh.
         */
        logger.debug("performing socket cleanup prior to entering loop for {}", name);
        closeServerSocket();
        logger.debug("socket cleanup complete for {}", name);

        // start the server socket
        try {

            //creates a cleanup manager that will destroy everything and restart
            cleanup = new CleanupManager<>() {
                @Override
                public PayloadReceiverCleanupSession build(ExecutorService executorService, Consumer<Void> consumer) {
                    return new PayloadReceiverCleanupSession("www.berserkr.llc", getGuid(), getPassword(), consumer, (payload) -> {
                        parseIncomingData(payload);
                    });
                }
            };

            cleanup.start();

        } catch (Exception e) {
            logger.error("error starting XMLSocketReceiver (" + this.getName() + "), receiver did not start", e);
            doShutdown();
        }

    }

    public String getPassword() {
        return password;
    }

    public String getGuid() {
        return guid;
    }

    private void parseIncomingData(byte [] data) {

        ChainsawLoggingEventBuilder build = new ChainsawLoggingEventBuilder();

        try {
            LogEvent event = JacksonUtil.deserialize(new String(data, StandardCharsets.UTF_8), LogEvent.class);

            build.clear();
            final String timeStamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.time), ZoneId.systemDefault()).toString();

            build.setLevelFromString(event.level)
                .setMessage(event.message)
                .setLogger(event.name)
                .setThreadName(event.threadName)
                .setTimestamp(ZonedDateTime.parse(timeStamp).toInstant());

            append(build.create());

        } catch (JacksonUtil.DataException e) {
            logger.error("error parsing incoming data", e);
        }

    }
}
