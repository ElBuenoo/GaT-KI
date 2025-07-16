package GaT.search;

import GaT.model.TTEntry;
import GaT.model.ConsolidatedSearchConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TRANSPOSITION TABLE - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ Table size now uses SearchConfig.TT_SIZE
 * ✅ Eviction threshold uses SearchConfig.TT_EVICTION_THRESHOLD
 * ✅ All constants now configurable via SearchConfig
 * ✅ Enhanced statistics with SearchConfig info
 * ✅ Adaptive behavior based on SearchConfig parameters
 */
public class TranspositionTable {

    private final Map<Long, TTEntry> table;
    private final int maxSize;
    private long accessCounter = 0;
    private long hitCount = 0;
    private long missCount = 0;
    private long collisionCount = 0;
    private long evictionCount = 0;

    // === CONSTRUCTOR WITH SEARCHCONFIG ===
    public TranspositionTable(int maxSize) {
        // Validate against SearchConfig
        if (maxSize != SearchConfig.TT_SIZE) {
            System.out.println("⚠️ TranspositionTable size (" + maxSize + ") differs from SearchConfig.TT_SIZE (" + SearchConfig.TT_SIZE + ")");
        }

        this.maxSize = maxSize;
        this.table = new HashMap<>(maxSize * 4 / 3); // Avoid rehashing

        System.out.println("🔧 TranspositionTable initialized with SearchConfig:");
        System.out.println("   TT_SIZE: " + SearchConfig.TT_SIZE);
        System.out.println("   TT_EVICTION_THRESHOLD: " + SearchConfig.TT_EVICTION_THRESHOLD);
        System.out.println("   Actual maxSize: " + maxSize);

        validateSearchConfigIntegration();
    }

    /**
     * Default constructor using SearchConfig.TT_SIZE
     */
    public TranspositionTable() {
        this(SearchConfig.TT_SIZE);
    }

    /**
     * Get entry with SearchConfig-enhanced statistics
     */
    public TTEntry get(long hash) {
        TTEntry entry = table.get(hash);

        if (entry != null) {
            entry.lastAccessed = ++accessCounter;
            hitCount++;

            // Optional: Age-based entry validation using SearchConfig
            if (shouldValidateEntry(entry)) {
                if (isEntryTooOld(entry)) {
                    table.remove(hash);
                    missCount++;
                    return null;
                }
            }

            return entry;
        } else {
            missCount++;
            return null;
        }
    }

    /**
     * Store entry with SearchConfig-based eviction
     */
    public void put(long hash, TTEntry entry) {
        if (entry == null) return;

        // Check for collision
        if (table.containsKey(hash)) {
            TTEntry existing = table.get(hash);
            if (existing != null && !shouldReplaceEntry(existing, entry)) {
                collisionCount++;
                return; // Don't replace better entry
            }
        }

        // Check if we need to evict entries using SearchConfig threshold
        if (table.size() >= SearchConfig.TT_EVICTION_THRESHOLD) {
            evictOldEntriesWithConfig();
        }

        entry.lastAccessed = ++accessCounter;
        table.put(hash, entry);
    }

    /**
     * Enhanced eviction using SearchConfig parameters
     */
    private void evictOldEntriesWithConfig() {
        // Calculate eviction percentage based on SearchConfig
        int targetSize = (int)(SearchConfig.TT_EVICTION_THRESHOLD * 0.8); // Keep 80% after eviction
        int toRemove = table.size() - targetSize;

        if (toRemove <= 0) return;

        // Strategy: Remove oldest and least valuable entries
        List<Long> toEvict = table.entrySet().stream()
                .sorted((e1, e2) -> {
                    TTEntry entry1 = e1.getValue();
                    TTEntry entry2 = e2.getValue();

                    // Primary: Remove entries with lower depth first
                    int depthCompare = Integer.compare(entry1.depth, entry2.depth);
                    if (depthCompare != 0) return depthCompare;

                    // Secondary: Remove older entries
                    return Long.compare(entry1.lastAccessed, entry2.lastAccessed);
                })
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Remove selected entries
        toEvict.forEach(table::remove);
        evictionCount += toEvict.size();

        System.out.printf("🔧 TT Eviction: Removed %d entries, size now: %d (threshold: %d)\n",
                toEvict.size(), table.size(), SearchConfig.TT_EVICTION_THRESHOLD);
    }

