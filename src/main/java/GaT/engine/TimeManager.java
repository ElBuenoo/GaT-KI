package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import java.util.List;

/**
 * PHASE 1 FIXED TimeManager - More Conservative Time Allocation
 *
 * PHASE 1 FIXES:
 * ✅ 1. Reduced critical position time (33% -> 25%)
 * ✅ 2. More conservative max time limits (33% -> 25%)
 * ✅ 3. Safer minimum time calculation (5% -> 4%)
 * ✅ 4. Better low-time handling (25% -> 20%)
 * ✅ 5. Emergency time conservation enhanced
 */
public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private int moveNumber;
    private Phase phase;

    // More conservative thresholds
    private static final long PANIC_TIME_THRESHOLD = 500;
    private static final long EMERGENCY_TIME_THRESHOLD = 3000;
    private static final long LOW_TIME_THRESHOLD = 10000;
    private static final long COMFORT_TIME_THRESHOLD = 30000;

    public enum Phase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = Math.max(estimatedMovesLeft, 15); // More conservative
        this.phase = Phase.OPENING;
        this.moveNumber = 0;
    }

    /**
     * PHASE 1 FIXED: More conservative time allocation
     */
    public long calculateTimeForMove(GameState state) {
        moveNumber++;

        // Absolute emergency - minimal time
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            return Math.max(50, remainingTime / 4); // Use 25% in panic
        }

        // Emergency mode - conservative
        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            return Math.max(200, remainingTime / 6); // Use ~16% in emergency
        }

        // Detect game phase and position criticality
        this.phase = detectGamePhase(state);
        boolean isCritical = isCriticalPosition(state);
        int complexity = evaluatePositionComplexity(state);

        // Base calculation - more conservative
        long baseTime = calculateBalancedBaseTime();

        // Phase-based adjustments - more balanced
        switch (phase) {
            case OPENING:
                if (moveNumber <= 10) {
                    baseTime = baseTime; // Less time in early opening
                }
                break;

            case MIDDLEGAME:
                baseTime = (long)(baseTime * 1.2); // Slightly more for complex positions
                if (complexity > 40) {
                    baseTime = (long)(baseTime * 1.3); // Extra for very complex
                }
                break;

            case ENDGAME:
                baseTime = (long)(baseTime * 1.5); // More time for endgames
                if (getTotalMaterial(state) <= 6) {
                    baseTime = baseTime * 2; // Double for extreme endgames
                }
                break;
        }

        // PHASE 1 FIX: Critical position adjustment - more conservative
        if (isCritical) {
            System.out.println("🔴 CRITICAL POSITION DETECTED!");
            long criticalTime = remainingTime / 4; // FIXED: Use 25% max (was 33%)
            baseTime = Math.max(baseTime, criticalTime);
        }

        // Material imbalance adjustment
        int materialDiff = getMaterialDifference(state);
        if (materialDiff < -1) { // We're behind
            baseTime = (long)(baseTime * 1.3); // 30% more when behind
        } else if (materialDiff > 2) { // We're ahead
            baseTime = (long)(baseTime * 0.9); // Slightly faster when winning
        }

        // PHASE 1 FIX: Time bounds - more conservative
        long minTime = Math.max(500, remainingTime / 25);    // FIXED: Min 4% (was 5%) or 0.5 seconds
        long maxTime = remainingTime / 4;                     // FIXED: Max 25% (was 33%)

        // PHASE 1 FIX: Special handling for low time - even more conservative
        if (remainingTime < LOW_TIME_THRESHOLD) {
            maxTime = remainingTime / 5; // FIXED: Max 20% when low on time (was 25%)
        }

        // Ensure we don't use too much time per move
        if (remainingTime > COMFORT_TIME_THRESHOLD) {
            // When we have comfortable time, don't use more than needed
            long moveBasedLimit = remainingTime / Math.max(10, estimatedMovesLeft - moveNumber);
            maxTime = Math.min(maxTime, moveBasedLimit * 2); // 2x average
        }

        baseTime = Math.max(minTime, Math.min(baseTime, maxTime));

        System.out.printf("🕐 Time: %dms (%.1f%% of %dms remaining)%n",
                baseTime, (double)baseTime/remainingTime*100, remainingTime);
        System.out.printf("   Phase: %s | Complexity: %d | Critical: %s | Material: %+d%n",
                phase, complexity, isCritical, materialDiff);

        return baseTime;
    }

    /**
     * More balanced base time calculation
     */
    private long calculateBalancedBaseTime() {
        // Dynamic moves estimation based on game progress
        int dynamicMovesLeft = estimatedMovesLeft - moveNumber;
        if (phase == Phase.ENDGAME) {
            dynamicMovesLeft = Math.max(5, dynamicMovesLeft / 2);
        }

        // Base: Use 1/moves of remaining time
        long targetTimePerMove = remainingTime / Math.max(5, dynamicMovesLeft);

        // Conservative minimums based on remaining time
        long conservativeMinimum;
        if (remainingTime > 150000) {      // > 2.5 minutes
            conservativeMinimum = 10000;    // At least 10 seconds (was 20)
        } else if (remainingTime > 90000) { // > 1.5 minutes
            conservativeMinimum = 7000;      // At least 7 seconds (was 15)
        } else if (remainingTime > 60000) { // > 1 minute
            conservativeMinimum = 5000;      // At least 5 seconds (was 10)
        } else if (remainingTime > 30000) { // > 30 seconds
            conservativeMinimum = 3000;      // At least 3 seconds (was 7)
        } else if (remainingTime > 15000) { // > 15 seconds
            conservativeMinimum = 2000;      // At least 2 seconds (was 4)
        } else if (remainingTime > 5000) {  // > 5 seconds
            conservativeMinimum = 1000;      // At least 1 second (was 2)
        } else {
            conservativeMinimum = remainingTime / 5; // 20% minimum
        }

        return Math.max(targetTimePerMove, conservativeMinimum);
    }

    /**
     * More accurate critical position detection
     */
    private boolean isCriticalPosition(GameState state) {
        // Material imbalance
        int materialDiff = getMaterialDifference(state);
        if (Math.abs(materialDiff) >= 2) {
            return true;
        }

        // Guards in danger or can win
        if (isGuardInDanger(state) || hasWinningMove(state)) {
            return true;
        }

        // Late endgame
        if (getTotalMaterial(state) <= 6 && areGuardsAdvanced(state)) {
            return true;
        }

        // High complexity with captures available
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int captures = countCaptures(moves, state);
        if (captures >= 3 && evaluatePositionComplexity(state) > 35) {
            return true;
        }

        return false;
    }

    private int countCaptures(List<Move> moves, GameState state) {
        int count = 0;
        for (Move move : moves) {
            if (isCapture(move, state)) count++;
        }
        return count;
    }

    // [Rest of the helper methods remain the same...]

    private Phase detectGamePhase(GameState state) {
        int totalPieces = getTotalMaterial(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        if (totalPieces <= 6 || (totalPieces <= 8 && guardsAdvanced)) {
            return Phase.ENDGAME;
        } else if (totalPieces <= 10 || guardsAdvanced || moveNumber > 15) {
            return Phase.MIDDLEGAME;
        } else {
            return Phase.OPENING;
        }
    }

    private int evaluatePositionComplexity(GameState state) {
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            int complexity = allMoves.size();

            // Count tactical moves
            int captureCount = 0;
            for (Move move : allMoves) {
                if (isCapture(move, state)) {
                    captureCount++;
                }
            }

            // Add capture complexity
            complexity += captureCount * 3;

            return complexity;
        } catch (Exception e) {
            return 20; // Default complexity
        }
    }

    private boolean areGuardsAdvanced(GameState state) {
        int redGuardRow = -1, blueGuardRow = -1;

        for (int i = 0; i < 49; i++) {
            if ((state.redGuard & (1L << i)) != 0) {
                redGuardRow = i / 7;
            }
            if ((state.blueGuard & (1L << i)) != 0) {
                blueGuardRow = i / 7;
            }
        }

        return (redGuardRow <=4) || (blueGuardRow >= 4);        //Everything from the middle into enemy space counts as advanced
    }

    private boolean isGuardInDanger(GameState state) {
        // Check if guards can be captured
        GameState copy = state.copy();
        copy.redToMove = !copy.redToMove;
        List<Move> opponentMoves = MoveGenerator.generateAllMoves(copy);

        for (Move move : opponentMoves) {
            if (capturesGuard(move, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWinningMove(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (capturesGuard(move, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return (state.redToMove && (state.blueGuard & toBit) != 0) ||
                (!state.redToMove && (state.redGuard & toBit) != 0);
    }

    private int getTotalMaterial(GameState state) {
        int total = 0;
        for (int i = 0; i < 49; i++) {
            total += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return total;
    }

    private int getMaterialDifference(GameState state) {
        int redMaterial = 0, blueMaterial = 0;
        for (int i = 0; i < 49; i++) {
            redMaterial += state.redStackHeights[i];
            blueMaterial += state.blueStackHeights[i];
        }
        return state.redToMove ? (redMaterial - blueMaterial) : (blueMaterial - redMaterial);
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        if(state.redToMove){
            return ((state.blueGuard | state.blueTowers) & toBit) !=0;
        }
        return ((state.redGuard | state.redTowers) & toBit) !=0;
    }

    // Public interface
    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = Math.max(0, remainingTime);
    }

    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 5) { // Keep minimum of 5
            this.estimatedMovesLeft--;
        }
    }

    public Phase getCurrentPhase() {
        return phase;
    }

    public long getRemainingTime() {
        return remainingTime;
    }

    public int getEstimatedMovesLeft() {
        return estimatedMovesLeft;
    }
}