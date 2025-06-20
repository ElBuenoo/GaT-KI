package Gameserver_Client;

import java.util.List;

import GaT.MoveGenerator;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.TimeManager;
import GaT.TimedMinimax;
import GaT.Minimax;
import GaT.QuiescenceSearch; // CRITICAL: Import QuiescenceSearch for time integration
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 50); // 3 minutes, ~50 moves

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");

        while (running) {
            try {
                // Request game state
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("❌ Couldn't get game");
                    break;
                }

                // Parse game state
                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                // Check if both players are connected
                if (game.get("bothConnected").getAsBoolean()) {
                    String turn = game.get("turn").getAsString();
                    String board = game.get("board").getAsString();
                    long timeRemaining = game.get("time").getAsLong();

                    // Only act when it's our turn
                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        moveNumber++;
                        System.out.println("\n" + "=".repeat(50));
                        System.out.println("🔄 Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board: " + board);
                        System.out.println("⏱️ Time Remaining: " + formatTime(timeRemaining));

                        // Record move start time
                        lastMoveStartTime = System.currentTimeMillis();

                        // Get AI move using enhanced time management
                        String move = getAIMove(board, player, timeRemaining);

                        // Calculate actual time used
                        long actualTimeUsed = System.currentTimeMillis() - lastMoveStartTime;

                        // Send move to server
                        network.send(gson.toJson(move));
                        System.out.println("📤 Sent move: " + move);
                        System.out.println("⏱️ Actual time used: " + actualTimeUsed + "ms");

                        // Update time manager
                        timeManager.decrementEstimatedMovesLeft();

                        System.out.println("=".repeat(50));
                    }
                }

                // Check if game has ended
                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("🏁 Game has ended");
                    String result = game.has("winner") ?
                            ("Winner: " + game.get("winner").getAsString()) :
                            "Game finished";
                    System.out.println("🎯 " + result);

                    // Print final game statistics
                    long finalTimeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;
                    printGameStatistics(finalTimeRemaining);
                    running = false;
                }

                // Small delay to avoid busy-waiting
                Thread.sleep(100);

            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
                e.printStackTrace();
                running = false;
                break;
            }
        }
    }

    /**
     * ENHANCED AI move calculation with comprehensive time management and error handling
     */
    private static String getAIMove(String board, int player, long timeLeft) {
        try {
            GameState state = GameState.fromFen(board);

            // CRITICAL: Update ALL time-aware components
            timeManager.updateRemainingTime(timeLeft);
            Minimax.setRemainingTime(timeLeft);
            QuiescenceSearch.setRemainingTime(timeLeft); // ESSENTIAL: Sync QuiescenceSearch too!

            // Get sophisticated time allocation
            long timeForMove = timeManager.calculateTimeForMove(state);

            System.out.println("🧠 AI Analysis:");
            System.out.println("   ⏰ Time allocated: " + timeForMove + "ms");
            System.out.println("   🎯 Strategy: ULTIMATE (PVS + Quiescence + Time-Aware Evaluation)");
            System.out.println("   🎮 Phase: " + timeManager.getCurrentPhase());

            // Use the ultimate AI strategy with precise time control
            long searchStartTime = System.currentTimeMillis();


            Move bestMove = TimedMinimax.findBestMoveWithStrategy(state, 99, timeForMove, Minimax.SearchStrategy.PVS_Q);

            long searchTime = System.currentTimeMillis() - searchStartTime;

            // Log search efficiency
            System.out.println("   ✅ Search completed in: " + searchTime + "ms");
            if (searchTime < timeForMove * 0.5) {
                System.out.println("   ⚡ Efficient search (used " +
                        String.format("%.1f%%", 100.0 * searchTime / timeForMove) + " of allocated time)");
            } else if (searchTime > timeForMove * 0.95) {
                System.out.println("   ⏳ Deep search (used full allocated time)");
            }

            // Validate move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            if (!legalMoves.contains(bestMove)) {
                System.out.println("⚠️ WARNING: AI returned illegal move! Using fallback...");
                bestMove = findSafeFallbackMove(state, legalMoves);
            }

            return bestMove.toString();

        } catch (Exception e) {
            System.err.println("❌ Error in AI move calculation: " + e.getMessage());
            e.printStackTrace();

            // Emergency fallback: find any legal move quickly
            try {
                GameState state = GameState.fromFen(board);
                List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
                if (!legalMoves.isEmpty()) {
                    Move fallbackMove = findSafeFallbackMove(state, legalMoves);
                    System.out.println("🚨 Using emergency fallback move: " + fallbackMove);
                    return fallbackMove.toString();
                }
            } catch (Exception fallbackError) {
                System.err.println("❌ Even fallback failed: " + fallbackError.getMessage());
            }

            // Last resort - this should never happen
            System.err.println("🆘 CRITICAL: No legal moves found! Using default move...");
            return "A1-A2-1";
        }
    }

    /**
     * Find a safe fallback move when AI fails
     */
    private static Move findSafeFallbackMove(GameState state, List<Move> legalMoves) {
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("No legal moves available!");
        }

        // Priority 1: Look for winning moves (reaching enemy castle)
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;

        for (Move move : legalMoves) {
            if (move.to == enemyCastle) {
                System.out.println("🎯 Found winning move in fallback: " + move);
                return move;
            }
        }

        // Priority 2: Look for captures
        for (Move move : legalMoves) {
            if (Minimax.isCapture(move, state)) {
                System.out.println("🎯 Found capture in fallback: " + move);
                return move;
            }
        }

        // Priority 3: Guard moves if guard exists
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0) {
            int guardPos = Long.numberOfTrailingZeros(guardBit);
            for (Move move : legalMoves) {
                if (move.from == guardPos) {
                    System.out.println("🛡️ Found guard move in fallback: " + move);
                    return move;
                }
            }
        }

        // Priority 4: Any legal move
        Move fallback = legalMoves.get(0);
        System.out.println("🎲 Using random legal move in fallback: " + fallback);
        return fallback;
    }

    /**
     * Format time in human-readable way
     */
    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%.1fs", milliseconds / 1000.0);
        }
    }

    /**
     * Print comprehensive game statistics at the end
     */
    private static void printGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 GAME STATISTICS:");
        System.out.println("   🎮 Total moves played: " + moveNumber);
        System.out.println("   ⏱️ Final time remaining: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining; // Assuming 3 minute start
            long averageTimePerMove = totalTimeUsed / moveNumber;
            System.out.println("   ⚡ Average time per move: " + averageTimePerMove + "ms");

            if (finalTimeRemaining < 10_000) {
                System.out.println("   ⚠️ Game ended in time pressure!");
            } else if (finalTimeRemaining > 60_000) {
                System.out.println("   😎 Comfortable time management");
            }
        }

        System.out.println("   🧠 AI Strategy: ULTIMATE (PVS + Quiescence + Time-Aware Evaluation)");
        System.out.println("   🔧 Time Manager: Advanced with Emergency Modes");
        System.out.println("   ✅ Integration: Complete with Minimax time awareness");
    }
}