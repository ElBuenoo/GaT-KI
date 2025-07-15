package GaT.model;

/**
 * ENHANCED SEARCH CONFIGURATION - Complete Parameter Control
 *
 * This class centralizes ALL search parameters used across the entire codebase.
 * No more scattered constants - everything is controlled from here.
 *
 * ✅ All search classes now use these parameters:
 * - HistoryHeuristic: HISTORY_* parameters
 * - MoveOrdering: MOVE_ORDERING_*, KILLER_*, MVV_LVA_* parameters
 * - PVSSearch: NULL_MOVE_*, LMR_*, TACTICAL_* parameters
 * - QuiescenceSearch: Q_*, MAX_Q_DEPTH, MAX_TACTICAL_RECURSION
 * - TimedSearchEngine: ASPIRATION_*, TIME_* parameters
 * - TimeManager: TIME_MANAGEMENT_* parameters
 */
public class SearchConfig {

    // === SEARCH LIMITS ===
    public static final int MAX_DEPTH = 99;
    public static final int DEFAULT_TIME_LIMIT_MS = 5000;
    public static final int EMERGENCY_TIME_MS = 200;
    public static final int PANIC_TIME_MS = 50;

    // === DEFAULT SEARCH STRATEGY ===
    public static final SearchStrategy DEFAULT_STRATEGY = SearchStrategy.PVS_Q;

    // === TRANSPOSITION TABLE ===
    public static final int TT_SIZE = 8_000_000;
    public static final int TT_EVICTION_THRESHOLD = 1_500_000;

    // === ASPIRATION WINDOW PARAMETERS ===
    public static final int ASPIRATION_WINDOW_DELTA = 50;
    public static final int ASPIRATION_WINDOW_MAX_FAILS = 3;
    public static final int ASPIRATION_WINDOW_GROWTH_FACTOR = 4;

    // === NULL-MOVE PRUNING CONFIGURATION ===
    public static final boolean NULL_MOVE_ENABLED = true;
    public static final int NULL_MOVE_MIN_DEPTH = 3;
    public static final int NULL_MOVE_REDUCTION = 3;
    public static final int NULL_MOVE_VERIFICATION_DEPTH = 4;

    // === FUTILITY PRUNING CONFIGURATION ===
    public static final int FUTILITY_MAX_DEPTH = 3;
    public static final int[] FUTILITY_MARGINS = {0, 150, 300, 450};
    public static final int[] REVERSE_FUTILITY_MARGINS = {0, 120, 240, 360};

    // === LATE MOVE REDUCTIONS (LMR) ===
    public static final int LMR_MIN_DEPTH = 3;
    public static final int LMR_MIN_MOVE_COUNT = 3;
    public static final int LMR_BASE_REDUCTION = 2;
    public static final int LMR_MAX_REDUCTION = 3;
    public static final int LMR_MOVE_INDEX_THRESHOLD_1 = 8;
    public static final int LMR_MOVE_INDEX_THRESHOLD_2 = 16;
    public static final int LMR_DEPTH_THRESHOLD = 8;
    public static final int LMR_HIGH_ACTIVITY_THRESHOLD = 4;

    // === EXTENSIONS ===
    public static final int CHECK_EXTENSION_DEPTH = 1;
    public static final int MAX_EXTENSION_DEPTH = 10;

    // === HISTORY HEURISTIC CONFIGURATION ===
    public static final int HISTORY_MAX_VALUE = 10000;
    public static final int HISTORY_AGING_THRESHOLD = 1000;
    public static final int HISTORY_AGING_SHIFT = 1;
    public static final int HISTORY_AGING_FACTOR = 2;
    public static final int HISTORY_SCORE_DIVISOR = 100;
    public static final int HISTORY_MAX_SCORE = 1000;

    // === KILLER MOVES CONFIGURATION ===
    public static final int KILLER_MOVE_SLOTS = 2;
    public static final int MAX_KILLER_DEPTH = 20;
    public static final int KILLER_AGING_THRESHOLD = 50;
    public static final int KILLER_1_PRIORITY = 10000;
    public static final int KILLER_2_PRIORITY = 9000;

