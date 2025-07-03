package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import GaT.evaluation.Evaluator;

/**
 * GameEngine - Now uses dependency injection
 */
public class GameEngine {
    private final SearchEngine searchEngine;
    private final Evaluator evaluator;

    // ✅ CONSTRUCTOR INJECTION with proper dependency setup
    public GameEngine() {
        // Create dependencies in CORRECT ORDER
        SearchStatistics statistics = new SearchStatistics(); // ✅ First
        TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
        MoveOrdering moveOrdering = new MoveOrdering(statistics); // ✅ Pass statistics!
        Evaluator evaluator = new Evaluator();

        // Inject all dependencies
        this.searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);
        this.evaluator = evaluator;
    }

    // ✅ ALTERNATIVE CONSTRUCTOR for testing (allows injecting mock dependencies)
    public GameEngine(SearchEngine searchEngine, Evaluator evaluator) {
        this.searchEngine = searchEngine;
        this.evaluator = evaluator;
    }

    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public Move findBestMove(GameState state, int depth) {
        return findBestMove(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    public Move findBestMove(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        return searchEngine.findBestMove(state, depth, strategy);
    }

    public int evaluate(GameState state) {
        return evaluator.evaluate(state, 0);
    }

    public boolean isGameOver(GameState state) {
        return GameRules.isGameOver(state);
    }

    public String getSearchStats() {
        return searchEngine.getStatistics().getSummary();
    }

    public SearchStatistics getStatistics() {
        return searchEngine.getStatistics();
    }

    public void clearCaches() {
        searchEngine.clearCaches();
    }

    public void resetStatistics() {
        searchEngine.resetStatistics();
    }
}