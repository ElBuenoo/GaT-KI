package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;

/**
 * NULL MOVE PRUNING - Deeper Tactical Vision
 *
 * Allows the AI to search deeper by pruning branches where even giving
 * the opponent a free move doesn't improve their position enough.
 * Essential for tactical depth and avoiding zugzwang positions.
 */
public class NullMovePruning {

    // === NULL MOVE CONSTANTS ===
    private static final int NULL_MOVE_MIN_DEPTH = 3;
    private static final int NULL_MOVE_REDUCTION = 2;
    private static final int ZUGZWANG_MATERIAL_THRESHOLD = 8; // Endgame threshold
    private static final int NULL_MOVE_MARGIN = 0; // Beta margin for null move

    // === VERIFICATION SEARCH CONSTANTS ===
    private static final int VERIFICATION_REDUCTION = 3;
    private static final int VERIFICATION_MIN_DEPTH = 6;

    /**
     * MAIN NULL MOVE INTERFACE - Enhanced for PVS
     */
    public static class NullMoveResult {
        public final boolean shouldPrune;
        public final int score;
        public final boolean threatDetected;

        public NullMoveResult(boolean shouldPrune, int score, boolean threatDetected) {
            this.shouldPrune = shouldPrune;
            this.score = score;
            this.threatDetected = threatDetected;
        }
    }

    /**
     * ENHANCED NULL MOVE PRUNING for PVS
     */
    public static NullMoveResult tryNullMovePruning(GameState state, int depth, int alpha, int beta,
                                                    boolean maximizingPlayer, boolean isPVNode,
                                                    SearchEngine searchEngine) {

        // Don't use null move in PV nodes
        if (isPVNode) {
            return new NullMoveResult(false, 0, false);
        }

        // Minimum depth check
        if (depth < NULL_MOVE_MIN_DEPTH) {
            return new NullMoveResult(false, 0, false);
        }

        // Don't null move in zugzwang-prone positions
        if (isZugzwangRisk(state)) {
            return new NullMoveResult(false, 0, false);
        }

        // Don't null move when in check (if we had check detection)
        if (isInCheck(state)) {
            return new NullMoveResult(false, 0, false);
        }

        // Create null move state
        GameState nullMoveState = createNullMoveState(state);

        // Calculate reduction
        int reduction = calculateNullMoveReduction(depth, beta - alpha);
        int nullDepth = depth - 1 - reduction;

        if (nullDepth <= 0) {
            return new NullMoveResult(false, 0, false);
        }

        // Perform null move search
        int nullScore;
        try {
            nullScore = searchEngine.search(nullMoveState, nullDepth, -beta, -beta + 1,
                    !maximizingPlayer, SearchConfig.SearchStrategy.PVS);
        } catch (Exception e) {
            return new NullMoveResult(false, 0, false);
        }

        // Flip score for current player
        nullScore = -nullScore;

        // Check for cutoff
        if ((maximizingPlayer && nullScore >= beta) || (!maximizingPlayer && nullScore <= alpha)) {

            // Verification search for high-value cutoffs
            if (depth >= VERIFICATION_MIN_DEPTH && Math.abs(nullScore) > 500) {
                return verifyNullMoveCutoff(state, depth, alpha, beta, maximizingPlayer,
                        nullScore, searchEngine);
            }

            SearchStatistics.getInstance().incrementNullMoveCutoffs();
            return new NullMoveResult(true, nullScore, false);
        }

        // Check for threats
        boolean threatDetected = detectNullMoveThreats(state, nullMoveState, nullScore);

        return new NullMoveResult(false, nullScore, threatDetected);
    }

