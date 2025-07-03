// File: src/main/java/GaT/search/QuiescenceSearch.java
package GaT.search;

import GaT.model.GameState;
import GaT.evaluation.Evaluator;
import GaT.search.strategy.QuiescenceStrategy;

/**
 * Wrapper for QuiescenceStrategy to match expected API
 */
public class QuiescenceSearch {

    private static Evaluator evaluator;

    public static void setEvaluator(Evaluator eval) {
        evaluator = eval;
    }

    public static int quiesce(GameState state, int alpha, int beta,
                              boolean maximizingPlayer, int qDepth) {
        if (evaluator == null) {
            throw new IllegalStateException("Evaluator not set in QuiescenceSearch");
        }
        return QuiescenceStrategy.quiesce(state, alpha, beta, maximizingPlayer, qDepth, evaluator);
    }

    // Delegate statistics methods
    public static long qNodes = 0;
    public static long qCutoffs = 0;

    public static void resetQuiescenceStats() {
        QuiescenceStrategy.resetQuiescenceStats();
        qNodes = QuiescenceStrategy.qNodes;
        qCutoffs = QuiescenceStrategy.qCutoffs;
    }
}