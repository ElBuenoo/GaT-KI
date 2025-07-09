package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class Network {
    private Socket client;
    private String server;
    private int port;
    private String playerNumber;

    // Buffer size constants
    private static final int INITIAL_BUFFER_SIZE = 2048;
    private static final int MAX_BUFFER_SIZE = 8192;
    private static final int SOCKET_TIMEOUT = 5000; // 5 seconds

    public Network() {
        this.server = "game.guard-and-towers.com";
        this.port = 35002;
        this.playerNumber = connect();
    }

    public String getP() {
        return playerNumber;
    }

    private String connect() {
        try {
            client = new Socket(server, port);
            client.setSoTimeout(SOCKET_TIMEOUT); // Set timeout to prevent hanging

            byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
            InputStream in = client.getInputStream();
            int bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8).trim();
            } else {
                System.err.println("No data received on connect");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String send(String data) {
        if (client == null || client.isClosed()) {
            System.err.println("Socket is closed or null");
            return null;
        }

        try {
            // Send data to server
            OutputStream out = client.getOutputStream();
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            out.write(dataBytes);
            out.flush();

            // Receive response with dynamic buffer sizing
            InputStream in = client.getInputStream();

            // Start with initial buffer
            byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
            int totalBytesRead = 0;
            int bytesRead;

            // Read data in chunks to handle large responses
            StringBuilder response = new StringBuilder();

            // First read
            bytesRead = in.read(buffer);
            if (bytesRead > 0) {
                response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                totalBytesRead = bytesRead;

                // Check if more data is available
                while (in.available() > 0 && totalBytesRead < MAX_BUFFER_SIZE) {
                    bytesRead = in.read(buffer);
                    if (bytesRead > 0) {
                        response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                        totalBytesRead += bytesRead;
                    } else {
                        break;
                    }
                }
            }

            if (totalBytesRead == 0) {
                System.err.println("No data received in response");
                return null;
            }

            return response.toString();

        } catch (SocketTimeoutException e) {
            System.err.println("Socket timeout while waiting for response");
            return null;
        } catch (IOException e) {
            System.err.println("IO Error in send: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Clean shutdown of the network connection
     */
    public void close() {
        if (client != null && !client.isClosed()) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}