    /**
     * ADAPTIVE NULL MOVE - Adjust reduction based on position
     */
    public static NullMoveResult tryAdaptiveNullMove(GameState state, int depth, int alpha, int beta,
                                                     boolean maximizingPlayer, boolean isPVNode,
                                                     SearchEngine searchEngine, int nodeCount) {

        if (isPVNode || depth < NULL_MOVE_MIN_DEPTH) {
            return new NullMoveResult(false, 0, false);
        }

        // Adaptive conditions based on search progress
        int material = getTotalMaterial(state);
        boolean isEndgame = material <= ZUGZWANG_MATERIAL_THRESHOLD;
        boolean highNodeCount = nodeCount > 10000;

        // Skip null move in complex endgames
        if (isEndgame && hasComplexPawnStructure(state)) {
            return new NullMoveResult(false, 0, false);
        }

        // Aggressive null move in high node count situations
        int baseReduction = NULL_MOVE_REDUCTION;
        if (highNodeCount && !isEndgame) {
            baseReduction += 1; // More aggressive pruning
        }

        return tryNullMovePruningWithReduction(state, depth, alpha, beta, maximizingPlayer,
                baseReduction, searchEngine);
    }

    /**
     * NULL MOVE WITH CUSTOM REDUCTION
     */
    private static NullMoveResult tryNullMovePruningWithReduction(GameState state, int depth,
                                                                  int alpha, int beta, boolean maximizingPlayer,
                                                                  int reduction, SearchEngine searchEngine) {

        GameState nullMoveState = createNullMoveState(state);
        int nullDepth = Math.max(1, depth - 1 - reduction);

        try {
            int nullScore = searchEngine.search(nullMoveState, nullDepth, -beta, -beta + 1,
                    !maximizingPlayer, SearchConfig.SearchStrategy.ALPHA_BETA);
            nullScore = -nullScore;

            boolean cutoff = (maximizingPlayer && nullScore >= beta) ||
                    (!maximizingPlayer && nullScore <= alpha);

            if (cutoff) {
                SearchStatistics.getInstance().incrementNullMoveCutoffs();
                return new NullMoveResult(true, nullScore, false);
            }

            return new NullMoveResult(false, nullScore, false);

        } catch (Exception e) {
            return new NullMoveResult(false, 0, false);
        }
    }

    /**
     * VERIFICATION SEARCH - Double-check important cutoffs
     */
    private static NullMoveResult verifyNullMoveCutoff(GameState state, int depth, int alpha, int beta,
                                                       boolean maximizingPlayer, int nullScore,
                                                       SearchEngine searchEngine) {

        // Reduced depth verification search
        int verifyDepth = Math.max(1, depth - VERIFICATION_REDUCTION);

        try {
            int verifyScore = searchEngine.search(state, verifyDepth, alpha, beta, maximizingPlayer,
                    SearchConfig.SearchStrategy.ALPHA_BETA);

            // If verification confirms the cutoff
            boolean confirmedCutoff = (maximizingPlayer && verifyScore >= beta) ||
                    (!maximizingPlayer && verifyScore <= alpha);

            if (confirmedCutoff) {
                return new NullMoveResult(true, verifyScore, false);
            } else {
                // Verification failed, continue normal search
                return new NullMoveResult(false, verifyScore, true);
            }

        } catch (Exception e) {
            return new NullMoveResult(false, nullScore, false);
        }
    }

    // === HELPER METHODS ===

    /**
     * CREATE NULL MOVE STATE - Switch turns without making a move
     */
    private static GameState createNullMoveState(GameState state) {
        GameState nullState = state.copy();
        nullState.redToMove = !nullState.redToMove;
        return nullState;
    }

    /**
     * CALCULATE DYNAMIC REDUCTION based on position
     */
    private static int calculateNullMoveReduction(int depth, int windowSize) {
        int reduction = NULL_MOVE_REDUCTION;

        // Increase reduction for deeper searches
        if (depth > 8) {
            reduction += 1;
        }

        // Increase reduction for wide windows (less precise searches)
        if (windowSize > 200) {
            reduction += 1;
        }

        return Math.min(reduction, depth - 1);
    }

