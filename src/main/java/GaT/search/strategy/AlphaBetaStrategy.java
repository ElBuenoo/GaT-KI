package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import java.util.List;

/**
 * Standard Alpha-Beta search implementation
 */
public class AlphaBetaStrategy implements ISearchStrategy {

    @Override
    public SearchResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            int score = context.evaluator.evaluate(context.state, 0);
            return new SearchResult(null, score, 0, context.statistics.getNodeCount());
        }

        // Order moves
        TTEntry ttEntry = context.ttable.get(context.state.hash());
        context.moveOrdering.orderMoves(moves, context.state, context.depth, ttEntry);

        Move bestMove = null;
        int bestScore = context.maximizingPlayer ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;
        int alpha = context.alpha;
        int beta = context.beta;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            // Create child context
            SearchContext childContext = context.withNewState(newState, context.depth - 1)
                    .withWindow(-beta, -alpha);

            // Recursive search
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
                context.statistics.incrementAlphaBetaCutoffs();
                break;
            }
        }

        // Store in TT
        storeInTT(context, context.state.hash(), bestScore, context.alpha, beta, bestMove);

        long timeMs = System.currentTimeMillis() - startTime;
        return new SearchResult(bestMove, bestScore, context.depth,
                context.statistics.getNodeCount(), timeMs, false);
    }

    protected int alphaBeta(SearchContext context) {
        context.statistics.incrementNodeCount();

        // Check timeout
        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // Terminal node check
        if (context.depth <= 0 || GameRules.isGameOver(context.state)) {
            context.statistics.incrementLeafNodeCount();
            return context.evaluator.evaluate(context.state, context.depth);
        }

        // TT lookup
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            context.statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= context.beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= context.alpha) {
                return entry.score;
            }
        } else {
            context.statistics.incrementTTMisses();
        }

        // Generate moves
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        context.statistics.addMovesGenerated(moves.size());

        if (moves.isEmpty()) {
            return context.evaluator.evaluate(context.state, context.depth);
        }

        // Order moves
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

            context.statistics.addMovesSearched(1);

            // Apply reductions/extensions
            int newDepth = context.depth - 1;

            // Check extension
            if (GameRules.isInCheck(newState)) {
                newDepth++;
                context.statistics.incrementCheckExtensions();
            }

            // LMR for late moves
            if (moveCount > 4 && newDepth > 2 && !GameRules.isCapture(move, context.state)) {
                // Reduced depth search
                SearchContext reducedContext = context.withNewState(newState, newDepth - 1)
                        .withWindow(-alpha - 1, -alpha);
                int score = -alphaBeta(reducedContext);

                // Re-search if it beats alpha
                if (score > alpha) {
                    SearchContext fullContext = context.withNewState(newState, newDepth)
                            .withWindow(-beta, -alpha);
                    score = -alphaBeta(fullContext);
                }
                context.statistics.incrementLMRReductions();
            } else {
                // Normal search
                SearchContext childContext = context.withNewState(newState, newDepth)
                        .withWindow(-beta, -alpha);
                int score = -alphaBeta(childContext);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }

            alpha = Math.max(alpha, bestScore);

            if (alpha >= beta) {
                context.statistics.incrementAlphaBetaCutoffs();

                // Update killer moves
                if (!GameRules.isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        // Store in TT
        storeInTT(context, hash, bestScore, context.alpha, beta, bestMove);

        return bestScore;
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

        TTEntry entry = new TTEntry(score, context.depth, flag, bestMove);
        context.ttable.put(hash, entry);
        context.statistics.incrementTTStores();
    }

    @Override
    public String getName() {
        return "AlphaBeta";
    }
}