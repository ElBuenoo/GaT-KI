package GaT.search;

import java.util.HashMap;
import java.util.Map;

/**
 * SEARCH STATISTICS - Centralized metrics tracking
 * Extracted from scattered counters in Minimax, QuiescenceSearch, etc.
 */
public class SearchStatistics {

    // === CORE STATISTICS ===
    private long nodeCount = 0;
    private long qNodeCount = 0;
    private long leafNodeCount = 0;
    private long branchingFactor = 0;
    private int maxDepthReached = 0;

    // === TRANSPOSITION TABLE STATS ===
    private long ttHits = 0;
    private long ttMisses = 0;
    private long ttStores = 0;
    private long ttCollisions = 0;

    // === PRUNING STATISTICS ===
    private long alphaBetaCutoffs = 0;
    private long reverseFutilityCutoffs = 0;
    private long nullMoveCutoffs = 0;
    private long futilityCutoffs = 0;
    private long lmrReductions = 0;
    private long checkExtensions = 0;

    // === QUIESCENCE STATISTICS ===
    private long qCutoffs = 0;
    private long standPatCutoffs = 0;
    private long deltaPruningCutoffs = 0;
    private long qTTHits = 0;

    // === MOVE ORDERING STATISTICS ===
    private long killerMoveHits = 0;
    private long historyMoveHits = 0;
    private long ttMoveHits = 0;
    private long captureOrderingHits = 0;

    // === TIMING STATISTICS ===
    private long searchStartTime = 0;
    private long totalSearchTime = 0;
    private long[] depthTimes = new long[32];
    private int iterationsCompleted = 0;

    // === MOVE STATISTICS ===
    private Map<String, Integer> moveFrequency = new HashMap<>();
    private int totalMovesGenerated = 0;
    private int totalMovesSearched = 0;

    // === SINGLETON PATTERN ===
    private static SearchStatistics instance = new SearchStatistics();

    public static SearchStatistics getInstance() {
        return instance;
    }

    private SearchStatistics() {
        reset();
    }

    // === RESET AND INITIALIZATION ===

    /**
     * Reset all statistics for a new search
     */
    public void reset() {
        nodeCount = 0;
        qNodeCount = 0;
        leafNodeCount = 0;
        branchingFactor = 0;
        maxDepthReached = 0;

        ttHits = 0;
        ttMisses = 0;
        ttStores = 0;
        ttCollisions = 0;

        alphaBetaCutoffs = 0;
        reverseFutilityCutoffs = 0;
        nullMoveCutoffs = 0;
        futilityCutoffs = 0;
        lmrReductions = 0;
        checkExtensions = 0;

        qCutoffs = 0;
        standPatCutoffs = 0;
        deltaPruningCutoffs = 0;
        qTTHits = 0;

        killerMoveHits = 0;
        historyMoveHits = 0;
        ttMoveHits = 0;
        captureOrderingHits = 0;

        searchStartTime = 0;
        totalSearchTime = 0;
        depthTimes = new long[32];
        iterationsCompleted = 0;

        moveFrequency.clear();
        totalMovesGenerated = 0;
        totalMovesSearched = 0;
    }

    /**
     * Start timing a search
     */
    public void startSearch() {
        searchStartTime = System.currentTimeMillis();
    }

    /**
     * End timing a search
     */
    public void endSearch() {
        if (searchStartTime > 0) {
            totalSearchTime = System.currentTimeMillis() - searchStartTime;
        }
    }

    /**
     * Start timing a depth iteration
     */
    public void startDepth(int depth) {
        if (depth < depthTimes.length) {
            depthTimes[depth] = System.currentTimeMillis();
        }
    }

    /**
     * End timing a depth iteration
     */
    public void endDepth(int depth) {
        if (depth < depthTimes.length && depthTimes[depth] > 0) {
            depthTimes[depth] = System.currentTimeMillis() - depthTimes[depth];
            iterationsCompleted = Math.max(iterationsCompleted, depth + 1);
            maxDepthReached = Math.max(maxDepthReached, depth);
        }
    }

