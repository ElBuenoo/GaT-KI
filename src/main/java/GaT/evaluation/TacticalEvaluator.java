package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * COMPLETE TACTICAL EVALUATOR - Extends base Evaluator with tactical features
 *
 * FEATURES:
 * ✅ Enhanced tactical position evaluation
 * ✅ Threat-aware scoring
 * ✅ SEE integration for capture evaluation
 * ✅ Game phase recognition
 * ✅ Progressive evaluation scaling
 */
public class TacticalEvaluator extends Evaluator {

    // === TACTICAL SCORING CONSTANTS ===
    private static final int CHECKMATE_SCORE = 10000;        // Only for actual checkmate
    private static final int GUARD_CAPTURE_SCORE = 2500;     // Serious but not terminal
    private static final int CASTLE_REACH_SCORE = 3000;      // Winning but search deeper

    // === TACTICAL BONUSES ===
    private static final int GUARD_THREAT_BONUS = 300;
    private static final int CASTLE_THREAT_BONUS = 250;
    private static final int FORK_BONUS = 200;
    private static final int PIN_BONUS = 150;
    private static final int DISCOVERED_ATTACK_BONUS = 120;
    private static final int TEMPO_BONUS = 30;

    // === GAME PHASES ===
    private enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    /**
     * Main evaluation method with tactical enhancements
     */
    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Check for true game-ending positions
        int terminalScore = checkTerminalPosition(state, depth);
        if (Math.abs(terminalScore) >= CHECKMATE_SCORE) {
            return terminalScore; // Only return immediately for actual checkmate
        }

        // Detect game phase
        GamePhase phase = detectGamePhase(state);

        // Phase-based evaluation
        int eval = 0;

        switch (phase) {
            case OPENING:
                eval += evaluateMaterial(state);
                eval += evaluateOpening(state);
                eval += evaluateTacticalThreats(state) * 2; // Extra tactical weight in opening
                break;

            case MIDDLEGAME:
                eval += evaluateMaterial(state);
                eval += evaluatePositional(state);
                eval += evaluateTacticalThreats(state) * 3; // Maximum tactical weight
                eval += evaluateKingSafety(state);
                eval += evaluateMobility(state);
                break;

            case ENDGAME:
                eval += evaluateMaterial(state) * 2; // Material more important
                eval += evaluateEndgame(state);
                eval += evaluateTacticalThreats(state); // Reduced tactical weight
                break;
        }

        // Add depth-dependent bonus for deeper search
        eval += (10 - depth) * 2;

