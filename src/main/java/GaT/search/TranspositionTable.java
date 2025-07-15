package GaT.search;

import GaT.model.ConsolidatedSearchConfig;
import GaT.model.TTEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TRANSPOSITION TABLE - FIXED COMPILATION ERRORS
 *
 * FIXES:
 * ✅ Removed references to non-existent TT_EVICTION_THRESHOLD
 * ✅ Removed references to old SearchConfig
 * ✅ Uses only ConsolidatedSearchConfig constants
 * ✅ Added missing constants locally
 */
public class TranspositionTable {

    private final Map<Long, TTEntry> table;
    private final int maxSize;

    // === LOCAL CONSTANTS (since not in ConsolidatedSearchConfig) ===
    private static final double DEFAULT_EVICTION_THRESHOLD = 0.75; // 75% full before eviction
    private static final int REPLACEMENT_SCHEME_DEPTH = 0; // Prefer depth-based replacement

    private long accessCounter = 0;
    private long hitCount = 0;
    private long missCount = 0;
    private long collisionCount = 0;
    private long evictionCount = 0;

    // === CONSTRUCTOR ===
    public TranspositionTable(int maxSize) {
        this.maxSize = maxSize;
        this.table = new HashMap<>(maxSize * 4 / 3); // Avoid rehashing

        System.out.println("🔧 TranspositionTable initialized:");
        System.out.println("   Max Size: " + maxSize + " entries");
        System.out.println("   Eviction Threshold: " + (DEFAULT_EVICTION_THRESHOLD * 100) + "%");
    }

    /**
     * Default constructor using ConsolidatedSearchConfig.TT_SIZE
     */
    public TranspositionTable() {
        this(ConsolidatedSearchConfig.TT_SIZE);
    }

    // === CORE TT OPERATIONS ===

    /**
     * Store entry in transposition table
     */
    public void store(long hash, int score, int depth, int flag, GaT.model.Move bestMove) {
        accessCounter++;

        // Check if we need to evict entries
        if (shouldEvict()) {
            evictEntries();
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        entry.lastAccessed = accessCounter;

        TTEntry existing = table.put(hash, entry);
        if (existing != null) {
            collisionCount++;
        }
    }

    /**
     * Retrieve entry from transposition table
     */
    public TTEntry retrieve(long hash) {
        accessCounter++;
        TTEntry entry = table.get(hash);

        if (entry != null) {
            hitCount++;
            entry.lastAccessed = accessCounter; // Update LRU
            return entry;
        } else {
            missCount++;
            return null;
        }
    }

    /**
     * Check if we should evict entries
     */
    private boolean shouldEvict() {
        return table.size() >= maxSize * DEFAULT_EVICTION_THRESHOLD;
    }

    /**
     * Evict old entries to make room
     */
    private void evictEntries() {
        int targetSize = (int)(maxSize * 0.5); // Evict to 50% capacity
        int toRemove = table.size() - targetSize;

        if (toRemove <= 0) return;

        // Get entries sorted by last access time (LRU)
        List<Map.Entry<Long, TTEntry>> sortedEntries = table.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().lastAccessed, e2.getValue().lastAccessed))
                .limit(toRemove)
                .collect(Collectors.toList());

        // Remove oldest entries
        for (Map.Entry<Long, TTEntry> entry : sortedEntries) {
            table.remove(entry.getKey());
            evictionCount++;
        }

        System.out.println("🗑️ TT evicted " + toRemove + " entries");
    }

    // === STATISTICS ===

    public double getHitRate() {
        long total = hitCount + missCount;
        return total > 0 ? (double) hitCount / total : 0.0;
    }

    public double getLoadFactor() {
        return (double) table.size() / maxSize;
    }

    public String getStatistics() {
        long total = hitCount + missCount;
        return String.format(
                "TT Stats: %,d/%,d entries (%.1f%% full), " +
                        "Hit rate: %.1f%% (%,d/%,d), " +
                        "Collisions: %,d, Evictions: %,d",
                table.size(), maxSize, getLoadFactor() * 100,
                getHitRate() * 100, hitCount, total,
                collisionCount, evictionCount
        );
    }

    // === MAINTENANCE ===

    public void clear() {
        table.clear();
        accessCounter = 0;
        hitCount = 0;
        missCount = 0;
        collisionCount = 0;
        evictionCount = 0;
        System.out.println("🧹 TranspositionTable cleared");
    }

    public int size() {
        return table.size();
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    // === COMPATIBILITY METHODS ===

    /**
     * Legacy method for compatibility
     */
    public void put(long hash, TTEntry entry) {
        store(hash, entry.score, entry.depth, entry.flag, entry.bestMove);
    }

    /**
     * Legacy method for compatibility
     */
    public TTEntry get(long hash) {
        return retrieve(hash);
    }
}