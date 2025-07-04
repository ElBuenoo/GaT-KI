package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Time-managed search engine using new architecture
 * FIXED VERSION with better error handling
 */
public class TimedGameEngine {
    private final GameEngine gameEngine;
    private final SearchEngine searchEngine;
    private final ExecutorService executor;

    // Search state
    private final AtomicBoolean searchActive = new AtomicBoolean(false);
    private final AtomicReference<Move> currentBestMove = new AtomicReference<>();
    private final AtomicReference<Move> lastValidMove = new AtomicReference<>();
    private volatile int currentDepth = 0;
    private volatile long searchStartTime;
    private volatile boolean debugMode = false;

    public TimedGameEngine() {
        this.gameEngine = new GameEngine();
        this.searchEngine = gameEngine.getSearchEngine();

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TimedSearch");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
    }

    public TimedGameEngine(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.searchEngine = gameEngine.getSearchEngine();
        this.executor = Executors.newSingleThreadExecutor();
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
        return findBestMove(state, 99, timeMillis, strategy);
    }

    /**
     * Find best move with max depth and time limit - FIXED VERSION
     */
    public Move findBestMove(GameState state, int maxDepth, long timeMillis,
                             SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("Error: null game state");
            return null;
        }

        // Reset state
        searchActive.set(true);
        searchStartTime = System.currentTimeMillis();
        currentBestMove.set(null);
        lastValidMove.set(null);
        currentDepth = 0;

        // Emergency mode for very low time
        if (timeMillis < 100) {
            System.out.println("⚡ Emergency mode - only depth 1");
            searchActive.set(false);
            return emergencySearch(state, strategy);
        }

        // Setup timeout checker
        AtomicBoolean timeout = new AtomicBoolean(false);
        setTimeoutChecker(timeout::get);

        // Calculate adjusted time limit (leave buffer for move transmission)
        long adjustedTimeLimit = Math.max(50, timeMillis - 100);

        // Submit iterative deepening task
        Future<Move> future = executor.submit(() -> {
            try {
                return iterativeDeepening(state, maxDepth, adjustedTimeLimit, strategy, timeout);
            } catch (Exception e) {
                System.err.println("Search task error: " + e.getMessage());
                e.printStackTrace();
                return lastValidMove.get();
            }
        });

