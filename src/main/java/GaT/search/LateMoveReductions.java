package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;

import java.util.List;

/**
 * LATE MOVE REDUCTIONS (LMR) - Focus on Best Moves
 *
 * Reduces search depth for moves that are unlikely to be best,
 * allowing deeper search on promising moves while maintaining
 * tactical accuracy through re-searches when needed.
 */
public class LateMoveReductions {

    // === LMR CONSTANTS ===
    private static final int LMR_MIN_DEPTH = 3;
    private static final int LMR_MIN_MOVE_NUMBER = 4;
    private static final int LMR_MAX_REDUCTION = 3;

    // === REDUCTION TABLE - Precomputed for efficiency ===
    private static final int[][] REDUCTION_TABLE = new int[32][64];

    static {
        initializeReductionTable();
    }

    /**
     * MAIN LMR INTERFACE
     */
    public static class LMRResult {
        public final boolean shouldReduce;
        public final int reduction;
        public final boolean requiresFullSearch;

        public LMRResult(boolean shouldReduce, int reduction, boolean requiresFullSearch) {
            this.shouldReduce = shouldReduce;
            this.reduction = reduction;
            this.requiresFullSearch = requiresFullSearch;
        }

        public static LMRResult noReduction() {
            return new LMRResult(false, 0, false);
        }

        public static LMRResult reduce(int reduction) {
            return new LMRResult(true, reduction, false);
        }

        public static LMRResult fullSearchRequired() {
            return new LMRResult(false, 0, true);
        }
    }

    /**
     * ENHANCED LMR for PVS Search
     */
    public static LMRResult calculateReduction(GameState state, Move move, int depth,
                                               int moveNumber, boolean isPVNode,
                                               boolean isCapture, boolean givesCheck,
                                               int alpha, int beta) {

        // Never reduce in PV nodes for first few moves
        if (isPVNode && moveNumber <= 2) {
            return LMRResult.noReduction();
        }

        // Don't reduce if depth is too low
        if (depth < LMR_MIN_DEPTH) {
            return LMRResult.noReduction();
        }

        // Don't reduce early moves
        if (moveNumber < LMR_MIN_MOVE_NUMBER) {
            return LMRResult.noReduction();
        }

        // Don't reduce tactical moves
        if (isCapture || givesCheck || isTacticalMove(move, state)) {
            return LMRResult.noReduction();
        }

        // Don't reduce if in check
        if (isInCheck(state)) {
            return LMRResult.noReduction();
        }

        // Don't reduce promoted pieces or important positional moves
        if (isImportantMove(move, state)) {
            return LMRResult.noReduction();
        }

        // Calculate reduction
        int reduction = calculateLMRReduction(depth, moveNumber, isPVNode, alpha, beta);

        return LMRResult.reduce(reduction);
    }

    /**
     * ADAPTIVE LMR - Adjusts reduction based on position characteristics
     */
    public static LMRResult calculateAdaptiveReduction(GameState state, Move move, int depth,
                                                       int moveNumber, boolean isPVNode,
                                                       boolean isCapture, boolean givesCheck,
                                                       int alpha, int beta, int nodeCount) {

        LMRResult basicResult = calculateReduction(state, move, depth, moveNumber,
                isPVNode, isCapture, givesCheck, alpha, beta);

        if (!basicResult.shouldReduce) {
            return basicResult;
        }

        // Adaptive adjustments
        int reduction = basicResult.reduction;

        // Increase reduction in high node count situations
        if (nodeCount > 100000) {
            reduction = Math.min(reduction + 1, LMR_MAX_REDUCTION);
        }

        // Reduce reduction in critical positions
        if (isCriticalPosition(state)) {
            reduction = Math.max(1, reduction - 1);
        }

        // Reduce reduction in endgame
        if (isEndgame(state)) {
            reduction = Math.max(1, reduction - 1);
        }

        // Increase reduction for moves far from action
        if (isMoveAwayFromAction(move, state)) {
            reduction = Math.min(reduction + 1, LMR_MAX_REDUCTION);
        }

        return LMRResult.reduce(reduction);
    }

