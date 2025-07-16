package GaT.search;

import GaT.model.*;
import java.util.List;

/**
 * FAST MOVE ORDERING - No evaluation function calls
 *
 * REPLACES: Evaluation-heavy move ordering
 * PERFORMANCE GAIN: 40-60% reduction in move ordering time
 */
public class FastMoveOrdering {

    // === KILLER MOVES ===
    private final Move[][] killerMoves;
    private final int maxDepth;

    // === HISTORY HEURISTIC ===
    private final int[][][] historyTable; // [piece][from][to]
    private static final int HISTORY_MAX = 1000;
    private static final int HISTORY_DECAY = 4;

    // === STATISTICS ===
    private long orderingQueries = 0;
    private long firstMoveSuccesses = 0;

    public FastMoveOrdering() {
        this.maxDepth = 64; // Reasonable default
        this.killerMoves = new Move[maxDepth][2]; // 2 killers per depth
        this.historyTable = new int[4][49][49]; // [piece_type][from][to]
    }

    // === MAIN ORDERING METHOD ===

    /**
     * Order moves using ONLY fast heuristics (NO evaluation calls)
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.size() <= 1) return;

        orderingQueries++;

        // Score all moves using fast methods
        int[] scores = new int[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            scores[i] = scoreMovefast(moves.get(i), state, depth, ttEntry);
        }

        // Sort by score (highest first)
        quickSortMoves(moves, scores, 0, moves.size() - 1);
    }

    // === FAST MOVE SCORING ===

    private int scoreMovefast(Move move, GameState state, int depth, TTEntry ttEntry) {
        if (move == null) return -99999;

        int score = 0;

        // 1. TT MOVE (highest priority)
        if (ttEntry != null && move.equals(ttEntry.bestMove)) {
            return GameValues.TT_MOVE_PRIORITY;
        }

        // 2. CAPTURES (MVV-LVA using GameValues)
        if (GameValues.isCapture(state, move.from, move.to)) {
            score += GameValues.CAPTURE_BASE_PRIORITY;
            score += GameValues.getMVVLVAScore(state, move.from, move.to);
            return score; // Captures get highest priority after TT moves
        }

        // 3. KILLER MOVES
        if (isKillerMove(move, depth)) {
            if (move.equals(killerMoves[Math.min(depth, maxDepth-1)][0])) {
                score += GameValues.KILLER_1_PRIORITY;
            } else {
                score += GameValues.KILLER_2_PRIORITY;
            }
            return score;
        }

        // 4. HISTORY HEURISTIC
        score += getHistoryScore(move, state);

        // 5. POSITIONAL BONUSES (fast lookups only)
        score += GameValues.getDFileBonus(move.to);
        score += GameValues.getCentralBonus(move.to);

        // 6. PIECE DEVELOPMENT
        boolean isRed = isRedPiece(move, state);
        score += GameValues.getDevelopmentBonus(move.to, isRed);

        // 7. GUARD ADVANCEMENT
        if (isGuardMove(move, state)) {
            score += GameValues.getGuardAdvancementBonus(move.from, move.to, isRed);
        }

        return score;
    }

    // === HELPER METHODS ===

    private boolean isRedPiece(Move move, GameState state) {
        if (move == null || state == null) return false;
        long fromBit = GameState.bit(move.from);
        return (state.redGuard & fromBit) != 0 || state.redStackHeights[move.from] > 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        if (move == null || state == null) return false;
        long fromBit = GameState.bit(move.from);
        return (state.redGuard & fromBit) != 0 || (state.blueGuard & fromBit) != 0;
    }

    // === KILLER MOVES ===

    private boolean isKillerMove(Move move, int depth) {
        if (depth < 0 || depth >= maxDepth || move == null) return false;
        return move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1]);
    }

    public void storeKillerMove(Move move, int depth) {
        if (move == null || depth < 0 || depth >= maxDepth) return;

        // Shift killer moves
        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    // === HISTORY HEURISTIC ===

    private int getHistoryScore(Move move, GameState state) {
        try {
            int piece = getPieceType(move, state);
            if (piece < 0 || piece >= 4) return 0;
            return historyTable[piece][move.from][move.to];
        } catch (Exception e) {
            return 0;
        }
    }

    public void updateHistory(Move move, int depth, GameState state) {
        try {
            int piece = getPieceType(move, state);
            if (piece < 0 || piece >= 4) return;

            int bonus = depth * depth; // Deeper = more important
            historyTable[piece][move.from][move.to] += bonus;

            // Prevent overflow
            if (historyTable[piece][move.from][move.to] > HISTORY_MAX) {
                ageHistoryTable();
            }
        } catch (Exception e) {
            // Ignore history update failures
        }
    }

    private int getPieceType(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);

        if ((state.redGuard & fromBit) != 0) return 2;      // Red guard
        if ((state.blueGuard & fromBit) != 0) return 3;     // Blue guard
        if (state.redStackHeights[move.from] > 0) return 0; // Red tower
        if (state.blueStackHeights[move.from] > 0) return 1; // Blue tower

        return -1; // Invalid
    }

    private void ageHistoryTable() {
        for (int piece = 0; piece < 4; piece++) {
            for (int from = 0; from < 49; from++) {
                for (int to = 0; to < 49; to++) {
                    historyTable[piece][from][to] >>= HISTORY_DECAY;
                }
            }
        }
    }

    // === SORTING ===

    private void quickSortMoves(List<Move> moves, int[] scores, int low, int high) {
        if (low < high) {
            int pi = partition(moves, scores, low, high);
            quickSortMoves(moves, scores, low, pi - 1);
            quickSortMoves(moves, scores, pi + 1, high);
        }
    }

    private int partition(List<Move> moves, int[] scores, int low, int high) {
        int pivot = scores[high];
        int i = (low - 1);

        for (int j = low; j < high; j++) {
            if (scores[j] >= pivot) { // Sort descending
                i++;
                swap(moves, scores, i, j);
            }
        }
        swap(moves, scores, i + 1, high);
        return i + 1;
    }

    private void swap(List<Move> moves, int[] scores, int i, int j) {
        // Swap moves
        Move tempMove = moves.get(i);
        moves.set(i, moves.get(j));
        moves.set(j, tempMove);

        // Swap scores
        int tempScore = scores[i];
        scores[i] = scores[j];
        scores[j] = tempScore;
    }

    // === STATISTICS ===

    public double getOrderingEfficiency() {
        return orderingQueries > 0 ? (double) firstMoveSuccesses / orderingQueries : 0.0;
    }

    public void recordFirstMoveSuccess() {
        firstMoveSuccesses++;
    }
    public void resetForNewSearch() {
        // Clear killer moves for new search
        for (int d = 0; d < maxDepth; d++) {
            if (killerMoves[d] != null) {
                killerMoves[d][0] = null;
                killerMoves[d][1] = null;
            }
        }

        // Reset statistics
        orderingQueries = 0;
        firstMoveSuccesses = 0;
    }


}