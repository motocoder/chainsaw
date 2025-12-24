package org.apache.log4j.net.payload;

import llc.berserkr.common.payload.auth.BaseAuthenticationProvider;
import llc.berserkr.common.payload.client.AuthenticatingPayloadGateway;
import llc.berserkr.common.payload.client.PayloadGateway;
import llc.berserkr.common.payload.connection.SocketClientConnection;
import llc.berserkr.common.payload.data.AuthenticatedCommand;
import llc.berserkr.common.payload.exception.CommandException;
import llc.berserkr.common.payload.exception.ProxyException;
import llc.berserkr.common.payload.util.CleanupManager;
import llc.berserkr.common.util.DoOnceConsumer;
import llc.berserkr.common.util.JacksonUtil;
import llc.ufwa.exception.FourOhOneException;
import llc.ufwa.util.WebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

import static llc.ufwa.util.DataUtils.bytesToChar;
import static llc.ufwa.util.DataUtils.bytesToInt;

public class PayloadReceiverCleanupSession extends CleanupManager.CleanupSession {

    //this feels wrong in a log4j project
    private static final Logger logger = LoggerFactory.getLogger(PayloadReceiverCleanupSession.class);

    private static final char BROADCAST = 'B';

    private final String host;
    private final String password;
    private final Consumer<byte[]> payloadConsumer;
    private final Consumer<Void> flagback;
    private final String guid;
    private AuthenticatingPayloadGateway gateway;

    public PayloadReceiverCleanupSession(
        final String host,
        final String guid,
        final String password,
        final Consumer<Void> flagback,
        final Consumer<byte []> payloadConsumer
    ) {

        this.guid = guid;
        this.host = host;
        this.password = password;
        this.flagback = flagback;
        this.payloadConsumer = payloadConsumer;

    }
    @Override
    public void start() {

        try {
            final String got =
                WebUtil.doGet(
                    new URL(
                        "https://www.berserkr.llc:8443/chainsawchoker/channel?channel=" + guid + "&password=" + password),
                        new HashMap<>()
                );

            final ChannelResponse response = JacksonUtil.deserialize(got, ChannelResponse.class);

            this.gateway = new AuthenticatingPayloadGateway(
                UUID.randomUUID().toString(),
                new SocketClientConnection(host, response.getPort()),
                new BaseAuthenticationProvider() {
                    @Override
                    public String getStoredPassword() {
                        return password;
                    }
                }
            );
            gateway.addConnectionConsumer(new DoOnceConsumer<>(connected -> {
                if (!connected) {
                    logger.debug("payload receiver flagged not connected");
                    flagback.accept(null);
                }
            }));
            gateway.addAuthenticatedListener((listenerControl, authenticatedCommand) -> {

                final byte[] data = authenticatedCommand.getTokenData().getData();

                logger.info("service auth command received " + data.length);

                char type = bytesToChar(new byte[]{data[0], data[1]});

                if (type == BROADCAST) {

                    final byte[] logEventData = new byte[data.length - 2];

                    System.arraycopy(data, 2, logEventData, 0, logEventData.length);

                    payloadConsumer.accept(logEventData);

                }

            });

            try {

                gateway.connect();
                gateway.authenticate(gateway.getProxyGUID(), password, (authenticated) -> {
                });

            } catch (ProxyException | CommandException e) {
                throw new RuntimeException(e);
            }
        } catch (FourOhOneException | IOException | JacksonUtil.DataException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void destroy() {

        if(this.gateway != null) {
            this.gateway.disconnect();
            this.gateway = null;
        }
    }
}
