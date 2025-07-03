package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Principal Variation Search (PVS) Implementation
 * Optimized version of Alpha-Beta that searches with null windows
 */
public class PVSSearch {

    // Timeout support
    private static volatile BooleanSupplier timeoutChecker = null;

    // Dependencies (passed from SearchEngine)
    private static Evaluator evaluator;
    private static TranspositionTable transpositionTable;
    private static MoveOrdering moveOrdering;
    private static SearchStatistics statistics;

    /**
     * Initialize dependencies (called by SearchEngine)
     */
    public static void initialize(Evaluator eval, TranspositionTable tt,
                                  MoveOrdering mo, SearchStatistics stats) {
        evaluator = eval;
        transpositionTable = tt;
        moveOrdering = mo;
        statistics = stats;
    }

    /**
     * PVS without quiescence
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {
        return pvsInternal(state, depth, alpha, beta, maximizingPlayer, isPVNode, false);
    }

    /**
     * PVS with quiescence search at leaf nodes
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {
        return pvsInternal(state, depth, alpha, beta, maximizingPlayer, isPVNode, true);
    }

    /**
     * Internal PVS implementation
     */
    private static int pvsInternal(GameState state, int depth, int alpha, int beta,
                                   boolean maximizingPlayer, boolean isPVNode, boolean useQuiescence) {
        statistics.incrementNodeCount();

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // Transposition table lookup
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth && !isPVNode) {
            statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        } else if (entry == null) {
            statistics.incrementTTMisses();
        }

        // Terminal node check
        if (isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(state, depth);
        }

        // Leaf node - use quiescence or static eval
        if (depth <= 0) {
            statistics.incrementLeafNodeCount();
            if (useQuiescence) {
                return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
            } else {
                return evaluator.evaluate(state, depth);
            }
        }

        // Move generation
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluator.evaluate(state, depth); // Stalemate or similar
        }

        statistics.addMovesGenerated(moves.size());
        statistics.addBranchingFactor(moves.size());

        // Move ordering
        moveOrdering.orderMoves(moves, state, depth, entry);

        // PVS Search
        Move bestMove = null;
        int originalAlpha = alpha;
        boolean searchPv = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);

                int eval;

                if (searchPv) {
                    // First move or PV node - search with full window
                    eval = -pvsInternal(copy, depth - 1, -beta, -alpha, false, isPVNode, useQuiescence);
                } else {
                    // Null window search
                    eval = -pvsInternal(copy, depth - 1, -alpha - 1, -alpha, false, false, useQuiescence);

                    // Re-search if it fails high
                    if (eval > alpha && eval < beta) {
                        eval = -pvsInternal(copy, depth - 1, -beta, -alpha, false, false, useQuiescence);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;

                    if (isPVNode) {
                        moveOrdering.storePVMove(move, depth);
                    }
                }

                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }

                searchPv = false; // After first move, use null window
            }

            // Store in transposition table
            storeTranspositionEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);

                int eval;

                if (searchPv) {
                    eval = -pvsInternal(copy, depth - 1, -beta, -alpha, true, isPVNode, useQuiescence);
                } else {
                    eval = -pvsInternal(copy, depth - 1, -alpha - 1, -alpha, true, false, useQuiescence);

                    if (eval > alpha && eval < beta) {
                        eval = -pvsInternal(copy, depth - 1, -beta, -alpha, true, false, useQuiescence);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;

                    if (isPVNode) {
                        moveOrdering.storePVMove(move, depth);
                    }
                }

                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }

                searchPv = false;
            }

            storeTranspositionEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    // === HELPER METHODS ===

    private static boolean isGameOver(GameState state) {
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
        int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);

        return redGuardPos == GameState.getIndex(0, 3) ||
                blueGuardPos == GameState.getIndex(6, 3);
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static void storeTranspositionEntry(long hash, int score, int depth,
                                                int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        transpositionTable.put(hash, entry);
        statistics.incrementTTStores();
    }

    // === TIMEOUT MANAGEMENT ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }
}