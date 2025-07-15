package GaT.search;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single statistics system - replaces SearchStatistics + QuiescenceSearch counters
 * Thread-safe with atomic operations
 */
public class UnifiedStatistics {

    // === CORE NODE COUNTERS ===
    private final AtomicLong regularNodes = new AtomicLong(0);
    private final AtomicLong quiescenceNodes = new AtomicLong(0);
    private final AtomicLong leafNodes = new AtomicLong(0);

    // === PRUNING COUNTERS ===
    private final AtomicLong alphaBetaCutoffs = new AtomicLong(0);
    private final AtomicLong nullMovePrunes = new AtomicLong(0);
    private final AtomicLong nullMoveAttempts = new AtomicLong(0);

    // === TRANSPOSITION TABLE ===
    private final AtomicLong ttHits = new AtomicLong(0);
    private final AtomicLong ttMisses = new AtomicLong(0);

    // === MOVE ORDERING ===
    private final AtomicLong firstMoveCutoffs = new AtomicLong(0);
    private final AtomicLong totalMoveOrderingQueries = new AtomicLong(0);

    // === TIMING ===
    private long searchStartTime;
    private long totalSearchTime;
    private int maxDepthReached;

    // === SINGLETON ===
    private static final UnifiedStatistics instance = new UnifiedStatistics();
    public static UnifiedStatistics getInstance() { return instance; }

    // === CORE OPERATIONS ===
    public void reset() {
        regularNodes.set(0);
        quiescenceNodes.set(0);
        leafNodes.set(0);
        alphaBetaCutoffs.set(0);
        nullMovePrunes.set(0);
        nullMoveAttempts.set(0);
        ttHits.set(0);
        ttMisses.set(0);
        firstMoveCutoffs.set(0);
        totalMoveOrderingQueries.set(0);
        totalSearchTime = 0;
        maxDepthReached = 0;
    }

    public void startSearch() {
        searchStartTime = System.currentTimeMillis();
    }

    public void endSearch() {
        totalSearchTime = System.currentTimeMillis() - searchStartTime;
    }

    // === INCREMENT METHODS ===
    public void incrementRegularNode() { regularNodes.incrementAndGet(); }
    public void incrementQuiescenceNode() { quiescenceNodes.incrementAndGet(); }
    public void incrementLeafNode() { leafNodes.incrementAndGet(); }
    public void incrementAlphaBetaCutoff() { alphaBetaCutoffs.incrementAndGet(); }
    public void incrementNullMovePrune() { nullMovePrunes.incrementAndGet(); }
    public void incrementNullMoveAttempt() { nullMoveAttempts.incrementAndGet(); }
    public void incrementTTHit() { ttHits.incrementAndGet(); }
    public void incrementTTMiss() { ttMisses.incrementAndGet(); }
    public void incrementFirstMoveCutoff() { firstMoveCutoffs.incrementAndGet(); }
    public void incrementMoveOrderingQuery() { totalMoveOrderingQueries.incrementAndGet(); }

    // === GETTERS ===
    public long getRegularNodes() { return regularNodes.get(); }
    public long getQuiescenceNodes() { return quiescenceNodes.get(); }
    public long getTotalNodes() { return regularNodes.get() + quiescenceNodes.get(); }
    public long getAlphaBetaCutoffs() { return alphaBetaCutoffs.get(); }
    public long getTTHits() { return ttHits.get(); }
    public long getTTMisses() { return ttMisses.get(); }
    public long getTotalSearchTime() { return totalSearchTime; }
    public int getMaxDepthReached() { return maxDepthReached; }

    public void setMaxDepthReached(int depth) {
        this.maxDepthReached = Math.max(this.maxDepthReached, depth);
    }

    // === CALCULATED METRICS ===
    public double getTTHitRate() {
        long total = ttHits.get() + ttMisses.get();
        return total > 0 ? (double) ttHits.get() / total : 0.0;
    }

    public double getCutoffRate() {
        long regular = regularNodes.get();
        return regular > 0 ? (double) alphaBetaCutoffs.get() / regular : 0.0;
    }

    public double getFirstMoveSuccessRate() {
        long total = totalMoveOrderingQueries.get();
        return total > 0 ? (double) firstMoveCutoffs.get() / total : 0.0;
    }

    public double getNodesPerSecond() {
        return totalSearchTime > 0 ? (double) getTotalNodes() * 1000 / totalSearchTime : 0;
    }

    // === SUMMARY ===
    public String getBriefSummary() {
        return String.format("Nodes: %,d (%,d reg + %,d q), Time: %,dms, NPS: %.0f, TT: %.1f%%, Cuts: %.1f%%",
                getTotalNodes(), getRegularNodes(), getQuiescenceNodes(),
                totalSearchTime, getNodesPerSecond(), getTTHitRate() * 100, getCutoffRate() * 100);
    }
}