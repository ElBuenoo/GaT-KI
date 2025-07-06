package GaT.model;

import GaT.evaluation.Evaluator;
import GaT.search.TranspositionTable;
import GaT.search.MoveOrdering;
import GaT.search.SearchStatistics;
import java.util.function.BooleanSupplier;

/**
 * Complete SearchContext implementation - FIXED VERSION
 * Contains all parameters needed for search operations
 *
 * FIXES:
 * - timeoutChecker is never null (prevents NPEs)
 * - Proper handling of maximizingPlayer toggle
 * - Safe integer bounds to prevent overflow
 */
public class SearchContext {
    public final GameState state;
    public final int depth;
    public final int alpha;
    public final int beta;
    public final boolean maximizingPlayer;
    public final boolean isPVNode;
    public final BooleanSupplier timeoutChecker;

    // Shared components
    public final Evaluator evaluator;
    public final TranspositionTable ttable;
    public final MoveOrdering moveOrdering;
    public final SearchStatistics statistics;

    private SearchContext(Builder builder) {
        this.state = builder.state;
        this.depth = builder.depth;
        this.alpha = builder.alpha;
        this.beta = builder.beta;
        this.maximizingPlayer = builder.maximizingPlayer;
        this.isPVNode = builder.isPVNode;

        // FIX: Ensure timeoutChecker is NEVER null
        this.timeoutChecker = builder.timeoutChecker != null ?
                builder.timeoutChecker :
                () -> false;  // Default: never timeout

        this.evaluator = builder.evaluator;
        this.ttable = builder.ttable;
        this.moveOrdering = builder.moveOrdering;
        this.statistics = builder.statistics;
    }

    /**
     * Create a new context with different state and depth
     * IMPORTANT: This toggles maximizingPlayer!
     */
    public SearchContext withNewState(GameState newState, int newDepth) {
        return new Builder()
                .state(newState)
                .depth(newDepth)
                .window(this.alpha, this.beta)
                .maximizingPlayer(!this.maximizingPlayer)  // Toggle player
                .pvNode(this.isPVNode)
                .timeoutChecker(this.timeoutChecker)
                .components(this.evaluator, this.ttable, this.moveOrdering, this.statistics)
                .build();
    }

    /**
     * Create a new context with different window
     * Keeps the same player (used for null window searches)
     */
    public SearchContext withWindow(int newAlpha, int newBeta) {
        return new Builder()
                .state(this.state)
                .depth(this.depth)
                .window(newAlpha, newBeta)
                .maximizingPlayer(this.maximizingPlayer)  // Keep same player
                .pvNode(this.isPVNode)
                .timeoutChecker(this.timeoutChecker)
                .components(this.evaluator, this.ttable, this.moveOrdering, this.statistics)
                .build();
    }

    /**
     * Create a new context for null move search
     * Toggles player but keeps same depth
     */
    public SearchContext forNullMove(int reducedDepth) {
        GameState nullState = this.state.copy();
        nullState.redToMove = !nullState.redToMove;  // Toggle turn

        return new Builder()
                .state(nullState)
                .depth(reducedDepth)
                .window(this.alpha, this.beta)
                .maximizingPlayer(!this.maximizingPlayer)  // Toggle player
                .pvNode(false)  // Null moves are not PV nodes
                .timeoutChecker(this.timeoutChecker)
                .components(this.evaluator, this.ttable, this.moveOrdering, this.statistics)
                .build();
    }

    /**
     * Check if we should timeout
     * Safe method that handles null checker
     */
    public boolean shouldTimeout() {
        return timeoutChecker != null && timeoutChecker.getAsBoolean();
    }

    /**
     * Get a debug string representation
     */
    @Override
    public String toString() {
        return String.format("SearchContext[depth=%d, alpha=%d, beta=%d, maxPlayer=%s, pvNode=%s]",
                depth, alpha, beta, maximizingPlayer, isPVNode);
    }

    /**
     * Builder for SearchContext
     */
    public static class Builder {
        private GameState state;
        private int depth;
        private int alpha = Integer.MIN_VALUE + 1;  // Avoid overflow
        private int beta = Integer.MAX_VALUE - 1;   // Avoid overflow
        private boolean maximizingPlayer = true;
        private boolean isPVNode = false;
        private BooleanSupplier timeoutChecker;
        private Evaluator evaluator;
        private TranspositionTable ttable;
        private MoveOrdering moveOrdering;
        private SearchStatistics statistics;

        public Builder state(GameState state) {
            this.state = state;
            return this;
        }

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder window(int alpha, int beta) {
            // Ensure safe bounds
            this.alpha = Math.max(Integer.MIN_VALUE + 1, alpha);
            this.beta = Math.min(Integer.MAX_VALUE - 1, beta);
            return this;
        }

        public Builder maximizingPlayer(boolean maximizing) {
            this.maximizingPlayer = maximizing;
            return this;
        }

        public Builder pvNode(boolean isPV) {
            this.isPVNode = isPV;
            return this;
        }

        public Builder timeoutChecker(BooleanSupplier checker) {
            this.timeoutChecker = checker;
            return this;
        }

        public Builder components(Evaluator eval, TranspositionTable tt,
                                  MoveOrdering mo, SearchStatistics stats) {
            this.evaluator = eval;
            this.ttable = tt;
            this.moveOrdering = mo;
            this.statistics = stats;
            return this;
        }

        public SearchContext build() {
            // Validate required components
            if (state == null) {
                throw new IllegalStateException("GameState is required");
            }
            if (evaluator == null) {
                throw new IllegalStateException("Evaluator is required");
            }
            if (ttable == null) {
                throw new IllegalStateException("TranspositionTable is required");
            }
            if (moveOrdering == null) {
                throw new IllegalStateException("MoveOrdering is required");
            }
            if (statistics == null) {
                throw new IllegalStateException("SearchStatistics is required");
            }

            // Ensure valid depth
            if (depth < 0) {
                throw new IllegalArgumentException("Depth cannot be negative: " + depth);
            }

            // Ensure valid window
            if (alpha >= beta) {
                throw new IllegalArgumentException("Invalid window: alpha=" + alpha + " >= beta=" + beta);
            }

            return new SearchContext(this);
        }
    }

    /**
     * Factory method for creating initial search context
     */
    public static SearchContext createInitial(GameState state, int depth,
                                              Evaluator evaluator, TranspositionTable tt,
                                              MoveOrdering moveOrdering, SearchStatistics stats) {
        return new Builder()
                .state(state)
                .depth(depth)
                .window(Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1)
                .maximizingPlayer(state.redToMove)
                .pvNode(true)
                .timeoutChecker(() -> false)  // No timeout by default
                .components(evaluator, tt, moveOrdering, stats)
                .build();
    }

    /**
     * Factory method for creating search context with timeout
     */
    public static SearchContext createWithTimeout(GameState state, int depth,
                                                  BooleanSupplier timeoutChecker,
                                                  Evaluator evaluator, TranspositionTable tt,
                                                  MoveOrdering moveOrdering, SearchStatistics stats) {
        return new Builder()
                .state(state)
                .depth(depth)
                .window(Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1)
                .maximizingPlayer(state.redToMove)
                .pvNode(true)
                .timeoutChecker(timeoutChecker)
                .components(evaluator, tt, moveOrdering, stats)
                .build();
    }
}