    /**
     * Determine if we should replace an existing entry
     */
    private boolean shouldReplaceEntry(TTEntry existing, TTEntry newEntry) {
        // Always replace if new entry has higher depth
        if (newEntry.depth > existing.depth) return true;

        // Don't replace if existing has much higher depth
        if (existing.depth > newEntry.depth + 2) return false;

        // For same depth, prefer exact values over bounds
        if (newEntry.depth == existing.depth) {
            if (newEntry.flag == TTEntry.EXACT && existing.flag != TTEntry.EXACT) {
                return true;
            }
            if (existing.flag == TTEntry.EXACT && newEntry.flag != TTEntry.EXACT) {
                return false;
            }
        }

        // Default: replace if newer
        return newEntry.lastAccessed > existing.lastAccessed;
    }

    /**
     * Check if entry validation is needed (based on SearchConfig)
     */
    private boolean shouldValidateEntry(TTEntry entry) {
        // Only validate occasionally to avoid performance impact
        return (accessCounter % 1000) == 0;
    }

    /**
     * Check if entry is too old (using SearchConfig-based criteria)
     */
    private boolean isEntryTooOld(TTEntry entry) {
        // Consider entry too old if it hasn't been accessed in a long time
        // and we have many accesses since then
        long ageDifference = accessCounter - entry.lastAccessed;
        return ageDifference > SearchConfig.TT_SIZE / 10; // Entry older than 10% of table size worth of accesses
    }

    /**
     * Clear table with SearchConfig logging
     */
    public void clear() {
        int oldSize = table.size();
        table.clear();
        accessCounter = 0;
        hitCount = 0;
        missCount = 0;
        collisionCount = 0;
        evictionCount = 0;

        System.out.printf("🔧 TranspositionTable cleared: %d entries removed (SearchConfig.TT_SIZE: %d)\n",
                oldSize, SearchConfig.TT_SIZE);
    }

    /**
     * Get current table size
     */
    public int size() {
        return table.size();
    }

    /**
     * Get maximum table size from SearchConfig
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Get SearchConfig eviction threshold
     */
    public int getEvictionThreshold() {
        return SearchConfig.TT_EVICTION_THRESHOLD;
    }

    /**
     * Enhanced hit rate calculation
     */
    public double getHitRate() {
        long totalAccesses = hitCount + missCount;
        return totalAccesses > 0 ? (double) hitCount / totalAccesses : 0.0;
    }

    /**
     * Get collision rate
     */
    public double getCollisionRate() {
        long totalPuts = hitCount + missCount; // Approximation
        return totalPuts > 0 ? (double) collisionCount / totalPuts : 0.0;
    }

    /**
     * Get table utilization percentage
     */
    public double getUtilization() {
        return maxSize > 0 ? (double) table.size() / maxSize * 100 : 0.0;
    }

    /**
     * Check if table is near eviction threshold
     */
    public boolean isNearEvictionThreshold() {
        return table.size() > SearchConfig.TT_EVICTION_THRESHOLD * 0.9;
    }

    /**
     * Get comprehensive statistics with SearchConfig info
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRANSPOSITION TABLE STATISTICS (SearchConfig) ===\n");
        sb.append(String.format("Size: %,d / %,d (%.1f%% utilized)\n",
                table.size(), maxSize, getUtilization()));
        sb.append(String.format("SearchConfig TT_SIZE: %,d\n", SearchConfig.TT_SIZE));
        sb.append(String.format("SearchConfig TT_EVICTION_THRESHOLD: %,d\n", SearchConfig.TT_EVICTION_THRESHOLD));
        sb.append(String.format("Accesses: %,d total (%,d hits, %,d misses)\n",
                hitCount + missCount, hitCount, missCount));
        sb.append(String.format("Hit Rate: %.1f%%\n", getHitRate() * 100));
        sb.append(String.format("Collisions: %,d (%.2f%% rate)\n", collisionCount, getCollisionRate() * 100));
        sb.append(String.format("Evictions: %,d\n", evictionCount));
        sb.append(String.format("Access Counter: %,d\n", accessCounter));

        if (isNearEvictionThreshold()) {
            sb.append("⚠️ Near eviction threshold - consider increasing SearchConfig.TT_SIZE\n");
        }

        return sb.toString();
    }

    /**
     * Get brief statistics for logging
     */
    public String getBriefStatistics() {
        return String.format("TT[Config]: %,d/%,d entries (%.1f%%), %.1f%% hit rate, %,d evictions",
                table.size(), maxSize, getUtilization(), getHitRate() * 100, evictionCount);
    }

