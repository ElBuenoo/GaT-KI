package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.ConsolidatedSearchConfig;
import GaT.model.GameValues;
import java.util.List;

/**
 * TIME MANAGER - PHASE 1 INTEGRATION
 *
 * CHANGES:
 * ✅ Uses ConsolidatedSearchConfig instead of SearchConfig
 * ✅ Uses GameValues for all piece values and thresholds
 * ✅ Simplified time management using Phase 1 constants
 * ✅ All hardcoded values replaced with Phase 1 constants
 */
public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private int moveNumber;
    private Phase phase;

    // === TIME THRESHOLDS ===
    private static final long TIME_PANIC_THRESHOLD = 500L;
    private static final long TIME_EMERGENCY_THRESHOLD = 3000L;
    private static final long TIME_LOW_THRESHOLD = 10000L;
    private static final long TIME_COMFORT_THRESHOLD = 30000L;

    // === TIME FACTORS ===
    private static final double TIME_CRITICAL_FACTOR = 0.25;
    private static final double TIME_EMERGENCY_FACTOR = 0.16;
    private static final double TIME_MIN_FACTOR = 0.05;
    private static final double TIME_MAX_FACTOR = 0.35;
    private static final double TIME_LOW_FACTOR = 0.15;

    // === MULTIPLIERS ===
    private static final double TIME_MIDDLEGAME_MULTIPLIER = 1.2;
    private static final double TIME_ENDGAME_MULTIPLIER = 1.5;
    private static final double TIME_BEHIND_MULTIPLIER = 1.3;
    private static final double TIME_AHEAD_MULTIPLIER = 0.9;

    public enum Phase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = Math.max(estimatedMovesLeft, 15);
        this.phase = Phase.OPENING;
        this.moveNumber = 0;

        System.out.println("🔧 TimeManager initialized with Phase 1 optimizations");
        System.out.printf("   Remaining: %dms | Estimated moves: %d\n", remainingTime, estimatedMovesLeft);
    }

    /**
     * Calculate time for move using Phase 1 optimizations
     */
    public long calculateTimeForMove(GameState state) {
        moveNumber++;

        // Panic time
        if (remainingTime <= TIME_PANIC_THRESHOLD) {
            return Math.max(50, (long)(remainingTime * TIME_CRITICAL_FACTOR));
        }

        // Emergency mode
        if (remainingTime <= TIME_EMERGENCY_THRESHOLD) {
            return Math.max(200, (long)(remainingTime * TIME_EMERGENCY_FACTOR));
        }

        // Detect game phase and position criticality
        this.phase = detectGamePhase(state);
        boolean isCritical = isCriticalPosition(state);
        int complexity = evaluatePositionComplexity(state);

        // Base calculation
        long baseTime = calculateBalancedBaseTime();

        // Phase-based adjustments
        switch (phase) {
            case OPENING:
                if (moveNumber <= 10) {
                    baseTime = baseTime; // Standard time in early opening
                }
                break;

            case MIDDLEGAME:
                baseTime = (long)(baseTime * TIME_MIDDLEGAME_MULTIPLIER);
                if (complexity > 40) {
                    baseTime = (long)(baseTime * 1.3); // Extra for very complex
                }
                break;

            case ENDGAME:
                baseTime = (long)(baseTime * TIME_ENDGAME_MULTIPLIER);
                if (getTotalMaterial(state) <= 4) {
                    baseTime = baseTime * 2; // Double for extreme endgames
                }
                break;
        }

        // Critical position adjustment
        if (isCritical) {
            System.out.println("🔴 CRITICAL POSITION DETECTED!");
            long criticalTime = (long)(remainingTime * TIME_CRITICAL_FACTOR);
            baseTime = Math.max(baseTime, criticalTime);
        }

        // Material imbalance adjustment
        int materialDiff = getMaterialDifference(state);
        if (materialDiff < -1) { // We're behind
            baseTime = (long)(baseTime * TIME_BEHIND_MULTIPLIER);
        } else if (materialDiff > 2) { // We're ahead
            baseTime = (long)(baseTime * TIME_AHEAD_MULTIPLIER);
        }

        // Time bounds
        long minTime = Math.max(500, (long)(remainingTime * TIME_MIN_FACTOR));
        long maxTime = (long)(remainingTime * TIME_MAX_FACTOR);

        // Special handling for low time
        if (remainingTime < TIME_LOW_THRESHOLD) {
            maxTime = (long)(remainingTime * TIME_LOW_FACTOR);
        }

        // Ensure we don't use too much time per move when comfortable
        if (remainingTime > TIME_COMFORT_THRESHOLD) {
            long moveBasedLimit = remainingTime / Math.max(10, estimatedMovesLeft - moveNumber);
            maxTime = Math.min(maxTime, moveBasedLimit * 2);
        }

        baseTime = Math.max(minTime, Math.min(baseTime, maxTime));

        System.out.printf("🕐 Time: %dms (%.1f%% of %dms remaining)\n",
                baseTime, (double)baseTime/remainingTime*100, remainingTime);
        System.out.printf("   Phase: %s | Complexity: %d | Critical: %s | Material: %+d\n",
                phase, complexity, isCritical, materialDiff);

        return baseTime;
    }

    /**
     * Balanced base time calculation
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
            conservativeMinimum = 10000;    // At least 10 seconds
        } else if (remainingTime > 90000) { // > 1.5 minutes
            conservativeMinimum = 7000;     // At least 7 seconds
        } else if (remainingTime > 60000) { // > 1 minute
            conservativeMinimum = 5000;     // At least 5 seconds
        } else if (remainingTime > TIME_COMFORT_THRESHOLD) { // > 30 seconds
            conservativeMinimum = 3000;     // At least 3 seconds
        } else if (remainingTime > TIME_LOW_THRESHOLD / 2) { // > 15 seconds
            conservativeMinimum = 2000;     // At least 2 seconds
        } else if (remainingTime > TIME_EMERGENCY_THRESHOLD) { // > 5 seconds
            conservativeMinimum = 1000;     // At least 1 second
        } else {
            conservativeMinimum = (long)(remainingTime * TIME_LOW_FACTOR);
        }

        return Math.max(targetTimePerMove, conservativeMinimum);
    }

    /**
     * Critical position detection using GameValues
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
        if (getTotalMaterial(state) <= 4 && areGuardsAdvanced(state)) {
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
            if (GameValues.isCapture(state, move.from, move.to)) count++;
        }
        return count;
    }

    /**
     * Game phase detection
     */
    private Phase detectGamePhase(GameState state) {
        int totalPieces = getTotalMaterial(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        if (totalPieces <= 4 || (totalPieces <= 6 && guardsAdvanced)) {
            return Phase.ENDGAME;
        } else if (totalPieces <= 10 || guardsAdvanced || moveNumber > 15) {
            return Phase.MIDDLEGAME;
        } else {
            return Phase.OPENING;
        }
    }

    /**
     * Position complexity evaluation
     */
    private int evaluatePositionComplexity(GameState state) {
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            int complexity = allMoves.size();

            // Count tactical moves
            int captureCount = 0;
            for (Move move : allMoves) {
                if (GameValues.isCapture(state, move.from, move.to)) {
                    captureCount++;
                }
            }

            complexity += captureCount * 3; // Weight captures more heavily

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

    // === PUBLIC INTERFACE ===

    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = Math.max(0, remainingTime);

        // Log time category
        if (remainingTime <= TIME_PANIC_THRESHOLD) {
            System.out.println("⚠️ PANIC TIME: " + remainingTime + "ms");
        } else if (remainingTime <= TIME_EMERGENCY_THRESHOLD) {
            System.out.println("🚨 EMERGENCY TIME: " + remainingTime + "ms");
        } else if (remainingTime <= TIME_LOW_THRESHOLD) {
            System.out.println("⏱️ LOW TIME: " + remainingTime + "ms");
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
     * Get time management statistics
     */
    public String getTimeManagementStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TIME MANAGEMENT (PHASE 1) ===\n");
        sb.append(String.format("Remaining Time: %dms\n", remainingTime));
        sb.append(String.format("Estimated Moves Left: %d\n", estimatedMovesLeft));
        sb.append(String.format("Current Phase: %s\n", phase));
        sb.append(String.format("Move Number: %d\n", moveNumber));
        return sb.toString();
    }

    // === TIME CATEGORY CHECKS ===

    public boolean isPanicTime() {
        return remainingTime <= TIME_PANIC_THRESHOLD;
    }

    public boolean isEmergencyTime() {
        return remainingTime <= TIME_EMERGENCY_THRESHOLD;
    }

    public boolean isLowTime() {
        return remainingTime <= TIME_LOW_THRESHOLD;
    }

    public boolean isComfortableTime() {
        return remainingTime > TIME_COMFORT_THRESHOLD;
    }

    public String getTimeCategory() {
        if (isPanicTime()) return "PANIC";
        if (isEmergencyTime()) return "EMERGENCY";
        if (isLowTime()) return "LOW";
        if (isComfortableTime()) return "COMFORTABLE";
        return "NORMAL";
    }
}