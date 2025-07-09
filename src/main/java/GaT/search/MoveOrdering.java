package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.StaticExchangeEvaluator;
import GaT.evaluation.ThreatDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * ENHANCED MOVE ORDERING ENGINE - With SEE and Threat Analysis
 * FIXED IMPLEMENTATION with proper integration
 */
public class MoveOrdering {

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
    private static final int EQUAL_CAPTURE_SCORE = 3000;
    private static final int CASTLE_APPROACH_SCORE = 2000;
    private static final int THREAT_EVASION_SCORE = 1800;
    private static final int COUNTER_THREAT_SCORE = 1500;
    private static final int HISTORY_BASE_SCORE = 1000;
    private static final int POSITIONAL_BASE_SCORE = 500;
    private static final int BAD_CAPTURE_SCORE = -1000;

    public MoveOrdering() {
        initializeTables();
    }

    private void initializeTables() {
        killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
        historyTable = new int[GameState.NUM_SQUARES][GameState.NUM_SQUARES];
        pvLine = new Move[SearchConfig.MAX_KILLER_DEPTH];
        killerAge = 0;
    }

    /**
     * STANDARD MOVE ORDERING - Fixed method signature
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves.size() <= 1) return;

        // Analyze threats in current position
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

        // Use different ordering strategies based on position characteristics
        if (threats.mustDefend) {
            orderMovesDefensive(moves, state, depth, ttEntry, threats);
        } else if (depth >= 8 && moves.size() > 20) {
            orderMovesUltimate(moves, state, depth, ttEntry, threats);
        } else if (depth >= 4) {
            orderMovesAdvanced(moves, state, depth, ttEntry, threats);
        } else {
            orderMovesBasic(moves, state, depth, ttEntry);
        }
    }

    /**
     * ENHANCED MOVE ORDERING - New signature with timeout
     */
    public void orderMovesEnhanced(List<Move> moves, GameState state, int depth,
                                   TTEntry ttEntry, Move pvMove, long timeRemaining) {
        if (moves.size() <= 1) return;

        // Quick ordering for time pressure
        if (timeRemaining < 100) {
            orderMovesQuick(moves, state, ttEntry);
            return;
        }

        // Full enhanced ordering
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        orderMovesWithThreatAnalysis(moves, state, depth, ttEntry, threats, pvMove);
    }

    /**
     * Defensive move ordering when under threat
     */
    private void orderMovesDefensive(List<Move> moves, GameState state, int depth,
                                     TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats) {
        // Prioritize defensive moves
        List<Move> defensiveMoves = threats.defensiveMoves;
        List<Move> otherMoves = new ArrayList<>();

        for (Move move : moves) {
            if (!defensiveMoves.contains(move)) {
                otherMoves.add(move);
            }
        }

        // Order defensive moves first, then others
        moves.clear();
        moves.addAll(defensiveMoves);
        moves.addAll(otherMoves);

        // Apply scoring to all moves
        orderMovesByScore(moves, state, depth, ttEntry, threats);
    }

    /**
     * Advanced move ordering for medium depths
     */
    private void orderMovesAdvanced(List<Move> moves, GameState state, int depth,
                                    TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats) {
        orderMovesByScore(moves, state, depth, ttEntry, threats);
    }

    /**
     * Ultimate move ordering for deep searches
     */
    private void orderMovesUltimate(List<Move> moves, GameState state, int depth,
                                    TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats) {
        // Phase 1: Extract TT move
        Move ttMove = extractTTMove(moves, ttEntry);

        // Phase 2: Partition moves by type
        List<Move> winningMoves = new ArrayList<>();
        List<Move> goodCaptures = new ArrayList<>();
        List<Move> neutralMoves = new ArrayList<>();
        List<Move> badCaptures = new ArrayList<>();

        for (Move move : moves) {
            if (isWinningMove(move, state)) {
                winningMoves.add(move);
            } else if (isCapture(move, state)) {
                int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                if (seeValue > 0) {
                    goodCaptures.add(move);
                } else {
                    badCaptures.add(move);
                }
            } else {
                neutralMoves.add(move);
            }
        }

        // Phase 3: Sort each category
        winningMoves.sort((a, b) -> Integer.compare(getMoveScore(b, state, depth, ttEntry, threats),
                getMoveScore(a, state, depth, ttEntry, threats)));
        goodCaptures.sort((a, b) -> Integer.compare(getMoveScore(b, state, depth, ttEntry, threats),
                getMoveScore(a, state, depth, ttEntry, threats)));
        neutralMoves.sort((a, b) -> Integer.compare(getMoveScore(b, state, depth, ttEntry, threats),
                getMoveScore(a, state, depth, ttEntry, threats)));

        // Phase 4: Rebuild move list
        moves.clear();
        if (ttMove != null) moves.add(ttMove);
        moves.addAll(winningMoves);
        moves.addAll(goodCaptures);
        moves.addAll(neutralMoves);
        moves.addAll(badCaptures);
    }

