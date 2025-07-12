package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

/**
 * MODULAR EVALUATOR - Complete Implementation with All Modules
 *
 * Integrates all evaluation modules for Guards and Towers:
 * - MaterialEval: Piece values and material balance
 * - PositionalEval: Guard advancement and piece positioning
 * - SafetyEval: Guard safety and threat detection
 * - TacticalEvaluator: Pattern recognition (forks, pins, chains)
 * - ThreatMap: Multi-turn threat analysis
 */
public class ModularEvaluator extends Evaluator {

    // === EVALUATION MODULES ===
    private final MaterialEval materialModule;
    private final PositionalEval positionalModule;
    private final SafetyEval safetyModule;
    private final TacticalEvaluator tacticalModule;
    private final ThreatMap threatMap;

    // === CONFIGURATION ===
    private volatile boolean useModularEvaluation = true;
    private volatile EvaluationMode mode = EvaluationMode.STANDARD;

    // === PHASE WEIGHTS ===
    private static final class PhaseWeights {
        final double material;
        final double positional;
        final double safety;
        final double tactical;
        final double threat;

        PhaseWeights(double material, double positional, double safety, double tactical, double threat) {
            this.material = material;
            this.positional = positional;
            this.safety = safety;
            this.tactical = tactical;
            this.threat = threat;
        }
    }

    // Weight configurations for different game phases
    private static final PhaseWeights OPENING_WEIGHTS =
            new PhaseWeights(0.30, 0.35, 0.15, 0.10, 0.10);
    private static final PhaseWeights MIDDLEGAME_WEIGHTS =
            new PhaseWeights(0.30, 0.20, 0.20, 0.15, 0.15);
    private static final PhaseWeights ENDGAME_WEIGHTS =
            new PhaseWeights(0.25, 0.30, 0.15, 0.20, 0.10);

    // === EVALUATION MODES ===
    public enum EvaluationMode {
        EMERGENCY,    // < 200ms - Ultra fast
        BLITZ,        // < 1000ms - Fast evaluation
        STANDARD,     // < 5000ms - Normal evaluation
        DEEP,         // > 5000ms - Full analysis
        ANALYSIS      // Unlimited - Complete evaluation
    }

    // === CONSTRUCTOR ===
    public ModularEvaluator() {
        // Initialize all modules
        this.materialModule = new MaterialEval();
        this.positionalModule = new PositionalEval();
        this.safetyModule = new SafetyEval();
        this.tacticalModule = new TacticalEvaluator();
        this.threatMap = new ThreatMap();

        System.out.println("🚀 ModularEvaluator initialized with all evaluation modules");
    }

    // === MAIN EVALUATION INTERFACE ===

    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Legacy compatibility
        if (!useModularEvaluation) {
            return super.evaluate(state, depth);
        }

