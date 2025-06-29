package GaT.engine;

/**
 * Time management for game moves
 * Handles time allocation based on remaining time and game phase
 */
public class TimeManager {
    private final long initialTime;
    private final int estimatedMovesPerGame;
    private long gameStartTime;

    public TimeManager(long initialTimeMs, int estimatedMovesPerGame) {
        this.initialTime = initialTimeMs;
        this.estimatedMovesPerGame = estimatedMovesPerGame;
        this.gameStartTime = System.currentTimeMillis();
    }

    /**
     * Calculate time to allocate for the next move
     * @param remainingTimeMs Total time remaining
     * @param moveNumber Current move number
     * @param expectedTotalMoves Expected total moves in game
     * @return Time to allocate in milliseconds
     */
    public long calculateMoveTime(long remainingTimeMs, int moveNumber, int expectedTotalMoves) {
        // Safety check
        if (remainingTimeMs <= 0) {
            return 100; // Emergency time
        }

        // Emergency mode - very low time
        if (remainingTimeMs < 5000) { // Less than 5 seconds
            return Math.min(remainingTimeMs / 10, 500);
        }

        // Calculate expected remaining moves
        int movesLeft = Math.max(expectedTotalMoves - moveNumber, 10);

        // Base time per move
        long baseTime = remainingTimeMs / movesLeft;

        // Game phase adjustment
        double phaseMultiplier = getPhaseMultiplier(moveNumber, expectedTotalMoves);

        // Time allocation with phase adjustment
        long allocatedTime = (long)(baseTime * phaseMultiplier);

        // Safety limits
        long minTime = 100; // Minimum 100ms
        long maxTime = remainingTimeMs / 5; // Max 20% of remaining time

        allocatedTime = Math.max(minTime, Math.min(maxTime, allocatedTime));

        // Extra safety for low time
        if (remainingTimeMs < 30000) { // Less than 30 seconds
            allocatedTime = Math.min(allocatedTime, remainingTimeMs / 15);
        }

        return allocatedTime;
    }

    /**
     * Get phase multiplier based on game stage
     */
    private double getPhaseMultiplier(int moveNumber, int expectedTotalMoves) {
        double gameProgress = (double) moveNumber / expectedTotalMoves;

        if (gameProgress < 0.15) {
            // Opening - use less time
            return 0.8;
        } else if (gameProgress < 0.6) {
            // Middle game - use more time for critical positions
            return 1.3;
        } else if (gameProgress < 0.85) {
            // Late middle game
            return 1.1;
        } else {
            // Endgame - use less time (positions simpler)
            return 0.9;
        }
    }

    /**
     * Alternative simple time calculation
     */
    public long calculateSimpleTime(long remainingTimeMs, int movesLeft) {
        if (movesLeft <= 0) movesLeft = 20;
        return Math.max(100, remainingTimeMs / movesLeft);
    }

    /**
     * Reset game start time
     */
    public void resetGameTime() {
        this.gameStartTime = System.currentTimeMillis();
    }

    /**
     * Get elapsed game time
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - gameStartTime;
    }
}