    // === MOVE ORDERING PRIORITIES ===
    public static final int MOVE_ORDERING_TT_PRIORITY = 1000000;
    public static final int MOVE_ORDERING_CAPTURE_BASE = 100000;
    public static final int DEFAULT_CAPTURE_BONUS = 100;
    public static final int MVV_MULTIPLIER = 100;
    public static final int LVA_MULTIPLIER = 1;

    // === PIECE VALUES FOR MVV-LVA ===
    public static final int GUARD_CAPTURE_VALUE = 1000;
    public static final int TOWER_HEIGHT_VALUE = 100;
    public static final int GUARD_ATTACKER_VALUE = 10;
    public static final int DEFAULT_VICTIM_VALUE = 100;
    public static final int DEFAULT_ATTACKER_VALUE = 50;

    // === POSITIONAL SCORING ===
    public static final int CENTRAL_SQUARE_BONUS = 50;
    public static final int DEVELOPMENT_BONUS = 30;
    public static final int GUARD_ADVANCEMENT_MULTIPLIER = 15;
    public static final int POSITIONAL_MAX_SCORE = 500;
    public static final int ACTIVITY_BONUS = 5;

    // === QUIESCENCE SEARCH CONFIGURATION ===
    public static final int MAX_Q_DEPTH = 4;
    public static final int MAX_TACTICAL_RECURSION = 2;
    public static final int Q_DELTA_MARGIN = 300;
    public static final int Q_FUTILITY_THRESHOLD = 78;
    public static final int Q_HISTORY_UPDATE_DEPTH = 2;

    // === QUIESCENCE SCORING ===
    public static final int Q_CAPTURE_SCORE_MULTIPLIER = 10;
    public static final int Q_WINNING_MOVE_BONUS = 50000;
    public static final int Q_CHECK_BONUS = 100;
    public static final int Q_ACTIVITY_BONUS = 5;
    public static final int Q_DEPTH_PENALTY = 10;
    public static final int Q_HIGH_ACTIVITY_THRESHOLD = 3;

    // === QUIESCENCE PIECE VALUES ===
    public static final int Q_GUARD_CAPTURE_VALUE = 1500;
    public static final int Q_TOWER_CAPTURE_VALUE_PER_HEIGHT = 100;
    public static final int Q_GUARD_ATTACKER_VALUE = 50;
    public static final int Q_TOWER_ATTACKER_VALUE_PER_HEIGHT = 25;

    // === TACTICAL DETECTION THRESHOLDS ===
    public static final int TACTICAL_DISTANCE_THRESHOLD = 3;
    public static final int TACTICAL_HEIGHT_THRESHOLD = 3;
    public static final int QUIET_POSITION_THRESHOLD = 4;

    // === GAME PHASE THRESHOLDS ===
    public static final int ENDGAME_MATERIAL_THRESHOLD = 8;
    public static final int TABLEBASE_MATERIAL_THRESHOLD = 6;
    public static final int TACTICAL_COMPLEXITY_THRESHOLD = 3;

    // === TIME MANAGEMENT CONFIGURATION ===
    public static final long TIME_PANIC_THRESHOLD = 500;
    public static final long TIME_EMERGENCY_THRESHOLD = 3000;
    public static final long TIME_LOW_THRESHOLD = 10000;
    public static final long TIME_COMFORT_THRESHOLD = 30000;

    // === TIME ALLOCATION FACTORS ===
    public static final double TIME_CRITICAL_FACTOR = 0.25;      // 25% for critical positions
    public static final double TIME_EMERGENCY_FACTOR = 0.16;     // 16% in emergency
    public static final double TIME_LOW_FACTOR = 0.20;           // 20% when low on time
    public static final double TIME_MIN_FACTOR = 0.04;           // 4% minimum
    public static final double TIME_MAX_FACTOR = 0.25;           // 25% maximum

