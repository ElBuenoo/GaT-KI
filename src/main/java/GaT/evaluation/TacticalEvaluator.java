package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * FIXED: TACTICAL EVALUATOR - Reasonable scores to prevent early termination
 *
 * KEY FIXES:
 * ✅ 1. Terminal scores reduced from ±5000 to ±2500 (except true checkmate)
 * ✅ 2. Added progressive evaluation based on game phase
 * ✅ 3. Better material imbalance handling
 * ✅ 4. More nuanced tactical evaluation
 * ✅ 5. Checkmate scores only for actual game-ending positions
 */
public class TacticalEvaluator extends Evaluator {

    // === REASONABLE TERMINAL SCORES ===
    private static final int CHECKMATE_SCORE = 10000;        // Only for actual checkmate
    private static final int GUARD_CAPTURE_SCORE = 2500;     // Serious but not terminal
    private static final int CASTLE_REACH_SCORE = 3000;      // Winning but search deeper

    // === TACTICAL CONSTANTS ===
    private static final int GUARD_THREAT_BONUS = 300;       // Reduced from 500
    private static final int CASTLE_THREAT_BONUS = 250;      // Reduced from 400
    private static final int FORK_BONUS = 200;               // Reduced from 300
    private static final int PIN_BONUS = 150;                // Reduced from 250
    private static final int DISCOVERED_ATTACK_BONUS = 120;  // Reduced from 200
    private static final int TEMPO_BONUS = 30;               // Reduced from 50

