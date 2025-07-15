package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;
import java.util.List;

/**
 * UNIFIED EVALUATOR for Turm & Wächter
 *
 * Combines core strategic evaluation with advanced competitive features.
 *
 * Core Strategic Elements (85% weight):
 * 1. Material (60%) - Tower count, height, and position
 * 2. Guard Advancement (25%) - Progress toward enemy castle
 * 3. Guard Safety (10%) - Immediate threat detection
 * 4. Piece Activity (5%) - Mobility and coordination
 *
 * Enhanced Features (15% weight):
 * 1. Height-based mobility bonuses
 * 2. Tower cluster analysis
 * 3. Path blocking evaluation
 * 4. Castle approach control
 * 5. Guard escort bonuses
 * 6. Stack efficiency evaluation
 *
 */
public class Evaluator {

    // === CORE GAME VALUES ===
    private static final int TOWER_VALUE = 100;           // Base value per tower piece
    private static final int GUARD_CAPTURE = 10000;       // Winning by capturing guard
    private static final int CASTLE_REACH = 10000;        // Winning by reaching castle

    // === CASTLE POSITIONS ===
    private static final int RED_CASTLE = GameState.getIndex(6, 3);  // D7
    private static final int BLUE_CASTLE = GameState.getIndex(0, 3); // D1

    // === CORE EVALUATION WEIGHTS ===
    private static final int CORE_WEIGHT = 85;            // Weight for core evaluation
    private static final int ENHANCED_WEIGHT = 15;        // Weight for enhanced features

    private static final int MATERIAL_WEIGHT = 60;
    private static final int ADVANCEMENT_WEIGHT = 25;
    private static final int SAFETY_WEIGHT = 10;
    private static final int ACTIVITY_WEIGHT = 5;

    // === CORE PARAMETERS ===
    private static final int ADVANCEMENT_BONUS = 5;       // Bonus per rank advanced per tower height
    private static final int D_FILE_BONUS = 8;           // Bonus for controlling D-file
    private static final int CENTRAL_BONUS = 3;          // Bonus for central files (C,D,E)
    private static final int GUARD_DISTANCE_BONUS = 50;  // Bonus per step closer to enemy castle
    private static final int D_FILE_GUARD_BONUS = 100;   // Bonus for guard on D-file
    private static final int CASTLE_PROXIMITY_BONUS = 200; // Bonus when guard very close to castle
    private static final int SAFETY_PENALTY = 500;       // Penalty when guard threatened
    private static final int ESCAPE_ROUTE_BONUS = 20;    // Bonus per escape route
    private static final int MOBILITY_BONUS = 2;         // Bonus per legal move
    private static final int COORDINATION_BONUS = 5;     // Bonus per adjacent friendly piece

    // === ENHANCED PARAMETERS ===
    private static final int HEIGHT_MOBILITY_BONUS = 3;    // Bonus per height per possible direction
    private static final int CLUSTER_REACH_BONUS = 8;      // Bonus when towers can reach each other
    private static final int PATH_BLOCKING_BONUS = 15;     // Bonus for blocking enemy guard path
    private static final int CASTLE_APPROACH_CONTROL = 25; // Bonus for controlling squares near enemy castle
    private static final int GUARD_ESCORT_BONUS = 12;      // Bonus for towers escorting advancing guard
    private static final int STACK_EFFICIENCY_BONUS = 6;   // Bonus for optimal stack heights

    /**
     * Main evaluation function
     */
    public int evaluate(GameState state) {
        // Check for terminal positions first
        int terminalScore = checkTerminalPosition(state);
        if (terminalScore != 0) {
            return terminalScore;
        }

        // Calculate core evaluation components
        int materialScore = evaluateMaterial(state);
        int advancementScore = evaluateGuardAdvancement(state);
        int safetyScore = evaluateGuardSafety(state);
        int activityScore = evaluatePieceActivity(state);

        // Weighted combination of core components
        int coreScore = (materialScore * MATERIAL_WEIGHT +
                advancementScore * ADVANCEMENT_WEIGHT +
                safetyScore * SAFETY_WEIGHT +  activityScore * ACTIVITY_WEIGHT) / 100;


        // Calculate enhanced features
        int enhancedScore = 0;
        enhancedScore += evaluateHeightMobility(state);
        enhancedScore += evaluateTowerClusters(state);
        enhancedScore += evaluatePathBlocking(state);
        enhancedScore += evaluateCastleApproachControl(state);
        enhancedScore += evaluateGuardEscort(state);
        enhancedScore += evaluateStackEfficiency(state);


        // Final weighted combination
        return (coreScore * CORE_WEIGHT + enhancedScore * ENHANCED_WEIGHT) / 100;
    }

