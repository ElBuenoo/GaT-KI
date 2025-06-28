// ===== AlphaBetaWithQuiescenceStrategy.java =====
package GaT.search.strategy;

import GaT.model.*;
import GaT.search.QuiescenceSearch;

/**
 * Alpha-Beta with Quiescence Search
 */
public class AlphaBetaWithQuiescenceStrategy extends AlphaBetaStrategy {

    @Override
    public SearchResult search(SearchContext context) {
        // Use the parent alpha-beta but at leaf nodes, use quiescence
        return super.search(context);
    }

    @Override
    protected int alphaBeta(SearchContext context) {
        context.statistics.incrementNodeCount();

        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // At depth 0, use quiescence search instead of static eval
        if (context.depth <= 0) {
            return QuiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0);
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

