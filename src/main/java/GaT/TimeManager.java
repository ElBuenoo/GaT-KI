package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;

import java.util.Arrays;
import java.util.List;

/**
 * TOURNAMENT-LEVEL TimeManager with aggressive time allocation
 *
 * Tournament Features:
 * - Aggressive emergency thresholds for maximum performance
 * - Enhanced complexity evaluation with tactical awareness
 * - Critical position detection with 4x time multiplier
 * - Winning advantage detection with 3x time multiplier
 * - Tournament decision point analysis
 * - Adaptive phase detection with endgame patterns
 * - Safety margins to prevent time forfeit
 */
public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // === TOURNAMENT-OPTIMIZED CONSTANTS ===
    private static final long TOURNAMENT_PANIC_THRESHOLD = 2000;     // Von 3000 → 2000 (aggressiver)
    private static final long TOURNAMENT_EMERGENCY_THRESHOLD = 8000; // Von 10000 → 8000 (aggressiver)
    private static final long CRITICAL_POSITION_MULTIPLIER = 4;      // Von 3 → 4 (mehr Zeit für kritische Positionen)
    private static final long WINNING_ADVANTAGE_MULTIPLIER = 3;      // Von 2 → 3 (mehr Zeit für Gewinnkonvertierung)
    private static final long ENDGAME_MULTIPLIER = 3;               // Von 2 → 3 (mehr Zeit für Endspiel)
    private static final double TOURNAMENT_SAFETY_MARGIN = 0.85;    // 15% Sicherheitspuffer

    enum Phase {
        START,
        MID,
        END,
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = estimatedMovesLeft;
        this.phase = Phase.START;
    }

    /**
     * TOURNAMENT-ENHANCED time calculation with aggressive optimization
     */
    public long calculateTimeForMove(GameState state) {
        // === TOURNAMENT EMERGENCY TIME MANAGEMENT ===
        if (remainingTime <= TOURNAMENT_PANIC_THRESHOLD) {
            long panicTime = Math.max(150, remainingTime / 12); // Mehr Zeit (war /10)
            System.out.println("🚨 TOURNAMENT PANIC: Only " + panicTime + "ms!");
            return panicTime;
        }

        if (remainingTime <= TOURNAMENT_EMERGENCY_THRESHOLD) {
            long emergencyTime = Math.max(400, remainingTime / 6); // Mehr Zeit (war /8)
            System.out.println("⚠️ TOURNAMENT EMERGENCY: " + emergencyTime + "ms");
            return emergencyTime;
        }

        // === TOURNAMENT TIME CALCULATION ===
        long baseTime = calculateTimePerMoveTournament();

        // Enhanced phase detection and adjustment
        this.phase = detectGamePhaseTournament(state);
        baseTime = adjustForPhaseTournament(baseTime, phase);

        // ENHANCED complexity adjustment
        int complexity = evaluatePositionComplexityTournament(state);
        baseTime = adjustForComplexityTournament(baseTime, complexity);

        // ENHANCED situation-specific adjustments
        baseTime = adjustForSituationTournament(baseTime, state);

        // === TOURNAMENT SAFE TIME LIMITS ===
        long maxTimeForMove = calculateMaxTimeForMove();
        long minTimeForMove = calculateMinTimeForMove();

        baseTime = Math.max(minTimeForMove, Math.min(baseTime, maxTimeForMove));

        // Apply tournament safety margin
        baseTime = (long) (baseTime * TOURNAMENT_SAFETY_MARGIN);

        System.out.println("⏱️ TOURNAMENT Allocated: " + baseTime + "ms (Phase: " + phase +
                ", Complexity: " + complexity + ", Remaining: " + remainingTime + "ms)");
        return baseTime;
    }

    /**
     * TOURNAMENT-OPTIMIZED base time calculation
     */
    private long calculateTimePerMoveTournament() {
        // More aggressive base time calculation
        int effectiveMovesLeft = Math.max(estimatedMovesLeft, 3); // Minimum 3 moves

        // Tournament-optimized distribution
        long baseTimePerMove = remainingTime / effectiveMovesLeft;

        // Tournament bonus: Use more time early for better positioning
        if (estimatedMovesLeft > 20) {
            baseTimePerMove = (long) (baseTimePerMove * 1.3); // 30% more in opening
        } else if (estimatedMovesLeft > 10) {
            baseTimePerMove = (long) (baseTimePerMove * 1.1); // 10% more in middlegame
        }

        return baseTimePerMove;
    }

    /**
     * ENHANCED tournament-level phase detection
     */
    private Phase detectGamePhaseTournament(GameState state) {
        int totalPieces = getMaterialCount(state);
        boolean guardsAdvanced = areGuardsAdvancedTournament(state);
        boolean tacticalPosition = isTacticalPosition(state);

        // Enhanced phase detection logic
        if (totalPieces > 10 && !guardsAdvanced && !tacticalPosition) {
            return Phase.START; // Opening: Many pieces, safe guards, quiet position
        } else if (totalPieces <= 5 || guardsAdvanced || isEndgamePattern(state)) {
            return Phase.END; // Endgame: Few pieces OR advanced guards OR endgame patterns
        } else {
            return Phase.MID; // Middlegame: Everything else
        }
    }

    /**
     * ENHANCED guard advancement detection
     */
    private boolean areGuardsAdvancedTournament(GameState state) {
        // More nuanced guard advancement detection
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redRank = GameState.rank(redGuardPos);
            if (redRank <= 3) return true; // Red guard past center
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueRank = GameState.rank(blueGuardPos);
            if (blueRank >= 3) return true; // Blue guard past center
        }

        return false;
    }

    /**
     * NEW: Tactical position detection
     */
    private boolean isTacticalPosition(GameState state) {
        // Check if position has immediate tactical features
        try {
            List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);
            return tacticalMoves.size() > 3; // More than 3 tactical moves = tactical position
        } catch (Exception e) {
            // Fallback if QuiescenceSearch not available
            return Minimax.isGuardInDangerImproved(state, true) ||
                    Minimax.isGuardInDangerImproved(state, false);
        }
    }

    /**
     * NEW: Endgame pattern detection
     */
    private boolean isEndgamePattern(GameState state) {
        // Detect specific endgame patterns

        // Both sides have only guard + few pieces
        int redPieces = 0, bluePieces = 0;
        for (int i = 0; i < 49; i++) {
            redPieces += state.redStackHeights[i];
            bluePieces += state.blueStackHeights[i];
        }

        if (redPieces <= 2 && bluePieces <= 2) return true;

        // One side significantly ahead in material
        int materialDiff = Math.abs(redPieces - bluePieces);
        if (materialDiff >= 4) return true;

        return false;
    }

    /**
     * ENHANCED phase-based time adjustment
     */
    private long adjustForPhaseTournament(long baseTime, Phase phase) {
        switch (phase) {
            case START:
                return (long) (baseTime * 0.8); // 20% less time in opening (was 33% less)
            case MID:
                return baseTime; // Normal time in middlegame
            case END:
                return (long) (baseTime * ENDGAME_MULTIPLIER); // 3x more time in endgame
            default:
                return baseTime;
        }
    }

    /**
     * TOURNAMENT-LEVEL complexity evaluation
     */
    private int evaluatePositionComplexityTournament(GameState state) {
        int complexity = 0;

        // Base complexity from move count
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        complexity += allMoves.size() / 3; // Normalize

        // ENHANCED: Tactical complexity (most important factor)
        try {
            List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);
            complexity += tacticalMoves.size() * 4; // Heavy weight for tactical moves

            // Check for specific tactical patterns
            for (Move move : tacticalMoves) {
                if (Minimax.isCapture(move, state)) {
                    complexity += 2; // Extra for captures
                }
            }
        } catch (Exception e) {
            // Fallback if QuiescenceSearch not available
            complexity += 5;
        }

        // ENHANCED: Guard danger analysis
        boolean redInDanger = Minimax.isGuardInDangerImproved(state, true);
        boolean blueInDanger = Minimax.isGuardInDangerImproved(state, false);

        if (redInDanger || blueInDanger) {
            complexity += 15; // High complexity for guard danger
            if (redInDanger && blueInDanger) {
                complexity += 10; // Extra for mutual guard danger
            }
        }

        // ENHANCED: Position evaluation spread (indicator of critical positions)
        int eval = Minimax.evaluate(state, 0);
        int absEval = Math.abs(eval);

        if (absEval > 800) {
            complexity += 12; // Very unbalanced positions need careful play
        } else if (absEval > 400) {
            complexity += 6; // Moderately unbalanced positions
        }

        // ENHANCED: Material imbalance complexity
        int materialImbalance = Math.abs(getMaterialBalance(state));
        if (materialImbalance > 3 && materialImbalance < 8) {
            complexity += 8; // Unclear material situations are complex
        }

        // ENHANCED: Guard advancement under pressure
        if (areGuardsAdvancedTournament(state)) {
            complexity += 10; // Advanced guards create complex situations
        }

        return Math.min(complexity, 50); // Cap complexity at reasonable level
    }

    /**
     * ENHANCED complexity-based time adjustment
     */
    private long adjustForComplexityTournament(long baseTime, int complexity) {
        if (complexity > 35) {
            return (long) (baseTime * 2.5); // Very complex: 2.5x time
        } else if (complexity > 25) {
            return (long) (baseTime * 2.0); // Complex: 2x time
        } else if (complexity > 15) {
            return (long) (baseTime * 1.5); // Moderate: 1.5x time
        } else if (complexity < 5) {
            return (long) (baseTime * 0.7); // Simple: 30% less time
        } else {
            return baseTime; // Normal complexity
        }
    }

    /**
     * TOURNAMENT-LEVEL situation-specific adjustments
     */
    private long adjustForSituationTournament(long baseTime, GameState state) {
        long adjustedTime = baseTime;

        // CRITICAL POSITION detection (most important)
        if (isCriticalPositionTournament(state)) {
            adjustedTime = (long) (adjustedTime * CRITICAL_POSITION_MULTIPLIER);
            System.out.println("🔥 CRITICAL POSITION detected - using " + CRITICAL_POSITION_MULTIPLIER + "x time");
        }

        // WINNING ADVANTAGE detection
        if (hasWinningAdvantageTournament(state)) {
            adjustedTime = (long) (adjustedTime * WINNING_ADVANTAGE_MULTIPLIER);
            System.out.println("🎯 WINNING ADVANTAGE - using " + WINNING_ADVANTAGE_MULTIPLIER + "x time to convert");
        }

        // TOURNAMENT DECISION POINT detection
        if (isTournamentDecisionPoint(state)) {
            adjustedTime = (long) (adjustedTime * 1.8);
            System.out.println("⚖️ TOURNAMENT DECISION POINT - using 1.8x time");
        }

        return adjustedTime;
    }

    /**
     * ENHANCED critical position detection
     */
    private boolean isCriticalPositionTournament(GameState state) {
        // Enhanced critical position detection

        // 1. Guard in immediate danger
        if (Minimax.isGuardInDangerImproved(state, true) ||
                Minimax.isGuardInDangerImproved(state, false)) {
            return true;
        }

        // 2. Multiple tactical threats available
        try {
            List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);
            if (tacticalMoves.size() > 5) return true;

            // Check for high-value tactical moves
            for (Move move : tacticalMoves) {
                if (Minimax.scoreMove(state, move) > 1000) {
                    return true; // High-value tactical move available
                }
            }
        } catch (Exception e) {
            // Fallback
        }

        // 3. Position evaluation is extreme
        int eval = Minimax.evaluate(state, 0);
        if (Math.abs(eval) > 1000) return true;

        // 4. Endgame with close material
        if (phase == Phase.END) {
            int materialBalance = Math.abs(getMaterialBalance(state));
            if (materialBalance <= 2) return true; // Close endgame
        }

        return false;
    }

    /**
     * ENHANCED winning advantage detection
     */
    private boolean hasWinningAdvantageTournament(GameState state) {
        int eval = Minimax.evaluate(state, 0);

        // Position evaluation indicates significant advantage
        if ((state.redToMove && eval > 600) || (!state.redToMove && eval < -600)) {
            return true;
        }

        // Material advantage in endgame
        if (phase == Phase.END) {
            int materialBalance = getMaterialBalance(state);
            if ((state.redToMove && materialBalance > 2) ||
                    (!state.redToMove && materialBalance < -2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * NEW: Tournament decision point detection
     */
    private boolean isTournamentDecisionPoint(GameState state) {
        // Detect positions where decision significantly impacts game outcome

        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        if (allMoves.size() < 5) return true; // Few options = important decision

        // Check if we have moves with very different evaluations
        // (indicates position where choice matters a lot)
        return allMoves.size() > 15; // Many options = complex decision
    }

    /**
     * TOURNAMENT-OPTIMIZED maximum time calculation
     */
    private long calculateMaxTimeForMove() {
        // Tournament-optimized maximum time
        int safeMovesLeft = Math.max(estimatedMovesLeft, 4);
        long maxTime = remainingTime / safeMovesLeft;

        // Tournament enhancement: Use more time if we have plenty
        if (remainingTime > 60000) { // More than 1 minute
            maxTime = (long) (maxTime * 1.5);
        }

        return Math.min(maxTime, remainingTime / 3); // Never more than 1/3 of remaining time
    }

    /**
     * TOURNAMENT-OPTIMIZED minimum time calculation
     */
    private long calculateMinTimeForMove() {
        // Tournament-optimized minimum time
        long minTime = Math.max(200, remainingTime / 80); // At least 200ms

        // Tournament enhancement: Higher minimum in complex positions
        if (phase == Phase.END) {
            minTime = Math.max(minTime, 500); // At least 500ms in endgame
        }

        return minTime;
    }

    /**
     * ENHANCED material analysis
     */
    private int getMaterialCount(GameState state) {
        return Arrays.stream(state.redStackHeights).sum() + Arrays.stream(state.blueStackHeights).sum();
    }

    /**
     * NEW: Material balance calculation
     */
    private int getMaterialBalance(GameState state) {
        int redMaterial = Arrays.stream(state.redStackHeights).sum();
        int blueMaterial = Arrays.stream(state.blueStackHeights).sum();
        return redMaterial - blueMaterial;
    }

    /**
     * TOURNAMENT-ENHANCED time update with adaptive adjustments
     */
    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;

        // Tournament enhancement: Adjust estimated moves based on time pressure
        if (remainingTime < 30000) { // Less than 30 seconds
            // Reduce estimated moves to encourage faster play
            this.estimatedMovesLeft = Math.max(this.estimatedMovesLeft - 2, 3);
            System.out.println("⏰ TOURNAMENT time pressure: reducing estimated moves to " + this.estimatedMovesLeft);
        }
    }

    /**
     * Decrement estimated moves left
     */
    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 1) {
            this.estimatedMovesLeft--;
        }
    }

    /**
     * NEW: Tournament time statistics
     */
    public void printTournamentTimeStats() {
        System.out.println("📊 TOURNAMENT Time Management Stats:");
        System.out.println("  Remaining time: " + formatTime(remainingTime));
        System.out.println("  Estimated moves left: " + estimatedMovesLeft);
        System.out.println("  Current phase: " + phase);
        System.out.println("  Time per move (base): " + calculateTimePerMoveTournament() + "ms");
    }

    /**
     * Helper method to format time
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%.1fs", milliseconds / 1000.0);
        }
    }

    // === GETTERS FOR DEBUGGING ===

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