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

import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import llc.berserkr.common.payload.auth.BaseAuthenticationProvider;
import llc.berserkr.common.payload.connection.SocketClientConnection;
import llc.berserkr.common.payload.util.CleanupManager;
import org.apache.log4j.chainsaw.logevents.ChainsawLoggingEvent;
import org.apache.log4j.chainsaw.logevents.ChainsawLoggingEventBuilder;
import org.apache.log4j.chainsaw.receiver.ChainsawReceiverSkeleton;
import org.apache.log4j.net.payload.PayloadReceiverCleanupSession;
import org.apache.log4j.spi.Decoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import llc.berserkr.common.payload.client.AuthenticatingPayloadGateway;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
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
    private static final Logger logger = LogManager.getLogger(PayloadProxyReceiver.class);

    // default to log4j xml decoder
    protected String decoder = "org.apache.log4j.xml.XMLDecoder";

    private boolean active = false;

    private CleanupManager<PayloadReceiverCleanupSession> cleanup;
    private String password;
//    private String host;
    private String guid;

    {

        this.addPropertyChangeListener("password", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {

                logger.debug("Password changed: " + evt.getOldValue() + " - " + evt.getNewValue());

                setPassword(evt.getNewValue().toString());
            }
        });

//        this.addPropertyChangeListener("host", new PropertyChangeListener() {
//            @Override
//            public void propertyChange(PropertyChangeEvent evt) {
//
//                logger.debug("host changed: " + evt.getOldValue() + " - " + evt.getNewValue());
//
//                setHost(evt.getNewValue().toString());
//            }
//        });

        this.addPropertyChangeListener("guid", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {

                logger.debug("guid changed: " + evt.getOldValue() + " - " + evt.getNewValue());

                setGuid(evt.getNewValue().toString());
            }
        });

    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

//    public void setHost(String host) {
//        this.host = host;
//    }

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
        // mark this as no longer running
        active = false;

        doShutdown();
    }

    /**
     * Does the actual shutting down by closing the server socket
     * and any connected sockets that have been created.
     */
    private synchronized void doShutdown() {
        active = false;

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
        active = true;

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
            active = false;
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

        Genson genson = new GensonBuilder().useDateAsTimestamp(true).create();

        ChainsawLoggingEventBuilder build = new ChainsawLoggingEventBuilder();

        ECSLogEvent evt = genson.deserialize(data, ECSLogEvent.class);

        append(evt.toChainsawLoggingEvent(build));

    }
}
