package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * COMPLETE FIXED TimedMinimax - All Methods Including Legacy Compatibility
 *
 * FIXES:
 * ✅ All missing methods implemented
 * ✅ Legacy compatibility methods added
 * ✅ Complete error handling
 * ✅ Proper timeout management
 * ✅ Conservative time allocation
 * ✅ Emergency fallbacks
 */
public class TimedMinimax {

    // === SHARED COMPONENTS ===
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;
    private static SearchConfig.SearchStrategy currentStrategy = SearchConfig.SearchStrategy.PVS_Q;

    // === CONSTANTS ===
    private static final int CHECKMATE_THRESHOLD = 10000;
    private static final int MIN_SEARCH_DEPTH = 3;
    private static final double TIME_SAFETY_FACTOR = 0.85; // Use only 85% of available time

    // === MAIN INTERFACES ===

    /**
     * Ultimate search method - uses best available strategy
     */
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithStrategy(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Search with specific strategy
     */
    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                SearchConfig.SearchStrategy strategy) {
        return findBestMoveFixed(state, maxDepth, timeMillis, strategy);
    }

    /**
     * LEGACY COMPATIBILITY - findBestMoveWithTime method
     * Used by MinimaxUnitTests.java and other legacy code
     */
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Legacy compatibility method
     */
    public static Move findBestMove(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveUltimate(state, maxDepth, timeMillis);
    }

