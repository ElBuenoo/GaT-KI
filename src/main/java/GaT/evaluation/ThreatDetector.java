package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import java.util.*;

/**
 * THREAT DETECTION SYSTEM - Explicit Defensive Analysis
 *
 * Systematically detects and evaluates threats in the position.
 * Essential for defensive play and avoiding tactical oversights.
 */
public class ThreatDetector {

    // === THREAT SEVERITY LEVELS ===
    public enum ThreatLevel {
        NONE(0),
        MINOR(50),
        MODERATE(150),
        SERIOUS(350),
        CRITICAL(800),
        FATAL(2000);

        public final int value;
        ThreatLevel(int value) { this.value = value; }
    }

    // === THREAT TYPES ===
    public enum ThreatType {
        GUARD_CAPTURE("Guard under attack"),
        CASTLE_THREAT("Enemy guard near castle"),
        MATERIAL_LOSS("Material under attack"),
        POSITIONAL_THREAT("Positional pressure"),
        TACTICAL_MOTIF("Tactical pattern"),
        ZUGZWANG("Forced into bad move");

        public final String description;
        ThreatType(String description) { this.description = description; }
    }

    // === THREAT ANALYSIS RESULT ===
    public static class ThreatAnalysis {
        public final List<DetectedThreat> threats;
        public final ThreatLevel maxThreatLevel;
        public final int totalThreatScore;
        public final List<Move> defensiveMoves;
        public final boolean requiresImmediateAction;

        public ThreatAnalysis(List<DetectedThreat> threats, List<Move> defensiveMoves) {
            this.threats = threats;
            this.defensiveMoves = defensiveMoves;
            this.maxThreatLevel = threats.stream()
                    .map(t -> t.level)
                    .max(Comparator.comparingInt(l -> l.value))
                    .orElse(ThreatLevel.NONE);
            this.totalThreatScore = threats.stream()
                    .mapToInt(t -> t.level.value)
                    .sum();
            this.requiresImmediateAction = maxThreatLevel.value >= ThreatLevel.SERIOUS.value;
        }

        public boolean hasCriticalThreats() {
            return maxThreatLevel.value >= ThreatLevel.CRITICAL.value;
        }

        public boolean hasAnyThreats() {
            return !threats.isEmpty();
        }
    }

    // === INDIVIDUAL THREAT ===
    public static class DetectedThreat {
        public final ThreatType type;
        public final ThreatLevel level;
        public final int targetSquare;
        public final int attackerSquare;
        public final Move threatMove;
        public final String description;
        public final List<Move> defenses;

        public DetectedThreat(ThreatType type, ThreatLevel level, int targetSquare,
                              int attackerSquare, Move threatMove, String description,
                              List<Move> defenses) {
            this.type = type;
            this.level = level;
            this.targetSquare = targetSquare;
            this.attackerSquare = attackerSquare;
            this.threatMove = threatMove;
            this.description = description;
            this.defenses = defenses != null ? defenses : new ArrayList<>();
        }
    }

    // === MAIN THREAT ANALYSIS INTERFACE ===

    /**
     * COMPREHENSIVE THREAT ANALYSIS
     */
    public static ThreatAnalysis analyzeThreats(GameState state) {
        List<DetectedThreat> allThreats = new ArrayList<>();

        // 1. Guard threats (highest priority)
        allThreats.addAll(detectGuardThreats(state));

        // 2. Castle threats
        allThreats.addAll(detectCastleThreats(state));

        // 3. Material threats
        allThreats.addAll(detectMaterialThreats(state));

        // 4. Positional threats
        allThreats.addAll(detectPositionalThreats(state));

        // 5. Tactical motifs
        allThreats.addAll(detectTacticalMotifs(state));

        // Find defensive moves
        List<Move> defensiveMoves = findDefensiveMoves(state, allThreats);

        return new ThreatAnalysis(allThreats, defensiveMoves);
    }

    /**
     * QUICK THREAT CHECK - For time-critical situations
     */
    public static ThreatAnalysis quickThreatCheck(GameState state) {
        List<DetectedThreat> criticalThreats = new ArrayList<>();

        // Only check the most critical threats
        criticalThreats.addAll(detectGuardThreats(state));
        criticalThreats.addAll(detectCastleThreats(state));

        List<Move> defensiveMoves = findImmediateDefenses(state, criticalThreats);

        return new ThreatAnalysis(criticalThreats, defensiveMoves);
    }

    // === SPECIFIC THREAT DETECTION METHODS ===

