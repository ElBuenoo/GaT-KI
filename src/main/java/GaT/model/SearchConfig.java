package GaT.model;

/**
 * SEARCH CONFIGURATION - Centralized search parameters
 * Replaces scattered constants and enables easy tuning
 */
public class SearchConfig {

    // === SEARCH LIMITS ===
    public static final int MAX_DEPTH = 99;
    public static final int DEFAULT_TIME_LIMIT_MS = 5000;
    public static final int EMERGENCY_TIME_MS = 200;
    public static final int PANIC_TIME_MS = 50;

    // === DEFAULT SEARCH STRATEGY - PHASE 2 FIX ===
    public static final SearchStrategy DEFAULT_STRATEGY = SearchStrategy.PVS_Q;

    // === ASPIRATION WINDOW PARAMETERS ===
    public static final int ASPIRATION_WINDOW_DELTA = 50;
    public static final int ASPIRATION_WINDOW_MAX_FAILS = 3;
    public static final int ASPIRATION_WINDOW_GROWTH_FACTOR = 4;

    // === TRANSPOSITION TABLE ===
    public static final int TT_SIZE = 2_000_000;
    public static final int TT_EVICTION_THRESHOLD = 1_500_000;

    // === PRUNING PARAMETERS ===
    public static final int NULL_MOVE_MIN_DEPTH = 3;
    public static final int FUTILITY_MAX_DEPTH = 3;
    public static final int LMR_MIN_DEPTH = 3;
    public static final int LMR_MIN_MOVE_COUNT = 4;

    // === PRUNING MARGINS ===
    public static final int[] REVERSE_FUTILITY_MARGINS = {0, 120, 240, 360};
    public static final int[] FUTILITY_MARGINS = {0, 150, 300, 450};

    // === EXTENSIONS ===
    public static final int CHECK_EXTENSION_DEPTH = 1;
    public static final int MAX_EXTENSION_DEPTH = 10;

    // === KILLER MOVES ===
    public static final int KILLER_MOVE_SLOTS = 2;
    public static final int MAX_KILLER_DEPTH = 20;

    // === HISTORY HEURISTIC ===
    public static final int HISTORY_MAX_VALUE = 10000;
    public static final int HISTORY_AGING_THRESHOLD = 1000;

    // === QUIESCENCE SEARCH ===
    public static final int MAX_Q_DEPTH = 12;
    public static final int Q_DELTA_MARGIN = 150;
    public static final int Q_FUTILITY_THRESHOLD = 78;

    // === EVALUATION WEIGHTS ===
    public static final int MATERIAL_WEIGHT = 100;
    public static final int POSITIONAL_WEIGHT = 50;
    public static final int SAFETY_WEIGHT = 75;
    public static final int MOBILITY_WEIGHT = 25;

    // === GAME PHASE THRESHOLDS ===
    public static final int ENDGAME_MATERIAL_THRESHOLD = 8;
    public static final int TABLEBASE_MATERIAL_THRESHOLD = 6;
    public static final int TACTICAL_COMPLEXITY_THRESHOLD = 3;

// === LATE MOVE REDUCTION CONSTANTS ===

    /** Minimum number of moves searched before applying LMR */
    public static final int LMR_MIN_MOVES_SEARCHED = 4;

    /** Maximum LMR reduction depth */
    public static final int LMR_MAX_REDUCTION = 3;

    /** LMR reduction factor for quiet moves */
    public static final double LMR_QUIET_REDUCTION_FACTOR = 0.75;

    /** LMR reduction factor for tactical moves */
    public static final double LMR_TACTICAL_REDUCTION_FACTOR = 0.5;

// === SEARCH WINDOW CONSTANTS ===

    /** Initial aspiration window size */
    public static final int ASPIRATION_INITIAL_WINDOW = 25;

    /** Maximum aspiration window failures before full window search */
    public static final int ASPIRATION_MAX_FAILURES = 4;

