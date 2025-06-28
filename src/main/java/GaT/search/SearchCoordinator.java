package GaT.search;

import GaT.model.*;
import GaT.search.strategy.*;
import GaT.evaluation.Evaluator;
import java.util.Map;
import java.util.HashMap;

/**
 * Coordinates all search operations - replaces static Minimax methods
 */
public class SearchCoordinator {
    private final Map<SearchConfig.SearchStrategy, ISearchStrategy> strategies;
    private final Evaluator evaluator;
    private final TranspositionTable transpositionTable;
    private final MoveOrdering moveOrdering;
    private final SearchStatistics statistics;

    public SearchCoordinator() {
        this.evaluator = new Evaluator();
        this.transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
        this.moveOrdering = new MoveOrdering();
        this.statistics = SearchStatistics.getInstance();

        // Register strategies
        this.strategies = new HashMap<>();
        this.strategies.put(SearchConfig.SearchStrategy.ALPHA_BETA,
                new AlphaBetaStrategy());
        this.strategies.put(SearchConfig.SearchStrategy.PVS,
                new PVSStrategy());
        this.strategies.put(SearchConfig.SearchStrategy.PVS_Q,
                new PVSWithQuiescenceStrategy());
        // Add more strategies...
    }

    /**
     * Main search method - replaces Minimax.findBestMove
     */
    public Move findBestMove(GameState state, int depth,
                             SearchConfig.SearchStrategy strategyType) {
        statistics.reset();
        statistics.startSearch();

        ISearchStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            strategy = strategies.get(SearchConfig.SearchStrategy.ALPHA_BETA);
        }

        SearchContext context = new SearchContext.Builder()
                .state(state)
                .depth(depth)
                .window(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .maximizingPlayer(state.redToMove)
                .pvNode(true)
                .components(evaluator, transpositionTable, moveOrdering, statistics)
                .build();

        SearchResult result = strategy.search(context);

        System.out.println("Search completed: " + strategy.getName());
        System.out.println("Nodes: " + result.nodesSearched);
        System.out.println("Best move: " + result.bestMove);
        System.out.println("Score: " + result.score);

        return result.bestMove;
    }

    // Additional coordinator methods
    public void clearTranspositionTable() {
        transpositionTable.clear();
    }

    public void resetStatistics() {
        statistics.reset();
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }
}


