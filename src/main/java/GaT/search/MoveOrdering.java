package GaT.search;

import GaT.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MOVE ORDERING ENGINE - Now uses dependency injection
 */
public class MoveOrdering {

    // ✅ INJECT STATISTICS DEPENDENCY
    private final SearchStatistics statistics;

    // === KILLER MOVES TABLE ===
    private Move[][] killerMoves;
    private int killerAge = 0;

    // === HISTORY HEURISTIC TABLE ===
    private int[][] historyTable;
    private static final int HISTORY_MAX = SearchConfig.HISTORY_MAX_VALUE;

    // === PRINCIPAL VARIATION TABLE ===
    private Move[] pvLine;

    // === MOVE SCORING CONSTANTS ===
    private static final int TT_MOVE_SCORE = 10000;
    private static final int WINNING_MOVE_SCORE = 9000;
    private static final int GUARD_CAPTURE_SCORE = 8000;
    private static final int PV_MOVE_SCORE = 7000;
    private static final int KILLER_MOVE_1_SCORE = 6000;
    private static final int KILLER_MOVE_2_SCORE = 5000;
    private static final int GOOD_CAPTURE_SCORE = 4000;
    private static final int CASTLE_APPROACH_SCORE = 2000;
    private static final int PROMOTION_SCORE = 1500;
    private static final int HISTORY_BASE_SCORE = 1000;
    private static final int POSITIONAL_BASE_SCORE = 500;

    // ✅ CONSTRUCTOR INJECTION
    public MoveOrdering(SearchStatistics statistics) {
        this.statistics = statistics;
        initializeTables();
    }

    /**
     * Initialize all move ordering tables
     */
    private void initializeTables() {
        killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
        historyTable = new int[GameState.NUM_SQUARES][GameState.NUM_SQUARES];
        pvLine = new Move[SearchConfig.MAX_KILLER_DEPTH];
        killerAge = 0;
    }

    /**
     * MAIN MOVE ORDERING INTERFACE
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves.size() <= 1) return;

        if (depth >= 8 && moves.size() > 20) {
            orderMovesUltimate(moves, state, depth, ttEntry);
        } else if (depth >= 4) {
            orderMovesAdvanced(moves, state, depth, ttEntry);
        } else {
            orderMovesBasic(moves, state, depth, ttEntry);
        }
    }

    /**
     * Store a killer move
     */
    public void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return;

        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;

