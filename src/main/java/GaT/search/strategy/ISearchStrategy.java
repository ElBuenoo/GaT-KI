package GaT.search.strategy;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchResult;
import GaT.model.SearchContext;

/**
 * Base interface for all search strategies
 */
public interface ISearchStrategy {
    SearchResult search(SearchContext context);
    String getName();
}