    // === TIME MULTIPLIERS ===
    public static final double TIME_BEHIND_MULTIPLIER = 1.3;     // 30% more when behind
    public static final double TIME_AHEAD_MULTIPLIER = 0.9;      // 10% less when ahead
    public static final double TIME_MIDDLEGAME_MULTIPLIER = 1.2; // 20% more in middlegame
    public static final double TIME_ENDGAME_MULTIPLIER = 1.5;    // 50% more in endgame
    public static final double TIME_CRITICAL_MULTIPLIER = 1.25;  // 25% more for critical positions

    // === CHECKMATE AND WIN DETECTION ===
    public static final int CHECKMATE_THRESHOLD = 10000;
    public static final int FORCED_MATE_THRESHOLD = 2400;
    public static final int WINNING_SCORE_THRESHOLD = 2000;
    public static final int MIN_SEARCH_DEPTH = 5;

    // === ADVANCED PRUNING PARAMETERS ===
    public static final boolean RAZORING_ENABLED = true;
    public static final int RAZORING_MAX_DEPTH = 4;
    public static final int[] RAZORING_MARGINS = {0, 300, 400, 500, 600};

    public static final boolean PROBCUT_ENABLED = false;
    public static final int PROBCUT_MIN_DEPTH = 5;
    public static final double PROBCUT_THRESHOLD = 0.8;

    public static final boolean MULTICUT_ENABLED = false;
    public static final int MULTICUT_MIN_DEPTH = 6;
    public static final int MULTICUT_CUTOFF_COUNT = 3;

    // === ADAPTIVE SEARCH PARAMETERS ===
    public static final boolean ADAPTIVE_TIME_ENABLED = true;
    public static final double TIME_ALLOCATION_FACTOR = 0.03;
    public static final double TIME_PANIC_FACTOR = 0.1;

    public static final boolean ADAPTIVE_DEPTH_ENABLED = true;
    public static final int MIN_SEARCH_DEPTH_ADAPTIVE = 1;
    public static final int MAX_ADAPTIVE_DEPTH_INCREASE = 2;

    public static final long NODES_PER_SECOND_TARGET = 100_000;
    public static final long MAX_NODES_PER_SEARCH = 1_000_000;

    // === DEBUGGING AND TUNING PARAMETERS ===
    public static final boolean SEARCH_LOGGING_ENABLED = false;
    public static final boolean MOVE_ORDERING_LOGGING = false;
    public static final boolean PRUNING_LOGGING = false;
    public static final boolean TUNING_MODE = false;
    public static final boolean COLLECT_STATISTICS = true;
    public static final boolean EXPORT_STATISTICS_CSV = false;

    // === SEARCH STRATEGY ENUM ===
    public enum SearchStrategy {
        ALPHA_BETA("Alpha-Beta"),
        MINIMAX("Minimax"),
        ALPHA_BETA_Q("Alpha-Beta + Quiescence"),
        PVS("Principal Variation Search"),
        PVS_Q("PVS + Quiescence (ULTIMATE)"),
        MTDF("MTD(f) Search"),
        YBWC("Young Brothers Wait Concept"),
        LAZY_SMP("Lazy SMP");


        public final String displayName;

