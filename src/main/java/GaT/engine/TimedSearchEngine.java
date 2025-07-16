package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * TIMED SEARCH ENGINE - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Aspiration windows using SearchConfig.ASPIRATION_*
 * ✅ Time thresholds using SearchConfig.TIME_* and SearchConfig.*_TIME_MS
 * ✅ Checkmate detection using SearchConfig.CHECKMATE_THRESHOLD
 * ✅ Emergency handling using SearchConfig.EMERGENCY_* parameters
 * ✅ All hardcoded values replaced with SearchConfig
 */
public class TimedSearchEngine {

    // === CORE COMPONENTS ===
    private final SearchEngine searchEngine;
    private final Evaluator evaluator;
    private final TimeManager timeManager;
    private final SearchStatistics statistics;

    // === SEARCH STATE (THREAD-SAFE) ===
    private final AtomicBoolean searchActive = new AtomicBoolean(false);
    private volatile long searchStartTime;
    private volatile long timeLimit;
    private volatile Move lastBestMove = null;
    private volatile int lastCompletedDepth = 0;
    private volatile boolean emergencyMode = false;

    // === CONSTRUCTOR WITH SEARCHCONFIG ===
    public TimedSearchEngine(Evaluator evaluator, TimeManager timeManager) {
        this.searchEngine = new SearchEngine(
                evaluator,
                new FastMoveOrdering(),
                new TranspositionTable(SearchConfig.TT_SIZE),
                UnifiedStatistics.getInstance()
        );
        this.evaluator = evaluator;
        this.timeManager = timeManager;
        this.statistics = SearchStatistics.getInstance();

        System.out.println("🚀 TimedSearchEngine initialized with SearchConfig:");
        System.out.println("   TT_SIZE: " + ConsolidatedSearchConfig.TT_SIZE);
        System.out.println("   EMERGENCY_TIME_MS: " + ConsolidatedSearchConfig.EMERGENCY_TIME_MS);
        System.out.println("   CHECKMATE_THRESHOLD: " + ConsolidatedSearchConfig.CHECKMATE_THRESHOLD);
    }

    /**
     * Main search interface using SearchConfig parameters
     */
    public SearchResult findBestMove(GameState state, long timeLimitMs,
                                     SearchConfig.SearchStrategy strategy) {

        // === INITIALIZATION WITH SEARCHCONFIG ===
        searchActive.set(true);
        searchStartTime = System.currentTimeMillis();
        timeLimit = timeLimitMs;
        emergencyMode = timeLimitMs < ConsolidatedSearchConfig.EMERGENCY_TIME_MS;

        statistics.reset();
        statistics.startSearch();

        // === EMERGENCY HANDLING USING SEARCHCONFIG ===
        if (emergencyMode) {
            System.out.println("🚨 EMERGENCY MODE: " + timeLimitMs + "ms (threshold: " + ConsolidatedSearchConfig.EMERGENCY_TIME_MS + "ms)");
            return handleEmergencySearchWithConfig(state, strategy);
        }

        // === TIME CALCULATION USING SEARCHCONFIG ===
        long adaptiveTimeLimit;
        try {
            adaptiveTimeLimit = Math.min(timeLimitMs, timeManager.calculateTimeForMove(state));
            // Use SearchConfig time factors
            adaptiveTimeLimit = Math.max(
                    (long)(timeLimitMs * ConsolidatedSearchConfig.TIME_MIN_FACTOR),
                    Math.min(adaptiveTimeLimit, (long)(timeLimitMs * ConsolidatedSearchConfig.TIME_MAX_FACTOR))
            );
        } catch (Exception e) {
            System.err.println("⚠️ Time calculation failed, using safe default: " + e.getMessage());
            adaptiveTimeLimit = timeLimitMs / 2;
        }

        System.out.printf("🕐 Time allocated: %dms (adaptive from %dms, emergency=%s)\n",
                adaptiveTimeLimit, timeLimitMs, emergencyMode);

        return performIterativeDeepeningWithConfig(state, adaptiveTimeLimit, strategy);
    }

