package GaT.search.strategy;

import GaT.model.*;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;

/**
 * PVS with Quiescence Search - dependency injection version
 */
public class PVSWithQuiescenceStrategy extends PVSStrategy {

    // ✅ CONSTRUCTOR INJECTION - calls parent constructor
    public PVSWithQuiescenceStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        super(statistics, quiescenceSearch, evaluator);
    }

    @Override
    protected int pvSearch(SearchContext context, boolean isPVNode) {
        statistics.incrementNodeCount(); // ✅ Use inherited field

        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // At depth 0, use quiescence search
        if (context.depth <= 0) {
            return quiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0); // ✅ Use inherited field
        }

        // Otherwise use normal PVS
        return super.pvSearch(context, isPVNode);
    }

    @Override
    public String getName() {
        return "PVS+Q";
    }

    @Override
    public boolean usesQuiescence() {
        return true;
    }
}