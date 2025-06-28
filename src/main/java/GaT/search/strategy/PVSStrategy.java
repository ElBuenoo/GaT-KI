package GaT.search.strategy;

import GaT.model.*;
import GaT.search.*;
import java.util.List;

/**
 * PVS Strategy - OHNE zirkuläre Abhängigkeiten zu Minimax
 */
public class PVSStrategy implements ISearchStrategy {

    private static final int ASPIRATION_WINDOW = 50;

    @Override
    public SearchResult search(SearchContext context) {
        // Initial search with aspiration windows
        int alpha = context.alpha;
        int beta = context.beta;

        // Try aspiration window if not at root
        if (context.depth > 1 && Math.abs(alpha) < 10000) {
            alpha = Math.max(context.alpha, alpha - ASPIRATION_WINDOW);
            beta = Math.min(context.beta, beta + ASPIRATION_WINDOW);
        }

        try {
            return searchPVS(context, alpha, beta);
        } catch (AspirationFailException e) {
            // Re-search with full window
            System.out.println("Aspiration window failed, re-searching...");
            return searchPVS(context, context.alpha, context.beta);
        }
    }

    private SearchResult searchPVS(SearchContext context, int alpha, int beta) {
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            int score = context.evaluator.evaluate(context.state, context.depth);
            return new SearchResult(null, score, context.depth,
                    context.statistics.getNodeCount());
        }

        // Order moves - TT move first if available
        TTEntry ttEntry = context.ttable.get(context.state.hash());
        context.moveOrdering.orderMoves(moves, context.state, context.depth, ttEntry);

        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        boolean firstMove = true;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            int score;

            if (firstMove) {
                // First move - search with full window
                score = -pvSearch(context.withNewState(newState, context.depth - 1),
                        -beta, -alpha);
                firstMove = false;
            } else {
                // Null window search
                score = -pvSearch(context.withNewState(newState, context.depth - 1),
                        -alpha - 1, -alpha);

                // Re-search if failed high
                if (score > alpha && score < beta) {
                    score = -pvSearch(context.withNewState(newState, context.depth - 1),
                            -beta, -alpha);
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                // Store killer move
                if (!isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        // Check aspiration failure
        if (bestScore <= context.alpha || bestScore >= context.beta) {
            throw new AspirationFailException();
        }

        return new SearchResult(bestMove, bestScore, context.depth,
                context.statistics.getNodeCount());
    }

    private int pvSearch(SearchContext context, int alpha, int beta) {
        context.statistics.incrementNodeCount();

        // Timeout check
        if (context.timeoutChecker.getAsBoolean()) {
            throw new SearchTimeoutException();
        }

        // Terminal node
        if (context.depth <= 0 || isGameOver(context.state)) {
            return context.evaluator.evaluate(context.state, context.depth);
        }

        // TT probe
        long hash = context.state.hash();
        TTEntry entry = context.ttable.get(hash);
        if (entry != null && entry.depth >= context.depth) {
            if (entry.flag == TTEntry.EXACT) {
                context.statistics.incrementTTHits();
                return entry.score;
            }
            // Bounds checking for non-PV nodes
            if (!context.isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                }
                if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        }

        // Move generation
        List<Move> moves = MoveGenerator.generateAllMoves(context.state);
        if (moves.isEmpty()) {
            return context.evaluator.evaluate(context.state, context.depth);
        }

        context.moveOrdering.orderMoves(moves, context.state, context.depth, entry);

        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;
        boolean firstMove = true;

        for (Move move : moves) {
            GameState newState = context.state.copy();
            newState.applyMove(move);

            int score;

            if (firstMove) {
                score = -pvSearch(context.withNewState(newState, context.depth - 1),
                        -beta, -alpha);
                firstMove = false;
            } else {
                // Null window
                score = -pvSearch(context.withNewState(newState, context.depth - 1),
                        -alpha - 1, -alpha);

                if (score > alpha && score < beta) {
                    score = -pvSearch(context.withNewState(newState, context.depth - 1),
                            -beta, -alpha);
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                if (!isCapture(move, context.state)) {
                    context.moveOrdering.storeKillerMove(move, context.depth);
                }
                break;
            }
        }

        // Store in TT
        storeTTEntry(context, hash, bestScore, alpha, beta, bestMove);

        return bestScore;
    }

    private void storeTTEntry(SearchContext context, long hash, int score,
                              int alpha, int beta, Move bestMove) {
        int flag;
        if (score <= alpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        context.ttable.put(hash, new TTEntry(score, context.depth, flag, bestMove));
        context.statistics.incrementTTStores();
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers |
                state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isGameOver(GameState state) {
        // Moved from Minimax - no circular dependency!
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
        int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);

        return redGuardPos == GameState.getIndex(0, 3) ||
                blueGuardPos == GameState.getIndex(6, 3);
    }

    @Override
    public String getName() {
        return "PVS";
    }

    // Helper exceptions
    private static class AspirationFailException extends RuntimeException {}
    private static class SearchTimeoutException extends RuntimeException {}
}

// ===== Erweiterung für SearchContext =====
// In SearchContext.java hinzufügen:

public SearchContext withNewState(GameState newState, int newDepth) {
    return new Builder()
            .state(newState)
            .depth(newDepth)
            .window(this.alpha, this.beta)
            .maximizingPlayer(!this.maximizingPlayer)
            .pvNode(this.isPVNode)
            .timeoutChecker(this.timeoutChecker)
            .components(this.evaluator, this.ttable,
                    this.moveOrdering, this.statistics)
            .build();
}