package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import java.util.List;

/**
 * Principal Variation Search implementation
 */
public class PVSStrategy implements ISearchStrategy {

    private static final int ASPIRATION_WINDOW = 50;

    @Override
    public SearchResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        // Try aspiration window
        int alpha = context.alpha;
        int beta = context.beta;

        if (context.depth > 3) {
            // Get previous iteration score from TT if available
            TTEntry entry = context.ttable.get(context.state.hash());
            if (entry != null && entry.flag == TTEntry.EXACT) {
                alpha = Math.max(context.alpha, entry.score - ASPIRATION_WINDOW);
                beta = Math.min(context.beta, entry.score + ASPIRATION_WINDOW);
            }
        }

        try {
            return performPVS(context.withWindow(alpha, beta), startTime);
        } catch (AspirationFailException e) {
            // Re-search with full window
            System.out.println("Aspiration fail - researching with full window");
            context.statistics.reset(); // Reset stats for clean search
            return performPVS(context, startTime);
        }
    }

    private SearchResult performPVS(SearchContext context, long startTime) {
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            int score = context.evaluator.evaluate(context.state, 0);
            return new SearchResult(null, score, 0, context.statistics.getNodeCount());
        }

        // Order moves
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
                // First move - full window search
                SearchContext childContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-beta, -alpha);
                score = -pvSearch(childContext, true);
                firstMove = false;
            } else {
                // Null window search
                SearchContext nullContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-alpha - 1, -alpha);
                score = -pvSearch(nullContext, false);

                // Re-search if failed high
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
                context.statistics.incrementAlphaBetaCutoffs();
                break;
            }
        }

        // Check aspiration failure
        if (bestScore <= context.alpha || bestScore >= context.beta) {
            throw new AspirationFailException();
        }

        // Store in TT
        storeInTT(context, context.state.hash(), bestScore, context.alpha, beta, bestMove);

        long timeMs = System.currentTimeMillis() - startTime;
        return new SearchResult(bestMove, bestScore, context.depth,
                context.statistics.getNodeCount(), timeMs, false);
    }

    protected int pvSearch(SearchContext context, boolean isPVNode) {
        context.statistics.incrementNodeCount();

        // Timeout check
        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // Terminal node
        if (context.depth <= 0 || GameRules.isGameOver(context.state)) {
            context.statistics.incrementLeafNodeCount();
            return context.evaluator.evaluate(context.state, context.depth);
        }

        // TT probe
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            context.statistics.incrementTTHits();

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
            context.statistics.incrementTTMisses();
        }

        // Move generation
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        context.statistics.addMovesGenerated(moves.size());

        if (moves.isEmpty()) {
            return context.evaluator.evaluate(context.state, context.depth);
        }

        context.moveOrdering.orderMoves(moves, context.state, context.depth, entry);

        int bestScore = Integer.MIN_VALUE + 1;
        Move bestMove = null;
        boolean firstMove = true;
        int alpha = context.alpha;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);
            context.statistics.addMovesSearched(1);

            int score;

            if (firstMove) {
                SearchContext childContext = context.withNewState(newState, context.depth - 1)
                        .withWindow(-context.beta, -alpha);
                score = -pvSearch(childContext, isPVNode);
                firstMove = false;
            } else {
                // Null window
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
                context.statistics.incrementAlphaBetaCutoffs();
                if (!GameRules.isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        // Store in TT
        storeInTT(context, hash, bestScore, context.alpha, context.beta, bestMove);

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

        context.ttable.put(hash, new TTEntry(score, context.depth, flag, bestMove));
        context.statistics.incrementTTStores();
    }

    @Override
    public String getName() {
        return "PVS";
    }

    private static class AspirationFailException extends RuntimeException {}
}