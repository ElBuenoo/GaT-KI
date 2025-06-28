// ===== 6. GAME ENGINE (NEW FACADE) =====
package GaT.engine;

import GaT.model.*;
import GaT.search.SearchCoordinator;

/**
 * New public API - replaces static Minimax methods
 */
public class GameEngine {
    private final SearchCoordinator coordinator;

    public GameEngine() {
        this.coordinator = new SearchCoordinator();
    }

    /**
     * Find best move with default strategy
     */
    public Move findBestMove(GameState state, int depth) {
        return coordinator.findBestMove(state, depth,
                SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Find best move with specific strategy
     */
    public Move findBestMove(GameState state, int depth,
                             SearchConfig.SearchStrategy strategy) {
        return coordinator.findBestMove(state, depth, strategy);
    }

    /**
     * Evaluate position (for debugging/testing)
     */
    public int evaluate(GameState state) {
        return coordinator.getEvaluator().evaluate(state, 0);
    }

    /**
     * Get search statistics
     */
    public String getSearchStats() {
        return coordinator.getStatistics().getSummary();
    }

    /**
     * Clear caches
     */
    public void clearCaches() {
        coordinator.clearTranspositionTable();
        coordinator.resetStatistics();
    }
}