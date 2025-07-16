package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.ConsolidatedSearchConfig;

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

    // === CORE TABLES USING SEARCHCONFIG ===
    private Move[][] killerMoves;
    private int[][] historyTable;
    private Move[] pvLine;
    private int killerAge = 0;

    // === CONSTRUCTOR WITH SEARCHCONFIG INITIALIZATION ===
    public MoveOrdering() {
        initializeTables();
        validateSearchConfig();
    }

    private void initializeTables() {
        // Use SearchConfig parameters instead of hardcoded values
        killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
        historyTable = new int[GameState.NUM_SQUARES][GameState.NUM_SQUARES];
        pvLine = new Move[SearchConfig.MAX_KILLER_DEPTH];
        killerAge = 0;

        System.out.println("🔧 MoveOrdering initialized with SearchConfig:");
        System.out.println("   MAX_KILLER_DEPTH: " + SearchConfig.MAX_KILLER_DEPTH);
        System.out.println("   KILLER_MOVE_SLOTS: " + SearchConfig.KILLER_MOVE_SLOTS);
        System.out.println("   HISTORY_MAX_VALUE: " + SearchConfig.HISTORY_MAX_VALUE);
    }

    // === MAIN INTERFACE WITH SEARCHCONFIG PRIORITIES ===

    /**
     * Move ordering using SearchConfig priorities
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.size() <= 1) return;

        if (state == null) {
            System.err.println("❌ ERROR: Null state in orderMoves - using basic ordering");
            orderMovesBasic(moves);
            return;
        }

        if (!state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in orderMoves - using basic ordering");
            orderMovesBasic(moves);
            return;
        }

        try {
            moves.sort((a, b) -> {
                try {
                    int scoreA = scoreMoveSafe(a, state, depth, ttEntry);
                    int scoreB = scoreMoveSafe(b, state, depth, ttEntry);
                    return Integer.compare(scoreB, scoreA);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Move comparison failed: " + e.getMessage());
                    return 0;
                }
            });
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move sorting failed: " + e.getMessage());
            orderMovesBasic(moves);
        }

        SearchStatistics.getInstance().incrementTotalMoveOrderingQueries();
    }

    /**
     * Safe move scoring using SearchConfig priorities
     */
    private int scoreMoveSafe(Move move, GameState state, int depth, TTEntry ttEntry) {
        if (move == null) return 0;
        if (state == null) return scoreBasic(move, depth, ttEntry);
        if (!state.isValid()) return scoreBasic(move, depth, ttEntry);

        try {
            // === 1. TT-MOVE (HIGHEST PRIORITY) ===
            if (ttEntry != null && move.equals(ttEntry.bestMove)) {
                return SearchConfig.MOVE_ORDERING_TT_PRIORITY;
            }

            // === 2. CAPTURES (MVV-LVA) ===
            if (isCaptureS(move, state)) {
                return SearchConfig.MOVE_ORDERING_CAPTURE_BASE + evaluateMVVLVA(move, state);
            }

            // === 3. KILLER MOVES ===
            if (isKillerMove(move, depth)) {
                return getKillerPriority(move, depth);
            }

            // === 4. HISTORY HEURISTIC ===
            int historyScore = getUnifiedHistoryScore(move, state);

            // === 5. POSITIONAL HINTS ===
            int positionalScore = getBasicPositionalScore(move, state);

            return historyScore + positionalScore;

        } catch (Exception e) {
            System.err.println("❌ ERROR: Move scoring failed for move " + move + ": " + e.getMessage());
            return scoreBasic(move, depth, ttEntry);
        }
    }

    // === MVV-LVA WITH SEARCHCONFIG PARAMETERS ===

    private int evaluateMVVLVA(Move move, GameState state) {
        try {
            int victimValue = getVictimValue(move, state);
            int attackerValue = getAttackerValue(move, state);

            // Use SearchConfig multipliers instead of hardcoded values
            return victimValue * SearchConfig.MVV_MULTIPLIER - attackerValue * SearchConfig.LVA_MULTIPLIER;
        } catch (Exception e) {
            return SearchConfig.DEFAULT_CAPTURE_BONUS;
        }
    }

    private int getVictimValue(Move move, GameState state) {
        try {
            long toBit = GameState.bit(move.to);

            // Guard capture = highest value
            if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
                return SearchConfig.GUARD_CAPTURE_VALUE;
            }

            // Tower capture = height-based value
            boolean isRed = state.redToMove;
            int victimHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            return victimHeight * SearchConfig.TOWER_HEIGHT_VALUE;
        } catch (Exception e) {
            return SearchConfig.DEFAULT_VICTIM_VALUE;
        }
    }

    private int getAttackerValue(Move move, GameState state) {
        try {
            // Guard = low value (prefer guard attacks)
            if (isGuardMoveS(move, state)) {
                return SearchConfig.GUARD_ATTACKER_VALUE;
            }

            // Tower = height value
            boolean isRed = state.redToMove;
            int attackerHeight = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
            return attackerHeight * SearchConfig.TOWER_HEIGHT_VALUE;
        } catch (Exception e) {
            return SearchConfig.DEFAULT_ATTACKER_VALUE;
        }
    }

    // === HISTORY SCORING WITH SEARCHCONFIG ===

    private int getUnifiedHistoryScore(Move move, GameState state) {
        try {
            if (isCaptureS(move, state)) return 0; // Only for quiet moves

            int historyScore = 0;

            if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
                historyScore = historyTable[move.from][move.to] / SearchConfig.HISTORY_SCORE_DIVISOR;
            }

            return Math.min(historyScore, SearchConfig.HISTORY_MAX_SCORE);
        } catch (Exception e) {
            return 0;
        }
    }

    // === KILLER MOVES WITH SEARCHCONFIG ===

    private boolean isKillerMove(Move move, int depth) {
        try {
            if (depth >= SearchConfig.MAX_KILLER_DEPTH) return false;
            return move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1]);
        } catch (Exception e) {
            return false;
        }
    }

    private int getKillerPriority(Move move, int depth) {
        try {
            if (depth >= SearchConfig.MAX_KILLER_DEPTH) return 0;

            if (move.equals(killerMoves[depth][0])) return SearchConfig.KILLER_1_PRIORITY;
            if (move.equals(killerMoves[depth][1])) return SearchConfig.KILLER_2_PRIORITY;

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // === POSITIONAL SCORING WITH SEARCHCONFIG ===

    private int getBasicPositionalScore(Move move, GameState state) {
        try {
            int score = 0;

            // Central squares preference
            if (isCentralSquare(move.to)) {
                score += SearchConfig.CENTRAL_SQUARE_BONUS;
            }

            // Guard advancement
            if (isGuardMoveS(move, state)) {
                score += getGuardAdvancementScore(move, state);
            }

            // Development from back rank
            if (isDevelopmentMove(move, state)) {
                score += SearchConfig.DEVELOPMENT_BONUS;
            }

            return Math.min(score, SearchConfig.POSITIONAL_MAX_SCORE);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getGuardAdvancementScore(Move move, GameState state) {
        try {
            boolean isRed = state.redToMove;
            int toRank = GameState.rank(move.to);
            int targetRank = isRed ? 0 : 6;

            int distance = Math.abs(toRank - targetRank);
            return Math.max(0, (7 - distance) * SearchConfig.GUARD_ADVANCEMENT_MULTIPLIER);
        } catch (Exception e) {
            return 0;
        }
    }

    // === HELPER METHODS WITH SEARCHCONFIG ===

    private boolean isCaptureS(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;
            long toBit = GameState.bit(move.to);
            return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGuardMoveS(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;
            boolean isRed = state.redToMove;
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCentralSquare(int square) {
        try {
            int file = GameState.file(square);
            int rank = GameState.rank(square);
            return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDevelopmentMove(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;
            boolean isRed = state.redToMove;
            int fromRank = GameState.rank(move.from);
            int toRank = GameState.rank(move.to);
            return isRed ? (fromRank == 6 && toRank < 6) : (fromRank == 0 && toRank > 0);
        } catch (Exception e) {
            return false;
        }
    }

    // === UPDATE METHODS WITH SEARCHCONFIG ===

    /**
     * Store killer move using SearchConfig parameters
     */
    public void storeKillerMove(Move move, int depth) {
        try {
            if (move == null || depth >= SearchConfig.MAX_KILLER_DEPTH) return;

            if (!move.equals(killerMoves[depth][0])) {
                killerMoves[depth][1] = killerMoves[depth][0];
                killerMoves[depth][0] = move;
            }

            SearchStatistics.getInstance().incrementKillerMoveHits();
        } catch (Exception e) {
            System.err.println("❌ ERROR: Killer move storage failed: " + e.getMessage());
        }
    }

    /**
     * Update history using SearchConfig parameters
     */
    public void updateHistory(Move move, int depth, GameState state) {
        try {
            if (move == null || state == null || !state.isValid()) return;
            if (isCaptureS(move, state)) return; // Only for quiet moves

            if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
                historyTable[move.from][move.to] += depth * depth;

                // Use SearchConfig for overflow prevention
                if (historyTable[move.from][move.to] > SearchConfig.HISTORY_MAX_VALUE) {
                    ageHistoryTable();
                }
            }

            SearchStatistics.getInstance().incrementHistoryMoveHits();
        } catch (Exception e) {
            System.err.println("❌ ERROR: History update failed: " + e.getMessage());
        }
    }

    // === MAINTENANCE WITH SEARCHCONFIG ===

    private void ageHistoryTable() {
        try {
            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                    historyTable[i][j] /= SearchConfig.HISTORY_AGING_FACTOR;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: History table aging failed: " + e.getMessage());
        }
    }

    /**
     * Reset using SearchConfig thresholds
     */
    public void resetForNewSearch() {
        try {
            killerAge++;
            if (killerAge > SearchConfig.KILLER_AGING_THRESHOLD) {
                killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
                killerAge = 0;
                System.out.println("🔧 Killer moves reset (age threshold: " + SearchConfig.KILLER_AGING_THRESHOLD + ")");
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Reset for new search failed: " + e.getMessage());
        }
    }

    // === FALLBACK METHODS ===

    private int scoreBasic(Move move, int depth, TTEntry ttEntry) {
        try {
            if (ttEntry != null && move.equals(ttEntry.bestMove)) {
                return SearchConfig.MOVE_ORDERING_TT_PRIORITY;
            }
            if (isKillerMove(move, depth)) {
                return getKillerPriority(move, depth);
            }
            return move.amountMoved * SearchConfig.ACTIVITY_BONUS;
        } catch (Exception e) {
            return 0;
        }
    }

    private void orderMovesBasic(List<Move> moves) {
        try {
            moves.sort((a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        } catch (Exception e) {
            System.err.println("❌ ERROR: Basic move ordering failed: " + e.getMessage());
        }
    }

    // === STATISTICS WITH SEARCHCONFIG INFO ===

    public String getStatistics() {
        try {
            int killerCount = 0;
            int historyCount = 0;

            for (int i = 0; i < SearchConfig.MAX_KILLER_DEPTH && i < killerMoves.length; i++) {
                if (killerMoves[i][0] != null) killerCount++;
                if (SearchConfig.KILLER_MOVE_SLOTS > 1 && killerMoves[i][1] != null) killerCount++;
            }

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                    if (historyTable[i][j] > 0) historyCount++;
                }
            }

            return String.format("MoveOrdering[Config]: %d killers, %d history, age=%d, max_depth=%d, slots=%d",
                    killerCount, historyCount, killerAge, SearchConfig.MAX_KILLER_DEPTH, SearchConfig.KILLER_MOVE_SLOTS);
        } catch (Exception e) {
            return "MoveOrdering[Config]: Statistics unavailable due to error";
        }
    }

    // === CONFIGURATION VALIDATION ===

    private void validateSearchConfig() {
        boolean valid = true;

        if (SearchConfig.MAX_KILLER_DEPTH <= 0) {
            System.err.println("❌ Invalid MAX_KILLER_DEPTH: " + SearchConfig.MAX_KILLER_DEPTH);
            valid = false;
        }

        if (SearchConfig.KILLER_MOVE_SLOTS <= 0 || SearchConfig.KILLER_MOVE_SLOTS > 10) {
            System.err.println("❌ Invalid KILLER_MOVE_SLOTS: " + SearchConfig.KILLER_MOVE_SLOTS);
            valid = false;
        }

        if (SearchConfig.HISTORY_MAX_VALUE <= 0) {
            System.err.println("❌ Invalid HISTORY_MAX_VALUE: " + SearchConfig.HISTORY_MAX_VALUE);
            valid = false;
        }

        if (valid) {
            System.out.println("✅ MoveOrdering SearchConfig integration validated");
        }
    }

    // === LEGACY COMPATIBILITY ===

    public void clear() {
        initializeTables();
    }

    public void resetKillerMoves() {
        resetForNewSearch();
    }

    public void updateHistoryOnCutoff(Move move, GameState state, int depth) {
        updateHistory(move, depth, state);
    }

    public void storePVMove(Move move, int depth) {
        try {
            if (move != null && depth < SearchConfig.MAX_KILLER_DEPTH && depth < pvLine.length) {
                pvLine[depth] = move;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: PV move storage failed: " + e.getMessage());
        }
    }

    /**
     * Public interface for scoring (using SearchConfig)
     */
    public int scoreMove(Move move, GameState state, int depth, TTEntry ttEntry) {
        return scoreMoveSafe(move, state, depth, ttEntry);
    }
}