    /**
     * FRACTIONAL LMR - More nuanced reduction calculation
     */
    public static LMRResult calculateFractionalReduction(GameState state, Move move, int depth,
                                                         int moveNumber, boolean isPVNode,
                                                         int alpha, int beta, double timeUsed) {

        if (depth < LMR_MIN_DEPTH || moveNumber < LMR_MIN_MOVE_NUMBER) {
            return LMRResult.noReduction();
        }

        // Base reduction from table
        int baseReduction = getTableReduction(depth, moveNumber);

        // Fractional adjustments based on various factors
        double reductionFactor = 1.0;

        // Time pressure adjustment
        if (timeUsed > 0.8) {
            reductionFactor += 0.5; // More aggressive when time is short
        } else if (timeUsed < 0.3) {
            reductionFactor -= 0.3; // Less aggressive when time is abundant
        }

        // Position complexity adjustment
        int complexity = evaluatePositionComplexity(state);
        if (complexity > 30) {
            reductionFactor -= 0.2; // Less reduction in complex positions
        } else if (complexity < 15) {
            reductionFactor += 0.3; // More reduction in simple positions
        }

        // Window size adjustment
        int windowSize = beta - alpha;
        if (windowSize > 200) {
            reductionFactor += 0.2; // More reduction for wide windows
        }

        // Apply fractional calculation
        int finalReduction = Math.max(1, Math.min(LMR_MAX_REDUCTION,
                (int)(baseReduction * reductionFactor)));

        return LMRResult.reduce(finalReduction);
    }

    /**
     * INTEGRATED LMR SEARCH - Complete LMR logic for search functions
     */
    public static int performLMRSearch(GameState state, Move move, int depth, int alpha, int beta,
                                       boolean maximizingPlayer, boolean isPVNode, int moveNumber,
                                       SearchEngine searchEngine, SearchConfig.SearchStrategy strategy) {

        boolean isCapture = isCapture(move, state);
        boolean givesCheck = givesCheck(move, state);

        // Calculate if we should reduce
        LMRResult lmrResult = calculateReduction(state, move, depth, moveNumber,
                isPVNode, isCapture, givesCheck, alpha, beta);

        if (!lmrResult.shouldReduce) {
            // No reduction - perform full search
            return searchEngine.search(state, depth, alpha, beta, maximizingPlayer, strategy);
        }

        // Perform reduced search
        int reducedDepth = Math.max(1, depth - lmrResult.reduction);
        int score = searchEngine.search(state, reducedDepth, alpha, beta, maximizingPlayer, strategy);

        // Check if we need to re-search
        boolean needsReSearch = false;

        if (maximizingPlayer) {
            needsReSearch = score > alpha; // Move looks good
        } else {
            needsReSearch = score < beta;  // Move looks good
        }

        // Re-search with full depth if the reduced search suggests this might be a good move
        if (needsReSearch) {
            SearchStatistics.getInstance().incrementLMRReductions();
            score = searchEngine.search(state, depth, alpha, beta, maximizingPlayer, strategy);
        }

        return score;
    }

    /**
     * PVS-OPTIMIZED LMR - Special handling for Principal Variation Search
     */
    public static int performPVSLMRSearch(GameState state, Move move, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, boolean isPVNode, int moveNumber,
                                          SearchEngine searchEngine) {

        boolean isCapture = isCapture(move, state);
        boolean givesCheck = givesCheck(move, state);

        LMRResult lmrResult = calculateReduction(state, move, depth, moveNumber,
                isPVNode, isCapture, givesCheck, alpha, beta);

        if (!lmrResult.shouldReduce) {
            // No reduction - use PVS
            if (isPVNode || moveNumber == 1) {
                return searchEngine.searchPVS(state, depth, alpha, beta, maximizingPlayer, true, true);
            } else {
                // Null window search first
                int nullScore = searchEngine.searchPVS(state, depth, alpha, alpha + 1, maximizingPlayer, false, true);
                if (nullScore > alpha && nullScore < beta) {
                    // Re-search with full window
                    return searchEngine.searchPVS(state, depth, nullScore, beta, maximizingPlayer, true, true);
                }
                return nullScore;
            }
        }

        // Perform LMR with PVS
        int reducedDepth = Math.max(1, depth - lmrResult.reduction);

        // Try reduced depth search with null window
        int score = searchEngine.searchPVS(state, reducedDepth, alpha, alpha + 1, maximizingPlayer, false, true);

        // If it looks promising, re-search
        if (score > alpha && score < beta) {
            // Re-search with reduced depth but full window
            score = searchEngine.searchPVS(state, reducedDepth, alpha, beta, maximizingPlayer, false, true);

            // If still promising, re-search with full depth
            if (score > alpha && score < beta) {
                score = searchEngine.searchPVS(state, depth, alpha, beta, maximizingPlayer, true, true);
            }
        }

        return score;
    }

