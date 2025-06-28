package GaT.model;

import GaT.evaluation.Evaluator;
import GaT.search.TranspositionTable;
import GaT.search.MoveOrdering;
import GaT.search.SearchStatistics;
import java.util.function.BooleanSupplier;

/**
 * Complete SearchContext implementation
 * Contains all parameters needed for search operations
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
        this.timeoutChecker = builder.timeoutChecker != null ? builder.timeoutChecker : () -> false;
        this.evaluator = builder.evaluator;
        this.ttable = builder.ttable;
        this.moveOrdering = builder.moveOrdering;
        this.statistics = builder.statistics;
    }

    /**
     * Create a new context with different state and depth
     */
    public SearchContext withNewState(GameState newState, int newDepth) {
        return new Builder()
                .state(newState)
                .depth(newDepth)
                .window(this.alpha, this.beta)
                .maximizingPlayer(!this.maximizingPlayer)
                .pvNode(this.isPVNode)
                .timeoutChecker(this.timeoutChecker)
                .components(this.evaluator, this.ttable, this.moveOrdering, this.statistics)
                .build();
    }

    /**
     * Create a new context with different window
     */
    public SearchContext withWindow(int newAlpha, int newBeta) {
        return new Builder()
                .state(this.state)
                .depth(this.depth)
                .window(newAlpha, newBeta)
                .maximizingPlayer(this.maximizingPlayer)
                .pvNode(this.isPVNode)
                .timeoutChecker(this.timeoutChecker)
                .components(this.evaluator, this.ttable, this.moveOrdering, this.statistics)
                .build();
    }

    public static class Builder {
        private GameState state;
        private int depth;
        private int alpha = Integer.MIN_VALUE + 1;  // Avoid overflow
        private int beta = Integer.MAX_VALUE - 1;
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
            this.alpha = alpha;
            this.beta = beta;
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
            if (state == null || evaluator == null || ttable == null ||
                    moveOrdering == null || statistics == null) {
                throw new IllegalStateException("Missing required components");
            }
            return new SearchContext(this);
        }
    }
}