    /** Factor to widen aspiration window after failure */
    public static final int ASPIRATION_WIDEN_FACTOR = 2;

// === TIME MANAGEMENT CONSTANTS ===

    /** Percentage of time to use for normal moves */
    public static final double NORMAL_TIME_PERCENTAGE = 0.04;

    /** Percentage of time to use for critical moves */
    public static final double CRITICAL_TIME_PERCENTAGE = 0.20;

    /** Emergency time reserve percentage */
    public static final double EMERGENCY_TIME_PERCENTAGE = 0.10;

    /** Complexity factor for time allocation */
    public static final double COMPLEXITY_TIME_FACTOR = 1.5;

// === THREAT DETECTION CONSTANTS ===

    /** Minimum threat level to trigger defensive mode */
    public static final int DEFENSIVE_MODE_THREAT_LEVEL = 7;

    /** Maximum threats to analyze per position */
    public static final int MAX_THREATS_ANALYZED = 10;

    /** Threat evaluation cache size */
    public static final int THREAT_CACHE_SIZE = 1000;

// === SEE (Static Exchange Evaluation) CONSTANTS ===

    /** Minimum SEE value to consider capture worthwhile */
    public static final int SEE_MINIMUM_GAIN = 0;

    /** SEE piece values */
    public static final int SEE_TOWER_VALUE = 100;
    public static final int SEE_GUARD_VALUE = 2000;

    /** Maximum SEE calculation depth */
    public static final int SEE_MAX_DEPTH = 16;

// === DEBUGGING AND TUNING FLAGS ===

    /** Enable detailed search debugging output */
    public static final boolean DEBUG_SEARCH = false;

    /** Enable move ordering statistics */
    public static final boolean DEBUG_MOVE_ORDERING = false;

    /** Enable pruning statistics */
    public static final boolean DEBUG_PRUNING = false;

    /** Enable threat detection debugging */
    public static final boolean DEBUG_THREATS = false;

// === POSITION ANALYSIS CONSTANTS ===

    /** Minimum piece count for middlegame */
    public static final int MIDDLEGAME_PIECE_THRESHOLD = 12;

    /** Maximum piece count for endgame special handling */
    public static final int ENDGAME_PIECE_THRESHOLD = 8;

    /** Complexity threshold for extended time usage */
    public static final int POSITION_COMPLEXITY_THRESHOLD = 5;

    // === SEARCH STRATEGY CONFIGURATION ===
    public enum SearchStrategy {
        ALPHA_BETA("Alpha-Beta"),
        ALPHA_BETA_Q("Alpha-Beta + Quiescence"),
        PVS("Principal Variation Search"),
        PVS_Q("PVS + Quiescence (ULTIMATE)");

        public final String displayName;

