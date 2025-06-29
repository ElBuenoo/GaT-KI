package client;

import java.util.List;

import GaT.engine.GameEngine;
import GaT.engine.TimedGameEngine;
import GaT.engine.TimeManager;
import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.GameRules;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Game Client using new refactored architecture
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // NEW: Use new engines instead of static methods
    private static final GameEngine gameEngine = new GameEngine();
    private static final TimedGameEngine timedEngine = new TimedGameEngine();
    private static final TimeManager timeManager = new TimeManager(180000, 20);

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long gameStartTime = System.currentTimeMillis();

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());

        System.out.println("🎮 Guards & Towers AI Client v2.0 - REFACTORED");
        System.out.println("👤 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Using NEW ARCHITECTURE with PVS + Quiescence");
        System.out.println("🔧 Features: Clean design, no circular dependencies!");

        while (running) {
            try {
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("❌ Couldn't get game data");
                    break;
                }

                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                if (game.get("bothConnected").getAsBoolean()) {
                    String turn = game.get("turn").getAsString();
                    String board = game.get("board").getAsString();
                    long timeRemaining = game.get("time").getAsLong();

                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        moveNumber++;
                        System.out.println("\n" + "=".repeat(60));
                        System.out.println("📍 Move #" + moveNumber);
                        System.out.println("⏱️  Time remaining: " + (timeRemaining / 1000) + "s");

                        // Parse game state
                        GameState state = parseBoard(board);
                        state.redToMove = turn.equals("r");

                        // Display position
                        System.out.println("\nCurrent position:");
                        state.printBoard();

                        // Check game state
                        if (gameEngine.isGameOver(state)) {
                            System.out.println("⚠️  Game over detected!");
                        }

                        // Calculate time allocation
                        long allocatedTime = timeManager.calculateMoveTime(
                                timeRemaining, moveNumber, 60
                        );
                        System.out.println("🎯 Time allocated for this move: " + allocatedTime + "ms");

                        // Find best move using new TimedGameEngine
                        long startTime = System.currentTimeMillis();

                        Move bestMove = timedEngine.findBestMove(
                                state,
                                allocatedTime,
                                SearchConfig.SearchStrategy.PVS_Q
                        );

                        long searchTime = System.currentTimeMillis() - startTime;

                        if (bestMove != null) {
                            // Display search results
                            System.out.println("\n📊 Search Results:");
                            System.out.println("🎯 Best move: " + bestMove);
                            System.out.println("⏱️  Search time: " + searchTime + "ms");
                            System.out.println("📈 Evaluation: " + gameEngine.evaluate(state));

                            // Show search statistics
                            System.out.println("\n📊 Search Statistics:");
                            System.out.println(timedEngine.getSearchStats());

                            // Send move
                            String moveStr = formatMove(bestMove);
                            System.out.println("\n📤 Sending move: " + moveStr);
                            network.send(gson.toJson(moveStr));

                            // Periodic cache clearing
                            if (moveNumber % 10 == 0) {
                                gameEngine.clearCaches();
                                System.out.println("🧹 Caches cleared");
                            }
                        } else {
                            System.err.println("❌ No valid move found!");
                            // Try to find any legal move as fallback
                            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
                            if (!legalMoves.isEmpty()) {
                                bestMove = legalMoves.get(0);
                                String moveStr = formatMove(bestMove);
                                System.out.println("🚨 Playing emergency move: " + moveStr);
                                network.send(gson.toJson(moveStr));
                            } else {
                                System.err.println("❌ No legal moves available - game over?");
                                break;
                            }
                        }
                    }

                    // Check for game end
                    if (game.has("winner")) {
                        String winner = game.get("winner").getAsString();
                        System.out.println("\n" + "=".repeat(60));
                        System.out.println("🏁 GAME OVER!");
                        System.out.println("🏆 Winner: " + winner);

                        boolean weWon = (player == 0 && winner.equals("r")) ||
                                (player == 1 && winner.equals("b"));
                        System.out.println(weWon ? "🎉 We won!" : "😔 We lost!");

                        long totalTime = System.currentTimeMillis() - gameStartTime;
                        System.out.println("⏱️  Total game time: " + (totalTime / 1000) + "s");
                        System.out.println("📊 Total moves: " + moveNumber);

                        running = false;
                    }

                } else {
                    System.out.println("⏳ Waiting for opponent to connect...");
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                System.err.println("❌ Error in game loop: " + e.getMessage());
                e.printStackTrace();

                // Try to recover
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        // Cleanup
        timedEngine.shutdown();
        network.close();
        System.out.println("\n👋 Game client shutting down");
    }

    /**
     * Parse board string from server to GameState
     * Format depends on your server protocol
     */
    private static GameState parseBoard(String boardStr) {
        // This is a placeholder - implement according to your server protocol
        // Example: if server sends FEN notation
        try {
            return GameState.fromFen(boardStr);
        } catch (Exception e) {
            System.err.println("Failed to parse board: " + boardStr);
            // Return default state as fallback
            return new GameState();
        }
    }

    /**
     * Format move for server protocol
     */
    private static String formatMove(Move move) {
        // Format: "fromRank,fromFile,toRank,toFile"
        int fromRank = GameState.rank(move.from);
        int fromFile = GameState.file(move.from);
        int toRank = GameState.rank(move.to);
        int toFile = GameState.file(move.to);

        return String.format("%d,%d,%d,%d", fromRank, fromFile, toRank, toFile);
    }
}