    /**
     * Check for game-ending positions
     */
    private int checkTerminalPosition(GameState state) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE;
        if (state.blueGuard == 0) return GUARD_CAPTURE;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(BLUE_CASTLE)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(RED_CASTLE)) != 0;

        if (redWins) return CASTLE_REACH;
        if (blueWins) return -CASTLE_REACH;

        return 0;
    }

    // ========================================
    // CORE EVALUATION COMPONENTS
    // ========================================

    /**
     * 1. MATERIAL EVALUATION (60% of core weight)
     * Evaluate material with position-based bonuses
     */
    private int evaluateMaterial(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red towers
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                score += height * TOWER_VALUE;
                score += getMaterialPositionBonus(i, height, true);
            }

            // Blue towers
            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                score -= height * TOWER_VALUE;
                score -= getMaterialPositionBonus(i, height, false);
            }
        }

        return score;
    }

    /**
     * Position-based bonuses for towers
     */
    private int getMaterialPositionBonus(int square, int height, boolean isRed) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);
        int bonus = 0;

        // Advancement bonus: towers are more valuable when advanced
        if (isRed && rank < 4) {
            bonus += (4 - rank) * height * ADVANCEMENT_BONUS;
        } else if (!isRed && rank > 2) {
            bonus += (rank - 2) * height * ADVANCEMENT_BONUS;
        }

        // D-file control: critical for guard advancement
        if (file == 3) {
            bonus += height * D_FILE_BONUS;
        }

        // Central files: generally more active
        if (file >= 2 && file <= 4) {
            bonus += height * CENTRAL_BONUS;
        }

        return bonus;
    }

    /**
     * 2. GUARD ADVANCEMENT (25% of core weight)
     * Evaluate guard progress toward enemy castle
     */
    private int evaluateGuardAdvancement(GameState state) {
        int score = 0;

        // Red guard advancement toward D1
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            score += evaluateGuardProgress(guardPos, BLUE_CASTLE);
        }

        // Blue guard advancement toward D7
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score -= evaluateGuardProgress(guardPos, RED_CASTLE);
        }

        return score;
    }

    /**
     * Evaluate how well-positioned a guard is for advancing
     */
    private int evaluateGuardProgress(int guardPos, int targetCastle) {
        int guardRank = GameState.rank(guardPos);
        int guardFile = GameState.file(guardPos);
        int targetRank = GameState.rank(targetCastle);
        int targetFile = GameState.file(targetCastle);

        // Manhattan distance to target castle
        int distance = Math.abs(guardRank - targetRank) + Math.abs(guardFile - targetFile);
        int maxDistance = 12; // Maximum possible distance

        // Base score: closer is better
        int score = (maxDistance - distance) * GUARD_DISTANCE_BONUS;

        // Big bonus for being on D-file (direct path)
        if (guardFile == 3) {
            score += D_FILE_GUARD_BONUS;
        }

        // Extra bonus for being very close to castle
        if (distance <= 2) {
            score += CASTLE_PROXIMITY_BONUS;
        }

        // Small penalty for being too far from D-file
        int fileDistance = Math.abs(guardFile - 3);
        score -= fileDistance * 10;

        return score;
    }

    /**
     * 3. GUARD SAFETY (10% of core weight)
     * Evaluate guard safety from immediate threats
     */
    private int evaluateGuardSafety(GameState state) {
        int score = 0;

        // Check if guards are under immediate attack
        if (isGuardThreatened(state, true)) {
            score -= SAFETY_PENALTY;
        }

        if (isGuardThreatened(state, false)) {
            score += SAFETY_PENALTY; // Good for red if blue guard threatened
        }

        // Bonus for guards with escape routes
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            score += countEscapeRoutes(state, guardPos, true) * ESCAPE_ROUTE_BONUS;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score -= countEscapeRoutes(state, guardPos, false) * ESCAPE_ROUTE_BONUS;
        }

        return score;
    }

    /**
     * Check if a guard is under immediate attack
     */
    private boolean isGuardThreatened(GameState state, boolean redGuard) {
        long guardBit = redGuard ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check if any enemy piece can attack the guard next move
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Check enemy towers
            int enemyHeight = redGuard ? state.blueStackHeights[i] : state.redStackHeights[i];
            if (enemyHeight > 0 && canAttackSquare(i, guardPos, enemyHeight)) {
                return true;
            }

            // Check enemy guard (can attack adjacent squares)
            long enemyGuard = redGuard ? state.blueGuard : state.redGuard;
            if (enemyGuard != 0 && i == Long.numberOfTrailingZeros(enemyGuard)) {
                if (isAdjacent(i, guardPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Count available escape routes for a guard
     */
    private int countEscapeRoutes(GameState state, int guardPos, boolean isRed) {
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -7, 7}; // left, right, up, down

        for (int dir : directions) {
            int target = guardPos + dir;

            // Check bounds and rank wrapping
            if (!isValidMove(guardPos, target, dir)) continue;

            // Check if square is empty
            if (isSquareEmpty(state, target)) {
                escapeRoutes++;
            }
        }

        return escapeRoutes;
    }

    /**
     * 4. PIECE ACTIVITY (5% of core weight)
     * Evaluate piece mobility and coordination
     */
    private int evaluatePieceActivity(GameState state) {
        int score = 0;

        // Mobility: count legal moves (simple approximation)
        score += evaluateMobility(state);

        // Coordination: pieces supporting each other
        score += evaluateCoordination(state);

        return score;
    }

    /**
     * Simple mobility evaluation
     */
    private int evaluateMobility(GameState state) {
        try {
            // Generate moves to get mobility count
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            int mobilityScore = Math.min(moves.size() * MOBILITY_BONUS, 100); // Cap at 100

            return state.redToMove ? mobilityScore : -mobilityScore;
        } catch (Exception e) {
            // Fallback if move generation fails
            return 0;
        }
    }

    /**
     * Evaluate piece coordination (adjacent friendly pieces)
     */
    private int evaluateCoordination(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red pieces
            if (state.redStackHeights[i] > 0 || (state.redGuard & GameState.bit(i)) != 0) {
                score += countAdjacentFriendly(state, i, true) * COORDINATION_BONUS;
            }

            // Blue pieces
            if (state.blueStackHeights[i] > 0 || (state.blueGuard & GameState.bit(i)) != 0) {
                score -= countAdjacentFriendly(state, i, false) * COORDINATION_BONUS;
            }
        }

        return score;
    }

    /**
     * Count adjacent friendly pieces
     */
    private int countAdjacentFriendly(GameState state, int square, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = square + dir;
            if (!isValidMove(square, adjacent, dir)) continue;

            if (isRed) {
                if (state.redStackHeights[adjacent] > 0 ||
                        (state.redGuard & GameState.bit(adjacent)) != 0) {
                    count++;
                }
            } else {
                if (state.blueStackHeights[adjacent] > 0 ||
                        (state.blueGuard & GameState.bit(adjacent)) != 0) {
                    count++;
                }
            }
        }

        return count;
    }

    // ========================================
    // ENHANCED EVALUATION FEATURES
    // ========================================

    /**
     * HEIGHT-BASED MOBILITY: Higher towers are more valuable due to increased mobility
     */
    private int evaluateHeightMobility(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red towers
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                int mobilityValue = height * countMobilityDirections(state, i, height) * HEIGHT_MOBILITY_BONUS;
                score += mobilityValue;
            }

            // Blue towers
            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                int mobilityValue = height * countMobilityDirections(state, i, height) * HEIGHT_MOBILITY_BONUS;
                score -= mobilityValue;
            }
        }

        return score;
    }

    /**
     * Count directions where a tower can move (not blocked immediately)
     */
    private int countMobilityDirections(GameState state, int square, int height) {
        int directions = 0;
        int[] dirArray = {-1, 1, -7, 7}; // left, right, up, down

        for (int dir : dirArray) {
            int target = square + dir;
            if (isValidMove(square, target, dir)) {
                // Check if can move at least one square in this direction
                if (isSquareEmpty(state, target) || isEnemyPiece(state, target, square)) {
                    directions++;
                }
            }
        }

        return directions;
    }

    /**
     * TOWER CLUSTERS: Bonus for towers that can reach each other
     */
    private int evaluateTowerClusters(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red clusters
            if (state.redStackHeights[i] > 0) {
                int reachableTowers = countReachableFriendlyTowers(state, i, true);
                score += reachableTowers * CLUSTER_REACH_BONUS;
            }

            // Blue clusters
            if (state.blueStackHeights[i] > 0) {
                int reachableTowers = countReachableFriendlyTowers(state, i, false);
                score -= reachableTowers * CLUSTER_REACH_BONUS;
            }
        }

        return score;
    }

    /**
     * Count friendly towers this piece can reach in one move
     */
    private int countReachableFriendlyTowers(GameState state, int square, boolean isRed) {
        int reachable = 0;
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int target = square + dir * dist;
                if (!isValidMove(square, target, dir)) break;

                // Found friendly tower?
                if ((isRed && state.redStackHeights[target] > 0) ||
                        (!isRed && state.blueStackHeights[target] > 0)) {
                    reachable++;
                    break;
                }

                // Path blocked?
                if (!isSquareEmpty(state, target)) break;
            }
        }

        return reachable;
    }

    /**
     * PATH BLOCKING: Bonus for controlling squares that block enemy guard advancement
     */
    private int evaluatePathBlocking(GameState state) {
        int score = 0;

        // Red blocking blue guard path to D7
        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score += evaluatePathBlockingForGuard(state, blueGuardPos, RED_CASTLE, true);
        }

        // Blue blocking red guard path to D1
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            score -= evaluatePathBlockingForGuard(state, redGuardPos, BLUE_CASTLE, false);
        }

        return score;
    }

    /**
     * Evaluate how well a player is blocking enemy guard's path to castle
     */
    private int evaluatePathBlockingForGuard(GameState state, int guardPos, int targetCastle, boolean blockingPlayer) {
        int blockingScore = 0;

        // Key squares on path to castle (simplified - D-file squares)
        int guardRank = GameState.rank(guardPos);
        int targetRank = GameState.rank(targetCastle);

        int startRank = Math.min(guardRank, targetRank);
        int endRank = Math.max(guardRank, targetRank);

        for (int rank = startRank; rank <= endRank; rank++) {
            int square = GameState.getIndex(rank, 3); // D-file squares

            // Check if blocking player controls this square
            if (blockingPlayer) {
                if (state.redStackHeights[square] > 0) {
                    blockingScore += PATH_BLOCKING_BONUS;
                }
            } else {
                if (state.blueStackHeights[square] > 0) {
                    blockingScore += PATH_BLOCKING_BONUS;
                }
            }
        }

        return blockingScore;
    }

    /**
     * CASTLE APPROACH CONTROL: Bonus for controlling squares near enemy castle
     */
    private int evaluateCastleApproachControl(GameState state) {
        int score = 0;

        // Squares around blue castle (D1)
        int[] blueCastleApproach = {
                GameState.getIndex(0, 2), GameState.getIndex(0, 4), // C1, E1
                GameState.getIndex(1, 3), // D2
                GameState.getIndex(1, 2), GameState.getIndex(1, 4)  // C2, E2
        };

        // Red controlling blue castle approach
        for (int square : blueCastleApproach) {
            if (square >= 0 && square < GameState.NUM_SQUARES) {
                if (state.redStackHeights[square] > 0) {
                    score += CASTLE_APPROACH_CONTROL;
                }
            }
        }

        // Squares around red castle (D7)
        int[] redCastleApproach = {
                GameState.getIndex(6, 2), GameState.getIndex(6, 4), // C7, E7
                GameState.getIndex(5, 3), // D6
                GameState.getIndex(5, 2), GameState.getIndex(5, 4)  // C6, E6
        };

        // Blue controlling red castle approach
        for (int square : redCastleApproach) {
            if (square >= 0 && square < GameState.NUM_SQUARES) {
                if (state.blueStackHeights[square] > 0) {
                    score -= CASTLE_APPROACH_CONTROL;
                }
            }
        }

        return score;
    }

    /**
     * GUARD ESCORT: Bonus for towers escorting advancing guard
     */
    private int evaluateGuardEscort(GameState state) {
        int score = 0;

        // Red guard escort
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);

            // Only count escort if guard is advancing (past middle)
            if (rank <= 3) {
                int escortTowers = countEscortTowers(state, guardPos, true);
                score += escortTowers * GUARD_ESCORT_BONUS;
            }
        }

        // Blue guard escort
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);

            // Only count escort if guard is advancing (past middle)
            if (rank >= 3) {
                int escortTowers = countEscortTowers(state, guardPos, false);
                score -= escortTowers * GUARD_ESCORT_BONUS;
            }
        }

        return score;
    }

    /**
     * Count towers providing escort to advancing guard
     */
    private int countEscortTowers(GameState state, int guardPos, boolean isRed) {
        int escorts = 0;

        // Check 2-square radius around guard
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(guardPos, i);
                if (distance <= 2) {
                    escorts++;
                }
            }
        }

        return escorts;
    }

    /**
     * STACK EFFICIENCY: Bonus for optimal stack heights (not too high, not too spread)
     */
    private int evaluateStackEfficiency(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                // Bonus for medium-height stacks (2-4)
                int height = state.redStackHeights[i];
                if (height >= 2 && height <= 4) {
                    score += STACK_EFFICIENCY_BONUS;
                }
            }

            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                if (height >= 2 && height <= 4) {
                    score -= STACK_EFFICIENCY_BONUS;
                }
            }
        }

        return score;
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Check if a piece can attack a square
     */
    private boolean canAttackSquare(int fromSquare, int toSquare, int range) {
        int rankDiff = Math.abs(GameState.rank(fromSquare) - GameState.rank(toSquare));
        int fileDiff = Math.abs(GameState.file(fromSquare) - GameState.file(toSquare));

        // Must be on same rank or file (orthogonal movement)
        if (rankDiff != 0 && fileDiff != 0) return false;

        // Must be within range
        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    /**
     * Check if two squares are adjacent
     */
    private boolean isAdjacent(int square1, int square2) {
        int rankDiff = Math.abs(GameState.rank(square1) - GameState.rank(square2));
        int fileDiff = Math.abs(GameState.file(square1) - GameState.file(square2));
        return (rankDiff == 1 && fileDiff == 0) || (rankDiff == 0 && fileDiff == 1);
    }

    /**
     * Check if a move is valid (bounds and rank wrapping)
     */
    private boolean isValidMove(int from, int to, int direction) {
        if (to < 0 || to >= GameState.NUM_SQUARES) return false;

        // Check for rank wrapping on horizontal moves
        if (Math.abs(direction) == 1) {
            return GameState.rank(from) == GameState.rank(to);
        }

        return true;
    }

    /**
     * Check if a square is empty
     */
    private boolean isSquareEmpty(GameState state, int square) {
        return state.redStackHeights[square] == 0 &&
                state.blueStackHeights[square] == 0 &&
                (state.redGuard & GameState.bit(square)) == 0 &&
                (state.blueGuard & GameState.bit(square)) == 0;
    }

    /**
     * Check if a piece on a square is an enemy piece relative to the piece on fromSquare
     */
    private boolean isEnemyPiece(GameState state, int square, int fromSquare) {
        // Determine if fromSquare has red or blue piece
        boolean fromIsRed = state.redStackHeights[fromSquare] > 0 ||
                (state.redGuard & GameState.bit(fromSquare)) != 0;

        if (fromIsRed) {
            return state.blueStackHeights[square] > 0 ||
                    (state.blueGuard & GameState.bit(square)) != 0;
        } else {
            return state.redStackHeights[square] > 0 ||
                    (state.redGuard & GameState.bit(square)) != 0;
        }
    }

    /**
     * Calculate Manhattan distance between two squares
     */
    private int calculateManhattanDistance(int from, int to) {
        return Math.abs(GameState.rank(from) - GameState.rank(to)) +
                Math.abs(GameState.file(from) - GameState.file(to));
    }

    // ========================================
    // DEBUGGING AND ANALYSIS METHODS
    // ========================================

    /**
     * Get detailed evaluation breakdown for position analysis
     */
    public String getEvaluationBreakdown(GameState state) {
        // Core evaluation components
        int material = evaluateMaterial(state);
        int advancement = evaluateGuardAdvancement(state);
        int safety = evaluateGuardSafety(state);
        int activity = evaluatePieceActivity(state);

        int coreTotal = (material * MATERIAL_WEIGHT +
                advancement * ADVANCEMENT_WEIGHT +
                safety * SAFETY_WEIGHT +
                activity * ACTIVITY_WEIGHT) / 100;

        // Enhanced evaluation components
        int heightMobility = evaluateHeightMobility(state);
        int clusters = evaluateTowerClusters(state);
        int pathBlocking = evaluatePathBlocking(state);
        int castleControl = evaluateCastleApproachControl(state);
        int guardEscort = evaluateGuardEscort(state);
        int stackEfficiency = evaluateStackEfficiency(state);

        int enhancedTotal = heightMobility + clusters + pathBlocking +
                castleControl + guardEscort + stackEfficiency;

        int finalScore = (coreTotal * CORE_WEIGHT + enhancedTotal * ENHANCED_WEIGHT) / 100;

        StringBuilder sb = new StringBuilder();
        sb.append("=== CORE EVALUATION BREAKDOWN ===\n");
        sb.append(String.format("Material:     %+5d × %2d%% = %+6d\n",
                material, MATERIAL_WEIGHT, material * MATERIAL_WEIGHT / 100));
        sb.append(String.format("Advancement:  %+5d × %2d%% = %+6d\n",
                advancement, ADVANCEMENT_WEIGHT, advancement * ADVANCEMENT_WEIGHT / 100));
        sb.append(String.format("Safety:       %+5d × %2d%% = %+6d\n",
                safety, SAFETY_WEIGHT, safety * SAFETY_WEIGHT / 100));
        sb.append(String.format("Activity:     %+5d × %2d%% = %+6d\n",
                activity, ACTIVITY_WEIGHT, activity * ACTIVITY_WEIGHT / 100));
        sb.append("==================================\n");
        sb.append(String.format("Core Total:   %+5d × %2d%% = %+6d\n",
                coreTotal, CORE_WEIGHT, coreTotal * CORE_WEIGHT / 100));

        sb.append("\n=== ENHANCED FEATURES BREAKDOWN ===\n");
        sb.append(String.format("Height Mobility:  %+5d\n", heightMobility));
        sb.append(String.format("Tower Clusters:   %+5d\n", clusters));
        sb.append(String.format("Path Blocking:    %+5d\n", pathBlocking));
        sb.append(String.format("Castle Control:   %+5d\n", castleControl));
        sb.append(String.format("Guard Escort:     %+5d\n", guardEscort));
        sb.append(String.format("Stack Efficiency: %+5d\n", stackEfficiency));
        sb.append("====================================\n");
        sb.append(String.format("Enhanced Total:   %+5d × %2d%% = %+6d\n",
                enhancedTotal, ENHANCED_WEIGHT, enhancedTotal * ENHANCED_WEIGHT / 100));

        sb.append("\n=====================================\n");
        sb.append(String.format("FINAL SCORE:      %+6d\n", finalScore));

        return sb.toString();
    }


    /**
     * Quick evaluation for compatibility with existing interfaces
     */
    public int evaluate(GameState state, int depth) {
        return evaluate(state);
    }

    /**
     * Check if guard is in danger (for compatibility)
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        return isGuardThreatened(state, checkRed);
    }

    /**
     * Set remaining time for time-aware evaluation adjustments
     */
    public static void setRemainingTime(long timeMs) {
        // Adjust evaluation behavior based on remaining time
        if (timeMs < SearchConfig.EMERGENCY_TIME_MS) {
            // In emergency mode, use faster/simpler evaluation
            System.out.println("🚨 Emergency mode: Using simplified evaluation");
        }
        // You could store the time in a static field if needed for evaluation adjustments
    }
}