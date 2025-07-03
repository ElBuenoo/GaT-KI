package GaT.search;

import GaT.model.GameState;
import GaT.evaluation.Evaluator;
import GaT.search.strategy.QuiescenceStrategy;

/**
 * Instance-based Quiescence Search
 * Now uses dependency injection instead of static state
 */
public class QuiescenceSearch {

    private final Evaluator evaluator;

    public QuiescenceSearch(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    // ✅ INSTANCE METHOD - can access injected evaluator
    public int quiesce(GameState state, int alpha, int beta,
                       boolean maximizingPlayer, int qDepth) {
        return QuiescenceStrategy.quiesce(state, alpha, beta, maximizingPlayer, qDepth, evaluator);
    }

    // ✅ INSTANCE METHODS for statistics - no more static state
    public long getQNodes() {
        return QuiescenceStrategy.qNodes;
    }

    public long getQCutoffs() {
        return QuiescenceStrategy.qCutoffs;
    }

    public void resetQuiescenceStats() {
        QuiescenceStrategy.resetQuiescenceStats();
    }
}