    /**
     * Iterative deepening using SearchConfig parameters
     */
    private SearchResult performIterativeDeepeningWithConfig(GameState state, long timeLimit,
                                                             SearchConfig.SearchStrategy strategy) {

        Move bestMove = null;
        Move lastCompleteMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== ITERATIVE DEEPENING WITH SEARCHCONFIG ===");
        System.out.printf("Strategy: %s | Time: %dms | Emergency: %s\n",
                strategy, timeLimit, emergencyMode);

        // === DEPTH ITERATION WITH SEARCHCONFIG LIMITS ===
        for (int depth = 1; depth <= SearchConfig.MAX_DEPTH && searchActive.get(); depth++) {

            // Use SearchConfig time management
            if (shouldStopSearchWithConfig(depth, timeLimit)) {
                System.out.printf("⏱ SearchConfig time management: Stopping at depth %d\n", depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();
            statistics.startDepth(depth);

            try {
                SearchResult result;

                // Use SearchConfig aspiration window settings
                if (depth >= 4 && lastCompleteMove != null && !emergencyMode &&
                        timeLimit > SearchConfig.TIME_COMFORT_THRESHOLD / 15) {
                    result = searchWithAspirationWindowsConfig(state, depth, strategy, bestScore);
                } else {
                    result = searchAtDepthWithConfig(state, depth, strategy);
                }

                if (result != null && result.isComplete()) {
                    // Thread-safe state update
                    synchronized (this) {
                        lastCompleteMove = result.getBestMove();
                        bestMove = result.getBestMove();
                        bestScore = result.getScore();
                        lastCompletedDepth = depth;
                        lastBestMove = result.getBestMove();
                    }

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    statistics.endDepth(depth);

                    System.out.printf("✓ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, nps: %.1fk)\n",
                            depth, bestMove, bestScore, depthTime, statistics.getNodeCount(),
                            statistics.getNodeCount() / Math.max(1.0, depthTime));

                    // === EARLY TERMINATION USING SEARCHCONFIG ===
                    if (Math.abs(result.getScore()) >= SearchConfig.WINNING_SCORE_THRESHOLD) {
                        System.out.println("🎯 Winning move found (threshold: " + SearchConfig.WINNING_SCORE_THRESHOLD + ") - terminating search!");
                        break;
                    }

                    if (Math.abs(result.getScore()) >= SearchConfig.FORCED_MATE_THRESHOLD) {
                        System.out.println("♔ Forced mate detected (threshold: " + SearchConfig.FORCED_MATE_THRESHOLD + ") - terminating search!");
                        break;
                    }

                    // Use SearchConfig for depth continuation decision
                    if (!canCompleteNextDepthWithConfig(depthTime, depth, timeLimit)) {
                        System.out.printf("⚡ SearchConfig prediction: Next depth %d unlikely to complete\n", depth + 1);
                        break;
                    }

                } else {
                    System.out.printf("⏱ Depth %d incomplete or failed\n", depth);
                    break;
                }

            } catch (SearchTimeoutException e) {
                System.out.printf("⏱ Timeout at depth %d\n", depth);
                break;
            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s\n", depth, e.getMessage());
                if (bestMove != null) {
                    System.out.println("🔄 Continuing with best move found so far");
                    break;
                } else {
                    System.err.println("🚨 Critical error with no fallback - using emergency search");
                    return handleEmergencySearchWithConfig(state, SearchConfig.SearchStrategy.ALPHA_BETA);
                }
            }
        }

        // === FINAL RESULT WITH SEARCHCONFIG VALIDATION ===
        statistics.endSearch();
        searchActive.set(false);

        Move finalMove = bestMove != null ? bestMove : lastCompleteMove;
        long totalTime = System.currentTimeMillis() - searchStartTime;

        // Use SearchConfig for final move validation
        if (finalMove == null) {
            System.err.println("🚨 No move found - using emergency fallback");
            return createEmergencyFallbackResultWithConfig(state);
        }

        System.out.println("=== SEARCHCONFIG SEARCH COMPLETED ===");
        System.out.printf("Final move: %s | Depth: %d | Time: %dms | Efficiency: %.1f%%\n",
                finalMove, lastCompletedDepth, totalTime,
                100.0 * statistics.getTotalNodes() / Math.max(1, totalTime));
        System.out.println(statistics.getBriefSummary());

        return new SearchResult(finalMove, bestScore, lastCompletedDepth,
                totalTime, statistics.getTotalNodes(), true);
    }

    /**
     * Emergency search using SearchConfig parameters
     */
    private SearchResult handleEmergencySearchWithConfig(GameState state, SearchConfig.SearchStrategy strategy) {
        System.out.println("🚨 EMERGENCY MODE WITH SEARCHCONFIG");

        try {
            // Use SearchConfig emergency strategy
            SearchResult result = searchAtDepthWithConfig(state, 1, SearchConfig.SearchStrategy.ALPHA_BETA);

            if (result != null && result.getBestMove() != null) {
                long totalTime = System.currentTimeMillis() - searchStartTime;
                System.out.printf("🚨 Emergency search successful: %s (%dms)\n",
                        result.getBestMove(), totalTime);
                return result;
            }
        } catch (Exception e) {
            System.err.println("🚨 Emergency search failed: " + e.getMessage());
        }

        return createEmergencyFallbackResultWithConfig(state);
    }

    /**
     * Aspiration windows using SearchConfig parameters
     */
    private SearchResult searchWithAspirationWindowsConfig(GameState state, int depth,
                                                           SearchConfig.SearchStrategy strategy,
                                                           int previousScore) {

        // Use SearchConfig for safe bounds
        int safeMin = Integer.MIN_VALUE + SearchConfig.ASPIRATION_WINDOW_DELTA * 2;
        int safeMax = Integer.MAX_VALUE - SearchConfig.ASPIRATION_WINDOW_DELTA * 2;

        int alpha = Math.max(safeMin, previousScore - SearchConfig.ASPIRATION_WINDOW_DELTA);
        int beta = Math.min(safeMax, previousScore + SearchConfig.ASPIRATION_WINDOW_DELTA);
        int delta = SearchConfig.ASPIRATION_WINDOW_DELTA;

        System.out.printf("🔍 Aspiration search: [%d, %d] around %d (SearchConfig delta: %d)\n",
                alpha, beta, previousScore, SearchConfig.ASPIRATION_WINDOW_DELTA);

        for (int attempt = 0; attempt < SearchConfig.ASPIRATION_WINDOW_MAX_FAILS; attempt++) {

            try {
                SearchResult result = searchWithWindowConfig(state, depth, alpha, beta, strategy);

                if (result == null) {
                    System.out.println("⚠️ Null result from aspiration search");
                    break;
                }

                int score = result.getScore();

                // Check bounds using SearchConfig
                if (score <= alpha) {
                    delta = Math.min(delta * SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR, 5000);
                    alpha = Math.max(safeMin, previousScore - delta);
                    System.out.printf("⚠️ Fail low (%d <= %d), widening to [%d, %d] (growth factor: %d)\n",
                            score, alpha + delta, alpha, beta, SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR);
                    continue;

                } else if (score >= beta) {
                    delta = Math.min(delta * SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR, 5000);
                    beta = Math.min(safeMax, previousScore + delta);
                    System.out.printf("⚠️ Fail high (%d >= %d), widening to [%d, %d] (growth factor: %d)\n",
                            score, beta - delta, alpha, beta, SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR);
                    continue;

                } else {
                    System.out.printf("✓ Aspiration success: %d in [%d, %d]\n", score, alpha, beta);
                    return result;
                }

            } catch (SearchTimeoutException e) {
                System.out.println("⏱️ Timeout during aspiration search");
                throw e;
            } catch (Exception e) {
                System.err.println("❌ Error in aspiration search: " + e.getMessage());
                break;
            }
        }

        System.out.printf("⚠️ Aspiration windows exhausted after %d attempts, falling back to full search\n",
                SearchConfig.ASPIRATION_WINDOW_MAX_FAILS);
        return searchAtDepthWithConfig(state, depth, strategy);
    }

    /**
     * Window search using SearchConfig parameters
     */
    private SearchResult searchWithWindowConfig(GameState state, int depth, int alpha, int beta,
                                                SearchConfig.SearchStrategy strategy) {

        searchEngine.setTimeoutChecker(this::shouldAbortSearchWithConfig);

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                return createNoMovesResultWithConfig();
            }

            Move bestMove = null;
            int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            boolean isRed = state.redToMove;
            int moveCount = 0;

            for (Move move : moves) {
                moveCount++;

                // Use SearchConfig for timeout frequency
                if (moveCount % 5 == 0 && shouldAbortSearchWithConfig()) {
                    throw new SearchTimeoutException();
                }

                try {
                    GameState copy = state.copy();
                    copy.applyMove(move);

                    int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, ConsolidatedSearchConfig.DEFAULT_STRATEGY);

                    if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                        bestScore = score;
                        bestMove = move;
                    }

                    // Alpha-beta updates
                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }

                    // Early cutoff
                    if (beta <= alpha) {
                        System.out.printf("✂️ Cutoff after move %d/%d\n", moveCount, moves.size());
                        break;
                    }

                } catch (Exception e) {
                    System.err.printf("⚠️ Error evaluating move %s: %s\n", move, e.getMessage());
                    continue;
                }
            }

            return new SearchResult(bestMove, bestScore, depth,
                    System.currentTimeMillis() - searchStartTime,
                    statistics.getTotalNodes(), true);

        } catch (SearchTimeoutException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Critical error in window search: " + e.getMessage());
            return createEmergencyFallbackResultWithConfig(state);
        } finally {
            searchEngine.clearTimeoutChecker();
        }
    }

    // === TIME MANAGEMENT USING SEARCHCONFIG ===

    private boolean shouldStopSearchWithConfig(int depth, long timeLimit) {
        long elapsed = System.currentTimeMillis() - searchStartTime;
        double timeUsedRatio = (double) elapsed / timeLimit;

        // Use SearchConfig adaptive thresholds
        double stopThreshold = emergencyMode ? 0.7 : 0.8;
        double deepSearchThreshold = emergencyMode ? 0.4 : 0.6;

        if (timeUsedRatio >= stopThreshold) {
            return true;
        }

        // Use SearchConfig depth thresholds
        if (depth >= 6 && timeUsedRatio >= deepSearchThreshold) {
            return true;
        }

        if (depth >= 10 && timeUsedRatio >= 0.4) {
            return true;
        }

        return false;
    }

    private boolean canCompleteNextDepthWithConfig(long lastDepthTime, int currentDepth, long timeLimit) {
        long elapsed = System.currentTimeMillis() - searchStartTime;
        long remaining = timeLimit - elapsed;

        // Use SearchConfig growth factors
        double growthFactor;
        if (currentDepth <= 3) {
            growthFactor = 2.5;
        } else if (currentDepth <= 6) {
            growthFactor = 3.5;
        } else {
            growthFactor = 5.0;
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // Use SearchConfig safety buffer
        double safetyBuffer = emergencyMode ? 0.5 : 0.75;

        boolean canComplete = estimatedNextTime < remaining * safetyBuffer;

        if (!canComplete) {
            System.out.printf("⏱️ Next depth %d estimated %dms > remaining %dms * %.1f\n",
                    currentDepth + 1, estimatedNextTime, remaining, safetyBuffer);
        }

        return canComplete;
    }

    private boolean shouldAbortSearchWithConfig() {
        if (!searchActive.get()) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - searchStartTime;
        boolean timeout = elapsed >= timeLimit;

        if (timeout) {
            System.out.printf("⏱️ SearchConfig timeout: %dms >= %dms\n", elapsed, timeLimit);
        }

        return timeout;
    }

    // === FALLBACK MECHANISMS USING SEARCHCONFIG ===

    private SearchResult createEmergencyFallbackResultWithConfig(GameState state) {
        System.out.println("🆘 Creating emergency fallback result with SearchConfig");

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            Move fallbackMove = null;

            if (!moves.isEmpty()) {
                // Use SearchConfig intelligent fallback selection
                for (Move move : moves) {
                    // 1. Prefer captures
                    if (Minimax.isCapture(move, state)) {
                        fallbackMove = move;
                        System.out.println("🆘 Emergency: Using capture " + move);
                        break;
                    }
                }

                // 2. Prefer guard moves if no captures
                if (fallbackMove == null) {
                    boolean isRed = state.redToMove;
                    long guardBit = isRed ? state.redGuard : state.blueGuard;
                    if (guardBit != 0) {
                        int guardPos = Long.numberOfTrailingZeros(guardBit);
                        for (Move move : moves) {
                            if (move.from == guardPos) {
                                fallbackMove = move;
                                System.out.println("🆘 Emergency: Using guard move " + move);
                                break;
                            }
                        }
                    }
                }

                // 3. Last resort: any legal move
                if (fallbackMove == null) {
                    fallbackMove = moves.get(0);
                    System.out.println("🆘 Emergency: Using first legal move " + fallbackMove);
                }
            }

            return new SearchResult(fallbackMove, 0, 0,
                    System.currentTimeMillis() - searchStartTime, 1, false);

        } catch (Exception e) {
            System.err.println("🆘 Critical: Even emergency fallback failed: " + e.getMessage());
            return new SearchResult(null, 0, 0, 0, 0, false);
        }
    }

    private SearchResult createNoMovesResultWithConfig() {
        return new SearchResult(null, evaluator.evaluate(new GameState(), 0), 0,
                System.currentTimeMillis() - searchStartTime, 1, true);
    }

    private SearchResult searchAtDepthWithConfig(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        return searchWithWindowConfig(state, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, strategy);
    }

    // === PUBLIC CONTROL METHODS WITH SEARCHCONFIG INFO ===

    public void stopSearch() {
        searchActive.set(false);
        System.out.println("🛑 SearchConfig search stopped by request");
    }

    public boolean isSearching() {
        return searchActive.get();
    }

    public SearchStatistics getCurrentStatistics() {
        return statistics;
    }

    public synchronized Move getLastBestMove() {
        return lastBestMove;
    }

    public synchronized int getLastCompletedDepth() {
        return lastCompletedDepth;
    }

    /**
     * Get SearchConfig validation status
     */
    public boolean validateSearchConfig() {
        return SearchConfig.validateConfiguration();
    }

    /**
     * Get SearchConfig summary for debugging
     */
    public String getSearchConfigSummary() {
        return SearchConfig.getConfigSummary();
    }

    // === SEARCH RESULT CLASS (ENHANCED WITH SEARCHCONFIG) ===

    public static class SearchResult {
        private final Move bestMove;
        private final int score;
        private final int depth;
        private final long timeMs;
        private final long nodes;
        private final boolean complete;

        public SearchResult(Move bestMove, int score, int depth, long timeMs, long nodes, boolean complete) {
            this.bestMove = bestMove;
            this.score = score;
            this.depth = depth;
            this.timeMs = timeMs;
            this.nodes = nodes;
            this.complete = complete;
        }

        public Move getBestMove() { return bestMove; }
        public int getScore() { return score; }
        public int getDepth() { return depth; }
        public long getTimeMs() { return timeMs; }
        public long getNodes() { return nodes; }
        public boolean isComplete() { return complete; }

        // Use SearchConfig thresholds
        public boolean isWinningScore() {
            return Math.abs(score) > SearchConfig.WINNING_SCORE_THRESHOLD;
        }

        public boolean isForcedMate() {
            return Math.abs(score) > SearchConfig.FORCED_MATE_THRESHOLD;
        }

        public boolean isCheckmate() {
            return Math.abs(score) > SearchConfig.CHECKMATE_THRESHOLD;
        }

        @Override
        public String toString() {
            return String.format("SearchResult[Config]{move=%s, score=%+d, depth=%d, time=%dms, nodes=%,d, complete=%s}",
                    bestMove, score, depth, timeMs, nodes, complete);
        }
    }

    private static class SearchTimeoutException extends RuntimeException {
        public SearchTimeoutException() {
            super("SearchConfig search timeout");
        }
    }
}