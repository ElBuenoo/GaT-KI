package GaT.search.strategy;

import GaT.model.*;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;

public class PVSWithQuiescenceStrategy extends PVSStrategy {

    public PVSWithQuiescenceStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        super(statistics, quiescenceSearch, evaluator);
    }

    @Override
    protected int pvSearch(SearchContext context, boolean isPVNode) {
        statistics.incrementNodeCount();

        // Timeout check
        if (context.timeoutChecker != null && context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // At depth 0, use quiescence search
        if (context.depth <= 0) {
            return quiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0);
        }

        // Check for game over
        if (GameRules.isGameOver(context.state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(context.state, context.depth);
        }

        // TT probe
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            statistics.incrementTTHits();

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
            statistics.incrementTTMisses();
        }

        // Null move pruning - KORRIGIERT
        if (!isPVNode && canDoNullMove(context)) {
            statistics.incrementNullMoveAttempts();
            try {
                int nullMoveScore = doNullMoveSearch(context);
                if (nullMoveScore >= context.beta) {
                    statistics.incrementNullMoveCutoffs();
                    return context.beta;
                }
            } catch (Exception e) {
                // Bei Fehler im Null-Move Search einfach weitermachen
                System.err.println("Null move search error: " + e.getMessage());
            }
        }

        // Continue with normal PVS
        return super.pvSearch(context, isPVNode);
    }

    @Override
    protected int doNullMoveSearch(SearchContext context) {
        try {
            // Create null move context safely
            SearchContext nullContext = context.forNullMove(
                    context.depth - SearchConfig.NULL_MOVE_REDUCTION - 1);

            return -pvSearch(nullContext, false);
        } catch (Exception e) {
            // Fallback bei Fehler
            System.err.println("Null move creation failed: " + e.getMessage());
            return context.alpha;
        }
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