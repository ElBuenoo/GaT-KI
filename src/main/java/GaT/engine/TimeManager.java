package GaT.engine;

import GaT.model.GameState;
import GaT.model.GameConfig;

public class TimeManager {

    private long remainingTime;
    private int estimatedMovesLeft;
    private int moveNumber = 0;

    public TimeManager(long initialTime, int estimatedMoves) {
        this.remainingTime = initialTime;
        this.estimatedMovesLeft = estimatedMoves;
    }

    public long calculateTimeForMove(GameState state) {
        // Simple time calculation
        long baseTime = remainingTime / Math.max(5, estimatedMovesLeft - moveNumber);

        // Emergency handling
        if (remainingTime < GameConfig.EMERGENCY_TIME_MS) {
            return Math.min(baseTime, remainingTime / 4);
        }

        // Low time handling
        if (remainingTime < GameConfig.LOW_TIME_THRESHOLD) {
            return Math.min(baseTime, remainingTime / 3);
        }

        // Normal time allocation
        return Math.min(baseTime, remainingTime / 4);
    }

    public void updateRemainingTime(long timeLeft) {
        this.remainingTime = timeLeft;
    }

    public void incrementMoveNumber() {
        this.moveNumber++;
        if (estimatedMovesLeft > 5) {
            this.estimatedMovesLeft--;
        }
    }

    public long getRemainingTime() {
        return remainingTime;
    }

    public boolean isEmergencyTime() {
        return remainingTime <= GameConfig.EMERGENCY_TIME_MS;
    }

    public boolean isLowTime() {
        return remainingTime <= GameConfig.LOW_TIME_THRESHOLD;
    }
}