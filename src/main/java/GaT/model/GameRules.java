package GaT.model;

public class GameRules {

    // Castle positions
    public static final int RED_CASTLE = GameState.getIndex(0, 3);  // D1
    public static final int BLUE_CASTLE = GameState.getIndex(6, 3); // D7

    public static boolean isGameOver(GameState state) {
        // Check if either guard is captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Check if guard reached enemy castle
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (redGuardPos == BLUE_CASTLE) return true;
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (blueGuardPos == RED_CASTLE) return true;
        }

        return false;
    }

    public static int getWinner(GameState state) {
        if (state.redGuard == 0) return -1;  // Blue wins
        if (state.blueGuard == 0) return 1;   // Red wins

        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (redGuardPos == BLUE_CASTLE) return 1;   // Red wins
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (blueGuardPos == RED_CASTLE) return -1;  // Blue wins
        }

        return 0; // Should not happen
    }

    public static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    public static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    // ✅ Check if position is in check - FÜR NULL MOVE PRUNING
    public static boolean isInCheck(GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        return isPositionUnderAttack(state, guardPos, !isRed);
    }

    // ✅ Check if a position is under attack by enemy pieces
    public static boolean isPositionUnderAttack(GameState state, int position, boolean byRed) {
        // Check if any enemy piece can attack this position

        // Check guard attacks (adjacent squares)
        long enemyGuard = byRed ? state.redGuard : state.blueGuard;
        if (enemyGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(enemyGuard);
            if (isAdjacentSquare(guardPos, position)) {
                return true;
            }
        }

        // Check tower attacks (rank/file attacks)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int towerHeight = byRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (towerHeight > 0) {
                if (canTowerAttack(i, position, towerHeight, state)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ✅ Check if two squares are adjacent (for guard attacks)
    private static boolean isAdjacentSquare(int from, int to) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Adjacent means exactly one square away in rank or file (not diagonal)
        return (rankDiff == 1 && fileDiff == 0) || (rankDiff == 0 && fileDiff == 1);
    }

    // ✅ Check if a tower can attack a position
    private static boolean canTowerAttack(int from, int to, int towerHeight, GameState state) {
        if (from == to) return false;

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        if (distance > towerHeight) return false;

        // Check if path is clear
        return isPathClear(state, from, to);
    }

    // ✅ Check if path between two squares is clear
    private static boolean isPathClear(GameState state, int from, int to) {
        int rankFrom = GameState.rank(from);
        int fileFrom = GameState.file(from);
        int rankTo = GameState.rank(to);
        int fileTo = GameState.file(to);

        int rankStep = Integer.compare(rankTo, rankFrom);
        int fileStep = Integer.compare(fileTo, fileFrom);

        int currentRank = rankFrom + rankStep;
        int currentFile = fileFrom + fileStep;

        while (currentRank != rankTo || currentFile != fileTo) {
            int square = GameState.getIndex(currentRank, currentFile);

            // Check if square is occupied
            if (((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                    & GameState.bit(square)) != 0) {
                return false;
            }

            currentRank += rankStep;
            currentFile += fileStep;
        }

        return true;
    }
}