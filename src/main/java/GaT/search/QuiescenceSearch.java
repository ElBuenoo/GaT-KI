package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.*;

/**
 * Quiescence Search implementation
 * Searches tactical moves to avoid horizon effect
 */
public class QuiescenceSearch {

    private static final int MAX_Q_DEPTH = 15;

    private final Evaluator evaluator;
    private final SearchStatistics statistics;

    public QuiescenceSearch(Evaluator evaluator, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.statistics = statistics;
    }

    /**
     * Main quiescence search method
     */
    public int quiesce(GameState state, int alpha, int beta,
                       boolean maximizingPlayer, int qDepth) {

        statistics.incrementQNodeCount();

        // Depth limit
        if (qDepth >= MAX_Q_DEPTH) {
            return evaluator.evaluate(state, -qDepth);
        }

        // Stand pat evaluation
        int standPat = evaluator.evaluate(state, -qDepth);

        if (maximizingPlayer) {
            if (standPat >= beta) {
                statistics.incrementStandPatCutoffs();
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

                int eval = quiesce(copy, alpha, beta, false, qDepth + 1);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementQCutoffs();
                    break;
                }
            }
            return maxEval;
        } else {
            if (standPat <= alpha) {
                statistics.incrementStandPatCutoffs();
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

                int eval = quiesce(copy, alpha, beta, true, qDepth + 1);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementQCutoffs();
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Generate only tactical moves (captures and winning moves)
     */
    private List<Move> generateTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }

        // Sort by MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
        tacticalMoves.sort((m1, m2) -> {
            int score1 = scoreTacticalMove(m1, state);
            int score2 = scoreTacticalMove(m2, state);
            return score2 - score1; // Higher scores first
        });

        return tacticalMoves;
    }

    /**
     * Check if a move is tactical
     */
    private boolean isTacticalMove(Move move, GameState state) {
        // Captures are always tactical
        if (GameRules.isCapture(move, state)) {
            return true;
        }

        // Winning guard moves
        if (isWinningGuardMove(move, state)) {
            return true;
        }

        // Guard is threatened
        if (isGuardThreatened(state) && GameRules.isGuardMove(move, state)) {
            return true;
        }

        return false;
    }

    /**
     * Score a tactical move for ordering
     */
    private int scoreTacticalMove(Move move, GameState state) {
        int score = 0;

        // Winning move
        if (isWinningGuardMove(move, state)) {
            return 10000;
        }

        // Capture scoring
        if (GameRules.isCapture(move, state)) {
            boolean isRed = state.redToMove;
            int victimHeight = isRed ?
                    state.blueStackHeights[move.to] :
                    state.redStackHeights[move.to];

            // Guard capture
            long enemyGuard = isRed ? state.blueGuard : state.redGuard;
            if ((enemyGuard & GameState.bit(move.to)) != 0) {
                score += 5000;
            }

            // Tower capture (MVV)
            score += victimHeight * 100;

            // Least valuable attacker bonus
            if (move.amountMoved == 1) {
                score += 50;
            }
        }

        return score;
    }

    /**
     * Check if move wins the game
     */
    private boolean isWinningGuardMove(Move move, GameState state) {
        if (!GameRules.isGuardMove(move, state)) {
            return false;
        }

        boolean isRed = state.redToMove;
        int targetCastle = isRed ?
                GameState.getIndex(0, 3) :  // Blue castle D1
                GameState.getIndex(6, 3);   // Red castle D7

        return move.to == targetCastle;
    }

    /**
     * Check if our guard is under threat
     */
    private boolean isGuardThreatened(GameState state) {
        boolean isRed = state.redToMove;
        long ourGuard = isRed ? state.redGuard : state.blueGuard;

        if (ourGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(ourGuard);

        // Simulate opponent's turn
        GameState simState = state.copy();
        simState.redToMove = !simState.redToMove;

        List<Move> enemyMoves = MoveGenerator.generateAllMoves(simState);
        for (Move enemyMove : enemyMoves) {
            if (enemyMove.to == guardPos) {
                return true;
            }
        }

        return false;
    }
}