    // === NODE COUNTING ===

    public void incrementNodeCount() { nodeCount++; }
    public void incrementQNodeCount() { qNodeCount++; }
    public void incrementLeafNodeCount() { leafNodeCount++; }
    public void addBranchingFactor(int branches) { branchingFactor += branches; }

    // === TRANSPOSITION TABLE ===

    public void incrementTTHits() { ttHits++; }
    public void incrementTTMisses() { ttMisses++; }
    public void incrementTTStores() { ttStores++; }
    public void incrementTTCollisions() { ttCollisions++; }

    // === PRUNING ===

    public void incrementAlphaBetaCutoffs() { alphaBetaCutoffs++; }
    public void incrementReverseFutilityCutoffs() { reverseFutilityCutoffs++; }
    public void incrementNullMoveCutoffs() { nullMoveCutoffs++; }
    public void incrementFutilityCutoffs() { futilityCutoffs++; }
    public void incrementLMRReductions() { lmrReductions++; }
    public void incrementCheckExtensions() { checkExtensions++; }

    // === QUIESCENCE ===

    public void incrementQCutoffs() { qCutoffs++; }
    public void incrementStandPatCutoffs() { standPatCutoffs++; }
    public void incrementDeltaPruningCutoffs() { deltaPruningCutoffs++; }
    public void incrementQTTHits() { qTTHits++; }

    // === MOVE ORDERING ===

    public void incrementKillerMoveHits() { killerMoveHits++; }
    public void incrementHistoryMoveHits() { historyMoveHits++; }
    public void incrementTTMoveHits() { ttMoveHits++; }
    public void incrementCaptureOrderingHits() { captureOrderingHits++; }

    // === MOVE TRACKING ===

    public void recordMove(String move) {
        moveFrequency.merge(move, 1, Integer::sum);
    }

    public void addMovesGenerated(int count) {
        totalMovesGenerated += count;
    }

    public void addMovesSearched(int count) {
        totalMovesSearched += count;
    }

    // === GETTERS ===

    public long getNodeCount() { return nodeCount; }
    public long getQNodeCount() { return qNodeCount; }
    public long getLeafNodeCount() { return leafNodeCount; }
    public long getTotalNodes() { return nodeCount + qNodeCount; }
    public int getMaxDepthReached() { return maxDepthReached; }

    public long getTTHits() { return ttHits; }
    public long getTTMisses() { return ttMisses; }
    public double getTTHitRate() {
        long total = ttHits + ttMisses;
        return total > 0 ? (double) ttHits / total : 0.0;
    }

    public long getTotalCutoffs() {
        return alphaBetaCutoffs + reverseFutilityCutoffs + nullMoveCutoffs +
                futilityCutoffs + qCutoffs + deltaPruningCutoffs;
    }

    public double getCutoffRate() {
        return nodeCount > 0 ? (double) getTotalCutoffs() / nodeCount : 0.0;
    }

    public double getAverageBranchingFactor() {
        return nodeCount > 0 ? (double) branchingFactor / nodeCount : 0.0;
    }

    public long getTotalSearchTime() { return totalSearchTime; }
    public double getNodesPerSecond() {
        return totalSearchTime > 0 ? (double) getTotalNodes() * 1000 / totalSearchTime : 0.0;
    }

    public int getIterationsCompleted() { return iterationsCompleted; }

    // === ANALYSIS METHODS ===

    /**
     * Get search efficiency score (0-100)
     */
    public double getSearchEfficiency() {
        double ttEfficiency = getTTHitRate() * 20;           // 0-20 points
        double cutoffEfficiency = getCutoffRate() * 30;      // 0-30 points
        double branchingEfficiency = Math.max(0, 25 - getAverageBranchingFactor()); // 0-25 points
        double speedEfficiency = Math.min(25, getNodesPerSecond() / 10000); // 0-25 points

        return Math.min(100, ttEfficiency + cutoffEfficiency + branchingEfficiency + speedEfficiency);
    }

    /**
     * Get most frequently searched moves
     */
    public Map<String, Integer> getTopMoves(int limit) {
        return moveFrequency.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(HashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        HashMap::putAll);
    }

