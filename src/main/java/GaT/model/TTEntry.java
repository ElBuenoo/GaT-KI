package GaT.model;

/**
 * Transposition Table Entry
 * Stores search results for position caching
 */
public class TTEntry {
    public int score;
    public int depth;
    public int flag;
    public Move bestMove;
    public long lastAccessed;     // For LRU eviction
    public long zobristHash;      // For hash collision detection

    // Flag constants
    public static final int EXACT = 0;
    public static final int LOWER_BOUND = 1;
    public static final int UPPER_BOUND = 2;

    /**
     * Full constructor with hash verification
     */
    public TTEntry(int score, int depth, int flag, Move bestMove, long hash) {
        this.score = score;
        this.depth = depth;
        this.flag = flag;
        this.bestMove = bestMove;
        this.lastAccessed = 0; // Will be set when added to TT
        this.zobristHash = hash;
    }

    /**
     * Constructor without hash (for backward compatibility)
     */
    public TTEntry(int score, int depth, int flag, Move bestMove) {
        this(score, depth, flag, bestMove, 0L);
    }

    /**
     * Old constructor for compatibility
     */
    public TTEntry(int score, int depth, int alpha, int beta) {
        this.score = score;
        this.depth = depth;
        this.flag = EXACT; // Default to exact
        this.bestMove = null;
        this.lastAccessed = 0;
        this.zobristHash = 0;
    }

    /**
     * Check if this entry is valid for the given hash
     */
    public boolean isValidFor(long hash) {
        return zobristHash == 0 || zobristHash == hash;
    }

    /**
     * Get a string representation for debugging
     */
    @Override
    public String toString() {
        String flagStr = flag == EXACT ? "EXACT" :
                flag == LOWER_BOUND ? "LOWER" : "UPPER";
        return String.format("TTEntry[score=%d, depth=%d, flag=%s, move=%s]",
                score, depth, flagStr, bestMove);
    }
}