        // ✅ USE INJECTED STATISTICS
        statistics.incrementKillerMoveHits();
    }

    /**
     * Update history table for move
     */
    public void updateHistory(Move move, int depth) {
        historyTable[move.from][move.to] += depth * depth;

        if (historyTable[move.from][move.to] > HISTORY_MAX) {
            ageHistoryTable();
        }

        // ✅ USE INJECTED STATISTICS
        statistics.incrementHistoryMoveHits();
    }

    /**
     * Store principal variation move
     */
    public void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    // ✅ ALL OTHER METHODS STAY THE SAME (they don't use SearchStatistics)
    private void orderMovesUltimate(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        // Extract TT move
        Move ttMove = extractTTMove(moves, ttEntry);

        // Partition moves by type
        List<Move> winningMoves = moves.stream()
                .filter(move -> isWinningMove(move, state))
                .toList();

        List<Move> captures = moves.stream()
                .filter(move -> !isWinningMove(move, state) && isCapture(move, state))
                .toList();

        List<Move> quietMoves = moves.stream()
                .filter(move -> !isWinningMove(move, state) && !isCapture(move, state))
                .toList();

        // Sort each category
        winningMoves.sort((a, b) -> Integer.compare(scoreWinningMove(b, state), scoreWinningMove(a, state)));
        captures.sort((a, b) -> Integer.compare(scoreCaptureMove(b, state), scoreCaptureMove(a, state)));
        quietMoves.sort((a, b) -> Integer.compare(scoreQuietMove(b, state, depth), scoreQuietMove(a, state, depth)));

        // Rebuild move list
        moves.clear();
        if (ttMove != null) moves.add(ttMove);
        moves.addAll(winningMoves);
        moves.addAll(captures);
        moves.addAll(quietMoves);
    }

    private void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        Move ttMove = extractTTMove(moves, ttEntry);

        if (moves.size() > 1) {
            int startIndex = (ttMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveAdvanced(a, state, depth);
                int scoreB = scoreMoveAdvanced(b, state, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }

        if (ttMove != null && !moves.isEmpty() && !moves.get(0).equals(ttMove)) {
            moves.remove(ttMove);
            moves.add(0, ttMove);
        }
    }

    private void orderMovesBasic(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        moves.sort((a, b) -> {
            int scoreA = scoreMoveBasic(a, state);
            int scoreB = scoreMoveBasic(b, state);
            return Integer.compare(scoreB, scoreA);
        });

        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            moves.remove(ttEntry.bestMove);
            moves.add(0, ttEntry.bestMove);
        }
    }

    // === ALL OTHER HELPER METHODS STAY THE SAME ===
    private int scoreMoveAdvanced(Move move, GameState state, int depth) {
        int score = 0;

        if (isWinningMove(move, state)) {
            score += WINNING_MOVE_SCORE;
        } else if (isCapture(move, state)) {
            score += scoreCaptureMove(move, state);
        } else if (isPVMove(move, depth)) {
            score += PV_MOVE_SCORE;
        } else if (isKillerMove(move, depth)) {
            score += getKillerMoveScore(move, depth);
        } else {
            score += getHistoryScore(move);
            score += getPositionalScore(move, state);
        }

        return score;
    }

    private int scoreMoveBasic(Move move, GameState state) {
        if (isWinningMove(move, state)) return WINNING_MOVE_SCORE;
        if (isCapture(move, state)) return scoreCaptureMove(move, state);
        return getPositionalScore(move, state);
    }

    // === HELPER METHODS (all stay the same) ===
    private Move extractTTMove(List<Move> moves, TTEntry ttEntry) {
        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            Move ttMove = ttEntry.bestMove;
            moves.remove(ttMove);
            return ttMove;
        }
        return null;
    }

    private boolean isWinningMove(Move move, GameState state) {
        return capturesGuard(move, state) || reachesEnemyCastle(move, state);
    }

    private boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;
        return ((isRed ? state.blueGuard : state.redGuard) & toBit) != 0;
    }

    private boolean reachesEnemyCastle(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;
        boolean isRed = state.redToMove;
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    private int scoreWinningMove(Move move, GameState state) {
        if (capturesGuard(move, state)) return GUARD_CAPTURE_SCORE;
        if (reachesEnemyCastle(move, state)) return GUARD_CAPTURE_SCORE + 100;
        return WINNING_MOVE_SCORE;
    }

    private int scoreCaptureMove(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 1500;
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 100;
        }

        int attackerValue = getAttackerValue(move, state);
        int mvvLvaScore = victimValue * 10 - attackerValue;
        int seeScore = calculateSEE(move, state);

        return GOOD_CAPTURE_SCORE + mvvLvaScore + seeScore;
    }

    private int scoreQuietMove(Move move, GameState state, int depth) {
        int score = 0;

        if (isPVMove(move, depth)) {
            score += PV_MOVE_SCORE;
        } else if (isKillerMove(move, depth)) {
            score += getKillerMoveScore(move, depth);
        }

        score += getHistoryScore(move);
        score += getPositionalScore(move, state);

        if (isGuardMove(move, state) && isEndgame(state)) {
            score += CASTLE_APPROACH_SCORE;
        }

        return score;
    }

    private int getHistoryScore(Move move) {
        return Math.min(HISTORY_BASE_SCORE, historyTable[move.from][move.to]);
    }

    private boolean isKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return false;
        return move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1]);
    }

    private int getKillerMoveScore(Move move, int depth) {
        if (depth >= killerMoves.length) return 0;
        if (move.equals(killerMoves[depth][0])) return KILLER_MOVE_1_SCORE;
        if (move.equals(killerMoves[depth][1])) return KILLER_MOVE_2_SCORE;
        return 0;
    }

    private boolean isPVMove(Move move, int depth) {
        return depth < pvLine.length && move.equals(pvLine[depth]);
    }

    private int getPositionalScore(Move move, GameState state) {
        int score = 0;

        if (isCentralSquare(move.to)) {
            score += 50;
        }

        if (isGuardMove(move, state)) {
            score += getGuardAdvancementBonus(move, state);
        }

        if (isDevelopmentMove(move, state)) {
            score += 30;
        }

        return score;
    }

    private boolean isCentralSquare(int square) {
        int file = GameState.file(square);
        int rank = GameState.rank(square);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
    }

    private boolean isDevelopmentMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);
        return isRed ? (fromRank == 6 && toRank < 6) : (fromRank == 0 && toRank > 0);
    }

    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    private int getAttackerValue(Move move, GameState state) {
        if (isGuardMove(move, state)) return 50;
        boolean isRed = state.redToMove;
        int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
        return height * 25;
    }

    private int calculateSEE(Move move, GameState state) {
        return 0; // Simplified
    }

    private int getGuardAdvancementBonus(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int toRank = GameState.rank(move.to);
        int toFile = GameState.file(move.to);

        int targetRank = isRed ? 0 : 6;
        int rankDistance = Math.abs(toRank - targetRank);
        int fileDistance = Math.abs(toFile - 3);

        return Math.max(0, 100 - rankDistance * 10 - fileDistance * 15);
    }

    private void ageHistoryTable() {
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                historyTable[i][j] /= 2;
            }
        }
    }

    public void resetKillerMoves() {
        killerAge++;
        if (killerAge > SearchConfig.HISTORY_AGING_THRESHOLD) {
            killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
            killerAge = 0;
        }
    }

    public void clear() {
        initializeTables();
    }

    public String getStatistics() {
        int killerCount = 0;
        int historyCount = 0;

        for (int i = 0; i < killerMoves.length; i++) {
            if (killerMoves[i][0] != null) killerCount++;
            if (killerMoves[i][1] != null) killerCount++;
        }

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                if (historyTable[i][j] > 0) historyCount++;
            }
        }

        return String.format("MoveOrdering: %d killers, %d history entries, age=%d",
                killerCount, historyCount, killerAge);
    }
}