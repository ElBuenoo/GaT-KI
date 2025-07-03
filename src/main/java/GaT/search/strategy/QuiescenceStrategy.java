package GaT.search.strategy;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class QuiescenceStrategy {

    // === THREAD-SAFE RECURSION PROTECTION ===
    private static final ThreadLocal<AtomicInteger> recursionDepth =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static final int MAX_Q_DEPTH = 12;
    private static final int MAX_TACTICAL_RECURSION = 2;

    // === STATISTICS ===
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;

    /**
     * FIXED: Thread-safe quiescence search
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        qNodes++;

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
     * FIXED: Safe tactical move generation without recursion
     */
    public static List<Move> generateTacticalMoves(GameState state) {
        return generateTacticalMovesSafe(state);
    }

    private static List<Move> generateTacticalMovesSafe(GameState state) {
        AtomicInteger depth = recursionDepth.get();

        if (depth.get() >= MAX_TACTICAL_RECURSION) {
            return new ArrayList<>(); // Prevent recursion
        }

        depth.incrementAndGet();
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            List<Move> tacticalMoves = new ArrayList<>();

            for (Move move : allMoves) {
                if (isTacticalMoveSafe(move, state)) {
                    tacticalMoves.add(move);
                }
            }

            return tacticalMoves;
        } finally {
            depth.decrementAndGet();
        }
    }

    /**
     * FIXED: Safe tactical move detection without recursion
     */
    private static boolean isTacticalMoveSafe(Move move, GameState state) {
        // 1. All captures are tactical
        if (isCapture(move, state)) {
            return true;
        }

        // 2. Winning guard moves
        if (isWinningGuardMove(move, state)) {
            return true;
        }

        // 3. Simple check detection (no expensive calculations)
        if (givesCheckSimple(move, state)) {
            return true;
        }

        return false;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static boolean isWinningGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // Check if it's a guard move
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
            return false;
        }

        // Check if moving to enemy castle
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle;
    }

    private static boolean givesCheckSimple(Move move, GameState state) {
        // Very simple check detection - just adjacent squares for guards
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        if (enemyGuard == 0) return false;

        int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
        int rankDiff = Math.abs(GameState.rank(move.to) - GameState.rank(enemyGuardPos));
        int fileDiff = Math.abs(GameState.file(move.to) - GameState.file(enemyGuardPos));

        // Simple adjacency check
        return (rankDiff + fileDiff == 1) ||
                (rankDiff == 0 && fileDiff <= move.amountMoved) ||
                (fileDiff == 0 && rankDiff <= move.amountMoved);
    }

    private static int estimateCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Guard capture
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 1500;
        }

        // Tower capture
        if (((isRed ? state.blueTowers : state.redTowers) & toBit) != 0) {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            return height * 100;
        }

        return 0;
    }

    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
    }

    public static void setRemainingTime(long timeMs) {
        // Adjust quiescence depth based on time
        // Implementation as needed
    }
}
