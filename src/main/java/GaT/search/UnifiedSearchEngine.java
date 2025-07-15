package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.Supplier;

/**
 * UNIFIED SEARCH ENGINE - FIXED für Ihre TranspositionTable
 */
public class UnifiedSearchEngine {

    private final Evaluator evaluator;
    private final FastMoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final UnifiedStatistics statistics;

    private volatile boolean searchInterrupted = false;
    private Supplier<Boolean> timeoutChecker = null;

    public UnifiedSearchEngine(Evaluator evaluator) {
        this.evaluator = evaluator;
        this.moveOrdering = new FastMoveOrdering();
        this.transpositionTable = new TranspositionTable(ConsolidatedSearchConfig.TT_SIZE);
        this.statistics = UnifiedStatistics.getInstance();
    }

    // === MAIN SEARCH INTERFACE (FIXED) ===

    public int search(GameState state, int depth, int alpha, int beta,
                      ConsolidatedSearchConfig.Strategy strategy) {
        if (state == null || depth <= 0) {
            return evaluator.evaluate(state);
        }

        switch (strategy) {
            case ALPHA_BETA:
                return alphaBetaSearch(state, depth, alpha, beta, true, false);
            case PVS:
                return principalVariationSearch(state, depth, alpha, beta, true, true);
            case PVS_QUIESCENCE:
                return principalVariationSearchWithQuiescence(state, depth, alpha, beta, true, true);
            default:
                return principalVariationSearchWithQuiescence(state, depth, alpha, beta, true, true);
        }
    }

    // === PVS WITH CORRECT TT CALLS ===

