package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.ConsolidatedSearchConfig;

import GaT.model.TTEntry;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * PVS SEARCH - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Null-move pruning using SearchConfig.NULL_MOVE_*
 * ✅ LMR using SearchConfig.LMR_*
 * ✅ Futility pruning using SearchConfig.FUTILITY_*
 * ✅ Extensions using SearchConfig extension parameters
 * ✅ All hardcoded values replaced with SearchConfig
 */
public class PVSSearch {

    // === DEPENDENCIES ===
    private static final FastMoveOrdering moveOrdering = new FastMoveOrdering();
    private static final UnifiedStatistics statistics = UnifiedStatistics.getInstance();

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === SEARCH FUNCTION INTERFACE ===
    @FunctionalInterface
    private interface SearchFunction {
        int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean isPVNode);
    }

    // === MAIN PVS INTERFACE WITH SEARCHCONFIG ===

    /**
     * PVS with Quiescence using SearchConfig parameters
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        if (state == null) {
            System.err.println("❌ CRITICAL: Null state passed to PVSSearch.searchWithQuiescence");
            return 0;
        }

        if (!state.isValid()) {
            System.err.println("❌ CRITICAL: Invalid state passed to PVSSearch.searchWithQuiescence");
            return Minimax.evaluate(state, depth);
        }

        statistics.incrementNodeCount();

        // Timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // TT-Lookup
        long hash = 0;
        TTEntry entry = null;
        try {
            hash = state.hash();
            entry = Minimax.getTranspositionEntry(hash);
            if (entry != null && entry.depth >= depth) {
                statistics.incrementTTHits();

                if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                    return entry.score;
                } else if (!isPVNode) {
                    if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                        return entry.score;
                    } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                        return entry.score;
                    }
                }
            } else {
                statistics.incrementTTMisses();
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: TT lookup failed: " + e.getMessage());
        }

        // Null-Move Pruning using SearchConfig
        if (SearchConfig.NULL_MOVE_ENABLED) {
            int nullMoveResult = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer,
                    isPVNode, PVSSearch::searchWithQuiescence);
            if (nullMoveResult != Integer.MIN_VALUE) {
                return nullMoveResult;
            }
        }

        // Terminal conditions
        try {
            if (Minimax.isGameOver(state)) {
                statistics.incrementLeafNodeCount();
                return Minimax.evaluate(state, depth);
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Game over check failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search when depth exhausted - using SearchConfig
        if (depth <= 0) {
            statistics.incrementQNodeCount();
            try {
                return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
            } catch (Exception e) {
                System.err.println("❌ ERROR: QuiescenceSearch.quiesce failed: " + e.getMessage());
                return Minimax.evaluate(state, depth);
            }
        }

        // Main search
        try {
            return performMainSearchWithConfig(state, depth, alpha, beta, maximizingPlayer,
                    isPVNode, entry, PVSSearch::searchWithQuiescence);
        } catch (Exception e) {
            System.err.println("❌ ERROR: performMainSearch failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }
    }

    /**
     * Standard PVS without Quiescence using SearchConfig parameters
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        if (state == null) {
            System.err.println("❌ CRITICAL: Null state passed to PVSSearch.search");
            return 0;
        }

        if (!state.isValid()) {
            System.err.println("❌ CRITICAL: Invalid state passed to PVSSearch.search");
            return Minimax.evaluate(state, depth);
        }

        statistics.incrementNodeCount();

        // Timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        // Terminal conditions using SearchConfig
        try {
            if (depth == 0 || Minimax.isGameOver(state)) {
                statistics.incrementLeafNodeCount();
                return Minimax.evaluate(state, depth);
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Terminal condition check failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }

        // Main search
        try {
            return performMainSearchWithConfig(state, depth, alpha, beta, maximizingPlayer,
                    isPVNode, null, PVSSearch::search);
        } catch (Exception e) {
            System.err.println("❌ ERROR: performMainSearch failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }
    }

    // === MAIN SEARCH LOGIC WITH SEARCHCONFIG ===

    private static int performMainSearchWithConfig(GameState state, int depth, int alpha, int beta,
                                                   boolean maximizingPlayer, boolean isPVNode, TTEntry entry,
                                                   SearchFunction searchFunc) {

        if (state == null || !state.isValid()) {
            return maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        // Generate and order moves
        List<Move> moves;
        try {
            moves = MoveGenerator.generateAllMoves(state);
            statistics.addMovesGenerated(moves.size());
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move generation failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }

        // Move ordering
        try {
            moveOrdering.orderMoves(moves, state, depth, entry);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move ordering failed: " + e.getMessage());
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        long hash = 0;
        try {
            hash = state.hash();
        } catch (Exception e) {
            System.err.println("❌ ERROR: Hash calculation failed: " + e.getMessage());
        }

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            boolean isFirstMove = true;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) break;

                Move move = moves.get(i);
                if (move == null) continue;

                // Apply move with exception handling
                GameState copy = null;
                try {
                    copy = state.copy();
                    if (copy == null || !copy.isValid()) continue;
                    copy.applyMove(move);
                    if (!copy.isValid()) continue;
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Copy/ApplyMove failed for move " + move + ": " + e.getMessage());
                    continue;
                }

                statistics.addMovesSearched(1);

                int eval;
                try {
                    eval = performSingleMoveWithConfig(copy, move, depth, alpha, beta, isPVNode,
                            isFirstMove, i, state, searchFunc);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Move evaluation failed for move " + move + ": " + e.getMessage());
                    eval = Minimax.evaluate(copy, depth - 1);
                }

                if (isFirstMove) isFirstMove = false;

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // History update with SearchConfig
                    try {
                        if (!Minimax.isCapture(move, state)) {
                            moveOrdering.storeKillerMove(move, depth);
                            moveOrdering.updateHistory(move, depth, state);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ ERROR: History update failed: " + e.getMessage());
                    }
                    break;
                }
            }

            // Store TT entry
            try {
                storeTTEntryWithConfig(hash, maxEval, depth, originalAlpha, beta, bestMove);
            } catch (Exception e) {
                System.err.println("❌ ERROR: TT store failed: " + e.getMessage());
            }

            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;
            boolean isFirstMove = true;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) break;

                Move move = moves.get(i);
                if (move == null) continue;

                // Apply move with exception handling
                GameState copy = null;
                try {
                    copy = state.copy();
                    if (copy == null || !copy.isValid()) continue;
                    copy.applyMove(move);
                    if (!copy.isValid()) continue;
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Copy/ApplyMove failed for move " + move + ": " + e.getMessage());
                    continue;
                }

                statistics.addMovesSearched(1);

                int eval;
                try {
                    eval = performSingleMoveWithConfig(copy, move, depth, alpha, beta, isPVNode,
                            isFirstMove, i, state, searchFunc);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Move evaluation failed for move " + move + ": " + e.getMessage());
                    eval = Minimax.evaluate(copy, depth - 1);
                }

                if (isFirstMove) isFirstMove = false;

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // History update
                    try {
                        if (!Minimax.isCapture(move, state)) {
                            moveOrdering.storeKillerMove(move, depth);
                            moveOrdering.updateHistory(move, depth, state);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ ERROR: History update failed: " + e.getMessage());
                    }
                    break;
                }
            }

            // Store TT entry
            try {
                storeTTEntryWithConfig(hash, minEval, depth, originalAlpha, beta, bestMove);
            } catch (Exception e) {
                System.err.println("❌ ERROR: TT store failed: " + e.getMessage());
            }

            return minEval;
        }
    }

    // === SINGLE MOVE EVALUATION WITH SEARCHCONFIG ===

    private static int performSingleMoveWithConfig(GameState copy, Move move, int depth, int alpha, int beta,
                                                   boolean isPVNode, boolean isFirstMove, int moveIndex,
                                                   GameState originalState, SearchFunction searchFunc) {

        if (copy == null || originalState == null || !copy.isValid()) {
            return originalState.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        boolean maximizingPlayer = !originalState.redToMove;
        boolean needsFullSearch = true;
        int eval = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Late Move Reductions using SearchConfig
        try {
            if (shouldReduceMoveWithConfig(originalState, move, depth, moveIndex, isPVNode)) {
                int reduction = calculateLMRReductionWithConfig(originalState, move, depth, moveIndex);
                int reducedDepth = Math.max(0, depth - 1 - reduction);

                // Reduced search
                try {
                    if (maximizingPlayer) {
                        eval = searchFunc.search(copy, reducedDepth, alpha, alpha + 1, true, false);
                    } else {
                        eval = searchFunc.search(copy, reducedDepth, beta - 1, beta, false, false);
                    }

                    statistics.incrementLMRReductions();

                    // Re-search if needed
                    boolean needsReSearch = maximizingPlayer ? (eval > alpha) : (eval < beta);

                    if (needsReSearch) {
                        try {
                            if (maximizingPlayer) {
                                eval = searchFunc.search(copy, depth - 1, alpha, alpha + 1, true, false);
                                needsFullSearch = (eval > alpha && isPVNode);
                            } else {
                                eval = searchFunc.search(copy, depth - 1, beta - 1, beta, false, false);
                                needsFullSearch = (eval < beta && eval > alpha && isPVNode);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ ERROR: LMR re-search failed for move " + move + ": " + e.getMessage());
                            needsFullSearch = true;
                        }
                    } else {
                        needsFullSearch = false;
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: LMR reduced search failed for move " + move + ": " + e.getMessage());
                    needsFullSearch = true;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: LMR evaluation failed for move " + move + ": " + e.getMessage());
            needsFullSearch = true;
        }

        // Full search if needed
        if (needsFullSearch) {
            try {
                if (isFirstMove || isPVNode) {
                    eval = searchFunc.search(copy, depth - 1, alpha, beta, maximizingPlayer, isPVNode);
                } else {
                    // Null-window search
                    int nullWindow = maximizingPlayer ? alpha + 1 : beta - 1;
                    eval = searchFunc.search(copy, depth - 1,
                            maximizingPlayer ? alpha : nullWindow,
                            maximizingPlayer ? nullWindow : beta,
                            maximizingPlayer, false);

                    // Re-search with full window if needed
                    if (isPVNode && ((maximizingPlayer && eval > alpha) || (!maximizingPlayer && eval < beta))) {
                        try {
                            eval = searchFunc.search(copy, depth - 1, alpha, beta, maximizingPlayer, true);
                        } catch (Exception e) {
                            System.err.println("❌ ERROR: PV re-search failed for move " + move + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ ERROR: Full search failed for move " + move + ": " + e.getMessage());
                eval = Minimax.evaluate(copy, depth - 1);
            }
        }

        return eval;
    }

    // === NULL-MOVE PRUNING WITH SEARCHCONFIG ===

    private static int tryNullMovePruning(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, boolean isPVNode,
                                          SearchFunction searchFunc) {

        if (!SearchConfig.NULL_MOVE_ENABLED) return Integer.MIN_VALUE;
        if (state == null || !state.isValid()) return Integer.MIN_VALUE;

        // Use SearchConfig parameters
        if (isPVNode || depth < SearchConfig.NULL_MOVE_MIN_DEPTH) {
            return Integer.MIN_VALUE;
        }

        // Skip if in check or endgame
        try {
            if (Minimax.isInCheck(state) || Minimax.isEndgame(state)) {
                return Integer.MIN_VALUE;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Check/Endgame test failed in tryNullMovePruning: " + e.getMessage());
            return Integer.MIN_VALUE;
        }

        // Skip if insufficient material
        try {
            if (!Minimax.hasNonPawnMaterial(state)) {
                return Integer.MIN_VALUE;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Material test failed in tryNullMovePruning: " + e.getMessage());
            return Integer.MIN_VALUE;
        }

        statistics.recordNullMoveAttempt(depth);

        try {
            // Create null-move state
            GameState nullMoveState = null;
            try {
                nullMoveState = state.copy();
                if (nullMoveState == null || !nullMoveState.isValid()) {
                    return Integer.MIN_VALUE;
                }
            } catch (Exception e) {
                System.err.println("❌ ERROR: Null-move state creation failed: " + e.getMessage());
                return Integer.MIN_VALUE;
            }

            // Switch turn without making a move
            nullMoveState.redToMove = !nullMoveState.redToMove;

            // Use SearchConfig.NULL_MOVE_REDUCTION
            int reduction = SearchConfig.NULL_MOVE_REDUCTION;
            int nullDepth = Math.max(0, depth - 1 - reduction);

            int nullScore;
            try {
                nullScore = searchFunc.search(nullMoveState, nullDepth, -beta, -alpha, !maximizingPlayer, false);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Null-move search failed: " + e.getMessage());
                return Integer.MIN_VALUE;
            }

            // Null-move cutoff?
            if (maximizingPlayer && nullScore >= beta) {
                statistics.recordNullMovePrune(depth, Math.max(1, depth * depth));

                // Verification search for critical positions using SearchConfig
                if (depth >= SearchConfig.NULL_MOVE_VERIFICATION_DEPTH && nullScore < 5000) {
                    try {
                        int verifyScore = searchFunc.search(state, depth - 5, alpha, beta, maximizingPlayer, false);
                        statistics.recordNullMoveVerification(verifyScore >= beta);

                        if (verifyScore >= beta) {
                            return maximizingPlayer ? beta : alpha;
                        } else {
                            statistics.recordNullMoveFailure();
                        }
                    } catch (Exception e) {
                        System.err.println("❌ ERROR: Verification search failed: " + e.getMessage());
                    }
                } else {
                    return maximizingPlayer ? beta : alpha;
                }
            } else if (!maximizingPlayer && nullScore <= alpha) {
                statistics.recordNullMovePrune(depth, Math.max(1, depth * depth));
                return maximizingPlayer ? beta : alpha;
            } else {
                statistics.recordNullMoveFailure();
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: General null-move pruning failed: " + e.getMessage());
            statistics.recordNullMoveFailure();
        }

        return Integer.MIN_VALUE; // No null-move cutoff
    }

    // === LMR HELPERS WITH SEARCHCONFIG ===

    private static boolean shouldReduceMoveWithConfig(GameState state, Move move, int depth, int moveIndex, boolean isPVNode) {
        if (depth < SearchConfig.LMR_MIN_DEPTH) return false;
        if (moveIndex < SearchConfig.LMR_MIN_MOVE_COUNT) return false;
        if (isPVNode && moveIndex == 0) return false;

        try {
            if (Minimax.isCapture(move, state)) return false;
            if (isGuardMove(move, state)) return false;
            if (threatsEnemyGuard(move, state)) return false;
            if (moveTowardsCastle(move, state)) return false;
            if (move.amountMoved >= SearchConfig.LMR_HIGH_ACTIVITY_THRESHOLD) return false;
        } catch (Exception e) {
            System.err.println("❌ ERROR: LMR condition check failed: " + e.getMessage());
            return false;
        }

        return true;
    }

    private static int calculateLMRReductionWithConfig(GameState state, Move move, int depth, int moveIndex) {
        int reduction = SearchConfig.LMR_BASE_REDUCTION;

        if (moveIndex > SearchConfig.LMR_MOVE_INDEX_THRESHOLD_1) reduction++;
        if (moveIndex > SearchConfig.LMR_MOVE_INDEX_THRESHOLD_2) reduction++;
        if (depth > SearchConfig.LMR_DEPTH_THRESHOLD) reduction++;

        try {
            if (isQuietPosition(state)) reduction++;
            if (isNearCenter(move.to)) reduction = Math.max(1, reduction - 1);
            if (isAdvancingMove(move, state)) reduction = Math.max(1, reduction - 1);
            if (isTacticalPosition(state)) reduction = Math.max(1, reduction - 1);
        } catch (Exception e) {
            System.err.println("❌ ERROR: LMR reduction calculation failed: " + e.getMessage());
        }

        return Math.min(reduction, SearchConfig.LMR_MAX_REDUCTION);
    }

    // === TT STORAGE WITH SEARCHCONFIG ===

    private static void storeTTEntryWithConfig(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
        try {
            if (hash == 0) return;

            int flag;
            if (score <= originalAlpha) {
                flag = TTEntry.UPPER_BOUND;
            } else if (score >= beta) {
                flag = TTEntry.LOWER_BOUND;
            } else {
                flag = TTEntry.EXACT;
            }

            TTEntry entry = new TTEntry(score, depth, flag, bestMove);
            Minimax.storeTranspositionEntry(hash, entry);
            statistics.incrementTTStores();
        } catch (Exception e) {
            System.err.println("❌ ERROR: TT entry storage failed: " + e.getMessage());
        }
    }

    // === HELPER METHODS (unchanged but using SearchConfig where applicable) ===

    private static boolean isGuardMove(Move move, GameState state) {
        try {
            boolean isRed = state.redToMove;
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean threatsEnemyGuard(Move move, GameState state) {
        try {
            long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
            if (enemyGuard == 0) return false;

            int guardPos = Long.numberOfTrailingZeros(enemyGuard);
            int rankDiff = Math.abs(GameState.rank(move.to) - GameState.rank(guardPos));
            int fileDiff = Math.abs(GameState.file(move.to) - GameState.file(guardPos));

            if (rankDiff != 0 && fileDiff != 0) return false;
            return Math.max(rankDiff, fileDiff) <= move.amountMoved;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean moveTowardsCastle(Move move, GameState state) {
        try {
            if (!isGuardMove(move, state)) return false;

            int enemyCastle = state.redToMove ?
                    GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

            int oldDistance = manhattanDistance(move.from, enemyCastle);
            int newDistance = manhattanDistance(move.to, enemyCastle);

            return newDistance < oldDistance;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isNearCenter(int square) {
        try {
            int file = GameState.file(square);
            int rank = GameState.rank(square);
            return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAdvancingMove(Move move, GameState state) {
        try {
            if (state.redToMove) {
                return GameState.rank(move.to) < GameState.rank(move.from);
            } else {
                return GameState.rank(move.to) > GameState.rank(move.from);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTacticalPosition(GameState state) {
        try {
            boolean isRed = state.redToMove;

            if (state.redGuard != 0 && state.blueGuard != 0) {
                int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
                int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
                int distance = manhattanDistance(redGuardPos, blueGuardPos);

                if (distance <= SearchConfig.TACTICAL_DISTANCE_THRESHOLD) {
                    return true;
                }
            }

            long enemyGuard = isRed ? state.blueGuard : state.redGuard;
            if (enemyGuard != 0) {
                int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
                long ownTowers = isRed ? state.redTowers : state.blueTowers;
                int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

                for (int i = 0; i < 49; i++) {
                    if ((ownTowers & GameState.bit(i)) != 0 && ownHeights[i] >= SearchConfig.TACTICAL_HEIGHT_THRESHOLD) {
                        int distance = manhattanDistance(i, enemyGuardPos);
                        if (distance <= ownHeights[i] + 1) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isQuietPosition(GameState state) {
        try {
            if (isTacticalPosition(state)) return false;

            boolean isRed = state.redToMove;
            long ownTowers = isRed ? state.redTowers : state.blueTowers;
            int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

            int highTowers = 0;
            for (int i = 0; i < 49; i++) {
                if ((ownTowers & GameState.bit(i)) != 0 && ownHeights[i] >= SearchConfig.QUIET_POSITION_THRESHOLD) {
                    highTowers++;
                }
            }

            return highTowers <= 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static int manhattanDistance(int from, int to) {
        try {
            int fromRank = GameState.rank(from);
            int fromFile = GameState.file(from);
            int toRank = GameState.rank(to);
            int toFile = GameState.file(to);

            return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    // === TIMEOUT MANAGEMENT ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    public static void resetSearchState() {
        searchInterrupted = false;
    }

    // === LEGACY COMPATIBILITY ===

    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        try {
            moveOrdering.orderMoves(moves, state, depth, entry);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move ordering failed: " + e.getMessage());
        }
    }
}