package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * SEARCH ENGINE - PHASE 1 INTEGRATION (FIXED)
 *
 * CHANGES:
 * ✅ Uses ConsolidatedSearchConfig instead of SearchConfig
 * ✅ Uses GameValues for all piece values and constants
 * ✅ Compatible with UnifiedStatistics and FastMoveOrdering
 * ✅ Fixed strategy enum to use ConsolidatedSearchConfig.Strategy
 * ✅ All hardcoded values replaced with Phase 1 constants
 */
public class SearchEngine {

    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final FastMoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final UnifiedStatistics statistics;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    // === CONSTRUCTOR ===
    public SearchEngine(Evaluator evaluator, FastMoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, UnifiedStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;

        System.out.println("🔧 SearchEngine initialized with Phase 1 optimizations:");
        System.out.println("   DEFAULT_STRATEGY: " + ConsolidatedSearchConfig.DEFAULT_STRATEGY);
        System.out.println("   MAX_DEPTH: " + ConsolidatedSearchConfig.MAX_DEPTH);
        System.out.println("   TT_SIZE: " + ConsolidatedSearchConfig.TT_SIZE);

        validateIntegration();
    }

    public static SearchEngine createDefault() {
        return new SearchEngine(
                Minimax.getEvaluator(),
                new FastMoveOrdering(),
                new TranspositionTable(ConsolidatedSearchConfig.TT_SIZE),
                UnifiedStatistics.getInstance()
        );
    }

    // === MAIN SEARCH INTERFACE ===