        try {
            // Wait for time limit
            Move result = future.get(adjustedTimeLimit, TimeUnit.MILLISECONDS);

            if (result == null) {
                result = lastValidMove.get();
            }

            if (result == null) {
                System.out.println("⚠️ No move from iterative deepening, using emergency search");
                result = emergencySearch(state, strategy);
            }

            return result;

        } catch (TimeoutException e) {
            // Time's up - cancel search
            timeout.set(true);
            future.cancel(true);

            Move bestSoFar = currentBestMove.get();
            if (bestSoFar == null) {
                bestSoFar = lastValidMove.get();
            }

            System.out.println("⏱️ Time limit reached at depth " + currentDepth);

            if (bestSoFar == null) {
                System.out.println("🚨 No move found, emergency search...");
                clearTimeoutChecker();
                bestSoFar = emergencySearch(state, strategy);
            }

            return bestSoFar;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Search interrupted");
            return lastValidMove.get();

        } catch (ExecutionException e) {
            System.err.println("Search execution error: " + e.getCause());
            e.getCause().printStackTrace();

            Move fallback = lastValidMove.get();
            if (fallback == null) {
                fallback = emergencySearch(state, strategy);
            }
            return fallback;

        } finally {
            searchActive.set(false);
            clearTimeoutChecker();
        }
    }

    /**
     * Iterative deepening search - FIXED VERSION
     */
    private Move iterativeDeepening(GameState state, int maxDepth, long timeMillis,
                                    SearchConfig.SearchStrategy strategy, AtomicBoolean timeout) {
        Move bestMove = null;
        long[] depthTimes = new long[maxDepth + 1];
        int consecutiveFailures = 0;
        long totalNodesSearched = 0;

        // Always search depth 1
        currentDepth = 1;
        long depthStart = System.currentTimeMillis();

        try {
            bestMove = gameEngine.findBestMove(state, 1, strategy);
            if (bestMove != null) {
                currentBestMove.set(bestMove);
                lastValidMove.set(bestMove);
            }
            depthTimes[1] = System.currentTimeMillis() - depthStart;
            totalNodesSearched = gameEngine.getStatistics().getTotalNodes();

            System.out.println("Depth 1: " + depthTimes[1] + "ms, nodes: " + totalNodesSearched);

        } catch (Exception e) {
            System.err.println("Error at depth 1: " + e.getMessage());
            return emergencySearchSimple(state);
        }

        // Continue deeper
        for (int depth = 2; depth <= maxDepth; depth++) {
            if (timeout.get()) {
                System.out.println("Timeout flag set, stopping at depth " + (depth-1));
                break;
            }

            long elapsed = System.currentTimeMillis() - searchStartTime;
            long remaining = timeMillis - elapsed;

            // Time management
            long expectedTime = predictNextDepthTime(depthTimes, depth);

            // More conservative time management
            if (expectedTime > remaining * 0.35 || remaining < 200) {
                System.out.println("Stopping at depth " + (depth-1) +
                        " (predicted " + expectedTime + "ms > " +
                        (remaining * 0.35) + "ms available)");
                break;
            }

            // Search next depth
            currentDepth = depth;
            depthStart = System.currentTimeMillis();

            try {
                // Clear statistics for this iteration
                gameEngine.resetStatistics();

                Move depthMove = gameEngine.findBestMove(state, depth, strategy);

                if (depthMove != null) {
                    bestMove = depthMove;
                    currentBestMove.set(bestMove);
                    lastValidMove.set(bestMove);
                    consecutiveFailures = 0;

                    depthTimes[depth] = System.currentTimeMillis() - depthStart;
                    long nodesThisDepth = gameEngine.getStatistics().getTotalNodes();
                    totalNodesSearched += nodesThisDepth;

                    System.out.println("Depth " + depth + ": " + depthTimes[depth] +
                            "ms, nodes: " + nodesThisDepth + ", best: " + bestMove);

                    // Check for mate score
                    int score = getLastSearchScore();
                    if (Math.abs(score) > 10000) {
                        System.out.println("Mate found! Score: " + score);
                        break;
                    }
                } else {
                    System.err.println("Null move at depth " + depth);
                    consecutiveFailures++;

                    if (consecutiveFailures >= 3) {
                        System.err.println("Too many consecutive failures, stopping search");
                        break;
                    }
                }

            } catch (RuntimeException e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

                if (errorMsg.contains("timeout") || errorMsg.contains("Timeout")) {
                    System.out.println("Timeout at depth " + depth);
                    break;
                }

                System.err.println("Error at depth " + depth + ": " + errorMsg);

                // Don't print stack trace for every depth
                if (debugMode || consecutiveFailures == 0) {
                    e.printStackTrace();
                }

                consecutiveFailures++;

                // Stop if too many errors
                if (consecutiveFailures >= 3) {
                    System.err.println("Too many errors, stopping at depth " + (depth-1));
                    break;
                }

                // Continue with last valid move
                if (lastValidMove.get() != null) {
                    depthTimes[depth] = System.currentTimeMillis() - depthStart;
                }
            }
        }

        System.out.println("Search completed. Total nodes: " + totalNodesSearched);
        return bestMove != null ? bestMove : lastValidMove.get();
    }

    /**
     * Emergency search - single depth with basic strategy
     */
    private Move emergencySearch(GameState state, SearchConfig.SearchStrategy strategy) {
        try {
            clearTimeoutChecker();
            System.out.println("🚨 Emergency search (depth 1)");

            Move move = gameEngine.findBestMove(state, 1, strategy);
            if (move != null) {
                return move;
            }

            // Fallback to simple alpha-beta
            move = gameEngine.findBestMove(state, 1, SearchConfig.SearchStrategy.ALPHA_BETA);
            if (move != null) {
                return move;
            }

        } catch (Exception e) {
            System.err.println("Emergency search failed: " + e.getMessage());
        }

        return emergencySearchSimple(state);
    }

    /**
     * Ultra-simple emergency search
     */
    private Move emergencySearchSimple(GameState state) {
        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (!moves.isEmpty()) {
                // Prefer captures
                for (Move move : moves) {
                    if (GameRules.isCapture(move, state)) {
                        System.out.println("🚑 Emergency capture: " + move);
                        return move;
                    }
                }

                // Otherwise first legal move
                System.out.println("🚑 Emergency move: " + moves.get(0));
                return moves.get(0);
            }
        } catch (Exception e) {
            System.err.println("Emergency search simple failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Predict time for next depth based on branching factor
     */
    private long predictNextDepthTime(long[] depthTimes, int nextDepth) {
        if (nextDepth <= 1) return 50;

        // Calculate average branching factor
        double totalFactor = 0;
        int count = 0;

        for (int i = 2; i < nextDepth && i < depthTimes.length; i++) {
            if (depthTimes[i] > 10 && depthTimes[i-1] > 10) {
                double factor = (double) depthTimes[i] / depthTimes[i-1];
                // Ignore outliers
                if (factor > 0.5 && factor < 10.0) {
                    totalFactor += factor;
                    count++;
                }
            }
        }

        // Conservative default
        double avgFactor = count > 0 ? totalFactor / count : 4.0;

        // Cap factor for safety
        avgFactor = Math.min(avgFactor, 6.0);

        long lastTime = depthTimes[nextDepth - 1];
        if (lastTime < 10) lastTime = 50; // Minimum prediction

        return (long)(lastTime * avgFactor * 1.3); // 30% safety margin
    }

    /**
     * Get last search score from statistics
     */
    private int getLastSearchScore() {
        try {
            String stats = gameEngine.getSearchStats();
            // Parse score from stats if available
            // For now, return 0
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Enable/disable debug mode
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    /**
     * Get search statistics
     */
    public String getSearchStats() {
        return gameEngine.getSearchStats();
    }

    /**
     * Get statistics object
     */
    public SearchStatistics getStatistics() {
        return gameEngine.getStatistics();
    }

    /**
     * Clear caches
     */
    public void clearCaches() {
        gameEngine.clearCaches();
    }

    /**
     * Reset statistics
     */
    public void resetStatistics() {
        gameEngine.resetStatistics();
    }

    /**
     * Evaluate position
     */
    public int evaluate(GameState state) {
        return gameEngine.evaluate(state);
    }

    /**
     * Check if game is over
     */
    public boolean isGameOver(GameState state) {
        return gameEngine.isGameOver(state);
    }

    /**
     * Generate legal moves
     */
    public List<Move> generateMoves(GameState state) {
        return MoveGenerator.generateAllMoves(state);
    }

    private void setTimeoutChecker(java.util.function.BooleanSupplier checker) {
        if (searchEngine != null) {
            searchEngine.setTimeoutChecker(checker);
        }
    }

    private void clearTimeoutChecker() {
        if (searchEngine != null) {
            searchEngine.clearTimeoutChecker();
        }
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        searchActive.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Force stop current search
     */
    public void stopSearch() {
        searchActive.set(false);
        executor.shutdownNow();
    }
}