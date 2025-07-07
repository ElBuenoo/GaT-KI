package GaT.search.strategy;

import GaT.model.*;
import GaT.search.MoveGenerator;
import GaT.search.SearchStatistics;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator;
import java.util.List;

public class AlphaBetaWithQuiescenceStrategy extends AlphaBetaStrategy {

    public AlphaBetaWithQuiescenceStrategy(SearchStatistics statistics, QuiescenceSearch quiescenceSearch, Evaluator evaluator) {
        super(statistics, quiescenceSearch, evaluator);
    }

    @Override
    public SearchResult search(SearchContext context) {
        return super.search(context);
    }

    @Override
    protected int alphaBeta(SearchContext context) {
        statistics.incrementNodeCount();

        // Timeout check
        if (context.timeoutChecker != null && context.timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // WICHTIG: Bei Tiefe 0 verwenden wir Quiescence Search!
        if (context.depth <= 0) {
            return this.quiescenceSearch.quiesce(context.state, context.alpha, context.beta,
                    context.maximizingPlayer, 0);
        }

        // Check for game over
        if (GameRules.isGameOver(context.state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(context.state, context.depth);
        }

        // TT lookup
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= context.beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= context.alpha) {
                return entry.score;
            }
        } else {
            statistics.incrementTTMisses();
        }

        // Null move pruning
        if (canDoNullMove(context)) {
            statistics.incrementNullMoveAttempts();
            int nullMoveScore = doNullMoveSearch(context);
            if (nullMoveScore >= context.beta) {
                statistics.incrementNullMoveCutoffs();
                return context.beta;
            }
        }

        // Generate moves
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        statistics.addMovesGenerated(moves.size());

        if (moves.isEmpty()) {
            return evaluator.evaluate(context.state, context.depth);
        }

        context.moveOrdering.orderMoves(moves, context.state, context.depth, entry);

        int bestScore = Integer.MIN_VALUE + 1;
        int alpha = context.alpha;
        int beta = context.beta;
        Move bestMove = null;
        int moveCount = 0;

        for (Move move : moves) {
            moveCount++;
            GameState newState = context.state.copy();
            newState.applyMove(move);

            statistics.addMovesSearched(1);

            int newDepth = context.depth - 1;

            // Check extension
            if (GameRules.isInCheck(newState)) {
                newDepth++;
                statistics.incrementCheckExtensions();
            }

            // KRITISCHER FIX: score Variable muss IMMER initialisiert werden!
            int score;

            if (moveCount > 4 && newDepth > 2 && !GameRules.isCapture(move, context.state)) {
                // Late Move Reduction
                SearchContext reducedContext = context.withNewState(newState, newDepth - 1)
                        .withWindow(-alpha - 1, -alpha);
                score = -alphaBeta(reducedContext);

                if (score > alpha) {
                    SearchContext fullContext = context.withNewState(newState, newDepth)
                            .withWindow(-beta, -alpha);
                    score = -alphaBeta(fullContext);
                }
                statistics.incrementLMRReductions();
            } else {
                // Normal search
                SearchContext childContext = context.withNewState(newState, newDepth)
                        .withWindow(-beta, -alpha);
                score = -alphaBeta(childContext);
            }

            // Score-Verarbeitung für ALLE Züge
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, bestScore);

            if (alpha >= beta) {
                statistics.incrementAlphaBetaCutoffs();
                if (!GameRules.isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        storeInTT(context, hash, bestScore, context.alpha, beta, bestMove);
        return bestScore;
    }

    @Override
    public String getName() {
        return "AlphaBeta+Q";
    }

    @Override
    public boolean usesQuiescence() {
        return true;
    }
}