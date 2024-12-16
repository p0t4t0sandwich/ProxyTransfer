package dev.neuralnexus.proxytransfer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class TransferData {
    private final String origin;
    private final int port;
    private final String server;

    private TransferData(Builder builder) {
        this.origin = builder.origin;
        this.port = builder.port;
        this.server = builder.server;
    }

    public String origin() {
        return origin;
    }

    public int port() {
        return port;
    }

    public String server() {
        return server;
    }

    // https://stackoverflow.com/questions/2183240/java-integer-to-byte-array
    public static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static int byteArrayToInt(byte[] bytes) {
        return (bytes[0] << 24) | (bytes[1] << 16) | (bytes[2] << 8) | bytes[3];
    }

    public byte[] toBytes() {
        byte[] serverBytes = server.getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[4 + 4 + 4 + serverBytes.length];
        try {
            System.arraycopy(InetAddress.getByName(origin).getAddress(), 0, buffer, 0, 4);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Failed to get address", e);
        }
        byte[] portBytes = intToByteArray(port);
        System.arraycopy(portBytes, 0, buffer, 4, 4);
        byte[] lengthBytes = intToByteArray(serverBytes.length);
        System.arraycopy(lengthBytes, 0, buffer, 8, 4);
        System.arraycopy(serverBytes, 0, buffer, 12, serverBytes.length);
        return buffer;
    }

    public static TransferData fromBytes(byte[] buffer) {
        byte[] address = new byte[4];
        System.arraycopy(buffer, 0, address, 0, 4);
        String origin;
        try {
            origin = InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Failed to get address", e);
        }
        byte[] portBytes = new byte[4];
        System.arraycopy(buffer, 4, portBytes, 0, 4);
        int port = byteArrayToInt(portBytes);
        byte[] lengthBytes = new byte[4];
        System.arraycopy(buffer, 8, lengthBytes, 0, 4);
        int length = byteArrayToInt(lengthBytes);
        byte[] serverBytes = new byte[length];
        System.arraycopy(buffer, 12, serverBytes, 0, length);
        String server = new String(serverBytes, StandardCharsets.UTF_8);
        return new TransferData.Builder()
                .origin(origin)
                .port(port)
                .server(server)
                .build();
    }

    public static class Builder {
        private String origin;
        private int port;
        private String server;

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder server(String server) {
            this.server = server;
            return this;
        }

        public TransferData build() {
            if (origin == null || port == 0 || server == null) {
                throw new IllegalStateException("Host, port, and server must be set");
            }
            return new TransferData(this);
        }
    }
}
