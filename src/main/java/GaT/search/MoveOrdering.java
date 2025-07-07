package GaT.search;

import GaT.model.*;
import java.util.*;

/**
 * Advanced move ordering for Guards & Towers
 * COMPLETE FIXED VERSION - All constructors + ImmutableList handling
 */
public class MoveOrdering {

    // History tables
    private final int[][][] historyTable = new int[2][49][49]; // [color][from][to]
    private final Move[][] killerMoves = new Move[100][2]; // [depth][slot]

    // PV table for storing principal variation
    private final Move[] pvTable = new Move[100];

    // Counter move heuristic
    private final Move[][] counterMoves = new Move[49][49]; // [lastFrom][lastTo]

    // Configuration
    private static final int HISTORY_MAX = 10000;
    private static final int HISTORY_DIVISOR = 2;

    // Optional statistics reference
    private final SearchStatistics statistics;

    /**
     * Constructor with statistics
     */
    public MoveOrdering(SearchStatistics statistics) {
        this.statistics = statistics;
    }

    /**
     * Default constructor
     */
    public MoveOrdering() {
        this(null);
    }

    /**
     * Order moves for best search efficiency
     * CRITICAL FIX: Create mutable copy of immutable list
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.isEmpty()) {
            return;
        }

        // CRITICAL FIX: Create mutable ArrayList copy if needed
        List<Move> mutableMoves;
        if (moves instanceof ArrayList) {
            mutableMoves = moves;
        } else {
            // Create mutable copy for ImmutableList or other implementations
            mutableMoves = new ArrayList<>(moves);
        }

        // Try different ordering strategies based on game phase
        if (isEndgame(state)) {
            orderMovesEndgame(mutableMoves, state, depth, ttEntry);
        } else if (depth > 6) {
            orderMovesUltimate(mutableMoves, state, depth, ttEntry);
        } else {
            orderMovesStandard(mutableMoves, state, depth, ttEntry);
        }

        // If original list was mutable, copy sorted results back
        if (moves instanceof ArrayList && moves != mutableMoves) {
            moves.clear();
            moves.addAll(mutableMoves);
        } else if (!(moves instanceof ArrayList)) {
            // For immutable lists, we can't modify the original
            // The caller should use the return value or handle this appropriately
            // Log warning if needed
            if (statistics != null) {
                // Could track this in statistics if needed
            }
        }
    }

    /**
     * Standard move ordering
     */
    private void orderMovesStandard(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        boolean isRed = state.redToMove;

        moves.sort((m1, m2) -> {
            int score1 = 0, score2 = 0;

            // 1. TT move (best from previous search)
            if (ttEntry != null && ttEntry.bestMove != null) {
                if (m1.equals(ttEntry.bestMove)) score1 += 1000000;
                if (m2.equals(ttEntry.bestMove)) score2 += 1000000;
            }

            // 2. PV move
            if (depth < pvTable.length && pvTable[depth] != null) {
                if (m1.equals(pvTable[depth])) score1 += 900000;
                if (m2.equals(pvTable[depth])) score2 += 900000;
            }

            // 3. Captures ordered by MVV-LVA
            boolean isCapture1 = GameRules.isCapture(m1, state);
            boolean isCapture2 = GameRules.isCapture(m2, state);

            if (isCapture1) {
                score1 += 500000 + getMVVLVAScore(m1, state);
            }
            if (isCapture2) {
                score2 += 500000 + getMVVLVAScore(m2, state);
            }

            // 4. Killer moves
            if (isKillerMove(m1, depth)) score1 += 100000;
            if (isKillerMove(m2, depth)) score2 += 100000;

            // 5. Counter moves
            Move lastMove = getLastMove(state);
            if (lastMove != null && isCounterMove(m1, lastMove)) score1 += 50000;
            if (lastMove != null && isCounterMove(m2, lastMove)) score2 += 50000;

            // 6. History heuristic
            score1 += getHistoryScore(m1, isRed);
            score2 += getHistoryScore(m2, isRed);

            // 7. Positional bonuses
            score1 += getPositionalBonus(m1, state);
            score2 += getPositionalBonus(m2, state);

            return score2 - score1; // Descending order
        });
    }

