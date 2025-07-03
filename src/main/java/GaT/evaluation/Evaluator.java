package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * ENHANCED EVALUATOR - Now properly integrates all evaluation components
 *
 * This version uses MaterialEval, PositionalEval, SafetyEval, and TacticalEvaluator
 * instead of reimplementing everything from scratch.
 */
public class Evaluator {

    // === COMPONENT EVALUATORS ===
    private final MaterialEval materialEval;
    private final PositionalEval positionalEval;
    private final SafetyEval safetyEval;

    // === TERMINAL POSITION SCORES ===
    private static final int CHECKMATE_SCORE = 50000;
    private static final int CASTLE_REACH_SCORE = 40000;

    // === TIME MANAGEMENT ===
    private static volatile long remainingTimeMs = 180000;
    private static volatile boolean emergencyMode = false;

    // === EVALUATION WEIGHTS ===
    private static class Weights {
        // Base component weights
        static final double MATERIAL = 1.0;
        static final double POSITIONAL = 0.6;
        static final double SAFETY = 0.8;
        static final double TACTICAL = 0.4;

        // Game phase adjustments
        static final double[] OPENING = {1.0, 0.7, 0.5, 0.3};
        static final double[] MIDDLEGAME = {1.0, 0.8, 1.0, 0.6};
        static final double[] ENDGAME = {0.8, 1.2, 0.9, 0.5};
    }

    /**
     * Constructor - Initialize all evaluation components
     */
    public Evaluator() {
        this.materialEval = new MaterialEval();
        this.positionalEval = new PositionalEval();
        this.safetyEval = new SafetyEval();
    }

    /**
     * MAIN EVALUATION METHOD - Now uses all components intelligently
     */
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // === TERMINAL POSITION CHECK ===
        int terminalScore = checkTerminalPosition(state, depth);
        if (Math.abs(terminalScore) >= CASTLE_REACH_SCORE) {
            return terminalScore;
        }

        // === DETECT GAME PHASE ===
        MaterialEval.GamePhase phase = detectGamePhase(state);

        // === TIME-ADAPTIVE EVALUATION STRATEGY ===
        emergencyMode = remainingTimeMs < 1000;

