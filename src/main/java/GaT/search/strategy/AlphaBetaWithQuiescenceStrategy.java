package GaT.search.strategy;

import GaT.model.*;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;

public class AlphaBetaWithQuiescenceStrategy extends AlphaBetaStrategy {

    public AlphaBetaWithQuiescenceStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        super(statistics, quiescenceSearch, evaluator);
    }

    @Override
    public SearchResult search(SearchContext context) {
        return super.search(context);
    }

    @Override
    protected int alphaBeta(SearchContext context) {
        statistics.incrementNodeCount();

        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // At depth 0, use quiescence search instead of static eval
        if (context.depth <= 0) {
            return this.quiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0);
        }

        // Check for game over
        if (GameRules.isGameOver(context.state)) {
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

        // ✅ === NULL MOVE PRUNING - KORRIGIERT ===
        if (canDoNullMove(context)) {
            statistics.incrementNullMoveAttempts();
            int nullMoveScore = doNullMoveSearch(context);
            if (nullMoveScore >= context.beta) {
                statistics.incrementNullMoveCutoffs();
                return context.beta;
            }
        }

        // Continue with normal alpha-beta + quiescence
        return super.alphaBeta(context);
    }

    @Override
    public String getName() {
        return "AlphaBeta+Q";
    }

    @Override
    public boolean usesQuiescence() {
        return true;
    }
}