        SearchStrategy(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // === TIME MANAGEMENT CONFIGURATION ===
    public static class TimeConfig {
        public final long totalTimeMs;
        public final int estimatedMovesLeft;
        public final double emergencyTimeRatio;
        public final double complexityTimeModifier;

        public TimeConfig(long totalTimeMs, int estimatedMovesLeft) {
            this(totalTimeMs, estimatedMovesLeft, 0.1, 1.5);
        }

        public TimeConfig(long totalTimeMs, int estimatedMovesLeft,
                          double emergencyTimeRatio, double complexityTimeModifier) {
            this.totalTimeMs = totalTimeMs;
            this.estimatedMovesLeft = estimatedMovesLeft;
            this.emergencyTimeRatio = emergencyTimeRatio;
            this.complexityTimeModifier = complexityTimeModifier;
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

    // === SEARCH CONFIGURATION BUILDER ===
    public static class Builder {
        private SearchStrategy strategy = SearchStrategy.PVS_Q;
        private TimeConfig timeConfig = new TimeConfig(5000, 30);
        private EvaluationConfig evalConfig = new EvaluationConfig();
        private boolean useIterativeDeepening = true;
        private boolean useAspirationWindows = true;
        private boolean useAdvancedPruning = true;

        public Builder withStrategy(SearchStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder withTimeConfig(TimeConfig timeConfig) {
            this.timeConfig = timeConfig;
            return this;
        }

        public Builder withEvaluationConfig(EvaluationConfig evalConfig) {
            this.evalConfig = evalConfig;
            return this;
        }

        public Builder withIterativeDeepening(boolean use) {
            this.useIterativeDeepening = use;
            return this;
        }

        public Builder withAspirationWindows(boolean use) {
            this.useAspirationWindows = use;
            return this;
        }

        public Builder withAdvancedPruning(boolean use) {
            this.useAdvancedPruning = use;
            return this;
        }

        public SearchConfiguration build() {
            return new SearchConfiguration(strategy, timeConfig, evalConfig,
                    useIterativeDeepening, useAspirationWindows, useAdvancedPruning);
        }
    }

    // === COMPLETE SEARCH CONFIGURATION ===
    public static class SearchConfiguration {
        public final SearchStrategy strategy;
        public final TimeConfig timeConfig;
        public final EvaluationConfig evaluationConfig;
        public final boolean useIterativeDeepening;
        public final boolean useAspirationWindows;
        public final boolean useAdvancedPruning;

        public SearchConfiguration(SearchStrategy strategy, TimeConfig timeConfig,
                                   EvaluationConfig evalConfig, boolean useIterativeDeepening,
                                   boolean useAspirationWindows, boolean useAdvancedPruning) {
            this.strategy = strategy;
            this.timeConfig = timeConfig;
            this.evaluationConfig = evalConfig;
            this.useIterativeDeepening = useIterativeDeepening;
            this.useAspirationWindows = useAspirationWindows;
            this.useAdvancedPruning = useAdvancedPruning;
        }

        @Override
        public String toString() {
            return String.format("SearchConfig{strategy=%s, time=%dms, moves=%d, ID=%s, AW=%s, AP=%s}",
                    strategy, timeConfig.totalTimeMs, timeConfig.estimatedMovesLeft,
                    useIterativeDeepening, useAspirationWindows, useAdvancedPruning);
        }
    }

    // === PRESET CONFIGURATIONS ===

    /**
     * Tournament configuration - Maximum strength
     */
    public static SearchConfiguration tournamentConfig(long timeMs, int movesLeft) {
        return new Builder()
                .withStrategy(SearchStrategy.PVS_Q)
                .withTimeConfig(new TimeConfig(timeMs, movesLeft))
                .withIterativeDeepening(true)
                .withAspirationWindows(true)
                .withAdvancedPruning(true)
                .build();
    }

    /**
     * Blitz configuration - Fast but strong
     */
    public static SearchConfiguration blitzConfig(long timeMs, int movesLeft) {
        return new Builder()
                .withStrategy(SearchStrategy.ALPHA_BETA_Q)
                .withTimeConfig(new TimeConfig(timeMs, movesLeft, 0.15, 1.2))
                .withIterativeDeepening(true)
                .withAspirationWindows(false)
                .withAdvancedPruning(true)
                .build();
    }

    /**
     * Debug configuration - Slower but detailed
     */
    public static SearchConfiguration debugConfig(long timeMs, int movesLeft) {
        return new Builder()
                .withStrategy(SearchStrategy.ALPHA_BETA)
                .withTimeConfig(new TimeConfig(timeMs, movesLeft))
                .withIterativeDeepening(true)
                .withAspirationWindows(false)
                .withAdvancedPruning(false)
                .build();
    }

    /**
     * Emergency configuration - Minimal time
     */
    public static SearchConfiguration emergencyConfig() {
        return new Builder()
                .withStrategy(SearchStrategy.ALPHA_BETA)
                .withTimeConfig(new TimeConfig(EMERGENCY_TIME_MS, 10))
                .withIterativeDeepening(false)
                .withAspirationWindows(false)
                .withAdvancedPruning(false)
                .build();
    }
}