    // === GAME PHASES ===
    private enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }

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
                eval += evaluateMaterial(state) * 40 / 100;
                eval += evaluateDevelopment(state) * 25 / 100;
                eval += evaluateCenterControl(state) * 20 / 100;
                eval += evaluateTactics(state) * 15 / 100;
                break;

            case MIDDLEGAME:
                eval += evaluateMaterial(state) * 35 / 100;
                eval += evaluateTactics(state) * 30 / 100;
                eval += evaluatePositionalFactors(state) * 20 / 100;
                eval += evaluateSafety(state) * 15 / 100;
                break;

            case ENDGAME:
                eval += evaluateMaterial(state) * 20 / 100;
                eval += evaluateEndgame(state) * 40 / 100;
                eval += evaluateGuardActivity(state) * 25 / 100;
                eval += evaluateTactics(state) * 15 / 100;
                break;
        }

        // Add terminal score if significant but not checkmate
        if (terminalScore != 0) {
            eval = (eval + terminalScore * 3) / 4; // Blend scores for smoother evaluation
        }

        return eval;
    }

    /**
     * FIXED: More reasonable terminal position scores
     */
    private int checkTerminalPosition(GameState state, int depth) {
        // True checkmate - guard captured
        if (state.redGuard == 0) {
            return -CHECKMATE_SCORE - depth; // Blue wins
        }
        if (state.blueGuard == 0) {
            return CHECKMATE_SCORE + depth; // Red wins
        }

        // Guard reached enemy castle - winning but not immediate checkmate
        boolean redWins = (state.redGuard & GameState.bit(GameState.getIndex(0, 3))) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(GameState.getIndex(6, 3))) != 0;

        if (redWins) {
            return CASTLE_REACH_SCORE + depth * 10;
        }
        if (blueWins) {
            return -CASTLE_REACH_SCORE - depth * 10;
        }

        return 0;
    }

    private GamePhase detectGamePhase(GameState state) {
        int totalMaterial = getTotalMaterial(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        if (totalMaterial <= 6 || (totalMaterial <= 8 && guardsAdvanced)) {
            return GamePhase.ENDGAME;
        } else if (totalMaterial <= 12 || guardsAdvanced) {
            return GamePhase.MIDDLEGAME;
        } else {
            return GamePhase.OPENING;
        }
    }

    /**
     * Material evaluation with better balance
     */
    private int evaluateMaterial(GameState state) {
        int materialScore = 0;
        int redMaterial = 0;
        int blueMaterial = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                redMaterial += height;
                materialScore += height * 100;

                // Positional bonus
                materialScore += getPositionalBonus(i, height, true, state);
            }

            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                blueMaterial += height;
                materialScore -= height * 100;

                // Positional bonus
                materialScore -= getPositionalBonus(i, height, false, state);
            }
        }

        // Material imbalance bonus
        int imbalance = redMaterial - blueMaterial;
        if (Math.abs(imbalance) >= 2) {
            materialScore += imbalance * 50; // Extra bonus for material advantage
        }

        return materialScore;
    }

    /**
     * Enhanced tactical evaluation with reasonable scores
     */
    private int evaluateTactics(GameState state) {
        int tacticalScore = 0;

        // Direct threats
        tacticalScore += evaluateDirectThreats(state);

        // Forcing moves and tempo
        tacticalScore += evaluateForcingMoves(state);

        // Piece coordination
        tacticalScore += evaluateAttackCoordination(state);

        return tacticalScore;
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

            // Control (can move to square)
            controlScore += evaluateSquareControl(state, square) * 10;
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

        // Tower support for guards
        endgameScore += evaluateGuardSupport(state);

        return endgameScore;
    }

    private int evaluateGuardActivity(GameState state) {
        int activityScore = 0;

        // Red guard
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            activityScore += countGuardMobility(state, guardPos, true) * 30;
        }

        // Blue guard
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            activityScore -= countGuardMobility(state, guardPos, false) * 30;
        }

        return activityScore;
    }

    // === HELPER METHODS ===



    private boolean areGuardsAdvanced(GameState state) {
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (rank <= 2) return true;
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (rank >= 4) return true;
        }
        return false;
    }

    private int getPositionalBonus(int square, int height, boolean isRed, GameState state) {
        int bonus = 0;
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        // Advancement bonus
        if (isRed) {
            bonus += (6 - rank) * 8;
        } else {
            bonus += rank * 8;
        }

        // Central files bonus
        if (file >= 2 && file <= 4) {
            bonus += 10;
        }

        // Height bonus for mobility
        bonus += height * 5;

        return bonus;
    }

    private int evaluatePositionalFactors(GameState state) {
        // Simplified to avoid deep recursion
        return evaluateCenterControl(state) / 2;
    }

    private int evaluateSafety(GameState state) {
        int safetyScore = 0;

        // Check if guards are safe
        if (isGuardInDanger(state, true)) {
            safetyScore -= 200;
        }
        if (isGuardInDanger(state, false)) {
            safetyScore += 200;
        }

        return safetyScore;
    }

    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Simple check - can enemy capture guard next move
        GameState simState = state.copy();
        simState.redToMove = !checkRed;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(simState);

        for (Move move : enemyMoves) {
            if (move.to == guardPos) {
                return true;
            }
        }

        return false;
    }

    private int evaluateForcingMoves(GameState state) {
        int forceScore = 0;
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        int analyzed = 0;
        for (Move move : moves) {
            if (analyzed++ > 20) break; // Limit analysis

            // Checks are forcing
            if (putGuardInCheck(move, state)) {
                forceScore += TEMPO_BONUS * 2;
            }
        }

        return state.redToMove ? forceScore : -forceScore;
    }

    private int evaluateAttackCoordination(GameState state) {
        int coordinationScore = 0;

        // Simplified - count pieces that can attack same squares
        for (int targetRank = 2; targetRank <= 4; targetRank++) {
            for (int targetFile = 2; targetFile <= 4; targetFile++) {
                int target = GameState.getIndex(targetRank, targetFile);
                int redAttackers = countAttackers(state, target, true);
                int blueAttackers = countAttackers(state, target, false);

                if (redAttackers >= 2) {
                    coordinationScore += 10 * (redAttackers - 1);
                }
                if (blueAttackers >= 2) {
                    coordinationScore -= 10 * (blueAttackers - 1);
                }
            }
        }

        return coordinationScore;
    }

    private int evaluateSquareControl(GameState state, int square) {
        return countAttackers(state, square, true) - countAttackers(state, square, false);
    }

    private int evaluateGuardSupport(GameState state) {
        int supportScore = 0;

        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            supportScore += countGuardSupport(state, guardPos, true) * 40;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            supportScore -= countGuardSupport(state, guardPos, false) * 40;
        }

        return supportScore;
    }

    private int countGuardMobility(GameState state, int guardPos, boolean isRed) {
        int mobility = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int target = guardPos + dir;
            if (GameState.isOnBoard(target) && !isRankWrap(guardPos, target, dir)) {
                long bit = GameState.bit(target);
                boolean blocked = isRed ?
                        ((state.redTowers | state.redGuard) & bit) != 0 :
                        ((state.blueTowers | state.blueGuard) & bit) != 0;

                if (!blocked) {
                    mobility++;
                }
            }
        }

        return mobility;
    }

    private int countGuardSupport(GameState state, int guardPos, boolean isRed) {
        int support = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = guardPos + dir;
            if (GameState.isOnBoard(adjacent) && !isRankWrap(guardPos, adjacent, dir)) {
                long bit = GameState.bit(adjacent);
                if (isRed && (state.redTowers & bit) != 0) {
                    support++;
                } else if (!isRed && (state.blueTowers & bit) != 0) {
                    support++;
                }
            }
        }

        return support;
    }

    private int countAttackers(GameState state, int target, boolean isRed) {
        int attackers = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (isRed) {
                if (state.redStackHeights[i] > 0 && canAttackFrom(i, target, state.redStackHeights[i])) {
                    attackers++;
                }
                if ((state.redGuard & GameState.bit(i)) != 0 && canAttackFrom(i, target, 1)) {
                    attackers++;
                }
            } else {
                if (state.blueStackHeights[i] > 0 && canAttackFrom(i, target, state.blueStackHeights[i])) {
                    attackers++;
                }
                if ((state.blueGuard & GameState.bit(i)) != 0 && canAttackFrom(i, target, 1)) {
                    attackers++;
                }
            }
        }

        return attackers;
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

    private boolean putGuardInCheck(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        return canAttackFrom(move.to, guardPos, move.amountMoved);
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

    private boolean isRankWrap(int from, int to, int direction) {
        if (Math.abs(direction) == 1) { // Horizontal movement
            return GameState.rank(from) != GameState.rank(to);
        }
        return false;
    }
}