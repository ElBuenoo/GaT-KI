package GaT.evaluation;

import GaT.game.GameState;

/**
 * COMPLETELY FIXED EVALUATOR for Guard & Towers
 *
 * FIXES APPLIED:
 * ✅ Fixed central control bonus calculation
 * ✅ Corrected perspective handling (no double negation)
 * ✅ Improved advancement bonus logic
 * ✅ Added proper guard safety evaluation
 * ✅ Fixed castle definitions and terminal detection
 * ✅ Enhanced positional factors
 */
public class Evaluator {

    // === CORE VALUES ===
    private static final int TOWER_VALUE = 100;
    private static final int GUARD_CAPTURE = 10000;
    private static final int CASTLE_REACH = 10000;

    // === CORRECT CASTLE DEFINITIONS ===
    // Red guard (starts at D7) tries to reach D1 (blue's castle)
    // Blue guard (starts at D1) tries to reach D7 (red's castle)
    private static final int RED_TARGET_CASTLE = GameState.getIndex(0, 3);   // D1 (index 3)
    private static final int BLUE_TARGET_CASTLE = GameState.getIndex(6, 3);  // D7 (index 45)

    // === FIXED EVALUATION WEIGHTS ===
    private static final int ADVANCEMENT_BONUS = 8;
    private static final int D_FILE_BONUS = 20;      // Increased for D-file importance
    private static final int CENTRAL_BONUS = 15;     // Increased for central files
    private static final int GUARD_DISTANCE_BONUS = 60;
    private static final int D_FILE_GUARD_BONUS = 120;
    private static final int CASTLE_PROXIMITY_BONUS = 250;
    private static final int SAFETY_PENALTY = 600;
    private static final int ESCAPE_ROUTE_BONUS = 25;
    private static final int MOBILITY_BONUS = 3;
    private static final int COORDINATION_BONUS = 8;
    private static final int HEIGHT_BONUS = 5;

    /**
     * FIXED: Main evaluation function - no double perspective handling
     */
    public int evaluate(GameState state) {
        if (state == null) return 0;

        // Check for terminal positions first
        int terminalScore = checkTerminal(state);
        if (terminalScore != 0) {
            // Terminal score is already from the correct perspective
            return state.redToMove ? terminalScore : -terminalScore;
        }

        int score = 0;

        // Material and positional evaluation (from Red's perspective)
        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            int rank = GameState.rank(square);
            int file = GameState.file(square);

            // Red towers (positive contribution)
            if (state.redStackHeights[square] > 0) {
                int height = state.redStackHeights[square];
                score += evaluateTower(height, rank, file, true);
            }

            // Blue towers (negative contribution)
            if (state.blueStackHeights[square] > 0) {
                int height = state.blueStackHeights[square];
                score -= evaluateTower(height, rank, file, false);
            }
        }

        // Guard evaluation (from Red's perspective)
        score += evaluateGuards(state);

