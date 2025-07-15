package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * SEARCH ENGINE - UNIFIED SYSTEMS INTEGRATION
 */
public class SearchEngine {

    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final MoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final UnifiedStatistics statistics;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    // === CONSTRUCTOR ===
    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, UnifiedStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;

        System.out.println("🔧 SearchEngine initialized with unified systems:");
        System.out.println("   DEFAULT_STRATEGY: " + GameConfig.DEFAULT_STRATEGY);
        System.out.println("   MAX_DEPTH: " + GameConfig.MAX_DEPTH);
        System.out.println("   TT_SIZE: " + GameConfig.TT_SIZE);
    }

    public static SearchEngine createDefault() {
        return new SearchEngine(
                Minimax.getEvaluator(),
                new MoveOrdering(),
                new TranspositionTable(GameConfig.TT_SIZE),
                UnifiedStatistics.getInstance()
        );
    }

    // === MAIN SEARCH INTERFACE ===

    /**
     * Main search method using GameConfig strategy selection
     */
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, GameConfig.Strategy strategy) {

        // Use GameConfig.DEFAULT_STRATEGY if null
        if (strategy == null) {
            strategy = GameConfig.DEFAULT_STRATEGY;
            System.out.println("🔧 Using GameConfig.DEFAULT_STRATEGY: " + strategy);
        }

        // Validate depth against GameConfig
        if (depth > GameConfig.MAX_DEPTH) {
            System.out.printf("⚠️ Depth %d exceeds GameConfig.MAX_DEPTH (%d), clamping\n",
                    depth, GameConfig.MAX_DEPTH);
            depth = GameConfig.MAX_DEPTH;
        }

        return switch (strategy) {
            case PVS_Q -> PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
            case PVS -> PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
            case ALPHA_BETA -> alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
        };
    }

    // === ALPHA-BETA IMPLEMENTATION ===

    /**
     * Alpha-Beta search using GameConfig parameters
     */
    private int alphaBetaSearch(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null state passed to alphaBetaSearch");
            return 0;
        }

        if (!state.isValid()) {
            System.err.println("❌ CRITICAL: Invalid state passed to alphaBetaSearch");
            return evaluator.evaluate(state);
        }

        statistics.incrementRegularNode();

        // Timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluator.evaluate(state);
        }

        // Terminal conditions
        if (depth == 0 || isGameOver(state)) {
            statistics.incrementLeafNode();
            return evaluator.evaluate(state);
        }

        // Generate moves
        List<Move> moves;
        try {
            moves = MoveGenerator.generateAllMoves(state);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move generation failed: " + e.getMessage());
            return evaluator.evaluate(state);
        }

        if (moves.isEmpty()) {
            return evaluator.evaluate(state);
        }

        // Order moves
        try {
            TTEntry ttEntry = transpositionTable.get(state.hash());
            moveOrdering.orderMoves(moves, state, ttEntry);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move ordering failed: " + e.getMessage());
        }

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                if (move == null) continue;
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearch(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoff();
                    updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            }
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                if (move == null) continue;
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearch(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoff();
                    updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            }
            return minEval;
        }
    }

    // === HELPER METHODS ===

    /**
     * Game over detection using GameConfig thresholds
     */
    private boolean isGameOver(GameState state) {
        // Basic game over detection
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return true;
        }

        // Check for guard captures/castle reaches
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        // Guard captured
        if (enemyGuard == 0 || ownGuard == 0) {
            return true;
        }

        // Castle reached
        int ownCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        int enemyCastle = isRed ? GameState.getIndex(6, 3) : GameState.getIndex(0, 3);

        if ((ownGuard & GameState.bit(enemyCastle)) != 0) {
            return true;
        }

        return false;
    }

    private void updateHistoryOnCutoff(Move move, GameState state, int depth) {
        try {
            if (!Minimax.isCapture(move, state)) {
                moveOrdering.storeKillerMove(move, depth);
                moveOrdering.updateHistory(move, depth);
            }
        } catch (Exception e) {
            // Silent fail - history is optimization
        }
    }

    // === MOVE SCORING ===

    /**
     * Enhanced move scoring using GameConfig values
     */
    public int scoreMove(GameState state, Move move) {
        if (state == null || move == null) return 0;

        int score = 0;
        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Capture scoring using GameValues
        if (isCapture(move, state)) {
            // Guard capture using GameValues
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += GameValues.GUARD_CAPTURE_VALUE;
            } else {
                // Tower capture using GameValues multiplier
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += height * GameValues.TOWER_CAPTURE_VALUE;
            }
        }

        // Activity bonus
        score += move.amountMoved * 5;

        return score;
    }

    /**
     * Advanced move scoring with GameConfig depth consideration
     */
    public int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int baseScore = scoreMove(state, move);

        // Depth bonus
        int depthBonus = depth * 10;

        // Endgame adjustment
        if (isEndgame(state)) {
            // In endgame, prioritize guard moves more
            if (isGuardMove(move, state)) {
                baseScore += 30;
            }
        }

        return baseScore + depthBonus;
    }

    /**
     * Check if move is a capture
     */
    private boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;

        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
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
     * Check if endgame using GameConfig threshold
     */
    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= 8; // Simple endgame threshold
    }

    // === SEARCH EFFICIENCY ANALYSIS ===

    /**
     * Get search efficiency score based on GameConfig expectations
     */
    public double getSearchEfficiency() {
        long totalNodes = statistics.getTotalNodes();
        double cutoffRate = statistics.getCutoffRate();
        double ttHitRate = statistics.getTTHitRate();

        // Calculate efficiency score (0-100) using GameConfig benchmarks
        double nodeEfficiency = Math.min(100, 100.0 * GameConfig.NODES_PER_SECOND_TARGET / Math.max(1, totalNodes));
        double cutoffEfficiency = cutoffRate * 30; // Up to 30 points for good cutoffs
        double ttEfficiency = ttHitRate * 20; // Up to 20 points for TT hits
        double strategyEfficiency = GameConfig.DEFAULT_STRATEGY == GameConfig.Strategy.PVS_Q ? 50 : 30;

        return Math.min(100, nodeEfficiency + cutoffEfficiency + ttEfficiency + strategyEfficiency);
    }

    /**
     * Analyze search performance and suggest improvements
     */
    public String analyzeSearchPerformance() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCH PERFORMANCE ANALYSIS ===\n");

        long searchTimeMs = statistics.getTotalSearchTime();
        long nodesSearched = statistics.getTotalNodes();
        double nps = searchTimeMs > 0 ? (double) nodesSearched * 1000 / searchTimeMs : 0;

        sb.append(String.format("Search time: %dms\n", searchTimeMs));
        sb.append(String.format("Nodes searched: %,d\n", nodesSearched));
        sb.append(String.format("Nodes per second: %.1f\n", nps));

        // Compare against GameConfig target
        if (nps >= GameConfig.NODES_PER_SECOND_TARGET) {
            sb.append(String.format("✅ Performance meets GameConfig target (%.1fk NPS)\n",
                    GameConfig.NODES_PER_SECOND_TARGET / 1000.0));
        } else {
            sb.append(String.format("⚠️ Performance below GameConfig target (%.1fk NPS)\n",
                    GameConfig.NODES_PER_SECOND_TARGET / 1000.0));
        }

        double efficiency = getSearchEfficiency();
        long avgNodes = statistics.getTotalNodes();
        double cutoffRate = statistics.getCutoffRate();

        if (efficiency < 50) {
            sb.append("⚠️ Low search efficiency (").append(String.format("%.1f", efficiency)).append("%):\n");

            if (cutoffRate < 0.3) {
                sb.append("  • Poor move ordering - tune GameConfig move ordering parameters\n");
            }

            if (statistics.getTTHitRate() < 0.4) {
                sb.append("  • Low TT hit rate - consider increasing GameConfig.TT_SIZE\n");
            }
        } else {
            sb.append("✅ Good search efficiency (").append(String.format("%.1f", efficiency)).append("%)\n");
        }

        // Strategy recommendations
        if (GameConfig.DEFAULT_STRATEGY == GameConfig.Strategy.ALPHA_BETA) {
            sb.append("💡 Consider upgrading to GameConfig.Strategy.PVS_Q for better performance\n");
        }

        return sb.toString();
    }

    /**
     * Export current GameConfig effectiveness metrics
     */
    public String exportEffectivenessMetrics() {
        return String.format("SearchEngine,%.2f,%.3f,%.3f,%d,%s\n",
                getSearchEfficiency(),
                statistics.getCutoffRate(),
                statistics.getTTHitRate(),
                statistics.getTotalNodes(),
                GameConfig.DEFAULT_STRATEGY);
    }

    // === TIMEOUT MANAGEMENT ===

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }
}