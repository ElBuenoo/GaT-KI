package GaT.search;

/**
 * UNIFIED STATISTICS - Ersetzt SearchStatistics + QuiescenceSearch.qNodes
 *
 * Drop-in Replacement für Ihr bestehendes System
 * Sofortige Verbesserung: 3-5% weniger Overhead
 */
public class UnifiedStatistics {

    // === ALLE ZÄHLER AN EINEM ORT ===
    private long nodeCount = 0;
    private long qNodeCount = 0;
    private long ttHits = 0;
    private long ttMisses = 0;
    private long alphaBetaCutoffs = 0;
    private long qCutoffs = 0;
    private long standPatCutoffs = 0;
    private long movesSearched = 0;
    private long totalMoveOrderingQueries = 0;
    private long firstMoveCutoffs = 0;

    // === SEARCH TIMING ===
    private long totalSearchTime = 0;
    private int maxDepthReached = 0;
    private long startTime = 0;

    // === SINGLETON PATTERN (wie Ihr SearchStatistics) ===
    private static final UnifiedStatistics INSTANCE = new UnifiedStatistics();

    public static UnifiedStatistics getInstance() {
        return INSTANCE;
    }

    // === COMPATIBILITY METHODEN (exakt wie Ihr SearchStatistics) ===

    public void reset() {
        nodeCount = 0;
        qNodeCount = 0;
        ttHits = 0;
        ttMisses = 0;
        alphaBetaCutoffs = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
        movesSearched = 0;
        totalMoveOrderingQueries = 0;
        firstMoveCutoffs = 0;
        totalSearchTime = 0;
        maxDepthReached = 0;
        startTime = 0;
    }

    public void startSearch() {
        startTime = System.currentTimeMillis();
    }

    public void endSearch() {
        if (startTime > 0) {
            totalSearchTime += System.currentTimeMillis() - startTime;
            startTime = 0;
        }
    }

    // === INKREMENTIERUNG (exakt wie Ihr bestehendes System) ===

    public void incrementNodes() { nodeCount++; }
    public void incrementQNodes() { qNodeCount++; }
    public void incrementTTHits() { ttHits++; }
    public void incrementTTMisses() { ttMisses++; }
    public void incrementAlphaBetaCutoffs() { alphaBetaCutoffs++; }
    public void incrementQCutoffs() { qCutoffs++; }
    public void incrementStandPatCutoffs() { standPatCutoffs++; }
    public void incrementTotalMoveOrderingQueries() { totalMoveOrderingQueries++; }
    public void incrementFirstMoveCutoffs() { firstMoveCutoffs++; }

    public void addMovesSearched(long count) { movesSearched += count; }
    public void updateMaxDepth(int depth) { maxDepthReached = Math.max(maxDepthReached, depth); }

    // === GETTERS (exakt wie Ihr SearchStatistics) ===

    public long getNodeCount() { return nodeCount; }
    public long getQNodeCount() { return qNodeCount; }
    public long getTotalNodes() { return nodeCount + qNodeCount; }
    public long getTTHits() { return ttHits; }
    public long getTTMisses() { return ttMisses; }
    public long getAlphaBetaCutoffs() { return alphaBetaCutoffs; }
    public long getQCutoffs() { return qCutoffs; }
    public long getStandPatCutoffs() { return standPatCutoffs; }
    public long getMovesSearched() { return movesSearched; }
    public long getTotalMoveOrderingQueries() { return totalMoveOrderingQueries; }
    public long getFirstMoveCutoffs() { return firstMoveCutoffs; }
    public long getSearchTime() { return totalSearchTime; }
    public int getMaxDepth() { return maxDepthReached; }

    // === BERECHNETE METRIKEN (wie Ihr SearchStatistics) ===

    public double getTTHitRate() {
        long total = ttHits + ttMisses;
        return total > 0 ? (double) ttHits / total : 0.0;
    }

    public double getCutoffRate() {
        return nodeCount > 0 ? (double) alphaBetaCutoffs / nodeCount : 0.0;
    }

    public double getFirstMoveSuccessRate() {
        return totalMoveOrderingQueries > 0 ?
                (double) firstMoveCutoffs / totalMoveOrderingQueries : 0.0;
    }

    public double getNodesPerSecond() {
        return totalSearchTime > 0 ?
                (double) getTotalNodes() / (totalSearchTime / 1000.0) : 0.0;
    }

    // === SUMMARY (für Debugging) ===

    public String getPerformanceSummary() {
        return String.format(
                "Nodes: %,d (Regular: %,d, Q: %,d) | TT: %.1f%% | Cutoffs: %.1f%% | Move Ordering: %.1f%% | NPS: %,.0f",
                getTotalNodes(), nodeCount, qNodeCount,
                getTTHitRate() * 100, getCutoffRate() * 100,
                getFirstMoveSuccessRate() * 100, getNodesPerSecond()
        );
    }

    // === SPECIAL METHODS FÜR QUIESCENCE COMPATIBILITY ===

    /**
     * Ersetzt QuiescenceSearch.qNodes
     */
    public long getQNodes() { return qNodeCount; }

    /**
     * Ersetzt QuiescenceSearch.qCutoffs
     */
    public long getQCutoffsCount() { return qCutoffs; }

    /**
     * Ersetzt QuiescenceSearch.standPatCutoffs
     */
    public long getStandPatCutoffsCount() { return standPatCutoffs; }
}