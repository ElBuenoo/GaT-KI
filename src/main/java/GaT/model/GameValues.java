package GaT.model;

/**
 * Single source of truth for all game piece values and priorities
 * Replaces the triple value system across move ordering, evaluation, and quiescence
 */
public class GameValues {

    // === CORE PIECE VALUES ===
    public static final int TOWER_VALUE = 100;           // Base value per tower piece
    public static final int GUARD_VALUE = 1000;          // Guard value for move ordering
    public static final int WINNING_SCORE = 10000;       // Terminal position score

    // === MOVE ORDERING PRIORITIES ===
    public static final int TT_MOVE_PRIORITY = 1000000;      // Transposition table move
    public static final int CAPTURE_PRIORITY_BASE = 100000;  // Base for all captures
    public static final int KILLER_MOVE_PRIORITY = 10000;    // Killer moves
    public static final int HISTORY_MAX_SCORE = 1000;        // Maximum history score

    // === CAPTURE VALUES (for MVV-LVA) ===
    public static final int GUARD_CAPTURE_VALUE = 900;       // Guard capture in move ordering
    public static final int TOWER_CAPTURE_VALUE = 100;       // Per tower height in capture

    // === POSITIONAL BONUSES (EVALUATION ONLY) ===
    public static final int D_FILE_BONUS = 8;                // Controlling D-file
    public static final int CENTRAL_BONUS = 3;               // Central files (C,D,E)
    public static final int ADVANCEMENT_BONUS = 5;           // Per rank advanced
    public static final int GUARD_DISTANCE_BONUS = 50;       // Per step closer to castle

    // === CONTEXT MULTIPLIERS ===
    public static final int MVV_MULTIPLIER = 100;
    public static final int LVA_MULTIPLIER = 1;
}