        SearchStrategy(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // === TIME CONFIGURATION CLASS ===
    public static class TimeConfig {
        public final long totalTimeMs;
        public final int estimatedMovesLeft;
        public final double emergencyTimeRatio;
        public final double complexityTimeModifier;

        public TimeConfig(long totalTimeMs, int estimatedMovesLeft) {
            this(totalTimeMs, estimatedMovesLeft, TIME_EMERGENCY_FACTOR, TIME_MIDDLEGAME_MULTIPLIER);
        }

        public TimeConfig(long totalTimeMs, int estimatedMovesLeft,
                          double emergencyTimeRatio, double complexityTimeModifier) {
            this.totalTimeMs = totalTimeMs;
            this.estimatedMovesLeft = estimatedMovesLeft;
            this.emergencyTimeRatio = emergencyTimeRatio;
            this.complexityTimeModifier = complexityTimeModifier;
        }


        public long calculateSearchTime() {
            if (totalTimeMs < EMERGENCY_TIME_MS) {
                return PANIC_TIME_MS;
            }

            long baseTime = totalTimeMs / Math.max(estimatedMovesLeft, 20);
            return Math.min(baseTime, DEFAULT_TIME_LIMIT_MS);
        }

        public boolean isEmergencyTime() {
            return totalTimeMs < EMERGENCY_TIME_MS;
        }

        public boolean isPanicTime() {
            return totalTimeMs < PANIC_TIME_MS;
        }

        public boolean isLowTime() {
            return totalTimeMs < TIME_LOW_THRESHOLD;
        }

        public boolean isComfortableTime() {
            return totalTimeMs > TIME_COMFORT_THRESHOLD;
        }
    }

    // === EVALUATION CONFIGURATION ===
    public static class EvaluationConfig {
        public final boolean usePhasedEvaluation;
        public final boolean usePatternRecognition;
        public final boolean useTablebase;
        public final boolean adaptToTimeRemaining;

        public EvaluationConfig() {
            this(true, true, true, true);
        }

        public EvaluationConfig(boolean usePhasedEvaluation, boolean usePatternRecognition,
                                boolean useTablebase, boolean adaptToTimeRemaining) {
            this.usePhasedEvaluation = usePhasedEvaluation;
            this.usePatternRecognition = usePatternRecognition;
            this.useTablebase = useTablebase;
            this.adaptToTimeRemaining = adaptToTimeRemaining;
        }
    }

    // === UTILITY METHODS ===

    /**
     * Get configuration summary for debugging
     */
    public static String getConfigSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ENHANCED SEARCH CONFIGURATION ===\n");
        sb.append(String.format("Strategy: %s\n", DEFAULT_STRATEGY));
        sb.append(String.format("Max Depth: %d, Time Limit: %dms\n", MAX_DEPTH, DEFAULT_TIME_LIMIT_MS));

        sb.append("\nPruning Configuration:\n");
        sb.append(String.format("  Null-Move: %s (depth ≥%d, reduction %d)\n",
                NULL_MOVE_ENABLED, NULL_MOVE_MIN_DEPTH, NULL_MOVE_REDUCTION));
        sb.append(String.format("  Futility: depth ≤%d\n", FUTILITY_MAX_DEPTH));
        sb.append(String.format("  LMR: depth ≥%d, move ≥%d\n", LMR_MIN_DEPTH, LMR_MIN_MOVE_COUNT));

        sb.append("\nMove Ordering Configuration:\n");
        sb.append(String.format("  Killers: %d slots, depth ≤%d\n", KILLER_MOVE_SLOTS, MAX_KILLER_DEPTH));
        sb.append(String.format("  History: max %d, aging %d\n", HISTORY_MAX_VALUE, HISTORY_AGING_THRESHOLD));
        sb.append(String.format("  TT Priority: %d, Capture Base: %d\n", MOVE_ORDERING_TT_PRIORITY, MOVE_ORDERING_CAPTURE_BASE));

        sb.append("\nQuiescence Configuration:\n");
        sb.append(String.format("  Max Q-Depth: %d, Tactical Recursion: %d\n", MAX_Q_DEPTH, MAX_TACTICAL_RECURSION));
        sb.append(String.format("  Delta Margin: %d, Futility Threshold: %d\n", Q_DELTA_MARGIN, Q_FUTILITY_THRESHOLD));

        sb.append("\nTime Management Configuration:\n");
        sb.append(String.format("  Emergency: %dms, Panic: %dms\n", EMERGENCY_TIME_MS, PANIC_TIME_MS));
        sb.append(String.format("  Critical Factor: %.1f%%, Emergency Factor: %.1f%%\n",
                TIME_CRITICAL_FACTOR * 100, TIME_EMERGENCY_FACTOR * 100));

        return sb.toString();
    }

