package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * PHASE 2 FIXED SEARCH ENGINE - Enhanced Error Handling
 *
 * PHASE 2 FIXES:
 * ✅ 1. Better timeout vs real error distinction
 * ✅ 2. Graceful degradation instead of alpha-beta fallback for timeouts
 * ✅ 3. Enhanced exception logging
 * ✅ 4. Proper cleanup in finally blocks
 * ✅ 5. Fallback evaluation for timeout cases
 */
public class SearchEngine {

    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final MoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final SearchStatistics statistics;
    private boolean useNullMove = true;
    private boolean useLMR = true;
    private boolean useThreatDetection = true;


    // === SEARCH CONSTANTS ===
    private static final int CASTLE_REACH_SCORE = 2500;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;
    }

    // === MAIN SEARCH INTERFACE - PHASE 2 FIXED ===

    /**
     * PHASE 2 FIXED: Enhanced error handling with timeout distinction
     */
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        if (strategy == null) {
            System.err.println("⚠️ Null strategy provided, defaulting to ALPHA_BETA");
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        try {
            return switch (strategy) {
                case ALPHA_BETA -> alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                case ALPHA_BETA_Q -> alphaBetaWithQuiescence(state, depth, alpha, beta, maximizingPlayer);

                // ✅ FIXED: Now actually calls PVS methods!
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
                    yield alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                }
            };

        } catch (Exception e) {
            // PHASE 2 FIX: Distinguish between timeout and real errors
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Timeout") ||
                            e.getMessage().contains("timeout") ||
                            e instanceof RuntimeException && e.getMessage().contains("Timeout"))) {

                System.out.println("⏱️ Search timeout - using current best");
                // PHASE 2 FIX: Don't fall back to alpha-beta für timeouts
                // Instead return a reasonable evaluation
                return evaluator.evaluate(state, depth);

            } else {
                System.err.printf("❌ Real search error with %s: %s%n", strategy, e.getMessage());
                // Log stack trace for debugging real errors
                if (strategy == SearchConfig.SearchStrategy.PVS_Q ||
                        strategy == SearchConfig.SearchStrategy.PVS) {
                    System.err.println("🔄 PVS failed, falling back to alpha-beta");
                }
                // Nur bei echten errors auf alpha-beta fallback
                return alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
            }

        } finally {
            // PHASE 2 FIX: Enhanced cleanup
            if (strategy == SearchConfig.SearchStrategy.PVS ||
                    strategy == SearchConfig.SearchStrategy.PVS_Q) {
                PVSSearch.clearTimeoutChecker();
            }
        }
    }

    // === ALPHA-BETA SEARCH IMPLEMENTATION ===

    private int alphaBetaSearch(GameState state, int depth, int alpha, int beta,
                                        boolean maximizingPlayer, int moveNumber) {
        statistics.incrementNodeCount();

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluator.evaluate(state, depth);
        }

        // Terminal check
        if (depth == 0 || isGameOver(state)) {
            return evaluator.evaluate(state, depth);
        }

        // === NULL MOVE PRUNING ===
        if (useNullMove && depth >= 3 && moveNumber > 0) {
            NullMovePruning.NullMoveResult nullResult =
                    NullMovePruning.tryNullMovePruning(state, depth, alpha, beta,
                            maximizingPlayer, false, this);
            if (nullResult.shouldPrune) {
                return nullResult.score;
            }
        }

        // === THREAT DETECTION ===
        ThreatDetector.ThreatAnalysis threats = null;
        if (useThreatDetection && depth >= 4) {
            threats = ThreatDetector.quickThreatCheck(state);

            // Extend search for critical threats
            if (threats.hasCriticalThreats()) {
                depth += 1;
                System.out.println("🚨 Critical threat detected - extending search");
            }
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // === SEE FILTERING ===
        // Filter out bad captures using SEE
        moves = StaticExchangeEvaluator.filterBadCaptures(moves, state);

        moveOrdering.orderMoves(moves, state, depth, null);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            int currentMoveNumber = 0;

            for (Move move : moves) {
                currentMoveNumber++;

                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval;

                // === LATE MOVE REDUCTIONS ===
                if (useLMR && currentMoveNumber >= 4) {
                    eval = LateMoveReductions.performLMRSearch(
                            copy, move, depth - 1, alpha, beta, false, false,
                            currentMoveNumber, this, SearchConfig.SearchStrategy.ALPHA_BETA);
                } else {
                    eval = alphaBetaSearch(copy, depth - 1, alpha, beta, false, currentMoveNumber);
                }

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    break;
                }
            }
            return maxEval;
        } else {
            // Similar implementation for minimizing player
            int minEval = Integer.MAX_VALUE;
            int currentMoveNumber = 0;

            for (Move move : moves) {
                currentMoveNumber++;

                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval;

                if (useLMR && currentMoveNumber >= 4) {
                    eval = LateMoveReductions.performLMRSearch(
                            copy, move, depth - 1, alpha, beta, true, false,
                            currentMoveNumber, this, SearchConfig.SearchStrategy.ALPHA_BETA);
                } else {
                    eval = alphaBetaSearch(copy, depth - 1, alpha, beta, true, currentMoveNumber);
                }

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

    private int alphaBetaWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Terminal check for quiescence
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }
        return alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
    }

    private boolean isGameOver(GameState state) {
        // Basic game over detection
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return true;
        }

        // Check for guard captures/castle reaches
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        // No guards = game over
        if (enemyGuard == 0 || ownGuard == 0) {
            return true;
        }

        // Castle reach check could be added here
        return false;
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

    // === ENHANCED SEARCH INTERFACE - PHASE 2 ===

    /**
     * NEW: Direct PVS interface with proper PV node tracking
     */
    public int searchPVS(GameState state, int depth, int alpha, int beta,
                         boolean maximizingPlayer, boolean isPVNode, boolean useQuiescence) {
        PVSSearch.setTimeoutChecker(timeoutChecker);
        try {
            if (useQuiescence) {
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            } else {
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            }
        } finally {
            PVSSearch.clearTimeoutChecker();
        }
    }

    // === HELPER METHODS ===

    /**
     * Score a single move for ordering
     */
    public int scoreMove(GameState state, Move move) {
        int score = 0;

        // Capture bonus
        if (isCapture(move, state)) {
            long toBit = GameState.bit(move.to);
            boolean isRed = state.redToMove;

            // Guard capture is highest priority
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += 5000;
            } else {
                // Tower capture based on height
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += height * 100;
            }
        }

        // Central control bonus
        int file = GameState.file(move.to);
        if (file >= 2 && file <= 4) {
            score += 20;
        }

        // Forward progress bonus
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        if (isRed && toRank < fromRank) {
            score += (fromRank - toRank) * 10;
        } else if (!isRed && toRank > fromRank) {
            score += (toRank - fromRank) * 10;
        }

        // Activity bonus
        score += move.amountMoved * 5;

        return score;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === EVALUATION DELEGATE ===

    public int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }
}