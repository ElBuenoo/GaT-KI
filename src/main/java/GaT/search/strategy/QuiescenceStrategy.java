// File: src/main/java/GaT/search/strategy/QuiescenceStrategy.java
package GaT.search.strategy;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import GaT.search.MoveGenerator;
import GaT.search.SearchStatistics;
import java.util.*;

public class QuiescenceStrategy {

    // Statistics
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;

    private static final int MAX_Q_DEPTH = 12;

    /**
     * Static quiescence search - needs evaluator reference
     */
    public static int quiesce(GameState state, int alpha, int beta,
                              boolean maximizingPlayer, int qDepth,
                              Evaluator evaluator) {  // Pass evaluator as parameter
        qNodes++;
        SearchStatistics.getInstance().incrementQNodeCount();

        if (qDepth >= MAX_Q_DEPTH) {
            return evaluator.evaluate(state, -qDepth);
        }

        // Stand pat evaluation - use passed evaluator
        int standPat = evaluator.evaluate(state, -qDepth);

        // Rest of the implementation...
        // Replace all Minimax.evaluate() calls with evaluator.evaluate()

        if (maximizingPlayer) {
            if (standPat >= beta) {
                standPatCutoffs++;
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

                int eval = quiesce(copy, alpha, beta, false, qDepth + 1, evaluator);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    qCutoffs++;
                    break;
                }
            }
            return maxEval;
        } else {
            // Similar for minimizing player...
            if (standPat <= alpha) {
                standPatCutoffs++;
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

                int eval = quiesce(copy, alpha, beta, true, qDepth + 1, evaluator);
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
        // Use GameRules instead of Minimax
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

    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
    }
}