package Gameserver_Client;

import java.util.List;

import GaT.MoveGenerator;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.TimeManager;
import GaT.TimedMinimax;
import GaT.Minimax;
import GaT.QuiescenceSearch;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * TOURNAMENT-ENHANCED GameClient with adaptive AI strategy
 *
 * Tournament Features:
 * - Auto-adaptive strategy selection based on time pressure
 * - Enhanced fallback mechanisms with priority-based move selection
 * - Comprehensive performance analysis and statistics
 * - Tournament-grade error handling and emergency procedures
 * - Complete synchronization across all AI components
 * - Real-time performance monitoring and adjustment
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // === TOURNAMENT-OPTIMIZED CONSTANTS ===
    private static final boolean TOURNAMENT_MODE = true;
    private static final long TOURNAMENT_MIN_TIME = 500;        // Minimum 500ms pro Zug
    private static final long TOURNAMENT_EMERGENCY_TIME = 200;  // Emergency minimum
    private static final double TOURNAMENT_TIME_SAFETY = 0.95;  // 5% Sicherheitspuffer

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 50); // 3 minutes, ~50 moves

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());

        System.out.println("🏆 TOURNAMENT AI INITIALIZED");
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Strategy: ULTIMATE TOURNAMENT (Auto-Adaptive)");
        System.out.println("⚙️ Mode: " + (TOURNAMENT_MODE ? "TOURNAMENT" : "STANDARD"));

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
                        System.out.println("\n" + "=".repeat(60));
                        System.out.println("🏆 TOURNAMENT Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board: " + board);
                        System.out.println("⏱️ Time Remaining: " + formatTime(timeRemaining));

                        // Record move start time
                        lastMoveStartTime = System.currentTimeMillis();

                        // Get TOURNAMENT AI move
                        String move = getAIMove(board, player, timeRemaining);

                        // Calculate actual time used
                        long actualTimeUsed = System.currentTimeMillis() - lastMoveStartTime;

                        // Send move to server
                        network.send(gson.toJson(move));
                        System.out.println("📤 TOURNAMENT Move sent: " + move);
                        System.out.println("⏱️ Actual time used: " + actualTimeUsed + "ms");

                        // Update time manager
                        timeManager.decrementEstimatedMovesLeft();

                        System.out.println("=".repeat(60));
                    }
                }

                // Check if game has ended
                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("🏁 TOURNAMENT GAME ENDED");
                    String result = game.has("winner") ?
                            ("🏆 Winner: " + game.get("winner").getAsString()) :
                            "🤝 Game finished";
                    System.out.println(result);

                    // Print comprehensive tournament statistics
                    long finalTimeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;
                    printTournamentGameStatistics(finalTimeRemaining);
                    running = false;
                }

                // Small delay to avoid busy-waiting
                Thread.sleep(100);

            } catch (Exception e) {
                System.out.println("❌ TOURNAMENT ERROR: " + e.getMessage());
                e.printStackTrace();
                running = false;
                break;
            }
        }

        System.out.println("🏆 TOURNAMENT AI SESSION COMPLETED");
    }

    /**
     * TOURNAMENT-ENHANCED AI move calculation with comprehensive strategy selection
     */
    private static String getAIMove(String board, int player, long timeLeft) {
        try {
            GameState state = GameState.fromFen(board);

            // === TOURNAMENT-CRITICAL TIME SYNCHRONIZATION ===
            timeManager.updateRemainingTime(timeLeft);
            Minimax.setRemainingTime(timeLeft);         // CRITICAL
            QuiescenceSearch.setRemainingTime(timeLeft); // CRITICAL

            // === TOURNAMENT TIME ALLOCATION ===
            long timeForMove = timeManager.calculateTimeForMove(state);

            // Tournament safety checks
            timeForMove = Math.max(timeForMove, TOURNAMENT_MIN_TIME);
            if (timeLeft < 5000) { // Extreme time pressure
                timeForMove = Math.max(TOURNAMENT_EMERGENCY_TIME, timeLeft / 20);
            }

            // Apply safety margin
            timeForMove = (long) (timeForMove * TOURNAMENT_TIME_SAFETY);

            System.out.println("🏆 TOURNAMENT AI Analysis:");
            System.out.println("   ⏰ Time allocated: " + timeForMove + "ms (of " + formatTime(timeLeft) + " remaining)");
            System.out.println("   🎯 Strategy: ULTIMATE TOURNAMENT (Auto-Select)");
            System.out.println("   🎮 Phase: " + timeManager.getCurrentPhase());
            System.out.println("   🧠 Mode: " + (TOURNAMENT_MODE ? "TOURNAMENT" : "STANDARD"));

            long searchStartTime = System.currentTimeMillis();

            // === TOURNAMENT STRATEGY SELECTION ===
            Move bestMove = selectAndExecuteTournamentStrategy(state, timeForMove, timeLeft);

            long searchTime = System.currentTimeMillis() - searchStartTime;

            // === TOURNAMENT PERFORMANCE ANALYSIS ===
            analyzeTournamentPerformance(searchTime, timeForMove, timeLeft);

            // === TOURNAMENT MOVE VALIDATION ===
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            if (!legalMoves.contains(bestMove)) {
                System.out.println("⚠️ TOURNAMENT WARNING: AI returned illegal move! Using tournament fallback...");
                bestMove = findTournamentFallbackMove(state, legalMoves);
            }

            return bestMove.toString();

        } catch (Exception e) {
            System.err.println("❌ TOURNAMENT ERROR in AI move calculation: " + e.getMessage());
            e.printStackTrace();

            // === TOURNAMENT EMERGENCY FALLBACK ===
            return getTournamentEmergencyMove(board, player, timeLeft);
        }
    }

    /**
     * TOURNAMENT strategy selection based on time pressure and position complexity
     */
    private static Move selectAndExecuteTournamentStrategy(GameState state, long timeForMove, long timeLeft) {
        if (!TOURNAMENT_MODE) {
            // Fallback to old method if tournament mode disabled
            return TimedMinimax.findBestMoveUltimate(state, 99, timeForMove);
        }

        // === TOURNAMENT STRATEGY AUTO-SELECTION ===

        if (timeLeft < 3000) {
            // EXTREME TIME PRESSURE
            System.out.println("🚨 TOURNAMENT: Extreme time pressure - using Alpha-Beta");
            return TimedMinimax.findBestMoveWithStrategy(state, 12, timeForMove,
                    Minimax.SearchStrategy.ALPHA_BETA);
        }
        else if (timeLeft < 10000) {
            // HIGH TIME PRESSURE
            System.out.println("⚠️ TOURNAMENT: High time pressure - using Alpha-Beta + Quiescence");
            return TimedMinimax.findBestMoveWithStrategy(state, 15, timeForMove,
                    Minimax.SearchStrategy.ALPHA_BETA_Q);
        }
        else if (timeLeft < 30000) {
            // MODERATE TIME PRESSURE
            System.out.println("⏰ TOURNAMENT: Moderate time - using PVS");
            return TimedMinimax.findBestMoveWithStrategy(state, 18, timeForMove,
                    Minimax.SearchStrategy.PVS);
        }
        else {
            // PLENTY OF TIME - USE ULTIMATE POWER
            System.out.println("🚀 TOURNAMENT: Full power - using Ultimate Strategy");
            return TimedMinimax.findBestMoveUltimate(state, 25, timeForMove);
        }
    }

    /**
     * COMPREHENSIVE tournament performance analysis
     */
    private static void analyzeTournamentPerformance(long searchTime, long timeForMove, long timeLeft) {
        double timeUsageRate = (double) searchTime / timeForMove;
        double remainingTimeRate = (double) timeLeft / 180000; // Assuming 3min start time

        System.out.println("📊 TOURNAMENT Performance Analysis:");
        System.out.println("   ✅ Search completed in: " + searchTime + "ms");
        System.out.println("   📈 Time usage: " + String.format("%.1f%%", timeUsageRate * 100) +
                " of allocated time");
        System.out.println("   🕐 Remaining time: " + String.format("%.1f%%", remainingTimeRate * 100) +
                " of game time");

        if (timeUsageRate < 0.5) {
            System.out.println("   ⚡ EFFICIENT: Used only " + String.format("%.1f%%", timeUsageRate * 100) +
                    " of allocated time");
        } else if (timeUsageRate > 0.95) {
            System.out.println("   ⏳ DEEP SEARCH: Used full allocated time for thorough analysis");
        }

        if (remainingTimeRate < 0.1) {
            System.out.println("   🚨 TIME WARNING: Only " + String.format("%.1f%%", remainingTimeRate * 100) +
                    " of game time remaining!");
        }
    }

    /**
     * ENHANCED tournament fallback with priority-based move selection
     */
    private static Move findTournamentFallbackMove(GameState state, List<Move> legalMoves) {
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("TOURNAMENT CRITICAL: No legal moves available!");
        }

        System.out.println("🛠️ TOURNAMENT Fallback Analysis:");

        // Priority 1: WINNING MOVES (absolute priority)
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;

        for (Move move : legalMoves) {
            if (move.to == enemyCastle && move.amountMoved == 1) {
                // Check if it's actually a guard move
                long fromBit = GameState.bit(move.from);
                boolean isGuardMove = (isRed && (state.redGuard & fromBit) != 0) ||
                        (!isRed && (state.blueGuard & fromBit) != 0);
                if (isGuardMove) {
                    System.out.println("🎯 TOURNAMENT Fallback: Found WINNING move: " + move);
                    return move;
                }
            }
        }

        // Priority 2: GUARD CAPTURES (extremely high value)
        for (Move move : legalMoves) {
            long toBit = GameState.bit(move.to);
            boolean capturesEnemyGuard = (isRed && (state.blueGuard & toBit) != 0) ||
                    (!isRed && (state.redGuard & toBit) != 0);
            if (capturesEnemyGuard) {
                System.out.println("👑 TOURNAMENT Fallback: Found GUARD CAPTURE: " + move);
                return move;
            }
        }

        // Priority 3: HIGH-VALUE CAPTURES
        Move bestCapture = null;
        int bestCaptureValue = 0;

        for (Move move : legalMoves) {
            if (Minimax.isCapture(move, state)) {
                int captureValue = Minimax.scoreMove(state, move);
                if (captureValue > bestCaptureValue) {
                    bestCaptureValue = captureValue;
                    bestCapture = move;
                }
            }
        }

        if (bestCapture != null && bestCaptureValue > 300) {
            System.out.println("💎 TOURNAMENT Fallback: Found HIGH-VALUE CAPTURE: " + bestCapture +
                    " (value: " + bestCaptureValue + ")");
            return bestCapture;
        }

        // Priority 4: GUARD SAFETY MOVES
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0) {
            int guardPos = Long.numberOfTrailingZeros(guardBit);

            if (Minimax.isGuardInDangerImproved(state, isRed)) {
                for (Move move : legalMoves) {
                    if (move.from == guardPos) {
                        // Quick check if this move gets guard to safety
                        System.out.println("🛡️ TOURNAMENT Fallback: Found GUARD SAFETY move: " + move);
                        return move;
                    }
                }
            }
        }

        // Priority 5: GUARD ADVANCEMENT
        if (guardBit != 0) {
            int guardPos = Long.numberOfTrailingZeros(guardBit);
            Move bestAdvancement = null;
            int bestAdvancementScore = -1000;

            for (Move move : legalMoves) {
                if (move.from == guardPos && move.amountMoved == 1) {
                    int toRank = GameState.rank(move.to);
                    int advancementScore = isRed ? (6 - toRank) : toRank;

                    if (advancementScore > bestAdvancementScore) {
                        bestAdvancementScore = advancementScore;
                        bestAdvancement = move;
                    }
                }
            }

            if (bestAdvancement != null) {
                System.out.println("🎯 TOURNAMENT Fallback: Found GUARD ADVANCEMENT: " + bestAdvancement);
                return bestAdvancement;
            }
        }

        // Priority 6: ANY REASONABLE MOVE
        Move fallback = legalMoves.get(0);
        System.out.println("🎲 TOURNAMENT Fallback: Using first legal move: " + fallback);
        return fallback;
    }

    /**
     * ENHANCED emergency fallback for critical situations
     */
    private static String getTournamentEmergencyMove(String board, int player, long timeLeft) {
        try {
            System.out.println("🆘 TOURNAMENT EMERGENCY FALLBACK ACTIVATED");

            GameState state = GameState.fromFen(board);
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (!legalMoves.isEmpty()) {
                Move emergencyMove = findTournamentFallbackMove(state, legalMoves);
                System.out.println("🚨 TOURNAMENT Emergency move: " + emergencyMove);
                return emergencyMove.toString();
            }

        } catch (Exception fallbackError) {
            System.err.println("❌ TOURNAMENT Even emergency fallback failed: " + fallbackError.getMessage());
        }

        // Last resort - this should never happen in tournament
        System.err.println("🆘 TOURNAMENT CRITICAL: Using hardcoded emergency move!");
        return "A1-A2-1";
    }

    /**
     * ENHANCED time formatting for better readability
     */
    private static String formatTime(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        }

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
     * COMPREHENSIVE tournament game statistics
     */
    private static void printTournamentGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 TOURNAMENT GAME STATISTICS:");
        System.out.println("   🎮 Total moves played: " + moveNumber);
        System.out.println("   ⏱️ Final time remaining: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining; // Assuming 3min start
            long averageTimePerMove = totalTimeUsed / moveNumber;
            System.out.println("   ⚡ Average time per move: " + averageTimePerMove + "ms");

            // Tournament performance assessment
            if (finalTimeRemaining < 5_000) {
                System.out.println("   🚨 TOURNAMENT RESULT: Finished in extreme time pressure!");
            } else if (finalTimeRemaining < 30_000) {
                System.out.println("   ⚠️ TOURNAMENT RESULT: Finished in time pressure");
            } else if (finalTimeRemaining > 90_000) {
                System.out.println("   😎 TOURNAMENT RESULT: Excellent time management");
            } else {
                System.out.println("   ✅ TOURNAMENT RESULT: Good time management");
            }

            // Performance rating
            double timeEfficiency = (double) finalTimeRemaining / 180_000;
            if (timeEfficiency > 0.3) {
                System.out.println("   🏆 TOURNAMENT RATING: Excellent time efficiency (" +
                        String.format("%.1f%%", timeEfficiency * 100) + " remaining)");
            } else if (timeEfficiency > 0.1) {
                System.out.println("   ✅ TOURNAMENT RATING: Good time efficiency (" +
                        String.format("%.1f%%", timeEfficiency * 100) + " remaining)");
            } else {
                System.out.println("   ⚠️ TOURNAMENT RATING: Tight time management (" +
                        String.format("%.1f%%", timeEfficiency * 100) + " remaining)");
            }
        }

        System.out.println("   🧠 AI Strategy: ULTIMATE TOURNAMENT (Auto-Adaptive)");
        System.out.println("   🔧 Time Manager: Advanced Tournament with Emergency Handling");
        System.out.println("   ✅ Integration: Complete with Enhanced Evaluation & Search");

        // Print final time manager stats
        timeManager.printTournamentTimeStats();
    }
}