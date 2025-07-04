package GaT.model;

public class SearchConfig {
    // === SEARCH LIMITS ===
    public static final int MAX_DEPTH = 99;
    public static final int DEFAULT_TIME_LIMIT_MS = 5000;
    public static final int EMERGENCY_TIME_MS = 200;
    public static final int PANIC_TIME_MS = 50;

    // === ASPIRATION WINDOW PARAMETERS ===
    public static final int ASPIRATION_WINDOW_DELTA = 50;
    public static final int ASPIRATION_WINDOW_MAX_FAILS = 3;
    public static final int ASPIRATION_WINDOW_GROWTH_FACTOR = 4;

    // === TRANSPOSITION TABLE ===
    public static final int TT_SIZE = 2_000_000;
    public static final int TT_EVICTION_THRESHOLD = 1_500_000;

    // === PRUNING PARAMETERS ===
    public static final int NULL_MOVE_MIN_DEPTH = 3;        // ✅ NEU
    public static final int NULL_MOVE_REDUCTION = 3;        // ✅ NEU: R-Wert
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

    // Rest der Klasse bleibt unverändert...
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

    public static SearchConfiguration tournamentConfig(long timeMs, int movesLeft) {
        return new Builder()
                .withStrategy(SearchStrategy.PVS_Q)
                .withTimeConfig(new TimeConfig(timeMs, movesLeft))
                .withIterativeDeepening(true)
                .withAspirationWindows(true)
                .withAdvancedPruning(true)
                .build();
    }

    public static SearchConfiguration blitzConfig(long timeMs, int movesLeft) {
        return new Builder()
                .withStrategy(SearchStrategy.ALPHA_BETA_Q)
                .withTimeConfig(new TimeConfig(timeMs, movesLeft, 0.15, 1.2))
                .withIterativeDeepening(true)
                .withAspirationWindows(false)
                .withAdvancedPruning(true)
                .build();
    }

    public static SearchConfiguration debugConfig(long timeMs, int movesLeft) {
        return new Builder()
                .withStrategy(SearchStrategy.ALPHA_BETA)
                .withTimeConfig(new TimeConfig(timeMs, movesLeft))
                .withIterativeDeepening(true)
                .withAspirationWindows(false)
                .withAdvancedPruning(false)
                .build();
    }

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
