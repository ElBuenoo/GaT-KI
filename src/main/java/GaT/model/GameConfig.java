package GaT.model;

/**
 * Minimal essential configuration - replaces bloated SearchConfig
 * Contains only the 15 most critical parameters
 */
public class GameConfig {

    // === CORE SEARCH PARAMETERS ===
    public static final int MAX_DEPTH = 10;
    public static final int MIN_SEARCH_DEPTH = 5;
    public static final int TT_SIZE = 8_000_000;
    public static final int NODES_PER_SECOND_TARGET = 50000;

    // === SEARCH STRATEGY ===
    public enum Strategy { ALPHA_BETA, PVS, PVS_Q }
    public static final Strategy DEFAULT_STRATEGY = Strategy.PVS_Q;

    // === TIME MANAGEMENT ===
    public static final long EMERGENCY_TIME_MS = 500;
    public static final long LOW_TIME_THRESHOLD = 10000;
    public static final double TIME_CRITICAL_FACTOR = 0.25;
    public static final double TIME_NORMAL_FACTOR = 0.12;

    // === PRUNING ===
    public static final boolean NULL_MOVE_ENABLED = true;
    public static final int NULL_MOVE_MIN_DEPTH = 3;
    public static final int NULL_MOVE_REDUCTION = 2;

    // === QUIESCENCE ===
    public static final int MAX_Q_DEPTH = 4;
    public static final int Q_DELTA_MARGIN = 300;

    // === MOVE ORDERING ===
    public static final int KILLER_MOVE_SLOTS = 2;
    public static final int MAX_KILLER_DEPTH = 20;
    public static final int HISTORY_MAX_VALUE = 10000;
}