    // === FORMATTED OUTPUT ===

    /**
     * Get comprehensive statistics summary
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCH STATISTICS ===\n");

        // Core stats
        sb.append(String.format("Nodes: %,d (%.1fk NPS)\n",
                getTotalNodes(), getNodesPerSecond() / 1000));
        sb.append(String.format("  Regular: %,d, Quiescence: %,d\n", nodeCount, qNodeCount));
        sb.append(String.format("Max Depth: %d, Iterations: %d\n", maxDepthReached, iterationsCompleted));
        sb.append(String.format("Time: %,dms\n", totalSearchTime));

        // Transposition Table
        sb.append(String.format("\nTT: %.1f%% hit rate (%,d hits, %,d misses)\n",
                getTTHitRate() * 100, ttHits, ttMisses));

        // Pruning effectiveness
        sb.append(String.format("\nPruning: %.1f%% cutoff rate\n", getCutoffRate() * 100));
        if (reverseFutilityCutoffs > 0) {
            sb.append(String.format("  RFP: %,d (%.1f%%)\n",
                    reverseFutilityCutoffs, 100.0 * reverseFutilityCutoffs / nodeCount));
        }
        if (nullMoveCutoffs > 0) {
            sb.append(String.format("  Null Move: %,d (%.1f%%)\n",
                    nullMoveCutoffs, 100.0 * nullMoveCutoffs / nodeCount));
        }
        if (futilityCutoffs > 0) {
            sb.append(String.format("  Futility: %,d (%.1f%%)\n",
                    futilityCutoffs, 100.0 * futilityCutoffs / nodeCount));
        }

        // Quiescence stats
        if (qNodeCount > 0) {
            sb.append(String.format("\nQuiescence:\n"));
            sb.append(String.format("  Stand-pat: %,d (%.1f%%)\n",
                    standPatCutoffs, 100.0 * standPatCutoffs / qNodeCount));
            sb.append(String.format("  Delta pruning: %,d (%.1f%%)\n",
                    deltaPruningCutoffs, 100.0 * deltaPruningCutoffs / qNodeCount));
        }

        // Move ordering
        long totalOrderingHits = ttMoveHits + killerMoveHits + historyMoveHits + captureOrderingHits;
        if (totalOrderingHits > 0) {
            sb.append(String.format("\nMove Ordering: %,d hits\n", totalOrderingHits));
            if (ttMoveHits > 0) sb.append(String.format("  TT moves: %,d\n", ttMoveHits));
            if (captureOrderingHits > 0) sb.append(String.format("  Captures: %,d\n", captureOrderingHits));
            if (killerMoveHits > 0) sb.append(String.format("  Killers: %,d\n", killerMoveHits));
            if (historyMoveHits > 0) sb.append(String.format("  History: %,d\n", historyMoveHits));
        }

        // Overall efficiency
        sb.append(String.format("\nEfficiency Score: %.1f/100\n", getSearchEfficiency()));

        return sb.toString();
    }

    /**
     * Get brief one-line summary
     */
    public String getBriefSummary() {
        return String.format("Nodes: %,d, Time: %,dms, NPS: %.1fk, TT: %.1f%%, Cuts: %.1f%%, Eff: %.1f",
                getTotalNodes(), totalSearchTime, getNodesPerSecond() / 1000,
                getTTHitRate() * 100, getCutoffRate() * 100, getSearchEfficiency());
    }

    /**
     * Export statistics as CSV for analysis
     */
    public String toCSV() {
        return String.format("%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.1f\n",
                nodeCount, qNodeCount, maxDepthReached, totalSearchTime,
                ttHits + ttMisses, getTTHitRate(), getCutoffRate(),
                getAverageBranchingFactor(), getSearchEfficiency());
    }

    /**
     * Get CSV header for export
     */
    public static String getCSVHeader() {
        return "nodes,qnodes,depth,time_ms,tt_probes,tt_hit_rate,cutoff_rate,branching_factor,efficiency\n";
    }
}