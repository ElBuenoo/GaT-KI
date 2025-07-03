package GaT.search.strategy;

import GaT.model.SearchContext;
import GaT.model.SearchResult;

/**
 * Base interface for all search strategies
 */
public interface ISearchStrategy {

    /**
     * Execute search with given context
     * @param context Search parameters and components
     * @return Search result with best move and statistics
     */
    SearchResult search(SearchContext context);

    /**
     * Get strategy name for debugging/logging
     * @return Strategy name
     */
    String getName();

    /**
     * Check if this strategy uses quiescence search
     * @return true if quiescence is used
     */
    default boolean usesQuiescence() {
        return false;
    }
}