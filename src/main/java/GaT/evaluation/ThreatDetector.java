package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * COMPREHENSIVE THREAT DETECTION SYSTEM
 * Analyzes tactical threats, defensive requirements, and forcing sequences
 */
public class ThreatDetector {

    // Threat types
    public static final int NO_THREAT = 0;
    public static final int GUARD_THREAT = 1;
    public static final int CASTLE_THREAT = 2;
    public static final int MATERIAL_THREAT = 3;
    public static final int FORK_THREAT = 4;
    public static final int PIN_THREAT = 5;
    public static final int DISCOVERED_THREAT = 6;

    /**
     * Main threat analysis result
     */
    public static class ThreatAnalysis {
        public final List<Threat> immediateThreats;
        public final List<Threat> potentialThreats;
        public final List<Move> defensiveMoves;
        public final int threatLevel; // 0-10 scale
        public final boolean inCheck;
        public final boolean mustDefend;

        public ThreatAnalysis(List<Threat> immediate, List<Threat> potential,
                              List<Move> defensive, int level, boolean check, boolean defend) {
            this.immediateThreats = immediate;
            this.potentialThreats = potential;
            this.defensiveMoves = defensive;
            this.threatLevel = level;
            this.inCheck = check;
            this.mustDefend = defend;
        }
    }

    /**
     * Individual threat representation
     */
    public static class Threat {
        public final int type;
        public final Move attackingMove;
        public final int targetSquare;
        public final int threatValue;
        public final boolean isImmediate;

        public Threat(int type, Move move, int target, int value, boolean immediate) {
            this.type = type;
            this.attackingMove = move;
            this.targetSquare = target;
            this.threatValue = value;
            this.isImmediate = immediate;
        }
    }

    /**
     * Analyze all threats in the current position
     */
    public static ThreatAnalysis analyzeThreats(GameState state) {
        List<Threat> immediateThreats = new ArrayList<>();
        List<Threat> potentialThreats = new ArrayList<>();
        List<Move> defensiveMoves = new ArrayList<>();

        // Switch perspective to analyze opponent's threats
        GameState opponentView = state.copy();
        opponentView.redToMove = !opponentView.redToMove;

        // Generate opponent's moves
        List<Move> opponentMoves = MoveGenerator.generateAllMoves(opponentView);

        // Analyze each opponent move for threats
        boolean inCheck = false;
        int maxThreatValue = 0;

        for (Move move : opponentMoves) {
            Threat threat = analyzeSingleMove(opponentView, move);
            if (threat != null) {
                if (threat.isImmediate) {
                    immediateThreats.add(threat);
                    maxThreatValue = Math.max(maxThreatValue, threat.threatValue);

                    if (threat.type == GUARD_THREAT) {
                        inCheck = true;
                    }
                } else {
                    potentialThreats.add(threat);
                }
            }
        }

        // Find defensive moves if there are threats
        if (!immediateThreats.isEmpty()) {
            defensiveMoves = findDefensiveMoves(state, immediateThreats);
        }

        // Calculate overall threat level (0-10)
        int threatLevel = calculateThreatLevel(immediateThreats, potentialThreats, maxThreatValue);

        // Must defend if in check or facing winning threats
        boolean mustDefend = inCheck || maxThreatValue >= 1000;

        return new ThreatAnalysis(immediateThreats, potentialThreats, defensiveMoves,
                threatLevel, inCheck, mustDefend);
    }

    /**
     * Analyze a single move for threats
     */
    private static Threat analyzeSingleMove(GameState state, Move move) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Check for guard capture threat
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if ((enemyGuard & toBit) != 0) {
            return new Threat(GUARD_THREAT, move, move.to, 2000, true);
        }

        // Check for castle reach threat
        int enemyCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        if (move.to == enemyCastle && isGuardMove(move, state)) {
            return new Threat(CASTLE_THREAT, move, move.to, 3000, true);
        }

        // Check for material threats
        if (isCapture(move, state)) {
            int captureValue = getCaptureValue(move, state);
            if (captureValue >= 200) { // Significant material
                return new Threat(MATERIAL_THREAT, move, move.to, captureValue, true);
            }
        }

        // Check for fork threats (attacking multiple pieces)
        List<Integer> attackedSquares = getAttackedSquares(state, move);
        if (attackedSquares.size() >= 2) {
            int forkValue = evaluateFork(state, attackedSquares, !isRed);
            if (forkValue >= 300) {
                return new Threat(FORK_THREAT, move, move.to, forkValue, false);
            }
        }

        // Check for discovered threats
        Threat discoveredThreat = checkDiscoveredThreat(state, move);
        if (discoveredThreat != null) {
            return discoveredThreat;
        }