    /**
     * Basic move ordering for shallow depths
     */
    private void orderMovesBasic(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        orderMovesByScore(moves, state, depth, ttEntry, null);
    }

    /**
     * Quick move ordering for time pressure
     */
    private void orderMovesQuick(List<Move> moves, GameState state, TTEntry ttEntry) {
        // Simple TT move + captures first
        moves.sort((a, b) -> {
            int scoreA = getQuickMoveScore(a, state, ttEntry);
            int scoreB = getQuickMoveScore(b, state, ttEntry);
            return Integer.compare(scoreB, scoreA);
        });
    }
    private Move extractTTMove(List<Move> moves, TTEntry ttEntry) {
        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            Move ttMove = ttEntry.bestMove;
            moves.remove(ttMove);
            return ttMove;
        }
        return null;
    }

    /**
     * Enhanced move ordering with threat analysis
     */
    private void orderMovesWithThreatAnalysis(List<Move> moves, GameState state, int depth,
                                              TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats, Move pvMove) {
        orderMovesByScore(moves, state, depth, ttEntry, threats);
    }

    /**
     * Core move scoring and sorting
     */
    private void orderMovesByScore(List<Move> moves, GameState state, int depth,
                                   TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats) {
        moves.sort((a, b) -> {
            int scoreA = getMoveScore(a, state, depth, ttEntry, threats);
            int scoreB = getMoveScore(b, state, depth, ttEntry, threats);
            return Integer.compare(scoreB, scoreA);
        });
    }
    /**
     * Check if move is winning
     */
    private boolean isWinningMove(Move move, GameState state) {
        return capturesGuard(move, state) || reachesEnemyCastle(move, state);
    }
    private boolean reachesEnemyCastle(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;

        boolean isRed = state.redToMove;
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle;
    }

    /**
     * Comprehensive move scoring - FIXED IMPLEMENTATION
     */
    private int getMoveScore(Move move, GameState state, int depth, TTEntry ttEntry,
                             ThreatDetector.ThreatAnalysis threats) {
        int score = 0;

        // === TRANSPOSITION TABLE MOVE ===
        if (ttEntry != null && ttEntry.bestMove != null && move.equals(ttEntry.bestMove)) {
            return TT_MOVE_SCORE;
        }

        // === CAPTURES WITH SEE ===
        if (isCapture(move, state)) {
            int seeValue = StaticExchangeEvaluator.evaluate(state, move);

            if (capturesGuard(move, state)) {
                score += GUARD_CAPTURE_SCORE;
            } else if (seeValue > 0) {
                score += GOOD_CAPTURE_SCORE + seeValue;
            } else if (seeValue == 0) {
                score += EQUAL_CAPTURE_SCORE;
            } else {
                score += BAD_CAPTURE_SCORE + seeValue; // Negative value for bad captures
            }
        }

        // === THREAT-BASED SCORING ===
        if (threats != null) {
            // Defensive moves
            if (threats.defensiveMoves.contains(move)) {
                score += THREAT_EVASION_SCORE;
            }

            // Counter-threat moves
            if (createsCounterThreat(move, state, threats)) {
                score += COUNTER_THREAT_SCORE;
            }
        }

        // === CASTLE APPROACH ===
        if (approachesCastle(move, state)) {
            score += CASTLE_APPROACH_SCORE;
        }

        // === KILLER MOVES ===
        score += getKillerMoveScore(move, depth);

        // === HISTORY HEURISTIC ===
        score += getHistoryScore(move);

        // === POSITIONAL FACTORS ===
        score += getPositionalScore(move, state);

        return score;
    }

    /**
     * Quick scoring for time pressure
     */
    private int getQuickMoveScore(Move move, GameState state, TTEntry ttEntry) {
        if (ttEntry != null && ttEntry.bestMove != null && move.equals(ttEntry.bestMove)) {
            return TT_MOVE_SCORE;
        }

        if (isCapture(move, state)) {
            return capturesGuard(move, state) ? GUARD_CAPTURE_SCORE : GOOD_CAPTURE_SCORE;
        }

        return 0;
    }

    // === HELPER METHODS - FIXED IMPLEMENTATIONS ===

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean approachesCastle(Move move, GameState state) {
        int enemyCastle = state.redToMove ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        int currentDistance = Math.max(
                Math.abs(GameState.rank(move.from) - GameState.rank(enemyCastle)),
                Math.abs(GameState.file(move.from) - GameState.file(enemyCastle))
        );
        int newDistance = Math.max(
                Math.abs(GameState.rank(move.to) - GameState.rank(enemyCastle)),
                Math.abs(GameState.file(move.to) - GameState.file(enemyCastle))
        );
        return newDistance < currentDistance;
    }

    private boolean createsCounterThreat(Move move, GameState state, ThreatDetector.ThreatAnalysis threats) {
        if (threats == null) return false;

        // Simplified check - if it's a capture with good SEE value
        if (isCapture(move, state)) {
            return StaticExchangeEvaluator.evaluate(state, move) >= 200;
        }

        return false;
    }

    private int getKillerMoveScore(Move move, int depth) {
        if (depth >= killerMoves.length) return 0;

        if (move.equals(killerMoves[depth][0])) {
            return KILLER_MOVE_1_SCORE;
        } else if (move.equals(killerMoves[depth][1])) {
            return KILLER_MOVE_2_SCORE;
        }

        return 0;
    }

    private int getHistoryScore(Move move) {
        if (move.from >= historyTable.length || move.to >= historyTable[move.from].length) {
            return 0;
        }

        int historyValue = historyTable[move.from][move.to];
        return (historyValue * HISTORY_BASE_SCORE) / HISTORY_MAX;
    }

    private boolean isDevelopmentMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        // Moving pieces toward center or enemy
        return isRed ? (fromRank == 6 && toRank < 6) : (fromRank == 0 && toRank > 0);
    }

    /**
     * Check if position is endgame
     */
    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= 8; // Simplified endgame threshold
    }

    /**
     * Get guard advancement bonus
     */
    private int getGuardAdvancementBonus(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int toRank = GameState.rank(move.to);
        int toFile = GameState.file(move.to);

        // Distance to enemy castle
        int targetRank = isRed ? 0 : 6;
        int rankDistance = Math.abs(toRank - targetRank);
        int fileDistance = Math.abs(toFile - 3); // D-file

        return Math.max(0, 100 - rankDistance * 10 - fileDistance * 15);
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

    private boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redToMove && (state.redGuard & fromBit) != 0) ||
                (!state.redToMove && (state.blueGuard & fromBit) != 0);
    }

    // === KILLER MOVE MANAGEMENT ===

    public void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;

        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }
    /**
     * Check if square is central
     */
    private boolean isCentralSquare(int square) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);
        return rank >= 2 && rank <= 4 && file >= 2 && file <= 4;
    }



    public void updateHistory(Move move, int depth, int bonus) {
        if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
            historyTable[move.from][move.to] += bonus;

            // Aging to prevent overflow
            if (historyTable[move.from][move.to] > HISTORY_MAX) {
                historyTable[move.from][move.to] = historyTable[move.from][move.to] * 3 / 4;
            }
        }
    }

    public void ageHistory() {
        killerAge++;
        if (killerAge > SearchConfig.HISTORY_AGING_THRESHOLD) {
            // Age history table
            for (int i = 0; i < historyTable.length; i++) {
                for (int j = 0; j < historyTable[i].length; j++) {
                    historyTable[i][j] = historyTable[i][j] * 7 / 8;
                }
            }
            killerAge = 0;
        }
    }

    // === PRINCIPAL VARIATION ===

    public void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    public Move getPVMove(int depth) {
        return (depth < pvLine.length) ? pvLine[depth] : null;
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