        if (emergencyMode) {
            return evaluateEmergency(state);
        } else if (remainingTimeMs < 5000) {
            return evaluateFast(state, phase);
        } else if (remainingTimeMs < 30000) {
            return evaluateStandard(state, phase, depth);
        } else {
            return evaluateComprehensive(state, phase, depth);
        }
    }

    // === TERMINAL POSITION EVALUATION ===
    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -CHECKMATE_SCORE - depth;
        if (state.blueGuard == 0) return CHECKMATE_SCORE + depth;

        // Guard reached enemy castle
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (redGuardPos == GameState.getIndex(0, 3)) {
                return CASTLE_REACH_SCORE + depth;
            }
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (blueGuardPos == GameState.getIndex(6, 3)) {
                return -CASTLE_REACH_SCORE - depth;
            }
        }

        return 0;
    }

    // === EMERGENCY EVALUATION (< 1 second) ===
    private int evaluateEmergency(GameState state) {
        // Just material count
        return materialEval.evaluateMaterialSimple(state);
    }

    // === FAST EVALUATION (< 5 seconds) ===
    private int evaluateFast(GameState state, MaterialEval.GamePhase phase) {
        int eval = 0;

        // Basic material with simple positional bonuses
        eval += materialEval.evaluateMaterialBasic(state);

        // Fast guard advancement check
        eval += positionalEval.evaluateGuardAdvancementFast(state) / 2;

        // Quick safety check
        if (safetyEval.isGuardInDanger(state, true)) eval -= 300;
        if (safetyEval.isGuardInDanger(state, false)) eval += 300;

        return eval;
    }

    // === STANDARD EVALUATION (< 30 seconds) ===
    private int evaluateStandard(GameState state, MaterialEval.GamePhase phase, int depth) {
        double[] weights = getPhaseWeights(phase);
        int eval = 0;

        // Material evaluation with activity
        int material = materialEval.evaluateMaterialWithActivity(state);
        eval += (int)(material * weights[0]);

        // Positional evaluation
        int positional = positionalEval.evaluatePositional(state);
        eval += (int)(positional * weights[1]);

        // Safety evaluation
        int safety = safetyEval.evaluateGuardSafety(state);
        eval += (int)(safety * weights[2]);

        // Basic tactical threats
        int tactical = evaluateBasicTactics(state);
        eval += (int)(tactical * weights[3]);

        return eval;
    }

    // === COMPREHENSIVE EVALUATION (plenty of time) ===
    private int evaluateComprehensive(GameState state, MaterialEval.GamePhase phase, int depth) {
        double[] weights = getPhaseWeights(phase);
        int eval = 0;

        // Advanced material evaluation
        int material = materialEval.evaluateMaterialAdvanced(state);
        eval += (int)(material * weights[0]);

        // Advanced positional evaluation
        int positional = positionalEval.evaluatePositionalAdvanced(state);
        eval += (int)(positional * weights[1]);

        // Advanced safety evaluation
        int safety = safetyEval.evaluateGuardSafetyAdvanced(state);
        eval += (int)(safety * weights[2]);

        // Advanced tactical evaluation (using TacticalEvaluator)
        TacticalEvaluator tacticalEval = new TacticalEvaluator();
        int tactical = tacticalEval.evaluate(state, depth);
        eval += (int)(tactical * weights[3] * 0.3); // Reduced to prevent wild swings

        // Piece activity bonus
        eval += materialEval.evaluatePieceActivity(state) / 3;

        // Endgame-specific evaluation
        if (phase == MaterialEval.GamePhase.ENDGAME) {
            eval += evaluateEndgameSpecific(state);
        }

        return eval;
    }

    // === HELPER EVALUATION METHODS ===

    /**
     * Basic tactical evaluation (faster than TacticalEvaluator)
     */
    private int evaluateBasicTactics(GameState state) {
        int tacticalScore = 0;

        // Count immediate threats
        int threats = safetyEval.countThreats(state);
        tacticalScore += threats * 50;

        // Check for guard threats
        if (safetyEval.isGuardInDanger(state, !state.redToMove)) {
            tacticalScore += state.redToMove ? 200 : -200;
        }

        return tacticalScore;
    }

    /**
     * Endgame-specific evaluation factors
     */
    private int evaluateEndgameSpecific(GameState state) {
        int endgameScore = 0;

        // Guard advancement is critical in endgame
        endgameScore += positionalEval.evaluateGuardAdvancement(state) * 2;

        // King activity (guard mobility in endgame)
        endgameScore += positionalEval.evaluateKingActivity(state);

        // Opposition and zugzwang potential
        endgameScore += evaluateOpposition(state);

        return endgameScore;
    }

    /**
     * Evaluate opposition in endgame
     */
    private int evaluateOpposition(GameState state) {
        if (state.redGuard == 0 || state.blueGuard == 0) return 0;

        int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
        int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);

        int rankDiff = Math.abs(GameState.rank(redGuardPos) - GameState.rank(blueGuardPos));
        int fileDiff = Math.abs(GameState.file(redGuardPos) - GameState.file(blueGuardPos));

        // Direct opposition bonus
        if (rankDiff == 2 && fileDiff == 0 || rankDiff == 0 && fileDiff == 2) {
            return state.redToMove ? -50 : 50; // Opponent has opposition
        }

        return 0;
    }

    /**
     * Detect game phase using material count
     */
    private MaterialEval.GamePhase detectGamePhase(GameState state) {
        int totalMaterial = materialEval.getTotalMaterial(state);
        boolean guardsAdvanced = positionalEval.areGuardsAdvanced(state);

        if (totalMaterial <= 6) {
            return MaterialEval.GamePhase.ENDGAME;
        } else if (totalMaterial <= 12 || guardsAdvanced) {
            return MaterialEval.GamePhase.MIDDLEGAME;
        } else {
            return MaterialEval.GamePhase.OPENING;
        }
    }

    /**
     * Get phase-specific weights
     */
    private double[] getPhaseWeights(MaterialEval.GamePhase phase) {
        return switch (phase) {
            case OPENING -> Weights.OPENING;
            case MIDDLEGAME -> Weights.MIDDLEGAME;
            case ENDGAME, TABLEBASE -> Weights.ENDGAME;
        };
    }

    // === PUBLIC UTILITY METHODS ===

    /**
     * Check if a guard is in danger (public for other components)
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        return safetyEval.isGuardInDanger(state, checkRed);
    }

    /**
     * Check if guards are in danger (for quiescence)
     */
    public boolean areGuardsInDanger(GameState state) {
        return safetyEval.areGuardsInDanger(state);
    }

    /**
     * Get material count for a side
     */
    public int getMaterialCount(GameState state, boolean isRed) {
        return materialEval.getTotalMaterial(state, isRed);
    }

    /**
     * Get total material on board
     */
    public int getTotalMaterial(GameState state) {
        return materialEval.getTotalMaterial(state);
    }

    // === TIME MANAGEMENT ===

    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = Math.max(0, timeMs);
        emergencyMode = timeMs < 1000;
    }

    public static long getRemainingTime() {
        return remainingTimeMs;
    }

    public static boolean isEmergencyMode() {
        return emergencyMode;
    }

    // === PARAMETER TUNING SUPPORT ===

    /**
     * Get current evaluation weights (for tuning)
     */
    public double[] getWeights() {
        return new double[] {
                Weights.MATERIAL, Weights.POSITIONAL,
                Weights.SAFETY, Weights.TACTICAL
        };
    }

    /**
     * Evaluate with custom weights (for parameter tuning)
     */
    public int evaluateWithWeights(GameState state, double[] weights) {
        if (weights.length != 4) {
            throw new IllegalArgumentException("Need 4 weights: material, positional, safety, tactical");
        }

        int eval = 0;
        eval += (int)(materialEval.evaluateMaterialWithActivity(state) * weights[0]);
        eval += (int)(positionalEval.evaluatePositional(state) * weights[1]);
        eval += (int)(safetyEval.evaluateGuardSafety(state) * weights[2]);
        eval += (int)(evaluateBasicTactics(state) * weights[3]);

        return eval;
    }
}