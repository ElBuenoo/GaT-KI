package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;

/**
 * LATE MOVE REDUCTIONS (LMR) - Complete Implementation
 *
 * Reduces search depth for moves that are unlikely to be best,
 * allowing deeper search on promising moves while maintaining tactical accuracy.
 *
 * FEATURES:
 * ✅ Dynamic reduction table based on depth and move number
 * ✅ Adaptive reductions based on position characteristics
 * ✅ Re-search mechanism for surprisingly good moves
 * ✅ Integration with move ordering and search statistics
 */
public class LateMoveReductions {

    // === LMR CONSTANTS ===
    private static final int MIN_DEPTH_FOR_LMR = 3;
    private static final int MIN_MOVES_FOR_LMR = 4;
    private static final int MAX_REDUCTION = 3;
    private static final double BASE_REDUCTION_FACTOR = 0.75;
    private static final double LOG_DIVISOR = 2.25;

    // === PRE-COMPUTED REDUCTION TABLE ===
    private static final int[][] REDUCTION_TABLE = new int[64][64]; // [depth][moveNumber]

    static {
        initializeReductionTable();
    }

    /**
     * Initialize the LMR reduction table
     */
    private static void initializeReductionTable() {
        for (int depth = 1; depth < 64; depth++) {
            for (int moveNum = 1; moveNum < 64; moveNum++) {
                double reduction = BASE_REDUCTION_FACTOR +
                        Math.log(depth) * Math.log(moveNum) / LOG_DIVISOR;
                REDUCTION_TABLE[depth][moveNum] = Math.max(0, (int) Math.round(reduction));
            }
        }
    }

    /**
     * Main LMR interface - calculate reduction for a move
     */
    public static int getReduction(GameState state, Move move, int depth, int moveNumber,
                                   boolean isPVNode, boolean inCheck) {

        // Don't reduce in these conditions
        if (depth < MIN_DEPTH_FOR_LMR) return 0;
        if (moveNumber < MIN_MOVES_FOR_LMR) return 0;
        if (isPVNode) return 0;
        if (inCheck) return 0;
        if (isImportantMove(state, move)) return 0;

        // Get base reduction from table
        int baseReduction = REDUCTION_TABLE[Math.min(depth, 63)][Math.min(moveNumber, 63)];

        // Apply modifiers
        int finalReduction = applyReductionModifiers(state, move, baseReduction, depth, moveNumber);

        // Ensure reduction doesn't exceed limits
        return Math.min(finalReduction, Math.min(MAX_REDUCTION, depth - 1));
    }

    /**
     * Simplified LMR interface for quick usage
     */
    public static int getReduction(int depth, int moveNumber, boolean isQuiet) {
        if (depth < MIN_DEPTH_FOR_LMR || moveNumber < MIN_MOVES_FOR_LMR) {
            return 0;
        }

        int baseReduction = REDUCTION_TABLE[Math.min(depth, 63)][Math.min(moveNumber, 63)];

        // Reduce less for non-quiet moves
        if (!isQuiet) {
            baseReduction = baseReduction * 2 / 3;
        }

        return Math.min(baseReduction, Math.min(MAX_REDUCTION, depth - 1));
    }

    /**
     * Apply modifiers to base reduction based on position and move characteristics
     */
    private static int applyReductionModifiers(GameState state, Move move, int baseReduction,
                                               int depth, int moveNumber) {
        int reduction = baseReduction;

        // === MOVE TYPE MODIFIERS ===

        // Captures: reduce less
        if (isCapture(move, state)) {
            reduction = Math.max(0, reduction - 1);
        }

        // Checks: reduce less (if we had proper check detection)
        if (givesCheck(move, state)) {
            reduction = Math.max(0, reduction - 1);
        }

        // Promotions or special moves: reduce less
        if (isSpecialMove(move, state)) {
            reduction = Math.max(0, reduction - 1);
        }

        // === POSITIONAL MODIFIERS ===

        // Central moves: reduce less
        if (isCentralMove(move)) {
            reduction = Math.max(0, reduction - 1);
        }

        // Castle approach moves: reduce less
        if (approachesCastle(move, state)) {
            reduction = Math.max(0, reduction - 1);
        }

        // === SEARCH DEPTH MODIFIERS ===

        // At very high depths, be more aggressive with reductions
        if (depth >= 12) {
            reduction += 1;
        }

        // Later in move ordering, increase reduction
        if (moveNumber >= 15) {
            reduction += 1;
        }

        // === GAME PHASE MODIFIERS ===

        GamePhase phase = detectGamePhase(state);
        switch (phase) {
            case ENDGAME:
                // Reduce less in endgame (more tactical)
                reduction = Math.max(0, reduction - 1);
                break;
            case OPENING:
                // Can reduce more in opening (less tactical)
                reduction += 1;
                break;
            case MIDDLEGAME:
                // Standard reduction in middlegame
                break;
        }

        return Math.max(0, reduction);
    }

    /**
     * Check if move should not be reduced (important moves)
     */
    private static boolean isImportantMove(GameState state, Move move) {
        // Guard moves are always important
        if (isGuardMove(move, state)) {
            return true;
        }

        // Captures are important
        if (isCapture(move, state)) {
            return true;
        }

        // Moves that give check are important
        if (givesCheck(move, state)) {
            return true;
        }

        // Moves to/from important squares
        if (isImportantSquare(move.from) || isImportantSquare(move.to)) {
            return true;
        }

        return false;
    }

