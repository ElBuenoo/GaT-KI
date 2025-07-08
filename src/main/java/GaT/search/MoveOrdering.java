package GaT.search;

import GaT.evaluation.StaticExchangeEvaluator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MOVE ORDERING ENGINE - Extracted from Minimax
 * Responsible for ordering moves to maximize alpha-beta cutoffs
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
    private static final int CASTLE_APPROACH_SCORE = 2000;
    private static final int PROMOTION_SCORE = 1500;
    private static final int HISTORY_BASE_SCORE = 1000;
    private static final int POSITIONAL_BASE_SCORE = 500;

    public MoveOrdering() {
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
     * Orders moves for maximum search efficiency
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves.size() <= 1) return;

        // Use different ordering strategies based on depth and position
        if (depth >= 8 && moves.size() > 20) {
            orderMovesUltimate(moves, state, depth, ttEntry);
        } else if (depth >= 4) {
            orderMovesAdvanced(moves, state, depth, ttEntry);
        } else {
            orderMovesBasic(moves, state, depth, ttEntry);
        }
    }



    /**
     * ULTIMATE MOVE ORDERING - For deep searches and complex positions
     */
    private void orderMovesUltimate(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        // Phase 1: Extract TT move (highest priority)
        Move ttMove = extractTTMove(moves, ttEntry);

        // Phase 2: Partition moves by type
        List<Move> winningMoves = moves.stream()
                .filter(move -> isWinningMove(move, state))
                .toList();

        List<Move> captures = moves.stream()
                .filter(move -> !isWinningMove(move, state) && isCapture(move, state))
                .toList();

        List<Move> quietMoves = moves.stream()
                .filter(move -> !isWinningMove(move, state) && !isCapture(move, state))
                .toList();

        // Phase 3: Sort each category optimally
        winningMoves.sort((a, b) -> Integer.compare(scoreWinningMove(b, state), scoreWinningMove(a, state)));
        captures.sort((a, b) -> Integer.compare(scoreCaptureMove(b, state), scoreCaptureMove(a, state)));
        quietMoves.sort((a, b) -> Integer.compare(scoreQuietMove(b, state, depth), scoreQuietMove(a, state, depth)));

        // Phase 4: Rebuild move list in optimal order
        moves.clear();
        if (ttMove != null) moves.add(ttMove);
        moves.addAll(winningMoves);
        moves.addAll(captures);
        moves.addAll(quietMoves);
    }

    /**
     * ADVANCED MOVE ORDERING - Standard tournament strength
     */
    private void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        // Extract TT move first
        Move ttMove = extractTTMove(moves, ttEntry);

        // Sort remaining moves by comprehensive scoring
        if (moves.size() > 1) {
            int startIndex = (ttMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveAdvanced(a, state, depth);
                int scoreB = scoreMoveAdvanced(b, state, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }

        // Place TT move first if found
        if (ttMove != null && !moves.isEmpty() && !moves.get(0).equals(ttMove)) {
            moves.remove(ttMove);
            moves.add(0, ttMove);
        }
    }

    /**
     * BASIC MOVE ORDERING - Fast for shallow searches
     */
    private void orderMovesBasic(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        moves.sort((a, b) -> {
            int scoreA = scoreMoveBasic(a, state);
            int scoreB = scoreMoveBasic(b, state);
            return Integer.compare(scoreB, scoreA);
        });

        // Move TT move to front if available
        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            moves.remove(ttEntry.bestMove);
            moves.add(0, ttEntry.bestMove);
        }
    }

    // === MOVE SCORING METHODS ===

    /**
     * Advanced move scoring with all heuristics
     */
    private int scoreMoveAdvanced(Move move, GameState state, int depth) {
        int score = scoreMoveAdvanced(move, state, depth);

        // 1. Winning moves (checkmate, castle capture)
        if (isWinningMove(move, state)) {
            score += WINNING_MOVE_SCORE;
        }

        // 2. Captures with MVV-LVA
        else if (isCapture(move, state)) {
            int seeScore = StaticExchangeEvaluator.evaluateCapture(move, state);
            score += seeScore / 2; // Weight SEE appropriately

            // Penalty for bad captures
            if (seeScore < 0) {
                score -= 1000; // Strong penalty for losing material
            }
        }

        // 3. PV move from previous iteration
        else if (isPVMove(move, depth)) {
            score += PV_MOVE_SCORE;
        }

        // 4. Killer moves
        else if (isKillerMove(move, depth)) {
            score += getKillerMoveScore(move, depth);
        }

        // 5. History heuristic
        else {
            score += getHistoryScore(move);
            score += getPositionalScore(move, state);
        }

        return score;
    }

    /**
     * Basic move scoring for shallow searches
     */
    private int scoreMoveBasic(Move move, GameState state) {
        // Quick scoring focusing on captures and obvious moves
        if (isWinningMove(move, state)) return WINNING_MOVE_SCORE;
        if (isCapture(move, state)) return scoreCaptureMove(move, state);
        return getPositionalScore(move, state);
    }

    /**
     * Score winning moves
     */
    private int scoreWinningMove(Move move, GameState state) {
        if (capturesGuard(move, state)) {
            return GUARD_CAPTURE_SCORE;
        }
        if (reachesEnemyCastle(move, state)) {
            return GUARD_CAPTURE_SCORE + 100; // Slightly higher than guard capture
        }
        return WINNING_MOVE_SCORE;
    }

    /**
     * Score capture moves using MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
     */
    private int scoreCaptureMove(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Victim value
        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 1500; // Guard
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 100; // Tower by height
        }

        // Attacker value (subtract to prefer using less valuable pieces)
        int attackerValue = getAttackerValue(move, state);

        // MVV-LVA score
        int mvvLvaScore = victimValue * 10 - attackerValue;

        // SEE (Static Exchange Evaluation) bonus/penalty
        int seeScore = calculateSEE(move, state);

        return GOOD_CAPTURE_SCORE + mvvLvaScore + seeScore;
    }

    /**
     * Score quiet (non-capture) moves
     */
    private int scoreQuietMove(Move move, GameState state, int depth) {
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

        // Guard moves in endgame
        if (isGuardMove(move, state) && isEndgame(state)) {
            score += CASTLE_APPROACH_SCORE;
        }

        return score;
    }

    // === HEURISTIC HELPERS ===

    /**
     * Extract TT move and place it first
     */
    private Move extractTTMove(List<Move> moves, TTEntry ttEntry) {
        if (ttEntry != null && ttEntry.bestMove != null && moves.contains(ttEntry.bestMove)) {
            Move ttMove = ttEntry.bestMove;
            moves.remove(ttMove);
            return ttMove;
        }
        return null;
    }

    /**
     * Get history table score for move
     */
    private int getHistoryScore(Move move) {
        return Math.min(HISTORY_BASE_SCORE, historyTable[move.from][move.to]);
    }

    /**
     * Check if move is a killer move and get its score
     */
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

    /**
     * Check if move is from principal variation
     */
    private boolean isPVMove(Move move, int depth) {
        return depth < pvLine.length && move.equals(pvLine[depth]);
    }

    /**
     * Get positional score for move
     */
    private int getPositionalScore(Move move, GameState state) {
        int score = 0;

        // Central squares bonus
        if (isCentralSquare(move.to)) {
            score += 50;
        }

        // Guard advancement bonus
        if (isGuardMove(move, state)) {
            score += getGuardAdvancementBonus(move, state);
        }

        // Piece development bonus
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

        // Moving pieces forward from back rank
        return isRed ? (fromRank == 6 && toRank < 6) : (fromRank == 0 && toRank > 0);
    }

    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    // === COMPLEX SCORING HELPERS ===

    private int getAttackerValue(Move move, GameState state) {
        if (isGuardMove(move, state)) return 50;

        boolean isRed = state.redToMove;
        int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
        return height * 25;
    }

    /**
     * STATIC EXCHANGE EVALUATION (SEE) - Critical for avoiding bad trades
     *
     * This replaces the calculateSEE() stub in MoveOrdering.java
     * Add this method to your MoveOrdering class.
     */

    /**
     * Calculate Static Exchange Evaluation for a capture move
     * Returns the material gain/loss from the capture sequence
     */
    private int calculateSEE(Move move, GameState state) {
        return StaticExchangeEvaluator.evaluateCapture(move, state);
    }

    /**
     * Get all pieces defending a square, sorted by value (least valuable first)
     */
    private List<Integer> getDefenders(int square, GameState state, boolean defendingColor) {
        List<Integer> defenders = new ArrayList<>();

        // Check all squares for pieces that can defend
        for (int from = 0; from < GameState.NUM_SQUARES; from++) {
            if (canPieceAttackSquare(from, square, state, defendingColor)) {
                int pieceValue = getPieceValueAt(from, state, defendingColor);
                if (pieceValue > 0) {
                    defenders.add(pieceValue);
                }
            }
        }

        // Sort by value (least valuable defenders first)
        Collections.sort(defenders);
        return defenders;
    }

    /**
     * Get all pieces attacking a square, sorted by value
     */
    private List<Integer> getAttackers(int square, GameState state, boolean attackingColor) {
        List<Integer> attackers = new ArrayList<>();

        for (int from = 0; from < GameState.NUM_SQUARES; from++) {
            if (canPieceAttackSquare(from, square, state, attackingColor)) {
                int pieceValue = getPieceValueAt(from, state, attackingColor);
                if (pieceValue > 0) {
                    attackers.add(pieceValue);
                }
            }
        }

        Collections.sort(attackers);
        return attackers;
    }

    /**
     * Simulate the complete exchange sequence
     */
    private int simulateExchange(int square, int firstAttackerValue, int capturedValue,
                                 List<Integer> defenders, GameState state, boolean redAttacking) {

        List<Integer> attackers = getAttackers(square, state, redAttacking);

        // Remove the first attacker (already moved)
        if (!attackers.isEmpty() && attackers.get(0) == firstAttackerValue) {
            attackers.remove(0);
        }

        // Exchange sequence: alternating captures
        int materialBalance = capturedValue; // We captured something
        int currentAttackerValue = firstAttackerValue; // This piece is now "hanging"
        boolean redToCapture = !redAttacking; // Opponent's turn to recapture

        while (true) {
            List<Integer> currentSideAttackers = redToCapture ?
                    getAttackers(square, state, true) : defenders;

            if (currentSideAttackers.isEmpty()) {
                // No more pieces can capture
                break;
            }

            // Use the least valuable piece to recapture
            int recapturingPieceValue = currentSideAttackers.get(0);
            currentSideAttackers.remove(0);

            if (redToCapture) {
                materialBalance += currentAttackerValue; // Red gains the hanging piece
            } else {
                materialBalance -= currentAttackerValue; // Blue gains the hanging piece
            }

            // The recapturing piece is now hanging
            currentAttackerValue = recapturingPieceValue;
            redToCapture = !redToCapture;

            // Check if the exchange should continue
            // Stop if the side to move would lose material by continuing
            int potentialGain = redToCapture ? currentAttackerValue : -currentAttackerValue;
            if (potentialGain < 0) {
                // Don't make a losing recapture
                break;
            }
        }

        return redAttacking ? materialBalance : -materialBalance;
    }

    /**
     * Check if a piece at 'from' can attack 'to'
     */
    private boolean canPieceAttackSquare(int from, int to, GameState state, boolean isRed) {
        if (from == to) return false;

        // Check if there's a piece at 'from'
        if (!hasPieceAt(from, state, isRed)) {
            return false;
        }

        // Get piece type and movement range
        boolean isGuard = isRed ?
                (state.redGuard & GameState.bit(from)) != 0 :
                (state.blueGuard & GameState.bit(from)) != 0;

        int range = isGuard ? 1 :
                (isRed ? state.redStackHeights[from] : state.blueStackHeights[from]);

        if (range <= 0) return false;

        // Check if target is in range and on same rank/file
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        if (distance > range) return false;

        // Check if path is clear (for towers)
        if (!isGuard && distance > 1) {
            return isPathClearForAttack(from, to, state);
        }

        return true;
    }

    /**
     * Check if path is clear for tower attacks
     */
    private boolean isPathClearForAttack(int from, int to, GameState state) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        int step = rankDiff != 0 ? (rankDiff > 0 ? 7 : -7) : (fileDiff > 0 ? 1 : -1);
        int current = from + step;

        while (current != to) {
            if (isOccupied(current, state)) {
                return false;
            }
            current += step;
        }

        return true;
    }

    /**
     * Get piece value at a square
     */
    private int getPieceValueAt(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);

        if (isRed) {
            if ((state.redGuard & bit) != 0) {
                return 800; // Guard value
            }
            if ((state.redTowers & bit) != 0) {
                return state.redStackHeights[square] * 100; // Tower value by height
            }
        } else {
            if ((state.blueGuard & bit) != 0) {
                return 800; // Guard value
            }
            if ((state.blueTowers & bit) != 0) {
                return state.blueStackHeights[square] * 100; // Tower value by height
            }
        }

        return 0;
    }

    /**
     * Get captured piece value
     */
    private int getCapturedPieceValue(int square, GameState state, boolean capturedColor) {
        return getPieceValueAt(square, state, capturedColor);
    }

    /**
     * Get attacker piece value
     */
    private int getAttackerPieceValue(int square, GameState state, boolean attackerColor) {
        return getPieceValueAt(square, state, attackerColor);
    }

    /**
     * Check if there's a piece of the given color at the square
     */
    private boolean hasPieceAt(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);
        if (isRed) {
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        } else {
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        }
    }

    /**
     * Check if square is occupied by any piece
     */
    private boolean isOccupied(int square, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                & GameState.bit(square)) != 0;
    }



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

    // === KILLER MOVE MANAGEMENT ===

    /**
     * Store a killer move
     */
    public void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return; // Already stored

        // Shift moves down
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;

        // Update statistics
        SearchStatistics.getInstance().incrementKillerMoveHits();
    }

    /**
     * Update history table for move
     */
    public void updateHistory(Move move, int depth) {
        historyTable[move.from][move.to] += depth * depth;

        // Prevent overflow
        if (historyTable[move.from][move.to] > HISTORY_MAX) {
            ageHistoryTable();
        }

        SearchStatistics.getInstance().incrementHistoryMoveHits();
    }

    /**
     * Store principal variation move
     */
    public void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    /**
     * Reset killer moves (called between searches)
     */
    public void resetKillerMoves() {
        killerAge++;
        if (killerAge > SearchConfig.HISTORY_AGING_THRESHOLD) {
            killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
            killerAge = 0;
        }
    }

    /**
     * Age history table to prevent stale data
     */
    private void ageHistoryTable() {
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                historyTable[i][j] /= 2;
            }
        }
    }

    /**
     * Clear all move ordering data
     */
    public void clear() {
        initializeTables();
    }

    // === STATISTICS AND DEBUGGING ===

    /**
     * Get move ordering statistics
     */
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



    // Add these methods to MoveOrdering.java:

    /**
     * Enhanced move ordering with all advanced features
     */
    public void orderMovesEnhanced(List<Move> moves, GameState state, int depth,
                                   TTEntry ttEntry, List<Move> pvLine, long remainingTime) {
        if (moves.size() <= 1) return;

        // For very deep searches or critical positions, use ultimate ordering
        if (depth >= 10 || remainingTime < 5000) {
            orderMovesUltimate(moves, state, depth, ttEntry);
        } else {
            orderMovesAdvanced(moves, state, depth, ttEntry);
        }
    }

    /**
     * Score move with enhanced features including threat analysis
     */
    public int scoreMoveEnhanced(Move move, GameState state, int depth,
                                 TTEntry ttEntry, List<Move> pvLine, ThreatAnalysis threats) {
        int score = scoreMoveAdvanced(move, state, depth);

        // Add threat-based scoring if available
        if (threats != null && threats.isThreatMove(move)) {
            score += 3000;
        }

        return score;
    }

    /**
     * Update history on cutoff for better move ordering
     */
    public void updateHistoryOnCutoff(Move move, boolean isRed, int depth) {
        updateHistory(move, depth);
        // Additional logic for side-specific history can be added here
    }

    /**
     * Threat analysis helper class
     */
    public static class ThreatAnalysis {
        private final GameState state;
        private final List<Move> threats;

        public ThreatAnalysis() {
            this.state = null;
            this.threats = new ArrayList<>();
        }

        public ThreatAnalysis(GameState state) {
            this.state = state;
            this.threats = analyzeThreatts(state);
        }

        public boolean isThreatMove(Move move) {
            return threats.contains(move);
        }

        private List<Move> analyzeThreatts(GameState state) {
            // Simple threat detection - moves that attack enemy guard
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