package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import java.util.List;

public class AlphaBetaStrategy implements ISearchStrategy {

    @Override
    public SearchResult search(SearchContext context) {
        Move bestMove = null;
        int bestScore = context.maximizingPlayer ?
                Integer.MIN_VALUE : Integer.MAX_VALUE;

        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            return new SearchResult(null, context.evaluator.evaluate(context.state, 0),
                    0, context.statistics.getNodeCount());
        }

        // Order moves
        context.moveOrdering.orderMoves(moves, context.state, context.depth,
                context.ttable.get(context.state.hash()));

        int alpha = context.alpha;
        int beta = context.beta;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            // Recursive search
            SearchContext childContext = new SearchContext.Builder()
                    .state(newState)
                    .depth(context.depth - 1)
                    .window(-beta, -alpha)
                    .maximizingPlayer(!context.maximizingPlayer)
                    .components(context.evaluator, context.ttable,
                            context.moveOrdering, context.statistics)
                    .timeoutChecker(context.timeoutChecker)
                    .build();

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
                break; // Pruning
            }
        }

        return new SearchResult(bestMove, bestScore, context.depth,
                context.statistics.getNodeCount());
    }

    private int alphaBeta(SearchContext context) {
        context.statistics.incrementNodeCount();

        // Check timeout
        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // Terminal node check
        if (context.depth <= 0 || isGameOver(context.state)) {
            return context.evaluator.evaluate(context.state, context.depth);
        }

        // TT lookup
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            }
            // Additional TT logic...
        }

        // Generate and search moves
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            return context.evaluator.evaluate(context.state, context.depth);
        }

        int bestScore = context.maximizingPlayer ?
                Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = context.alpha;
        int beta = context.beta;
        Move bestMove = null;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            SearchContext childContext = new SearchContext.Builder()
                    .state(newState)
                    .depth(context.depth - 1)
                    .window(-beta, -alpha)
                    .maximizingPlayer(!context.maximizingPlayer)
                    .components(context.evaluator, context.ttable,
                            context.moveOrdering, context.statistics)
                    .timeoutChecker(context.timeoutChecker)
                    .build();

            int score = -alphaBeta(childContext);

            if (context.maximizingPlayer && score > bestScore) {
                bestScore = score;
                bestMove = move;
                alpha = Math.max(alpha, score);
            } else if (!context.maximizingPlayer && score < bestScore) {
                bestScore = score;
                bestMove = move;
                beta = Math.min(beta, score);
            }

            if (beta <= alpha) {
                break;
            }
        }

        // Store in TT
        storeInTT(context, hash, bestScore, bestMove);

        return bestScore;
    }

    private void storeInTT(SearchContext context, long hash, int score, Move bestMove) {
        int flag;
        if (score <= context.alpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= context.beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, context.depth, flag, bestMove);
        context.ttable.put(hash, entry);
    }

    private boolean isGameOver(GameState state) {
        // Game over logic
        return false; // Placeholder
    }

    @Override
    public String getName() {
        return "AlphaBeta";
    }
}