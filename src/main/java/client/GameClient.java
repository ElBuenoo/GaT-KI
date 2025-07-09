package client;

import java.util.List;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.engine.TimeManager;
import GaT.engine.TimedMinimax;
import GaT.search.Minimax;
import GaT.search.QuiescenceSearch;
import GaT.search.SearchStatistics;
import GaT.evaluation.TacticalEvaluator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * FIXED GAME CLIENT - Ultra-aggressive with tactical awareness
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 20); // Adjusted estimate

    // Use the new tactical evaluator
    private static TacticalEvaluator evaluator = new TacticalEvaluator();

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Using ULTRA-AGGRESSIVE AI with TACTICAL AWARENESS");
        System.out.println("💪 Features: PVS + Quiescence + Tactical Evaluation + Aggressive Time");

        while (running) {
            try {
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("❌ Couldn't get game");
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
                        System.out.println("🔥 ULTRA-AGGRESSIVE Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board: " + board);
                        System.out.println("⏱️ Time Remaining: " + formatTime(timeRemaining));

                        lastMoveStartTime = System.currentTimeMillis();

                        // Get ultra-aggressive AI move
                        String move = getUltraAggressiveAIMove(board, player, timeRemaining);

                        long actualTimeUsed = System.currentTimeMillis() - lastMoveStartTime;

                        network.send(gson.toJson(move));
                        System.out.println("📤 Move Sent: " + move);
                        System.out.println("⏱️ Time used: " + actualTimeUsed + "ms");

                        // Show statistics
                        SearchStatistics stats = SearchStatistics.getInstance();
                        System.out.printf("📊 Nodes: %,d (regular: %,d, quiescence: %,d)%n",
                                stats.getTotalNodes(), stats.getNodeCount(), stats.getQNodeCount());

                        // Update time manager
                        timeManager.updateRemainingTime(timeRemaining - actualTimeUsed);
                        timeManager.decrementEstimatedMovesLeft();

                        System.out.println("=".repeat(60));
                    }
                }

                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("🏁 Game has ended");
                    String result = game.has("winner") ?
                            ("Winner: " + game.get("winner").getAsString()) :
                            "Game finished";
                    System.out.println("🎯 " + result);

                    long finalTimeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;
                    printGameStatistics(finalTimeRemaining);
                    running = false;
                }

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
     * Ultra-aggressive AI move calculation with all fixes
     */
    private static String getUltraAggressiveAIMove(String board, int player, long timeLeft) {
        try {
            GameState state = GameState.fromFen(board);

            // Update all components with remaining time
            timeManager.updateRemainingTime(timeLeft);
            Minimax.setRemainingTime(timeLeft);
            QuiescenceSearch.setRemainingTime(timeLeft);
            TacticalEvaluator.setRemainingTime(timeLeft);

            // Get aggressive time allocation
            long timeForMove = timeManager.calculateTimeForMove(state);

            System.out.println("🧠 ULTRA-AGGRESSIVE AI Analysis:");
            System.out.printf("   ⏰ Time allocated: %dms (%.1f%% of remaining)%n",
                    timeForMove, 100.0 * timeForMove / timeLeft);
            System.out.println("   🎯 Strategy: PVS + Quiescence (ULTIMATE)");
            System.out.println("   📊 Evaluator: TacticalEvaluator");
            System.out.println("   🎮 Phase: " + timeManager.getCurrentPhase());

            // Analyze position
            analyzePosition(state);

            long searchStartTime = System.currentTimeMillis();

            // Use PVS + Quiescence with aggressive depth
            int maxDepth = 99; // Let time management decide actual depth
            Move bestMove = TimedMinimax.findBestMoveWithStrategy(
                    state, maxDepth, timeForMove, SearchConfig.SearchStrategy.PVS_Q);

            long searchTime = System.currentTimeMillis() - searchStartTime;

            // Validate move
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (bestMove == null || !legalMoves.contains(bestMove)) {
                System.out.println("⚠️ WARNING: Invalid move! Using tactical fallback...");
                bestMove = findTacticalFallback(state, legalMoves);
            }

            // Enhanced logging
            System.out.printf("   ✅ Search completed in: %dms (%.1f%% of allocated)%n",
                    searchTime, 100.0 * searchTime / timeForMove);

            // Time efficiency analysis
            double efficiency = (double)searchTime / timeForMove;
            if (efficiency < 0.5) {
                System.out.println("   ⚡ Could have used more time");
            } else if (efficiency > 0.9) {
                System.out.println("   ⏳ Excellent time utilization");
            } else {
                System.out.println("   ✅ Good time utilization");
            }

            return bestMove.toString();

        } catch (Exception e) {
            System.err.println("❌ Error in AI: " + e.getMessage());
            e.printStackTrace();
            return getEmergencyFallback(board);
        }
    }

    /**
     * Analyze position for tactical opportunities
     */
    private static void analyzePosition(GameState state) {
        System.out.println("   🔍 Position Analysis:");

        // Check for immediate threats
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int captures = 0;
        int guardThreats = 0;

        for (Move move : moves) {
            if (isCapture(move, state)) {
                captures++;
                if (capturesEnemyGuard(move, state)) {
                    guardThreats++;
                }
            }
        }

        System.out.printf("      - Legal moves: %d%n", moves.size());
        System.out.printf("      - Captures available: %d%n", captures);
        if (guardThreats > 0) {
            System.out.printf("      - ⚠️ GUARD THREATS: %d%n", guardThreats);
        }

        // Material count
        int redMaterial = getTotalMaterial(state, true);
        int blueMaterial = getTotalMaterial(state, false);
        System.out.printf("      - Material: Red=%d, Blue=%d (diff=%+d)%n",
                redMaterial, blueMaterial, redMaterial - blueMaterial);
    }

    /**
     * Find best tactical fallback move
     */
    private static Move findTacticalFallback(GameState state, List<Move> legalMoves) {
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("No legal moves!");
        }

        System.out.println("🎯 Finding tactical fallback...");

        // Priority 1: Winning moves
        for (Move move : legalMoves) {
            if (isWinningMove(move, state)) {
                System.out.println("   💎 Found winning move: " + move);
                return move;
            }
        }

        // Priority 2: Guard captures
        for (Move move : legalMoves) {
            if (capturesEnemyGuard(move, state)) {
                System.out.println("   🎯 Found guard capture: " + move);
                return move;
            }
        }

        // Priority 3: Best captures by value
        Move bestCapture = null;
        int bestCaptureValue = 0;

        for (Move move : legalMoves) {
            if (isCapture(move, state)) {
                int value = getCaptureValue(move, state);
                if (value > bestCaptureValue) {
                    bestCaptureValue = value;
                    bestCapture = move;
                }
            }
        }

        if (bestCapture != null) {
            System.out.println("   ⚔️ Found capture: " + bestCapture + " (value=" + bestCaptureValue + ")");
            return bestCapture;
        }

        // Priority 4: Aggressive positional moves
        Move bestPositional = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : legalMoves) {
            int score = scorePositionalMove(move, state);
            if (score > bestScore) {
                bestScore = score;
                bestPositional = move;
            }
        }

        System.out.println("   📍 Using positional move: " + bestPositional + " (score=" + bestScore + ")");
        return bestPositional;
    }

    // === HELPER METHODS ===

    private static boolean isWinningMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        // Check if guard reaches enemy castle
        if (move.to == targetCastle && isGuardMove(move, state)) {
            return true;
        }

        return false;
    }

    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    private static boolean capturesEnemyGuard(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        return (enemyGuard & GameState.bit(move.to)) != 0;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 1500; // Guard
        }

        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * 100; // Tower
    }

    private static int scorePositionalMove(Move move, GameState state) {
        int score = 0;
        boolean isRed = state.redToMove;

        // Advancement bonus
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);
        if (isRed && toRank < fromRank) {
            score += (fromRank - toRank) * 50;
        } else if (!isRed && toRank > fromRank) {
            score += (toRank - fromRank) * 50;
        }

        // Central control
        int file = GameState.file(move.to);
        if (file >= 2 && file <= 4) {
            score += 30;
        }
        if (file == 3) { // D-file
            score += 20;
        }

        // Move distance (activity)
        score += move.amountMoved * 10;

        return score;
    }

    private static int getTotalMaterial(GameState state, boolean isRed) {
        int total = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            total += isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
        }
        return total;
    }

    private static String getEmergencyFallback(String board) {
        try {
            System.out.println("🚨 EMERGENCY MODE");
            GameState state = GameState.fromFen(board);
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (!legalMoves.isEmpty()) {
                Move emergencyMove = findTacticalFallback(state, legalMoves);
                return emergencyMove.toString();
            }
        } catch (Exception e) {
            System.err.println("❌ Emergency fallback error: " + e.getMessage());
        }

        return "A1-A2-1"; // Last resort
    }

    private static void printGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 GAME STATISTICS:");
        System.out.println("   🎮 Total moves: " + moveNumber);
        System.out.println("   ⏱️ Final time: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining;
            long averageTimePerMove = totalTimeUsed / moveNumber;
            double timeUtilization = 100.0 * totalTimeUsed / 180_000;

            System.out.printf("   ⚡ Average time/move: %dms%n", averageTimePerMove);
            System.out.printf("   📊 Time utilization: %.1f%%%n", timeUtilization);

            if (timeUtilization < 60) {
                System.out.println("   📈 Could use more time!");
            } else if (timeUtilization < 80) {
                System.out.println("   ✅ Good time management!");
            } else if (timeUtilization < 95) {
                System.out.println("   💪 Excellent aggressive time usage!");
            } else {
                System.out.println("   ⏰ Very close to time limit!");
            }
        }

        System.out.println("   🧠 AI Engine: Fixed TimedMinimax");
        System.out.println("   📊 Evaluator: TacticalEvaluator");
        System.out.println("   🎯 Strategy: PVS + Quiescence");
        System.out.println("   ⚡ Time: Ultra-Aggressive");
    }

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
}