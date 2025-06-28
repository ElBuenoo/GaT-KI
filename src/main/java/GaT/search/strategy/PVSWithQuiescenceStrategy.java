// ===== PVSWithQuiescenceStrategy.java =====
package GaT.search.strategy;

import GaT.model.*;
import GaT.search.QuiescenceSearch;

/**
 * PVS with Quiescence Search
 */
public class PVSWithQuiescenceStrategy extends PVSStrategy {

    @Override
    protected int pvSearch(SearchContext context, boolean isPVNode) {
        context.statistics.incrementNodeCount();

        if (context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // At depth 0, use quiescence search
        if (context.depth <= 0) {
            return QuiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0);
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