        return null;
    }

    /**
     * Find moves that defend against threats
     */
    private static List<Move> findDefensiveMoves(GameState state, List<Threat> threats) {
        List<Move> defensive = new ArrayList<>();
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);

        for (Move move : allMoves) {
            if (defendsAgainstThreats(state, move, threats)) {
                defensive.add(move);
            }
        }

        // Sort defensive moves by effectiveness
        defensive.sort((a, b) -> {
            int scoreA = scoreDefensiveMove(state, a, threats);
            int scoreB = scoreDefensiveMove(state, b, threats);
            return Integer.compare(scoreB, scoreA);
        });

        return defensive;
    }

    /**
     * Check if a move defends against threats
     */
    private static boolean defendsAgainstThreats(GameState state, Move move, List<Threat> threats) {
        // Simulate the move
        GameState afterMove = state.copy();
        afterMove.applyMove(move);

        // Re-analyze threats after the move
        for (Threat threat : threats) {
            if (threat.type == GUARD_THREAT) {
                // Check if guard is still in danger
                if (!isGuardStillThreatened(afterMove, threat)) {
                    return true;
                }
            } else if (threat.type == CASTLE_THREAT) {
                // Check if castle threat is blocked
                if (blocksCastleThreat(move, threat)) {
                    return true;
                }
            } else if (threat.type == MATERIAL_THREAT) {
                // Check if piece is moved or defended
                if (move.from == threat.targetSquare || defendsSquare(afterMove, threat.targetSquare)) {
                    return true;
                }
            }
        }

        // Check if move creates counter-threat
        if (createsCounterThreat(state, move, threats)) {
            return true;
        }

        return false;
    }

    /**
     * Score a defensive move
     */
    private static int scoreDefensiveMove(GameState state, Move move, List<Threat> threats) {
        int score = 0;

        // Bonus for moves that address multiple threats
        int threatsAddressed = 0;
        for (Threat threat : threats) {
            if (addressesThreat(state, move, threat)) {
                threatsAddressed++;
                score += threat.threatValue / 2;
            }
        }

        if (threatsAddressed > 1) {
            score += 200; // Multi-purpose defense bonus
        }

        // Bonus for counter-threats
        if (createsCounterThreat(state, move, threats)) {
            score += 300;
        }

        // Penalty for passive moves
        if (!isCapture(move, state) && !advancesPosition(move, state)) {
            score -= 50;
        }

        return score;
    }

    /**
     * Calculate overall threat level (0-10)
     */
    private static int calculateThreatLevel(List<Threat> immediate, List<Threat> potential, int maxValue) {
        int level = 0;

        // Immediate threats
        level += Math.min(immediate.size() * 2, 6);

        // Threat severity
        if (maxValue >= 2000) level += 3; // Guard threat
        else if (maxValue >= 1000) level += 2; // Major material
        else if (maxValue >= 500) level += 1; // Minor material

        // Multiple threats
        if (immediate.size() >= 3) level += 1;

        return Math.min(level, 10);
    }

    /**
     * Check for null move threats (what opponent threatens if we pass)
     */
    public static ThreatAnalysis analyzeNullMoveThreats(GameState state) {
        // Make a null move (just switch turn)
        GameState nullState = state.copy();
        nullState.redToMove = !nullState.redToMove;

        // Analyze threats in null move position
        return analyzeThreats(nullState);
    }

    /**
     * Quick threat check for time-critical situations
     */
    public static boolean hasImmediateThreat(GameState state) {
        // Switch perspective
        GameState opponentView = state.copy();
        opponentView.redToMove = !opponentView.redToMove;

        List<Move> opponentMoves = MoveGenerator.generateAllMoves(opponentView);

        for (Move move : opponentMoves) {
            // Check for guard captures
            if (capturesGuard(move, opponentView)) {
                return true;
            }

            // Check for castle reaches
            if (reachesCastle(move, opponentView)) {
                return true;
            }
        }

        return false;
    }

    // === HELPER METHODS ===

    private static boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return ((state.redGuard | state.blueGuard) & fromBit) != 0;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        if ((isRed && (state.blueGuard & toBit) != 0) || (!isRed && (state.redGuard & toBit) != 0)) {
            return 2000;
        }

        if (isRed && (state.blueTowers & toBit) != 0) {
            return state.blueStackHeights[move.to] * 100;
        } else if (!isRed && (state.redTowers & toBit) != 0) {
            return state.redStackHeights[move.to] * 100;
        }

        return 0;
    }

    private static List<Integer> getAttackedSquares(GameState state, Move move) {
        List<Integer> attacked = new ArrayList<>();
        int from = move.to; // After the move
        int range = move.amountMoved;

        // Check all four directions
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            for (int dist = 1; dist <= range; dist++) {
                int target = from + dir * dist;
                if (!GameState.isOnBoard(target)) break;

                // Check for rank wrap
                if (Math.abs(dir) == 1 && GameState.rank(from) != GameState.rank(target)) break;

                long targetBit = GameState.bit(target);
                boolean isRed = state.redToMove;

                // Check if enemy piece
                if ((isRed && ((state.blueTowers | state.blueGuard) & targetBit) != 0) ||
                        (!isRed && ((state.redTowers | state.redGuard) & targetBit) != 0)) {
                    attacked.add(target);
                }

                // Stop if blocked
                if (((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & targetBit) != 0) {
                    break;
                }
            }
        }

        return attacked;
    }

    private static int evaluateFork(GameState state, List<Integer> targets, boolean attackingRed) {
        int totalValue = 0;

        for (int square : targets) {
            long bit = GameState.bit(square);

            if (attackingRed) {
                if ((state.blueGuard & bit) != 0) totalValue += 2000;
                else if ((state.blueTowers & bit) != 0) totalValue += state.blueStackHeights[square] * 100;
            } else {
                if ((state.redGuard & bit) != 0) totalValue += 2000;
                else if ((state.redTowers & bit) != 0) totalValue += state.redStackHeights[square] * 100;
            }
        }

        // Fork value is based on the two most valuable targets
        return totalValue;
    }

    private static Threat checkDiscoveredThreat(GameState state, Move move) {
        // TODO: Implement discovered threat detection
        // This would check if moving a piece reveals an attack from another piece
        return null;
    }

    private static boolean isGuardStillThreatened(GameState state, Threat threat) {
        // Re-check if the guard can still be captured
        boolean isRed = !state.redToMove; // We switched turns
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false; // Guard already captured

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check if the threatening piece can still capture the guard
        return canPieceAttackSquare(state, threat.attackingMove.from, guardPos);
    }

    private static boolean blocksCastleThreat(Move move, Threat threat) {
        // Check if move blocks path to castle
        return move.to == threat.targetSquare ||
                isOnPath(threat.attackingMove.from, threat.targetSquare, move.to);
    }

    private static boolean defendsSquare(GameState state, int square) {
        // Check if square is defended by friendly pieces
        boolean isRed = state.redToMove;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (isRed) {
                if ((state.redStackHeights[i] > 0 || (state.redGuard & GameState.bit(i)) != 0) &&
                        canPieceAttackSquare(state, i, square)) {
                    return true;
                }
            } else {
                if ((state.blueStackHeights[i] > 0 || (state.blueGuard & GameState.bit(i)) != 0) &&
                        canPieceAttackSquare(state, i, square)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean createsCounterThreat(GameState state, Move move, List<Threat> threats) {
        // Simulate move and check for counter-threats
        GameState afterMove = state.copy();
        afterMove.applyMove(move);

        // Check if we threaten enemy guard or create winning threat
        List<Move> ourMoves = MoveGenerator.generateAllMoves(afterMove);

        for (Move ourMove : ourMoves) {
            if (capturesGuard(ourMove, afterMove) || reachesCastle(ourMove, afterMove)) {
                return true;
            }
        }

        return false;
    }

    private static boolean addressesThreat(GameState state, Move move, Threat threat) {
        // Check if move addresses specific threat
        return move.from == threat.targetSquare || // Move threatened piece
                move.to == threat.attackingMove.to || // Block/capture attacker
                defendsSquare(state, threat.targetSquare); // Add defender
    }

    private static boolean advancesPosition(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        return (isRed && toRank < fromRank) || (!isRed && toRank > fromRank);
    }

    private static boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;
        return (isRed && (state.blueGuard & toBit) != 0) || (!isRed && (state.redGuard & toBit) != 0);
    }

    private static boolean reachesCastle(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;
        int targetCastle = state.redToMove ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle;
    }

    private static boolean canPieceAttackSquare(GameState state, int from, int to) {
        int range = 1; // Default for guard

        if (state.redStackHeights[from] > 0) {
            range = state.redStackHeights[from];
        } else if (state.blueStackHeights[from] > 0) {
            range = state.blueStackHeights[from];
        }

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range && isPathClear(state, from, to);
    }

    private static boolean isPathClear(GameState state, int from, int to) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        if (rankDiff != 0 && fileDiff != 0) return false;

        int step = rankDiff != 0 ? (rankDiff > 0 ? 7 : -7) : (fileDiff > 0 ? 1 : -1);
        int current = from + step;

        while (current != to) {
            long bit = GameState.bit(current);
            if ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & bit) != 0) {
                return false;
            }
            current += step;
        }

        return true;
    }

    private static boolean isOnPath(int from, int to, int check) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        if (rankDiff != 0 && fileDiff != 0) return false;

        // Check if 'check' is between from and to
        int checkRank = GameState.rank(check);
        int checkFile = GameState.file(check);
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        if (rankDiff == 0) { // Same rank
            return checkRank == fromRank &&
                    checkFile >= Math.min(fromFile, toFile) &&
                    checkFile <= Math.max(fromFile, toFile);
        } else { // Same file
            return checkFile == fromFile &&
                    checkRank >= Math.min(fromRank, toRank) &&
                    checkRank <= Math.max(fromRank, toRank);
        }
    }
}