        // FIXED: Return score from current player's perspective
        return state.redToMove ? score : -score;
    }

    /**
     * Terminal position check - returns score from Red's perspective
     */
    public int checkTerminal(GameState state) {
        if (state == null) return 0;

        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE;  // Red lost
        if (state.blueGuard == 0) return GUARD_CAPTURE;   // Blue lost

        // Guard reached target castle
        if ((state.redGuard & GameState.bit(RED_TARGET_CASTLE)) != 0) {
            return CASTLE_REACH;  // Red guard reached D1 - Red wins
        }
        if ((state.blueGuard & GameState.bit(BLUE_TARGET_CASTLE)) != 0) {
            return -CASTLE_REACH; // Blue guard reached D7 - Blue wins
        }

        return 0; // Not terminal
    }

    /**
     * FIXED: Evaluate a single tower with corrected position bonuses
     */
    private int evaluateTower(int height, int rank, int file, boolean isRed) {
        int score = height * TOWER_VALUE;

        // Position bonuses
        score += getPositionBonus(rank, file, height, isRed);

        // Height bonus (taller towers are more valuable)
        if (height >= 3) {
            score += HEIGHT_BONUS * height;
        }

        return score;
    }

    /**
     * FIXED: Combined position bonus calculation
     */
    private int getPositionBonus(int rank, int file, int height, boolean isRed) {
        int bonus = 0;

        // Advancement bonus (moving toward opponent's side)
        if (isRed && rank < 3) {
            // Red advancing toward blue (lower ranks)
            bonus += (3 - rank) * height * ADVANCEMENT_BONUS;
        } else if (!isRed && rank > 3) {
            // Blue advancing toward red (higher ranks)
            bonus += (rank - 3) * height * ADVANCEMENT_BONUS;
        }

        // D-file control (critical for guard advancement)
        if (file == 3) {
            bonus += height * D_FILE_BONUS;
        }

        // Central files (C, D, E files are important)
        if (file >= 2 && file <= 4) {
            bonus += height * CENTRAL_BONUS;
        }

        // FIXED: Additional center bonus for D-file
        if (file == 3) {
            bonus += height * CENTRAL_BONUS; // Extra bonus for D-file
        }

        return bonus;
    }

    /**
     * Evaluate both guards (returns score from Red's perspective)
     */
    private int evaluateGuards(GameState state) {
        int score = 0;

        // Red guard evaluation (positive)
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            score += evaluateGuardPosition(guardPos, RED_TARGET_CASTLE, true);
            score += evaluateGuardSafety(state, guardPos, true);
        }

        // Blue guard evaluation (negative)
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score -= evaluateGuardPosition(guardPos, BLUE_TARGET_CASTLE, false);
            score -= evaluateGuardSafety(state, guardPos, false);
        }

        return score;
    }

    /**
     * Evaluate guard advancement toward target castle
     */
    private int evaluateGuardPosition(int guardPos, int targetCastle, boolean isRed) {
        int guardRank = GameState.rank(guardPos);
        int guardFile = GameState.file(guardPos);
        int targetRank = GameState.rank(targetCastle);
        int targetFile = GameState.file(targetCastle);

        // Manhattan distance to target
        int distance = Math.abs(guardRank - targetRank) + Math.abs(guardFile - targetFile);
        int maxDistance = 12; // Maximum possible distance on 7x7 board

        int score = (maxDistance - distance) * GUARD_DISTANCE_BONUS;

        // Big bonus for D-file (direct path to castle)
        if (guardFile == 3) {
            score += D_FILE_GUARD_BONUS;
        }

        // Extra bonus for being very close to target
        if (distance <= 2) {
            score += CASTLE_PROXIMITY_BONUS;
        }

        // Small penalty for being far from center file
        int fileDistance = Math.abs(guardFile - 3);
        score -= fileDistance * 15;

        return score;
    }

    /**
     * Evaluate guard safety
     */
    private int evaluateGuardSafety(GameState state, int guardPos, boolean isRed) {
        int score = 0;

        // Check if guard is threatened
        if (isGuardThreatened(state, isRed)) {
            score -= SAFETY_PENALTY;
        }

        // Count escape routes
        score += countEscapeRoutes(state, guardPos, isRed) * ESCAPE_ROUTE_BONUS;

        // Basic mobility
        score += approximateMobility(state, guardPos, isRed) * MOBILITY_BONUS;

        // Coordination with nearby towers
        score += countNearbyFriendlyTowers(state, guardPos, isRed) * COORDINATION_BONUS;

        return score;
    }

    /**
     * Check if guard is under immediate attack
     */
    public boolean isGuardThreatened(GameState state, boolean redGuard) {
        if (state == null) return false;

        long guardBit = redGuard ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check if any enemy tower can attack this square
        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            int enemyHeight = redGuard ? state.blueStackHeights[square] : state.redStackHeights[square];
            if (enemyHeight > 0 && canTowerAttack(square, guardPos, enemyHeight)) {
                return true;
            }
        }

        // Check if enemy guard can attack (adjacent)
        long enemyGuard = redGuard ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            if (isAdjacent(enemyGuardPos, guardPos)) {
                return true;
            }
        }

        return false;
    }

    // === HELPER METHODS ===

    private int countEscapeRoutes(GameState state, int guardPos, boolean isRed) {
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE}; // left, right, up, down

        for (int dir : directions) {
            int target = guardPos + dir;
            if (isValidSquare(guardPos, target, dir) && isSquareEmpty(state, target)) {
                escapeRoutes++;
            }
        }

        return escapeRoutes;
    }

    private int approximateMobility(GameState state, int guardPos, boolean isRed) {
        int mobility = 0;
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

        for (int dir : directions) {
            int target = guardPos + dir;
            if (isValidSquare(guardPos, target, dir)) {
                if (isSquareEmpty(state, target) || isEnemyPiece(state, target, isRed)) {
                    mobility++;
                }
            }
        }

        return mobility;
    }

    private int countNearbyFriendlyTowers(GameState state, int guardPos, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE,
                -GameState.BOARD_SIZE-1, -GameState.BOARD_SIZE+1,
                GameState.BOARD_SIZE-1, GameState.BOARD_SIZE+1}; // All 8 directions

        for (int dir : directions) {
            int adjacent = guardPos + dir;
            if (isValidSquare(guardPos, adjacent, dir)) {
                if (adjacent >= 0 && adjacent < GameState.NUM_SQUARES) {
                    if (isRed && state.redStackHeights[adjacent] > 0) {
                        count++;
                    } else if (!isRed && state.blueStackHeights[adjacent] > 0) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private boolean canTowerAttack(int fromSquare, int toSquare, int range) {
        int fromRank = GameState.rank(fromSquare);
        int fromFile = GameState.file(fromSquare);
        int toRank = GameState.rank(toSquare);
        int toFile = GameState.file(toSquare);

        // Must be on same rank or file (orthogonal)
        if (fromRank != toRank && fromFile != toFile) return false;

        // Must be within range
        int distance = Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
        return distance <= range;
    }

    private boolean isAdjacent(int square1, int square2) {
        int rank1 = GameState.rank(square1);
        int file1 = GameState.file(square1);
        int rank2 = GameState.rank(square2);
        int file2 = GameState.file(square2);
        int rankDiff = Math.abs(rank1 - rank2);
        int fileDiff = Math.abs(file1 - file2);
        return (rankDiff == 1 && fileDiff == 0) || (rankDiff == 0 && fileDiff == 1);
    }

    private boolean isValidSquare(int from, int to, int direction) {
        if (!GameState.isOnBoard(to)) return false;

        // Check for rank wrapping on horizontal moves
        if (Math.abs(direction) == 1) {
            return GameState.rank(from) == GameState.rank(to); // Same rank
        }

        return true;
    }

    private boolean isSquareEmpty(GameState state, int square) {
        if (!GameState.isOnBoard(square)) return false;
        return state.redStackHeights[square] == 0 &&
                state.blueStackHeights[square] == 0 &&
                (state.redGuard & GameState.bit(square)) == 0 &&
                (state.blueGuard & GameState.bit(square)) == 0;
    }

    private boolean isEnemyPiece(GameState state, int square, boolean weAreRed) {
        if (!GameState.isOnBoard(square)) return false;
        if (weAreRed) {
            return state.blueStackHeights[square] > 0 ||
                    (state.blueGuard & GameState.bit(square)) != 0;
        } else {
            return state.redStackHeights[square] > 0 ||
                    (state.redGuard & GameState.bit(square)) != 0;
        }
    }

    /**
     * FIXED: Evaluation breakdown with correct calculations
     */
    public String getEvaluationBreakdown(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EVALUATION BREAKDOWN ===\n");

        if (state == null) {
            sb.append("ERROR: null state\n");
            return sb.toString();
        }

        int terminalScore = checkTerminal(state);
        if (terminalScore != 0) {
            sb.append("Terminal position: ").append(terminalScore).append("\n");
            sb.append("From current player perspective: ");
            sb.append(state.redToMove ? terminalScore : -terminalScore).append("\n");
            return sb.toString();
        }

        int materialScore = 0;
        int positionScore = 0;
        int guardScore = 0;

        // Calculate components (from Red's perspective)
        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            if (state.redStackHeights[square] > 0) {
                int height = state.redStackHeights[square];
                materialScore += height * TOWER_VALUE;
                positionScore += getPositionBonus(GameState.rank(square), GameState.file(square), height, true);
            }
            if (state.blueStackHeights[square] > 0) {
                int height = state.blueStackHeights[square];
                materialScore -= height * TOWER_VALUE;
                positionScore -= getPositionBonus(GameState.rank(square), GameState.file(square), height, false);
            }
        }

        guardScore = evaluateGuards(state);

        int totalFromRed = materialScore + positionScore + guardScore;
        int totalFromCurrent = state.redToMove ? totalFromRed : -totalFromRed;

        sb.append(String.format("Material (Red perspective):  %+6d\n", materialScore));
        sb.append(String.format("Position (Red perspective):  %+6d\n", positionScore));
        sb.append(String.format("Guards (Red perspective):    %+6d\n", guardScore));
        sb.append("========================================\n");
        sb.append(String.format("Total (Red perspective):     %+6d\n", totalFromRed));
        sb.append(String.format("Total (Current perspective): %+6d\n", totalFromCurrent));
        sb.append(String.format("Current player: %s\n", state.redToMove ? "Red" : "Blue"));

        return sb.toString();
    }

    /**
     * Quick evaluation for performance testing
     */
    public int evaluateQuick(GameState state) {
        if (state == null) return 0;

        // Check terminal first
        int terminalScore = checkTerminal(state);
        if (terminalScore != 0) {
            return state.redToMove ? terminalScore : -terminalScore;
        }

        int score = 0;

        // Material only (simplified)
        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            score += (state.redStackHeights[square] - state.blueStackHeights[square]) * TOWER_VALUE;
        }

        // Basic guard distance (simplified)
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int guardRank = GameState.rank(guardPos);
            int guardFile = GameState.file(guardPos);
            int targetRank = GameState.rank(RED_TARGET_CASTLE);
            int targetFile = GameState.file(RED_TARGET_CASTLE);
            int distance = Math.abs(guardRank - targetRank) + Math.abs(guardFile - targetFile);
            score += (12 - distance) * (GUARD_DISTANCE_BONUS / 2);
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int guardRank = GameState.rank(guardPos);
            int guardFile = GameState.file(guardPos);
            int targetRank = GameState.rank(BLUE_TARGET_CASTLE);
            int targetFile = GameState.file(BLUE_TARGET_CASTLE);
            int distance = Math.abs(guardRank - targetRank) + Math.abs(guardFile - targetFile);
            score -= (12 - distance) * (GUARD_DISTANCE_BONUS / 2);
        }

        // Return from current player's perspective
        return state.redToMove ? score : -score;
    }

    // === DEBUG METHODS ===

    public String getDebugInfo(GameState state) {
        if (state == null) return "NULL STATE";

        StringBuilder sb = new StringBuilder();
        sb.append("=== EVALUATOR DEBUG INFO ===\n");

        // Guard positions
        if (state.redGuard != 0) {
            int redPos = Long.numberOfTrailingZeros(state.redGuard);
            sb.append("Red guard: ").append(getSquareName(redPos)).append(" (index ").append(redPos).append(")\n");
        } else {
            sb.append("Red guard: CAPTURED\n");
        }

        if (state.blueGuard != 0) {
            int bluePos = Long.numberOfTrailingZeros(state.blueGuard);
            sb.append("Blue guard: ").append(getSquareName(bluePos)).append(" (index ").append(bluePos).append(")\n");
        } else {
            sb.append("Blue guard: CAPTURED\n");
        }

        // Castle info
        sb.append("Red target: ").append(getSquareName(RED_TARGET_CASTLE)).append(" (index ").append(RED_TARGET_CASTLE).append(")\n");
        sb.append("Blue target: ").append(getSquareName(BLUE_TARGET_CASTLE)).append(" (index ").append(BLUE_TARGET_CASTLE).append(")\n");

        // Terminal check
        int terminal = checkTerminal(state);
        sb.append("Terminal score (Red perspective): ").append(terminal).append("\n");

        // Full evaluation
        int fullEval = evaluate(state);
        sb.append("Full evaluation (current perspective): ").append(fullEval).append("\n");

        sb.append("Side to move: ").append(state.redToMove ? "RED" : "BLUE").append("\n");

        return sb.toString();
    }

    private String getSquareName(int index) {
        int rank = GameState.rank(index);
        int file = GameState.file(index);
        return "" + (char)('A' + file) + (rank + 1);
    }
}