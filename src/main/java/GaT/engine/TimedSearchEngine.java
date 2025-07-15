package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TIMED SEARCH ENGINE - UNIFIED SYSTEMS INTEGRATION
 */
public class TimedSearchEngine {

    // === CORE COMPONENTS ===
    private final SearchEngine searchEngine;
    private final Evaluator evaluator;
    private final TimeManager timeManager;
    private final UnifiedStatistics statistics;

    // === SEARCH STATE (THREAD-SAFE) ===
    private final AtomicBoolean searchActive = new AtomicBoolean(false);
    private volatile long searchStartTime;
    private volatile long timeLimit;
    private volatile Move lastBestMove = null;
    private volatile int lastCompletedDepth = 0;
    private volatile boolean emergencyMode = false;

    // === CONSTRUCTOR ===
    public TimedSearchEngine(Evaluator evaluator, TimeManager timeManager) {
        this.searchEngine = SearchEngine.createDefault();
        this.evaluator = evaluator;
        this.timeManager = timeManager;
        this.statistics = UnifiedStatistics.getInstance();

        System.out.println("🚀 TimedSearchEngine initialized with unified systems:");
        System.out.println("   TT_SIZE: " + GameConfig.TT_SIZE);
        System.out.println("   EMERGENCY_TIME_MS: " + GameConfig.EMERGENCY_TIME_MS);
    }

    /**
     * Main search interface using GameConfig parameters
     */
    public SearchResult findBestMove(GameState state, long timeLimitMs,
                                     GameConfig.Strategy strategy) {

        // === INITIALIZATION ===
        searchActive.set(true);
        searchStartTime = System.currentTimeMillis();
        timeLimit = timeLimitMs;
        emergencyMode = timeLimitMs < GameConfig.EMERGENCY_TIME_MS;

        statistics.reset();
        statistics.startSearch();

        // === EMERGENCY HANDLING ===
        if (emergencyMode) {
            System.out.println("🚨 EMERGENCY MODE: " + timeLimitMs + "ms (threshold: " + GameConfig.EMERGENCY_TIME_MS + "ms)");
            return handleEmergencySearch(state, strategy);
        }

        // === TIME CALCULATION ===
        long adaptiveTimeLimit;
        try {
            adaptiveTimeLimit = Math.min(timeLimitMs, timeManager.calculateTimeForMove(state));
            // Use GameConfig time factors
            adaptiveTimeLimit = Math.max(
                    (long)(timeLimitMs * GameConfig.TIME_NORMAL_FACTOR),
                    Math.min(adaptiveTimeLimit, (long)(timeLimitMs * GameConfig.TIME_CRITICAL_FACTOR))
            );
        } catch (Exception e) {
            System.err.println("⚠️ Time calculation failed, using safe default: " + e.getMessage());
            adaptiveTimeLimit = timeLimitMs / 2;
        }

        System.out.printf("🕐 Time allocated: %dms (adaptive from %dms, emergency=%s)\n",
                adaptiveTimeLimit, timeLimitMs, emergencyMode);

        return performIterativeDeepening(state, adaptiveTimeLimit, strategy);
    }