    /**
     * DETECT GUARD THREATS - Highest priority
     */
    private static List<DetectedThreat> detectGuardThreats(GameState state) {
        List<DetectedThreat> threats = new ArrayList<>();
        boolean isRed = state.redToMove;

        // Check our guard
        long ourGuard = isRed ? state.redGuard : state.blueGuard;
        if (ourGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(ourGuard);
            List<Move> attackingMoves = findAttacksOnSquare(guardPos, state, !isRed);

            for (Move attack : attackingMoves) {
                ThreatLevel level = ThreatLevel.FATAL;
                String desc = String.format("Guard on %s under attack by %s",
                        squareName(guardPos), squareName(attack.from));

                List<Move> defenses = findGuardDefenses(state, guardPos, attack);

                threats.add(new DetectedThreat(ThreatType.GUARD_CAPTURE, level,
                        guardPos, attack.from, attack, desc, defenses));
            }
        }

        return threats;
    }

    /**
     * DETECT CASTLE THREATS
     */
    private static List<DetectedThreat> detectCastleThreats(GameState state) {
        List<DetectedThreat> threats = new ArrayList<>();
        boolean isRed = state.redToMove;

        int ourCastle = isRed ? GameState.getIndex(6, 3) : GameState.getIndex(0, 3); // D7 or D1
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            int distance = calculateManhattanDistance(enemyGuardPos, ourCastle);

            if (distance <= 3) {
                ThreatLevel level = distance <= 1 ? ThreatLevel.CRITICAL :
                        distance <= 2 ? ThreatLevel.SERIOUS : ThreatLevel.MODERATE;

                String desc = String.format("Enemy guard %d moves from our castle", distance);
                List<Move> defenses = findCastleDefenses(state, ourCastle);

                threats.add(new DetectedThreat(ThreatType.CASTLE_THREAT, level,
                        ourCastle, enemyGuardPos, null, desc, defenses));
            }
        }

