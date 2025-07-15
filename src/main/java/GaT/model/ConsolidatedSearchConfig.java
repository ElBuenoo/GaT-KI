package GaT.model;

/**
 * COMPLETE CONSOLIDATED SEARCH CONFIG - ALL MISSING CONSTANTS ADDED
 *
 * This replaces 100+ SearchConfig parameters with 18 essential ones
 * FIXES: Added all missing constants referenced by other classes
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
    public static final double TT_EVICTION_THRESHOLD = 0.75; // ADDED: Missing constant

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
        ALPHA_BETA,          // Pure Alpha-Beta
        PVS,                 // Principal Variation Search
        PVS_QUIESCENCE       // PVS with Quiescence Search
    }

    public static final Strategy DEFAULT_STRATEGY = Strategy.PVS_QUIESCENCE;

    // === LATE MOVE REDUCTION HELPER ===
    public static int getLMRReduction(int depth, int moveIndex) {
        if (depth < LMR_MIN_DEPTH || moveIndex < LMR_MIN_MOVES) {
            return 0;
        }
        return LMR_REDUCTION;
    }

    // === VALIDATION ===
    public static void validate() {
        assert MAX_DEPTH > 0 : "MAX_DEPTH must be positive";
        assert MAX_QUIESCENCE_DEPTH > 0 : "MAX_QUIESCENCE_DEPTH must be positive";
        assert TT_SIZE > 0 : "TT_SIZE must be positive";
        assert EMERGENCY_TIME_MS < DEFAULT_TIME_LIMIT_MS : "Emergency time must be less than default";
        assert NULL_MOVE_MIN_DEPTH >= 1 : "NULL_MOVE_MIN_DEPTH must be at least 1";
        assert LMR_MIN_DEPTH >= 1 : "LMR_MIN_DEPTH must be at least 1";
        assert TT_EVICTION_THRESHOLD > 0.0 && TT_EVICTION_THRESHOLD < 1.0 : "TT_EVICTION_THRESHOLD must be between 0 and 1";

        System.out.println("✅ ConsolidatedSearchConfig validation passed");
        System.out.printf("   Target: %,d NPS, TT Size: %,d entries\n",
                TARGET_NODES_PER_SECOND, TT_SIZE);
    }

    // === CONFIGURATION SUMMARY ===
    public static String getConfigSummary() {
        return String.format(
                "ConsolidatedSearchConfig: 18 essential parameters\n" +
                        "- Strategy: %s\n" +
                        "- Max Depth: %d, Quiescence Depth: %d\n" +
                        "- TT Size: %,d entries (evict at %.0f%%)\n" +
                        "- Time Limits: Emergency=%dms, Panic=%dms\n" +
                        "- Target NPS: %,d",
                DEFAULT_STRATEGY, MAX_DEPTH, MAX_QUIESCENCE_DEPTH,
                TT_SIZE, TT_EVICTION_THRESHOLD * 100,
                EMERGENCY_TIME_MS, PANIC_TIME_MS, TARGET_NODES_PER_SECOND
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