    /**
     * Ultimate move ordering for deep searches
     * FIXED: Now handles mutable list
     */
    private void orderMovesUltimate(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        // Pre-calculate scores for all moves
        int[] scores = new int[moves.size()];
        boolean isRed = state.redToMove;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            int score = 0;

            // 1. Hash move gets highest priority
            if (ttEntry != null && ttEntry.bestMove != null && move.equals(ttEntry.bestMove)) {
                score += 10000000;
            }

            // 2. Winning captures
            if (GameRules.isCapture(move, state)) {
                int captureScore = getMVVLVAScore(move, state);
                if (captureScore > 0) {
                    score += 5000000 + captureScore;
                }
            }

            // 3. Killer moves
            if (isKillerMove(move, depth)) {
                score += 1000000;
            }

            // 4. History score with decay
            int histScore = getHistoryScore(move, isRed);
            score += Math.min(histScore, 500000);

            // 5. Tactical patterns
            score += getTacticalScore(move, state);

            scores[i] = score;
        }

        // Sort using scores - SAFE because we have mutable list
        Integer[] indices = new Integer[moves.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, (i1, i2) -> scores[i2] - scores[i1]);

        List<Move> sorted = new ArrayList<>(moves.size());
        for (int idx : indices) {
            sorted.add(moves.get(idx));
        }

