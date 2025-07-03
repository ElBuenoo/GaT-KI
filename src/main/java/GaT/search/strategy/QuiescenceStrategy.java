package GaT.search.strategy;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import GaT.search.MoveGenerator;
import GaT.search.SearchStatistics;
import java.util.*;

public class QuiescenceStrategy {

    private static final int MAX_Q_DEPTH = 12;

    /**
     * ✅ UPDATED: Now takes SearchStatistics as parameter
     */
    public static int quiesce(GameState state, int alpha, int beta,
                              boolean maximizingPlayer, int qDepth,
                              Evaluator evaluator, SearchStatistics statistics) {

        statistics.incrementQNodeCount(); // ✅ Use injected statistics

        if (qDepth >= MAX_Q_DEPTH) {
            return evaluator.evaluate(state, -qDepth);
        }

        int standPat = evaluator.evaluate(state, -qDepth);

        if (maximizingPlayer) {
            if (standPat >= beta) {
                statistics.incrementStandPatCutoffs(); // ✅ Use injected statistics
                return beta;
            }
            alpha = Math.max(alpha, standPat);

            List<Move> tacticalMoves = generateTacticalMoves(state);
            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int maxEval = standPat;
            for (Move move : tacticalMoves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesce(copy, alpha, beta, false, qDepth + 1, evaluator, statistics);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementQCutoffs(); // ✅ Use injected statistics
                    break;
                }
            }
            return maxEval;
        } else {
            if (standPat <= alpha) {
                statistics.incrementStandPatCutoffs(); // ✅ Use injected statistics
                return alpha;
            }
            beta = Math.min(beta, standPat);

            List<Move> tacticalMoves = generateTacticalMoves(state);
            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int minEval = standPat;
            for (Move move : tacticalMoves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesce(copy, alpha, beta, true, qDepth + 1, evaluator, statistics);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementQCutoffs(); // ✅ Use injected statistics
                    break;
                }
            }
            return minEval;
        }
    }

    // ✅ KEEP THESE HELPER METHODS (they're fine)
    private static List<Move> generateTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }
        return tacticalMoves;
    }

    private static boolean isTacticalMove(Move move, GameState state) {
        if (GameRules.isCapture(move, state)) {
            return true;
        }
        if (isWinningGuardMove(move, state)) {
            return true;
        }
        return false;
    }

    private static boolean isWinningGuardMove(Move move, GameState state) {
        if (!GameRules.isGuardMove(move, state)) return false;

        boolean isRed = state.redToMove;
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle;
    }

    // ✅ NO MORE STATIC STATISTICS - they're handled by SearchStatistics now
}