        return threats;
    }

    /**
     * DETECT MATERIAL THREATS
     */
    private static List<DetectedThreat> detectMaterialThreats(GameState state) {
        List<DetectedThreat> threats = new ArrayList<>();
        boolean isRed = state.redToMove;

        // Find our pieces under attack
        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            int pieceValue = getPieceValue(square, state, isRed);
            if (pieceValue == 0) continue;

            List<Move> attacks = findAttacksOnSquare(square, state, !isRed);
            if (attacks.isEmpty()) continue;

            // Calculate if the piece is adequately defended
            int defenseValue = calculateDefenseValue(square, state, isRed);
            int attackValue = calculateAttackValue(attacks, state);

            if (attackValue > defenseValue) {
                ThreatLevel level = pieceValue >= 300 ? ThreatLevel.SERIOUS :
                        pieceValue >= 150 ? ThreatLevel.MODERATE : ThreatLevel.MINOR;

                String desc = String.format("Piece worth %d under insufficient protection", pieceValue);
                List<Move> defenses = findPieceDefenses(state, square);

                threats.add(new DetectedThreat(ThreatType.MATERIAL_LOSS, level,
                        square, attacks.get(0).from, attacks.get(0), desc, defenses));
            }
        }

        return threats;
    }

    /**
     * DETECT POSITIONAL THREATS
     */
    private static List<DetectedThreat> detectPositionalThreats(GameState state) {
        List<DetectedThreat> threats = new ArrayList<>();

        // Check for positional weaknesses
        if (hasWeakGuardPosition(state)) {
            threats.add(new DetectedThreat(ThreatType.POSITIONAL_THREAT, ThreatLevel.MODERATE,
                    -1, -1, null, "Guard in vulnerable position", new ArrayList<>()));
        }

        if (hasPoorPieceCoordination(state)) {
            threats.add(new DetectedThreat(ThreatType.POSITIONAL_THREAT, ThreatLevel.MINOR,
                    -1, -1, null, "Poor piece coordination", new ArrayList<>()));
        }

        return threats;
    }

    /**
     * DETECT TACTICAL MOTIFS (forks, pins, skewers)
     */
    private static List<DetectedThreat> detectTacticalMotifs(GameState state) {
        List<DetectedThreat> threats = new ArrayList<>();

        // Check for fork threats
        threats.addAll(detectForkThreats(state));

        // Check for pin/skewer threats
        threats.addAll(detectPinThreats(state));

        return threats;
    }

    // === DEFENSE FINDING METHODS ===

    /**
     * FIND DEFENSIVE MOVES for detected threats
     */
    private static List<Move> findDefensiveMoves(GameState state, List<DetectedThreat> threats) {
        Set<Move> defensiveMoves = new HashSet<>();

        for (DetectedThreat threat : threats) {
            defensiveMoves.addAll(threat.defenses);
        }

        // Add general defensive moves
        defensiveMoves.addAll(findGeneralDefensiveMoves(state));

        return new ArrayList<>(defensiveMoves);
    }

    /**
     * FIND IMMEDIATE DEFENSES for critical threats
     */
    private static List<Move> findImmediateDefenses(GameState state, List<DetectedThreat> threats) {
        List<Move> immediateDefenses = new ArrayList<>();

        for (DetectedThreat threat : threats) {
            if (threat.level.value >= ThreatLevel.SERIOUS.value) {
                immediateDefenses.addAll(threat.defenses);
            }
        }

        return immediateDefenses;
    }

    /**
     * FIND GUARD DEFENSES
     */
    private static List<Move> findGuardDefenses(GameState state, int guardPos, Move attack) {
        List<Move> defenses = new ArrayList<>();
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);

        for (Move move : allMoves) {
            // 1. Move the guard away
            if (move.from == guardPos) {
                defenses.add(move);
            }

            // 2. Block the attack
            if (canBlockAttack(move, attack, state)) {
                defenses.add(move);
            }

            // 3. Counter-attack the attacker
            if (move.to == attack.from) {
                defenses.add(move);
            }
        }

        return defenses;
    }

    // === HELPER METHODS ===

    private static List<Move> findAttacksOnSquare(int square, GameState state, boolean byPlayer) {
        List<Move> attacks = new ArrayList<>();
        List<Move> allMoves = MoveGenerator.generateAllMoves(createStateForPlayer(state, byPlayer));

        for (Move move : allMoves) {
            if (move.to == square) {
                attacks.add(move);
            }
        }

        return attacks;
    }

    private static GameState createStateForPlayer(GameState state, boolean forRed) {
        GameState copy = state.copy();
        copy.redToMove = forRed;
        return copy;
    }

    private static int getPieceValue(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);

        // Check guard
        long guard = isRed ? state.redGuard : state.blueGuard;
        if ((guard & bit) != 0) return 1500;

        // Check towers
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
        return height * 100;
    }

    private static int calculateDefenseValue(int square, GameState state, boolean isRed) {
        List<Move> defenses = findAttacksOnSquare(square, state, isRed);
        return defenses.size() * 100; // Simplified defense calculation
    }

    private static int calculateAttackValue(List<Move> attacks, GameState state) {
        return attacks.size() * 100; // Simplified attack calculation
    }

    private static List<Move> findPieceDefenses(GameState state, int square) {
        List<Move> defenses = new ArrayList<>();
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);

        for (Move move : allMoves) {
            // Move that defend this square
            if (canDefendSquare(move, square, state)) {
                defenses.add(move);
            }
        }

        return defenses;
    }

    private static List<Move> findCastleDefenses(GameState state, int castle) {
        List<Move> defenses = new ArrayList<>();
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);

        for (Move move : allMoves) {
            // Moves that help protect the castle
            if (move.to == castle || isAdjacentToSquare(move.to, castle)) {
                defenses.add(move);
            }
        }

        return defenses;
    }

    private static List<Move> findGeneralDefensiveMoves(GameState state) {
        List<Move> defensiveMoves = new ArrayList<>();
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);

        for (Move move : allMoves) {
            if (isDefensiveMove(move, state)) {
                defensiveMoves.add(move);
            }
        }

        return defensiveMoves;
    }

    // === TACTICAL PATTERN DETECTION ===

    private static List<DetectedThreat> detectForkThreats(GameState state) {
        List<DetectedThreat> forkThreats = new ArrayList<>();

        // Check if enemy can fork our pieces
        GameState enemyState = createStateForPlayer(state, !state.redToMove);
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(enemyState);

        for (Move move : enemyMoves) {
            int threatsCount = countThreatsFromSquare(move.to, state, !state.redToMove);
            if (threatsCount >= 2) {
                forkThreats.add(new DetectedThreat(ThreatType.TACTICAL_MOTIF, ThreatLevel.SERIOUS,
                        move.to, move.from, move, "Fork threat detected", new ArrayList<>()));
            }
        }

        return forkThreats;
    }

    private static List<DetectedThreat> detectPinThreats(GameState state) {
        // Simplified pin detection for Guard & Towers
        List<DetectedThreat> pinThreats = new ArrayList<>();

        // In Guard & Towers, pins occur when a piece cannot move because
        // it would expose the guard to attack

        // TODO: Implement specific pin detection logic for Guard & Towers rules

        return pinThreats;
    }

    // === POSITIONAL ANALYSIS ===

    private static boolean hasWeakGuardPosition(GameState state) {
        boolean isRed = state.redToMove;
        long guard = isRed ? state.redGuard : state.blueGuard;

        if (guard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guard);
        int adjacentFriendly = countAdjacentFriendlyPieces(guardPos, state, isRed);

        return adjacentFriendly == 0; // Guard has no support
    }

    private static boolean hasPoorPieceCoordination(GameState state) {
        boolean isRed = state.redToMove;
        int isolatedPieces = 0;
        int totalPieces = 0;

        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            if (getPieceValue(square, state, isRed) > 0) {
                totalPieces++;
                if (countAdjacentFriendlyPieces(square, state, isRed) == 0) {
                    isolatedPieces++;
                }
            }
        }

        return totalPieces > 0 && (double)isolatedPieces / totalPieces > 0.5;
    }

    // === UTILITY METHODS ===

    private static int countThreatsFromSquare(int square, GameState state, boolean byPlayer) {
        int threats = 0;
        long enemyPieces = byPlayer ?
                (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((enemyPieces & GameState.bit(i)) != 0) {
                if (canAttackFromTo(square, i, state, byPlayer)) {
                    threats++;
                }
            }
        }

        return threats;
    }

    private static int countAdjacentFriendlyPieces(int square, GameState state, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = square + dir;
            if (GameState.isOnBoard(adjacent) && !isRankWrap(square, adjacent, dir)) {
                if (getPieceValue(adjacent, state, isRed) > 0) {
                    count++;
                }
            }
        }

        return count;
    }

    private static boolean canBlockAttack(Move blockingMove, Move attack, GameState state) {
        // Check if the blocking move places a piece between attacker and target
        return isOnPath(blockingMove.to, attack.from, attack.to);
    }

    private static boolean canDefendSquare(Move move, int square, GameState state) {
        // Check if the move helps defend the square
        return isAdjacentToSquare(move.to, square) || move.to == square;
    }

    private static boolean isDefensiveMove(Move move, GameState state) {
        // Simplified: moves that bring pieces closer to our guard
        boolean isRed = state.redToMove;
        long guard = isRed ? state.redGuard : state.blueGuard;

        if (guard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guard);
        int oldDistance = calculateManhattanDistance(move.from, guardPos);
        int newDistance = calculateManhattanDistance(move.to, guardPos);

        return newDistance < oldDistance;
    }

    private static boolean canAttackFromTo(int from, int to, GameState state, boolean byPlayer) {
        int range = 1; // Default for guards

        if (byPlayer) {
            if (state.redStackHeights[from] > 0) range = state.redStackHeights[from];
        } else {
            if (state.blueStackHeights[from] > 0) range = state.blueStackHeights[from];
        }

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (rankDiff != 0 && fileDiff != 0) return false;
        return Math.max(rankDiff, fileDiff) <= range;
    }

    private static boolean isOnPath(int point, int start, int end) {
        // Check if point lies on the path from start to end
        int startRank = GameState.rank(start);
        int startFile = GameState.file(start);
        int endRank = GameState.rank(end);
        int endFile = GameState.file(end);
        int pointRank = GameState.rank(point);
        int pointFile = GameState.file(point);

        // Must be on same line
        if (startRank == endRank && pointRank == startRank) {
            int minFile = Math.min(startFile, endFile);
            int maxFile = Math.max(startFile, endFile);
            return pointFile > minFile && pointFile < maxFile;
        } else if (startFile == endFile && pointFile == startFile) {
            int minRank = Math.min(startRank, endRank);
            int maxRank = Math.max(startRank, endRank);
            return pointRank > minRank && pointRank < maxRank;
        }

        return false;
    }

    private static boolean isAdjacentToSquare(int square1, int square2) {
        int rankDiff = Math.abs(GameState.rank(square1) - GameState.rank(square2));
        int fileDiff = Math.abs(GameState.file(square1) - GameState.file(square2));
        return (rankDiff + fileDiff) == 1;
    }

    private static boolean isRankWrap(int from, int to, int direction) {
        if (Math.abs(direction) == 1) {
            return GameState.rank(from) != GameState.rank(to);
        }
        return false;
    }

    private static int calculateManhattanDistance(int from, int to) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));
        return rankDiff + fileDiff;
    }

    private static String squareName(int square) {
        char file = (char) ('A' + GameState.file(square));
        int rank = GameState.rank(square) + 1;
        return "" + file + rank;
    }
}