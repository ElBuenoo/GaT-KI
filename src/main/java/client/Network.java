package client;

import java.io.*;
import java.net.*;

/**
 * SIMPLE NETWORK CLASS - Fixes GameClient compilation errors
 *
 * PROVIDES:
 * ✅ All methods referenced by GameClient
 * ✅ Basic socket-based networking
 * ✅ Exception-safe operations
 */
public class Network {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected = false;

    /**
     * Connect to server
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("✅ Connected to " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send message to server
     */
    public boolean sendMessage(String message) {
        if (!connected || writer == null) {
            return false;
        }

        try {
            writer.println(message);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Send failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Receive message from server
     */
    public String receiveMessage() throws IOException {
        if (!connected || reader == null) {
            throw new IOException("Not connected");
        }

        return reader.readLine();
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        connected = false;

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();

            System.out.println("📡 Disconnected from server");
        } catch (IOException e) {
            System.err.println("⚠️ Disconnect warning: " + e.getMessage());
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}