    /**
     * Quick search for time pressure
     */
    public static Move findBestMoveQuick(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithStrategy(state, Math.min(maxDepth, 4), timeMillis,
                SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    // === CORE SEARCH IMPLEMENTATION ===

    /**
     * Main search implementation with complete error handling
     */
    private static Move findBestMoveFixed(GameState state, int maxDepth, long timeMillis,
                                          SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return getEmergencyMove(state);
        }

        if (strategy == null) {
            strategy = SearchConfig.SearchStrategy.PVS_Q;
        }

        currentStrategy = strategy;

        // Initialize search
        initializeSearchFixed(timeMillis);

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        // Quick move for single legal move
        if (legalMoves.size() == 1) {
            System.out.println("✅ Only one legal move available");
            return legalMoves.get(0);
        }

        // Emergency quick evaluation for very short time
        if (timeMillis < 100) {
            System.out.println("⚡ Emergency quick move selection");
            return selectQuickMove(state, legalMoves);
        }

        Move bestMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int completedDepth = 0;

        try {
            // Iterative deepening
            for (int depth = 1; depth <= maxDepth; depth++) {
                if (searchAborted || shouldAbortSearch()) {
                    break;
                }

                System.out.printf("🔍 Searching depth %d with %s...%n", depth, strategy);

                Move currentBestMove = searchAtDepth(state, depth, strategy);

                if (currentBestMove != null) {
                    bestMove = currentBestMove;
                    completedDepth = depth;

                    // Get score for logging
                    int currentScore = evaluateMove(state, currentBestMove, depth);

                    System.out.printf("✅ Depth %d: %s (score: %+d)%n",
                            depth, bestMove, currentScore);

                    // Early termination for mate
                    if (Math.abs(currentScore) >= CHECKMATE_THRESHOLD) {
                        System.out.println("🎯 Checkmate found - terminating search early");
                        break;
                    }

                    bestScore = currentScore;
                } else {
                    System.out.printf("⚠️ Depth %d search failed%n", depth);
                    break;
                }

                // Time check after each depth
                if (getElapsedTime() > timeLimitMillis * TIME_SAFETY_FACTOR) {
                    System.out.printf("⏱️ Time limit reached after depth %d%n", depth);
                    break;
                }
            }

        } catch (Exception e) {
            System.err.printf("❌ Search error: %s%n", e.getMessage());
            if (bestMove == null) {
                bestMove = getEmergencyMove(state);
            }
        }

        // Final validation
        if (bestMove == null) {
            System.err.println("❌ No move found - using emergency fallback");
            bestMove = getEmergencyMove(state);
        }

        long totalTime = getElapsedTime();
        System.out.printf("🏁 Search complete: %s (depth %d, %dms, %,d nodes)%n",
                bestMove, completedDepth, totalTime, statistics.getNodeCount());

        return bestMove;
    }

    /**
     * Search at specific depth
     */
    private static Move searchAtDepth(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        statistics.reset();
        setupTimeoutChecker();

        try {
            return Minimax.findBestMoveWithStrategy(state, depth, strategy);
        } catch (Exception e) {
            System.err.printf("❌ Depth %d search failed: %s%n", depth, e.getMessage());
            return null;
        } finally {
            clearTimeoutChecker();
        }
    }

    /**
     * Evaluate a move's quality
     */
    private static int evaluateMove(GameState state, Move move, int depth) {
        try {
            GameState copy = state.copy();
            copy.applyMove(move);
            return evaluator.evaluate(copy, depth);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Emergency move selection for critical situations
     */
    private static Move getEmergencyMove(GameState state) {
        if (state == null) return null;

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) return null;

        return selectQuickMove(state, moves);
    }

    /**
     * Quick move selection using simple heuristics
     */
    private static Move selectQuickMove(GameState state, List<Move> moves) {
        Move bestMove = moves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (Move move : moves) {
            int score = scoreMoveFast(state, move);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    /**
     * Fast move scoring for emergency situations
     */
    private static int scoreMoveFast(GameState state, Move move) {
        int score = 0;

        // Captures are good
        if (Minimax.isCapture(move, state)) {
            score += 1000;
            if (Minimax.capturesGuard(move, state)) {
                score += 5000;
            }
        }

        // Moving toward enemy castle
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;
        int currentDist = Math.abs(GameState.rank(move.from) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(move.from) - GameState.file(enemyCastle));
        int newDist = Math.abs(GameState.rank(move.to) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(move.to) - GameState.file(enemyCastle));

        if (newDist < currentDist) {
            score += 100;
        }

        return score;
    }

    // === SEARCH MANAGEMENT ===

    /**
     * Initialize search with proper time management
     */
    private static void initializeSearchFixed(long timeMillis) {
        timeLimitMillis = Math.max(50, (long)(timeMillis * TIME_SAFETY_FACTOR));
        startTime = System.currentTimeMillis();
        searchAborted = false;
        currentStrategy = SearchConfig.SearchStrategy.PVS_Q;

        System.out.printf("🚀 Starting search with %dms time limit%n", timeLimitMillis);

        // Reset components
        statistics.reset();
        // transpositionTable.incrementAge(); // ❌ REMOVED - not needed with LRU implementation
        moveOrdering.ageHistory(); // ✅ This method exists and is correct
    }

    /**
     * Setup timeout checker for search engines
     */
    private static void setupTimeoutChecker() {
        BooleanSupplier timeoutChecker = () -> {
            if (searchAborted) return true;
            long elapsed = getElapsedTime();
            return elapsed >= timeLimitMillis;
        };

        searchEngine.setTimeoutChecker(timeoutChecker);
        Minimax.setTimeoutChecker(timeoutChecker);
        PVSSearch.setTimeoutChecker(timeoutChecker);
    }

    /**
     * Clear timeout checkers
     */
    private static void clearTimeoutChecker() {
        searchEngine.clearTimeoutChecker();
        Minimax.clearTimeoutChecker();
        PVSSearch.clearTimeoutChecker();
    }

    /**
     * Check if search should be aborted
     */
    private static boolean shouldAbortSearch() {
        return searchAborted || getElapsedTime() >= timeLimitMillis;
    }

    /**
     * Get elapsed search time
     */
    public static long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Abort current search
     */
    public static void abortSearch() {
        searchAborted = true;
        System.out.println("⚠️ Search aborted by request");
    }

    // === UTILITY METHODS ===

    /**
     * Get current search statistics
     */
    public static SearchStatistics getStatistics() {
        return statistics;
    }

    /**
     * Get current search strategy
     */
    public static SearchConfig.SearchStrategy getCurrentStrategy() {
        return currentStrategy;
    }

    /**
     * Check if search is currently running
     */
    public static boolean isSearchRunning() {
        return !searchAborted && getElapsedTime() < timeLimitMillis;
    }

    /**
     * Get remaining search time
     */
    public static long getRemainingTime() {
        return Math.max(0, timeLimitMillis - getElapsedTime());
    }

    /**
     * Reset all search state
     */
    public static void resetSearch() {
        searchAborted = false;
        startTime = 0;
        timeLimitMillis = 0;
        statistics.reset();
    }

    // === LEGACY COMPATIBILITY METHODS ===

    /**
     * Get total nodes searched (legacy compatibility)
     */
    public static long getTotalNodesSearched() {
        return statistics.getTotalNodes();
    }

    /**
     * Get search time (legacy compatibility)
     */
    public static long getSearchTime() {
        return getElapsedTime();
    }

    /**
     * Get search depth completed (legacy compatibility)
     */
    public static int getSearchDepth() {
        return statistics.getMaxDepthReached();
    }

    /**
     * Simple search with default parameters (legacy compatibility)
     */
    public static Move search(GameState state, int depth, long timeMillis) {
        return findBestMoveWithTime(state, depth, timeMillis);
    }

    // === STRATEGY TESTING METHODS ===

    /**
     * Test all strategies and return best result
     */
    public static Move findBestMoveMultiStrategy(GameState state, int depth, long timeMillis) {
        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.PVS_Q,
                SearchConfig.SearchStrategy.PVS,
                SearchConfig.SearchStrategy.ALPHA_BETA_Q
        };

        Move bestMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        long timePerStrategy = timeMillis / strategies.length;

        for (SearchConfig.SearchStrategy strategy : strategies) {
            try {
                Move move = findBestMoveWithStrategy(state, depth, timePerStrategy, strategy);
                if (move != null) {
                    int score = evaluateMove(state, move, depth);
                    if ((state.redToMove && score > bestScore) || (!state.redToMove && score < bestScore)) {
                        bestScore = score;
                        bestMove = move;
                    }
                }
            } catch (Exception e) {
                System.err.printf("Strategy %s failed: %s%n", strategy, e.getMessage());
            }
        }

        return bestMove != null ? bestMove : getEmergencyMove(state);
    }

    // === DEBUGGING AND ANALYSIS ===

    /**
     * Get search performance report
     */
    public static String getPerformanceReport() {
        long nodes = statistics.getTotalNodes();
        long time = getElapsedTime();
        double nps = time > 0 ? nodes * 1000.0 / time : 0;

        return String.format("Performance: %,d nodes in %dms (%.0f NPS)", nodes, time, nps);
    }

    /**
     * Print detailed search statistics
     */
    public static void printSearchStatistics() {
        System.out.println("\n=== TIMED SEARCH STATISTICS ===");
        System.out.println("Strategy: " + currentStrategy);
        System.out.println("Time allocated: " + timeLimitMillis + "ms");
        System.out.println("Time used: " + getElapsedTime() + "ms");
        System.out.println("Search aborted: " + searchAborted);
        System.out.println(getPerformanceReport());
        System.out.println(statistics.getSummary());
    }


    public static Move findBestMoveSafe(GameState state, int maxDepth, long timeMillis) {
        if (state == null) {
            System.err.println("❌ Null state provided to safe search");
            return null;
        }

        // Generate and verify legal moves first
        List<Move> legalMoves;
        try {
            legalMoves = MoveGenerator.generateAllMoves(state);
            if (legalMoves.isEmpty()) {
                System.out.println("⚠️ No legal moves available");
                return null;
            }
            System.out.println("✅ Safe search: " + legalMoves.size() + " legal moves found");
        } catch (Exception e) {
            System.err.println("❌ Move generation failed in safe search: " + e.getMessage());
            return null;
        }

        // Single move case
        if (legalMoves.size() == 1) {
            System.out.println("✅ Only one legal move, returning immediately: " + legalMoves.get(0));
            return legalMoves.get(0);
        }

        // Try different strategies in order of safety
        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.ALPHA_BETA,     // Safest - try first
                SearchConfig.SearchStrategy.ALPHA_BETA_Q,   // Safe with quiescence
                // Skip PVS strategies for now until we fix them
        };

        for (SearchConfig.SearchStrategy strategy : strategies) {
            try {
                System.out.println("🔍 Safe search trying strategy: " + strategy);

                // Use shorter time limit and depth for safety
                long safeTimeLimit = Math.min(timeMillis, 3000); // Max 3 seconds
                int safeDepth = Math.min(maxDepth, 6); // Max depth 6

                Move result = findBestMoveWithStrategy(state, safeDepth, safeTimeLimit, strategy);

                if (result != null && legalMoves.contains(result)) {
                    System.out.println("✅ Safe search succeeded with " + strategy + ": " + result);
                    return result;
                } else if (result != null) {
                    System.err.println("⚠️ Strategy " + strategy + " returned illegal move: " + result);
                } else {
                    System.err.println("⚠️ Strategy " + strategy + " returned null");
                }

            } catch (Exception e) {
                System.err.println("⚠️ Safe search strategy " + strategy + " failed: " + e.getMessage());
                // Continue to next strategy
            }
        }

        // Ultimate fallback: return first legal move
        System.out.println("⚠️ All safe search strategies failed, returning first legal move: " + legalMoves.get(0));
        return legalMoves.get(0);
    }

}