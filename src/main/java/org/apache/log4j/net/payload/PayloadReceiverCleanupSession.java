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
import llc.berserkr.common.util.DoOnceConsumer;
import llc.berserkr.common.util.JacksonUtil;
import llc.ufwa.exception.FourOhOneException;
import llc.ufwa.util.WebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
//            .hostnameVerifier((hostname, session) -> true)
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
                        System.out.println("payload receiver flagged not connected");
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

                    System.out.println("connect " + host + " " + guid + " " + password);

                    gateway.connect();

                    System.out.println("connected " + host + " " + guid + " " + password);
                    gateway.authenticate(gateway.getProxyGUID(), password, (authenticated) -> {

                        System.out.println("authenticate " + host + " " + guid + " " + password);

                    });

                } catch (ProxyException | CommandException e) {
                    throw new RuntimeException(e);
                }

            }
            else {
                System.out.println("failure " + executed.isSuccessful() + " " + executed.body() + " " + executed.errorBody().string());
                throw new RuntimeException("failure " + executed.isSuccessful() + " " + executed.body() + " " + executed.errorBody().string());
            }
        } catch (IOException e) {

            System.out.println("failed request to launch channel " + e.getMessage());
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
}
