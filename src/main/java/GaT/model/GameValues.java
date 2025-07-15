package GaT.model;

/**
 * GAME VALUES - Single source of truth for all piece values
 *
 * ELIMINATES:
 * - Evaluation dependency in move ordering
 * - Scattered piece value constants
 * - Runtime evaluation calls for move scoring
 *
 * PROVIDES:
 * - Compile-time constants for maximum performance
 * - Centralized value management
 * - Zero-overhead move scoring
 */
public final class GameValues {

    // === CORE PIECE VALUES ===
    public static final int TOWER_BASE_VALUE = 100;
    public static final int TOWER_HEIGHT_MULTIPLIER = 100;  // Each height level worth 100
    public static final int GUARD_VALUE = 1000;

    // === CAPTURE VALUES (MVV-LVA) ===
    public static final int GUARD_CAPTURE_VALUE = 10000;    // Winning move
    public static final int TOWER_CAPTURE_BASE = 200;       // Base value for capturing tower

    // === POSITIONAL BONUSES ===
    public static final int CENTER_FILE_BONUS = 50;         // Files C, D, E
    public static final int D_FILE_BONUS = 100;             // Special bonus for D-file
    public static final int ADVANCEMENT_BONUS = 25;         // Per rank toward enemy
    public static final int CASTLE_PROXIMITY_BONUS = 200;   // Near enemy castle

    // === TACTICAL VALUES ===
    public static final int CHECK_BONUS = 300;              // Threatening enemy guard
    public static final int ESCAPE_PENALTY = 150;           // Losing escape routes
    public static final int MOBILITY_BONUS = 10;            // Per available move

    // === MOVE ORDERING PRIORITIES ===
    public static final int TT_MOVE_PRIORITY = 1000000;     // Highest priority
    public static final int GUARD_CAPTURE_PRIORITY = 100000;
    public static final int TOWER_CAPTURE_PRIORITY = 50000;
    public static final int KILLER_MOVE_PRIORITY = 10000;
    public static final int HISTORY_MOVE_BASE = 1000;

    // === QUICK VALUE LOOKUPS ===

    /**
     * Get tower value based on height (zero-overhead)
     */
    public static int getTowerValue(int height) {
        return TOWER_BASE_VALUE + (height * TOWER_HEIGHT_MULTIPLIER);
    }

    /**
     * Get capture value for MVV-LVA ordering
     */
    public static int getCaptureValue(boolean isGuardCapture, int capturedTowerHeight) {
        if (isGuardCapture) {
            return GUARD_CAPTURE_VALUE;
        }
        return TOWER_CAPTURE_BASE + (capturedTowerHeight * TOWER_HEIGHT_MULTIPLIER);
    }

    /**
     * Get piece value for attacker (LVA - Least Valuable Attacker)
     */
    public static int getAttackerValue(boolean isGuardAttacker, int attackerTowerHeight) {
        if (isGuardAttacker) {
            return GUARD_VALUE;
        }
        return getTowerValue(attackerTowerHeight);
    }

    /**
     * Calculate MVV-LVA score for move ordering
     * Higher scores = better captures (capture valuable piece with cheap piece)
     */
    public static int getMVVLVAScore(GameState state, int from, int to) {
        // Determine what's being captured
        long toBit = GameState.bit(to);
        boolean capturesGuard = ((state.redGuard | state.blueGuard) & toBit) != 0;
        int capturedTowerHeight = Math.max(state.redStackHeights[to], state.blueStackHeights[to]);

        if (!capturesGuard && capturedTowerHeight == 0) {
            return 0; // No capture
        }

        // Determine what's attacking
        long fromBit = GameState.bit(from);
        boolean attackerIsGuard = ((state.redGuard | state.blueGuard) & fromBit) != 0;
        int attackerTowerHeight = Math.max(state.redStackHeights[from], state.blueStackHeights[from]);

        // Calculate MVV-LVA: High victim value - Low attacker value
        int victimValue = getCaptureValue(capturesGuard, capturedTowerHeight);
        int attackerValue = getAttackerValue(attackerIsGuard, attackerTowerHeight);

        return victimValue * 10 - attackerValue; // Scale victim value higher
    }

    /**
     * Simple capture detection
     */
    public static boolean isCapture(GameState state, int from, int to) {
        long toBit = GameState.bit(to);
        return ((state.redGuard | state.blueGuard) & toBit) != 0 ||
                state.redStackHeights[to] > 0 || state.blueStackHeights[to] > 0;
    }

    // === POSITIONAL VALUE LOOKUPS ===

    /**
     * Get file bonus (D-file is most important)
     */
    public static int getDFileBonus(int square) {
        int file = square % 7;
        if (file == 3) return D_FILE_BONUS;      // D-file (central)
        if (file == 2 || file == 4) return 25;  // C and E files
        return 0;
    }

    /**
     * Central control bonus
     */
    public static int getCentralBonus(int square) {
        int file = square % 7;
        int rank = square / 7;

        // D-file gets highest bonus
        if (file == 3) return CENTER_FILE_BONUS;

        // Central files get medium bonus
        if (file >= 2 && file <= 4) return CENTER_FILE_BONUS / 2;

        // Central ranks get small bonus
        if (rank >= 2 && rank <= 4) return 10;

        return 0;
    }

    /**
     * Advancement bonus (moving toward enemy)
     */
    public static int getGuardAdvancementBonus(int from, int to, boolean isRed) {
        int fromRank = from / 7;
        int toRank = to / 7;

        if (isRed && toRank > fromRank) {
            return ADVANCEMENT_BONUS; // Red advances up
        }
        if (!isRed && toRank < fromRank) {
            return ADVANCEMENT_BONUS; // Blue advances down
        }

        return 0;
    }

    /**
     * Development bonus (piece away from starting position)
     */
    public static int getDevelopmentBonus(int square, boolean isRed) {
        int rank = square / 7;

        if (isRed && rank > 0) return 15;   // Red away from rank 0
        if (!isRed && rank < 6) return 15;  // Blue away from rank 6

        return 0;
    }

    /**
     * Castle proximity bonus
     */
    public static int getCastleProximityBonus(int square, boolean isRed) {
        int targetCastle = isRed ? BLUE_CASTLE_SQUARE : RED_CASTLE_SQUARE;
        int distance = getManhattanDistance(square, targetCastle);

        // Closer to enemy castle = higher bonus
        return Math.max(0, CASTLE_PROXIMITY_BONUS - (distance * 20));
    }

    private static int getManhattanDistance(int square1, int square2) {
        int rank1 = square1 / 7, file1 = square1 % 7;
        int rank2 = square2 / 7, file2 = square2 % 7;
        return Math.abs(rank1 - rank2) + Math.abs(file1 - file2);
    }

    // === GAME-SPECIFIC CONSTANTS ===

    // Castle squares
    public static final int RED_CASTLE_SQUARE = 45;    // D7 (6*7 + 3)
    public static final int BLUE_CASTLE_SQUARE = 3;    // D1 (0*7 + 3)

    // Search bounds
    public static final int ALPHA_INIT = -999999;
    public static final int BETA_INIT = 999999;
    public static final int TIMEOUT_VALUE = 0;

    // Terminal values
    public static final int CHECKMATE_VALUE = 100000;
    public static final int STALEMATE_VALUE = 0;
    public static final int DRAW_VALUE = 0;
}