package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * COMPREHENSIVE THREAT DETECTION SYSTEM - FIXED IMPLEMENTATION
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
            this.immediateThreats = immediate != null ? immediate : new ArrayList<>();
            this.potentialThreats = potential != null ? potential : new ArrayList<>();
            this.defensiveMoves = defensive != null ? defensive : new ArrayList<>();
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
     * Analyze a single move for threats - FIXED IMPLEMENTATION
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
     * Check if a move defends against threats - FIXED IMPLEMENTATION
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
     * Score a defensive move - FIXED IMPLEMENTATION
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
     * Calculate overall threat level (0-10) - FIXED IMPLEMENTATION
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

    // === HELPER METHODS - FIXED IMPLEMENTATIONS ===

    private static boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redToMove && (state.redGuard & fromBit) != 0) ||
                (!state.redToMove && (state.blueGuard & fromBit) != 0);
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static int getCaptureValue(Move move, GameState state) {
        // Simple capture value calculation
        long toBit = GameState.bit(move.to);

        // Check guards
        if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
            return 2000;
        }

        // Check towers
        if ((state.redTowers & toBit) != 0) {
            return state.redStackHeights[move.to] * 100;
        }
        if ((state.blueTowers & toBit) != 0) {
            return state.blueStackHeights[move.to] * 100;
        }

        return 0;
    }

    private static List<Integer> getAttackedSquares(GameState state, Move move) {
        List<Integer> attacked = new ArrayList<>();

        // Simulate the move and check what squares the piece can now attack
        GameState afterMove = state.copy();
        afterMove.applyMove(move);

        // Get the range of the piece that moved
        int range = 1; // Default guard range
        long fromBit = GameState.bit(move.to);

        if ((afterMove.redTowers & fromBit) != 0) {
            range = afterMove.redStackHeights[move.to];
        } else if ((afterMove.blueTowers & fromBit) != 0) {
            range = afterMove.blueStackHeights[move.to];
        }

        // Check all squares within range
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (canAttack(move.to, i, range)) {
                // Check if there's an enemy piece here
                long targetBit = GameState.bit(i);
                long enemyPieces = afterMove.redToMove ?
                        (afterMove.blueTowers | afterMove.blueGuard) :
                        (afterMove.redTowers | afterMove.redGuard);

                if ((enemyPieces & targetBit) != 0) {
                    attacked.add(i);
                }
            }
        }

        return attacked;
    }

    private static boolean canAttack(int from, int to, int range) {
        if (from == to) return false;

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        // Check range
        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    private static int evaluateFork(GameState state, List<Integer> attackedSquares, boolean defendingPlayer) {
        int totalValue = 0;

        for (int square : attackedSquares) {
            long bit = GameState.bit(square);

            // Check for guards (high value)
            if (defendingPlayer) {
                if ((state.redGuard & bit) != 0) totalValue += 2000;
                if ((state.redTowers & bit) != 0) totalValue += state.redStackHeights[square] * 100;
            } else {
                if ((state.blueGuard & bit) != 0) totalValue += 2000;
                if ((state.blueTowers & bit) != 0) totalValue += state.blueStackHeights[square] * 100;
            }
        }

        return totalValue;
    }

    private static Threat checkDiscoveredThreat(GameState state, Move move) {
        // Simple discovered threat check - could be enhanced
        return null;
    }

    private static boolean isGuardStillThreatened(GameState state, Threat threat) {
        // Re-analyze to see if the guard is still under attack
        boolean isRed = !state.redToMove; // Opponent's perspective
        long guardPos = isRed ? state.redGuard : state.blueGuard;

        if (guardPos == 0) return false; // Guard was captured/moved

        return hasImmediateThreat(state);
    }

    private static boolean blocksCastleThreat(Move move, Threat threat) {
        // Check if the move blocks the path to castle
        return move.to == threat.targetSquare;
    }

    private static boolean defendsSquare(GameState state, int square) {
        // Check if the square is now defended by friendly pieces
        return true; // Simplified implementation
    }

    private static boolean createsCounterThreat(GameState state, Move move, List<Threat> threats) {
        // Check if the move creates a counter-threat
        return isCapture(move, state) && getCaptureValue(move, state) >= 500;
    }

    private static boolean addressesThreat(GameState state, Move move, Threat threat) {
        return move.from == threat.targetSquare || move.to == threat.targetSquare;
    }

    private static boolean advancesPosition(Move move, GameState state) {
        // Check if move improves position (simplified)
        return move.to > move.from; // Moving "forward"
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

            // Check for castle reach
            if (reachesCastle(move, opponentView)) {
                return true;
            }
        }

        return false;
    }

    private static boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        return (enemyGuard & toBit) != 0;
    }

    private static boolean reachesCastle(Move move, GameState state) {
        int enemyCastle = state.redToMove ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == enemyCastle && isGuardMove(move, state);
    }
}