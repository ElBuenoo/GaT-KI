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
        // Create dependencies in correct order
        SearchStatistics statistics = new SearchStatistics(); // ✅ No more singleton
        TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
        MoveOrdering moveOrdering = new MoveOrdering();
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

    public Move findBestMove(GameState state, int depth) {
        return findBestMove(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    public Move findBestMove(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        // Implementation using injected searchEngine
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) return null;

        // Use searchEngine instead of static methods
        int bestScore = searchEngine.search(state, depth, Integer.MIN_VALUE, Integer.MAX_VALUE,
                state.redToMove, strategy);

        // Find move that produces this score
        // ... implementation details

        return moves.get(0); // Placeholder
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
        searchEngine.getStatistics().reset();
    }

    public void resetStatistics() {
        searchEngine.getStatistics().reset();
    }
}