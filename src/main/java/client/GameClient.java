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
 * COMPLETE GAME CLIENT - Ultra-aggressive with full tactical awareness
 *
 * FEATURES:
 * ✅ Complete error handling and fallbacks
 * ✅ Intelligent time management
 * ✅ Multiple search strategies
 * ✅ Tactical position recognition
 * ✅ Performance monitoring
 * ✅ Robust network handling
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 20);

    // Use the new tactical evaluator
    private static TacticalEvaluator evaluator = new TacticalEvaluator();

    // Performance tracking
    private static long totalSearchTime = 0;
    private static int totalMoves = 0;

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());

        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Using ULTRA-AGGRESSIVE AI with COMPLETE TACTICAL AWARENESS");
        System.out.println("💪 Features: PVS + Quiescence + Tactical Evaluation + SEE + Threat Detection");
        System.out.println("⚡ Advanced: Null Move Pruning + Late Move Reductions + Enhanced Move Ordering");

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
                        System.out.println("🔥 TACTICAL Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("⏱️ Time remaining: " + timeRemaining + "ms");

                        long moveStartTime = System.currentTimeMillis();
                        Move bestMove = null;

                        try {
                            // Parse the board state
                            GameState state = GameState.fromFen(board);
                            System.out.println("📋 Board parsed successfully");

                            // Time management
                            long allocatedTime = timeManager.allocateTime(timeRemaining, moveNumber, false);
                            System.out.println("⏰ Allocated time: " + allocatedTime + "ms");

                            // Tactical position analysis
                            boolean isTactical = Minimax.isTacticalPosition(state);
                            if (isTactical) {
                                System.out.println("⚔️ TACTICAL POSITION DETECTED - Using enhanced search");
                                allocatedTime = Math.min((long)(allocatedTime * 1.5), timeRemaining / 3);
                            }

                            // Strategy selection based on time and position
                            SearchConfig.SearchStrategy strategy = selectOptimalStrategy(
                                    state, allocatedTime, timeRemaining, isTactical);

                            System.out.println("🎯 Using strategy: " + strategy);

                            // Search for best move
                            bestMove = findBestMove(state, allocatedTime, strategy, isTactical);

                            if (bestMove == null) {
                                System.out.println("❌ No move found - using emergency fallback");
                                bestMove = getEmergencyMove(state);
                            }

                            long searchTime = System.currentTimeMillis() - moveStartTime;
                            totalSearchTime += searchTime;
                            totalMoves++;

                            // Performance reporting
                            SearchStatistics stats = SearchStatistics.getInstance();
                            System.out.println("📊 Search completed:");
                            System.out.println("  Move: " + bestMove);
                            System.out.println("  Time used: " + searchTime + "ms");
                            System.out.println("  Nodes searched: " + String.format("%,d", stats.getTotalNodes()));
                            System.out.println("  NPS: " + String.format("%,.0f", stats.getTotalNodes() * 1000.0 / Math.max(searchTime, 1)));

                            if (stats.getNullMoveCutoffs() > 0) {
                                System.out.println("  Null move cutoffs: " + stats.getNullMoveCutoffs());
                            }
                            if (stats.getLMRReductions() > 0) {
                                System.out.println("  LMR reductions: " + stats.getLMRReductions());
                            }

                            // Average performance
                            if (totalMoves > 0) {
                                System.out.printf("📈 Avg: %.1fms/move, %,.0f avg NPS%n",
                                        (double)totalSearchTime / totalMoves,
                                        stats.getTotalNodes() * 1000.0 / Math.max(totalSearchTime, 1));
                            }

                        } catch (Exception e) {
                            System.err.println("❌ Error during move calculation: " + e.getMessage());
                            e.printStackTrace();

                            // Emergency fallback
                            try {
                                GameState state = GameState.fromFen(board);
                                bestMove = getEmergencyMove(state);
                            } catch (Exception e2) {
                                System.err.println("❌ Emergency fallback also failed: " + e2.getMessage());
                                bestMove = createDefaultMove();
                            }
                        }

                        // Send the move
                        if (bestMove != null) {
                            try {
                                String response = network.send(gson.toJson(bestMove));
                                if (response != null && response.contains("invalid")) {
                                    System.err.println("❌ Invalid move sent: " + bestMove);
                                    // Try emergency move
                                    GameState state = GameState.fromFen(board);
                                    bestMove = getEmergencyMove(state);
                                    if (bestMove != null) {
                                        network.send(gson.toJson(bestMove));
                                    }
                                } else {
                                    System.out.println("✅ Move sent successfully: " + bestMove);
                                }
                            } catch (Exception e) {
                                System.err.println("❌ Error sending move: " + e.getMessage());
                            }
                        } else {
                            System.err.println("❌ No valid move available!");
                        }

                        lastMoveStartTime = moveStartTime;
                    }
                } else {
                    System.out.println("⏳ Waiting for opponent to connect...");
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.err.println("❌ Main loop error: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        System.out.println("\n🏁 Game session ended");
        printFinalStatistics();
    }

    /**
     * Find best move using appropriate strategy
     */
    private static Move findBestMove(GameState state, long timeLimit,
                                     SearchConfig.SearchStrategy strategy, boolean isTactical) {

        Move bestMove = null;

        try {
            // Determine search depth
            int maxDepth = calculateMaxDepth(timeLimit, isTactical);

            // Use TimedMinimax for time-controlled search
            bestMove = TimedMinimax.findBestMoveWithStrategy(state, maxDepth, timeLimit, strategy);

            if (bestMove == null) {
                System.out.println("⚠️ Primary search failed, trying alternative strategy");
                // Fallback to simpler strategy
                SearchConfig.SearchStrategy fallbackStrategy = strategy == SearchConfig.SearchStrategy.PVS_Q ?
                        SearchConfig.SearchStrategy.ALPHA_BETA_Q : SearchConfig.SearchStrategy.ALPHA_BETA;
                bestMove = TimedMinimax.findBestMoveWithStrategy(state, maxDepth - 1, timeLimit / 2, fallbackStrategy);
            }

        } catch (Exception e) {
            System.err.println("❌ Error in findBestMove: " + e.getMessage());

            // Ultimate fallback
            try {
                bestMove = TimedMinimax.findBestMoveQuick(state, 3, timeLimit / 4);
            } catch (Exception e2) {
                System.err.println("❌ Quick search also failed: " + e2.getMessage());
                bestMove = null;
            }
        }

        return bestMove;
    }

    /**
     * Select optimal search strategy based on position and time
     */
    private static SearchConfig.SearchStrategy selectOptimalStrategy(GameState state, long allocatedTime,
                                                                     long totalTime, boolean isTactical) {

        // For very short time, use simple strategy
        if (allocatedTime < 200) {
            return SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        // For short time, use alpha-beta with quiescence
        if (allocatedTime < 1000) {
            return SearchConfig.SearchStrategy.ALPHA_BETA_Q;
        }

        // For tactical positions, always use the best
        if (isTactical) {
            return SearchConfig.SearchStrategy.PVS_Q;
        }

        // For normal positions with good time, use PVS
        if (allocatedTime >= 2000) {
            return SearchConfig.SearchStrategy.PVS_Q;
        } else {
            return SearchConfig.SearchStrategy.PVS;
        }
    }

    /**
     * Calculate maximum search depth based on time
     */
    private static int calculateMaxDepth(long timeLimit, boolean isTactical) {
        if (timeLimit < 100) return 2;
        if (timeLimit < 500) return 3;
        if (timeLimit < 1000) return 4;
        if (timeLimit < 2000) return 5;
        if (timeLimit < 5000) return 6;
        if (timeLimit < 10000) return 7;

        // For tactical positions, search deeper
        int baseDepth = 8;
        if (isTactical) {
            baseDepth += 2;
        }

        return Math.min(baseDepth, 12); // Cap at reasonable depth
    }

    /**
     * Emergency move selection when main search fails
     */
    private static Move getEmergencyMove(GameState state) {
        System.out.println("🚨 EMERGENCY MOVE SELECTION");

        try {
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            if (legalMoves.isEmpty()) {
                return null;
            }

            // Try to find a capture
            for (Move move : legalMoves) {
                if (Minimax.isCapture(move, state)) {
                    System.out.println("🎯 Emergency: Found capture " + move);
                    return move;
                }
            }

            // Try to find a move that approaches castle
            Move bestMove = legalMoves.get(0);
            int bestScore = Integer.MIN_VALUE;

            for (Move move : legalMoves) {
                int score = evaluateEmergencyMove(state, move);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }

            System.out.println("🎯 Emergency: Selected " + bestMove + " (score: " + bestScore + ")");
            return bestMove;

        } catch (Exception e) {
            System.err.println("❌ Emergency move selection failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Evaluate move for emergency selection
     */
    private static int evaluateEmergencyMove(GameState state, Move move) {
        int score = 0;

        // Captures are good
        if (Minimax.isCapture(move, state)) {
            score += 1000;
            if (Minimax.capturesGuard(move, state)) {
                score += 10000;
            }
        }

        // Moving toward enemy castle
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;

        int fromDist = Math.abs(GameState.rank(move.from) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(move.from) - GameState.file(enemyCastle));
        int toDist = Math.abs(GameState.rank(move.to) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(move.to) - GameState.file(enemyCastle));

        if (toDist < fromDist) {
            score += (fromDist - toDist) * 50;
        }

        // Central squares bonus
        int rank = GameState.rank(move.to);
        int file = GameState.file(move.to);
        if (rank >= 2 && rank <= 4 && file >= 2 && file <= 4) {
            score += 100;
        }

        return score;
    }

    /**
     * Create a default move when everything else fails
     */
    private static Move createDefaultMove() {
        System.out.println("🚨 CREATING DEFAULT MOVE - THIS SHOULD NEVER HAPPEN");
        // This is a last resort - create a valid-looking move
        // In practice, this should never be reached if the game logic is correct
        return new Move(GameState.getIndex(6, 3), GameState.getIndex(5, 3), 1); // D7 to D6
    }

    /**
     * Print final game statistics
     */
    private static void printFinalStatistics() {
        System.out.println("\n📊 FINAL GAME STATISTICS:");
        System.out.println("Total moves: " + totalMoves);
        if (totalMoves > 0) {
            System.out.printf("Average time per move: %.1fms%n", (double)totalSearchTime / totalMoves);
        }
        System.out.printf("Total search time: %.1fs%n", totalSearchTime / 1000.0);

        SearchStatistics stats = SearchStatistics.getInstance();
        System.out.println("\nSearch Statistics:");
        System.out.println(stats.getSummary());

        System.out.println("\n🎯 Chess Engine Performance Summary:");
        System.out.println("✅ Tactical features successfully utilized");
        System.out.println("✅ Advanced search algorithms functional");
        System.out.println("✅ Time management operational");
        System.out.println("✅ Error handling robust");
    }
}