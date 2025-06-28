package GaT.model;

/**
 * Game rules and win condition checking
 * Extracted from Minimax to avoid circular dependencies
 */
public class GameRules {

    // Castle positions
    public static final int RED_CASTLE = GameState.getIndex(0, 3);  // D1
    public static final int BLUE_CASTLE = GameState.getIndex(6, 3); // D7

    /**
     * Check if the game is over
     */
    public static boolean isGameOver(GameState state) {
        // Check if either guard is captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Check if guard reached enemy castle
        int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
        int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);

        // Red guard on blue castle (D1) or blue guard on red castle (D7)
        return redGuardPos == BLUE_CASTLE || blueGuardPos == RED_CASTLE;
    }

    /**
     * Get the winner of the game (assuming game is over)
     * @return 1 for red, -1 for blue, 0 for draw (shouldn't happen)
     */
    public static int getWinner(GameState state) {
        if (state.redGuard == 0) return -1;  // Blue wins
        if (state.blueGuard == 0) return 1;   // Red wins

        int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
        int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);

        if (redGuardPos == BLUE_CASTLE) return 1;   // Red wins
        if (blueGuardPos == RED_CASTLE) return -1;  // Blue wins

        return 0; // Should not happen
    }

    /**
     * Check if a move captures a piece
     */
    public static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if a move is a guard move
     */
    public static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    /**
     * Check if position is in check (simplified)
     */
    public static boolean isInCheck(GameState state) {
        // Simplified check detection
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check if any enemy piece can capture the guard
        long enemyPieces = isRed ? (state.blueTowers | state.blueGuard) :
                (state.redTowers | state.redGuard);

        // Simplified: just check adjacent squares
        int rank = GameState.rank(guardPos);
        int file = GameState.file(guardPos);

        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                if (Math.abs(dr) == Math.abs(df)) continue; // No diagonals

                int newRank = rank + dr;
                int newFile = file + df;

                if (newRank >= 0 && newRank < 7 && newFile >= 0 && newFile < 7) {
                    int square = GameState.getIndex(newRank, newFile);
                    if ((enemyPieces & GameState.bit(square)) != 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}