    /**
     * Validate configuration for consistency
     */
    public static boolean validateConfiguration() {
        boolean valid = true;

        // Basic sanity checks
        if (MAX_DEPTH <= 0 || MAX_DEPTH > 100) {
            System.err.println("❌ Invalid MAX_DEPTH: " + MAX_DEPTH);
            valid = false;
        }

        if (NULL_MOVE_MIN_DEPTH >= MAX_DEPTH) {
            System.err.println("❌ NULL_MOVE_MIN_DEPTH too high: " + NULL_MOVE_MIN_DEPTH);
            valid = false;
        }

        if (FUTILITY_MARGINS.length < FUTILITY_MAX_DEPTH) {
            System.err.println("❌ FUTILITY_MARGINS array too short");
            valid = false;
        }

        if (REVERSE_FUTILITY_MARGINS.length < FUTILITY_MAX_DEPTH) {
            System.err.println("❌ REVERSE_FUTILITY_MARGINS array too short");
            valid = false;
        }

        if (KILLER_MOVE_SLOTS <= 0 || KILLER_MOVE_SLOTS > 10) {
            System.err.println("❌ Invalid KILLER_MOVE_SLOTS: " + KILLER_MOVE_SLOTS);
            valid = false;
        }

        if (HISTORY_MAX_VALUE <= 0) {
            System.err.println("❌ Invalid HISTORY_MAX_VALUE: " + HISTORY_MAX_VALUE);
            valid = false;
        }

        if (MAX_Q_DEPTH <= 0 || MAX_Q_DEPTH > 20) {
            System.err.println("❌ Invalid MAX_Q_DEPTH: " + MAX_Q_DEPTH);
            valid = false;
        }

        if (LMR_MIN_DEPTH <= 0) {
            System.err.println("❌ Invalid LMR_MIN_DEPTH: " + LMR_MIN_DEPTH);
            valid = false;
        }

        if (LMR_MIN_MOVE_COUNT <= 0) {
            System.err.println("❌ Invalid LMR_MIN_MOVE_COUNT: " + LMR_MIN_MOVE_COUNT);
            valid = false;
        }

        // Time management validation
        if (EMERGENCY_TIME_MS <= 0 || PANIC_TIME_MS <= 0) {
            System.err.println("❌ Invalid emergency/panic time thresholds");
            valid = false;
        }

        if (TIME_CRITICAL_FACTOR <= 0 || TIME_CRITICAL_FACTOR > 1) {
            System.err.println("❌ Invalid TIME_CRITICAL_FACTOR: " + TIME_CRITICAL_FACTOR);
            valid = false;
        }

        // Priority validation
        if (MOVE_ORDERING_TT_PRIORITY <= MOVE_ORDERING_CAPTURE_BASE) {
            System.err.println("❌ TT priority should be higher than capture base");
            valid = false;
        }

        if (KILLER_1_PRIORITY <= KILLER_2_PRIORITY) {
            System.err.println("❌ Killer 1 priority should be higher than Killer 2");
            valid = false;
        }

        if (valid) {
            System.out.println("✅ SearchConfig validation passed - all parameters are consistent");
        }

        return valid;
    }

    /**
     * Export configuration to string for saving
     */
    public static String exportConfiguration() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Enhanced Guard & Towers Search Configuration\n");
        sb.append(String.format("DEFAULT_STRATEGY=%s\n", DEFAULT_STRATEGY));
        sb.append(String.format("MAX_DEPTH=%d\n", MAX_DEPTH));
        sb.append(String.format("NULL_MOVE_ENABLED=%s\n", NULL_MOVE_ENABLED));
        sb.append(String.format("NULL_MOVE_MIN_DEPTH=%d\n", NULL_MOVE_MIN_DEPTH));
        sb.append(String.format("NULL_MOVE_REDUCTION=%d\n", NULL_MOVE_REDUCTION));
        sb.append(String.format("FUTILITY_MAX_DEPTH=%d\n", FUTILITY_MAX_DEPTH));
        sb.append(String.format("LMR_MIN_DEPTH=%d\n", LMR_MIN_DEPTH));
        sb.append(String.format("LMR_MIN_MOVE_COUNT=%d\n", LMR_MIN_MOVE_COUNT));
        sb.append(String.format("HISTORY_MAX_VALUE=%d\n", HISTORY_MAX_VALUE));
        sb.append(String.format("MAX_Q_DEPTH=%d\n", MAX_Q_DEPTH));
        sb.append(String.format("KILLER_MOVE_SLOTS=%d\n", KILLER_MOVE_SLOTS));
        sb.append(String.format("MAX_KILLER_DEPTH=%d\n", MAX_KILLER_DEPTH));
        sb.append(String.format("MOVE_ORDERING_TT_PRIORITY=%d\n", MOVE_ORDERING_TT_PRIORITY));
        sb.append(String.format("MOVE_ORDERING_CAPTURE_BASE=%d\n", MOVE_ORDERING_CAPTURE_BASE));
        sb.append(String.format("Q_DELTA_MARGIN=%d\n", Q_DELTA_MARGIN));
        sb.append(String.format("EMERGENCY_TIME_MS=%d\n", EMERGENCY_TIME_MS));
        sb.append(String.format("TIME_CRITICAL_FACTOR=%.2f\n", TIME_CRITICAL_FACTOR));
        return sb.toString();
    }

