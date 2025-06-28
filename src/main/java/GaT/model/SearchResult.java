package GaT.model;

/**
 * Immutable search result containing all relevant information
 */
public class SearchResult {
    public final Move bestMove;
    public final int score;
    public final int depth;
    public final long nodesSearched;
    public final long timeMs;
    public final boolean timeout;

    public SearchResult(Move bestMove, int score, int depth, long nodesSearched) {
        this(bestMove, score, depth, nodesSearched, 0, false);
    }

    public SearchResult(Move bestMove, int score, int depth, long nodesSearched, long timeMs, boolean timeout) {
        this.bestMove = bestMove;
        this.score = score;
        this.depth = depth;
        this.nodesSearched = nodesSearched;
        this.timeMs = timeMs;
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return String.format("SearchResult{move=%s, score=%d, depth=%d, nodes=%d, time=%dms%s}",
                bestMove, score, depth, nodesSearched, timeMs, timeout ? ", TIMEOUT" : "");
    }
}