    /**
     * Re-search decision after LMR
     */
    public static boolean shouldReSearch(int lmrScore, int alpha, int beta, int reduction) {
        // If the reduced search suggests the move might be good, re-search
        if (reduction > 0 && lmrScore > alpha) {
            return true;
        }

        // If the score is very close to the bounds, re-search for accuracy
        if (Math.abs(lmrScore - alpha) < 50 || Math.abs(lmrScore - beta) < 50) {
            return true;
        }

        return false;
    }

    /**
     * Get statistics about LMR usage
     */
    public static LMRStatistics getStatistics() {
        return new LMRStatistics();
    }

    // === HELPER METHODS ===

    /**
     * Check if move is a capture
     */
    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move gives check (simplified)
     */
    private static boolean givesCheck(Move move, GameState state) {
        // Simplified check detection - could be enhanced with ThreatDetector
        return isGuardMove(move, state) && isNearEnemyCastle(move.to, state);
    }

    /**
     * Check if move is by guard
     */
    private static boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redToMove && (state.redGuard & fromBit) != 0) ||
                (!state.redToMove && (state.blueGuard & fromBit) != 0);
    }

    /**
     * Check if position is near enemy castle
     */
    private static boolean isNearEnemyCastle(int square, GameState state) {
        int enemyCastle = state.redToMove ?
                GameState.getIndex(0, 3) : // Blue castle for red
                GameState.getIndex(6, 3);  // Red castle for blue

        int distance = Math.abs(GameState.rank(square) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(square) - GameState.file(enemyCastle));

        return distance <= 2;
    }

    /**
     * Check if move is to central squares
     */
    private static boolean isCentralMove(Move move) {
        int rank = GameState.rank(move.to);
        int file = GameState.file(move.to);
        return rank >= 2 && rank <= 4 && file >= 2 && file <= 4;
    }

    /**
     * Check if move approaches enemy castle
     */
    private static boolean approachesCastle(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        int currentDist = Math.abs(GameState.rank(move.from) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(move.from) - GameState.file(enemyCastle));
        int newDist = Math.abs(GameState.rank(move.to) - GameState.rank(enemyCastle)) +
                Math.abs(GameState.file(move.to) - GameState.file(enemyCastle));

        return newDist < currentDist;
    }

    /**
     * Check if move is special (promotion, etc.)
     */
    private static boolean isSpecialMove(Move move, GameState state) {
        // In this game, reaching enemy castle with guard is special
        return isGuardMove(move, state) && isNearEnemyCastle(move.to, state);
    }

    /**
     * Check if square is strategically important
     */
    private static boolean isImportantSquare(int square) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        // Center squares
        if (rank >= 2 && rank <= 4 && file >= 2 && file <= 4) {
            return true;
        }

        // Castle squares
        if ((rank == 0 && file == 3) || (rank == 6 && file == 3)) {
            return true;
        }

        return false;
    }

    /**
     * Detect game phase for LMR adjustments
     */
    private static GamePhase detectGamePhase(GameState state) {
        int totalMaterial = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalMaterial <= 8) {
            return GamePhase.ENDGAME;
        } else if (totalMaterial <= 20) {
            return GamePhase.MIDDLEGAME;
        } else {
            return GamePhase.OPENING;
        }
    }

    // === INNER CLASSES ===

    /**
     * Game phase enumeration
     */
    private enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    /**
     * LMR statistics class
     */
    public static class LMRStatistics {
        public int totalReductions = 0;
        public int reSearches = 0;
        public int avgReduction = 0;
        public double reductionEfficiency = 0.0;

        public LMRStatistics() {
            // Get statistics from SearchStatistics if available
            SearchStatistics stats = SearchStatistics.getInstance();
            this.totalReductions = (int) stats.getLMRReductions();
            this.reSearches = (int) stats.getLMRReSearches();

            if (totalReductions > 0) {
                this.avgReduction = totalReductions / Math.max(1, (int) stats.getNodeCount());
                this.reductionEfficiency = 1.0 - ((double) reSearches / totalReductions);
            }
        }

        @Override
        public String toString() {
            return String.format("LMR Stats: %d reductions, %d re-searches, %.1f%% efficiency",
                    totalReductions, reSearches, reductionEfficiency * 100);
        }
    }

    // === UTILITY METHODS ===

    /**
     * Get reduction table for debugging
     */
    public static int[][] getReductionTable() {
        return REDUCTION_TABLE.clone();
    }

    /**
     * Print reduction table for analysis
     */
    public static void printReductionTable() {
        System.out.println("LMR Reduction Table:");
        System.out.print("Depth\\Move: ");
        for (int move = 1; move <= 10; move++) {
            System.out.printf("%3d ", move);
        }
        System.out.println();

        for (int depth = 1; depth <= 10; depth++) {
            System.out.printf("    %2d:    ", depth);
            for (int move = 1; move <= 10; move++) {
                System.out.printf("%3d ", REDUCTION_TABLE[depth][move]);
            }
            System.out.println();
        }
    }

    /**
     * Test LMR with specific parameters
     */
    public static void testLMR(GameState state, Move move, int depth, int moveNumber) {
        int reduction = getReduction(state, move, depth, moveNumber, false, false);
        System.out.printf("LMR Test: depth=%d, move#=%d, reduction=%d%n",
                depth, moveNumber, reduction);
    }
}