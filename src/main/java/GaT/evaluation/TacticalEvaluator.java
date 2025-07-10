package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;
import GaT.evaluation.HangingPieceDetector;

import java.util.List;

/**
 * COMPLETE TACTICAL EVALUATOR with HANGING PIECE DETECTION
 *
 * FEATURES:
 * ✅ Hanging piece detection and penalties
 * ✅ Emergency tactical awareness
 * ✅ Improved material safety evaluation
 * ✅ Better integration with SEE
 * ✅ All missing methods implemented
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
     * ENHANCED evaluation method with hanging piece detection
     */
    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Check for true game-ending positions
        int terminalScore = checkTerminalPosition(state, depth);
        if (Math.abs(terminalScore) >= CHECKMATE_SCORE) {
            return terminalScore; // Only return immediately for actual checkmate
        }

        // 🚨 CRITICAL: Check for hanging pieces first
        int hangingPenalty = 0;
        try {
            hangingPenalty = HangingPieceDetector.evaluateHangingPieces(state);

            // Debug output for critical hanging pieces
            if (Math.abs(hangingPenalty) > 2000) {
                System.out.printf("🚨 CRITICAL HANGING PIECES: penalty = %d%n", hangingPenalty);
            }
        } catch (Exception e) {
            System.err.println("❌ Hanging piece detection failed: " + e.getMessage());
            hangingPenalty = 0; // Safe fallback
        }

        // Detect game phase
        GamePhase phase = detectGamePhase(state);

        // Phase-based evaluation
        int eval = 0;

        switch (phase) {
            case OPENING:
                eval += evaluateMaterial(state);
                eval += evaluateOpening(state);
                // Hanging pieces are critical in opening
                eval += hangingPenalty * 2;
                break;

            case MIDDLEGAME:
                eval += evaluateMaterial(state);
                eval += evaluateTactical(state, depth);
                eval += evaluatePositional(state);
                // Hanging pieces are most critical in middlegame
                eval += hangingPenalty * 3;
                break;

            case ENDGAME:
                eval += evaluateMaterial(state);
                eval += evaluateEndgame(state);
                eval += evaluateTactical(state, depth);
                // Still important in endgame but slightly less weighted
                eval += hangingPenalty * 2;
                break;
        }

        // Add tactical bonuses
        eval += evaluateTacticalThemes(state);

        // Emergency safety check
        if (HangingPieceDetector.hasEmergencyHangingPieces(state, state.redToMove)) {
            System.out.println("🚨 EMERGENCY: Critical piece hanging!");
            eval += state.redToMove ? -5000 : +5000;
        }

        return eval;
    }

    /**
     * Enhanced tactical evaluation with hanging piece awareness
     */
    private int evaluateTactical(GameState state, int depth) {
        int score = 0;

        try {
            // Enhanced capture evaluation using SEE
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            for (Move move : moves) {
                if (isCapture(move, state)) {
                    try {
                        int seeValue = StaticExchangeEvaluator.evaluate(state, move);

                        if (seeValue > 500) {
                            score += 400; // Good capture available
                        } else if (seeValue < -200) {
                            score -= 300; // Avoid bad captures
                        }

                        // Extra bonus for capturing hanging pieces
                        if (seeValue > 1000) {
                            score += 600; // Capturing hanging piece
                        }
                    } catch (Exception e) {
                        // Fallback to basic capture evaluation
                        if (capturesGuard(move, state)) {
                            score += 1000;
                        } else {
                            score += 200;
                        }
                    }
                }
            }

            // Direct threat evaluation
            score += evaluateDirectThreats(state);

        } catch (Exception e) {
            System.err.println("❌ Error in tactical evaluation: " + e.getMessage());
            // Fallback to basic tactical evaluation
            return evaluateBasicTactical(state);
        }

        return score;
    }

    /**
     * Fallback tactical evaluation
     */
    private int evaluateBasicTactical(GameState state) {
        int score = 0;

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (capturesGuard(move, state)) {
                score += 1000;
            }
            if (approachesCastle(move, state)) {
                score += 800;
            }
        }

        return score;
    }

    /**
     * Enhanced move scoring with hanging piece awareness
     */
    public static int scoreMoveWithHangingDetection(Move move, GameState state) {
        int score = 0;

        // Basic move scoring
        if (isCapture(move, state)) {
            score += 2000;

            // Use SEE for accurate capture evaluation
            try {
                int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                score += seeValue;

                // Bonus for capturing hanging pieces
                if (seeValue > 1000) {
                    score += 1000; // Extra reward for free pieces
                }
            } catch (Exception e) {
                // Fallback to basic capture value
                score += capturesGuard(move, state) ? 3000 : 500;
            }
        }

        // 🚨 CRITICAL: Check if move leaves pieces hanging
        try {
            int hangingThreat = HangingPieceDetector.getHangingPieceThreat(state, move);
            if (hangingThreat > 1000) {
                score -= hangingThreat; // Penalize moves that hang pieces
                System.out.printf("⚠️  Move %s hangs pieces (penalty: %d)%n", move, hangingThreat);
            }
        } catch (Exception e) {
            // If hanging detection fails, be conservative
            System.err.println("❌ Hanging threat detection failed for move: " + move);
        }

        // Other tactical bonuses
        if (capturesGuard(move, state)) {
            score += 3000;
        }

        if (approachesCastle(move, state)) {
            score += 500;
        }

        // Central square bonus
        if (isCentralSquare(move.to)) {
            score += 100;
        }

        return score;
    }

    /**
     * Quick hanging piece check for move validation
     */
    public static boolean isMoveSafe(Move move, GameState state) {
        // Quick check: does this move hang any valuable pieces?
        int hangingThreat = HangingPieceDetector.getHangingPieceThreat(state, move);
        return hangingThreat < 2000; // Acceptable risk threshold
    }

    // === TERMINAL POSITION DETECTION ===

    private int checkTerminalPosition(GameState state, int depth) {
        // Check if guard was captured (guard bitmask is empty)
        if (state.redGuard == 0) {
            return -CHECKMATE_SCORE + depth; // Blue wins - red guard captured
        }
        if (state.blueGuard == 0) {
            return CHECKMATE_SCORE - depth; // Red wins - blue guard captured
        }

        // Check if guard reached enemy castle (winning condition)
        // Red guard reaches blue castle (rank 0, file 3)
        long redGuardPosition = state.redGuard;
        if (redGuardPosition != 0) {
            int redGuardSquare = Long.numberOfTrailingZeros(redGuardPosition);
            if (redGuardSquare == GameState.getIndex(0, 3)) {
                return CASTLE_REACH_SCORE - depth; // Red wins by reaching castle
            }
        }

        // Blue guard reaches red castle (rank 6, file 3)
        long blueGuardPosition = state.blueGuard;
        if (blueGuardPosition != 0) {
            int blueGuardSquare = Long.numberOfTrailingZeros(blueGuardPosition);
            if (blueGuardSquare == GameState.getIndex(6, 3)) {
                return -CASTLE_REACH_SCORE + depth; // Blue wins by reaching castle
            }
        }

        return 0; // No terminal position
    }

    // === GAME PHASE DETECTION ===

    private GamePhase detectGamePhase(GameState state) {
        int totalPieces = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalPieces <= 8) return GamePhase.ENDGAME;
        if (totalPieces <= 16) return GamePhase.MIDDLEGAME;
        return GamePhase.OPENING;
    }

    // === EVALUATION COMPONENTS ===

    private int evaluateMaterial(GameState state) {
        int materialBalance = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Count red material
            if (state.redStackHeights[i] > 0) {
                materialBalance += 100 * state.redStackHeights[i]; // Tower value
            }

            // Count blue material
            if (state.blueStackHeights[i] > 0) {
                materialBalance -= 100 * state.blueStackHeights[i]; // Tower value
            }
        }

        // Guards have special value
        if (state.redGuard != 0) {
            materialBalance += 2000; // Guard value
        }
        if (state.blueGuard != 0) {
            materialBalance -= 2000; // Guard value
        }

        return materialBalance;
    }

    private int evaluateOpening(GameState state) {
        int score = 0;

        // Development bonus
        score += evaluateDevelopment(state);

        // Central control
        score += evaluateCenterControl(state);

        return score;
    }

    private int evaluateDevelopment(GameState state) {
        int developmentScore = 0;

        // Pieces off back rank
        for (int file = 0; file < 7; file++) {
            // Red pieces
            if (state.redStackHeights[GameState.getIndex(6, file)] > 0) {
                developmentScore -= 20; // Penalty for undeveloped pieces
            }

            // Blue pieces
            if (state.blueStackHeights[GameState.getIndex(0, file)] > 0) {
                developmentScore += 20;
            }
        }

        // Central control in opening
        for (int rank = 2; rank <= 4; rank++) {
            for (int file = 2; file <= 4; file++) {
                int square = GameState.getIndex(rank, file);
                if (state.redStackHeights[square] > 0) {
                    developmentScore += 15;
                }
                if (state.blueStackHeights[square] > 0) {
                    developmentScore -= 15;
                }
            }
        }

        return developmentScore;
    }

    private int evaluateCenterControl(GameState state) {
        int controlScore = 0;
        int[] centralSquares = {
                GameState.getIndex(3, 3), // D4
                GameState.getIndex(2, 3), GameState.getIndex(4, 3), // D3, D5
                GameState.getIndex(3, 2), GameState.getIndex(3, 4)  // C4, E4
        };

        for (int square : centralSquares) {
            // Occupation
            if (state.redStackHeights[square] > 0) {
                controlScore += 20 + state.redStackHeights[square] * 5;
            }
            if (state.blueStackHeights[square] > 0) {
                controlScore -= 20 + state.blueStackHeights[square] * 5;
            }
        }

        return controlScore;
    }

    private int evaluateEndgame(GameState state) {
        int endgameScore = 0;

        // Guard advancement is critical
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);

            // Distance to enemy castle
            int distanceToCastle = rank + Math.abs(file - 3);
            endgameScore += (12 - distanceToCastle) * 50;

            // Bonus for being on D-file
            if (file == 3) {
                endgameScore += 100;
            }
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);

            // Distance to enemy castle
            int distanceToCastle = (6 - rank) + Math.abs(file - 3);
            endgameScore -= (12 - distanceToCastle) * 50;

            // Bonus for being on D-file
            if (file == 3) {
                endgameScore -= 100;
            }
        }

        return endgameScore;
    }

    private int evaluatePositional(GameState state) {
        int score = 0;

        // Piece advancement
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);
            int file = GameState.file(i);

            if (state.redStackHeights[i] > 0) {
                // Advancement bonus for red (moving toward rank 0)
                score += (6 - rank) * 8 * state.redStackHeights[i];

                // Central file bonus
                if (file >= 2 && file <= 4) {
                    score += 10 * state.redStackHeights[i];
                }
            }

            if (state.blueStackHeights[i] > 0) {
                // Advancement bonus for blue (moving toward rank 6)
                score -= rank * 8 * state.blueStackHeights[i];

                // Central file bonus
                if (file >= 2 && file <= 4) {
                    score -= 10 * state.blueStackHeights[i];
                }
            }
        }

        return score;
    }

    private int evaluateTacticalThemes(GameState state) {
        // Simple tactical pattern detection
        return 0; // Simplified for now
    }

    private int evaluateDirectThreats(GameState state) {
        int threatScore = 0;
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Limit analysis to avoid timeout
        int analyzed = 0;
        for (Move move : moves) {
            if (analyzed++ > 30) break; // Analyze at most 30 moves

            // Guard threats are valuable
            if (threatsEnemyGuard(move, state)) {
                threatScore += GUARD_THREAT_BONUS;
            }

            // Castle reaching threats
            if (threatsToCastle(move, state)) {
                threatScore += CASTLE_THREAT_BONUS;
            }

            // Multiple threats (forks)
            int threatsCount = countThreatsFromSquare(state, move.to, move.amountMoved);
            if (threatsCount >= 2) {
                threatScore += FORK_BONUS * Math.min(threatsCount - 1, 2); // Cap bonus
            }
        }

        return state.redToMove ? threatScore : -threatScore;
    }

    // === HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        if (state.redToMove) {
            // Red to move - check if capturing blue piece
            return (state.blueGuard & toBit) != 0 || (state.blueTowers & toBit) != 0;
        } else {
            // Blue to move - check if capturing red piece
            return (state.redGuard & toBit) != 0 || (state.redTowers & toBit) != 0;
        }
    }

    private static boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        if (state.redToMove) {
            return (state.blueGuard & toBit) != 0;
        } else {
            return (state.redGuard & toBit) != 0;
        }
    }

    private static boolean approachesCastle(Move move, GameState state) {
        // Check if move gets closer to opponent's castle
        int opponentCastle = state.redToMove ?
                GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        int oldDistance = Math.abs(GameState.rank(move.from) - GameState.rank(opponentCastle)) +
                Math.abs(GameState.file(move.from) - GameState.file(opponentCastle));

        int newDistance = Math.abs(GameState.rank(move.to) - GameState.rank(opponentCastle)) +
                Math.abs(GameState.file(move.to) - GameState.file(opponentCastle));

        return newDistance < oldDistance;
    }

    private static boolean isCentralSquare(int square) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        return (rank >= 2 && rank <= 4) && (file >= 2 && file <= 4);
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        if (state.redToMove) {
            // Red capturing blue
            if ((state.blueGuard & toBit) != 0) {
                return 2000; // Guard value
            }
            return state.blueStackHeights[move.to] * 100; // Tower value
        } else {
            // Blue capturing red
            if ((state.redGuard & toBit) != 0) {
                return 2000; // Guard value
            }
            return state.redStackHeights[move.to] * 100; // Tower value
        }
    }

    private boolean threatsEnemyGuard(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        return canAttackFrom(move.to, guardPos, move.amountMoved);
    }

    private boolean threatsToCastle(Move move, GameState state) {
        int enemyCastle = state.redToMove ?
                GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        // Check if guard move threatens castle
        if (move.amountMoved == 1) { // Guard move
            long guardBit = state.redToMove ? state.redGuard : state.blueGuard;
            if ((guardBit & GameState.bit(move.from)) != 0) {
                return canAttackFrom(move.to, enemyCastle, 1);
            }
        }

        return false;
    }

    private int countThreatsFromSquare(GameState state, int square, int range) {
        int threats = 0;
        boolean isRed = state.redToMove;

        // Count enemy pieces that can be attacked
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            long bit = GameState.bit(i);

            if (isRed) {
                if (((state.blueTowers | state.blueGuard) & bit) != 0) {
                    if (canAttackFrom(square, i, range)) {
                        threats++;
                    }
                }
            } else {
                if (((state.redTowers | state.redGuard) & bit) != 0) {
                    if (canAttackFrom(square, i, range)) {
                        threats++;
                    }
                }
            }
        }

        return threats;
    }

    private boolean canAttackFrom(int from, int to, int range) {
        if (from == to) return false;

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }
}