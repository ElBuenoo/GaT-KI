package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;
import java.util.List;

public class AlphaBetaStrategy implements ISearchStrategy {

    protected final SearchStatistics statistics;
    protected final QuiescenceSearch quiescenceSearch;
    protected final Evaluator evaluator;

    public AlphaBetaStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        this.statistics = statistics;
        this.quiescenceSearch = quiescenceSearch;
        this.evaluator = evaluator;
    }

    @Override
    public SearchResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            int score = evaluator.evaluate(context.state, 0);
            return new SearchResult(null, score, 0, statistics.getNodeCount());
        }

        TTEntry ttEntry = context.ttable.get(context.state.hash());
        context.moveOrdering.orderMoves(moves, context.state, context.depth, ttEntry);

        Move bestMove = null;
        int bestScore = context.maximizingPlayer ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;
        int alpha = context.alpha;
        int beta = context.beta;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            SearchContext childContext = context.withNewState(newState, context.depth - 1)
                    .withWindow(-beta, -alpha);

            int score = -alphaBeta(childContext);

            if (context.maximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
            }

            if (beta <= alpha) {
                statistics.incrementAlphaBetaCutoffs();
                break;
            }
        }

        storeInTT(context, context.state.hash(), bestScore, context.alpha, beta, bestMove);

        long timeMs = System.currentTimeMillis() - startTime;
        return new SearchResult(bestMove, bestScore, context.depth,
                statistics.getNodeCount(), timeMs, false);
    }

    protected int alphaBeta(SearchContext context) {
        statistics.incrementNodeCount();

        // FIX: Null-Check für timeoutChecker
        if (context.timeoutChecker != null && context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        if (context.depth <= 0 || GameRules.isGameOver(context.state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(context.state, context.depth);
        }

        // TT lookup
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= context.beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= context.alpha) {
                return entry.score;
            }
        } else {
            statistics.incrementTTMisses();
        }

        // Null move pruning
        if (canDoNullMove(context)) {
            statistics.incrementNullMoveAttempts();
            int nullMoveScore = doNullMoveSearch(context);
            if (nullMoveScore >= context.beta) {
                statistics.incrementNullMoveCutoffs();
                return context.beta;
            }
        }

        // Generate moves
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        statistics.addMovesGenerated(moves.size());

        if (moves.isEmpty()) {
            return evaluator.evaluate(context.state, context.depth);
        }

        context.moveOrdering.orderMoves(moves, context.state, context.depth, entry);

        int bestScore = Integer.MIN_VALUE + 1;
        int alpha = context.alpha;
        int beta = context.beta;
        Move bestMove = null;
        int moveCount = 0;

        for (Move move : moves) {
            moveCount++;
            GameState newState = context.state.copy();
            newState.applyMove(move);

            statistics.addMovesSearched(1);

            int newDepth = context.depth - 1;

            // Check extension
            if (GameRules.isInCheck(newState)) {
                newDepth++;
                statistics.incrementCheckExtensions();
            }

            // FIX: Score-Berechnung und -Verwendung korrigiert
            int score;

            if (moveCount > 4 && newDepth > 2 && !GameRules.isCapture(move, context.state)) {
                // Late Move Reduction
                SearchContext reducedContext = context.withNewState(newState, newDepth - 1)
                        .withWindow(-alpha - 1, -alpha);
                score = -alphaBeta(reducedContext);

                if (score > alpha) {
                    SearchContext fullContext = context.withNewState(newState, newDepth)
                            .withWindow(-beta, -alpha);
                    score = -alphaBeta(fullContext);
                }
                statistics.incrementLMRReductions();
            } else {
                // Normal search
                SearchContext childContext = context.withNewState(newState, newDepth)
                        .withWindow(-beta, -alpha);
                score = -alphaBeta(childContext);
            }

            // Score-Verarbeitung für ALLE Züge
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, bestScore);

            if (alpha >= beta) {
                statistics.incrementAlphaBetaCutoffs();
                if (!GameRules.isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        storeInTT(context, hash, bestScore, context.alpha, beta, bestMove);
        return bestScore;
    }

    protected boolean canDoNullMove(SearchContext context) {
        return context.depth >= SearchConfig.NULL_MOVE_MIN_DEPTH &&
                !context.isPVNode &&
                !GameRules.isInCheck(context.state) &&
                !isEndgamePosition(context.state) &&
                hasNonPawnMaterial(context.state);
    }

    protected int doNullMoveSearch(SearchContext context) {
        // Null Move ausführen
        GameState nullState = context.state.copy();
        nullState.redToMove = !nullState.redToMove;

        // Reduzierte Suchtiefe
        int nullDepth = context.depth - SearchConfig.NULL_MOVE_REDUCTION - 1;

        // Null Window Search
        SearchContext nullContext = context.withNewState(nullState, nullDepth)
                .withWindow(-context.beta, -context.beta + 1);

        return -alphaBeta(nullContext);
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

    protected void storeInTT(SearchContext context, long hash, int score,
                           int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, context.depth, flag, bestMove);
        context.ttable.put(hash, entry);
        statistics.incrementTTStores();
    }

    @Override
    public String getName() {
        return "AlphaBeta";
    }
}