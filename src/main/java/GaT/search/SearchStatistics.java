package GaT.search;

import java.util.HashMap;
import java.util.Map;

import static GaT.engine.TimedMinimax.getElapsedTime;


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
    private long lmrReSearches = 0;

    private long movesSearched = 0;

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
    public void incrementTTCollisions() { ttCollisions++; }

    // === PRUNING ===

    public void incrementAlphaBetaCutoffs() { alphaBetaCutoffs++; }
    public void incrementReverseFutilityCutoffs() { reverseFutilityCutoffs++; }

    public void incrementCheckExtensions() { checkExtensions++; }

    // === QUIESCENCE ===

    public void incrementQCutoffs() { qCutoffs++; }
    public void incrementStandPatCutoffs() { standPatCutoffs++; }
    public void incrementDeltaPruningCutoffs() { deltaPruningCutoffs++; }
    public void incrementQTTHits() { qTTHits++; }

    // === MOVE ORDERING ===


    public void incrementTTMoveHits() { ttMoveHits++; }
    public void incrementCaptureOrderingHits() { captureOrderingHits++; }

    // === MOVE TRACKING ===

    public void recordMove(String move) {
        moveFrequency.merge(move, 1, Integer::sum);
    }

    public void addMovesGenerated(int count) {
        totalMovesGenerated += count;
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

    // ADD THESE METHODS TO YOUR EXISTING SearchStatistics.java CLASS

    /**
     * Get Late Move Reductions count
     */
    public long getLMRReductions() {
        return lmrReductions;
    }

    /**
     * Increment Late Move Reductions
     */
    public void incrementLMRReductions() {
        lmrReductions++;
    }

    /**
     * Get LMR Re-searches count
     */
    public long getLMRReSearches() {
        return lmrReSearches;
    }

    /**
     * Increment LMR Re-searches
     */
    public void incrementLMRReSearches() {
        lmrReSearches++;
    }

    /**
     * Get Null Move cutoffs count
     */
    public long getNullMoveCutoffs() {
        return nullMoveCutoffs;
    }

    /**
     * Increment Null Move cutoffs
     */
    public void incrementNullMoveCutoffs() {
        nullMoveCutoffs++;
    }

    /**
     * Get Futility cutoffs count
     */
    public long getFutilityCutoffs() {
        return futilityCutoffs;
    }

    /**
     * Increment Futility cutoffs
     */
    public void incrementFutilityCutoffs() {
        futilityCutoffs++;
    }

    /**
     * Get TT stores count
     */
    public long getTTStores() {
        return ttStores;
    }

    /**
     * Increment TT stores
     */
    public void incrementTTStores() {
        ttStores++;
    }

    /**
     * Get Killer Move hits count
     */
    public long getKillerMoveHits() {
        return killerMoveHits;
    }

    /**
     * Increment Killer Move hits
     */
    public void incrementKillerMoveHits() {
        killerMoveHits++;
    }

    /**
     * Get History Move hits count
     */
    public long getHistoryMoveHits() {
        return historyMoveHits;
    }

    /**
     * Increment History Move hits
     */
    public void incrementHistoryMoveHits() {
        historyMoveHits++;
    }

    /**
     * Add to moves searched count
     */
    public void addMovesSearched(int count) {
        movesSearched += count;
    }

    /**
     * Get total moves searched
     */
    public long getMovesSearched() {
        return movesSearched;
    }

    /**
     * Get search efficiency (nodes per second)
     */
    public double getSearchEfficiency() {
        long elapsed = getElapsedTime();
        if (elapsed > 0) {
            return (double) getTotalNodes() * 1000.0 / elapsed;
        }
        return 0.0;
    }

    /**
     * Get move ordering efficiency
     */
    public double getMoveOrderingEfficiency() {
        long totalOrderingAttempts = killerMoveHits + historyMoveHits + captureOrderingHits;
        if (alphaBetaCutoffs > 0) {
            return (double) totalOrderingAttempts / alphaBetaCutoffs;
        }
        return 0.0;
    }

    /**
     * Get pruning effectiveness
     */
    public double getPruningEffectiveness() {
        long totalPruning = nullMoveCutoffs + futilityCutoffs + reverseFutilityCutoffs + lmrReductions;
        if (nodeCount > 0) {
            return (double) totalPruning / nodeCount;
        }
        return 0.0;
    }

    /**
     * Enhanced summary with all tactical features
     */
    public String getEnhancedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ENHANCED SEARCH STATISTICS ===\n");

        // Basic stats
        sb.append(String.format("Nodes: %,d (Regular: %,d, Q: %,d)\n",
                getTotalNodes(), nodeCount, qNodeCount));
        sb.append(String.format("Time: %,dms (%.1f NPS)\n",
                getElapsedTime(), getSearchEfficiency()));

        // Search depth and iterations
        sb.append(String.format("Max Depth: %d, Iterations: %d\n",
                maxDepthReached, iterationsCompleted));

        // Transposition Table
        sb.append(String.format("TT: %.1f%% hit rate (%,d hits, %,d stores)\n",
                getTTHitRate() * 100, ttHits, ttStores));

        // Pruning statistics
        sb.append(String.format("Pruning Effectiveness: %.1f%%\n", getPruningEffectiveness() * 100));
        if (nullMoveCutoffs > 0) {
            sb.append(String.format("  Null Move: %,d cutoffs\n", nullMoveCutoffs));
        }
        if (lmrReductions > 0) {
            sb.append(String.format("  LMR: %,d reductions, %,d re-searches\n",
                    lmrReductions, lmrReSearches));
        }
        if (futilityCutoffs > 0) {
            sb.append(String.format("  Futility: %,d cutoffs\n", futilityCutoffs));
        }
        if (reverseFutilityCutoffs > 0) {
            sb.append(String.format("  Reverse Futility: %,d cutoffs\n", reverseFutilityCutoffs));
        }

        // Move ordering
        sb.append(String.format("Move Ordering Efficiency: %.1f%%\n", getMoveOrderingEfficiency() * 100));
        if (killerMoveHits > 0) {
            sb.append(String.format("  Killer Moves: %,d hits\n", killerMoveHits));
        }
        if (historyMoveHits > 0) {
            sb.append(String.format("  History: %,d hits\n", historyMoveHits));
        }
        if (captureOrderingHits > 0) {
            sb.append(String.format("  Capture Ordering: %,d hits\n", captureOrderingHits));
        }

        // Quiescence details
        if (qNodeCount > 0) {
            sb.append(String.format("Quiescence: %,d nodes\n", qNodeCount));
            if (standPatCutoffs > 0) {
                sb.append(String.format("  Stand Pat: %,d cutoffs\n", standPatCutoffs));
            }
            if (deltaPruningCutoffs > 0) {
                sb.append(String.format("  Delta Pruning: %,d cutoffs\n", deltaPruningCutoffs));
            }
        }

        return sb.toString();
    }

    /**
     * Performance analysis report
     */
    public String getPerformanceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PERFORMANCE ANALYSIS ===\n");

        double nps = getSearchEfficiency();
        sb.append(String.format("Search Speed: %.0f NPS\n", nps));

        if (nps > 100000) {
            sb.append("🚀 Excellent search speed!\n");
        } else if (nps > 50000) {
            sb.append("✅ Good search speed\n");
        } else if (nps > 10000) {
            sb.append("⚠️ Moderate search speed\n");
        } else {
            sb.append("🐌 Slow search speed - consider optimization\n");
        }

        double pruningEff = getPruningEffectiveness();
        sb.append(String.format("Pruning: %.1f%% effectiveness\n", pruningEff * 100));

        if (pruningEff > 0.3) {
            sb.append("🔥 Excellent pruning!\n");
        } else if (pruningEff > 0.2) {
            sb.append("✅ Good pruning\n");
        } else if (pruningEff > 0.1) {
            sb.append("⚠️ Moderate pruning\n");
        } else {
            sb.append("🐌 Poor pruning - check algorithms\n");
        }

        double ttHitRate = getTTHitRate();
        sb.append(String.format("TT Hit Rate: %.1f%%\n", ttHitRate * 100));

        if (ttHitRate > 0.5) {
            sb.append("🎯 Excellent TT utilization!\n");
        } else if (ttHitRate > 0.3) {
            sb.append("✅ Good TT utilization\n");
        } else if (ttHitRate > 0.1) {
            sb.append("⚠️ Moderate TT utilization\n");
        } else {
            sb.append("🔍 Poor TT utilization - check hash function\n");
        }

        return sb.toString();
    }

    /**
     * Tactical features usage report
     */
    public String getTacticalReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TACTICAL FEATURES USAGE ===\n");

        // Null Move Pruning
        if (nullMoveCutoffs > 0) {
            double nullMoveRate = (double) nullMoveCutoffs / nodeCount;
            sb.append(String.format("Null Move Pruning: %,d cutoffs (%.1f%% of nodes)\n",
                    nullMoveCutoffs, nullMoveRate * 100));
            if (nullMoveRate > 0.1) {
                sb.append("  🎯 Excellent null move usage!\n");
            } else {
                sb.append("  ⚠️ Consider tuning null move parameters\n");
            }
        } else {
            sb.append("Null Move Pruning: Not used\n");
        }

        // Late Move Reductions
        if (lmrReductions > 0) {
            double lmrRate = (double) lmrReductions / movesSearched;
            double reSearchRate = (double) lmrReSearches / lmrReductions;
            sb.append(String.format("Late Move Reductions: %,d reductions (%.1f%% re-search rate)\n",
                    lmrReductions, reSearchRate * 100));
            if (reSearchRate < 0.3) {
                sb.append("  🎯 Excellent LMR efficiency!\n");
            } else if (reSearchRate < 0.5) {
                sb.append("  ✅ Good LMR efficiency\n");
            } else {
                sb.append("  ⚠️ High re-search rate - consider tuning LMR\n");
            }
        } else {
            sb.append("Late Move Reductions: Not used\n");
        }

        // Futility Pruning
        if (futilityCutoffs > 0) {
            double futilityRate = (double) futilityCutoffs / nodeCount;
            sb.append(String.format("Futility Pruning: %,d cutoffs (%.1f%% of nodes)\n",
                    futilityCutoffs, futilityRate * 100));
        }

        return sb.toString();
    }







}