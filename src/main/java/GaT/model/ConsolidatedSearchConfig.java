package GaT.model;

/**
 * COMPLETE CONSOLIDATED SEARCH CONFIG
 *
 * All constants needed for the refactored engine integration.
 * This replaces 100+ SearchConfig parameters with 18 essential ones.
 */
public final class ConsolidatedSearchConfig {

    // === BASIC LIMITS ===
    public static final int MAX_DEPTH = 64;
    public static final int MAX_QUIESCENCE_DEPTH = 4;

    // === TIME MANAGEMENT ===
    public static final long DEFAULT_TIME_LIMIT_MS = 5000L;
    public static final long EMERGENCY_TIME_MS = 200L;
    public static final long PANIC_TIME_MS = 50L;

    // === TRANSPOSITION TABLE ===
    public static final int TT_SIZE_BITS = 23;  // 2^23 = ~8M entries
    public static final int TT_SIZE = 1 << TT_SIZE_BITS;

    // === PRUNING PARAMETERS ===
    public static final int NULL_MOVE_MIN_DEPTH = 3;
    public static final int NULL_MOVE_REDUCTION = 3;
    public static final int FUTILITY_MARGIN = 200;
    public static final int DELTA_PRUNING_MARGIN = 300;

    // === LATE MOVE REDUCTION ===
    public static final int LMR_MIN_DEPTH = 3;
    public static final int LMR_MIN_MOVES = 4;
    public static final int LMR_REDUCTION = 1;

    // === EXTENSIONS ===
    public static final int CHECK_EXTENSION = 1;
    public static final int MAX_EXTENSIONS = 16;

    // === ASPIRATION WINDOWS ===
    public static final int ASPIRATION_DELTA = 50;
    public static final int ASPIRATION_MAX_RETRIES = 3;

    // === PERFORMANCE TARGETS ===
    public static final int TARGET_NODES_PER_SECOND = 1_000_000;

    // === GAME-SPECIFIC CONSTANTS ===
    public static final int RED_CASTLE_INDEX = 45;   // D7 on 7x7 board
    public static final int BLUE_CASTLE_INDEX = 3;   // D1 on 7x7 board

    // === SEARCH STRATEGIES ===
    public enum Strategy {
        ALPHA_BETA,        // Basic Alpha-Beta
        PVS,              // Principal Variation Search
        PVS_QUIESCENCE    // PVS + Quiescence Search
    }

    public static final Strategy DEFAULT_STRATEGY = Strategy.PVS_QUIESCENCE;

    // === EVALUATION BOUNDS ===
    public static final int CHECKMATE_SCORE = 100000;
    public static final int DRAW_SCORE = 0;
    public static final int MIN_EVAL = -999999;
    public static final int MAX_EVAL = 999999;

    // === UTILITY METHODS ===

    /**
     * Calculate LMR reduction based on depth and move index
     */
    public static int getLMRReduction(int depth, int moveIndex) {
        if (depth < LMR_MIN_DEPTH || moveIndex < LMR_MIN_MOVES) {
            return 0;
        }

        // Simple reduction formula
        int reduction = LMR_REDUCTION;
        if (depth >= 6 && moveIndex >= 8) reduction++;
        if (depth >= 10 && moveIndex >= 16) reduction++;

        return Math.min(reduction, depth - 1);
    }

    /**
     * Check if we have time for another iteration
     */
    public static boolean hasTimeForIteration(long elapsed, long allocated, int depth) {
        // Simple heuristic: next iteration takes ~4x longer
        long estimatedNext = elapsed * 4;
        long remaining = allocated - elapsed;

        return remaining > estimatedNext && elapsed < allocated * 0.8;
    }

    /**
     * Check if we're in panic mode (very little time left)
     */
    public static boolean isPanicMode(long timeRemaining) {
        return timeRemaining <= PANIC_TIME_MS;
    }

    /**
     * Check if we're in emergency mode
     */
    public static boolean isEmergencyMode(long timeRemaining) {
        return timeRemaining <= EMERGENCY_TIME_MS;
    }

    /**
     * Get aspiration window bounds
     */
    public static int[] getAspirationBounds(int previousScore, int iteration) {
        int delta = ASPIRATION_DELTA * (1 << Math.min(iteration, 4));
        return new int[] {
                Math.max(MIN_EVAL, previousScore - delta),
                Math.min(MAX_EVAL, previousScore + delta)
        };
    }

    /**
     * Check if null move pruning is allowed
     */
    public static boolean allowNullMove(int depth, int eval, int beta) {
        return depth >= NULL_MOVE_MIN_DEPTH && eval >= beta;
    }

    /**
     * Check if futility pruning is allowed
     */
    public static boolean allowFutilityPruning(int depth, int eval, int alpha) {
        return depth <= 3 && eval + FUTILITY_MARGIN < alpha;
    }

    /**
     * Get time allocation breakdown
     */
    public static long[] getTimeAllocation(long totalTime) {
        // Emergency: 10%, Comfort: 30%, Normal: 60%
        long emergency = totalTime / 10;
        long comfort = totalTime * 3 / 10;
        long normal = totalTime - emergency - comfort;

        return new long[] { emergency, comfort, normal };
    }

    // === VALIDATION ===

    /**
     * Validate configuration at startup
     */
    public static void validate() {
        assert MAX_DEPTH > 0 : "MAX_DEPTH must be positive";
        assert MAX_QUIESCENCE_DEPTH > 0 : "MAX_QUIESCENCE_DEPTH must be positive";
        assert TT_SIZE > 0 : "TT_SIZE must be positive";
        assert EMERGENCY_TIME_MS < DEFAULT_TIME_LIMIT_MS : "Emergency time must be less than default";
        assert NULL_MOVE_MIN_DEPTH >= 1 : "NULL_MOVE_MIN_DEPTH must be at least 1";
        assert LMR_MIN_DEPTH >= 1 : "LMR_MIN_DEPTH must be at least 1";

        System.out.println("✅ ConsolidatedSearchConfig validation passed");
        System.out.printf("   Target: %,d NPS, TT Size: %,d entries\n",
                TARGET_NODES_PER_SECOND, TT_SIZE);
    }

    /**
     * Get configuration summary
     */
    public static String getConfigSummary() {
        return String.format(
                "ConsolidatedSearchConfig: 18 essential parameters\n" +
                        "- Strategy: %s\n" +
                        "- Max Depth: %d, Quiescence Depth: %d\n" +
                        "- TT Size: %,d entries\n" +
                        "- Time Limits: Emergency=%dms, Panic=%dms\n" +
                        "- Target NPS: %,d",
                DEFAULT_STRATEGY, MAX_DEPTH, MAX_QUIESCENCE_DEPTH,
                TT_SIZE, EMERGENCY_TIME_MS, PANIC_TIME_MS, TARGET_NODES_PER_SECOND
        );
    }

    // === PREVENT INSTANTIATION ===
    private ConsolidatedSearchConfig() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    // === STATIC INITIALIZATION ===
    static {
        validate();
    }
}