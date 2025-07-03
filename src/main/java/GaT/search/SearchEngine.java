package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import GaT.search.strategy.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.Map;

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

    // === SEARCH STRATEGIES ===
    private final Map<SearchConfig.SearchStrategy, ISearchStrategy> strategies;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    // ✅ CONSTRUCTOR INJECTION - all dependencies injected
    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;
        this.quiescenceSearch = new QuiescenceSearch(evaluator, statistics); // ✅ Inject dependencies

        // ✅ CREATE STRATEGIES WITH ALL DEPENDENCIES
        this.strategies = new HashMap<>();
        this.strategies.put(SearchConfig.SearchStrategy.ALPHA_BETA,
                new AlphaBetaStrategy(statistics, quiescenceSearch, evaluator));
        this.strategies.put(SearchConfig.SearchStrategy.ALPHA_BETA_Q,
                new AlphaBetaWithQuiescenceStrategy(statistics, quiescenceSearch, evaluator));
        this.strategies.put(SearchConfig.SearchStrategy.PVS,
                new PVSStrategy(statistics, quiescenceSearch, evaluator));
        this.strategies.put(SearchConfig.SearchStrategy.PVS_Q,
                new PVSWithQuiescenceStrategy(statistics, quiescenceSearch, evaluator));
    }

    // ✅ MAIN SEARCH METHOD - improved error handling
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        if (strategy == null) {
            throw new IllegalArgumentException("Search strategy cannot be null");
        }

        if (state == null) {
            throw new IllegalArgumentException("Game state cannot be null");
        }

        try {
            return executeSearch(state, depth, alpha, beta, maximizingPlayer, strategy);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw e; // Re-throw timeout
            }
            System.err.println("Search error: " + e.getMessage());
            throw new IllegalStateException("Search failed: " + e.getMessage(), e);
        }
    }

    // ✅ EXECUTE SEARCH with strategy pattern
    private int executeSearch(GameState state, int depth, int alpha, int beta,
                              boolean maximizingPlayer, SearchConfig.SearchStrategy strategyType) {

        ISearchStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            System.err.println("Unknown strategy: " + strategyType + ", using ALPHA_BETA");
            strategy = strategies.get(SearchConfig.SearchStrategy.ALPHA_BETA);
        }

        // Create search context
        SearchContext context = new SearchContext.Builder()
                .state(state)
                .depth(depth)
                .window(alpha, beta)
                .maximizingPlayer(maximizingPlayer)
                .pvNode(true)
                .timeoutChecker(timeoutChecker != null ? timeoutChecker : () -> false)
                .components(evaluator, transpositionTable, moveOrdering, statistics)
                .build();

        // Execute search
        SearchResult result = strategy.search(context);
        return result.score;
    }

    // ✅ FIND BEST MOVE - main public API
    public Move findBestMove(GameState state, int depth, SearchConfig.SearchStrategy strategyType) {
        if (state == null) {
            System.err.println("Error: null game state");
            return null;
        }

        // Reset statistics
        statistics.reset();
        statistics.startSearch();

        // Get strategy
        ISearchStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            System.err.println("Unknown strategy: " + strategyType + ", using PVS_Q");
            strategy = strategies.get(SearchConfig.SearchStrategy.PVS_Q);
        }

        // Create search context
        SearchContext context = new SearchContext.Builder()
                .state(state)
                .depth(depth)
                .window(Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1)
                .maximizingPlayer(state.redToMove)
                .pvNode(true)
                .timeoutChecker(timeoutChecker != null ? timeoutChecker : () -> false)
                .components(evaluator, transpositionTable, moveOrdering, statistics)
                .build();

        try {
            SearchResult result = strategy.search(context);
            statistics.endSearch();

            if (result.bestMove != null) {
                System.out.println("Search completed: " + strategy.getName());
                System.out.println("Best move: " + result.bestMove);
                System.out.println("Score: " + result.score);
                System.out.println("Depth: " + result.depth);
                System.out.println("Nodes: " + result.nodesSearched);
                System.out.println("Time: " + result.timeMs + "ms");
            }

            return result.bestMove;

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                System.out.println("Search timeout - returning best so far");
                // Could try to get partial result here
                return null;
            }
            System.err.println("Search error: " + e.getMessage());
            return fallbackMove(state);
        }
    }

    // ✅ CONVENIENCE METHODS
    public Move findBestMove(GameState state, int depth) {
        return findBestMove(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    public int evaluate(GameState state) {
        return evaluator.evaluate(state, 0);
    }

    // ✅ FALLBACK MOVE - in case of errors
    private Move fallbackMove(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        // Try to find a reasonable move
        for (Move move : moves) {
            if (GameRules.isCapture(move, state)) {
                return move; // Prefer captures
            }
        }

        return moves.get(0); // Return first legal move
    }

    // ✅ LEGACY SUPPORT METHODS (for backward compatibility)
    public int alphaBetaSearch(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    public int alphaBetaWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    public int searchPVS(GameState state, int depth, int alpha, int beta,
                         boolean maximizingPlayer, boolean isPVNode, boolean useQuiescence) {
        SearchConfig.SearchStrategy strategy = useQuiescence ?
                SearchConfig.SearchStrategy.PVS_Q : SearchConfig.SearchStrategy.PVS;
        return search(state, depth, alpha, beta, maximizingPlayer, strategy);
    }

    // === TIMEOUT MANAGEMENT ===
    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
        // Also set it for strategies if they support it
        // (This is a simplification - in a full implementation you'd pass it through context)
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    // === COMPONENT ACCESS ===
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

    public QuiescenceSearch getQuiescenceSearch() {
        return quiescenceSearch;
    }

    // === CACHE MANAGEMENT ===
    public void clearCaches() {
        transpositionTable.clear();
        moveOrdering.clear();
        statistics.reset();
    }

    public void resetStatistics() {
        statistics.reset();
    }

    // === DIAGNOSTICS ===
    public String getSearchStats() {
        return statistics.getSummary();
    }

    public boolean isSearchActive() {
        return timeoutChecker != null;
    }

    // === STRATEGY MANAGEMENT ===
    public void addStrategy(SearchConfig.SearchStrategy type, ISearchStrategy strategy) {
        strategies.put(type, strategy);
    }

    public ISearchStrategy getStrategy(SearchConfig.SearchStrategy type) {
        return strategies.get(type);
    }

    public boolean hasStrategy(SearchConfig.SearchStrategy type) {
        return strategies.containsKey(type);
    }

    // === UTILITY METHODS ===
    public boolean isGameOver(GameState state) {
        return GameRules.isGameOver(state);
    }

    public List<Move> generateMoves(GameState state) {
        return MoveGenerator.generateAllMoves(state);
    }

    // === PERFORMANCE TESTING ===
    public SearchResult benchmarkStrategy(GameState state, int depth,
                                          SearchConfig.SearchStrategy strategy, int iterations) {
        long totalTime = 0;
        long totalNodes = 0;
        Move bestMove = null;
        int bestScore = 0;

        for (int i = 0; i < iterations; i++) {
            statistics.reset();
            long startTime = System.currentTimeMillis();

            Move move = findBestMove(state, depth, strategy);

            long endTime = System.currentTimeMillis();
            totalTime += endTime - startTime;
            totalNodes += statistics.getTotalNodes();

            if (i == 0) {
                bestMove = move;
                bestScore = statistics.getNodeCount() > 0 ? evaluate(state) : 0;
            }
        }

        return new SearchResult(bestMove, bestScore, depth, totalNodes / iterations,
                totalTime / iterations, false);
    }
}
