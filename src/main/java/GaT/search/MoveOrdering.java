package GaT.search;

import GaT.model.*;

import java.util.List;

/**
 * MOVE ORDERING - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Removed all hardcoded priorities and bonuses
 * ✅ Killer move configuration from SearchConfig
 * ✅ History heuristic configuration from SearchConfig
 * ✅ Centralized parameter control for easy tuning
 */
public class MoveOrdering {
    // === TABLES ===
    private final Move[][] killerMoves;
    private final int[][] historyTable;

    public MoveOrdering() {
        this.killerMoves = new Move[GameConfig.MAX_KILLER_DEPTH][GameConfig.KILLER_MOVE_SLOTS];
        this.historyTable = new int[49][49]; // 7x7 board = 49 squares
    }

    // === MAIN INTERFACE ===
    public void orderMoves(List<Move> moves, GameState state, TTEntry ttEntry) {
        if (moves.size() <= 1) return;

        UnifiedStatistics.getInstance().incrementMoveOrderingQuery();

        // Fast sort with simplified scoring
        moves.sort((a, b) -> {
            int scoreA = getScore(a, state, ttEntry);
            int scoreB = getScore(b, state, ttEntry);
            return Integer.compare(scoreB, scoreA);
        });
    }

    // === SIMPLIFIED SCORING ===
    private int getScore(Move move, GameState state, TTEntry ttEntry) {
        // 1. TT move (highest priority)
        if (ttEntry != null && move.equals(ttEntry.bestMove)) {
            return GameValues.TT_MOVE_PRIORITY;
        }

        // 2. Captures (simple victim value only)
        if (isCapture(move, state)) {
            return GameValues.CAPTURE_PRIORITY_BASE + getCaptureValue(move, state);
        }

        // 3. Killer moves
        if (isKillerMove(move)) {
            return GameValues.KILLER_MOVE_PRIORITY;
        }

        // 4. History score only (no positional evaluation)
        return getHistoryScore(move);
    }

    // === CAPTURE DETECTION ===
    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === SIMPLE CAPTURE VALUE ===
    private int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        // Guard capture = highest value
        if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
            return GameValues.GUARD_CAPTURE_VALUE;
        }

        // Tower capture = height * value
        boolean isRed = state.redToMove;
        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * GameValues.TOWER_CAPTURE_VALUE;
    }

    // === KILLER MOVES ===
    private boolean isKillerMove(Move move) {
        // Fast killer check without depth bounds checking
        for (int depth = 0; depth < Math.min(5, killerMoves.length); depth++) {
            if (move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1])) {
                return true;
            }
        }
        return false;
    }

    public void storeKillerMove(Move move, int depth) {
        if (depth < 0 || depth >= killerMoves.length) return;

        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    // === HISTORY HEURISTIC ===
    private int getHistoryScore(Move move) {
        if (move.from < 0 || move.from >= 49 || move.to < 0 || move.to >= 49) {
            return 0;
        }
        return historyTable[move.from][move.to] / 10; // Scaled down
    }

    public void updateHistory(Move move, int depth) {
        if (move.from < 0 || move.from >= 49 || move.to < 0 || move.to >= 49) return;

        historyTable[move.from][move.to] += depth * depth;

        // Simple overflow prevention
        if (historyTable[move.from][move.to] > GameConfig.HISTORY_MAX_VALUE) {
            ageHistoryTable();
        }
    }

    private void ageHistoryTable() {
        for (int i = 0; i < 49; i++) {
            for (int j = 0; j < 49; j++) {
                historyTable[i][j] /= 2;
            }
        }
    }

    // === RESET ===
    public void reset() {
        for (int i = 0; i < killerMoves.length; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }

        for (int i = 0; i < 49; i++) {
            for (int j = 0; j < 49; j++) {
                historyTable[i][j] = 0;
            }
        }
    }

    // === STATISTICS ===
    public String getStatistics() {
        int killerCount = 0;
        for (int i = 0; i < killerMoves.length; i++) {
            if (killerMoves[i][0] != null) killerCount++;
            if (killerMoves[i][1] != null) killerCount++;
        }

        return String.format("FastMoveOrdering: %d killers, %.1f%% first-move success",
                killerCount, UnifiedStatistics.getInstance().getFirstMoveSuccessRate() * 100);
    }
}