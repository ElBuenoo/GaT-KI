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
    private static final int SOCKET_TIMEOUT = 15000; // ERHÖHT von 5000 auf 15000 (15 Sekunden)

    // Retry constants
    private static final int MAX_SEND_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500;

    public Network() {
        this.server = "game.guard-and-towers.com";
        this.port = 35000;
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

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

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

        // VERBESSERUNG: Retry-Mechanismus
        for (int attempt = 1; attempt <= MAX_SEND_RETRIES; attempt++) {
            try {
                String response = sendInternal(data);
                if (response != null) {
                    return response;
                }

                System.err.println("Attempt " + attempt + " failed, no response");

                if (attempt < MAX_SEND_RETRIES) {
                    System.out.println("Retrying in " + RETRY_DELAY_MS + "ms...");
                    Thread.sleep(RETRY_DELAY_MS);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Send attempt " + attempt + " error: " + e.getMessage());
                if (attempt == MAX_SEND_RETRIES) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    private String sendInternal(String data) throws IOException {
        // Send data to server
        OutputStream out = client.getOutputStream();
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        out.write(dataBytes);
        out.flush();

        // Debug output
        System.out.println("📤 Sent to server: " + data);

        // Receive response with dynamic buffer sizing
        InputStream in = client.getInputStream();

        // Start with initial buffer
        byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
        int totalBytesRead = 0;
        int bytesRead;

        // Read data in chunks to handle large responses
        StringBuilder response = new StringBuilder();

        try {
            Thread.sleep(200); // Give server time to process
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

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

        String responseStr = response.toString();
        System.out.println("📥 Received from server: " +
                (responseStr.length() > 100 ?
                        responseStr.substring(0, 100) + "..." : responseStr));

        return responseStr;
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

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return client != null && !client.isClosed() && client.isConnected();
    }

    /**
     * Get connection info for debugging
     */
    public void diagnose() {
        System.out.println("🔍 NETWORK DIAGNOSTICS:");
        System.out.println("  Server: " + server + ":" + port);
        System.out.println("  Player: " + playerNumber);
        System.out.println("  Connected: " + isConnected());
        System.out.println("  Timeout: " + SOCKET_TIMEOUT + "ms");
        System.out.println("  Max retries: " + MAX_SEND_RETRIES);
    }
}