    /**
     * Iterative deepening using GameConfig parameters
     */
    private SearchResult performIterativeDeepening(GameState state, long timeLimit,
                                                   GameConfig.Strategy strategy) {

        Move bestMove = null;
        Move lastCompleteMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int bestDepth = 0;

        System.out.println("=== ITERATIVE DEEPENING SEARCH ===");
        System.out.printf("Strategy: %s | Time limit: %dms\n", strategy, timeLimit);

        // Set timeout checker
        searchEngine.setTimeoutChecker(() ->
                !searchActive.get() ||
                        System.currentTimeMillis() - searchStartTime >= timeLimit * 95 / 100);

        try {
            for (int depth = 1; depth <= GameConfig.MAX_DEPTH && searchActive.get(); depth++) {
                long depthStartTime = System.currentTimeMillis();

                try {
                    int score = searchEngine.search(state, depth,
                            Integer.MIN_VALUE, Integer.MAX_VALUE,
                            state.redToMove, strategy);

                    // Find best move at this depth
                    List<Move> moves = MoveGenerator.generateAllMoves(state);
                    Move depthBestMove = findBestMoveAtDepth(state, moves, depth, strategy);

                    if (depthBestMove != null) {
                        lastCompleteMove = depthBestMove;
                        bestMove = depthBestMove;
                        bestScore = score;
                        bestDepth = depth;
                        lastCompletedDepth = depth;

                        long depthTime = System.currentTimeMillis() - depthStartTime;
                        long totalNodes = statistics.getTotalNodes();
                        double nps = depthTime > 0 ? (double)totalNodes * 1000 / depthTime : 0;

                        System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, nps: %.0f)\n",
                                depth, bestMove, score, depthTime, totalNodes, nps);

                        // Early termination for winning positions
                        if (Math.abs(score) >= GameValues.WINNING_SCORE) {
                            System.out.println("🎯 Winning position found, terminating search");
                            break;
                        }

                        // Time management
                        long elapsed = System.currentTimeMillis() - searchStartTime;
                        if (elapsed >= timeLimit * 0.8) {
                            System.out.println("⏱️ 80% of time used, stopping search");
                            break;
                        }

                    } else {
                        System.out.printf("⚠️ Depth %d: No move found\n", depth);
                        break;
                    }

                } catch (Exception e) {
                    System.err.printf("❌ Error at depth %d: %s\n", depth, e.getMessage());
                    break;
                }
            }
        } finally {
            searchEngine.clearTimeoutChecker();
            searchActive.set(false);
            statistics.endSearch();
        }

        // Use last completed move if available
        if (lastCompleteMove != null) {
            bestMove = lastCompleteMove;
        }

        return bestMove != null ? new SearchResult(bestMove, bestScore, bestDepth) : null;
    }

    /**
     * Emergency search for very low time
     */
    private SearchResult handleEmergencySearch(GameState state, GameConfig.Strategy strategy) {
        System.out.println("🚨 EMERGENCY SEARCH: Using depth 2 only");

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        Move bestMove = moves.get(0); // Fallback
        int bestScore = Integer.MIN_VALUE;

        try {
            searchEngine.setTimeoutChecker(() ->
                    System.currentTimeMillis() - searchStartTime >= timeLimit * 90 / 100);

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                int score = searchEngine.search(copy, 1,
                        Integer.MIN_VALUE, Integer.MAX_VALUE,
                        !state.redToMove, GameConfig.Strategy.ALPHA_BETA); // Fast strategy

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
        } finally {
            searchEngine.clearTimeoutChecker();
            statistics.endSearch();
        }

        return new SearchResult(bestMove, bestScore, 2);
    }

    /**
     * Find best move at specific depth
     */
    private Move findBestMoveAtDepth(GameState state, List<Move> moves, int depth, GameConfig.Strategy strategy) {
        if (moves.isEmpty()) return null;

        Move bestMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            try {
                GameState copy = state.copy();
                copy.applyMove(move);

                int score = searchEngine.search(copy, depth - 1,
                        Integer.MIN_VALUE, Integer.MAX_VALUE,
                        !state.redToMove, strategy);

                if ((state.redToMove && score > bestScore) ||
                        (!state.redToMove && score < bestScore)) {
                    bestScore = score;
                    bestMove = move;
                }

            } catch (Exception e) {
                System.err.println("❌ Error evaluating move " + move + ": " + e.getMessage());
                continue;
            }
        }

        return bestMove;
    }

    // === RESULT CLASS ===
    public static class SearchResult {
        public final Move move;
        public final int score;
        public final int depth;

        public SearchResult(Move move, int score, int depth) {
            this.move = move;
            this.score = score;
            this.depth = depth;
        }

        @Override
        public String toString() {
            return String.format("SearchResult{move=%s, score=%+d, depth=%d}", move, score, depth);
        }
    }

    // === PUBLIC UTILITIES ===

    public void stopSearch() {
        searchActive.set(false);
    }

    public boolean isSearching() {
        return searchActive.get();
    }

    public long getElapsedTime() {
        return searchActive.get() ?
                System.currentTimeMillis() - searchStartTime : 0;
    }

    public String getSearchStatistics() {
        return statistics.getBriefSummary();
    }

    // === FACTORY METHOD ===
    public static TimedSearchEngine createDefault() {
        return new TimedSearchEngine(
                Minimax.getEvaluator(),
                new TimeManager(180000, 40) // 3 minutes, 40 moves
        );
    }
}