    // === REDUCTION CALCULATION METHODS ===

    /**
     * CALCULATE BASE LMR REDUCTION
     */
    private static int calculateLMRReduction(int depth, int moveNumber, boolean isPVNode,
                                             int alpha, int beta) {

        // Use precomputed table for base reduction
        int tableReduction = getTableReduction(depth, moveNumber);

        // Adjust for PV nodes
        if (isPVNode) {
            tableReduction = Math.max(1, tableReduction - 1);
        }

        // Adjust for window size
        int windowSize = beta - alpha;
        if (windowSize < 50) {
            tableReduction += 1; // More aggressive for narrow windows
        }

        return Math.min(tableReduction, LMR_MAX_REDUCTION);
    }

    /**
     * GET REDUCTION from precomputed table
     */
    private static int getTableReduction(int depth, int moveNumber) {
        depth = Math.min(depth, REDUCTION_TABLE.length - 1);
        moveNumber = Math.min(moveNumber, REDUCTION_TABLE[0].length - 1);
        return REDUCTION_TABLE[depth][moveNumber];
    }

    /**
     * INITIALIZE REDUCTION TABLE
     */
    private static void initializeReductionTable() {
        for (int depth = 1; depth < REDUCTION_TABLE.length; depth++) {
            for (int moveNumber = 1; moveNumber < REDUCTION_TABLE[depth].length; moveNumber++) {

                // Base formula: reduction = log(depth) * log(moveNumber) / 2
                double reduction = Math.log(depth) * Math.log(moveNumber) / 2.0;

                // Minimum reduction of 1 for qualifying moves
                int intReduction = Math.max(1, (int)Math.round(reduction));

                // Cap at maximum
                REDUCTION_TABLE[depth][moveNumber] = Math.min(intReduction, LMR_MAX_REDUCTION);
            }
        }
    }

    // === MOVE CLASSIFICATION METHODS ===

    /**
     * CHECK if move is tactical (should not be reduced)
     */
    private static boolean isTacticalMove(Move move, GameState state) {
        // Captures are already handled separately

        // Check if move attacks enemy guard
        if (attacksEnemyGuard(move, state)) {
            return true;
        }

        // Check if move defends our guard
        if (defendsOurGuard(move, state)) {
            return true;
        }

        // Check if move creates threats
        if (createsThreat(move, state)) {
            return true;
        }

        return false;
    }

    /**
     * CHECK if move is important positionally
     */
    private static boolean isImportantMove(Move move, GameState state) {
        // Guard moves in endgame
        if (isEndgame(state) && isGuardMove(move, state)) {
            return true;
        }

        // Moves to/from key squares
        if (isKeySquare(move.to) || isKeySquare(move.from)) {
            return true;
        }

        // High-value piece moves
        if (isHighValuePiece(move.from, state)) {
            return true;
        }

        return false;
    }

    /**
     * CHECK if position is critical (reduce reductions)
     */
    private static boolean isCriticalPosition(GameState state) {
        // Guard in danger
        if (isGuardInDanger(state)) {
            return true;
        }

        // Material imbalance
        if (hasLargeMaterialImbalance(state)) {
            return true;
        }

        // Near endgame with advancement
        if (isEndgame(state) && hasAdvancedGuards(state)) {
            return true;
        }

        return false;
    }

    // === HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static boolean givesCheck(Move move, GameState state) {
        // In Guard & Towers, "check" means attacking the enemy guard
        return attacksEnemyGuard(move, state);
    }

    private static boolean isInCheck(GameState state) {
        // Check if our guard is under attack
        boolean isRed = state.redToMove;
        long guard = isRed ? state.redGuard : state.blueGuard;

        if (guard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guard);
        return isSquareUnderAttack(guardPos, state, !isRed);
    }

    private static boolean attacksEnemyGuard(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        return canAttackFromTo(move.to, guardPos, move.amountMoved);
    }

    private static boolean defendsOurGuard(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guard = isRed ? state.redGuard : state.blueGuard;

        if (guard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guard);
        int distance = calculateDistance(move.to, guardPos);

        return distance <= 2; // Move brings piece closer to guard
    }

    private static boolean createsThreat(Move move, GameState state) {
        // Simplified threat detection
        return move.amountMoved > 2; // Long-range moves often create threats
    }

    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guard = isRed ? state.redGuard : state.blueGuard;
        return guard != 0 && move.from == Long.numberOfTrailingZeros(guard);
    }

    private static boolean isKeySquare(int square) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        // Central squares and castle squares
        return (file >= 2 && file <= 4 && rank >= 2 && rank <= 4) ||
                (rank == 0 && file == 3) || (rank == 6 && file == 3);
    }

    private static boolean isHighValuePiece(int square, GameState state) {
        boolean isRed = state.redToMove;

        // Guard is always high value
        long guard = isRed ? state.redGuard : state.blueGuard;
        if ((guard & GameState.bit(square)) != 0) {
            return true;
        }

        // High towers are high value
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
        return height >= 3;
    }

    private static boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= 8;
    }

    private static boolean isGuardInDanger(GameState state) {
        boolean isRed = state.redToMove;
        long guard = isRed ? state.redGuard : state.blueGuard;

        if (guard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guard);
        return isSquareUnderAttack(guardPos, state, !isRed);
    }

    private static boolean hasLargeMaterialImbalance(GameState state) {
        int redMaterial = 0, blueMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redMaterial += state.redStackHeights[i];
            blueMaterial += state.blueStackHeights[i];
        }
        return Math.abs(redMaterial - blueMaterial) >= 3;
    }

    private static boolean hasAdvancedGuards(GameState state) {
        // Check if guards are in advanced positions
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (rank <= 2) return true;
        }

        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (rank >= 4) return true;
        }

        return false;
    }

    private static boolean isMoveAwayFromAction(Move move, GameState state) {
        // Check if move is toward the edges and away from center action
        int toFile = GameState.file(move.to);
        int toRank = GameState.rank(move.to);

        boolean nearEdge = toFile <= 1 || toFile >= 5 || toRank <= 1 || toRank >= 5;

        // Also check if moving away from enemy guard
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyPos = Long.numberOfTrailingZeros(enemyGuard);
            int oldDistance = calculateDistance(move.from, enemyPos);
            int newDistance = calculateDistance(move.to, enemyPos);
            return nearEdge && newDistance > oldDistance;
        }

        return nearEdge;
    }

    private static int evaluatePositionComplexity(GameState state) {
        // Count pieces and possible interactions
        int complexity = 0;

        // Count total pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            complexity += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        // Add guard presence
        if (state.redGuard != 0) complexity += 2;
        if (state.blueGuard != 0) complexity += 2;

        // Add mobility factor
        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            complexity += moves.size() / 3;
        } catch (Exception e) {
            complexity += 10; // Default if move generation fails
        }

        return complexity;
    }

    private static boolean isSquareUnderAttack(int square, GameState state, boolean byPlayer) {
        long attackers = byPlayer ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((attackers & GameState.bit(i)) != 0) {
                int range = byPlayer ?
                        (state.redStackHeights[i] > 0 ? state.redStackHeights[i] : 1) :
                        (state.blueStackHeights[i] > 0 ? state.blueStackHeights[i] : 1);

                if (canAttackFromTo(i, square, range)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canAttackFromTo(int from, int to, int range) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (rankDiff != 0 && fileDiff != 0) return false;
        return Math.max(rankDiff, fileDiff) <= range;
    }

    private static int calculateDistance(int from, int to) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));
        return rankDiff + fileDiff;
    }

    // === PUBLIC UTILITIES ===

    /**
     * CHECK if LMR should be used for this position
     */
    public static boolean shouldUseLMR(GameState state, int depth) {
        return depth >= LMR_MIN_DEPTH && !isCriticalPosition(state);
    }

    /**
     * GET STATISTICS about LMR usage
     */
    public static String getLMRStatistics() {
        long reductions = SearchStatistics.getInstance().getTotalNodes(); // Placeholder
        return String.format("LMR: %d reductions applied", reductions);
    }
}