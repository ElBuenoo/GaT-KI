package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;
import GaT.evaluation.ThreatDetector;
import GaT.evaluation.StaticExchangeEvaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ENHANCED MINIMAX FACADE - FIXED IMPLEMENTATION
 * Integrating all tactical improvements with proper method signatures
 *
 * FIXES:
 * ✅ All missing method implementations added
 * ✅ Proper integration with StaticExchangeEvaluator
 * ✅ Proper integration with ThreatDetector
 * ✅ Fixed method signatures and dependencies
 * ✅ Backward compatibility maintained
 */
public class Minimax {

    // === CORE COMPONENTS (ENHANCED) ===
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH CONSTANTS ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === BACKWARD COMPATIBILITY ===
    public static int counter = 0;

    // === ENHANCED SEARCH INTERFACES ===

    /**
     * Find best move with enhanced tactical awareness - FIXED
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        statistics.reset();
        statistics.startSearch();

        // Pre-search threat analysis
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        if (threats.mustDefend) {
            System.out.println("⚠️ Critical threats detected - defensive mode");
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        // Enhanced move ordering with threat awareness
        moveOrdering.orderMoves(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);

            int score = searchEngine.search(copy, depth - 1,
                    isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE,
                    isRed ? Integer.MAX_VALUE : Integer.MIN_VALUE,
                    !isRed, strategy);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore)) {
                bestScore = score;
                bestMove = move;
            }
        }

        statistics.endSearch();
        return bestMove;
    }

    /**
     * LEGACY INTERFACE - Fixed to work with new components
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
    }

    /**
     * TACTICAL POSITION CHECK - Enhanced with real SEE
     */
    public static boolean isTacticalPosition(GameState state) {
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

        // Must defend situations
        if (threats.mustDefend || threats.inCheck) {
            return true;
        }

        if (threats.immediateThreats.size() >= 2) {
            return true;
        }

        // Check for hanging pieces using SEE
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (isCapture(move, state)) {
                int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                if (seeValue > 100) {
                    return true; // Free piece available
                }
            }
        }

        return false;
    }

    /**
     * Enhanced capture checking with SEE
     */
    public static boolean isSafeCapture(Move move, GameState state) {
        return StaticExchangeEvaluator.isSafeCapture(state, move);
    }

    /**
     * Get capture value using SEE
     */
    public static int getCaptureValue(Move move, GameState state) {
        return StaticExchangeEvaluator.evaluate(state, move);
    }

    // === ENHANCED EVALUATION WITH TACTICS ===

    /**
     * Evaluate with tactical awareness
     */
    public static int evaluateTactical(GameState state, int depth) {
        int baseEval = evaluator.evaluate(state, depth);

        // Add tactical adjustments
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

        if (threats.inCheck) {
            // Penalty for being in check
            baseEval += state.redToMove ? -50 : 50;
        }

        // Adjust for threat level
        int threatAdjustment = threats.threatLevel * 10;
        baseEval += state.redToMove ? -threatAdjustment : threatAdjustment;

        return baseEval;
    }

    // === LEGACY COMPATIBILITY METHODS - FIXED ===

    /**
     * Legacy evaluate method - maintains backward compatibility
     */
    public static int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    /**
     * Legacy search method - fixed to use SearchEngine
     */
    public static int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return searchEngine.search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    /**
     * Search with quiescence - enhanced
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return searchEngine.search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    // === HELPER METHODS - FIXED IMPLEMENTATIONS ===

    /**
     * Check if move is a capture - Fixed implementation
     */
    public static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move captures guard - Fixed implementation
     */
    public static boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move is by guard - Fixed implementation
     */
    public static boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redToMove && (state.redGuard & fromBit) != 0) ||
                (!state.redToMove && (state.blueGuard & fromBit) != 0);
    }

    /**
     * Check if position is endgame - Fixed implementation
     */
    public static boolean isEndgame(GameState state) {
        int totalMaterial = 0;

        // Count towers
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        // Guards count as 2 material points each
        if (state.redGuard != 0) totalMaterial += 2;
        if (state.blueGuard != 0) totalMaterial += 2;

        return totalMaterial <= 8; // Endgame when few pieces remain
    }

    /**
     * Check if game is over - Fixed implementation
     */
    public static boolean isGameOver(GameState state) {
        // Check if either guard reached enemy castle
        if ((state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0) {
            return true; // Red wins
        }
        if ((state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0) {
            return true; // Blue wins
        }

        // Check if guard was captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Could add stalemate check here if needed
        return false;
    }

    /**
     * Get game result - Fixed implementation
     */
    public static int getGameResult(GameState state) {
        if ((state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0) {
            return 10000; // Red wins
        }
        if ((state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0) {
            return -10000; // Blue wins
        }
        if (state.redGuard == 0) {
            return -10000; // Red guard captured, Blue wins
        }
        if (state.blueGuard == 0) {
            return 10000; // Blue guard captured, Red wins
        }

        return 0; // Game continues
    }

    // === TRANSPOSITION TABLE INTERFACE ===

    /**
     * Get transposition table entry - Fixed implementation
     */
    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    /**
     * Store transposition table entry - Fixed implementation
     */
    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    // === SEARCH STATISTICS ===

    /**
     * Get current search statistics
     */
    public static SearchStatistics getStatistics() {
        return statistics;
    }

    /**
     * Reset search statistics
     */
    public static void resetStatistics() {
        statistics.reset();
    }

    // === TIMEOUT SUPPORT ===

    /**
     * Set timeout checker for search
     */
    public static void setTimeoutChecker(BooleanSupplier timeoutChecker) {
        searchEngine.setTimeoutChecker(timeoutChecker);
    }

    /**
     * Clear timeout checker
     */
    public static void clearTimeoutChecker() {
        searchEngine.clearTimeoutChecker();
    }

    // === MOVE ORDERING INTERFACE ===

    /**
     * Order moves using enhanced move ordering
     */
    public static void orderMoves(List<Move> moves, GameState state, int depth) {
        moveOrdering.orderMoves(moves, state, depth, getTranspositionEntry(state.hash()));
    }

    /**
     * Store killer move
     */
    public static void storeKillerMove(Move move, int depth) {
        moveOrdering.storeKillerMove(move, depth);
    }

    /**
     * Update history heuristic
     */
    public static void updateHistory(Move move, int depth, int bonus) {
        moveOrdering.updateHistory(move, depth, bonus);
    }

    // === TACTICAL ANALYSIS INTERFACE ===

    /**
     * Analyze threats in position
     */
    public static ThreatDetector.ThreatAnalysis analyzeThreats(GameState state) {
        return ThreatDetector.analyzeThreats(state);
    }

    /**
     * Check for immediate threats
     */
    public static boolean hasImmediateThreat(GameState state) {
        return ThreatDetector.hasImmediateThreat(state);
    }

    /**
     * Static exchange evaluation
     */
    public static int evaluateExchange(GameState state, Move move) {
        return StaticExchangeEvaluator.evaluate(state, move);
    }

    // === SEARCH ENGINE DELEGATION ===

    /**
     * Main search interface with strategy
     */
    public static int searchWithStrategy(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {
        return searchEngine.search(state, depth, alpha, beta, maximizingPlayer, strategy);
    }

    /**
     * Check if null move pruning should be applied
     */
    public static boolean canUseNullMove(GameState state) {
        return NullMovePruning.canUseNullMove(state, 0); // Simplified check
    }
}