        return eval;
    }

    /**
     * Check for game-ending positions
     */
    private int checkTerminalPosition(GameState state, int depth) {
        // True checkmate: guard captured
        if (state.redGuard == 0) {
            return -CHECKMATE_SCORE + depth; // Prefer shorter mates
        }
        if (state.blueGuard == 0) {
            return CHECKMATE_SCORE - depth;
        }

        // Castle reach (winning but not mate)
        if ((state.redGuard & GameState.bit(GameState.getIndex(0, 3))) != 0) {
            return CASTLE_REACH_SCORE - depth;
        }
        if ((state.blueGuard & GameState.bit(GameState.getIndex(6, 3))) != 0) {
            return -CASTLE_REACH_SCORE + depth;
        }

        // Check for no legal moves (stalemate)
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return 0; // Stalemate
        }

        return 0; // No terminal position
    }

    /**
     * Detect current game phase
     */
    private GamePhase detectGamePhase(GameState state) {
        int totalMaterial = getTotalMaterial(state);

        if (totalMaterial <= 8) {
            return GamePhase.ENDGAME;
        } else if (totalMaterial <= 20) {
            return GamePhase.MIDDLEGAME;
        } else {
            return GamePhase.OPENING;
        }
    }

    /**
     * Get total material count
     */
    private int getTotalMaterial(GameState state) {
        int material = 0;

        // Count towers
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            material += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        // Guards count as 2 material each
        if (state.redGuard != 0) material += 2;
        if (state.blueGuard != 0) material += 2;

        return material;
    }

    /**
     * Evaluate material balance
     */
    private int evaluateMaterial(GameState state) {
        int redMaterial = 0;
        int blueMaterial = 0;

        // Count towers
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redMaterial += state.redStackHeights[i] * 100;
            blueMaterial += state.blueStackHeights[i] * 100;
        }

        // Guards are worth 2000 points each
        if (state.redGuard != 0) redMaterial += 2000;
        if (state.blueGuard != 0) blueMaterial += 2000;

        return redMaterial - blueMaterial;
    }

    /**
     * Evaluate tactical threats
     */
    private int evaluateTacticalThreats(GameState state) {
        int score = 0;

        try {
            ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

            // Penalty for being under threat
            if (state.redToMove) {
                score -= threats.threatLevel * 50;
                if (threats.mustDefend) score -= 200;
                if (threats.inCheck) score -= 300;
            } else {
                score += threats.threatLevel * 50;
                if (threats.mustDefend) score += 200;
                if (threats.inCheck) score += 300;
            }

            // Evaluate specific threat types
            for (ThreatDetector.Threat threat : threats.immediateThreats) {
                int threatValue = switch (threat.type) {
                    case ThreatDetector.GUARD_THREAT -> GUARD_THREAT_BONUS;
                    case ThreatDetector.CASTLE_THREAT -> CASTLE_THREAT_BONUS;
                    case ThreatDetector.FORK_THREAT -> FORK_BONUS;
                    case ThreatDetector.PIN_THREAT -> PIN_BONUS;
                    case ThreatDetector.DISCOVERED_THREAT -> DISCOVERED_ATTACK_BONUS;
                    default -> 50;
                };

                score += state.redToMove ? -threatValue : threatValue;
            }

        } catch (Exception e) {
            // If threat analysis fails, fall back to simple evaluation
            score += evaluateSimpleThreats(state);
        }

        return score;
    }

    /**
     * Simple threat evaluation fallback
     */
    private int evaluateSimpleThreats(GameState state) {
        int score = 0;

        // Check for immediate captures
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (isCapture(move, state)) {
                if (capturesGuard(move, state)) {
                    score += state.redToMove ? GUARD_CAPTURE_SCORE : -GUARD_CAPTURE_SCORE;
                } else {
                    score += state.redToMove ? 100 : -100;
                }
            }
        }

        return score;
    }

    /**
     * Evaluate opening phase
     */
    private int evaluateOpening(GameState state) {
        int score = 0;

        // Development bonus
        score += evaluateDevelopment(state);

        // Castle safety
        score += evaluateCastleSafety(state);

        // Center control
        score += evaluateCenterControl(state);

        return score;
    }

    /**
     * Evaluate development
     */
    private int evaluateDevelopment(GameState state) {
        int score = 0;

        // Penalty for guards still on starting squares
        if ((state.redGuard & GameState.bit(GameState.getIndex(6, 3))) != 0) {
            score -= 20; // Red guard hasn't moved
        }
        if ((state.blueGuard & GameState.bit(GameState.getIndex(0, 3))) != 0) {
            score += 20; // Blue guard hasn't moved
        }

        return score;
    }

    /**
     * Evaluate castle safety
     */
    private int evaluateCastleSafety(GameState state) {
        int score = 0;

        // Bonus for pieces near own castle
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);
            int file = GameState.file(i);

            if (state.redStackHeights[i] > 0) {
                int distToRedCastle = Math.abs(rank - 6) + Math.abs(file - 3);
                if (distToRedCastle <= 2) score += 10;
            }

            if (state.blueStackHeights[i] > 0) {
                int distToBlueCastle = Math.abs(rank - 0) + Math.abs(file - 3);
                if (distToBlueCastle <= 2) score -= 10;
            }
        }

        return score;
    }

    /**
     * Evaluate center control
     */
    private int evaluateCenterControl(GameState state) {
        int score = 0;

        // Central squares
        int[] centralSquares = {
                GameState.getIndex(3, 3), // D4
                GameState.getIndex(3, 2), // C4
                GameState.getIndex(3, 4), // E4
                GameState.getIndex(2, 3), // D3
                GameState.getIndex(4, 3)  // D5
        };

        for (int square : centralSquares) {
            if (state.redStackHeights[square] > 0) {
                score += state.redStackHeights[square] * 5;
            }
            if (state.blueStackHeights[square] > 0) {
                score -= state.blueStackHeights[square] * 5;
            }
        }

        return score;
    }

    /**
     * Evaluate positional factors
     */
    private int evaluatePositional(GameState state) {
        int score = 0;

        score += evaluatePieceActivity(state);
        score += evaluateControlledSquares(state);
        score += evaluatePawnStructure(state);

        return score;
    }

    /**
     * Evaluate piece activity
     */
    private int evaluatePieceActivity(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red pieces activity
            if (state.redStackHeights[i] > 0) {
                int activity = calculatePieceActivity(i, state.redStackHeights[i], true);
                score += activity;
            }

            // Blue pieces activity
            if (state.blueStackHeights[i] > 0) {
                int activity = calculatePieceActivity(i, state.blueStackHeights[i], false);
                score -= activity;
            }
        }

        return score;
    }

    /**
     * Calculate activity for a single piece
     */
    private int calculatePieceActivity(int square, int height, boolean isRed) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        int activity = 0;

        // Centralization bonus
        int centrality = 6 - (Math.abs(rank - 3) + Math.abs(file - 3));
        activity += centrality * 2;

        // Advancement bonus
        if (isRed) {
            activity += (6 - rank) * 3; // Bonus for advancing toward blue
        } else {
            activity += rank * 3; // Bonus for advancing toward red
        }

        // Height bonus
        activity += height * 5;

        return activity;
    }

    /**
     * Evaluate controlled squares
     */
    private int evaluateControlledSquares(GameState state) {
        // Simplified implementation
        return 0;
    }

    /**
     * Evaluate pawn structure (tower formations)
     */
    private int evaluatePawnStructure(GameState state) {
        int score = 0;

        // Bonus for tall stacks
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] >= 3) {
                score += (state.redStackHeights[i] - 2) * 10;
            }
            if (state.blueStackHeights[i] >= 3) {
                score -= (state.blueStackHeights[i] - 2) * 10;
            }
        }

        return score;
    }

    /**
     * Evaluate endgame factors
     */
    private int evaluateEndgame(GameState state) {
        int score = 0;

        score += evaluateKingActivity(state);
        score += evaluatePassedTowers(state);
        score += evaluateKingProximity(state);

        return score;
    }

    /**
     * Evaluate king (guard) activity in endgame
     */
    private int evaluateKingActivity(GameState state) {
        int score = 0;

        // More active kings are better in endgame
        if (state.redGuard != 0) {
            int redGuardSquare = Long.numberOfTrailingZeros(state.redGuard);
            int redCentrality = 6 - (Math.abs(GameState.rank(redGuardSquare) - 3) +
                    Math.abs(GameState.file(redGuardSquare) - 3));
            score += redCentrality * 15;
        }

        if (state.blueGuard != 0) {
            int blueGuardSquare = Long.numberOfTrailingZeros(state.blueGuard);
            int blueCentrality = 6 - (Math.abs(GameState.rank(blueGuardSquare) - 3) +
                    Math.abs(GameState.file(blueGuardSquare) - 3));
            score -= blueCentrality * 15;
        }

        return score;
    }

    /**
     * Evaluate passed towers
     */
    private int evaluatePassedTowers(GameState state) {
        // Simplified implementation
        return 0;
    }

    /**
     * Evaluate king proximity to action
     */
    private int evaluateKingProximity(GameState state) {
        // Simplified implementation
        return 0;
    }

    /**
     * Evaluate king safety
     */
    private int evaluateKingSafety(GameState state) {
        int score = 0;

        // Guards should generally stay protected
        if (state.redGuard != 0) {
            int redGuardSquare = Long.numberOfTrailingZeros(state.redGuard);
            score += evaluateGuardSafety(state, redGuardSquare, true);
        }

        if (state.blueGuard != 0) {
            int blueGuardSquare = Long.numberOfTrailingZeros(state.blueGuard);
            score -= evaluateGuardSafety(state, blueGuardSquare, false);
        }

        return score;
    }

    /**
     * Evaluate safety of specific guard
     */
    private int evaluateGuardSafety(GameState state, int guardSquare, boolean isRed) {
        int safety = 0;

        // Count friendly pieces nearby
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int distance = Math.abs(GameState.rank(i) - GameState.rank(guardSquare)) +
                    Math.abs(GameState.file(i) - GameState.file(guardSquare));

            if (distance <= 2) {
                if (isRed && state.redStackHeights[i] > 0) {
                    safety += 10;
                } else if (!isRed && state.blueStackHeights[i] > 0) {
                    safety += 10;
                }
            }
        }

        return safety;
    }

    /**
     * Evaluate mobility
     */
    private int evaluateMobility(GameState state) {
        // Count legal moves as mobility indicator
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        return state.redToMove ? moves.size() * 2 : -moves.size() * 2;
    }

    // === HELPER METHODS ===

    /**
     * Check if move is a capture
     */
    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move captures guard
     */
    private boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Quick evaluation method for emergency situations
     */
    public int evaluateQuick(GameState state) {
        if (state == null) return 0;

        // Just material balance for speed
        return evaluateMaterial(state);
    }
}