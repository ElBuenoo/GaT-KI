package GaT.search.strategy;

import GaT.model.*;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;

/**
 * Alpha-Beta with Quiescence Search - dependency injection version
 */
public class AlphaBetaWithQuiescenceStrategy extends AlphaBetaStrategy {

    // ✅ CONSTRUCTOR INJECTION - calls parent constructor
    public AlphaBetaWithQuiescenceStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        super(statistics, quiescenceSearch, evaluator);
    }

    @Override
    public SearchResult search(SearchContext context) {
        // Use the parent alpha-beta but at leaf nodes, use quiescence
        return super.search(context);
    }

    @Override
    protected int alphaBeta(SearchContext context) {
        statistics.incrementNodeCount(); // ✅ Use inherited field

        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // At depth 0, use quiescence search instead of static eval
        if (context.depth <= 0) {
            return this.quiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0); // ✅ Use inherited field
        }

        // Otherwise use normal alpha-beta
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