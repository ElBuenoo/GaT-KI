import GaT.evaluation.Evaluator;
import GaT.model.GameState;
import GaT.search.SearchStatistics;
import GaT.search.strategy.QuiescenceStrategy;

public class QuiescenceSearch {
    private final Evaluator evaluator;
    private final SearchStatistics statistics; // ✅ ADD THIS

    public QuiescenceSearch(Evaluator evaluator, SearchStatistics statistics) { // ✅ ADD PARAMETER
        this.evaluator = evaluator;
        this.statistics = statistics;
    }

    public int quiesce(GameState state, int alpha, int beta,
                       boolean maximizingPlayer, int qDepth) {
        return QuiescenceStrategy.quiesce(state, alpha, beta, maximizingPlayer, qDepth,
                evaluator, statistics); // ✅ PASS STATISTICS
    }
}