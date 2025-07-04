package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;
import java.util.List;

public class PVSStrategy implements ISearchStrategy {

    protected final SearchStatistics statistics;
    protected final QuiescenceSearch quiescenceSearch;
    protected final Evaluator evaluator;

    private static final int ASPIRATION_WINDOW = 50;

    public PVSStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        this.statistics = statistics;
        this.quiescenceSearch = quiescenceSearch;
        this.evaluator = evaluator;
    }

    @Override
    public SearchResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        int alpha = context.alpha;
        int beta = context.beta;

        if (context.depth > 3) {
            TTEntry entry = context.ttable.get(context.state.hash());
            if (entry != null && entry.flag == TTEntry.EXACT) {
                alpha = Math.max(context.alpha, entry.score - ASPIRATION_WINDOW);
                beta = Math.min(context.beta, entry.score + ASPIRATION_WINDOW);
            }
        }

        try {
            return performPVS(context.withWindow(alpha, beta), startTime);
        } catch (AspirationFailException e) {
            System.out.println("Aspiration fail - researching with full window");
            statistics.reset();
            return performPVS(context, startTime);
        }
    }

    private SearchResult performPVS(SearchContext context, long startTime) {
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            int score = evaluator.evaluate(context.state, 0);
            return new SearchResult(null, score, 0, statistics.getNodeCount());
        }

        TTEntry ttEntry = context.ttable.get(context.state.hash());
        context.moveOrdering.orderMoves(moves, context.state, context.depth, ttEntry);

        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE + 1;
        boolean firstMove = true;
        int alpha = context.alpha;
        int beta = context.beta;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            int score;

            if (firstMove) {
                SearchContext childContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-beta, -alpha);
                score = -pvSearch(childContext, true);
                firstMove = false;
            } else {
                SearchContext nullContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-alpha - 1, -alpha);
                score = -pvSearch(nullContext, false);

                if (score > alpha && score < beta) {
                    SearchContext fullContext = context.withNewState(newState, context.depth - 1)
                            .withWindow(-beta, -alpha);
                    score = -pvSearch(fullContext, false);
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                statistics.incrementAlphaBetaCutoffs();
                break;
            }
        }

        if (bestScore <= context.alpha || bestScore >= context.beta) {
            throw new AspirationFailException();
        }

        storeInTT(context, context.state.hash(), bestScore, context.alpha, beta, bestMove);

        long timeMs = System.currentTimeMillis() - startTime;
        return new SearchResult(bestMove, bestScore, context.depth,
                statistics.getNodeCount(), timeMs, false);
    }

    protected int pvSearch(SearchContext context, boolean isPVNode) {
        statistics.incrementNodeCount();

        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        if (context.depth <= 0 || GameRules.isGameOver(context.state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(context.state, context.depth);
        }

        // TT probe
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            statistics.incrementTTHits();

            if (entry.flag == TTEntry.EXACT && (!isPVNode || context.depth <= 2)) {
                return entry.score;
            }

            if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= context.beta) {
                    return entry.score;
                }
                if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= context.alpha) {
                    return entry.score;
                }
            }
        } else {
            statistics.incrementTTMisses();
        }

        // ✅ === NULL MOVE PRUNING für PVS - KORRIGIERT ===
        if (!isPVNode && canDoNullMove(context)) {
            statistics.incrementNullMoveAttempts();
            int nullMoveScore = doNullMoveSearch(context);
            if (nullMoveScore >= context.beta) {
                statistics.incrementNullMoveCutoffs();
                return context.beta;
            }
        }

        // Move generation
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        statistics.addMovesGenerated(moves.size());

        if (moves.isEmpty()) {
            return evaluator.evaluate(context.state, context.depth);
        }

        context.moveOrdering.orderMoves(moves, context.state, context.depth, entry);

        int bestScore = Integer.MIN_VALUE + 1;
        Move bestMove = null;
        boolean firstMove = true;
        int alpha = context.alpha;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);
            statistics.addMovesSearched(1);

            int score;

            if (firstMove) {
                SearchContext childContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-context.beta, -alpha);
                score = -pvSearch(childContext, isPVNode);
                firstMove = false;
            } else {
                SearchContext nullContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-alpha - 1, -alpha);
                score = -pvSearch(nullContext, false);

                if (score > alpha && score < context.beta) {
                    SearchContext fullContext = context.withNewState(newState, context.depth - 1)
                            .withWindow(-context.beta, -alpha);
                    score = -pvSearch(fullContext, false);
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            if (alpha >= context.beta) {
                statistics.incrementAlphaBetaCutoffs();
                if (!GameRules.isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        storeInTT(context, hash, bestScore, context.alpha, context.beta, bestMove);
        return bestScore;
    }

    // ✅ === NULL MOVE PRUNING METHODEN - KORRIGIERT ===

    protected boolean canDoNullMove(SearchContext context) {
        return context.depth >= SearchConfig.NULL_MOVE_MIN_DEPTH &&
                !GameRules.isInCheck(context.state) &&
                !isEndgamePosition(context.state) &&
                hasNonPawnMaterial(context.state);
    }

    protected int doNullMoveSearch(SearchContext context) {
        GameState nullState = context.state.copy();
        nullState.redToMove = !nullState.redToMove;

        int nullDepth = context.depth - SearchConfig.NULL_MOVE_REDUCTION - 1;

        // ✅ KORRIGIERT: Ohne .maximizingPlayer()
        SearchContext nullContext = context.withNewState(nullState, nullDepth)
                .withWindow(-context.beta, -context.beta + 1);

        return -pvSearch(nullContext, false);
    }

    protected boolean isEndgamePosition(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    protected boolean hasNonPawnMaterial(GameState state) {
        boolean isRed = state.redToMove;
        return isRed ? state.redTowers != 0 : state.blueTowers != 0;
    }

    private void storeInTT(SearchContext context, long hash, int score,
                           int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        context.ttable.put(hash, new TTEntry(score, context.depth, flag, bestMove));
        statistics.incrementTTStores();
    }

    @Override
    public String getName() {
        return "PVS";
    }

    private static class AspirationFailException extends RuntimeException {}
}