    /**
     * ZUGZWANG RISK DETECTION - Avoid null move in risky positions
     */
    private static boolean isZugzwangRisk(GameState state) {
        int material = getTotalMaterial(state);

        // High risk in endgames
        if (material <= ZUGZWANG_MATERIAL_THRESHOLD) {
            return true;
        }

        // High risk when pieces are limited
        boolean redLimited = countMobilePieces(state, true) <= 2;
        boolean blueLimited = countMobilePieces(state, false) <= 2;

        return state.redToMove ? redLimited : blueLimited;
    }

    /**
     * CHECK DETECTION - Simplified for Guard & Towers
     */
    private static boolean isInCheck(GameState state) {
        // In Guard & Towers, "check" means guard is under immediate attack
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        return isSquareUnderAttack(guardPos, state, !isRed);
    }

    /**
     * THREAT DETECTION after null move
     */
    private static boolean detectNullMoveThreats(GameState originalState, GameState nullMoveState, int nullScore) {
        // Simple threat detection based on evaluation swing
        int originalEval = Minimax.evaluate(originalState, 0);
        int nullEval = Minimax.evaluate(nullMoveState, 0);

        int evalSwing = Math.abs(nullEval - originalEval);
        return evalSwing > 300; // Significant position change indicates threats
    }

    /**
     * SQUARE ATTACK DETECTION
     */
    private static boolean isSquareUnderAttack(int square, GameState state, boolean byPlayer) {
        long attackers = byPlayer ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((attackers & GameState.bit(i)) != 0) {
                int range = byPlayer ?
                        (state.redStackHeights[i] > 0 ? state.redStackHeights[i] : 1) :
                        (state.blueStackHeights[i] > 0 ? state.blueStackHeights[i] : 1);

                if (canAttackSquare(i, square, range)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canAttackSquare(int from, int to, int range) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (rankDiff != 0 && fileDiff != 0) return false;
        return Math.max(rankDiff, fileDiff) <= range;
    }

    private static int getTotalMaterial(GameState state) {
        int total = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            total += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return total;
    }

    private static int countMobilePieces(GameState state, boolean isRed) {
        int count = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) count++;
        }

        // Add guard if present
        long guard = isRed ? state.redGuard : state.blueGuard;
        if (guard != 0) count++;

        return count;
    }

    private static boolean hasComplexPawnStructure(GameState state) {
        // In Guard & Towers, this checks for complex tower arrangements
        int clusters = 0;
        boolean[] visited = new boolean[GameState.NUM_SQUARES];

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (!visited[i] && (state.redStackHeights[i] > 0 || state.blueStackHeights[i] > 0)) {
                markCluster(state, i, visited);
                clusters++;
            }
        }

        return clusters >= 4; // Complex if many separate clusters
    }

    private static void markCluster(GameState state, int start, boolean[] visited) {
        if (visited[start]) return;

        visited[start] = true;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int next = start + dir;
            if (GameState.isOnBoard(next) && !visited[next] &&
                    (state.redStackHeights[next] > 0 || state.blueStackHeights[next] > 0)) {
                markCluster(state, next, visited);
            }
        }
    }

    // === PUBLIC UTILITIES ===

    /**
     * CHECK if null move should be attempted in this position
     */
    public static boolean shouldAttemptNullMove(GameState state, int depth, boolean isPVNode) {
        return !isPVNode &&
                depth >= NULL_MOVE_MIN_DEPTH &&
                !isZugzwangRisk(state) &&
                !isInCheck(state);
    }

    /**
     * GET OPTIMAL REDUCTION for current position
     */
    public static int getOptimalReduction(GameState state, int depth) {
        int reduction = NULL_MOVE_REDUCTION;

        if (getTotalMaterial(state) <= ZUGZWANG_MATERIAL_THRESHOLD) {
            reduction = Math.max(1, reduction - 1); // Be more careful in endgames
        }

        if (depth > 10) {
            reduction += 1; // More aggressive in deep searches
        }

        return Math.min(reduction, depth - 1);
    }
}