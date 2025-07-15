package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.GameConfig;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * PVS SEARCH - COMPLETE GAMECONFIG INTEGRATION
 */
public class PVSSearch {

    // === DEPENDENCIES ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final UnifiedStatistics statistics = UnifiedStatistics.getInstance();

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === SEARCH FUNCTION INTERFACE ===
    @FunctionalInterface
    private interface SearchFunction {
        int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean isPVNode);
    }

    // === MAIN PVS INTERFACE WITH GAMECONFIG ===

    /**
     * PVS with Quiescence using GameConfig parameters
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

        statistics.incrementRegularNode();

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
                statistics.incrementTTHit();

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
                statistics.incrementTTMiss();
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: TT lookup failed: " + e.getMessage());
        }

        // Null-Move Pruning using GameConfig
        if (GameConfig.NULL_MOVE_ENABLED) {
            int nullMoveResult = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer,
                    isPVNode, PVSSearch::searchWithQuiescence);
            if (nullMoveResult != Integer.MIN_VALUE) {
                return nullMoveResult;
            }
        }

        // Terminal conditions
        try {
            if (Minimax.isGameOver(state)) {
                statistics.incrementLeafNode();
                return Minimax.evaluate(state, depth);
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Game over check failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search when depth exhausted
        if (depth <= 0) {
            statistics.incrementQuiescenceNode();
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
     * Standard PVS without Quiescence
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

        statistics.incrementRegularNode();

        // Timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        // Terminal conditions
        try {
            if (depth == 0 || Minimax.isGameOver(state)) {
                statistics.incrementLeafNode();
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

    // === MAIN SEARCH LOGIC ===

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
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move generation failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }

        // Move ordering
        try {
            moveOrdering.orderMoves(moves, state, entry);
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

                int eval;
                try {
                    if (isFirstMove || isPVNode) {
                        eval = searchFunc.search(copy, depth - 1, alpha, beta, false, isPVNode);
                    } else {
                        // Null-window search
                        eval = searchFunc.search(copy, depth - 1, alpha, alpha + 1, false, false);
                        if (eval > alpha && isPVNode) {
                            eval = searchFunc.search(copy, depth - 1, alpha, beta, false, true);
                        }
                    }
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
                    statistics.incrementAlphaBetaCutoff();

                    // History update
                    try {
                        if (!Minimax.isCapture(move, state)) {
                            moveOrdering.storeKillerMove(move, depth);
                            moveOrdering.updateHistory(move, depth);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ ERROR: History update failed: " + e.getMessage());
                    }
                    break;
                }
            }

            // Store TT entry
            try {
                storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
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

                int eval;
                try {
                    if (isFirstMove || isPVNode) {
                        eval = searchFunc.search(copy, depth - 1, alpha, beta, true, isPVNode);
                    } else {
                        // Null-window search
                        eval = searchFunc.search(copy, depth - 1, beta - 1, beta, true, false);
                        if (eval < beta && isPVNode) {
                            eval = searchFunc.search(copy, depth - 1, alpha, beta, true, true);
                        }
                    }
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
                    statistics.incrementAlphaBetaCutoff();

                    // History update
                    try {
                        if (!Minimax.isCapture(move, state)) {
                            moveOrdering.storeKillerMove(move, depth);
                            moveOrdering.updateHistory(move, depth);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ ERROR: History update failed: " + e.getMessage());
                    }
                    break;
                }
            }

            // Store TT entry
            try {
                storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            } catch (Exception e) {
                System.err.println("❌ ERROR: TT store failed: " + e.getMessage());
            }

            return minEval;
        }
    }

    // === NULL-MOVE PRUNING ===

    private static int tryNullMovePruning(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, boolean isPVNode,
                                          SearchFunction searchFunc) {

        if (!GameConfig.NULL_MOVE_ENABLED) return Integer.MIN_VALUE;
        if (state == null || !state.isValid()) return Integer.MIN_VALUE;

        if (isPVNode || depth < GameConfig.NULL_MOVE_MIN_DEPTH) {
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

        statistics.incrementNullMoveAttempt();

        try {
            // Create null-move state
            GameState nullMoveState = state.copy();
            if (nullMoveState == null || !nullMoveState.isValid()) {
                return Integer.MIN_VALUE;
            }

            // Switch turn without making a move
            nullMoveState.redToMove = !nullMoveState.redToMove;

            int reduction = GameConfig.NULL_MOVE_REDUCTION;
            int nullDepth = Math.max(0, depth - 1 - reduction);

            int nullScore = searchFunc.search(nullMoveState, nullDepth, -beta, -alpha, !maximizingPlayer, false);

            // Null-move cutoff?
            if (maximizingPlayer && nullScore >= beta) {
                statistics.incrementNullMovePrune();
                return beta;
            } else if (!maximizingPlayer && nullScore <= alpha) {
                statistics.incrementNullMovePrune();
                return alpha;
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: General null-move pruning failed: " + e.getMessage());
        }

        return Integer.MIN_VALUE; // No null-move cutoff
    }

    // === TT STORAGE ===

    private static void storeTTEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
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
        } catch (Exception e) {
            System.err.println("❌ ERROR: TT entry storage failed: " + e.getMessage());
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
            moveOrdering.orderMoves(moves, state, entry);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move ordering failed: " + e.getMessage());
        }
    }
}