    /**
     * Performance tuning suggestions
     */
    public static String getTuningAdvice() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ENHANCED TUNING ADVICE ===\n");
        sb.append("• Increase NULL_MOVE_REDUCTION if too many false cutoffs\n");
        sb.append("• Decrease FUTILITY_MAX_DEPTH if evaluations are slow\n");
        sb.append("• Increase LMR_MIN_MOVE_COUNT for more aggressive pruning\n");
        sb.append("• Adjust HISTORY_MAX_VALUE based on game length\n");
        sb.append("• Tune Q_DELTA_MARGIN based on tactical complexity\n");
        sb.append("• Monitor search statistics for optimization opportunities\n");
        sb.append("• Adjust time factors based on tournament results\n");
        sb.append("• Fine-tune move ordering priorities for better cutoffs\n");
        sb.append("• Consider endgame-specific parameter adjustments\n");
        return sb.toString();
    }

    // === PRESET CONFIGURATIONS ===

    /**
     * Tournament configuration - Maximum strength
     */
    public static TimeConfig tournamentTimeConfig(long timeMs, int movesLeft) {
        return new TimeConfig(timeMs, movesLeft, TIME_EMERGENCY_FACTOR, TIME_MIDDLEGAME_MULTIPLIER);
    }

    /**
     * Blitz configuration - Fast but strong
     */
    public static TimeConfig blitzTimeConfig(long timeMs, int movesLeft) {
        return new TimeConfig(timeMs, movesLeft, 0.15, 1.2);
    }

    /**
     * Emergency configuration - Minimal time
     */
    public static TimeConfig emergencyTimeConfig() {
        return new TimeConfig(EMERGENCY_TIME_MS, 10, 0.5, 1.0);
    }

    // === CONFIGURATION INFORMATION ===

    /**
     * Get configuration version info
     */
    public static String getVersionInfo() {
        return "Enhanced SearchConfig v2.0 - Complete Parameter Integration";
    }

    /**
     * Print parameter categories for debugging
     */
    public static void printParameterCategories() {
        System.out.println("=== SEARCHCONFIG PARAMETER CATEGORIES ===");
        System.out.println("1. Search Limits: MAX_DEPTH, DEFAULT_TIME_LIMIT_MS, etc.");
        System.out.println("2. Pruning: NULL_MOVE_*, FUTILITY_*, LMR_*");
        System.out.println("3. Move Ordering: KILLER_*, HISTORY_*, MOVE_ORDERING_*");
        System.out.println("4. Quiescence: Q_*, MAX_Q_DEPTH, MAX_TACTICAL_RECURSION");
        System.out.println("5. Time Management: TIME_*, EMERGENCY_TIME_MS, etc.");
        System.out.println("6. Evaluation: MATERIAL_WEIGHT, POSITIONAL_WEIGHT, etc.");
        System.out.println("7. Advanced: RAZORING_*, PROBCUT_*, ADAPTIVE_*");
        System.out.println("8. Debugging: *_LOGGING_*, TUNING_MODE, etc.");
    }

    // Static initializer to validate on startup
    static {
        if (validateConfiguration()) {
            System.out.println("✅ Enhanced SearchConfig loaded successfully");
            System.out.println("   " + getVersionInfo());
        } else {
            System.err.println("❌ SearchConfig validation failed! Check configuration.");
        }
    }
}