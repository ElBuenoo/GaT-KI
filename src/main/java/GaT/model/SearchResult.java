package GaT.model;

/**
 * Immutable search result
 */
public class SearchResult {
    public final Move bestMove;
    public final int score;
    public final int depth;
    public final long nodesSearched;

    public SearchResult(Move bestMove, int score, int depth, long nodesSearched) {
        this.bestMove = bestMove;
        this.score = score;
        this.depth = depth;
        this.nodesSearched = nodesSearched;
    }
}