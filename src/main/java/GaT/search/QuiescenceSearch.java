package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class QuiescenceSearch {

    // === THREAD-SAFE RECURSION PROTECTION ===
    private static final ThreadLocal<AtomicInteger> recursionDepth =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static final int MAX_Q_DEPTH = 12;
    private static final int MAX_TACTICAL_RECURSION = 2;

    // === STATISTICS - INTEGRATED WITH MAIN SYSTEM ===
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;

    /**
     * FIXED: Integrated quiescence search with proper statistics
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        // FIXED: Update main statistics system
        SearchStatistics.getInstance().incrementQNodeCount();
        qNodes++; // Keep local counter for compatibility

        if (qDepth >= MAX_Q_DEPTH) {
            return Minimax.evaluate(state, -qDepth);
        }

        // Stand pat evaluation
        int standPat = Minimax.evaluate(state, -qDepth);

        if (maximizingPlayer) {
            if (standPat >= beta) {
                standPatCutoffs++;
                return beta;
            }
            alpha = Math.max(alpha, standPat);

            // FIXED: Safe tactical move generation
            List<Move> tacticalMoves = generateTacticalMovesSafe(state);

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int maxEval = standPat;
            for (Move move : tacticalMoves) {
                // Simple delta pruning
                if (isCapture(move, state)) {
                    int captureValue = estimateCaptureValue(move, state);
                    if (standPat + captureValue + 150 < alpha) {
                        continue; // Skip bad captures
                    }
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesce(copy, alpha, beta, false, qDepth + 1);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    qCutoffs++;
                    break;
                }
            }
            return maxEval;

        } else {
            if (standPat <= alpha) {
                standPatCutoffs++;
                return alpha;
            }
            beta = Math.min(beta, standPat);

            List<Move> tacticalMoves = generateTacticalMovesSafe(state);

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int minEval = standPat;
            for (Move move : tacticalMoves) {
                if (isCapture(move, state)) {
                    int captureValue = estimateCaptureValue(move, state);
                    if (standPat - captureValue - 150 > beta) {
                        continue;
                    }
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesce(copy, alpha, beta, true, qDepth + 1);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    qCutoffs++;
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * ENHANCED: Safe tactical move generation with better filtering
     */
    private static List<Move> generateTacticalMovesSafe(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }

        // Sort tactical moves by estimated value (most promising first)
        tacticalMoves.sort((m1, m2) -> {
            int value1 = estimateCaptureValue(m1, state);
            int value2 = estimateCaptureValue(m2, state);
            return Integer.compare(value2, value1); // Descending order
        });

        // Limit tactical moves to prevent explosion
        return tacticalMoves.size() > 8 ? tacticalMoves.subList(0, 8) : tacticalMoves;
    }

    /**
     * Enhanced tactical move detection
     */
    private static boolean isTacticalMove(Move move, GameState state) {
        // Captures are always tactical
        if (isCapture(move, state)) {
            return true;
        }

        // Promotions would be tactical (if applicable to your game)
        // Checks would be tactical (if applicable)
        // For now, focus on captures which are most important for quiescence

        return false;
    }

    /**
     * Enhanced capture detection
     */
    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * ENHANCED: Better capture value estimation using actual piece values
     */
    private static int estimateCaptureValue(Move move, GameState state) {
        if (!isCapture(move, state)) {
            return 0;
        }

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Check for guard captures (high value)
        if ((isRed && (state.blueGuard & toBit) != 0) ||
                (!isRed && (state.redGuard & toBit) != 0)) {
            return 2000; // Guard value
        }

        // Check for tower captures
        if (isRed && (state.blueTowers & toBit) != 0) {
            return state.blueStackHeights[move.to] * 100; // Tower value per height
        } else if (!isRed && (state.redTowers & toBit) != 0) {
            return state.redStackHeights[move.to] * 100;
        }

        return 0;
    }

    /**
     * FIXED: Proper MVV-LVA (Most Valuable Victim - Least Valuable Attacker) scoring
     */
    private static int getMVVLVAScore(Move move, GameState state) {
        int victimValue = estimateCaptureValue(move, state);
        int attackerValue = getAttackerValue(move, state);

        // MVV-LVA: High victim value, low attacker value = higher score
        return victimValue * 100 - attackerValue;
    }

    /**
     * Get the value of the piece making the move
     */
    private static int getAttackerValue(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        boolean isRed = state.redToMove;

        // Check if it's a guard move
        if ((isRed && (state.redGuard & fromBit) != 0) ||
                (!isRed && (state.blueGuard & fromBit) != 0)) {
            return 2000; // Guard value
        }

        // Otherwise it's a tower move
        return move.amountMoved * 100; // Tower value per piece moved
    }

    /**
     * Reset quiescence statistics - ENHANCED
     */
    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
        // Note: Main statistics Q-node count is reset via SearchStatistics.reset()
    }

    /**
     * Get quiescence statistics summary
     */
    public static String getStatsSummary() {
        long mainQNodes = SearchStatistics.getInstance().getQNodeCount();
        return String.format("Q-Stats: Main=%d, Local=%d, Cutoffs=%d, StandPat=%d",
                mainQNodes, qNodes, qCutoffs, standPatCutoffs);
    }
}