package GaT.model;

/**
 * GAME VALUES - Turm & Wächter spezifische Piece Values
 *
 * Ersetzt teure Evaluation-Aufrufe in Move Ordering mit schnellen Lookups
 * Direkt angepasst an Ihre Spielregeln
 */
public final class GameValues {

    // === TURM & WÄCHTER PIECE VALUES ===
    public static final int GUARD_VALUE = 10000;           // Wächter ist wertvollste Figur
    public static final int TOWER_BASE_VALUE = 100;        // Turm Grundwert
    public static final int TOWER_HEIGHT_MULTIPLIER = 50;  // Zusatzwert pro Höhe

    // === CAPTURE VALUES (MVV-LVA für Ihre Engine) ===
    public static final int GUARD_CAPTURE = 10000;         // Wächter schlagen = Gewinn
    public static final int TOWER_CAPTURE_BASE = 100;      // Turm schlagen
    public static final int HEIGHT_CAPTURE_BONUS = 100;    // Bonus pro Turmhöhe

    // === MOVE ORDERING PRIORITIES ===
    public static final int TT_MOVE_PRIORITY = 1000000;    // Hash-Move höchste Priorität
    public static final int CAPTURE_BASE_PRIORITY = 100000; // Schlagzüge
    public static final int KILLER_1_PRIORITY = 10000;     // Killer-Move 1
    public static final int KILLER_2_PRIORITY = 9000;      // Killer-Move 2
    public static final int HISTORY_MAX_PRIORITY = 1000;   // History Heuristic max

    // === TURM & WÄCHTER POSITIONAL WERTE ===

    /**
     * Turm-Wert basierend auf Höhe (schnell!)
     */
    public static int getTowerValue(int height) {
        return TOWER_BASE_VALUE + (height * TOWER_HEIGHT_MULTIPLIER);
    }

    /**
     * Schneller Capture-Wert für MVV-LVA
     */
    public static int getCaptureValue(GameState state, int toSquare) {
        // Wächter schlagen?
        long toBit = GameState.bit(toSquare);
        if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
            return GUARD_CAPTURE;
        }

        // Turm schlagen?
        int redHeight = state.redStackHeights[toSquare];
        int blueHeight = state.blueStackHeights[toSquare];
        int capturedHeight = Math.max(redHeight, blueHeight);

        if (capturedHeight > 0) {
            return TOWER_CAPTURE_BASE + (capturedHeight * HEIGHT_CAPTURE_BONUS);
        }

        return 0; // Leeres Feld
    }

    /**
     * Angreifer-Wert für LVA (Least Valuable Attacker)
     */
    public static int getAttackerValue(GameState state, int fromSquare) {
        long fromBit = GameState.bit(fromSquare);

        // Wächter greift an
        if ((state.redGuard & fromBit) != 0 || (state.blueGuard & fromBit) != 0) {
            return GUARD_VALUE;
        }

        // Turm greift an
        int redHeight = state.redStackHeights[fromSquare];
        int blueHeight = state.blueStackHeights[fromSquare];
        int attackerHeight = Math.max(redHeight, blueHeight);

        if (attackerHeight > 0) {
            return getTowerValue(attackerHeight);
        }

        return 0;
    }

    /**
     * MVV-LVA Score: Maximize Victim Value, Minimize Attacker Value
     * SCHNELL - keine Evaluation-Aufrufe!
     */
    public static int getMVVLVAScore(GameState state, int fromSquare, int toSquare) {
        int victimValue = getCaptureValue(state, toSquare);
        int attackerValue = getAttackerValue(state, fromSquare);

        // Standard MVV-LVA Formel
        return victimValue * 100 - attackerValue;
    }

    // === TURM & WÄCHTER POSITIONAL BONUSES (EINFACH) ===

    /**
     * D-File Bonus (wichtig in Turm & Wächter)
     */
    public static int getDFileBonus(int square) {
        int file = square % 7;
        return file == 3 ? 50 : 0; // D-file ist File 3
    }

    /**
     * Zentral-Kontrolle Bonus
     */
    public static int getCentralBonus(int square) {
        int file = square % 7;
        int rank = square / 7;

        // D-file bekommt höchsten Bonus
        if (file == 3) return 50;

        // C und E files bekommen mittleren Bonus
        if (file == 2 || file == 4) return 25;

        // Zentrale Reihen bekommen kleinen Bonus
        if (rank >= 2 && rank <= 4) return 10;

        return 0;
    }

    /**
     * Wächter-Advancement Bonus (Richtung gegnerische Burg)
     */
    public static int getGuardAdvancementBonus(int fromSquare, int toSquare, boolean isRed) {
        int fromRank = fromSquare / 7;
        int toRank = toSquare / 7;

        if (isRed && toRank > fromRank) return 30; // Rot bewegt sich zu höheren Reihen
        if (!isRed && toRank < fromRank) return 30; // Blau bewegt sich zu niedrigeren Reihen

        return 0;
    }

    /**
     * Development Bonus - Figur weg von Grundreihe
     */
    public static int getDevelopmentBonus(int square, boolean isRed) {
        int rank = square / 7;

        if (isRed && rank > 0) return 20;  // Rot weg von Reihe 0
        if (!isRed && rank < 6) return 20; // Blau weg von Reihe 6

        return 0;
    }

    // === SPIEL-SPEZIFISCHE KONSTANTEN ===

    // Burg-Positionen (wie in Ihrem Code)
    public static final int RED_CASTLE_SQUARE = GameState.getIndex(6, 3);   // D7
    public static final int BLUE_CASTLE_SQUARE = GameState.getIndex(0, 3);  // D1

    // Terminal Werte
    public static final int CHECKMATE_VALUE = 100000;
    public static final int STALEMATE_VALUE = 0;
    public static final int DRAW_VALUE = 0;

    // Search Bounds
    public static final int ALPHA_INIT = -999999;
    public static final int BETA_INIT = 999999;
    public static final int TIMEOUT_VALUE = 0;
}