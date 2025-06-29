import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.search.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class VerifyStrategyTest {

    @Before
    public void setUp() {
        // Reset statistics before each test
        SearchStatistics.getInstance().reset();
        QuiescenceSearch.resetQuiescenceStats();
    }

    @Test
    public void testPVSQuiescenceActuallyUsed() {
        System.out.println("\n=== VERIFYING PVS + QUIESCENCE USAGE ===");

        // Use a tactical position to ensure quiescence activates
        GameState tacticalState = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");

        // Reset statistics
        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();
        QuiescenceSearch.resetQuiescenceStats();

        // Search with PVS_Q
        Move move = TimedMinimax.findBestMoveWithStrategy(
                tacticalState, 6, 3000, SearchConfig.SearchStrategy.PVS_Q);

        // Verify move was found
        assertNotNull("Should find a move", move);

        // Get statistics
        long regularNodes = stats.getNodeCount();
        long qNodes = stats.getQNodeCount();
        long totalNodes = stats.getTotalNodes();

        System.out.println("Results:");
        System.out.println("  Move found: " + move);
        System.out.println("  Regular nodes: " + regularNodes);
        System.out.println("  Quiescence nodes: " + qNodes);
        System.out.println("  Total nodes: " + totalNodes);
        System.out.println("  Q-nodes from QuiescenceSearch: " + QuiescenceSearch.qNodes);

        // Verify nodes were searched
        assertTrue("Should search regular nodes", regularNodes > 0);
        assertTrue("Should search quiescence nodes in tactical position",
                qNodes > 0 || QuiescenceSearch.qNodes > 0);

        System.out.println("✅ PVS + Quiescence verified working!");
    }

    @Test
    public void testStrategyComparison() {
        System.out.println("\n=== COMPARING SEARCH STRATEGIES ===");

        GameState state = new GameState();
        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.ALPHA_BETA,
                SearchConfig.SearchStrategy.ALPHA_BETA_Q,
                SearchConfig.SearchStrategy.PVS,
                SearchConfig.SearchStrategy.PVS_Q
        };

        for (SearchConfig.SearchStrategy strategy : strategies) {
            SearchStatistics.getInstance().reset();
            QuiescenceSearch.resetQuiescenceStats();

            long startTime = System.currentTimeMillis();
            Move move = TimedMinimax.findBestMoveWithStrategy(state, 5, 2000, strategy);
            long endTime = System.currentTimeMillis();

            assertNotNull("Should find move with " + strategy, move);

            long nodes = SearchStatistics.getInstance().getTotalNodes();
            long qNodes = SearchStatistics.getInstance().getQNodeCount();

            System.out.printf("%s: move=%s, nodes=%d, q-nodes=%d, time=%dms%n",
                    strategy, move, nodes, qNodes, endTime - startTime);

            // Verify quiescence is used in Q strategies
            if (strategy.toString().contains("Q")) {
                assertTrue("Q strategies should eventually use quiescence",
                        qNodes > 0 || QuiescenceSearch.qNodes > 0);
            }
        }
    }

    @Test
    public void testTimeUsageImprovement() {
        System.out.println("\n=== TESTING TIME USAGE ===");

        GameState state = new GameState();
        long timeLimit = 5000; // 5 seconds

        SearchStatistics.getInstance().reset();

        long startTime = System.currentTimeMillis();
        Move move = TimedMinimax.findBestMoveUltimate(state, 99, timeLimit);
        long actualTime = System.currentTimeMillis() - startTime;

        assertNotNull("Should find move", move);

        double timeUsagePercent = (double)actualTime / timeLimit * 100;

        System.out.printf("Time limit: %dms%n", timeLimit);
        System.out.printf("Time used: %dms (%.1f%%)%n", actualTime, timeUsagePercent);
        System.out.printf("Nodes searched: %,d%n", SearchStatistics.getInstance().getTotalNodes());

        // With aggressive time management, should use at least 30% of time
        assertTrue("Should use at least 30% of allocated time", timeUsagePercent >= 30);

        System.out.println("✅ Time usage verified!");
    }

    @Test
    public void testTacticalPositionHandling() {
        System.out.println("\n=== TESTING TACTICAL POSITION HANDLING ===");

        // Position where Red can capture Blue's guard
        GameState winningPosition = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");

        SearchStatistics.getInstance().reset();

        Move move = TimedMinimax.findBestMoveUltimate(winningPosition, 4, 1000);

        assertNotNull("Should find move", move);

        // The best move should be D4xD6 (capturing the blue guard)
        boolean capturesGuard = move.from == GameState.getIndex(3, 3) &&
                move.to == GameState.getIndex(5, 3);

        System.out.println("Move found: " + move);
        System.out.println("Captures guard: " + capturesGuard);

        assertTrue("Should find guard capture in tactical position", capturesGuard);

        System.out.println("✅ Tactical awareness verified!");
    }
}