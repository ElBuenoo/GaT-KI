package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;
import java.util.List;

/**
 * Principal Variation Search implementation with dependency injection
 */
public class PVSStrategy implements ISearchStrategy {

    // ✅ INJECTED DEPENDENCIES
    protected final SearchStatistics statistics;
    protected final QuiescenceSearch quiescenceSearch;
    protected final Evaluator evaluator;

    private static final int ASPIRATION_WINDOW = 50;

    // ✅ CONSTRUCTOR INJECTION
    public PVSStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        this.statistics = statistics;
        this.quiescenceSearch = quiescenceSearch;
        this.evaluator = evaluator;
    }

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
            statistics.reset(); // ✅ Use injected instance
            return performPVS(context, startTime);
        }
    }

    private SearchResult performPVS(SearchContext context, long startTime) {
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            int score = evaluator.evaluate(context.state, 0); // ✅ Use injected instance
            return new SearchResult(null, score, 0, statistics.getNodeCount()); // ✅ Use injected instance
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
                statistics.incrementAlphaBetaCutoffs(); // ✅ Use injected instance
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
                statistics.getNodeCount(), timeMs, false); // ✅ Use injected instance
    }

    protected int pvSearch(SearchContext context, boolean isPVNode) {
        statistics.incrementNodeCount(); // ✅ Use injected instance

        // Timeout check
        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // Terminal node
        if (context.depth <= 0 || GameRules.isGameOver(context.state)) {
            statistics.incrementLeafNodeCount(); // ✅ Use injected instance
            return evaluator.evaluate(context.state, context.depth); // ✅ Use injected instance
        }

        // TT probe
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            statistics.incrementTTHits(); // ✅ Use injected instance

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
            statistics.incrementTTMisses(); // ✅ Use injected instance
        }

        // Move generation
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        statistics.addMovesGenerated(moves.size()); // ✅ Use injected instance

        if (moves.isEmpty()) {
            return evaluator.evaluate(context.state, context.depth); // ✅ Use injected instance
        }

        context.moveOrdering.orderMoves(moves, context.state, context.depth, entry);

        int bestScore = Integer.MIN_VALUE + 1;
        Move bestMove = null;
        boolean firstMove = true;
        int alpha = context.alpha;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);
            statistics.addMovesSearched(1); // ✅ Use injected instance

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
                statistics.incrementAlphaBetaCutoffs(); // ✅ Use injected instance
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
        statistics.incrementTTStores(); // ✅ Use injected instance
    }

    @Override
    public String getName() {
        return "PVS";
    }

    private static class AspirationFailException extends RuntimeException {}
}