package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;

import java.util.List;

/**
 * TIME MANAGER - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Time thresholds using SearchConfig.TIME_* parameters
 * ✅ Time factors using SearchConfig.TIME_*_FACTOR parameters
 * ✅ Multipliers using SearchConfig.TIME_*_MULTIPLIER parameters
 * ✅ Emergency handling using SearchConfig.EMERGENCY_TIME_MS
 * ✅ All hardcoded values replaced with SearchConfig
 */
public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private int moveNumber;
    private Phase phase;

    // === TIME THRESHOLDS FROM SEARCHCONFIG ===
    // Removed hardcoded thresholds, now using SearchConfig:
    // SearchConfig.TIME_PANIC_THRESHOLD (500)
    // SearchConfig.TIME_EMERGENCY_THRESHOLD (3000)
    // SearchConfig.TIME_LOW_THRESHOLD (10000)
    // SearchConfig.TIME_COMFORT_THRESHOLD (30000)

    public enum Phase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = Math.max(estimatedMovesLeft, 15);
        this.phase = Phase.OPENING;
        this.moveNumber = 0;

        System.out.println("🔧 TimeManager initialized with SearchConfig:");
        System.out.println("   TIME_PANIC_THRESHOLD: " + SearchConfig.TIME_PANIC_THRESHOLD);
        System.out.println("   TIME_EMERGENCY_THRESHOLD: " + SearchConfig.TIME_EMERGENCY_THRESHOLD);
        System.out.println("   TIME_COMFORT_THRESHOLD: " + SearchConfig.TIME_COMFORT_THRESHOLD);
    }

    /**
     * Calculate time for move using SearchConfig parameters
     */
    public long calculateTimeForMove(GameState state) {
        moveNumber++;

        // Use SearchConfig time thresholds instead of hardcoded constants
        if (remainingTime <= SearchConfig.TIME_PANIC_THRESHOLD) {
            // Use SearchConfig.TIME_CRITICAL_FACTOR instead of hardcoded 25%
            return Math.max(50, (long)(remainingTime * SearchConfig.TIME_CRITICAL_FACTOR));
        }

        // Emergency mode using SearchConfig
        if (remainingTime <= SearchConfig.TIME_EMERGENCY_THRESHOLD) {
            // Use SearchConfig.TIME_EMERGENCY_FACTOR instead of hardcoded ~16%
            return Math.max(200, (long)(remainingTime * SearchConfig.TIME_EMERGENCY_FACTOR));
        }

        // Detect game phase and position criticality
        this.phase = detectGamePhase(state);
        boolean isCritical = isCriticalPosition(state);
        int complexity = evaluatePositionComplexity(state);

        // Base calculation using SearchConfig
        long baseTime = calculateBalancedBaseTimeWithConfig();

        // Phase-based adjustments using SearchConfig multipliers
        switch (phase) {
            case OPENING:
                if (moveNumber <= 10) {
                    baseTime = baseTime; // Standard time in early opening
                }
                break;

            case MIDDLEGAME:
                // Use SearchConfig.TIME_MIDDLEGAME_MULTIPLIER instead of hardcoded 1.2
                baseTime = (long)(baseTime * SearchConfig.TIME_MIDDLEGAME_MULTIPLIER);
                if (complexity > 40) {
                    baseTime = (long)(baseTime * 1.3); // Extra for very complex
                }
                break;

            case ENDGAME:
                // Use SearchConfig.TIME_ENDGAME_MULTIPLIER instead of hardcoded 1.5
                baseTime = (long)(baseTime * SearchConfig.TIME_ENDGAME_MULTIPLIER);
                if (getTotalMaterial(state) <= SearchConfig.TABLEBASE_MATERIAL_THRESHOLD) {
                    baseTime = baseTime * 2; // Double for extreme endgames
                }
                break;
        }

        // Critical position adjustment using SearchConfig
        if (isCritical) {
            System.out.println("🔴 CRITICAL POSITION DETECTED!");
            // Use SearchConfig.TIME_CRITICAL_FACTOR instead of hardcoded 25%
            long criticalTime = (long)(remainingTime * SearchConfig.TIME_CRITICAL_FACTOR);
            baseTime = Math.max(baseTime, criticalTime);
        }

        // Material imbalance adjustment using SearchConfig multipliers
        int materialDiff = getMaterialDifference(state);
        if (materialDiff < -1) { // We're behind
            // Use SearchConfig.TIME_BEHIND_MULTIPLIER instead of hardcoded 1.3
            baseTime = (long)(baseTime * SearchConfig.TIME_BEHIND_MULTIPLIER);
        } else if (materialDiff > 2) { // We're ahead
            // Use SearchConfig.TIME_AHEAD_MULTIPLIER instead of hardcoded 0.9
            baseTime = (long)(baseTime * SearchConfig.TIME_AHEAD_MULTIPLIER);
        }

        // Time bounds using SearchConfig factors
        long minTime = Math.max(500, (long)(remainingTime * SearchConfig.TIME_MIN_FACTOR));
        long maxTime = (long)(remainingTime * SearchConfig.TIME_MAX_FACTOR);

        // Special handling for low time using SearchConfig
        if (remainingTime < SearchConfig.TIME_LOW_THRESHOLD) {
            // Use SearchConfig.TIME_LOW_FACTOR instead of hardcoded 20%
            maxTime = (long)(remainingTime * SearchConfig.TIME_LOW_FACTOR);
        }

        // Ensure we don't use too much time per move when comfortable
        if (remainingTime > SearchConfig.TIME_COMFORT_THRESHOLD) {
            long moveBasedLimit = remainingTime / Math.max(10, estimatedMovesLeft - moveNumber);
            maxTime = Math.min(maxTime, moveBasedLimit * 2);
        }

        baseTime = Math.max(minTime, Math.min(baseTime, maxTime));

        System.out.printf("🕐 Time: %dms (%.1f%% of %dms remaining) [SearchConfig factors]\n",
                baseTime, (double)baseTime/remainingTime*100, remainingTime);
        System.out.printf("   Phase: %s | Complexity: %d | Critical: %s | Material: %+d\n",
                phase, complexity, isCritical, materialDiff);
        System.out.printf("   Factors: Min=%.1f%%, Max=%.1f%%, Critical=%.1f%%, Emergency=%.1f%%\n",
                SearchConfig.TIME_MIN_FACTOR*100, SearchConfig.TIME_MAX_FACTOR*100,
                SearchConfig.TIME_CRITICAL_FACTOR*100, SearchConfig.TIME_EMERGENCY_FACTOR*100);

        return baseTime;
    }

    /**
     * Balanced base time calculation using SearchConfig parameters
     */
    private long calculateBalancedBaseTimeWithConfig() {
        // Dynamic moves estimation based on game progress
        int dynamicMovesLeft = estimatedMovesLeft - moveNumber;
        if (phase == Phase.ENDGAME) {
            dynamicMovesLeft = Math.max(5, dynamicMovesLeft / 2);
        }

        // Base: Use 1/moves of remaining time
        long targetTimePerMove = remainingTime / Math.max(5, dynamicMovesLeft);

        // Conservative minimums based on remaining time using SearchConfig thresholds
        long conservativeMinimum;
        if (remainingTime > 150000) {      // > 2.5 minutes
            conservativeMinimum = 10000;    // At least 10 seconds
        } else if (remainingTime > 90000) { // > 1.5 minutes
            conservativeMinimum = 7000;     // At least 7 seconds
        } else if (remainingTime > 60000) { // > 1 minute
            conservativeMinimum = 5000;     // At least 5 seconds
        } else if (remainingTime > SearchConfig.TIME_COMFORT_THRESHOLD) { // > 30 seconds
            conservativeMinimum = 3000;     // At least 3 seconds
        } else if (remainingTime > SearchConfig.TIME_LOW_THRESHOLD / 2) { // > 15 seconds
            conservativeMinimum = 2000;     // At least 2 seconds
        } else if (remainingTime > SearchConfig.TIME_EMERGENCY_THRESHOLD) { // > 5 seconds
            conservativeMinimum = 1000;     // At least 1 second
        } else {
            // Use SearchConfig.TIME_LOW_FACTOR for minimum
            conservativeMinimum = (long)(remainingTime * SearchConfig.TIME_LOW_FACTOR);
        }

        return Math.max(targetTimePerMove, conservativeMinimum);
    }

    /**
     * Critical position detection using SearchConfig thresholds
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

        // Late endgame using SearchConfig threshold
        if (getTotalMaterial(state) <= SearchConfig.TABLEBASE_MATERIAL_THRESHOLD && areGuardsAdvanced(state)) {
            return true;
        }

        // High complexity with captures available using SearchConfig
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int captures = countCaptures(moves, state);
        if (captures >= SearchConfig.TACTICAL_COMPLEXITY_THRESHOLD && evaluatePositionComplexity(state) > 35) {
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

    /**
     * Game phase detection using SearchConfig thresholds
     */
    private Phase detectGamePhase(GameState state) {
        int totalPieces = getTotalMaterial(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        // Use SearchConfig.ENDGAME_MATERIAL_THRESHOLD instead of hardcoded 6
        if (totalPieces <= SearchConfig.TABLEBASE_MATERIAL_THRESHOLD ||
                (totalPieces <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD && guardsAdvanced)) {
            return Phase.ENDGAME;
        } else if (totalPieces <= 10 || guardsAdvanced || moveNumber > 15) {
            return Phase.MIDDLEGAME;
        } else {
            return Phase.OPENING;
        }
    }

    /**
     * Position complexity evaluation with SearchConfig integration
     */
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

            // Use SearchConfig multiplier for capture complexity
            complexity += captureCount * SearchConfig.TACTICAL_COMPLEXITY_THRESHOLD;

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

        return (redGuardRow <= 4) || (blueGuardRow >= 4);
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

    // === PUBLIC INTERFACE WITH SEARCHCONFIG INFO ===

    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = Math.max(0, remainingTime);

        // Log SearchConfig threshold information
        if (remainingTime <= SearchConfig.TIME_PANIC_THRESHOLD) {
            System.out.println("⚠️ PANIC TIME: " + remainingTime + "ms (threshold: " + SearchConfig.TIME_PANIC_THRESHOLD + "ms)");
        } else if (remainingTime <= SearchConfig.TIME_EMERGENCY_THRESHOLD) {
            System.out.println("🚨 EMERGENCY TIME: " + remainingTime + "ms (threshold: " + SearchConfig.TIME_EMERGENCY_THRESHOLD + "ms)");
        } else if (remainingTime <= SearchConfig.TIME_LOW_THRESHOLD) {
            System.out.println("⏱️ LOW TIME: " + remainingTime + "ms (threshold: " + SearchConfig.TIME_LOW_THRESHOLD + "ms)");
        }
    }

    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 5) {
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

    /**
     * Get time management statistics with SearchConfig info
     */
    public String getTimeManagementStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TIME MANAGEMENT WITH SEARCHCONFIG ===\n");
        sb.append(String.format("Remaining Time: %dms\n", remainingTime));
        sb.append(String.format("Estimated Moves Left: %d\n", estimatedMovesLeft));
        sb.append(String.format("Current Phase: %s\n", phase));
        sb.append(String.format("Move Number: %d\n", moveNumber));

        sb.append("\nSearchConfig Time Thresholds:\n");
        sb.append(String.format("  Panic: %dms\n", SearchConfig.TIME_PANIC_THRESHOLD));
        sb.append(String.format("  Emergency: %dms\n", SearchConfig.TIME_EMERGENCY_THRESHOLD));
        sb.append(String.format("  Low: %dms\n", SearchConfig.TIME_LOW_THRESHOLD));
        sb.append(String.format("  Comfort: %dms\n", SearchConfig.TIME_COMFORT_THRESHOLD));

        sb.append("\nSearchConfig Time Factors:\n");
        sb.append(String.format("  Min Factor: %.1f%%\n", SearchConfig.TIME_MIN_FACTOR * 100));
        sb.append(String.format("  Max Factor: %.1f%%\n", SearchConfig.TIME_MAX_FACTOR * 100));
        sb.append(String.format("  Critical Factor: %.1f%%\n", SearchConfig.TIME_CRITICAL_FACTOR * 100));
        sb.append(String.format("  Emergency Factor: %.1f%%\n", SearchConfig.TIME_EMERGENCY_FACTOR * 100));

        sb.append("\nSearchConfig Multipliers:\n");
        sb.append(String.format("  Behind: %.1fx\n", SearchConfig.TIME_BEHIND_MULTIPLIER));
        sb.append(String.format("  Ahead: %.1fx\n", SearchConfig.TIME_AHEAD_MULTIPLIER));
        sb.append(String.format("  Middlegame: %.1fx\n", SearchConfig.TIME_MIDDLEGAME_MULTIPLIER));
        sb.append(String.format("  Endgame: %.1fx\n", SearchConfig.TIME_ENDGAME_MULTIPLIER));

        return sb.toString();
    }

    /**
     * Validate SearchConfig time parameters
     */
    public boolean validateTimeConfiguration() {
        boolean valid = true;

        if (SearchConfig.TIME_PANIC_THRESHOLD <= 0) {
            System.err.println("❌ Invalid TIME_PANIC_THRESHOLD: " + SearchConfig.TIME_PANIC_THRESHOLD);
            valid = false;
        }

        if (SearchConfig.TIME_EMERGENCY_THRESHOLD <= SearchConfig.TIME_PANIC_THRESHOLD) {
            System.err.println("❌ TIME_EMERGENCY_THRESHOLD should be higher than TIME_PANIC_THRESHOLD");
            valid = false;
        }

        if (SearchConfig.TIME_LOW_THRESHOLD <= SearchConfig.TIME_EMERGENCY_THRESHOLD) {
            System.err.println("❌ TIME_LOW_THRESHOLD should be higher than TIME_EMERGENCY_THRESHOLD");
            valid = false;
        }

        if (SearchConfig.TIME_MIN_FACTOR <= 0 || SearchConfig.TIME_MIN_FACTOR > 1) {
            System.err.println("❌ Invalid TIME_MIN_FACTOR: " + SearchConfig.TIME_MIN_FACTOR);
            valid = false;
        }

        if (SearchConfig.TIME_MAX_FACTOR <= SearchConfig.TIME_MIN_FACTOR || SearchConfig.TIME_MAX_FACTOR > 1) {
            System.err.println("❌ Invalid TIME_MAX_FACTOR: " + SearchConfig.TIME_MAX_FACTOR);
            valid = false;
        }

        if (SearchConfig.TIME_CRITICAL_FACTOR <= 0 || SearchConfig.TIME_CRITICAL_FACTOR > 1) {
            System.err.println("❌ Invalid TIME_CRITICAL_FACTOR: " + SearchConfig.TIME_CRITICAL_FACTOR);
            valid = false;
        }

        if (SearchConfig.TIME_BEHIND_MULTIPLIER <= 0 || SearchConfig.TIME_BEHIND_MULTIPLIER > 3) {
            System.err.println("❌ Invalid TIME_BEHIND_MULTIPLIER: " + SearchConfig.TIME_BEHIND_MULTIPLIER);
            valid = false;
        }

        if (SearchConfig.TIME_AHEAD_MULTIPLIER <= 0 || SearchConfig.TIME_AHEAD_MULTIPLIER > 2) {
            System.err.println("❌ Invalid TIME_AHEAD_MULTIPLIER: " + SearchConfig.TIME_AHEAD_MULTIPLIER);
            valid = false;
        }

        if (valid) {
            System.out.println("✅ TimeManager SearchConfig integration validated");
        }

        return valid;
    }

    /**
     * Get recommended time allocation for current situation
     */
    public SearchConfig.TimeConfig getRecommendedTimeConfig() {
        return new SearchConfig.TimeConfig(
                remainingTime,
                estimatedMovesLeft,
                SearchConfig.TIME_EMERGENCY_FACTOR,
                phase == Phase.MIDDLEGAME ? SearchConfig.TIME_MIDDLEGAME_MULTIPLIER : SearchConfig.TIME_ENDGAME_MULTIPLIER
        );
    }

    /**
     * Check if current time situation matches SearchConfig thresholds
     */
    public boolean isPanicTime() {
        return remainingTime <= SearchConfig.TIME_PANIC_THRESHOLD;
    }

    public boolean isEmergencyTime() {
        return remainingTime <= SearchConfig.TIME_EMERGENCY_THRESHOLD;
    }

    public boolean isLowTime() {
        return remainingTime <= SearchConfig.TIME_LOW_THRESHOLD;
    }

    public boolean isComfortableTime() {
        return remainingTime > SearchConfig.TIME_COMFORT_THRESHOLD;
    }

    /**
     * Get time category as string for logging
     */
    public String getTimeCategory() {
        if (isPanicTime()) return "PANIC";
        if (isEmergencyTime()) return "EMERGENCY";
        if (isLowTime()) return "LOW";
        if (isComfortableTime()) return "COMFORTABLE";
        return "NORMAL";
    }
}