        try {
            // Build threat map for position analysis
            threatMap.buildThreatMap(state);

            // Determine evaluation mode based on time
            updateEvaluationMode();

            // Terminal position check
            int terminalScore = checkTerminalPosition(state, depth);
            if (terminalScore != 0) {
                return terminalScore;
            }

            // Choose evaluation strategy based on mode
            return switch (mode) {
                case EMERGENCY -> evaluateEmergency(state, depth);
                case BLITZ -> evaluateBlitz(state, depth);
                case STANDARD -> evaluateStandard(state, depth);
                case DEEP -> evaluateDeep(state, depth);
                case ANALYSIS -> evaluateAnalysis(state, depth);
            };

        } catch (Exception e) {
            // Graceful fallback
            System.err.println("⚠️ ModularEvaluator error: " + e.getMessage());
            return super.evaluate(state, depth);
        }
    }

    // === EVALUATION STRATEGIES ===

    /**
     * EMERGENCY MODE - Ultra fast evaluation
     */
    private int evaluateEmergency(GameState state, int depth) {
        // Only material counting
        return materialModule.evaluateMaterialSimple(state);
    }

    /**
     * BLITZ MODE - Fast evaluation with basics
     */
    private int evaluateBlitz(GameState state, int depth) {
        int eval = 0;

        // Material (70%)
        eval += materialModule.evaluateMaterialBasic(state) * 70 / 100;

        // Basic safety (20%)
        eval += safetyModule.evaluateGuardSafetyBasic(state) * 20 / 100;

        // Fast positional (10%)
        eval += positionalModule.evaluateGuardAdvancementFast(state) * 10 / 100;

        return eval;
    }

    /**
     * STANDARD MODE - Balanced evaluation
     */
    private int evaluateStandard(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // Material evaluation
        eval += (int)(materialModule.evaluateMaterialWithActivity(state) * weights.material);

        // Positional evaluation
        eval += (int)(positionalModule.evaluatePositional(state) * weights.positional);

        // Safety evaluation (pass threat map for integration)
        safetyModule.setThreatMap(threatMap);
        eval += (int)(safetyModule.evaluateGuardSafety(state) * weights.safety);

        // Tactical evaluation
        eval += (int)(tacticalModule.evaluateTactical(state) * weights.tactical);

        // Threat evaluation
        eval += (int)(threatMap.calculateThreatScore(state) * weights.threat);

        return eval;
    }

    /**
     * DEEP MODE - Comprehensive evaluation
     */
    private int evaluateDeep(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // Advanced evaluations
        eval += (int)(materialModule.evaluateMaterialAdvanced(state) * weights.material);
        eval += (int)(positionalModule.evaluatePositionalAdvanced(state) * weights.positional);

        // Pass threat map to safety module
        safetyModule.setThreatMap(threatMap);
        eval += (int)(safetyModule.evaluateGuardSafetyAdvanced(state) * weights.safety);

        eval += (int)(tacticalModule.evaluateTactical(state) * weights.tactical);
        eval += (int)(threatMap.calculateThreatScore(state) * weights.threat);

        // Phase-specific adjustments
        switch (phase) {
            case OPENING -> eval += evaluateOpeningSpecific(state);
            case MIDDLEGAME -> eval += evaluateMiddlegameSpecific(state);
            case ENDGAME -> eval += evaluateEndgameSpecific(state);
        }

        return eval;
    }

    /**
     * ANALYSIS MODE - Maximum depth evaluation
     */
    private int evaluateAnalysis(GameState state, int depth) {
        // Use deep evaluation with extra analysis
        return evaluateDeep(state, depth);
    }

    // === GAME PHASE DETECTION ===

    private enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    private GamePhase detectGamePhase(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        // Check if guards are advanced
        boolean guardsAdvanced = false;
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (rank <= 2) guardsAdvanced = true;
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (rank >= 4) guardsAdvanced = true;
        }

        // Phase determination
        if (totalMaterial <= 6 || (totalMaterial <= 8 && guardsAdvanced)) {
            return GamePhase.ENDGAME;
        } else if (totalMaterial <= 12 || guardsAdvanced) {
            return GamePhase.MIDDLEGAME;
        } else {
            return GamePhase.OPENING;
        }
    }

    private PhaseWeights getPhaseWeights(GamePhase phase) {
        return switch (phase) {
            case OPENING -> OPENING_WEIGHTS;
            case MIDDLEGAME -> MIDDLEGAME_WEIGHTS;
            case ENDGAME -> ENDGAME_WEIGHTS;
        };
    }

    // === PHASE-SPECIFIC EVALUATIONS ===

    private int evaluateOpeningSpecific(GameState state) {
        int bonus = 0;

        // Central control is important in opening
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int file = GameState.file(i);
            if (file >= 2 && file <= 4) { // Central files
                bonus += state.redStackHeights[i] * 10;
                bonus -= state.blueStackHeights[i] * 10;
            }
        }

        return bonus;
    }

    private int evaluateMiddlegameSpecific(GameState state) {
        int bonus = 0;

        // Piece coordination and tactics
        if (tacticalModule.detectForks(state) != 0) {
            bonus += 50;
        }

        return bonus;
    }

    private int evaluateEndgameSpecific(GameState state) {
        int bonus = 0;

        // Guard activity is crucial
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            bonus += (6 - rank) * 50;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            bonus -= rank * 50;
        }

        return bonus;
    }

    // === TERMINAL POSITIONS ===

    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Castle reached
        int redCastle = GameState.getIndex(0, 3); // D1
        int blueCastle = GameState.getIndex(6, 3); // D7

        boolean redWins = (state.redGuard & GameState.bit(redCastle)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(blueCastle)) != 0;

        if (redWins) return CASTLE_REACH_SCORE + depth;
        if (blueWins) return -CASTLE_REACH_SCORE - depth;

        return 0;
    }

    // === TIME MANAGEMENT ===

    private void updateEvaluationMode() {
        long remainingTime = getRemainingTime();

        if (remainingTime < 200) {
            mode = EvaluationMode.EMERGENCY;
        } else if (remainingTime < 1000) {
            mode = EvaluationMode.BLITZ;
        } else if (remainingTime < 5000) {
            mode = EvaluationMode.STANDARD;
        } else if (remainingTime < 20000) {
            mode = EvaluationMode.DEEP;
        } else {
            mode = EvaluationMode.ANALYSIS;
        }
    }

    // === CONFIGURATION METHODS ===

    public void setUseModularEvaluation(boolean use) {
        this.useModularEvaluation = use;
        System.out.println("ModularEvaluator: " + (use ? "ENABLED" : "DISABLED"));
    }

    public void setEvaluationMode(EvaluationMode mode) {
        this.mode = mode;
        System.out.println("ModularEvaluator mode: " + mode);
    }

    public EvaluationMode getEvaluationMode() {
        return mode;
    }

    // === DIAGNOSTICS ===

    public String getEvaluationBreakdown(GameState state) {
        if (!useModularEvaluation) {
            return "ModularEvaluation disabled";
        }

        // Rebuild threat map for analysis
        threatMap.buildThreatMap(state);

        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        StringBuilder sb = new StringBuilder();
        sb.append("=== MODULAR EVALUATION BREAKDOWN ===\n");
        sb.append("Phase: ").append(phase).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        sb.append("Time remaining: ").append(getRemainingTime()).append("ms\n\n");

        sb.append("Components:\n");
        sb.append(String.format("  Material: %+d (%.0f%%)\n",
                materialModule.evaluateMaterialWithActivity(state), weights.material * 100));
        sb.append(String.format("  Positional: %+d (%.0f%%)\n",
                positionalModule.evaluatePositional(state), weights.positional * 100));

        // Pass threat map to safety module for breakdown
        safetyModule.setThreatMap(threatMap);
        sb.append(String.format("  Safety: %+d (%.0f%%)\n",
                safetyModule.evaluateGuardSafety(state), weights.safety * 100));

        sb.append(String.format("  Tactical: %+d (%.0f%%)\n",
                tacticalModule.evaluateTactical(state), weights.tactical * 100));
        sb.append(String.format("  Threats: %+d (%.0f%%)\n",
                threatMap.calculateThreatScore(state), weights.threat * 100));

        sb.append("\nTotal: ").append(evaluate(state, 0));

        return sb.toString();
    }

    // === LEGACY COMPATIBILITY ===

    @Override
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        return safetyModule.isGuardInDanger(state, checkRed);
    }

    // === CONSTANTS ===
    private static final int GUARD_CAPTURE_SCORE = 2500;
    private static final int CASTLE_REACH_SCORE = 3000;
}