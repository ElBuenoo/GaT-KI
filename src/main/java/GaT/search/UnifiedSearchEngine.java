package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.Supplier;

/**
 * UNIFIED SEARCH ENGINE - COMPLETE AND FIXED
 *
 * All integration issues resolved:
 * ✅ Complete TT lookup implementation
 * ✅ Missing method implementations added
 * ✅ Proper imports and dependencies
 * ✅ Integration with FastMoveOrdering and UnifiedStatistics
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

    // === MAIN SEARCH INTERFACE ===

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

    // === PVS WITH COMPLETE IMPLEMENTATION ===

    public int principalVariationSearchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                                      boolean maximizing, boolean isPVNode) {
        if (isSearchInterrupted()) return GameValues.TIMEOUT_VALUE;

        statistics.incrementNodes();

        if (depth <= 0) {
            return quiescenceSearch(state, alpha, beta, maximizing, 0);
        }

        // Check for terminal position using TerminalPositionDetector
        TerminalPositionDetector.TerminalType terminal = TerminalPositionDetector.detectTerminal(state);
        if (terminal != TerminalPositionDetector.TerminalType.NOT_TERMINAL) {
            return TerminalPositionDetector.evaluateTerminal(terminal, depth);
        }

        // === COMPLETE TT LOOKUP (FIXED) ===
        TTEntry ttEntry = transpositionTable.get(state.hash()); // FIXED: was truncated
        if (ttEntry != null && ttEntry.depth >= depth && !isPVNode) {
            statistics.incrementTTHits();
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
            if (alpha >= beta) return ttEntry.score;
        } else {
            statistics.incrementTTMisses();
        }

        // Move generation and ordering
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            // No moves = terminal position
            return evaluateNoMovesPosition(state, depth, maximizing);
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
                // Full window search for first move
                value = -principalVariationSearchWithQuiescence(
                        childState, depth - 1, -beta, -alpha, !maximizing, isPVNode);
                firstMove = false;
            } else {
                // Late Move Reduction
                int reduction = getLMRReduction(depth, i, move, state);
                int searchDepth = Math.max(1, depth - 1 - reduction);

                // Null window search (PVS optimization)
                value = -principalVariationSearchWithQuiescence(
                        childState, searchDepth, -alpha - 1, -alpha, !maximizing, false);

                // Research if needed
                if (value > alpha && value < beta && (reduction > 0 || !isPVNode)) {
                    value = -principalVariationSearchWithQuiescence(
                            childState, depth - 1, -beta, -alpha, !maximizing, isPVNode);
                }
            }

            if (maximizing) {
                if (value > bestValue) {
                    bestValue = value;
                    bestMove = move;
                }
                alpha = Math.max(alpha, value);
            } else {
                if (value < bestValue) {
                    bestValue = value;
                    bestMove = move;
                }
                beta = Math.min(beta, value);
            }

            if (alpha >= beta) {
                statistics.incrementAlphaBetaCutoffs();
                if (i == 0) statistics.incrementFirstMoveCutoffs();

                // Store killer moves and history for non-captures
                if (!isCapture(move, state)) {
                    moveOrdering.storeKillerMove(move, depth);
                    moveOrdering.updateHistory(move, depth, state);
                }
                break;
            }
        }

        // === TT STORAGE ===
        int flag = bestValue <= alpha ? TTEntry.UPPER_BOUND :
                bestValue >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
        TTEntry newEntry = new TTEntry(bestValue, depth, flag, bestMove);
        transpositionTable.put(state.hash(), newEntry);

        return bestValue;
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

        // Generate only tactical moves (captures)
        List<Move> tacticalMoves = generateTacticalMoves(state);
        if (tacticalMoves.isEmpty()) return standPat;

        // Order tactical moves by MVV-LVA
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

    // === ALPHA-BETA SEARCH (simplified) ===

    private int alphaBetaSearch(GameState state, int depth, int alpha, int beta,
                                boolean maximizing, boolean isPVNode) {
        if (depth <= 0) {
            return evaluator.evaluate(state);
        }

        statistics.incrementNodes();

        // Terminal check
        TerminalPositionDetector.TerminalType terminal = TerminalPositionDetector.detectTerminal(state);
        if (terminal != TerminalPositionDetector.TerminalType.NOT_TERMINAL) {
            return TerminalPositionDetector.evaluateTerminal(terminal, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluateNoMovesPosition(state, depth, maximizing);
        }

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

    // === HELPER METHODS ===

    private boolean isSearchInterrupted() {
        if (searchInterrupted) return true;
        if (timeoutChecker != null && timeoutChecker.get()) {
            searchInterrupted = true;
            return true;
        }
        return false;
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
        return GameValues.isCapture(state, move.from, move.to);
    }

    private int getLMRReduction(int depth, int moveIndex, Move move, GameState state) {
        if (isCapture(move, state)) return 0;
        return ConsolidatedSearchConfig.getLMRReduction(depth, moveIndex);
    }

    private List<Move> generateTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        return allMoves.stream()
                .filter(move -> isCapture(move, state))
                .collect(java.util.stream.Collectors.toList());
    }

    private int evaluateNoMovesPosition(GameState state, int depth, boolean maximizing) {
        // No legal moves - could be stalemate or checkmate
        // In Turm & Wächter, no moves usually means losing
        return maximizing ? -GameValues.CHECKMATE_VALUE + depth :
                GameValues.CHECKMATE_VALUE - depth;
    }

    // === PVS ALIAS ===

    private int principalVariationSearch(GameState state, int depth, int alpha, int beta,
                                         boolean maximizing, boolean isPVNode) {
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

    // === COMPATIBILITY METHODS ===

    public int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                    boolean maximizing, boolean isPVNode) {
        return principalVariationSearchWithQuiescence(state, depth, alpha, beta, maximizing, isPVNode);
    }

    public int quiesce(GameState state, int alpha, int beta, boolean maximizing, int qDepth) {
        return quiescenceSearch(state, alpha, beta, maximizing, qDepth);
    }
}