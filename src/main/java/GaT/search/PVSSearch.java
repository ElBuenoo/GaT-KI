package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.ThreatDetector;
import GaT.evaluation.StaticExchangeEvaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * COMPLETE PRINCIPAL VARIATION SEARCH - All Methods Implemented
 *
 * FEATURES:
 * ✅ Full PVS implementation with null window search
 * ✅ Quiescence search integration
 * ✅ Null move pruning
 * ✅ Late move reductions
 * ✅ SEE-based pruning
 * ✅ Proper timeout handling
 */
public class PVSSearch {

    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === NULL MOVE CONSTANTS ===
    private static final int NULL_MOVE_REDUCTION = 2;

    // === LMR TABLE ===
    private static final int[][] LMR_TABLE = new int[64][64];

    static {
        // Initialize LMR reduction table
        for (int depth = 1; depth < 64; depth++) {
            for (int moveNum = 1; moveNum < 64; moveNum++) {
                double reduction = 0.75 + Math.log(depth) * Math.log(moveNum) / 2.25;
                LMR_TABLE[depth][moveNum] = (int) Math.round(reduction);
            }
        }
    }

    // === MAIN PVS INTERFACES ===

    /**
     * Standard PVS search
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount();

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // Terminal node check
        if (depth <= 0 || isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // Transposition table lookup
        long hash = state.hash();
        TTEntry ttEntry = Minimax.getTranspositionEntry(hash);
        Move ttMove = null;

        if (ttEntry != null && ttEntry.depth >= depth) {
            ttMove = ttEntry.bestMove;

            if (!isPVNode) {
                switch (ttEntry.flag) {
                    case TTEntry.EXACT:
                        return ttEntry.score;
                    case TTEntry.LOWER_BOUND:
                        if (ttEntry.score >= beta) return ttEntry.score;
                        alpha = Math.max(alpha, ttEntry.score);
                        break;
                    case TTEntry.UPPER_BOUND:
                        if (ttEntry.score <= alpha) return ttEntry.score;
                        beta = Math.min(beta, ttEntry.score);
                        break;
                }
            }
        }

        int originalAlpha = alpha;

        // === NULL MOVE PRUNING ===
        if (!isPVNode && depth >= SearchConfig.NULL_MOVE_MIN_DEPTH &&
                !isInCheck(state) && canDoNullMove(state)) {

            GameState nullState = createNullMoveState(state);
            int nullScore = -search(nullState, depth - 1 - NULL_MOVE_REDUCTION,
                    -beta, -beta + 1, !maximizingPlayer, false);

            if (nullScore >= beta) {
                statistics.incrementNullMoveCutoffs();
                return nullScore;
            }
        }

        // Generate and order moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return Minimax.evaluate(state, depth);
        }

        orderMovesForPV(moves, state, depth, ttEntry);

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        Move bestMove = null;
        boolean isFirstMove = true;
        int moveCount = 0;

        for (Move move : moves) {
            if (searchInterrupted || (timeoutChecker != null && timeoutChecker.getAsBoolean())) {
                break;
            }

            moveCount++;
            boolean isQuiet = !isCapture(move, state) && !givesCheck(move, state);

            // === SEE PRUNING ===
            if (!isPVNode && depth <= 3 && isCapture(move, state)) {
                if (!StaticExchangeEvaluator.isSafeCapture(state, move)) {
                    continue;
                }
            }

            // === FUTILITY PRUNING ===
            if (!isPVNode && depth <= SearchConfig.FUTILITY_MAX_DEPTH && isQuiet && !isInCheck(state)) {
                int futilityValue = Minimax.evaluate(state, 0);
                int futilityMargin = SearchConfig.FUTILITY_MARGINS[Math.min(depth, SearchConfig.FUTILITY_MARGINS.length - 1)];

                if (maximizingPlayer) {
                    if (futilityValue + futilityMargin <= alpha) {
                        statistics.incrementFutilityCutoffs();
                        continue;
                    }
                } else {
                    if (futilityValue - futilityMargin >= beta) {
                        statistics.incrementFutilityCutoffs();
                        continue;
                    }
                }
            }

            GameState copy = state.copy();
            copy.applyMove(move);
            statistics.addMovesSearched(1);

            int score;

            if (isFirstMove || isPVNode) {
                // Full window search for first move and PV nodes
                score = -search(copy, depth - 1, -beta, -alpha, !maximizingPlayer, isPVNode);
                isFirstMove = false;
            } else {
                // === LATE MOVE REDUCTIONS ===
                int reduction = 0;
                if (depth >= SearchConfig.LMR_MIN_DEPTH &&
                        moveCount > SearchConfig.LMR_MIN_MOVE_COUNT &&
                        isQuiet && !isPVNode) {

                    reduction = getLMRReduction(depth, moveCount, isQuiet);
                }

                // Null window search (with possible reduction)
                score = -search(copy, depth - 1 - reduction, -alpha - 1, -alpha, !maximizingPlayer, false);

                // Re-search conditions
                if (score > alpha && score < beta) {
                    statistics.incrementLMRReductions();
                    score = -search(copy, depth - 1, -beta, -alpha, !maximizingPlayer, true);
                } else if (reduction > 0 && score > alpha) {
                    // Re-search at full depth if LMR was applied and move is good
                    score = -search(copy, depth - 1, -beta, -alpha, !maximizingPlayer, false);
                }
            }

            if (maximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth, depth * depth);
                    }
                    break;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth, depth * depth);
                    }
                    break;
                }
            }
        }

        // Store in transposition table
        if (bestMove != null) {
            storeTTEntry(hash, bestScore, depth, originalAlpha, beta, bestMove);
        }

        return bestScore;
    }

    /**
     * PVS with Quiescence search
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        return search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
    }

    // === HELPER METHODS ===

    /**
     * Check if position is game over
     */
    private static boolean isGameOver(GameState state) {
        // Check for captured guards
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Check for castle reach
        if ((state.redGuard & GameState.bit(GameState.getIndex(0, 3))) != 0) {
            return true; // Red reached blue castle
        }
        if ((state.blueGuard & GameState.bit(GameState.getIndex(6, 3))) != 0) {
            return true; // Blue reached red castle
        }

        // Check for no legal moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        return moves.isEmpty();
    }

