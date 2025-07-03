package GaT.engine;

import GaT.model.*;
import GaT.search.SearchStatistics;

/**
 * New public API - replaces static Minimax methods
 * This is the main entry point for all search operations
 */
public class GameEngine {
    private final SearchCoordinator coordinator;

    public GameEngine() {
        this.coordinator = new SearchCoordinator();
    }

    /**
     * Find best move with default strategy (PVS_Q)
     */
    public Move findBestMove(GameState state, int depth) {
        return coordinator.findBestMove(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Find best move with specific strategy
     */
    public Move findBestMove(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        return coordinator.findBestMove(state, depth, strategy);
    }

    /**
     * Evaluate position (for debugging/testing)
     */
    public int evaluate(GameState state) {
        return coordinator.evaluate(state);
    }

    /**
     * Check if game is over
     */
    public boolean isGameOver(GameState state) {
        return GameRules.isGameOver(state);
    }

    /**
     * Get search statistics
     */
    public String getSearchStats() {
        return coordinator.getStatistics().getSummary();
    }

    /**
     * Get raw statistics object
     */
    public SearchStatistics getStatistics() {
        return coordinator.getStatistics();
    }

    /**
     * Clear caches
     */
    public void clearCaches() {
        coordinator.clearTranspositionTable();
        coordinator.resetStatistics();
    }

    /**
     * Reset statistics only
     */
    public void resetStatistics() {
        coordinator.resetStatistics();
    }
}