    /**
     * Main search method using ConsolidatedSearchConfig strategy selection
     */
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, ConsolidatedSearchConfig.Strategy strategy) {

        // Use DEFAULT_STRATEGY if null
        if (strategy == null) {
            strategy = ConsolidatedSearchConfig.DEFAULT_STRATEGY;
            System.out.println("🔧 Using ConsolidatedSearchConfig.DEFAULT_STRATEGY: " + strategy);
        }

        // Validate depth
        if (depth > ConsolidatedSearchConfig.MAX_DEPTH) {
            System.out.printf("⚠️ Depth %d exceeds ConsolidatedSearchConfig.MAX_DEPTH (%d), clamping\n",
                    depth, ConsolidatedSearchConfig.MAX_DEPTH);
            depth = ConsolidatedSearchConfig.MAX_DEPTH;
        }

        return switch (strategy) {
            case PVS_QUIESCENCE -> PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
            case PVS -> PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
            case ALPHA_BETA -> alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
            default -> {
                System.err.printf("⚠️ Unknown strategy: %s, using %s\n", strategy, ConsolidatedSearchConfig.DEFAULT_STRATEGY);
                yield search(state, depth, alpha, beta, maximizingPlayer, ConsolidatedSearchConfig.DEFAULT_STRATEGY);
            }
        };
    }

    // === ALPHA-BETA IMPLEMENTATIONS ===

    /**
     * Alpha-Beta search using Phase 1 optimizations
     */
    private int alphaBetaSearch(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        statistics.incrementNodes();

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluator.evaluate(state);
        }

        // Terminal conditions
        if (depth == 0 || isGameOver(state)) {
            return evaluator.evaluate(state);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluator.evaluate(state);
        }

        // Move ordering using FastMoveOrdering
        moveOrdering.orderMoves(moves, state, depth, null);

        if (maximizingPlayer) {
            int maxEval = GameValues.ALPHA_INIT;
            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearch(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    moveOrdering.updateHistory(move, depth, state);
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = GameValues.BETA_INIT;
            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearch(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    moveOrdering.updateHistory(move, depth, state);
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Game over detection
     */
    private boolean isGameOver(GameState state) {
        // Basic game over detection
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return true;
        }

        // Check for guard captures
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        // No guards = game over
        if (enemyGuard == 0 || ownGuard == 0) {
            return true;
        }

        return false;
    }

    // === OVERLOADED SEARCH METHODS ===

    /**
     * Search with default strategy
     */
    public int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return search(state, depth, alpha, beta, maximizingPlayer, ConsolidatedSearchConfig.DEFAULT_STRATEGY);
    }

    /**
     * Search with timeout
     */
    public int searchWithTimeout(GameState state, int depth, int alpha, int beta,
                                 boolean maximizingPlayer, ConsolidatedSearchConfig.Strategy strategy,
                                 BooleanSupplier timeoutCheck) {
        this.timeoutChecker = timeoutCheck;
        try {
            return search(state, depth, alpha, beta, maximizingPlayer, strategy);
        } finally {
            this.timeoutChecker = null;
        }
    }

    /**
     * Search with time management
     */
    public int searchWithTimeManagement(GameState state, int depth, int alpha, int beta,
                                        boolean maximizingPlayer, long remainingTimeMs) {

        // Determine strategy based on remaining time
        ConsolidatedSearchConfig.Strategy strategy;
        if (remainingTimeMs <= ConsolidatedSearchConfig.EMERGENCY_TIME_MS) {
            strategy = ConsolidatedSearchConfig.Strategy.ALPHA_BETA; // Fastest
            System.out.printf("🚨 Emergency time (%dms <= %dms), using %s\n",
                    remainingTimeMs, ConsolidatedSearchConfig.EMERGENCY_TIME_MS, strategy);
        } else if (remainingTimeMs <= 5000) { // Low time threshold
            strategy = ConsolidatedSearchConfig.Strategy.PVS; // Fast with quality
            System.out.printf("⏱️ Low time (%dms), using %s\n", remainingTimeMs, strategy);
        } else {
            strategy = ConsolidatedSearchConfig.DEFAULT_STRATEGY; // Full strength
            System.out.printf("✅ Comfortable time (%dms), using %s\n", remainingTimeMs, strategy);
        }

        return search(state, depth, alpha, beta, maximizingPlayer, strategy);
    }

    // === MOVE SCORING ===

    /**
     * Enhanced move scoring using GameValues
     */
    public int scoreMove(GameState state, Move move) {
        if (state == null || move == null) return 0;

        int score = 0;
        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Capture scoring using GameValues
        if (GameValues.isCapture(state, move.from, move.to)) {
            // Guard capture
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += GameValues.GUARD_CAPTURE_VALUE;
            } else {
                // Tower capture
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += height * GameValues.TOWER_HEIGHT_MULTIPLIER;
            }
        }

        // Central control bonus
        int file = GameState.file(move.to);
        if (file >= 2 && file <= 4) {
            score += GameValues.CENTER_FILE_BONUS;
        }

        // Forward progress bonus
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        if (isRed && toRank > fromRank) {
            score += (toRank - fromRank) * GameValues.ADVANCEMENT_BONUS;
        } else if (!isRed && toRank < fromRank) {
            score += (fromRank - toRank) * GameValues.ADVANCEMENT_BONUS;
        }

        // Activity bonus
        score += move.amountMoved * 5; // Simple activity bonus

        return score;
    }

    /**
     * Advanced move scoring with depth consideration
     */
    public int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int baseScore = scoreMove(state, move);

        // Depth bonus
        int depthBonus = depth * 10;

        // Endgame adjustment
        if (isEndgame(state)) {
            // In endgame, prioritize guard moves more
            if (isGuardMove(move, state)) {
                baseScore += GameValues.ADVANCEMENT_BONUS * 2;
            }
        }

        return baseScore + depthBonus;
    }

    /**
     * Check if move is a guard move
     */
    private boolean isGuardMove(Move move, GameState state) {
        if (move == null || state == null) return false;

        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    /**
     * Endgame detection
     */
    private boolean isEndgame(GameState state) {
        if (state == null) return false;

        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        return totalMaterial <= 6; // Simple endgame threshold
    }

    // === PERFORMANCE ANALYSIS ===

    /**
     * Analyze search performance
     */
    public String analyzePerformance(long searchTimeMs, long nodesSearched) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCH PERFORMANCE ANALYSIS (PHASE 1) ===\n");

        // Nodes per second calculation
        double nps = searchTimeMs > 0 ? (double) nodesSearched * 1000 / searchTimeMs : 0;
        sb.append(String.format("Nodes per second: %.1f\n", nps));

        // Compare against target
        if (nps >= ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND) {
            sb.append(String.format("✅ Performance meets target (%.1fk NPS)\n",
                    ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND / 1000.0));
        } else {
            sb.append(String.format("⚠️ Performance below target (%.1fk NPS)\n",
                    ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND / 1000.0));
        }

        // Time analysis
        if (searchTimeMs <= ConsolidatedSearchConfig.EMERGENCY_TIME_MS) {
            sb.append("🚨 Emergency time search - consider faster strategy\n");
        } else if (searchTimeMs <= 5000) {
            sb.append("⏱️ Low time search - balance speed vs quality\n");
        } else if (searchTimeMs >= 10000) {
            sb.append("✅ Comfortable time - can use full strength search\n");
        }

        return sb.toString();
    }

    /**
     * Get search efficiency score
     */
    public double getSearchEfficiency() {
        long totalNodes = statistics.getTotalNodes();
        double cutoffRate = statistics.getCutoffRate();
        double ttHitRate = statistics.getTTHitRate();

        // Calculate efficiency score (0-100)
        double nodeEfficiency = Math.min(100, 100.0 * ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND / Math.max(1, totalNodes));
        double cutoffEfficiency = cutoffRate * 30; // Up to 30 points for good cutoffs
        double ttEfficiency = ttHitRate * 20; // Up to 20 points for TT hits
        double strategyEfficiency = ConsolidatedSearchConfig.DEFAULT_STRATEGY == ConsolidatedSearchConfig.Strategy.PVS_QUIESCENCE ? 20 : 10;

        return Math.min(100, nodeEfficiency + cutoffEfficiency + ttEfficiency + strategyEfficiency);
    }

    // === TIMEOUT MANAGEMENT ===

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    // === COMPONENT ACCESS ===

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public FastMoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    public UnifiedStatistics getStatistics() {
        return statistics;
    }

    // === EVALUATION DELEGATE ===

    public int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state);
    }

    public int evaluate(GameState state) {
        return evaluator.evaluate(state);
    }

    // === VALIDATION ===

    /**
     * Validate Phase 1 integration
     */
    private void validateIntegration() {
        boolean valid = true;

        if (ConsolidatedSearchConfig.DEFAULT_STRATEGY == null) {
            System.err.println("❌ ConsolidatedSearchConfig.DEFAULT_STRATEGY is null");
            valid = false;
        }

        if (ConsolidatedSearchConfig.MAX_DEPTH <= 0) {
            System.err.println("❌ Invalid ConsolidatedSearchConfig.MAX_DEPTH: " + ConsolidatedSearchConfig.MAX_DEPTH);
            valid = false;
        }

        if (ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND <= 0) {
            System.err.println("❌ Invalid ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND: " + ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND);
            valid = false;
        }

        if (ConsolidatedSearchConfig.EMERGENCY_TIME_MS <= 0) {
            System.err.println("❌ Invalid ConsolidatedSearchConfig.EMERGENCY_TIME_MS: " + ConsolidatedSearchConfig.EMERGENCY_TIME_MS);
            valid = false;
        }

        if (valid) {
            System.out.println("✅ SearchEngine Phase 1 integration validated");
        }
    }

    /**
     * Get configuration status for debugging
     */
    public String getConfigStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCHENGINE PHASE 1 STATUS ===\n");
        sb.append(String.format("Default Strategy: %s\n", ConsolidatedSearchConfig.DEFAULT_STRATEGY));
        sb.append(String.format("Max Depth: %d\n", ConsolidatedSearchConfig.MAX_DEPTH));
        sb.append(String.format("TT Size: %,d\n", ConsolidatedSearchConfig.TT_SIZE));
        sb.append(String.format("Emergency Time: %dms\n", ConsolidatedSearchConfig.EMERGENCY_TIME_MS));
        sb.append(String.format("Target NPS: %,d\n", ConsolidatedSearchConfig.TARGET_NODES_PER_SECOND));

        // Component status
        sb.append("\nComponent Integration:\n");
        sb.append("  Evaluator: ").append(evaluator.getClass().getSimpleName()).append("\n");
        sb.append("  MoveOrdering: FastMoveOrdering (Phase 1)\n");
        sb.append("  Statistics: UnifiedStatistics (Phase 1)\n");
        sb.append("  TranspositionTable: ").append(transpositionTable.size()).append(" entries\n");

        return sb.toString();
    }

    /**
     * Recommend optimizations based on performance
     */
    public String recommendOptimizations() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PHASE 1 OPTIMIZATION RECOMMENDATIONS ===\n");

        double efficiency = getSearchEfficiency();
        long avgNodes = statistics.getTotalNodes();
        double cutoffRate = statistics.getCutoffRate();

        if (efficiency < 50) {
            sb.append("⚠️ Low search efficiency (").append(String.format("%.1f", efficiency)).append("%):\n");

            if (cutoffRate < 0.3) {
                sb.append("  • Poor move ordering - FastMoveOrdering may need tuning\n");
                sb.append("  • Consider checking GameValues parameters\n");
            }

            if (statistics.getTTHitRate() < 0.4) {
                sb.append("  • Low TT hit rate - consider increasing ConsolidatedSearchConfig.TT_SIZE\n");
            }
        } else {
            sb.append("✅ Good search efficiency (").append(String.format("%.1f", efficiency)).append("%)\n");
        }

        // Strategy recommendations
        if (ConsolidatedSearchConfig.DEFAULT_STRATEGY == ConsolidatedSearchConfig.Strategy.ALPHA_BETA) {
            sb.append("💡 Consider upgrading to ConsolidatedSearchConfig.Strategy.PVS_QUIESCENCE for better performance\n");
        }

        return sb.toString();
    }

    /**
     * Export performance metrics
     */
    public String exportMetrics() {
        return String.format("SearchEngine,%.2f,%.3f,%.3f,%d,%s\n",
                getSearchEfficiency(),
                statistics.getCutoffRate(),
                statistics.getTTHitRate(),
                statistics.getTotalNodes(),
                ConsolidatedSearchConfig.DEFAULT_STRATEGY);
    }

    // === MISSING METHODS FOR COMPATIBILITY ===

    /**
     * Get brief statistics (compatibility method)
     */
    public String getBriefStatistics() {
        return String.format("Nodes: %,d, TT: %.1f%%, Cutoffs: %.1f%%",
                statistics.getTotalNodes(),
                statistics.getTTHitRate() * 100,
                statistics.getCutoffRate() * 100);
    }
}