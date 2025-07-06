package GaT.search;

import GaT.model.TTEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transposition Table implementation with hash collision detection
 */
public class TranspositionTable {

    private final Map<Long, TTEntry> table;
    private final int maxSize;
    private long accessCounter = 0;
    private long collisions = 0; // Track hash collisions
    private static final boolean DEBUG_COLLISIONS = false;

    public TranspositionTable(int maxSize) {
        this.maxSize = maxSize;
        this.table = new HashMap<>(maxSize * 4 / 3); // Avoid rehashing
    }

    /**
     * Get entry with hash verification
     */
    public TTEntry get(long hash) {
        TTEntry entry = table.get(hash);

        if (entry != null) {
            // Verify hash if stored
            if (entry.zobristHash != 0 && entry.zobristHash != hash) {
                collisions++;
                if (DEBUG_COLLISIONS) {
                    System.err.println("TT Hash collision detected! Expected: " + hash +
                            ", Found: " + entry.zobristHash);
                }
                return null; // Don't use invalid entry
            }

            entry.lastAccessed = ++accessCounter;
        }

        return entry;
    }

    /**
     * Store entry with hash
     */
    public void put(long hash, TTEntry entry) {
        // Store the hash in the entry for verification
        if (entry.zobristHash == 0) {
            entry.zobristHash = hash;
        } else if (entry.zobristHash != hash) {
            System.err.println("ERROR: TT hash mismatch on store!");
            return;
        }

        // Check if we need to evict entries
        if (table.size() >= maxSize) {
            evictOldEntries();
        }

        entry.lastAccessed = ++accessCounter;
        table.put(hash, entry);
    }

    /**
     * Evict oldest entries when table is full
     */
    private void evictOldEntries() {
        // Strategy: Remove 25% of oldest entries
        int toRemove = maxSize / 4;

        // Find entries to remove (oldest by access time)
        List<Long> toEvict = table.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().lastAccessed,
                        e2.getValue().lastAccessed))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Remove them
        toEvict.forEach(table::remove);

        if (DEBUG_COLLISIONS) {
            System.out.println("TT: Evicted " + toEvict.size() +
                    " entries, size now: " + table.size());
        }
    }

    /**
     * Clear the table
     */
    public void clear() {
        table.clear();
        accessCounter = 0;
        collisions = 0;
    }

    /**
     * Get current size
     */
    public int size() {
        return table.size();
    }

    /**
     * Get collision count
     */
    public long getCollisions() {
        return collisions;
    }

    /**
     * Get utilization percentage
     */
    public double getUtilization() {
        return (double) table.size() / maxSize * 100.0;
    }

    /**
     * Get statistics string
     */
    public String getStats() {
        return String.format("TT Stats: Size=%d/%d (%.1f%%), Collisions=%d",
                table.size(), maxSize, getUtilization(), collisions);
    }
}