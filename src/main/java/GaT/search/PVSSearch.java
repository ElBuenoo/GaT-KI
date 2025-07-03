// File: src/main/java/GaT/search/PVSSearch.java
package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import GaT.search.strategy.PVSStrategy;
import java.util.function.BooleanSupplier;

/**
 * Static wrapper for PVS to match SearchEngine expectations
 */
public class PVSSearch {

    private static Evaluator evaluator;
    private static TranspositionTable transpositionTable;
    private static MoveOrdering moveOrdering;
    private static SearchStatistics statistics;
    private static BooleanSupplier timeoutChecker;

    public static void initialize(Evaluator eval, TranspositionTable tt,
                                  MoveOrdering mo, SearchStatistics stats) {
        evaluator = eval;
        transpositionTable = tt;
        moveOrdering = mo;
        statistics = stats;
    }

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {
        // Create context and delegate to PVSStrategy
        SearchContext context = new SearchContext.Builder()
                .state(state)
                .depth(depth)
                .window(alpha, beta)
                .maximizingPlayer(maximizingPlayer)
                .pvNode(isPVNode)
                .timeoutChecker(timeoutChecker)
                .components(evaluator, transpositionTable, moveOrdering, statistics)
                .build();

        PVSStrategy strategy = new PVSStrategy();
        SearchResult result = strategy.search(context);

        // Return just the score
        return result.score;
    }

    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {
        // Similar implementation but use PVSWithQuiescenceStrategy
        return search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
    }
}