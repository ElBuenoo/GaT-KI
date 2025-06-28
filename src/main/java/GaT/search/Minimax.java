package GaT.search;

import GaT.model.*;
import GaT.engine.GameEngine;
import GaT.evaluation.Evaluator;
import java.util.function.BooleanSupplier;

/**
 * DEPRECATED - Backward compatibility wrapper
 * Use GameEngine instead!
 *
 * This class maintains the old static API while delegating to the new architecture
 */
@Deprecated
public class Minimax {
    private static final GameEngine engine = new GameEngine();
    private static final Evaluator evaluator = new Evaluator();

    // Legacy compatibility
    public static int counter = 0;

    // === DEPRECATED METHODS - Use GameEngine instead ===

    @Deprecated
    public static Move findBestMove(GameState state, int depth) {
        counter = 0; // Reset for compatibility
        return engine.findBestMove(state, depth);
    }

    @Deprecated
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return engine.findBestMove(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    @Deprecated
    public static Move findBestMoveWithStrategy(GameState state, int depth,
                                                SearchConfig.SearchStrategy strategy) {
        return engine.findBestMove(state, depth, strategy);
    }

    @Deprecated
    public static int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    @Deprecated
    public static boolean isGameOver(GameState state) {
        return GameRules.isGameOver(state);
    }

    @Deprecated
    public static boolean isCapture(Move move, GameState state) {
        return GameRules.isCapture(move, state);
    }

    @Deprecated
    public static int scoreMove(GameState state, Move move) {
        // Basic move scoring for backward compatibility
        int score = 0;
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Capture scoring
        boolean capturesGuard = ((isRed ? state.blueGuard : state.redGuard) & toBit) != 0;
        boolean capturesTower = ((isRed ? state.blueTowers : state.redTowers) & toBit) != 0;

        if (capturesGuard) score += 10000;
        if (capturesTower) {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            score += 1000 + height * 100;
        }

        return score;
    }

    @Deprecated
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        return scoreMove(state, move); // Simplified for compatibility
    }

    @Deprecated
    public static void orderMovesAdvanced(java.util.List<Move> moves, GameState state,
                                          int depth, TTEntry entry) {
        // Delegate to move ordering
        MoveOrdering ordering = new MoveOrdering();
        ordering.orderMoves(moves, state, depth, entry);
    }

    @Deprecated
    public static TTEntry getTranspositionEntry(long hash) {
        // Can't access internal TT from here - return null
        return null;
    }

    @Deprecated
    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        // No-op for compatibility
    }

    @Deprecated
    public static void clearTranspositionTable() {
        engine.clearCaches();
    }

    @Deprecated
    public static void resetPruningStats() {
        engine.resetStatistics();
    }

    @Deprecated
    public static void storeKillerMove(Move move, int depth) {
        // No-op for compatibility
    }

    @Deprecated
    public static void resetKillerMoves() {
        // No-op for compatibility
    }

    @Deprecated
    public static void setTimeoutChecker(BooleanSupplier checker) {
        // No-op for compatibility
    }

    @Deprecated
    public static void clearTimeoutChecker() {
        // No-op for compatibility
    }

    // Castle indices for compatibility
    public static final int RED_CASTLE_INDEX = GameRules.RED_CASTLE;
    public static final int BLUE_CASTLE_INDEX = GameRules.BLUE_CASTLE;
}