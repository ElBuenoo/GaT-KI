package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.ConsolidatedSearchConfig;
import GaT.model.GameValues;
import GaT.model.TTEntry;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * TIMED MINIMAX - PHASE 1 INTEGRATION
 *
 * CHANGES:
 * ✅ Uses UnifiedStatistics instead of SearchStatistics
 * ✅ Uses FastMoveOrdering instead of MoveOrdering
 * ✅ Uses ConsolidatedSearchConfig instead of SearchConfig
 * ✅ Uses GameValues for all piece values and constants
 * ✅ All hardcoded values replaced with Phase 1 constants
 */
public class TimedMinimax {

    // === SHARED COMPONENTS WITH PHASE 1 ===
    private static final UnifiedStatistics statistics = UnifiedStatistics.getInstance();
    private static final Evaluator evaluator = Minimax.getEvaluator();
    private static final FastMoveOrdering moveOrdering = new FastMoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(ConsolidatedSearchConfig.TT_SIZE);
    private static final SearchEngine searchEngine = new SearchEngine(Minimax.getEvaluator(), moveOrdering, transpositionTable, statistics);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;
    private static ConsolidatedSearchConfig.Strategy currentStrategy = ConsolidatedSearchConfig.DEFAULT_STRATEGY;

    // === MAIN INTERFACES ===

    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, ConsolidatedSearchConfig.DEFAULT_STRATEGY);
    }

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis, ConsolidatedSearchConfig.Strategy strategy) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, strategy);
    }

    // === CORE SEARCH ===

    private static Move findBestMoveWithConfig(GameState state, int maxDepth, long timeMillis, ConsolidatedSearchConfig.Strategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = ConsolidatedSearchConfig.DEFAULT_STRATEGY;
        }

        // Validate maxDepth
        if (maxDepth > ConsolidatedSearchConfig.MAX_DEPTH) {
            System.out.printf("⚠️ maxDepth %d exceeds ConsolidatedSearchConfig.MAX_DEPTH (%d), clamping\n",
                    maxDepth, ConsolidatedSearchConfig.MAX_DEPTH);
            maxDepth = ConsolidatedSearchConfig.MAX_DEPTH;
        }

        currentStrategy = strategy;

        // Initialize search
        initializeSearchWithConfig(state, timeMillis);

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        Move bestMove = legalMoves.get(0); // Emergency fallback
        Move lastCompletedMove = bestMove;
        int bestScore = Integer.MIN_VALUE;
        int bestDepth = 0;
        long totalNodes = 0;

        System.out.println("=== TIMED SEARCH WITH PHASE 1 OPTIMIZATIONS ===");
        System.out.printf("Strategy: %s | Time: %dms | Legal moves: %d | Max depth: %d\n",
                strategy, timeMillis, legalMoves.size(), maxDepth);
        System.out.printf("Config: Emergency=%dms, Target NPS=%,d\n",
                ConsolidatedSearchConfig.EMERGENCY_TIME_MS, ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND);

        // === ITERATIVE DEEPENING ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getNodeCount();
            long qNodesBefore = statistics.getQNodeCount();

            try {
                SearchResult result = performSearchWithConfig(state, depth, legalMoves);

                if (result != null && result.move != null) {
                    lastCompletedMove = result.move;
                    bestMove = result.move;
                    bestScore = result.score;
                    bestDepth = depth;

                    long depthNodes = statistics.getNodeCount() - nodesBefore;
                    long depthQNodes = statistics.getQNodeCount() - qNodesBefore;
                    totalNodes = statistics.getTotalNodes();

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    double nps = depthTime > 0 ? (double)(depthNodes + depthQNodes) * 1000 / depthTime : 0;

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, q-nodes: %,d, nps: %.0f)\n",
                            depth, bestMove, result.score, depthTime, depthNodes, depthQNodes, nps);

                    // Early termination for checkmates
                    if (Math.abs(result.score) >= GameValues.CHECKMATE_VALUE && depth >= 3) {
                        System.out.printf("♔ Checkmate found at depth %d, terminating\n", depth);
                        break;
                    }

                    // Time management
                    if (!shouldContinueSearchWithConfig(depthTime, timeMillis, depth, totalNodes)) {
                        System.out.println("⏱ Time management: Stopping search");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed, using last good move\n", depth);
                    bestMove = lastCompletedMove;
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s, using last good move\n", depth, e.getMessage());
                bestMove = lastCompletedMove;
                break;
            }
        }

        // === FINAL STATISTICS ===
        long totalTime = System.currentTimeMillis() - startTime;
        double timeUsagePercent = (double)totalTime / timeMillis * 100;
        double npsAchieved = totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0;

        System.out.println("=== SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Score: %+d | Depth: %d | Time: %dms (%.1f%% of allocated)\n",
                bestMove, bestScore, bestDepth, totalTime, timeUsagePercent);
        System.out.printf("Total nodes: %,d | NPS: %,.0f (target: %,d)\n",
                totalNodes, npsAchieved, ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND);

        // Performance analysis
        if (npsAchieved >= ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND) {
            System.out.println("✅ Performance meets target");
        } else {
            System.out.printf("⚠️ Performance below target (%.1f%% of target)\n",
                    npsAchieved / ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND * 100);
        }

        // Show move ordering statistics
        System.out.printf("📊 Move ordering efficiency: %.1f%%\n", moveOrdering.getOrderingEfficiency() * 100);

        return bestMove;
    }

    // === TIME MANAGEMENT ===

    private static boolean shouldContinueSearchWithConfig(long lastDepthTime, long totalTimeLimit,
                                                          int currentDepth, long totalNodes) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // Always search to minimum depth
        if (currentDepth < 3 && remaining > 100) {
            return true;
        }

        // Use 60% threshold for time management
        if (remaining > totalTimeLimit * 0.6) {
            System.out.printf("  ⚡ Plenty of time left (%.1f%%), continuing to depth %d\n",
                    (double)remaining/totalTimeLimit*100, currentDepth + 1);
            return true;
        }

        // Growth factor prediction
        double growthFactor;
        if (currentDepth <= 4) {
            growthFactor = 2.8;
        } else if (currentDepth <= 6) {
            growthFactor = 3.2;
        } else if (currentDepth <= 8) {
            growthFactor = 3.8;
        } else {
            growthFactor = 4.5;
        }

        // Adjust based on node performance
        if (totalNodes < ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND / 2) {
            growthFactor *= 0.9; // Efficient search, slightly more optimistic
        } else if (totalNodes > ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND * 2) {
            growthFactor *= 1.2; // Inefficient search, more conservative
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // Use 75% of remaining time
        boolean canComplete = estimatedNextTime < remaining * 0.75;

        if (!canComplete) {
            System.out.printf("  ⏱ Next depth %d estimated %dms > 75%% of remaining %dms\n",
                    currentDepth + 1, estimatedNextTime, remaining);
        }

        return canComplete;
    }

    // === SEARCH IMPLEMENTATION ===

    private static SearchResult performSearchWithConfig(GameState state, int depth, List<Move> legalMoves) {
        // Order moves using FastMoveOrdering
        orderMovesWithConfig(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = GameValues.ALPHA_INIT;
        int beta = GameValues.BETA_INIT;

        // Set timeout checker
        searchEngine.setTimeoutChecker(() -> searchAborted ||
                System.currentTimeMillis() - startTime >= timeLimitMillis * 92 / 100);

        try {
            int moveCount = 0;
            for (Move move : legalMoves) {
                if (searchAborted) break;

                moveCount++;
                GameState copy = state.copy();
                copy.applyMove(move);

                // Search using current strategy
                int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, currentStrategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;

                    // Update alpha-beta
                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }

                    // Update history heuristics on best move
                    updateHistoryHeuristicsWithConfig(move, state, depth, score);
                }

                // Log exceptional moves
                if (Math.abs(score) >= GameValues.CHECKMATE_VALUE) {
                    System.out.printf("  ♔ Checkmate move found: %s (score: %+d)\n", move, score);
                }

                // Periodic time checks
                if (moveCount % 3 == 0 && shouldAbortSearchWithConfig()) {
                    System.out.println("  ⏱ Time limit approaching, completing current depth");
                    break;
                }
            }
        } finally {
            searchEngine.clearTimeoutChecker();
        }

        return bestMove != null ? new SearchResult(bestMove, bestScore) : null;
    }

    // === HELPER METHODS ===

    private static void initializeSearchWithConfig(GameState state, long timeMillis) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;
        searchAborted = false;

        // Reset statistics
        statistics.reset();
        statistics.startSearch();

        // Clear TT if too full
        if (transpositionTable.size() > ConsolidatedSearchConfig.TT_SIZE * 0.75) {
            transpositionTable.clear();
            System.out.printf("🔧 Cleared transposition table (size > 75%% of %,d)\n", ConsolidatedSearchConfig.TT_SIZE);
        }

        // Reset move ordering
        moveOrdering.resetForNewSearch();

        System.out.printf("🔧 Search initialized with %s\n", currentStrategy);
        System.out.printf("   Time: %dms | Emergency threshold: %dms | TT size: %,d\n",
                timeMillis, ConsolidatedSearchConfig.EMERGENCY_TIME_MS, ConsolidatedSearchConfig.TT_SIZE);
    }

    private static void orderMovesWithConfig(List<Move> moves, GameState state, int depth) {
        if (moves.size() <= 1) return;

        try {
            TTEntry entry = transpositionTable.get(state.hash());
            moveOrdering.orderMoves(moves, state, depth, entry);
        } catch (Exception e) {
            // Fallback to simple ordering
            moves.sort((a, b) -> {
                boolean aCap = GameValues.isCapture(state, a.from, a.to);
                boolean bCap = GameValues.isCapture(state, b.from, b.to);
                if (aCap && !bCap) return -1;
                if (!aCap && bCap) return 1;
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        }
    }

    private static void updateHistoryHeuristicsWithConfig(Move move, GameState state, int depth, int score) {
        // Update on good moves
        if (Math.abs(score) > GameValues.TOWER_BASE_VALUE) {
            moveOrdering.updateHistory(move, depth, state);
        }
    }

    private static boolean shouldAbortSearchWithConfig() {
        if (!searchAborted && System.currentTimeMillis() - startTime >= timeLimitMillis * 96 / 100) {
            searchAborted = true;
            System.out.println("🚨 Timeout threshold reached (96%)");
            return true;
        }
        return searchAborted;
    }

    // === RESULT CLASS ===

    private static class SearchResult {
        final Move move;
        final int score;

        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    // === PUBLIC INTERFACES ===


    /**
     * Get total nodes searched
     */
    public static long getTotalNodesSearched() {
        return statistics.getTotalNodes();
    }

    // === LEGACY COMPATIBILITY ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, ConsolidatedSearchConfig.DEFAULT_STRATEGY);
    }
}