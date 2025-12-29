package org.apache.log4j.net.payload;

import okhttp3.OkHttpClient;
import llc.berserkr.common.payload.auth.BaseAuthenticationProvider;
import llc.berserkr.common.payload.client.AuthenticatingPayloadGateway;
import llc.berserkr.common.payload.client.PayloadGateway;
import llc.berserkr.common.payload.connection.SocketClientConnection;
import llc.berserkr.common.payload.data.AuthenticatedCommand;
import llc.berserkr.common.payload.exception.CommandException;
import llc.berserkr.common.payload.exception.ProxyException;
import llc.berserkr.common.payload.util.CleanupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

public class PayloadReceiverCleanupSession extends CleanupManager.CleanupSession {

    //this feels wrong in a log4j project
    private static final Logger logger = LoggerFactory.getLogger(PayloadReceiverCleanupSession.class);

    private static final char BROADCAST = 'B';

    private final String host;
    private final String password;
    private final Consumer<byte[]> payloadConsumer;
    private final Consumer<Void> flagback;
    private final String guid;
    private final LaunchAPI launchService;
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

        final OkHttpClient client = new OkHttpClient.Builder()
            .build();

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + host + "/chainsawchoker/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.launchService = retrofit.create(LaunchAPI.class);


    }
    @Override
    public void start() {

        final Call<ChannelResponse> call = launchService.launchChannel(guid, password);

        try {

            final Response<ChannelResponse> executed = call.execute();

            if (executed.isSuccessful() && executed.body() != null) {

                final ChannelResponse channelResponse = executed.body();

                this.gateway = new AuthenticatingPayloadGateway(
                    UUID.randomUUID().toString(),
                    new SocketClientConnection(host, channelResponse.getPort()),
                    new BaseAuthenticationProvider() {
                        @Override
                        public String getStoredPassword() {
                            return password;
                        }
                    }
                );
                gateway.addConnectionConsumer((connected -> {
                    if (!connected) {
                        flagback.accept(null);
                    }
                }));
                gateway.addAuthenticatedListener((listenerControl, authenticatedCommand) -> {

                    final byte[] data = authenticatedCommand.getTokenData().getData();

                    char type = bytesToChar(new byte[]{data[0], data[1]});

                    if (type == BROADCAST) {

                        final byte[] logEventData = new byte[data.length - 2];

                        System.arraycopy(data, 2, logEventData, 0, logEventData.length);

                        payloadConsumer.accept(logEventData);

                    }

                });

                try {

                    gateway.connect("chainsawChokerAPIKey");

                    gateway.authenticate(gateway.getProxyGUID(), password, (authenticated) -> {

                    });

                } catch (ProxyException | CommandException e) {
                    throw new RuntimeException(e);
                }

            }
            else {
                throw new RuntimeException("failure " + executed.isSuccessful() + " " + executed.body() + " " + executed.errorBody().string());
            }
        } catch (IOException e) {
            throw new RuntimeException("failed request to launch channel " + e.getMessage());
        }

    }

    @Override
    public void destroy() {

        if(this.gateway != null) {
            this.gateway.disconnect();
            this.gateway = null;
        }
    }

    public static int bytesToInt(byte [] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static char bytesToChar(byte[] bytes) {

        if(bytes.length != 2) { throw new IllegalArgumentException("bytes must be 2 lenght " + bytes.length); }

        final byte byte1 = bytes[0]; // Example: 'A' (most significant byte)
        final byte byte2 = bytes[1]; // Example: (least significant byte for 'A' in little-endian UTF-16)

        // Combine the two bytes into a short, then cast to char
        // Assuming byte1 is the most significant byte and byte2 is the least significant byte
        // This order is typical for big-endian systems, or if you're constructing a specific UTF-16 value.
        return (char) (((byte1 & 0xFF) << 8) | (byte2 & 0xFF));

    }
}
