package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.ConsolidatedSearchConfig;

import GaT.evaluation.Evaluator; // Your new unified evaluator

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * MINIMAX - UNIFIED EVALUATOR INTEGRATION
 *
 * CHANGES:
 * ✅ Single unified evaluator instance used throughout
 * ✅ All evaluation calls route through unified evaluator
 * ✅ Removed legacy ModularEvaluator and EnhancedEvaluator references
 * ✅ Simplified evaluation interface
 * ✅ Maintains all existing functionality with new evaluator
 */
public class Minimax {

    // === CORE COMPONENTS WITH UNIFIED EVALUATOR ===
    private static final Evaluator evaluator = new Evaluator(); // Single unified evaluator instance
    private static final FastMoveOrdering moveOrdering = new FastMoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(ConsolidatedSearchConfig.TT_SIZE);
    private static final UnifiedStatistics statistics = UnifiedStatistics.getInstance();


    // === CASTLE POSITIONS ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === TIMEOUT SUPPORT ===
    private static BooleanSupplier timeoutChecker = null;

    // === LEGACY COMPATIBILITY ===
    public static int counter = 0;

    // === EVALUATION METHODS ===

    /**
     * Main static evaluation method - uses unified evaluator
     */
    public static int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state);
    }

    /**
     * Overloaded method for compatibility
     */
    public static int evaluate(GameState state) {
        return evaluator.evaluate(state);
    }

    /**
     * Get evaluator instance for other classes
     */
    public static Evaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Check if a move is a capture move
     */
    public static boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === MAIN SEARCH INTERFACES ===

    /**
     * Find best move using SearchConfig strategy
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ ERROR: Null game state in findBestMoveWithStrategy");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.DEFAULT_STRATEGY;
            System.out.println("🔧 Using SearchConfig.DEFAULT_STRATEGY: " + strategy);
        }

        statistics.reset();
        statistics.startSearch();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        // Order moves using SearchConfig-aware move ordering
        TTEntry ttEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (move == null) continue;

            try {
                GameState newState = state.copy();
                newState.applyMove(move);

                int score;
                switch (strategy) {
                    case PVS:
                        score = PVSSearch.search(newState, depth - 1,
                                Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed, true);
                        break;
                    case MINIMAX:
                    default:
                        score = minimax(newState, depth - 1,
                                Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed);
                        break;
                }

                if ((isRed && score > bestScore) || (!isRed && score < bestScore)) {
                    bestScore = score;
                    bestMove = move;
                }

            } catch (Exception e) {
                System.err.println("❌ ERROR: Search failed for move " + move + ": " + e.getMessage());
                continue;
            }
        }

        statistics.endSearch();
        return bestMove;
    }

    /**
     * Legacy method - uses unified evaluator
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
    }

    /**
     * PVS search method
     */
    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS);
    }

    /**
     * Quiescence search method
     */
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS); // PVS includes quiescence
    }

    /**
     * Ultimate AI method using SearchConfig.DEFAULT_STRATEGY
     */
    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
    }

    // === CORE MINIMAX ALGORITHM ===

    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        counter++;
        statistics.incrementNodeCount();

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluate(state, depth);
        }

        if (depth == 0 || isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluate(state, depth);
        }

        // Move ordering
        TTEntry ttEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (move == null) continue;

                try {
                    GameState newState = state.copy();
                    newState.applyMove(move);
                    int eval = minimax(newState, depth - 1, alpha, beta, false);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) {
                        statistics.incrementAlphaBetaCutoffs();
                        break; // Beta cutoff
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR in minimax: " + e.getMessage());
                    continue;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (move == null) continue;

                try {
                    GameState newState = state.copy();
                    newState.applyMove(move);
                    int eval = minimax(newState, depth - 1, alpha, beta, true);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) {
                        statistics.incrementAlphaBetaCutoffs();
                        break; // Alpha cutoff
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR in minimax: " + e.getMessage());
                    continue;
                }
            }
            return minEval;
        }
    }

    // === ANALYSIS AND DIAGNOSTICS ===

    /**
     * Debug evaluation breakdown using unified evaluator
     */
    public static void printEvaluationBreakdown(GameState state) {
        System.out.println(evaluator.getEvaluationBreakdown(state));
    }

    /**
     * Reset all components
     */
    public static void reset() {
        statistics.reset();
        transpositionTable.clear();
        moveOrdering.resetKillerMoves();
        counter = 0;
        clearTimeoutChecker();

        System.out.println("🔄 Minimax reset with unified evaluator");
    }

    /**
     * Performance analysis
     */
    public static void analyzePosition(GameState state, int maxDepth) {
        System.out.println("=== POSITION ANALYSIS WITH UNIFIED EVALUATOR ===");
        state.printBoard();
        System.out.println("To move: " + (state.redToMove ? "RED" : "BLUE"));
        System.out.println("Material balance: " + getMaterialBalance(state));
        System.out.println("Game over: " + isGameOver(state));
        System.out.println("Endgame: " + isEndgame(state));
        System.out.println("Strategy: " + SearchConfig.DEFAULT_STRATEGY);

        if (!isGameOver(state)) {
            for (int depth = 1; depth <= Math.min(maxDepth, SearchConfig.MAX_DEPTH); depth++) {
                reset();
                long startTime = System.currentTimeMillis();
                Move bestMove = findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
                long endTime = System.currentTimeMillis();

                if (bestMove != null) {
                    GameState result = state.copy();
                    result.applyMove(bestMove);
                    int eval = evaluate(result, 0);

                    System.out.printf("Depth %d: %s (eval: %+d, time: %dms, nodes: %d)\n",
                            depth, bestMove, eval, endTime - startTime, statistics.getNodeCount());
                }
            }
        }

        System.out.println("\nEvaluation Breakdown:");
        printEvaluationBreakdown(state);
    }

    // === UTILITY METHODS ===

    public static boolean isGameOver(GameState state) {
        // Guard captured
        if (state.redGuard == 0 || state.blueGuard == 0) return true;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;

        return redWins || blueWins;
    }

    public static boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    public static int getMaterialBalance(GameState state) {
        int balance = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            balance += (state.redStackHeights[i] - state.blueStackHeights[i]) * 100;
        }
        return balance;
    }

    public static boolean isInCheck(GameState state) {
        return evaluator.isGuardInDanger(state, state.redToMove);
    }

    public static boolean hasNonPawnMaterial(GameState state) {
        // For Turm & Wächter, check if there are any towers
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0 || state.blueStackHeights[i] > 0) {
                return true;
            }
        }
        return false;
    }

    public static int scoreMove(GameState state, Move move) {
        try {
            GameState newState = state.copy();
            newState.applyMove(move);
            return evaluate(newState, 0);
        } catch (Exception e) {
            return state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
    }

    // === TRANSPOSITION TABLE ===

    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    // === TIMEOUT MANAGEMENT ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    // === COMPONENT STATUS ===

    public static String getComponentStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MINIMAX COMPONENT STATUS (Unified Evaluator) ===\n");
        sb.append("Evaluator: ").append(evaluator.getClass().getSimpleName()).append("\n");
        sb.append("Move Ordering: ").append(moveOrdering.getStatistics()).append("\n");
        sb.append("Transposition Table: ").append(transpositionTable.getBriefStatistics()).append("\n");
        sb.append("Search Statistics: ").append(statistics.getBriefSummary()).append("\n");
        sb.append("Default Strategy: ").append(SearchConfig.DEFAULT_STRATEGY).append("\n");
        sb.append("Max Depth: ").append(SearchConfig.MAX_DEPTH).append("\n");
        return sb.toString();
    }

    public static boolean validateSearchConfigIntegration() {
        return SearchConfig.validateConfiguration();
    }
}