    /**
     * Check if position is in check
     */
    private static boolean isInCheck(GameState state) {
        try {
            ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
            return threats.inCheck;
        } catch (Exception e) {
            return false; // Safe fallback
        }
    }

    /**
     * Check if null move is safe
     */
    private static boolean canDoNullMove(GameState state) {
        int pieceCount = 0;

        if (state.redToMove) {
            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                pieceCount += state.redStackHeights[i];
            }
            return pieceCount >= 3;
        } else {
            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                pieceCount += state.blueStackHeights[i];
            }
            return pieceCount >= 3;
        }
    }

    /**
     * Create null move state
     */
    private static GameState createNullMoveState(GameState state) {
        GameState nullState = state.copy();
        nullState.redToMove = !nullState.redToMove;
        return nullState;
    }

    /**
     * Check if move is a capture
     */
    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move gives check
     */
    private static boolean givesCheck(Move move, GameState state) {
        try {
            GameState copy = state.copy();
            copy.applyMove(move);
            return ThreatDetector.analyzeThreats(copy).inCheck;
        } catch (Exception e) {
            return false; // Safe fallback
        }
    }

    /**
     * Get LMR reduction amount
     */
    private static int getLMRReduction(int depth, int moveCount, boolean isQuiet) {
        if (depth < 3 || moveCount < 4) return 0;

        int tableReduction = LMR_TABLE[Math.min(depth, 63)][Math.min(moveCount, 63)];

        int reduction = tableReduction;

        if (!isQuiet) {
            reduction = reduction * 2 / 3;
        }

        reduction = Math.min(reduction, depth - 2);

        return Math.max(0, reduction);
    }

    /**
     * Order moves for PV search
     */
    private static void orderMovesForPV(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // Put TT move first
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // Sort remaining moves
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveForPV(state, a, depth);
                int scoreB = scoreMoveForPV(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Score move for PV ordering
     */
    private static int scoreMoveForPV(GameState state, Move move, int depth) {
        int score = 0;

        // Captures first
        if (isCapture(move, state)) {
            score += 10000;

            // Guard captures highest
            long toBit = GameState.bit(move.to);
            if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
                score += 5000;
            } else {
                // SEE value for other captures
                try {
                    score += StaticExchangeEvaluator.evaluate(state, move);
                } catch (Exception e) {
                    score += 100; // Safe fallback
                }
            }
        }

        // Positional factors for quiet moves
        if (!isCapture(move, state)) {
            // Central squares bonus
            int rank = GameState.rank(move.to);
            int file = GameState.file(move.to);
            if (rank >= 2 && rank <= 4 && file >= 2 && file <= 4) {
                score += 50;
            }

            // Forward movement bonus
            if (state.redToMove && GameState.rank(move.to) < GameState.rank(move.from)) {
                score += 30;
            } else if (!state.redToMove && GameState.rank(move.to) > GameState.rank(move.from)) {
                score += 30;
            }

            // Castle approach bonus
            boolean isRed = state.redToMove;
            int enemyCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
            int currentDist = Math.abs(GameState.rank(move.from) - GameState.rank(enemyCastle)) +
                    Math.abs(GameState.file(move.from) - GameState.file(enemyCastle));
            int newDist = Math.abs(GameState.rank(move.to) - GameState.rank(enemyCastle)) +
                    Math.abs(GameState.file(move.to) - GameState.file(enemyCastle));

            if (newDist < currentDist) {
                score += 20;
            }
        }

        return score;
    }

    /**
     * Store entry in transposition table
     */
    private static void storeTTEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
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
    }

    // === TIMEOUT MANAGEMENT ===

    /**
     * Set timeout checker from TimedMinimax
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    /**
     * Clear timeout checker
     */
    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    /**
     * Reset search state for clean start
     */
    public static void resetSearchState() {
        searchInterrupted = false;
    }

    /**
     * Check if search was interrupted
     */
    public static boolean isSearchInterrupted() {
        return searchInterrupted;
    }

    // === LEGACY COMPATIBILITY ===

    /**
     * Legacy method for backward compatibility
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesForPV(moves, state, depth, entry);
    }

    /**
     * Get current search statistics
     */
    public static SearchStatistics getStatistics() {
        return statistics;
    }

    /**
     * Reset statistics
     */
    public static void resetStatistics() {
        statistics.reset();
    }
}