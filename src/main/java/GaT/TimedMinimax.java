package GaT;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.List;

/**
 * TOURNAMENT-ENHANCED TimedMinimax with intelligent strategy selection
 *
 * Tournament Features:
 * - Intelligent strategy selection based on time and complexity
 * - Enhanced time management with adaptive thresholds
 * - Tournament-optimized defaults and parameters
 * - Comprehensive statistics and monitoring
 * - Auto-scaling search depth and pruning
 */
public class TimedMinimax {

    private static long timeLimitMillis;
    private static long startTime;

    // === TOURNAMENT-OPTIMIZED DEFAULTS ===
    private static final Minimax.SearchStrategy DEFAULT_TOURNAMENT_STRATEGY = Minimax.SearchStrategy.PVS_Q;
    private static final int MIN_DEPTH_FOR_TOURNAMENT = 4;  // Minimum depth für Tournament features
    private static final long TOURNAMENT_TIME_THRESHOLD = 1000; // Min Zeit für Tournament strategy

    /**
     * ULTIMATE TOURNAMENT AI - Best possible search with all enhancements
     */
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // Enhanced setup for ultimate performance
        PVSSearch.setTimeoutChecker(() -> timedOut());
        Minimax.resetKillerMoves();
        QuiescenceSearch.setRemainingTime(timeMillis);
        QuiescenceSearch.resetQuiescenceStats();
        Minimax.setRemainingTime(timeMillis); // CRITICAL: Sync time with Minimax

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== ULTIMATE TOURNAMENT AI (Enhanced PVS + Quiescence + All Optimizations) ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                // ENHANCED: Use tournament strategy with intelligent selection
                Minimax.SearchStrategy strategy = selectOptimalStrategy(state, depth, timeMillis);

