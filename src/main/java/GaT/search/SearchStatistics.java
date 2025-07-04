package GaT.search;

import java.util.HashMap;
import java.util.Map;

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
    private long nullMoveCutoffs = 0;       // ✅ NEU
    private long nullMoveAttempts = 0;      // ✅ NEU
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

    public SearchStatistics() {
        reset();
    }

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
        nullMoveCutoffs = 0;        // ✅ NEU
        nullMoveAttempts = 0;       // ✅ NEU
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

    public void startSearch() {
        searchStartTime = System.currentTimeMillis();
    }

    public void endSearch() {
        if (searchStartTime > 0) {
            totalSearchTime = System.currentTimeMillis() - searchStartTime;
        }
    }

    public void startDepth(int depth) {
        if (depth < depthTimes.length) {
            depthTimes[depth] = System.currentTimeMillis();
        }
    }

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
    public void incrementNullMoveCutoffs() { nullMoveCutoffs++; }     // ✅ NEU
    public void incrementNullMoveAttempts() { nullMoveAttempts++; }   // ✅ NEU
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

    // ✅ NEU: Null Move Statistiken
    public long getNullMoveCutoffs() { return nullMoveCutoffs; }
    public long getNullMoveAttempts() { return nullMoveAttempts; }
    public double getNullMoveSuccessRate() {
        return nullMoveAttempts > 0 ? (double) nullMoveCutoffs / nullMoveAttempts : 0.0;
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

    public double getSearchEfficiency() {
        double ttEfficiency = getTTHitRate() * 20;
        double cutoffEfficiency = getCutoffRate() * 30;
        double branchingEfficiency = Math.max(0, 25 - getAverageBranchingFactor());
        double speedEfficiency = Math.min(25, getNodesPerSecond() / 10000);

        return Math.min(100, ttEfficiency + cutoffEfficiency + branchingEfficiency + speedEfficiency);
    }

    public Map<String, Integer> getTopMoves(int limit) {
        return moveFrequency.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(HashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        HashMap::putAll);
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCH STATISTICS ===\n");

        sb.append(String.format("Nodes: %,d (%.1fk NPS)\n",
                getTotalNodes(), getNodesPerSecond() / 1000));
        sb.append(String.format("  Regular: %,d, Quiescence: %,d\n", nodeCount, qNodeCount));
        sb.append(String.format("Max Depth: %d, Iterations: %d\n", maxDepthReached, iterationsCompleted));
        sb.append(String.format("Time: %,dms\n", totalSearchTime));

        sb.append(String.format("\nTT: %.1f%% hit rate (%,d hits, %,d misses)\n",
                getTTHitRate() * 100, ttHits, ttMisses));

        sb.append(String.format("\nPruning: %.1f%% cutoff rate\n", getCutoffRate() * 100));
        if (reverseFutilityCutoffs > 0) {
            sb.append(String.format("  RFP: %,d (%.1f%%)\n",
                    reverseFutilityCutoffs, 100.0 * reverseFutilityCutoffs / nodeCount));
        }
        // ✅ NEU: Null Move Statistiken
        if (nullMoveAttempts > 0) {
            sb.append(String.format("  Null Move: %,d/%,d (%.1f%% success)\n",
                    nullMoveCutoffs, nullMoveAttempts, getNullMoveSuccessRate() * 100));
        }
        if (futilityCutoffs > 0) {
            sb.append(String.format("  Futility: %,d (%.1f%%)\n",
                    futilityCutoffs, 100.0 * futilityCutoffs / nodeCount));
        }

        if (qNodeCount > 0) {
            sb.append(String.format("\nQuiescence:\n"));
            sb.append(String.format("  Stand-pat: %,d (%.1f%%)\n",
                    standPatCutoffs, 100.0 * standPatCutoffs / qNodeCount));
            sb.append(String.format("  Delta pruning: %,d (%.1f%%)\n",
                    deltaPruningCutoffs, 100.0 * deltaPruningCutoffs / qNodeCount));
        }

        long totalOrderingHits = ttMoveHits + killerMoveHits + historyMoveHits + captureOrderingHits;
        if (totalOrderingHits > 0) {
            sb.append(String.format("\nMove Ordering: %,d hits\n", totalOrderingHits));
            if (ttMoveHits > 0) sb.append(String.format("  TT moves: %,d\n", ttMoveHits));
            if (captureOrderingHits > 0) sb.append(String.format("  Captures: %,d\n", captureOrderingHits));
            if (killerMoveHits > 0) sb.append(String.format("  Killers: %,d\n", killerMoveHits));
            if (historyMoveHits > 0) sb.append(String.format("  History: %,d\n", historyMoveHits));
        }

        sb.append(String.format("\nEfficiency Score: %.1f/100\n", getSearchEfficiency()));

        return sb.toString();
    }

    public String getBriefSummary() {
        return String.format("Nodes: %,d, Time: %,dms, NPS: %.1fk, TT: %.1f%%, Cuts: %.1f%%, NMP: %.1f%%, Eff: %.1f",
                getTotalNodes(), totalSearchTime, getNodesPerSecond() / 1000,
                getTTHitRate() * 100, getCutoffRate() * 100, getNullMoveSuccessRate() * 100, getSearchEfficiency());
    }

    public String toCSV() {
        return String.format("%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f\n",
                nodeCount, qNodeCount, maxDepthReached, totalSearchTime,
                ttHits + ttMisses, getTTHitRate(), getCutoffRate(),
                getNullMoveSuccessRate(), getAverageBranchingFactor(), getSearchEfficiency());
    }

    public static String getCSVHeader() {
        return "nodes,qnodes,depth,time_ms,tt_probes,tt_hit_rate,cutoff_rate,nmp_success_rate,branching_factor,efficiency\n";
    }
}
