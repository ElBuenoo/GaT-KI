package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * SEARCH ENGINE - Now uses dependency injection
 */
public class SearchEngine {

    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final MoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final SearchStatistics statistics;
    private final QuiescenceSearch quiescenceSearch;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    // ✅ CONSTRUCTOR INJECTION - all dependencies injected
    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;
        this.quiescenceSearch = new QuiescenceSearch(evaluator); // ✅ Inject evaluator
    }

    // ✅ IMPROVED ERROR HANDLING - specific exceptions instead of catch-all
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        if (strategy == null) {
            throw new IllegalArgumentException("Search strategy cannot be null");
        }

        try {
            return switch (strategy) {
                case ALPHA_BETA -> alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                case ALPHA_BETA_Q -> alphaBetaWithQuiescence(state, depth, alpha, beta, maximizingPlayer);
                case PVS -> searchPVS(state, depth, alpha, beta, maximizingPlayer, true, false);
                case PVS_Q -> searchPVS(state, depth, alpha, beta, maximizingPlayer, true, true);
            };
        } catch (SearchTimeoutException e) {
            // Expected timeout - return best move so far
            throw e; // Re-throw so caller can handle
        } catch (IllegalStateException e) {
            throw new SearchException("Invalid game state for search: " + e.getMessage(), e);
        }
    }

    // ✅ PVS methods now use injected QuiescenceSearch
    private int searchPVS(GameState state, int depth, int alpha, int beta,
                          boolean maximizingPlayer, boolean isPVNode, boolean useQuiescence) {
        // Implementation using injected dependencies
        // ...
        return 0; // Placeholder
    }

    // Rest of SearchEngine methods stay the same, but now use injected dependencies
    // ...

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }
}