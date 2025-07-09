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
 * ENHANCED SEARCH ENGINE - COMPLETE FIXED IMPLEMENTATION
 * With Null Move Pruning, Late Move Reductions, and proper integration
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
            // Handle timeout vs real errors
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Timeout") ||
                            e.getMessage().contains("timeout") ||
                            e instanceof RuntimeException && e.getMessage().contains("Timeout"))) {

                System.out.println("⏱️ Search timeout - using current best");
                return evaluator.evaluate(state, depth);

            } else {
                System.err.printf("❌ Real search error with %s: %s%n", strategy, e.getMessage());
                if (strategy == SearchConfig.SearchStrategy.PVS_Q ||
                        strategy == SearchConfig.SearchStrategy.PVS) {
                    System.err.println("🔄 PVS failed, falling back to alpha-beta");
                }
                return alphaBetaSearchEnhanced(state, depth, alpha, beta, maximizingPlayer, true, 0);
            }

        } finally {
            // Enhanced cleanup
            if (strategy == SearchConfig.SearchStrategy.PVS ||
                    strategy == SearchConfig.SearchStrategy.PVS_Q) {
                PVSSearch.clearTimeoutChecker();
            }
        }
    }

    // === ENHANCED ALPHA-BETA WITH NULL MOVE PRUNING & LMR ===

    /**
     * Enhanced Alpha-Beta with tactical improvements
     */
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

                int nullScore = -alphaBetaSearchEnhanced(nullState, depth - 1 - NULL_MOVE_REDUCTION,
                        -beta, -beta + 1, !maximizingPlayer, false, ply + 1);

                if ((maximizingPlayer && nullScore >= beta) || (!maximizingPlayer && nullScore <= alpha)) {
                    statistics.incrementNullMoveCutoffs();
                    return nullScore;
                }
            }
        }

        // Generate moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluator.evaluate(state, depth); // Stalemate
        }

        // Move ordering
        TTEntry ttEntry = transpositionTable.get(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        Move bestMove = null;

        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            Move move = moves.get(moveIndex);

            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }

            GameState copy = state.copy();
            copy.applyMove(move);

            int score;
            boolean needsFullSearch = true;

            // === LATE MOVE REDUCTIONS ===
            if (depth >= SearchConfig.LMR_MIN_DEPTH &&
                    moveIndex >= SearchConfig.LMR_MIN_MOVE_COUNT &&
                    !isCapture(move, state) && !givesCheck(move, state)) {

                int reduction = getLMRReduction(depth, moveIndex);
                score = -alphaBetaSearchEnhanced(copy, depth - 1 - reduction, -beta, -alpha,
                        !maximizingPlayer, true, ply + 1);

                // Re-search if LMR move is good
                if (score > alpha && score < beta) {
                    needsFullSearch = true;
                } else {
                    needsFullSearch = false;
                }
            }

            // Full search
            if (needsFullSearch) {
                score = -alphaBetaSearchEnhanced(copy, depth - 1, -beta, -alpha,
                        !maximizingPlayer, true, ply + 1);
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
                    }
                    break;
                }
            }
        }

        // Store in transposition table
        if (bestMove != null) {
            TTEntry entry = new TTEntry(bestScore, depth, bestMove,
                    bestScore <= alpha ? TTEntry.UPPER_BOUND :
                            bestScore >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT);
            transpositionTable.put(state.hash(), entry);
        }

        return bestScore;
    }

    /**
     * Enhanced Alpha-Beta with Quiescence
     */
    private int alphaBetaWithQuiescenceEnhanced(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, boolean allowNull, int ply) {
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }
        return alphaBetaSearchEnhanced(state, depth, alpha, beta, maximizingPlayer, allowNull, ply);
    }

    // === BASIC ALPHA-BETA (FALLBACK) ===

    /**
     * Basic alpha-beta search (fallback implementation)
     */
    private int alphaBetaSearch(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        statistics.incrementNodeCount();

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluator.evaluate(state, depth);
        }

        if (depth == 0 || isGameOver(state)) {
            return evaluator.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        moveOrdering.orderMoves(moves, state, depth, null);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearch(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearch(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Alpha-beta with quiescence fallback
     */
    private int alphaBetaWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }
        return alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
    }

    // === HELPER METHODS ===

    /**
     * Check if game is over
     */
    private boolean isGameOver(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return true;
        }

        // Check for guard captures/castle reaches
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        return enemyGuard == 0 || ownGuard == 0;
    }

    /**
     * Check if position is in check (simplified)
     */
    private boolean isInCheck(GameState state) {
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        return threats.inCheck;
    }

    /**
     * Check if null move is safe
     */
    private boolean canDoNullMove(GameState state) {
        // Don't null move in endgame or when few pieces
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
     * Check if move is a capture
     */
    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move gives check (simplified)
     */
    private boolean givesCheck(Move move, GameState state) {
        GameState copy = state.copy();
        copy.applyMove(move);
        return ThreatDetector.analyzeThreats(copy).inCheck;
    }

    /**
     * Get LMR reduction amount
     */
    private int getLMRReduction(int depth, int moveIndex) {
        if (depth < 3 || moveIndex < 4) return 0;

        int tableReduction = LMR_TABLE[Math.min(depth, 63)][Math.min(moveIndex, 63)];
        return Math.min(tableReduction, depth - 2);
    }

    // === TIMEOUT MANAGEMENT ===

    /**
     * Set timeout checker
     */
    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    /**
     * Clear timeout checker
     */
    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    // === SEARCH OVERLOADS ===

    /**
     * Search with default strategy
     */
    public int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.DEFAULT_STRATEGY);
    }

    /**
     * Search with timeout
     */
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

    /**
     * Direct evaluation access
     */
    public int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    // === COMPONENT ACCESS ===

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }
}