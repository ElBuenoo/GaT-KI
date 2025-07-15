package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.GameConfig;
import GaT.model.GameValues;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * TIMED MINIMAX - UNIFIED SYSTEMS INTEGRATION
 */
public class TimedMinimax {

    // === SHARED COMPONENTS ===
    private static final UnifiedStatistics statistics = UnifiedStatistics.getInstance();
    private static final Evaluator evaluator = Minimax.getEvaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(GameConfig.TT_SIZE);
    private static final SearchEngine searchEngine = SearchEngine.createDefault();

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;
    private static GameConfig.Strategy currentStrategy = GameConfig.DEFAULT_STRATEGY;

    // === MAIN INTERFACES ===

    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, GameConfig.DEFAULT_STRATEGY);
    }

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis, GameConfig.Strategy strategy) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, strategy);
    }

    // === CORE SEARCH ===

    private static Move findBestMoveWithConfig(GameState state, int maxDepth, long timeMillis, GameConfig.Strategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = GameConfig.DEFAULT_STRATEGY;
        }

        // Validate maxDepth
        if (maxDepth > GameConfig.MAX_DEPTH) {
            System.out.printf("⚠️ maxDepth %d exceeds GameConfig.MAX_DEPTH (%d), clamping\n",
                    maxDepth, GameConfig.MAX_DEPTH);
            maxDepth = GameConfig.MAX_DEPTH;
        }

        currentStrategy = strategy;

        // Initialize search
        initializeSearch(state, timeMillis);

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

        System.out.println("=== TIMED SEARCH ===");
        System.out.printf("Strategy: %s | Time: %dms | Legal moves: %d | Max depth: %d\n",
                strategy, timeMillis, legalMoves.size(), maxDepth);

        // === ITERATIVE DEEPENING ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getRegularNodes();
            long qNodesBefore = statistics.getQuiescenceNodes();

            try {
                SearchResult result = performSearch(state, depth, legalMoves);

                if (result != null && result.move != null) {
                    lastCompletedMove = result.move;
                    bestMove = result.move;
                    bestScore = result.score;
                    bestDepth = depth;

                    long depthNodes = statistics.getRegularNodes() - nodesBefore;
                    long depthQNodes = statistics.getQuiescenceNodes() - qNodesBefore;
                    totalNodes = statistics.getTotalNodes();

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    double nps = depthTime > 0 ? (double)(depthNodes + depthQNodes) * 1000 / depthTime : 0;

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, q-nodes: %,d, nps: %.0f)\n",
                            depth, bestMove, result.score, depthTime, depthNodes, depthQNodes, nps);

                    // Early termination for winning positions
                    if (Math.abs(result.score) >= GameValues.WINNING_SCORE && depth >= GameConfig.MIN_SEARCH_DEPTH) {
                        System.out.printf("♔ Winning position found at depth %d, terminating\n", depth);
                        break;
                    }

                    // Time management
                    if (!shouldContinueSearch(depthTime, timeMillis, depth, totalNodes)) {
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
                totalNodes, npsAchieved, GameConfig.NODES_PER_SECOND_TARGET);

        // Performance analysis
        if (npsAchieved >= GameConfig.NODES_PER_SECOND_TARGET) {
            System.out.println("✅ Performance meets target");
        } else {
            System.out.printf("⚠️ Performance below target (%.1f%% of target)\n",
                    npsAchieved / GameConfig.NODES_PER_SECOND_TARGET * 100);
        }

        // Show move ordering statistics
        System.out.println("📊 " + moveOrdering.getStatistics());

        return bestMove;
    }

    // === TIME MANAGEMENT ===

    private static boolean shouldContinueSearch(long lastDepthTime, long totalTimeLimit,
                                                int currentDepth, long totalNodes) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // Always search to minimum depth regardless of time
        if (currentDepth < GameConfig.MIN_SEARCH_DEPTH && remaining > 100) {
            return true;
        }

        // Use time thresholds for decision making
        if (remaining > totalTimeLimit * 0.6) { // More than 60% time remaining
            System.out.printf("  ⚡ Plenty of time left (%.1f%%), continuing to depth %d\n",
                    (double)remaining/totalTimeLimit*100, currentDepth + 1);
            return true;
        }

        // Growth prediction
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
        if (totalNodes < GameConfig.NODES_PER_SECOND_TARGET / 2) {
            growthFactor *= 0.9; // Efficient search, slightly more optimistic
        } else if (totalNodes > GameConfig.NODES_PER_SECOND_TARGET * 2) {
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

    private static SearchResult performSearch(GameState state, int depth, List<Move> legalMoves) {
        // Move ordering
        orderMoves(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

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
                    updateHistoryHeuristics(move, state, depth, score);
                }

                // Log exceptional moves
                if (Math.abs(score) >= GameValues.WINNING_SCORE) {
                    System.out.printf("  🎯 Winning move found: %s (score: %+d)\n", move, score);
                }

                // Periodic time checks
                if (moveCount % 3 == 0 && shouldAbortSearch()) {
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

    private static void initializeSearch(GameState state, long timeMillis) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;
        searchAborted = false;

        // Reset statistics
        statistics.reset();
        statistics.startSearch();
        QuiescenceSearch.resetQuiescenceStats();

        // Clear TT if too large
        if (transpositionTable.size() > 1000000) {
            transpositionTable.clear();
            System.out.println("🔧 Cleared transposition table");
        }

        // Reset move ordering
        moveOrdering.reset();

        System.out.printf("🔧 Search initialized with %s\n", currentStrategy);
        System.out.printf("   Time: %dms | Emergency threshold: %dms | TT size: %,d\n",
                timeMillis, GameConfig.EMERGENCY_TIME_MS, GameConfig.TT_SIZE);
    }

    private static void orderMoves(List<Move> moves, GameState state, int depth) {
        if (moves.size() <= 1) return;

        try {
            TTEntry entry = transpositionTable.get(state.hash());
            moveOrdering.orderMoves(moves, state, entry);
        } catch (Exception e) {
            // Fallback to simple ordering
            moves.sort((a, b) -> {
                boolean aCap = isCapture(a, state);
                boolean bCap = isCapture(b, state);
                if (aCap && !bCap) return -1;
                if (!aCap && bCap) return 1;
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        }
    }

    private static void updateHistoryHeuristics(Move move, GameState state, int depth, int score) {
        // Update on good moves
        if (Math.abs(score) > GameValues.TOWER_VALUE) {
            moveOrdering.updateHistory(move, depth);
        }
    }

    private static boolean shouldAbortSearch() {
        if (!searchAborted && System.currentTimeMillis() - startTime >= timeLimitMillis * 96 / 100) {
            searchAborted = true;
            System.out.println("🚨 Timeout threshold reached (96%)");
            return true;
        }
        return searchAborted;
    }

    private static boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
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
     * Get search statistics
     */
    public static String getEnhancedStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TIMED MINIMAX STATISTICS ===\n");
        sb.append("Total nodes: ").append(statistics.getTotalNodes()).append("\n");
        sb.append("Regular nodes: ").append(statistics.getRegularNodes()).append("\n");
        sb.append("Quiescence nodes: ").append(statistics.getQuiescenceNodes()).append("\n");
        sb.append("Strategy used: ").append(currentStrategy).append("\n");
        sb.append("GameConfig parameters:\n");
        sb.append("  DEFAULT_STRATEGY: ").append(GameConfig.DEFAULT_STRATEGY).append("\n");
        sb.append("  MAX_DEPTH: ").append(GameConfig.MAX_DEPTH).append("\n");
        sb.append("  MIN_SEARCH_DEPTH: ").append(GameConfig.MIN_SEARCH_DEPTH).append("\n");
        sb.append("  NODES_PER_SECOND_TARGET: ").append(GameConfig.NODES_PER_SECOND_TARGET).append("\n");
        sb.append(moveOrdering.getStatistics()).append("\n");
        return sb.toString();
    }

    /**
     * Reset for new game
     */
    public static void resetForNewGame() {
        moveOrdering.reset();
        transpositionTable.clear();
        statistics.reset();
        currentStrategy = GameConfig.DEFAULT_STRATEGY;

        System.out.println("🔄 Reset all systems for new game");
        System.out.printf("   Strategy reset to: %s\n", GameConfig.DEFAULT_STRATEGY);
    }

    // === LEGACY COMPATIBILITY ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, GameConfig.DEFAULT_STRATEGY);
    }

    public static long getTotalNodesSearched() {
        return statistics.getTotalNodes();
    }
}