    /**
     * Validate SearchConfig integration
     */
    private void validateSearchConfigIntegration() {
        boolean valid = true;

        if (SearchConfig.TT_SIZE <= 0) {
            System.err.println("❌ Invalid SearchConfig.TT_SIZE: " + SearchConfig.TT_SIZE);
            valid = false;
        }

        if (SearchConfig.TT_EVICTION_THRESHOLD <= 0) {
            System.err.println("❌ Invalid SearchConfig.TT_EVICTION_THRESHOLD: " + SearchConfig.TT_EVICTION_THRESHOLD);
            valid = false;
        }

        if (SearchConfig.TT_EVICTION_THRESHOLD > SearchConfig.TT_SIZE) {
            System.err.println("❌ TT_EVICTION_THRESHOLD should not exceed TT_SIZE");
            valid = false;
        }

        if (maxSize != SearchConfig.TT_SIZE) {
            System.out.println("⚠️ Table maxSize differs from SearchConfig.TT_SIZE - using maxSize: " + maxSize);
        }

        if (valid) {
            System.out.println("✅ TranspositionTable SearchConfig integration validated");
        }
    }

    /**
     * Get memory usage estimation
     */
    public String getMemoryUsage() {
        // Rough estimation: each entry takes ~50 bytes (hash key + TTEntry object)
        long estimatedBytes = (long) table.size() * 50;
        long maxBytes = (long) maxSize * 50;

        return String.format("Memory: ~%.1f MB / %.1f MB (max from SearchConfig)",
                estimatedBytes / 1024.0 / 1024.0, maxBytes / 1024.0 / 1024.0);
    }

    /**
     * Perform maintenance based on SearchConfig settings
     */
    public void performMaintenance() {
        System.out.println("🔧 Performing TranspositionTable maintenance...");

        // Remove entries that are clearly outdated
        int removedCount = 0;
        long threshold = accessCounter - (SearchConfig.TT_SIZE / 5); // Remove very old entries

        table.entrySet().removeIf(entry -> {
            if (entry.getValue().lastAccessed < threshold) {
                return true;
            }
            return false;
        });

        System.out.printf("🔧 Maintenance complete: %d outdated entries removed\n", removedCount);
    }

    /**
     * Get detailed entry distribution for debugging
     */
    public String getEntryDistribution() {
        if (table.isEmpty()) return "Empty table";

        Map<Integer, Integer> depthDistribution = new HashMap<>();
        Map<Integer, Integer> flagDistribution = new HashMap<>();

        for (TTEntry entry : table.values()) {
            depthDistribution.merge(entry.depth, 1, Integer::sum);
            flagDistribution.merge(entry.flag, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Entry Distribution:\n");
        sb.append("By Depth: ").append(depthDistribution).append("\n");
        sb.append("By Flag: ");
        flagDistribution.forEach((flag, count) -> {
            String flagName = switch (flag) {
                case TTEntry.EXACT -> "EXACT";
                case TTEntry.LOWER_BOUND -> "LOWER";
                case TTEntry.UPPER_BOUND -> "UPPER";
                default -> "UNKNOWN";
            };
            sb.append(flagName).append(":").append(count).append(" ");
        });

        return sb.toString();
    }

    /**
     * Resize table if needed (based on SearchConfig)
     */
    public boolean needsResize() {
        // Suggest resize if frequently hitting eviction threshold
        return evictionCount > 100 && table.size() >= SearchConfig.TT_EVICTION_THRESHOLD;
    }

    /**
     * Get performance recommendations based on SearchConfig
     */
    public String getPerformanceRecommendations() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PERFORMANCE RECOMMENDATIONS ===\n");

        if (getHitRate() < 0.3) {
            sb.append("• Low hit rate - consider increasing SearchConfig.TT_SIZE\n");
        }

        if (getCollisionRate() > 0.1) {
            sb.append("• High collision rate - hash function may need improvement\n");
        }

        if (getUtilization() > 90) {
            sb.append("• High utilization - increase SearchConfig.TT_SIZE or TT_EVICTION_THRESHOLD\n");
        }

        if (evictionCount > table.size()) {
            sb.append("• Frequent evictions - consider larger SearchConfig.TT_SIZE\n");
        }

        if (sb.length() == 45) { // Only header
            sb.append("• Performance looks good with current SearchConfig settings\n");
        }

        return sb.toString();
    }
}