                Move candidate = Minimax.findBestMoveWithStrategy(state, depth, strategy);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ ULTIMATE Depth " + depth + " (" + strategy + ") completed in " +
                            depthTime + "ms. Best: " + candidate);

                    // Enhanced winning move detection
                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 ULTIMATE AI found winning move at depth " + depth);
                        break;
                    }
                }

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                    System.out.println("⏱ ULTIMATE Timeout at depth " + depth);
                } else {
                    System.out.println("❌ ULTIMATE Error at depth " + depth + ": " + e.getMessage());
                }
                break;
            }

            // Enhanced time management
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;

            // More aggressive time allocation for tournament
            double timeUsageRate = (double) elapsed / timeMillis;
            if (timeUsageRate > 0.75) { // Stop if used 75% of time
                System.out.println("⚡ ULTIMATE AI stopping early: used " +
                        String.format("%.1f%%", timeUsageRate * 100) + " of time");
                break;
            }
        }

        // Enhanced final statistics
        printUltimateStatistics();

        System.out.println("=== ULTIMATE TOURNAMENT AI completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * TOURNAMENT-OPTIMIZED strategy selection based on time and complexity
     */
    private static Minimax.SearchStrategy selectOptimalStrategy(GameState state, int depth, long timeMillis) {
        // Time-based strategy selection for optimal performance

        if (timeMillis < TOURNAMENT_TIME_THRESHOLD) {
            // Very low time - use fastest strategy
            return Minimax.SearchStrategy.ALPHA_BETA;
        }

        if (depth < MIN_DEPTH_FOR_TOURNAMENT) {
            // Shallow search - simple strategy sufficient
            return Minimax.SearchStrategy.ALPHA_BETA_Q;
        }

        // Check position complexity
        int complexity = evaluatePositionComplexity(state);

        if (complexity > 20) {
            // Complex tactical position - use full power
            return Minimax.SearchStrategy.PVS_Q;
        } else if (complexity > 10) {
            // Moderate complexity - PVS without quiescence
            return Minimax.SearchStrategy.PVS;
        } else {
            // Simple position - alpha-beta with quiescence sufficient
            return Minimax.SearchStrategy.ALPHA_BETA_Q;
        }
    }

    /**
     * ENHANCED position complexity evaluation for strategy selection
     */
    private static int evaluatePositionComplexity(GameState state) {
        int complexity = 0;

        // Base complexity from move count
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        complexity += moves.size() / 3; // Normalize

        // Material complexity
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalMaterial > 12) complexity += 5;      // Opening complexity
        if (totalMaterial <= 6) complexity += 10;     // Endgame complexity

        // Tactical complexity
        List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);
        complexity += tacticalMoves.size() * 2; // Heavy weight for tactics

        // Guard danger complexity
        if (Minimax.isGuardInDangerImproved(state, true) ||
                Minimax.isGuardInDangerImproved(state, false)) {
            complexity += 15; // High complexity for guard danger
        }

        // Position evaluation spread (indicator of tactical richness)
        int eval = Minimax.evaluate(state, 0);
        if (Math.abs(eval) > 500) {
            complexity += 8; // Unbalanced positions are complex
        }

        return complexity;
    }

    /**
     * ENHANCED main timed search - automatically selects best approach
     */
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        // Enhanced version that automatically selects best approach
        if (timeMillis > 5000) {
            // Enough time for ultimate search
            return findBestMoveUltimate(state, maxDepth, timeMillis);
        } else {
            // Time pressure - use enhanced but faster approach
            return findBestMoveWithTimeAndQuiescence(state, maxDepth, timeMillis);
        }
    }

    /**
     * Enhanced timed search with quiescence
     */
    public static Move findBestMoveWithTimeAndQuiescence(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        Minimax.resetKillerMoves();
        QuiescenceSearch.resetQuiescenceStats();
        QuiescenceSearch.setRemainingTime(timeMillis);
        Minimax.setRemainingTime(timeMillis);

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== TOURNAMENT Iterative Deepening with Quiescence ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                Move candidate = searchDepthWithQuiescence(state, depth);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ TOURNAMENT Depth " + depth + " completed in " + depthTime + "ms. Best: " + candidate);

                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 TOURNAMENT winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ TOURNAMENT Timeout at depth " + depth);
                break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                System.out.println("⚡ TOURNAMENT stopping early to save time. Remaining: " + remaining + "ms");
                break;
            }
        }

        // Print quiescence statistics
        if (QuiescenceSearch.qNodes > 0) {
            System.out.println("TOURNAMENT Q-nodes used: " + QuiescenceSearch.qNodes);
            if (QuiescenceSearch.qNodes > 0) {
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("TOURNAMENT Stand-pat rate: " + String.format("%.1f%%", standPatRate));
            }
        }

        System.out.println("=== TOURNAMENT Search with Quiescence completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * ENHANCED strategy-based search with tournament optimizations
     */
    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                Minimax.SearchStrategy strategy) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // Enhanced setup based on strategy
        if (strategy == Minimax.SearchStrategy.PVS || strategy == Minimax.SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(() -> timedOut());
        }

        Minimax.resetKillerMoves();
        Minimax.setRemainingTime(timeMillis); // CRITICAL sync

        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(timeMillis);
            QuiescenceSearch.resetQuiescenceStats();
        }

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== TOURNAMENT " + strategy + " with Enhanced Iterative Deepening ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                // Use the enhanced strategy interface from Minimax
                Move candidate = Minimax.findBestMoveWithStrategy(state, depth, strategy);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ TOURNAMENT Depth " + depth + " completed in " +
                            depthTime + "ms. Best: " + candidate);

                    // Enhanced winning move detection
                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 TOURNAMENT winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                    System.out.println("⏱ TOURNAMENT Timeout at depth " + depth);
                } else {
                    System.out.println("❌ TOURNAMENT Error at depth " + depth + ": " + e.getMessage());
                }
                break;
            }

            // Enhanced time management with adaptive thresholds
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;

            // Adaptive stopping based on strategy
            double stopThreshold = getStopThreshold(strategy);
            if (elapsed > timeMillis * stopThreshold) {
                System.out.println("⚡ TOURNAMENT stopping early: used " +
                        String.format("%.1f%%", 100.0 * elapsed / timeMillis) + " of time");
                break;
            }
        }

        // Print strategy-specific statistics
        printStrategyStatistics(strategy);

        System.out.println("=== TOURNAMENT " + strategy + " completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * NEW: Ultimate tournament method - automatically selects best approach
     */
    public static Move findBestMoveTournament(GameState state, int maxDepth, long timeMillis) {
        // Ultimate tournament method - automatically selects best approach

        // Time-based strategy selection
        if (timeMillis > 8000) {
            // Plenty of time - use full ultimate search
            return findBestMoveUltimate(state, maxDepth, timeMillis);
        } else if (timeMillis > 3000) {
            // Moderate time - use PVS with quiescence
            return findBestMoveWithStrategy(state, maxDepth, timeMillis, Minimax.SearchStrategy.PVS_Q);
        } else if (timeMillis > 1000) {
            // Time pressure - use alpha-beta with quiescence
            return findBestMoveWithStrategy(state, maxDepth, timeMillis, Minimax.SearchStrategy.ALPHA_BETA_Q);
        } else {
            // Extreme time pressure - use fastest strategy
            return findBestMoveWithStrategy(state, maxDepth, timeMillis, Minimax.SearchStrategy.ALPHA_BETA);
        }
    }

    /**
     * Enhanced PVS search (for compatibility)
     */
    public static Move findBestMoveWithPVS(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithStrategy(state, maxDepth, timeMillis, Minimax.SearchStrategy.PVS);
    }

    // === ENHANCED HELPER METHODS ===

    /**
     * Search depth with quiescence
     */
    private static Move searchDepthWithQuiescence(GameState state, int depth) throws TimeoutException {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);

        orderMovesWithAdvancedHeuristics(moves, state, depth, entry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            GameState copy = state.copy();
            copy.applyMove(move);

            try {
                // Use minimax with quiescence instead of regular minimax
                int score = minimaxWithQuiescenceAndTimeout(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }
            } catch (RuntimeException e) {
                if (e.getMessage().equals("Timeout")) {
                    throw new TimeoutException();
                }
                throw e;
            }
        }

        return bestMove;
    }

    /**
     * Minimax with quiescence and timeout support
     */
    private static int minimaxWithQuiescenceAndTimeout(GameState state, int depth, int alpha, int beta,
                                                       boolean maximizingPlayer) {
        if (timedOut()) {
            throw new RuntimeException("Timeout");
        }

        // Check transposition table first
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // Use quiescence search when depth <= 0
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // Regular alpha-beta search
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Minimax.orderMovesTournament(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = minimaxWithQuiescenceAndTimeout(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            // Store in transposition table
            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeInTranspositionTable(hash, maxEval, depth, flag, bestMove);

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = minimaxWithQuiescenceAndTimeout(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            // Store in transposition table
            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeInTranspositionTable(hash, minEval, depth, flag, bestMove);

            return minEval;
        }
    }

    /**
     * Enhanced timeout check with safety margin
     */
    private static boolean timedOut() {
        // Enhanced timeout check with safety margin
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - startTime;

        // Add small safety margin (50ms) to prevent time forfeit
        return elapsed >= (timeLimitMillis - 50);
    }

    /**
     * Get strategy-specific stopping threshold
     */
    private static double getStopThreshold(Minimax.SearchStrategy strategy) {
        // Different stopping thresholds for different strategies
        switch (strategy) {
            case PVS_Q:
                return 0.80; // Most thorough strategy - use more time
            case PVS:
                return 0.75;
            case ALPHA_BETA_Q:
                return 0.70;
            case ALPHA_BETA:
                return 0.65; // Fastest strategy - stop earlier
            default:
                return 0.70;
        }
    }

    /**
     * Enhanced statistics for ultimate search
     */
    private static void printUltimateStatistics() {
        // Print comprehensive tournament statistics
        if (QuiescenceSearch.qNodes > 0) {
            System.out.println("🔍 ULTIMATE Q-Search Statistics:");
            System.out.println("  Q-nodes: " + QuiescenceSearch.qNodes);

            double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
            System.out.println("  Stand-pat rate: " + String.format("%.1f%%", standPatRate));

            if (QuiescenceSearch.deltaPruningCutoffs > 0) {
                double deltaPruningRate = (100.0 * QuiescenceSearch.deltaPruningCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("  Delta pruning: " + String.format("%.1f%%", deltaPruningRate));
            }
        }

        if (Minimax.counter > 0) {
            System.out.println("🚀 ULTIMATE Pruning Efficiency:");
            long totalPruning = Minimax.reverseFutilityCutoffs + Minimax.nullMoveCutoffs + Minimax.futilityCutoffs;
            if (totalPruning > 0) {
                double pruningRate = 100.0 * totalPruning / (Minimax.counter + totalPruning);
                System.out.println("  Total pruning: " + String.format("%.1f%%", pruningRate));
            }
        }
    }

    /**
     * Strategy-specific statistics
     */
    private static void printStrategyStatistics(Minimax.SearchStrategy strategy) {
        System.out.println("📊 TOURNAMENT " + strategy + " Statistics:");

        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("  Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("  Stand-pat rate: " + String.format("%.1f%%", standPatRate));
            }
        }

        if (Minimax.counter > 0) {
            System.out.println("  Search nodes: " + Minimax.counter);
            long totalPruning = Minimax.reverseFutilityCutoffs + Minimax.nullMoveCutoffs + Minimax.futilityCutoffs;
            if (totalPruning > 0) {
                double pruningRate = 100.0 * totalPruning / (Minimax.counter + totalPruning);
                System.out.println("  Pruning efficiency: " + String.format("%.1f%%", pruningRate));
            }
        }
    }

    /**
     * Move ordering with advanced heuristics
     */
    private static void orderMovesWithAdvancedHeuristics(List<Move> moves, GameState state, int depth, TTEntry entry) {
        Minimax.orderMovesTournament(moves, state, depth, entry);
    }

    /**
     * Helper method to store in transposition table
     */
    private static void storeInTranspositionTable(long hash, int score, int depth, int flag, Move bestMove) {
        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        Minimax.storeTranspositionEntry(hash, entry);
    }

    private static class TimeoutException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}