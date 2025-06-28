package GaT.search;

import GaT.model.*;
import GaT.search.strategy.*;
import GaT.evaluation.Evaluator;
import java.util.Map;
import java.util.HashMap;
import java.util.function.BooleanSupplier;

/**
 * Coordinates all search operations - replaces static Minimax methods
 */
public class SearchCoordinator {
    private final Map<SearchConfig.SearchStrategy, ISearchStrategy> strategies;
    private final Evaluator evaluator;
    private final TranspositionTable transpositionTable;
    private final MoveOrdering moveOrdering;
    private final SearchStatistics statistics;

    // Timeout support
    private BooleanSupplier timeoutChecker = null;

    public SearchCoordinator() {
        this.evaluator = new Evaluator();
        this.transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
        this.moveOrdering = new MoveOrdering();
        this.statistics = SearchStatistics.getInstance();

        // Register strategies
        this.strategies = new HashMap<>();
        this.strategies.put(SearchConfig.SearchStrategy.ALPHA_BETA,
                new AlphaBetaStrategy());
        this.strategies.put(SearchConfig.SearchStrategy.ALPHA_BETA_Q,
                new AlphaBetaWithQuiescenceStrategy());
        this.strategies.put(SearchConfig.SearchStrategy.PVS,
                new PVSStrategy());
        this.strategies.put(SearchConfig.SearchStrategy.PVS_Q,
                new PVSWithQuiescenceStrategy());
    }

    /**
     * Main search method - replaces Minimax.findBestMove
     */
    public Move findBestMove(GameState state, int depth,
                             SearchConfig.SearchStrategy strategyType) {
        if (state == null) {
            System.err.println("Error: null game state");
            return null;
        }

        statistics.reset();
        statistics.startSearch();

        ISearchStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            System.err.println("Unknown strategy: " + strategyType + ", using ALPHA_BETA");
            strategy = strategies.get(SearchConfig.SearchStrategy.ALPHA_BETA);
        }

        SearchContext context = new SearchContext.Builder()
                .state(state)
                .depth(depth)
                .window(Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1)
                .maximizingPlayer(state.redToMove)
                .pvNode(true)
                .timeoutChecker(timeoutChecker)
                .components(evaluator, transpositionTable, moveOrdering, statistics)
                .build();

        try {
            SearchResult result = strategy.search(context);

            statistics.endSearch();

            // Debug output
            System.out.println("Search completed: " + strategy.getName());
            System.out.println("Nodes: " + result.nodesSearched);
            System.out.println("Time: " + result.timeMs + "ms");
            if (result.bestMove != null) {
                System.out.println("Best move: " + result.bestMove);
                System.out.println("Score: " + result.score);
            }

            return result.bestMove;

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                System.out.println("Search timeout - returning best so far");
                // Could implement getting best move so far from TT
                return null;
            }
            throw e;
        }
    }

    /**
     * Find best move with default strategy
     */
    public Move findBestMove(GameState state, int depth) {
        return findBestMove(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Evaluate a position
     */
    public int evaluate(GameState state) {
        return evaluator.evaluate(state, 0);
    }

    // === Timeout management ===

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    // === Component access ===

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

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    public MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }
}