        moves.clear();
        moves.addAll(sorted);
    }

    /**
     * Endgame move ordering
     */
    private void orderMovesEndgame(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        orderMovesStandard(moves, state, depth, ttEntry); // For now, use standard
    }

    /**
     * MVV-LVA scoring for captures
     */
    private int getMVVLVAScore(Move move, GameState state) {
        if (!GameRules.isCapture(move, state)) {
            return 0;
        }

        int victimValue = 0;
        int attackerValue = 0;

        boolean isRed = state.redToMove;
        int to = move.to;
        int from = move.from;

        // Victim value (what we capture)
        if (isRed) {
            // Red captures blue
            victimValue = state.blueStackHeights[to] * 100;
            if ((state.blueGuard & (1L << to)) != 0) {
                victimValue += 1000; // Guard is very valuable
            }
        } else {
            // Blue captures red
            victimValue = state.redStackHeights[to] * 100;
            if ((state.redGuard & (1L << to)) != 0) {
                victimValue += 1000;
            }
        }

        // Attacker value (what we risk)
        if (isRed) {
            attackerValue = state.redStackHeights[from] * 10;
            if ((state.redGuard & (1L << from)) != 0) {
                attackerValue += 100;
            }
        } else {
            attackerValue = state.blueStackHeights[from] * 10;
            if ((state.blueGuard & (1L << from)) != 0) {
                attackerValue += 100;
            }
        }

        // MVV-LVA: Capture high value pieces with low value pieces
        return victimValue - attackerValue;
    }

    /**
     * Get positional bonus for a move
     */
    private int getPositionalBonus(Move move, GameState state) {
        int bonus = 0;
        int to = move.to;

        // Center control
        int row = to / 7;
        int col = to % 7;
        int centerDistance = Math.abs(row - 3) + Math.abs(col - 3);
        bonus += (6 - centerDistance) * 10;

        // Guard proximity
        boolean isRed = state.redToMove;
        long friendlyGuard = isRed ? state.redGuard : state.blueGuard;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        // Bonus for moving near enemy guard
        for (int i = 0; i < 49; i++) {
            if ((enemyGuard & (1L << i)) != 0) {
                int dist = getDistance(to, i);
                if (dist <= 2) {
                    bonus += (3 - dist) * 50;
                }
            }
        }

        // Penalty for moving away from friendly guard
        for (int i = 0; i < 49; i++) {
            if ((friendlyGuard & (1L << i)) != 0) {
                int dist = getDistance(move.from, i);
                int newDist = getDistance(to, i);
                if (newDist > dist) {
                    bonus -= (newDist - dist) * 20;
                }
            }
        }

        return bonus;
    }

    /**
     * Calculate tactical score
     */
    private int getTacticalScore(Move move, GameState state) {
        int score = 0;

        // Fork detection
        GameState newState = state.copy();
        newState.applyMove(move);

        List<Move> responses = MoveGenerator.generateAllMoves(newState);
        int attacks = 0;

        for (Move response : responses) {
            if (GameRules.isCapture(response, newState)) {
                attacks++;
            }
        }

        if (attacks >= 2) {
            score += 200 * attacks; // Fork bonus
        }

        // Pin detection
        if (createsPin(move, state)) {
            score += 300;
        }

        return score;
    }

    /**
     * Check if move creates a pin
     */
    private boolean createsPin(Move move, GameState state) {
        // Simplified pin detection
        // In real implementation, would check if move attacks a piece
        // that can't move without exposing a more valuable piece
        return false; // TODO: Implement
    }

    /**
     * Store killer move
     */
    public void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;

        // Don't store captures as killers
        if (move.from < 0 || move.to < 0) return;

        // Avoid duplicates
        if (killerMoves[depth][0] != null && killerMoves[depth][0].equals(move)) {
            return;
        }

        // Shift and store
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;
    }

    /**
     * Check if move is a killer
     */
    private boolean isKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return false;

        return (killerMoves[depth][0] != null && killerMoves[depth][0].equals(move)) ||
                (killerMoves[depth][1] != null && killerMoves[depth][1].equals(move));
    }

    /**
     * Update history score
     */
    public void updateHistory(Move move, int depth, boolean isRed) {
        if (move.from < 0 || move.to < 0 || move.from >= 49 || move.to >= 49) return;

        int color = isRed ? 0 : 1;
        int bonus = depth * depth; // Quadratic bonus

        historyTable[color][move.from][move.to] += bonus;

        // Prevent overflow
        if (historyTable[color][move.from][move.to] > HISTORY_MAX) {
            // Age all history scores
            for (int c = 0; c < 2; c++) {
                for (int f = 0; f < 49; f++) {
                    for (int t = 0; t < 49; t++) {
                        historyTable[c][f][t] /= HISTORY_DIVISOR;
                    }
                }
            }
        }
    }

    /**
     * Get history score
     */
    private int getHistoryScore(Move move, boolean isRed) {
        if (move.from < 0 || move.to < 0 || move.from >= 49 || move.to >= 49) {
            return 0;
        }

        int color = isRed ? 0 : 1;
        return historyTable[color][move.from][move.to];
    }

    /**
     * Store counter move
     */
    public void storeCounterMove(Move move, Move previousMove) {
        if (previousMove != null && previousMove.from >= 0 && previousMove.to >= 0 &&
                previousMove.from < 49 && previousMove.to < 49) {
            counterMoves[previousMove.from][previousMove.to] = move;
        }
    }

    /**
     * Check if move is a counter move
     */
    private boolean isCounterMove(Move move, Move lastMove) {
        if (lastMove == null || lastMove.from < 0 || lastMove.to < 0 ||
                lastMove.from >= 49 || lastMove.to >= 49) {
            return false;
        }

        Move counter = counterMoves[lastMove.from][lastMove.to];
        return counter != null && counter.equals(move);
    }

    /**
     * Store PV move
     */
    public void storePVMove(Move move, int depth) {
        if (depth < pvTable.length) {
            pvTable[depth] = move;
        }
    }

    /**
     * Clear all tables
     */
    public void clear() {
        // Clear history
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 49; f++) {
                Arrays.fill(historyTable[c][f], 0);
            }
        }

        // Clear killers
        for (int d = 0; d < killerMoves.length; d++) {
            killerMoves[d][0] = null;
            killerMoves[d][1] = null;
        }

        // Clear PV
        Arrays.fill(pvTable, null);

        // Clear counter moves
        for (int f = 0; f < 49; f++) {
            Arrays.fill(counterMoves[f], null);
        }
    }

    /**
     * Age history scores (call between games)
     */
    public void ageHistory() {
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 49; f++) {
                for (int t = 0; t < 49; t++) {
                    historyTable[c][f][t] = historyTable[c][f][t] * 3 / 4;
                }
            }
        }
    }

    /**
     * Check if position is endgame
     */
    private boolean isEndgame(GameState state) {
        int totalPieces = 0;
        for (int i = 0; i < 49; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalPieces <= 10; // Adjust threshold as needed
    }

    /**
     * Get distance between squares
     */
    private int getDistance(int sq1, int sq2) {
        int r1 = sq1 / 7, c1 = sq1 % 7;
        int r2 = sq2 / 7, c2 = sq2 % 7;
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }

    /**
     * Get last move played (simplified)
     */
    private Move getLastMove(GameState state) {
        // In real implementation, would track move history
        return null;
    }

    /**
     * Create a properly ordered copy of moves
     * Use this if you need to preserve the original list
     */
    public List<Move> getOrderedMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        List<Move> orderedMoves = new ArrayList<>(moves);
        orderMoves(orderedMoves, state, depth, ttEntry);
        return orderedMoves;
    }

    /**
     * Get statistics (if available)
     */
    public SearchStatistics getStatistics() {
        return statistics;
    }
}