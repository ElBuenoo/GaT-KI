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
 * ENHANCED SEARCH ENGINE - With Null Move Pruning and Late Move Reductions
 *
 * NEW FEATURES:
 * ✅ 1. Null Move Pruning for tactical depth
 * ✅ 2. Late Move Reductions for search efficiency
 * ✅ 3. Static Exchange Evaluation integration
 * ✅ 4. Enhanced threat detection
 */
public class SearchEngine {
    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final MoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final SearchStatistics statistics;

    // === SEARCH CONSTANTS ===
    private static final int CASTLE_REACH_SCORE = 2500;

    // === NULL MOVE PRUNING CONSTANTS ===
    private static final int NULL_MOVE_REDUCTION = 2;
    private static final int NULL_MOVE_VERIFICATION_REDUCTION = 1;

    // === LMR CONSTANTS ===
    private static final int[][] LMR_TABLE = new int[64][64]; // [depth][moveNumber]

    static {
        // Initialize LMR reduction table
        for (int depth = 1; depth < 64; depth++) {
            for (int moveNum = 1; moveNum < 64; moveNum++) {
                double reduction = 0.75 + Math.log(depth) * Math.log(moveNum) / 2.25;
                LMR_TABLE[depth][moveNum] = (int) Math.round(reduction);
            }
        }
    }

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;
    }

    // === MAIN SEARCH INTERFACE ===

    /**
     * Enhanced search with new tactical features
     */
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        if (strategy == null) {
            System.err.println("⚠️ Null strategy provided, defaulting to ALPHA_BETA");
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        try {
            return switch (strategy) {
                case ALPHA_BETA -> alphaBetaSearchEnhanced(state, depth, alpha, beta, maximizingPlayer, true, 0);
                case ALPHA_BETA_Q -> alphaBetaWithQuiescenceEnhanced(state, depth, alpha, beta, maximizingPlayer, true, 0);
                case PVS -> {
                    PVSSearch.setTimeoutChecker(timeoutChecker);
                    yield PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
                }
                case PVS_Q -> {
                    PVSSearch.setTimeoutChecker(timeoutChecker);
                    yield PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
                }
                default -> {
                    System.err.println("⚠️ Unknown strategy: " + strategy + ", using ALPHA_BETA");
                    yield alphaBetaSearchEnhanced(state, depth, alpha, beta, maximizingPlayer, true, 0);
                }
            };

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                System.out.println("⏱️ Search timeout - using current best");
                return evaluator.evaluate(state, depth);
            } else {
                System.err.printf("❌ Real search error with %s: %s%n", strategy, e.getMessage());
                return alphaBetaSearchEnhanced(state, depth, alpha, beta, maximizingPlayer, true, 0);
            }
        } finally {
            if (strategy == SearchConfig.SearchStrategy.PVS ||
                    strategy == SearchConfig.SearchStrategy.PVS_Q) {
                PVSSearch.clearTimeoutChecker();
            }
        }
    }

    // === ENHANCED ALPHA-BETA WITH NULL MOVE PRUNING & LMR ===

    private int alphaBetaSearchEnhanced(GameState state, int depth, int alpha, int beta,
                                        boolean maximizingPlayer, boolean allowNull, int ply) {
        statistics.incrementNodeCount();

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluator.evaluate(state, depth);
        }

        // Check for terminal positions
        if (depth == 0 || isGameOver(state)) {
            return evaluator.evaluate(state, depth);
        }

        // === NULL MOVE PRUNING ===
        if (allowNull && depth >= SearchConfig.NULL_MOVE_MIN_DEPTH && !isInCheck(state)) {
            if (canDoNullMove(state)) {
                // Make null move (just switch turns)
                GameState nullState = state.copy();
                nullState.redToMove = !nullState.redToMove;

                int reduction = NULL_MOVE_REDUCTION;
                if (depth > 6) reduction += 1; // Extra reduction for deeper searches

                int nullScore = -alphaBetaSearchEnhanced(nullState, depth - reduction - 1,
                        -beta, -beta + 1, !maximizingPlayer, false, ply + 1);

                if (nullScore >= beta) {
                    statistics.incrementNullMoveCutoffs();

                    // Verification search for high depths to avoid zugzwang
                    if (depth > 8) {
                        int verifyScore = alphaBetaSearchEnhanced(state, depth - NULL_MOVE_VERIFICATION_REDUCTION - 1,
                                beta - 1, beta, maximizingPlayer, false, ply + 1);
                        if (verifyScore >= beta) {
                            return beta;
                        }
                    } else {
                        return beta;
                    }
                }
            }
        }

        // Generate and order moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        TTEntry ttEntry = transpositionTable.get(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        if (moves.isEmpty()) {
            return evaluator.evaluate(state, depth);
        }

        int moveCount = 0;
        int quietMoveCount = 0;
        Move bestMove = null;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                moveCount++;
                boolean isQuiet = !isCapture(move, state) && !givesCheck(move, state);
                if (isQuiet) quietMoveCount++;

                // === FUTILITY PRUNING ===
                if (depth <= SearchConfig.FUTILITY_MAX_DEPTH && isQuiet && !isInCheck(state)) {
                    int futilityValue = evaluator.evaluate(state, 0) + SearchConfig.FUTILITY_MARGINS[depth];
                    if (futilityValue <= alpha) {
                        statistics.incrementFutilityCutoffs();
                        continue;
                    }
                }

                // === SEE PRUNING ===
                if (depth <= 3 && isCapture(move, state)) {
                    if (!StaticExchangeEvaluator.isSafeCapture(state, move)) {
                        continue; // Skip bad captures
                    }
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval;

                // === LATE MOVE REDUCTIONS ===
                if (depth >= SearchConfig.LMR_MIN_DEPTH &&
                        moveCount > SearchConfig.LMR_MIN_MOVE_COUNT &&
                        isQuiet && !isInCheck(copy)) {

                    int reduction = getLMRReduction(depth, moveCount, isQuiet);

                    // Reduced depth search
                    eval = alphaBetaSearchEnhanced(copy, depth - 1 - reduction, alpha, beta,
                            false, true, ply + 1);

                    // Re-search at full depth if it exceeds alpha
                    if (eval > alpha) {
                        statistics.incrementLMRReductions();
                        eval = alphaBetaSearchEnhanced(copy, depth - 1, alpha, beta,
                                false, true, ply + 1);
                    }
                } else {
                    // Normal search
                    eval = alphaBetaSearchEnhanced(copy, depth - 1, alpha, beta, false, true, ply + 1);
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // Update killer moves and history for quiet moves
                    if (isQuiet) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            // Store in transposition table
            if (bestMove != null) {
                int flag = maxEval >= beta ? TTEntry.LOWER_BOUND :
                        maxEval <= alpha ? TTEntry.UPPER_BOUND : TTEntry.EXACT;
                transpositionTable.put(state.hash(), new TTEntry(maxEval, depth, flag, bestMove));
            }

            return maxEval;

        } else {
            // Minimizing player (similar structure with inverted logic)
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                moveCount++;
                boolean isQuiet = !isCapture(move, state) && !givesCheck(move, state);
                if (isQuiet) quietMoveCount++;

                // === FUTILITY PRUNING ===
                if (depth <= SearchConfig.FUTILITY_MAX_DEPTH && isQuiet && !isInCheck(state)) {
                    int futilityValue = evaluator.evaluate(state, 0) - SearchConfig.FUTILITY_MARGINS[depth];
                    if (futilityValue >= beta) {
                        statistics.incrementFutilityCutoffs();
                        continue;
                    }
                }

                // === SEE PRUNING ===
                if (depth <= 3 && isCapture(move, state)) {
                    if (!StaticExchangeEvaluator.isSafeCapture(state, move)) {
                        continue;
                    }
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval;

                // === LATE MOVE REDUCTIONS ===
                if (depth >= SearchConfig.LMR_MIN_DEPTH &&
                        moveCount > SearchConfig.LMR_MIN_MOVE_COUNT &&
                        isQuiet && !isInCheck(copy)) {

                    int reduction = getLMRReduction(depth, moveCount, isQuiet);

                    eval = alphaBetaSearchEnhanced(copy, depth - 1 - reduction, alpha, beta,
                            true, true, ply + 1);

                    if (eval < beta) {
                        statistics.incrementLMRReductions();
                        eval = alphaBetaSearchEnhanced(copy, depth - 1, alpha, beta,
                                true, true, ply + 1);
                    }
                } else {
                    eval = alphaBetaSearchEnhanced(copy, depth - 1, alpha, beta, true, true, ply + 1);
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    if (isQuiet) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            // Store in transposition table
            if (bestMove != null) {
                int flag = minEval <= alpha ? TTEntry.UPPER_BOUND :
                        minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
                transpositionTable.put(state.hash(), new TTEntry(minEval, depth, flag, bestMove));
            }

            return minEval;
        }
    }

    private int alphaBetaWithQuiescenceEnhanced(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, boolean allowNull, int ply) {
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }
        return alphaBetaSearchEnhanced(state, depth, alpha, beta, maximizingPlayer, allowNull, ply);
    }

    // === HELPER METHODS ===

    /**
     * Check if we can do null move (have pieces to move)
     */
    private boolean canDoNullMove(GameState state) {
        // Don't do null move if we have very few pieces
        int pieceCount = 0;

        if (state.redToMove) {
            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                pieceCount += state.redStackHeights[i];
            }
            return pieceCount >= 3; // Need at least 3 tower pieces
        } else {
            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                pieceCount += state.blueStackHeights[i];
            }
            return pieceCount >= 3;
        }
    }

    /**
     * Check if current side is in check
     */
    private boolean isInCheck(GameState state) {
        return ThreatDetector.analyzeThreats(state).inCheck;
    }

    /**
     * Check if move gives check
     */
    private boolean givesCheck(Move move, GameState state) {
        GameState copy = state.copy();
        copy.applyMove(move);
        return ThreatDetector.analyzeThreats(copy).inCheck;
    }

    /**
     * Calculate LMR reduction amount
     */
    private int getLMRReduction(int depth, int moveCount, boolean isQuiet) {
        if (depth < 3 || moveCount < 4) return 0;

        // Use pre-calculated table
        int tableReduction = LMR_TABLE[Math.min(depth, 63)][Math.min(moveCount, 63)];

        // Adjust based on move characteristics
        int reduction = tableReduction;

        // Less reduction for captures that passed SEE
        if (!isQuiet) {
            reduction = reduction * 2 / 3;
        }

        // Ensure we don't reduce too much
        reduction = Math.min(reduction, depth - 2);

        return Math.max(0, reduction);
    }

    private boolean isGameOver(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return true;
        }

        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        return enemyGuard == 0 || ownGuard == 0;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === TIMEOUT MANAGEMENT ===

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    // === SEARCH OVERLOADS ===

    public int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.DEFAULT_STRATEGY);
    }

    public int searchWithTimeout(GameState state, int depth, int alpha, int beta,
                                 boolean maximizingPlayer, SearchConfig.SearchStrategy strategy,
                                 BooleanSupplier timeoutCheck) {
        this.timeoutChecker = timeoutCheck;
        try {
            return search(state, depth, alpha, beta, maximizingPlayer, strategy);
        } finally {
            this.timeoutChecker = null;
        }
    }

    // === EVALUATION DELEGATE ===

    public int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }
}