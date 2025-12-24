package org.apache.log4j.net.payload;

public class ChannelResponse {

    private int port;

    private ChannelResponse() {}

    public ChannelResponse(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