    public int principalVariationSearchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                                      boolean maximizing, boolean isPVNode) {
        if (isSearchInterrupted()) return GameValues.TIMEOUT_VALUE;

        statistics.incrementNodes();

        if (depth <= 0) {
            return quiescenceSearch(state, alpha, beta, maximizing, 0);
        }

        if (isTerminalPosition(state)) {
            return evaluateTerminalPosition(state, depth);
        }

        // === FIXED TT LOOKUP ===
        TTEntry ttEntry = transpositionTable.get(state.hash()); // FIXED: get() statt probe()
        if (ttEntry != null && ttEntry.depth >= depth && !isPVNode) {
            statistics.incrementTTHits();
            switch (ttEntry.flag) {
                case TTEntry.EXACT: return ttEntry.score;
                case TTEntry.LOWER_BOUND:
                    if (ttEntry.score >= beta) return ttEntry.score;
                    alpha = Math.max(alpha, ttEntry.score);
                    break;
                case TTEntry.UPPER_BOUND:
                    if (ttEntry.score <= alpha) return ttEntry.score;
                    beta = Math.min(beta, ttEntry.score);
                    break;
            }
            if (alpha >= beta) return ttEntry.score;
        } else {
            statistics.incrementTTMisses();
        }

        // Move generation und ordering
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluateTerminalPosition(state, depth);
        }

        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        // === PVS SEARCH LOOP ===
        int bestValue = maximizing ? GameValues.ALPHA_INIT : GameValues.BETA_INIT;
        Move bestMove = null;
        boolean firstMove = true;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            if (move == null) continue;

            GameState childState = makeMove(state, move);
            if (childState == null) continue;

            int value;

            if (firstMove) {
                value = -principalVariationSearchWithQuiescence(
                        childState, depth - 1, -beta, -alpha, !maximizing, isPVNode);
                firstMove = false;
            } else {
                int reduction = getLMRReduction(depth, i, move, state);
                int searchDepth = Math.max(1, depth - 1 - reduction);

                // NULL WINDOW SEARCH (PVS Optimization)
                value = -principalVariationSearchWithQuiescence(
                        childState, searchDepth, -alpha - 1, -alpha, !maximizing, false);

                if (value > alpha && value < beta && (reduction > 0 || !isPVNode)) {
                    value = -principalVariationSearchWithQuiescence(
                            childState, depth - 1, -beta, -alpha, !maximizing, isPVNode);
                }
            }

            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }

            if (maximizing) {
                alpha = Math.max(alpha, value);
            } else {
                beta = Math.min(beta, value);
            }

            if (alpha >= beta) {
                statistics.incrementAlphaBetaCutoffs();
                if (i == 0) statistics.incrementFirstMoveCutoffs();

                if (!isCapture(move, state)) {
                    moveOrdering.storeKillerMove(move, depth);
                    moveOrdering.updateHistory(move, depth, state);
                }
                break;
            }
        }

        // === FIXED TT STORAGE ===
        int flag = bestValue <= alpha ? TTEntry.UPPER_BOUND :
                bestValue >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
        TTEntry newEntry = new TTEntry(bestValue, depth, flag, bestMove); // FIXED: TTEntry erstellen
        transpositionTable.put(state.hash(), newEntry); // FIXED: put() statt store()

        return bestValue;
    }

    // === HELPER METHODS ===

    private boolean isSearchInterrupted() {
        if (searchInterrupted) return true;
        if (timeoutChecker != null && timeoutChecker.get()) {
            searchInterrupted = true;
            return true;
        }
        return false;
    }

    private boolean isTerminalPosition(GameState state) {
        return (state.redGuard == 0 || state.blueGuard == 0) ||
                ((state.redGuard & GameState.bit(ConsolidatedSearchConfig.BLUE_CASTLE_INDEX)) != 0) ||
                ((state.blueGuard & GameState.bit(ConsolidatedSearchConfig.RED_CASTLE_INDEX)) != 0);
    }

    private int evaluateTerminalPosition(GameState state, int depth) {
        if (state.redGuard == 0) return -GameValues.CHECKMATE_VALUE + depth;
        if (state.blueGuard == 0) return GameValues.CHECKMATE_VALUE - depth;

        long redCastle = GameState.bit(ConsolidatedSearchConfig.RED_CASTLE_INDEX);
        long blueCastle = GameState.bit(ConsolidatedSearchConfig.BLUE_CASTLE_INDEX);

        if ((state.redGuard & blueCastle) != 0) return GameValues.CHECKMATE_VALUE - depth;
        if ((state.blueGuard & redCastle) != 0) return -GameValues.CHECKMATE_VALUE + depth;

        return GameValues.DRAW_VALUE;
    }

    private GameState makeMove(GameState state, Move move) {
        try {
            GameState copy = state.copy();
            copy.applyMove(move);
            return copy.isValid() ? copy : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isCapture(Move move, GameState state) {
        if (move == null) return false;
        long toBit = GameState.bit(move.to);
        return ((state.redGuard | state.blueGuard) & toBit) != 0 ||
                state.redStackHeights[move.to] > 0 || state.blueStackHeights[move.to] > 0;
    }

    private int getLMRReduction(int depth, int moveIndex, Move move, GameState state) {
        if (isCapture(move, state)) return 0;
        return ConsolidatedSearchConfig.getLMRReduction(depth, moveIndex);
    }

    // === QUIESCENCE SEARCH ===

    private int quiescenceSearch(GameState state, int alpha, int beta, boolean maximizing, int qDepth) {
        if (qDepth >= ConsolidatedSearchConfig.MAX_QUIESCENCE_DEPTH || isSearchInterrupted()) {
            return evaluator.evaluate(state);
        }

        statistics.incrementQNodes();

        int standPat = evaluator.evaluate(state);

        if (maximizing) {
            if (standPat >= beta) {
                statistics.incrementStandPatCutoffs();
                return beta;
            }
            alpha = Math.max(alpha, standPat);
        } else {
            if (standPat <= alpha) {
                statistics.incrementStandPatCutoffs();
                return alpha;
            }
            beta = Math.min(beta, standPat);
        }

        List<Move> tacticalMoves = generateTacticalMoves(state);
        if (tacticalMoves.isEmpty()) return standPat;

        tacticalMoves.sort((a, b) -> Integer.compare(
                GameValues.getMVVLVAScore(state, b.from, b.to),
                GameValues.getMVVLVAScore(state, a.from, a.to)
        ));

        int bestValue = standPat;

        for (Move move : tacticalMoves) {
            GameState childState = makeMove(state, move);
            if (childState == null) continue;

            int value = quiescenceSearch(childState, alpha, beta, !maximizing, qDepth + 1);

            if (maximizing) {
                bestValue = Math.max(bestValue, value);
                alpha = Math.max(alpha, value);
                if (alpha >= beta) {
                    statistics.incrementQCutoffs();
                    break;
                }
            } else {
                bestValue = Math.min(bestValue, value);
                beta = Math.min(beta, value);
                if (alpha >= beta) {
                    statistics.incrementQCutoffs();
                    break;
                }
            }
        }

        return bestValue;
    }

    private List<Move> generateTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        return allMoves.stream()
                .filter(move -> isCapture(move, state))
                .collect(java.util.stream.Collectors.toList());
    }

    // === ALPHA-BETA (vereinfacht) ===

    private int alphaBetaSearch(GameState state, int depth, int alpha, int beta, boolean maximizing, boolean isPVNode) {
        if (depth <= 0) {
            return evaluator.evaluate(state);
        }

        statistics.incrementNodes();

        if (isTerminalPosition(state)) {
            return evaluateTerminalPosition(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) return evaluateTerminalPosition(state, depth);

        moveOrdering.orderMoves(moves, state, depth, null);

        int bestValue = maximizing ? GameValues.ALPHA_INIT : GameValues.BETA_INIT;

        for (Move move : moves) {
            GameState childState = makeMove(state, move);
            if (childState == null) continue;

            int value = alphaBetaSearch(childState, depth - 1, alpha, beta, !maximizing, false);

            if (maximizing) {
                bestValue = Math.max(bestValue, value);
                alpha = Math.max(alpha, value);
            } else {
                bestValue = Math.min(bestValue, value);
                beta = Math.min(beta, value);
            }

            if (alpha >= beta) {
                statistics.incrementAlphaBetaCutoffs();
                break;
            }
        }

        return bestValue;
    }

    private int principalVariationSearch(GameState state, int depth, int alpha, int beta, boolean maximizing, boolean isPVNode) {
        return principalVariationSearchWithQuiescence(state, depth, alpha, beta, maximizing, isPVNode);
    }

    // === PUBLIC INTERFACE ===

    public void setTimeoutChecker(Supplier<Boolean> checker) {
        this.timeoutChecker = checker;
    }

    public void abortSearch() {
        this.searchInterrupted = true;
    }

    public void resetForNewSearch() {
        this.searchInterrupted = false;
        this.moveOrdering.resetForNewSearch();
    }

    public UnifiedStatistics getStatistics() {
        return statistics;
    }

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    // === COMPATIBILITY ===

    public int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                    boolean maximizing, boolean isPVNode) {
        return principalVariationSearchWithQuiescence(state, depth, alpha, beta, maximizing, isPVNode);
    }

    public int quiesce(GameState state, int alpha, int beta, boolean maximizing, int qDepth) {
        return quiescenceSearch(state, alpha, beta, maximizing, qDepth);
    }
}