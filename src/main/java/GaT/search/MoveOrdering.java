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
 *
 * IMPROVEMENTS:
 * ✅ Real Static Exchange Evaluation for captures
 * ✅ Threat-based move ordering
 * ✅ Enhanced scoring with tactical awareness
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
     * ENHANCED MOVE ORDERING with SEE and Threat Analysis
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

        // Order defensive moves by quality
        defensiveMoves.sort((a, b) -> {
            int scoreA = scoreDefensiveMove(a, state, threats);
            int scoreB = scoreDefensiveMove(b, state, threats);
            return Integer.compare(scoreB, scoreA);
        });

        // Order other moves normally
        otherMoves.sort((a, b) -> {
            int scoreA = scoreMoveAdvancedWithSEE(a, state, depth);
            int scoreB = scoreMoveAdvancedWithSEE(b, state, depth);
            return Integer.compare(scoreB, scoreA);
        });

        // Rebuild list with defensive moves first
        moves.clear();
        moves.addAll(defensiveMoves);
        moves.addAll(otherMoves);

        // TT move still gets highest priority if it's defensive
        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            moves.remove(ttEntry.bestMove);
            moves.add(0, ttEntry.bestMove);
        }
    }

    /**
     * Ultimate move ordering for deep searches
     */
    private void orderMovesUltimate(List<Move> moves, GameState state, int depth,
                                    TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats) {
        // Extract TT move
        Move ttMove = extractTTMove(moves, ttEntry);

        // Categorize moves with SEE
        List<Move> winningMoves = new ArrayList<>();
        List<Move> goodCaptures = new ArrayList<>();
        List<Move> equalCaptures = new ArrayList<>();
        List<Move> badCaptures = new ArrayList<>();
        List<Move> quietMoves = new ArrayList<>();

        for (Move move : moves) {
            if (isWinningMove(move, state)) {
                winningMoves.add(move);
            } else if (isCapture(move, state)) {
                int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                if (seeValue > 100) {
                    goodCaptures.add(move);
                } else if (seeValue >= 0) {
                    equalCaptures.add(move);
                } else {
                    badCaptures.add(move);
                }
            } else {
                quietMoves.add(move);
            }
        }

        // Sort each category
        winningMoves.sort((a, b) -> Integer.compare(scoreWinningMove(b, state), scoreWinningMove(a, state)));
        goodCaptures.sort((a, b) -> Integer.compare(scoreCaptureWithSEE(b, state), scoreCaptureWithSEE(a, state)));
        equalCaptures.sort((a, b) -> Integer.compare(scoreCaptureWithSEE(b, state), scoreCaptureWithSEE(a, state)));
        quietMoves.sort((a, b) -> Integer.compare(scoreQuietMove(b, state, depth, threats), scoreQuietMove(a, state, depth, threats)));
        badCaptures.sort((a, b) -> Integer.compare(scoreCaptureWithSEE(b, state), scoreCaptureWithSEE(a, state)));

        // Rebuild move list
        moves.clear();
        if (ttMove != null) moves.add(ttMove);
        moves.addAll(winningMoves);
        moves.addAll(goodCaptures);
        moves.addAll(equalCaptures);
        moves.addAll(quietMoves);
        moves.addAll(badCaptures); // Bad captures go last
    }

    /**
     * Advanced move ordering with SEE
     */
    private void orderMovesAdvanced(List<Move> moves, GameState state, int depth,
                                    TTEntry ttEntry, ThreatDetector.ThreatAnalysis threats) {
        Move ttMove = extractTTMove(moves, ttEntry);

        if (moves.size() > 1) {
            int startIndex = (ttMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveAdvancedWithSEE(a, state, depth);
                int scoreB = scoreMoveAdvancedWithSEE(b, state, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }

        if (ttMove != null && !moves.isEmpty() && !moves.get(0).equals(ttMove)) {
            moves.remove(ttMove);
            moves.add(0, ttMove);
        }
    }

    /**
     * Basic move ordering
     */
    private void orderMovesBasic(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        moves.sort((a, b) -> {
            int scoreA = scoreMoveBasicWithSEE(a, state);
            int scoreB = scoreMoveBasicWithSEE(b, state);
            return Integer.compare(scoreB, scoreA);
        });

        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            moves.remove(ttEntry.bestMove);
            moves.add(0, ttEntry.bestMove);
        }
    }

    // === ENHANCED MOVE SCORING WITH SEE ===

    /**
     * Advanced move scoring with SEE integration
     */
    private int scoreMoveAdvancedWithSEE(Move move, GameState state, int depth) {
        int score = 0;

        // 1. Winning moves
        if (isWinningMove(move, state)) {
            score += WINNING_MOVE_SCORE;
        }

        // 2. Captures with SEE evaluation
        else if (isCapture(move, state)) {
            score += scoreCaptureWithSEE(move, state);
        }

        // 3. PV move
        else if (isPVMove(move, depth)) {
            score += PV_MOVE_SCORE;
        }

        // 4. Killer moves
        else if (isKillerMove(move, depth)) {
            score += getKillerMoveScore(move, depth);
        }

        // 5. History heuristic and positional
        else {
            score += getHistoryScore(move);
            score += getPositionalScore(move, state);
        }

        return score;
    }

    /**
     * Basic move scoring with SEE
     */
    private int scoreMoveBasicWithSEE(Move move, GameState state) {
        if (isWinningMove(move, state)) return WINNING_MOVE_SCORE;
        if (isCapture(move, state)) return scoreCaptureWithSEE(move, state);
        return getPositionalScore(move, state);
    }

    /**
     * Score capture moves using real SEE
     */
    private int scoreCaptureWithSEE(Move move, GameState state) {
        // Get SEE value
        int seeValue = StaticExchangeEvaluator.evaluate(state, move);

        // Base capture score
        int score = GOOD_CAPTURE_SCORE;

        // Adjust based on SEE result
        if (seeValue > 100) {
            score += seeValue; // Winning capture
        } else if (seeValue >= 0) {
            score = EQUAL_CAPTURE_SCORE + seeValue; // Equal or slightly winning
        } else {
            score = BAD_CAPTURE_SCORE + seeValue; // Losing capture
        }

        // MVV bonus (Most Valuable Victim)
        int victimValue = getVictimValue(move, state);
        score += victimValue / 10;

        return score;
    }

    /**
     * Score defensive moves
     */
    private int scoreDefensiveMove(Move move, GameState state, ThreatDetector.ThreatAnalysis threats) {
        int score = 0;

        // Check how many threats this move addresses
        int threatsAddressed = 0;
        for (ThreatDetector.Threat threat : threats.immediateThreats) {
            if (addressesThreat(move, threat, state)) {
                threatsAddressed++;
                score += threat.threatValue / 2;
            }
        }

        if (threatsAddressed > 1) {
            score += 300; // Multi-purpose defense bonus
        }

        // Counter-threat bonus
        if (createsCounterThreat(move, state)) {
            score += COUNTER_THREAT_SCORE;
        }

        // SEE check for captures
        if (isCapture(move, state)) {
            int seeValue = StaticExchangeEvaluator.evaluate(state, move);
            if (seeValue >= 0) {
                score += seeValue;
            } else {
                score -= 100; // Penalty for bad defensive capture
            }
        }

        return score + THREAT_EVASION_SCORE;
    }

    /**
     * Score quiet moves with threat awareness
     */
    private int scoreQuietMove(Move move, GameState state, int depth, ThreatDetector.ThreatAnalysis threats) {
        int score = 0;

        // PV move
        if (isPVMove(move, depth)) {
            score += PV_MOVE_SCORE;
        }

        // Killer moves
        else if (isKillerMove(move, depth)) {
            score += getKillerMoveScore(move, depth);
        }

        // History heuristic
        score += getHistoryScore(move);

        // Positional scoring
        score += getPositionalScore(move, state);

        // Threat-based adjustments
        if (createsThreat(move, state)) {
            score += 200;
        }

        // Guard moves in endgame
        if (isGuardMove(move, state) && isEndgame(state)) {
            score += CASTLE_APPROACH_SCORE;
        }

        return score;
    }

    // === HELPER METHODS ===

    private boolean addressesThreat(Move move, ThreatDetector.Threat threat, GameState state) {
        // Move the threatened piece
        if (move.from == threat.targetSquare) return true;

        // Capture the attacker
        if (move.to == threat.attackingMove.from) return true;

        // Block the threat
        if (isOnPath(threat.attackingMove.from, threat.targetSquare, move.to)) return true;

        return false;
    }

    private boolean createsCounterThreat(Move move, GameState state) {
        GameState copy = state.copy();
        copy.applyMove(move);

        // Quick check for major threats
        List<Move> ourMoves = MoveGenerator.generateAllMoves(copy);
        for (Move ourMove : ourMoves) {
            if (capturesGuard(ourMove, copy) || reachesEnemyCastle(ourMove, copy)) {
                return true;
            }
        }

        return false;
    }

    private boolean createsThreat(Move move, GameState state) {
        GameState copy = state.copy();
        copy.applyMove(move);

        ThreatDetector.ThreatAnalysis newThreats = ThreatDetector.analyzeThreats(copy);
        return !newThreats.immediateThreats.isEmpty();
    }

    private int getVictimValue(Move move, GameState state) {
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

    private boolean isOnPath(int from, int to, int check) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        if (rankDiff != 0 && fileDiff != 0) return false;

        int checkRank = GameState.rank(check);
        int checkFile = GameState.file(check);
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        if (rankDiff == 0) {
            return checkRank == fromRank &&
                    checkFile >= Math.min(fromFile, toFile) &&
                    checkFile <= Math.max(fromFile, toFile);
        } else {
            return checkFile == fromFile &&
                    checkRank >= Math.min(fromRank, toRank) &&
                    checkRank <= Math.max(fromRank, toRank);
        }
    }

    // [Include all the other helper methods from the original MoveOrdering class]
    // ... (rest of the helper methods remain the same)

    private Move extractTTMove(List<Move> moves, TTEntry ttEntry) {
        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            Move ttMove = ttEntry.bestMove;
            moves.remove(ttMove);
            return ttMove;
        }
        return null;
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

    // === MOVE CLASSIFICATION ===

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

    private int getGuardAdvancementBonus(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int toRank = GameState.rank(move.to);
        int toFile = GameState.file(move.to);

        int targetRank = isRed ? 0 : 6;
        int rankDistance = Math.abs(toRank - targetRank);
        int fileDistance = Math.abs(toFile - 3);

        return Math.max(0, 100 - rankDistance * 10 - fileDistance * 15);
    }

    // === KILLER MOVE MANAGEMENT ===

    public void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return;

        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;

        SearchStatistics.getInstance().incrementKillerMoveHits();
    }

    public void updateHistory(Move move, int depth) {
        historyTable[move.from][move.to] += depth * depth;

        if (historyTable[move.from][move.to] > HISTORY_MAX) {
            ageHistoryTable();
        }

        SearchStatistics.getInstance().incrementHistoryMoveHits();
    }

    public void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    public void resetKillerMoves() {
        killerAge++;
        if (killerAge > SearchConfig.HISTORY_AGING_THRESHOLD) {
            killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
            killerAge = 0;
        }
    }

    private void ageHistoryTable() {
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                historyTable[i][j] /= 2;
            }
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

    // === ENHANCED INTERFACES ===

    public void orderMovesEnhanced(List<Move> moves, GameState state, int depth,
                                   TTEntry ttEntry, List<Move> pvLine, long remainingTime) {
        if (moves.size() <= 1) return;

        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

        if (remainingTime < 5000 || threats.threatLevel >= 8) {
            orderMovesDefensive(moves, state, depth, ttEntry, threats);
        } else if (depth >= 10 || remainingTime < 5000) {
            orderMovesUltimate(moves, state, depth, ttEntry, threats);
        } else {
            orderMovesAdvanced(moves, state, depth, ttEntry, threats);
        }
    }

    public int scoreMoveEnhanced(Move move, GameState state, int depth,
                                 TTEntry ttEntry, List<Move> pvLine, ThreatAnalysis threats) {
        int score = scoreMoveAdvancedWithSEE(move, state, depth);

        if (threats != null && threats.isThreatMove(move)) {
            score += 3000;
        }

        return score;
    }

    public void updateHistoryOnCutoff(Move move, boolean isRed, int depth) {
        updateHistory(move, depth);
    }

    public static class ThreatAnalysis {
        private final GameState state;
        private final List<Move> threats;

        public ThreatAnalysis() {
            this.state = null;
            this.threats = new ArrayList<>();
        }

        public ThreatAnalysis(GameState state) {
            this.state = state;
            this.threats = analyzeThreats(state);
        }

        public boolean isThreatMove(Move move) {
            return threats.contains(move);
        }

        private List<Move> analyzeThreats(GameState state) {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            List<Move> threatMoves = new ArrayList<>();

            for (Move move : allMoves) {
                if (Minimax.isCapture(move, state)) {
                    long toBit = GameState.bit(move.to);
                    boolean isRed = state.redToMove;
                    boolean capturesGuard = ((isRed ? state.blueGuard : state.redGuard) & toBit) != 0;
                    if (capturesGuard) {
                        threatMoves.add(move);
                    }
                }
            }

            return threatMoves;
        }
    }
}