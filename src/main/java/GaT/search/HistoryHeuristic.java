package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;

/**
 * HISTORY HEURISTIC - FULL SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ No more hardcoded values
 * ✅ Centralized parameter control
 * ✅ Easy tuning via SearchConfig
 */
public class HistoryHeuristic {

    // === CONFIGURATION FROM SEARCHCONFIG ===
    // Removed: private static final int MAX_HISTORY_SCORE = 10000;
    // Now uses: SearchConfig.HISTORY_MAX_VALUE

    // Removed: private static final int AGING_SHIFT = 1;
    // Now uses: SearchConfig.HISTORY_AGING_SHIFT

    // Removed: private static final int AGING_THRESHOLD = 50000;
    // Now uses: SearchConfig.HISTORY_AGING_THRESHOLD

    // === HISTORY TABLES ===
    private final int[][] redHistoryTable = new int[64][64];
    private final int[][] blueHistoryTable = new int[64][64];

    // Statistics
    private long updateCount = 0;
    private long maxScoreEver = 0;

    // === CORE INTERFACE WITH SEARCHCONFIG ===

    /**
     * Update history score using SearchConfig parameters
     */
    public void update(Move move, int depth, boolean isRedMove) {
        if (move == null || !isValidSquare(move.from) || !isValidSquare(move.to)) {
            return;
        }

        int bonus = calculateDepthBonus(depth);
        int[][] table = isRedMove ? redHistoryTable : blueHistoryTable;

        // Use SearchConfig.HISTORY_MAX_VALUE instead of hardcoded constant
        int currentScore = table[move.from][move.to];
        table[move.from][move.to] = Math.min(currentScore + bonus, SearchConfig.HISTORY_MAX_VALUE);

        updateCount++;
        maxScoreEver = Math.max(maxScoreEver, table[move.from][move.to]);

        // Use SearchConfig.HISTORY_AGING_THRESHOLD instead of hardcoded constant
        if (table[move.from][move.to] > SearchConfig.HISTORY_AGING_THRESHOLD) {
            ageHistoryTable(table);
        }
    }

    /**
     * Get history score using SearchConfig parameters
     */
    public int getScore(Move move, boolean isRedMove) {
        if (move == null || !isValidSquare(move.from) || !isValidSquare(move.to)) {
            return 0;
        }

        int[][] table = isRedMove ? redHistoryTable : blueHistoryTable;
        return table[move.from][move.to];
    }

    // === ENHANCED INTEGRATION WITH SEARCHCONFIG ===

    /**
     * Check if move should use history heuristic based on SearchConfig
     */
    public boolean isQuietMove(Move move, GameState state) {
        if (move == null || state == null) {
            return false;
        }

        // Only use history for quiet moves (not captures)
        long toBit = GameState.bit(move.to);
        boolean isCapture = ((state.redTowers | state.blueTowers |
                state.redGuard | state.blueGuard) & toBit) != 0;

        return !isCapture;
    }

    // === MAINTENANCE WITH SEARCHCONFIG ===

    /**
     * Age history table using SearchConfig aging shift
     */
    private void ageHistoryTable(int[][] table) {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                // Use SearchConfig.HISTORY_AGING_SHIFT instead of hardcoded constant
                table[i][j] >>>= SearchConfig.HISTORY_AGING_SHIFT;
            }
        }
    }

    /**
     * Reset with SearchConfig validation
     */
    public void reset() {
        clearTable(redHistoryTable);
        clearTable(blueHistoryTable);
        updateCount = 0;
        maxScoreEver = 0;

        // Log configuration being used
        System.out.println("🔧 HistoryHeuristic reset with SearchConfig:");
        System.out.println("   MAX_VALUE: " + SearchConfig.HISTORY_MAX_VALUE);
        System.out.println("   AGING_THRESHOLD: " + SearchConfig.HISTORY_AGING_THRESHOLD);
        System.out.println("   AGING_SHIFT: " + SearchConfig.HISTORY_AGING_SHIFT);
    }

    /**
     * Enhanced statistics with SearchConfig info
     */
    public String getStatistics() {
        int redEntries = countNonZeroEntries(redHistoryTable);
        int blueEntries = countNonZeroEntries(blueHistoryTable);

        return String.format(
                "HistoryHeuristic: Updates=%d, MaxScore=%d/%d, Entries=[R:%d,B:%d], Config=[Max:%d,Threshold:%d]",
                updateCount, maxScoreEver, SearchConfig.HISTORY_MAX_VALUE,
                redEntries, blueEntries, SearchConfig.HISTORY_MAX_VALUE, SearchConfig.HISTORY_AGING_THRESHOLD
        );
    }

    // === UTILITY METHODS ===

    private int calculateDepthBonus(int depth) {
        // Enhanced depth bonus calculation with SearchConfig consideration
        int baseBonus = depth * depth;

        // Scale to SearchConfig.HISTORY_MAX_VALUE
        return Math.min(baseBonus, SearchConfig.HISTORY_MAX_VALUE / 20);
    }

    private boolean isValidSquare(int square) {
        return square >= 0 && square < 64;
    }

    private void clearTable(int[][] table) {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                table[i][j] = 0;
            }
        }
    }

    private int countNonZeroEntries(int[][] table) {
        int count = 0;
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (table[i][j] > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Get configuration-aware best move
     */
    public String getBestHistoryMove() {
        int maxScore = 0;
        int bestFrom = -1, bestTo = -1;
        String color = "";

        // Check both tables
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (redHistoryTable[i][j] > maxScore) {
                    maxScore = redHistoryTable[i][j];
                    bestFrom = i;
                    bestTo = j;
                    color = "RED";
                }
                if (blueHistoryTable[i][j] > maxScore) {
                    maxScore = blueHistoryTable[i][j];
                    bestFrom = i;
                    bestTo = j;
                    color = "BLUE";
                }
            }
        }

        if (maxScore > 0) {
            return String.format("%s move %d->%d (score: %d/%d)",
                    color, bestFrom, bestTo, maxScore, SearchConfig.HISTORY_MAX_VALUE);
        } else {
            return "No history moves recorded";
        }
    }

    /**
     * Validate SearchConfig integration
     */
    public boolean validateConfiguration() {
        boolean valid = true;

        if (SearchConfig.HISTORY_MAX_VALUE <= 0) {
            System.err.println("❌ Invalid HISTORY_MAX_VALUE: " + SearchConfig.HISTORY_MAX_VALUE);
            valid = false;
        }

        if (SearchConfig.HISTORY_AGING_THRESHOLD <= 0) {
            System.err.println("❌ Invalid HISTORY_AGING_THRESHOLD: " + SearchConfig.HISTORY_AGING_THRESHOLD);
            valid = false;
        }

        if (SearchConfig.HISTORY_AGING_SHIFT < 0 || SearchConfig.HISTORY_AGING_SHIFT > 5) {
            System.err.println("❌ Invalid HISTORY_AGING_SHIFT: " + SearchConfig.HISTORY_AGING_SHIFT);
            valid = false;
        }

        if (valid) {
            System.out.println("✅ HistoryHeuristic SearchConfig integration validated");
        }

        return valid;
    }
}