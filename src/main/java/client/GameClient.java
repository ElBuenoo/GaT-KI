package client;

import GaT.engine.TurmWaechterEngine;
import GaT.model.*;
import GaT.validation.StreamlinedValidation;
import GaT.error.OptimizedExceptionHandling;

/**
 * GAME CLIENT - Updated for Optimized Engine Integration
 *
 * Replace TimedMinimax calls with TurmWaechterEngine
 */
public class GameClient {

    private final TurmWaechterEngine engine;
    private final String serverHost;
    private final int serverPort;
    private GameState currentState;
    private boolean isConnected = false;

    // Network components (placeholder - adapt to your actual network implementation)
    private Network networkHandler;

    public GameClient(TurmWaechterEngine engine, String host, int port) {
        this.engine = engine;
        this.serverHost = host;
        this.serverPort = port;
        this.networkHandler = new Network();

        System.out.println("🎮 GameClient initialized with optimized engine");
    }

    /**
     * Connect to game server
     */
    public boolean connect() {
        try {
            isConnected = networkHandler.connect(serverHost, serverPort);
            if (isConnected) {
                System.out.println("✅ Connected to server: " + serverHost + ":" + serverPort);
                engine.startNewGame();
                startGameLoop();
            }
            return isConnected;
        } catch (Exception e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Main game loop with optimized engine
     */
    private void startGameLoop() {
        System.out.println("🎯 Starting game loop with optimized engine");

        while (isConnected && engine.isGameActive()) {
            try {
                // Wait for server message
                String serverMessage = networkHandler.receiveMessage();

                if (serverMessage.startsWith("MOVE_REQUEST")) {
                    handleMoveRequest(serverMessage);
                } else if (serverMessage.startsWith("OPPONENT_MOVE")) {
                    handleOpponentMove(serverMessage);
                } else if (serverMessage.startsWith("GAME_STATE")) {
                    handleGameStateUpdate(serverMessage);
                }

            } catch (Exception e) {
                System.err.println("❌ Game loop error: " + e.getMessage());
                break;
            }
        }

        disconnect();
    }

    /**
     * Handle move request from server using optimized engine
     */
    private void handleMoveRequest(String message) {
        // Parse time limit from server message
        long timeLimit = parseTimeLimit(message);

        // Use optimized engine to find best move
        long startTime = System.currentTimeMillis();
        Move bestMove = engine.findBestMove(engine.getCurrentState(), timeLimit);
        long elapsed = System.currentTimeMillis() - startTime;

        if (bestMove != null) {
            // Validate move before sending (streamlined validation)
            StreamlinedValidation.ValidationResult validation =
                    StreamlinedValidation.validateSmart(
                            engine.getCurrentState(), bestMove,
                            StreamlinedValidation.ValidationMode.FULL
                    );

            if (validation == StreamlinedValidation.ValidationResult.VALID) {
                // Send move to server
                String moveMessage = formatMoveMessage(bestMove);
                networkHandler.sendMessage(moveMessage);

                // Make move locally
                engine.makeMove(bestMove);

                System.out.printf("✅ Move sent: %s (found in %dms)\n", bestMove, elapsed);
            } else {
                System.err.println("❌ Generated invalid move: " +
                        StreamlinedValidation.getErrorMessage(validation));
                sendErrorToServer("INVALID_MOVE_GENERATED");
            }
        } else {
            System.err.println("❌ No move found within time limit");
            sendErrorToServer("NO_MOVE_FOUND");
        }
    }

    /**
     * Handle opponent move with exception-free processing
     */
    private void handleOpponentMove(String message) {
        Move opponentMove = parseOpponentMove(message);

        if (opponentMove != null) {
            // Use exception-free move making
            OptimizedExceptionHandling.Result<GameState> result =
                    OptimizedExceptionHandling.makeMoveSafely(
                            engine.getCurrentState(), opponentMove
                    );

            if (result.isSuccess()) {
                // Update local state through engine
                engine.makeMove(opponentMove);
                System.out.println("✅ Opponent move applied: " + opponentMove);
            } else {
                System.err.println("❌ Opponent move failed: " + result.errorMessage);
                sendErrorToServer("INVALID_OPPONENT_MOVE");
            }
        }
    }

    /**
     * Handle game state updates
     */
    private void handleGameStateUpdate(String message) {
        try {
            GameState newState = parseGameState(message);
            if (newState != null) {
                // Validate new state
                StreamlinedValidation.ValidationResult validation =
                        StreamlinedValidation.validateSmart(
                                newState, null,
                                StreamlinedValidation.ValidationMode.BASIC
                        );

                if (validation == StreamlinedValidation.ValidationResult.VALID) {
                    currentState = newState;
                    System.out.println("✅ Game state updated");
                } else {
                    System.err.println("❌ Invalid game state from server");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to parse game state: " + e.getMessage());
        }
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        if (isConnected) {
            networkHandler.disconnect();
            isConnected = false;
            System.out.println("📡 Disconnected from server");

            // Print final performance report
            System.out.println(engine.getPerformanceReport());
        }
    }

    // === HELPER METHODS ===

    private long parseTimeLimit(String message) {
        // Parse time limit from server message format
        // Adapt this to your actual protocol
        try {
            String[] parts = message.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("TIME") && i + 1 < parts.length) {
                    return Long.parseLong(parts[i + 1]);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("⚠️ Failed to parse time limit, using default");
        }
        return 5000; // Default 5 seconds
    }

    private Move parseOpponentMove(String message) {
        // Parse opponent move from server message
        // Adapt this to your actual protocol
        try {
            String[] parts = message.split(" ");
            if (parts.length >= 3) {
                int from = Integer.parseInt(parts[1]);
                int to = Integer.parseInt(parts[2]);
                return new Move(from, to,1);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to parse opponent move: " + e.getMessage());
        }
        return null;
    }

    private GameState parseGameState(String message) {
        // Parse game state from server message
        // Adapt this to your actual protocol
        try {
            String fenPart = message.substring(message.indexOf("FEN:") + 4);
            return GameState.fromFen(fenPart.trim());
        } catch (Exception e) {
            System.err.println("❌ Failed to parse game state: " + e.getMessage());
            return null;
        }
    }

    private String formatMoveMessage(Move move) {
        // Format move for server protocol
        return "MOVE " + move.from + " " + move.to;
    }

    private void sendErrorToServer(String errorType) {
        try {
            networkHandler.sendMessage("ERROR " + errorType);
        } catch (Exception e) {
            System.err.println("❌ Failed to send error to server: " + e.getMessage());
        }
    }
}