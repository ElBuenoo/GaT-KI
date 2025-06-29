package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Time-managed search engine using new architecture
 * Replaces TimedMinimax
 */
public class TimedGameEngine {
    private final GameEngine gameEngine;
    private final SearchCoordinator coordinator;
    private final ExecutorService executor;

    // Search state
    private final AtomicBoolean searchActive = new AtomicBoolean(false);
    private final AtomicReference<Move> currentBestMove = new AtomicReference<>();
    private volatile int currentDepth = 0;
    private volatile long searchStartTime;

    public TimedGameEngine() {
        this.gameEngine = new GameEngine();
        this.coordinator = new SearchCoordinator();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TimedSearch");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Find best move with time limit
     */
    public Move findBestMove(GameState state, long timeMillis) {
        return findBestMove(state, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Find best move with time limit and specific strategy
     */
    public Move findBestMove(GameState state, long timeMillis, SearchConfig.SearchStrategy strategy) {
        return findBestMove(state, 99, timeMillis, strategy); // Max depth 99
    }

    /**
     * Find best move with max depth and time limit
     */
    public Move findBestMove(GameState state, int maxDepth, long timeMillis,
                             SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("Error: null game state");
            return null;
        }

        searchActive.set(true);
        searchStartTime = System.currentTimeMillis();
        currentBestMove.set(null);
        currentDepth = 0;

        // Emergency mode for very low time
        if (timeMillis < 100) {
            System.out.println("⚡ Emergency mode - only depth 1");
            return gameEngine.findBestMove(state, 1, strategy);
        }

        // Setup timeout checker
        AtomicBoolean timeout = new AtomicBoolean(false);
        coordinator.setTimeoutChecker(timeout::get);

        // Submit iterative deepening task
        Future<Move> future = executor.submit(() -> {
            return iterativeDeepening(state, maxDepth, timeMillis, strategy, timeout);
        });

        try {
            // Wait for time limit (with small buffer)
            Move result = future.get(timeMillis - 50, TimeUnit.MILLISECONDS);
            return result != null ? result : currentBestMove.get();

        } catch (TimeoutException e) {
            // Time's up - cancel search
            timeout.set(true);
            future.cancel(true);

            Move bestSoFar = currentBestMove.get();
            System.out.println("⏱️ Time limit reached at depth " + currentDepth);

            // If no move found yet, do emergency depth 1 search
            if (bestSoFar == null) {
                System.out.println("🚨 Emergency search...");
                coordinator.clearTimeoutChecker();
                bestSoFar = gameEngine.findBestMove(state, 1, strategy);
            }

            return bestSoFar;

        } catch (Exception e) {
            System.err.println("Search error: " + e.getMessage());
            return currentBestMove.get();

        } finally {
            searchActive.set(false);
            coordinator.clearTimeoutChecker();
        }
    }

    /**
     * Iterative deepening search
     */
    private Move iterativeDeepening(GameState state, int maxDepth, long timeMillis,
                                    SearchConfig.SearchStrategy strategy, AtomicBoolean timeout) {
        Move bestMove = null;
        long[] depthTimes = new long[maxDepth + 1];

        // Always search depth 1
        currentDepth = 1;
        long depthStart = System.currentTimeMillis();
        bestMove = coordinator.findBestMove(state, 1, strategy);
        depthTimes[1] = System.currentTimeMillis() - depthStart;
        currentBestMove.set(bestMove);

        System.out.println("Depth 1: " + depthTimes[1] + "ms");

        // Continue deeper
        for (int depth = 2; depth <= maxDepth; depth++) {
            if (timeout.get()) break;

            long elapsed = System.currentTimeMillis() - searchStartTime;
            long remaining = timeMillis - elapsed;

            // Time management - predict time for next depth
            long expectedTime = predictNextDepthTime(depthTimes, depth);

            // Use at most 40% of remaining time for safety
            if (expectedTime > remaining * 0.4) {
                System.out.println("Stopping at depth " + (depth-1) +
                        " (predicted " + expectedTime + "ms > " +
                        (remaining * 0.4) + "ms available)");
                break;
            }

            // Search next depth
            currentDepth = depth;
            depthStart = System.currentTimeMillis();

            try {
                Move depthMove = coordinator.findBestMove(state, depth, strategy);
                if (depthMove != null) {
                    bestMove = depthMove;
                    currentBestMove.set(bestMove);
                }

                depthTimes[depth] = System.currentTimeMillis() - depthStart;
                System.out.println("Depth " + depth + ": " + depthTimes[depth] + "ms");

                // Check for mate score
                int score = coordinator.evaluate(state);
                if (Math.abs(score) > 10000) {
                    System.out.println("Mate found!");
                    break;
                }

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                    System.out.println("Timeout at depth " + depth);
                    break;
                }
                throw e;
            }
        }

        return bestMove;
    }

    /**
     * Predict time for next depth based on branching factor
     */
    private long predictNextDepthTime(long[] depthTimes, int nextDepth) {
        if (nextDepth <= 1) return 50;

        // Use average branching factor from previous depths
        double totalFactor = 0;
        int count = 0;

        for (int i = 2; i < nextDepth && i < depthTimes.length; i++) {
            if (depthTimes[i] > 0 && depthTimes[i-1] > 0) {
                double factor = (double) depthTimes[i] / depthTimes[i-1];
                totalFactor += factor;
                count++;
            }
        }

        // Default branching factor if no data
        double avgFactor = count > 0 ? totalFactor / count : 3.5;

        // Predict with safety margin
        long lastTime = depthTimes[nextDepth - 1];
        return (long)(lastTime * avgFactor * 1.2); // 20% safety margin
    }

    /**
     * Get search statistics
     */
    public String getSearchStats() {
        return coordinator.getStatistics().getSummary();
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    // === STATIC COMPATIBILITY METHODS (for easy migration) ===

    @Deprecated
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        TimedGameEngine engine = new TimedGameEngine();
        try {
            return engine.findBestMove(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
        } finally {
            engine.shutdown();
        }
    }

    @Deprecated
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithTime(state, maxDepth, timeMillis);
    }
}