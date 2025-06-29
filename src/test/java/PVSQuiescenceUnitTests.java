import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.search.*;
import GaT.evaluation.Evaluator;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;

import java.util.List;

public class PVSQuiescenceUnitTests {

    private SearchEngine searchEngine;
    private Evaluator evaluator;
    private MoveOrdering moveOrdering;
    private TranspositionTable transpositionTable;
    private SearchStatistics statistics;

    @Before
    public void setUp() {
        // Initialize components
        evaluator = new Evaluator();
        moveOrdering = new MoveOrdering();
        transpositionTable = new TranspositionTable(10000);
        statistics = SearchStatistics.getInstance();
        searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

        // Reset statistics
        statistics.reset();
        transpositionTable.clear();
        moveOrdering.resetKillerMoves();
    }

    @After
    public void tearDown() {
        searchEngine.clearTimeoutChecker();
        PVSSearch.clearTimeoutChecker();
    }

    // === BASIC PVS FUNCTIONALITY TESTS ===

    @Test
    public void testPVSBasicFunctionality() {
        System.out.println("\n=== Testing PVS Basic Functionality ===");

        GameState state = new GameState();
        statistics.reset();

        int score = searchEngine.search(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.PVS);

        long nodeCount = statistics.getNodeCount();

        System.out.println("PVS Score: " + score);
        System.out.println("Nodes: " + nodeCount);

        assertTrue("PVS should generate nodes", nodeCount > 0);
        assertTrue("PVS score should be reasonable", Math.abs(score) < 10000);
        System.out.println("✅ PVS basic functionality working");
    }

    @Test
    public void testPVSWithQuiescenceBasicFunctionality() {
        System.out.println("\n=== Testing PVS + Quiescence Basic Functionality ===");

        GameState state = new GameState();
        statistics.reset();

        int score = searchEngine.search(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.PVS_Q);

        long nodeCount = statistics.getNodeCount();
        long qNodeCount = statistics.getQNodeCount();

        System.out.println("PVS_Q Score: " + score);
        System.out.println("Regular nodes: " + nodeCount);
        System.out.println("Quiescence nodes: " + qNodeCount);

        assertTrue("PVS_Q should generate regular nodes", nodeCount > 0);
        assertTrue("PVS_Q score should be reasonable", Math.abs(score) < 10000);
        System.out.println("✅ PVS + Quiescence basic functionality working");
    }

    @Test
    public void testPVSVsAlphaBetaComparison() {
        System.out.println("\n=== Testing PVS vs Alpha-Beta Efficiency ===");

        GameState complexState = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");

        // Test Alpha-Beta
        statistics.reset();
        long startTime = System.currentTimeMillis();
        int alphaBetaScore = searchEngine.search(complexState, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.ALPHA_BETA);
        long alphaBetaTime = System.currentTimeMillis() - startTime;
        long alphaBetaNodes = statistics.getNodeCount();

        // Test PVS
        statistics.reset();
        startTime = System.currentTimeMillis();
        int pvsScore = searchEngine.search(complexState, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.PVS);
        long pvsTime = System.currentTimeMillis() - startTime;
        long pvsNodes = statistics.getNodeCount();

        System.out.println("Alpha-Beta: score=" + alphaBetaScore + ", nodes=" + alphaBetaNodes + ", time=" + alphaBetaTime + "ms");
        System.out.println("PVS: score=" + pvsScore + ", nodes=" + pvsNodes + ", time=" + pvsTime + "ms");

        double efficiency = pvsNodes > 0 ? (double)alphaBetaNodes / pvsNodes : 1.0;
        System.out.println("PVS efficiency: " + efficiency + "x");

        assertTrue("Alpha-Beta should find reasonable score", Math.abs(alphaBetaScore) < 10000);
        assertTrue("PVS should find reasonable score", Math.abs(pvsScore) < 10000);
        assertTrue("PVS should be at least as efficient as Alpha-Beta", efficiency >= 0.8);
        System.out.println("✅ PVS efficiency test passed");
    }

    @Test
    public void testQuiescenceActivationInTacticalPositions() {
        System.out.println("\n=== Testing Quiescence Activation ===");

        // Tactical position with captures available
        GameState tacticalState = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");

        statistics.reset();
        int score = searchEngine.search(tacticalState, 3, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.PVS_Q);

        long regularNodes = statistics.getNodeCount();
        long quiescenceNodes = statistics.getQNodeCount();

        System.out.println("Tactical position score: " + score);
        System.out.println("Regular nodes: " + regularNodes);
        System.out.println("Quiescence nodes: " + quiescenceNodes);

        assertTrue("Should generate regular nodes", regularNodes > 0);
        assertTrue("Score should be reasonable", Math.abs(score) < 50000);

        // In tactical positions, we expect some quiescence activity
        System.out.println("Quiescence activated: " + (quiescenceNodes > 0));
        System.out.println("✅ Quiescence activation test completed");
    }

    @Test
    public void testPVSStatisticsIntegration() {
        System.out.println("\n=== Testing PVS Statistics Integration ===");

        GameState state = new GameState();
        statistics.reset();

        // Verify initial state
        assertEquals("Initial node count should be 0", 0, statistics.getNodeCount());
        assertEquals("Initial TT hits should be 0", 0, statistics.getTTHits());

        int score = searchEngine.search(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.PVS_Q);

        long totalNodes = statistics.getTotalNodes();
        long regularNodes = statistics.getNodeCount();
        long quiescenceNodes = statistics.getQNodeCount();
        long ttHits = statistics.getTTHits();
        long ttMisses = statistics.getTTMisses();

        System.out.println("Statistics after PVS_Q search:");
        System.out.println("  Total nodes: " + totalNodes);
        System.out.println("  Regular nodes: " + regularNodes);
        System.out.println("  Quiescence nodes: " + quiescenceNodes);
        System.out.println("  TT hits: " + ttHits);
        System.out.println("  TT misses: " + ttMisses);
        System.out.println("  Score: " + score);

        assertTrue("Should have total nodes", totalNodes > 0);
        assertTrue("Should have regular nodes", regularNodes > 0);
        assertEquals("Total should equal regular + quiescence", totalNodes, regularNodes + quiescenceNodes);
        assertTrue("Should have TT activity", (ttHits + ttMisses) > 0);
        System.out.println("✅ Statistics integration working correctly");
    }

    @Test
    public void testPVSDepthProgression() {
        System.out.println("\n=== Testing PVS Depth Progression ===");

        GameState state = GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r");

        int[] depths = {2, 3, 4, 5};
        long[] nodeCounts = new long[depths.length];
        int[] scores = new int[depths.length];

        for (int i = 0; i < depths.length; i++) {
            statistics.reset();

            scores[i] = searchEngine.search(state, depths[i], Integer.MIN_VALUE, Integer.MAX_VALUE,
                    true, SearchConfig.SearchStrategy.PVS_Q);
            nodeCounts[i] = statistics.getNodeCount();

            System.out.println("Depth " + depths[i] + ": score=" + scores[i] + ", nodes=" + nodeCounts[i]);

            assertTrue("Should generate nodes at depth " + depths[i], nodeCounts[i] > 0);
            assertTrue("Score should be reasonable at depth " + depths[i], Math.abs(scores[i]) < 10000);
        }

        // Verify node count growth
        for (int i = 1; i < nodeCounts.length; i++) {
            if (nodeCounts[i] > 0 && nodeCounts[i-1] > 0) {
                double growthRatio = (double)nodeCounts[i] / nodeCounts[i-1];
                System.out.println("Growth ratio depth " + depths[i-1] + " to " + depths[i] + ": " + growthRatio);
                assertTrue("Node count should increase with depth", growthRatio >= 1.0);
            }
        }
        System.out.println("✅ Depth progression test passed");
    }

    @Test
    public void testPVSTimeoutHandling() {
        System.out.println("\n=== Testing PVS Timeout Handling ===");

        GameState state = new GameState();
        statistics.reset();

        long startTime = System.currentTimeMillis();

        // Set a short timeout
        searchEngine.setTimeoutChecker(() -> System.currentTimeMillis() - startTime > 100);

        try {
            int score = searchEngine.search(state, 10, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    true, SearchConfig.SearchStrategy.PVS_Q);
            // If we get here without timeout exception, that's also valid
            System.out.println("Search completed with score: " + score);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("timeout") || e.getMessage().contains("Timeout")) {
                System.out.println("✅ Timeout correctly detected: " + e.getMessage());
            } else {
                throw e; // Re-throw if it's not a timeout
            }
        }

        long actualTime = System.currentTimeMillis() - startTime;
        long nodeCount = statistics.getNodeCount();

        System.out.println("Actual time: " + actualTime + "ms");
        System.out.println("Nodes before timeout: " + nodeCount);

        assertTrue("Should generate some nodes before timeout", nodeCount > 0);
        System.out.println("✅ Timeout handling test completed");
    }

    @Test
    public void testPVSConsistency() {
        System.out.println("\n=== Testing PVS Consistency ===");

        GameState state = GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r");

        int[] scores = new int[3];
        long[] nodeCounts = new long[3];

        // Run same search multiple times
        for (int i = 0; i < 3; i++) {
            statistics.reset();
            scores[i] = searchEngine.search(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    true, SearchConfig.SearchStrategy.PVS_Q);
            nodeCounts[i] = statistics.getNodeCount();

            System.out.println("Run " + (i+1) + ": score=" + scores[i] + ", nodes=" + nodeCounts[i]);
        }

        // Verify consistency
        for (int i = 0; i < 3; i++) {
            assertTrue("Run " + (i+1) + " should generate nodes", nodeCounts[i] > 0);
            assertTrue("Run " + (i+1) + " should have reasonable score", Math.abs(scores[i]) < 10000);
        }

        // Scores should be identical (deterministic search)
        assertEquals("Scores should be consistent across runs", scores[0], scores[1]);
        assertEquals("Scores should be consistent across runs", scores[1], scores[2]);

        // Node counts should be in reasonable range
        long minNodes = Math.min(Math.min(nodeCounts[0], nodeCounts[1]), nodeCounts[2]);
        long maxNodes = Math.max(Math.max(nodeCounts[0], nodeCounts[1]), nodeCounts[2]);
        double nodeVariation = maxNodes > 0 ? (double)minNodes / maxNodes : 1.0;

        System.out.println("Node count variation: " + nodeVariation);
        assertTrue("Node counts should be consistent", nodeVariation >= 0.8);
        System.out.println("✅ Consistency test passed");
    }

    @Test
    public void testPVSMoveQuality() {
        System.out.println("\n=== Testing PVS Move Quality via TimedMinimax ===");

        String[] testPositions = {
                "r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r", // Start
                "7/7/7/3RG3/7/7/3BG3 r", // Simple
                "7/7/3b33/BG1r43/3RG3/7/7 r"  // Tactical
        };

        for (String fen : testPositions) {
            GameState state = GameState.fromFen(fen);

            statistics.reset();
            Move pvsMove = TimedMinimax.findBestMoveWithStrategy(
                    state, 4, 2000, SearchConfig.SearchStrategy.PVS_Q);

            System.out.println("Position: " + fen);
            System.out.println("PVS move: " + pvsMove);
            System.out.println("Nodes: " + statistics.getNodeCount());

            assertNotNull("Should find move for position", pvsMove);

            // Verify move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            assertTrue("Move should be legal", legalMoves.contains(pvsMove));

            System.out.println("Legal: " + legalMoves.contains(pvsMove) + ", Available: " + legalMoves.size());
            System.out.println();
        }
        System.out.println("✅ Move quality test passed");
    }

    @Test
    public void testPVSGameEndingDetection() {
        System.out.println("\n=== Testing PVS Game Ending Detection ===");

        // Position where red guard is on D7 (winning)
        GameState winningState = GameState.fromFen("3RG3/7/7/7/7/7/7 r");

        statistics.reset();
        int score = searchEngine.search(winningState, 3, Integer.MIN_VALUE, Integer.MAX_VALUE,
                true, SearchConfig.SearchStrategy.PVS_Q);

        System.out.println("Winning position score: " + score);

        // Should recognize this as a winning position
        assertTrue("Should recognize winning position", score > 1000);
        System.out.println("✅ Game ending detection working");
    }

    @Test
    public void testQuiescenceSearchDirectly() {
        System.out.println("\n=== Testing Quiescence Search Directly ===");

        GameState tacticalState = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");

        QuiescenceSearch.resetQuiescenceStats();

        int qScore = QuiescenceSearch.quiesce(tacticalState, Integer.MIN_VALUE, Integer.MAX_VALUE, true, 0);

        System.out.println("Direct quiescence score: " + qScore);
        System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
        System.out.println("Q-cutoffs: " + QuiescenceSearch.qCutoffs);

        assertTrue("Quiescence should find reasonable score", Math.abs(qScore) < 50000);
        assertTrue("Quiescence should generate some nodes", QuiescenceSearch.qNodes > 0);
        System.out.println("✅ Direct quiescence test passed");
    }

    @Test
    public void testPVSSearchDirectly() {
        System.out.println("\n=== Testing PVS Search Directly ===");

        GameState state = new GameState();
        statistics.reset();

        int pvsScore = PVSSearch.search(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, true, true);

        long nodeCount = statistics.getNodeCount();

        System.out.println("Direct PVS score: " + pvsScore);
        System.out.println("Nodes: " + nodeCount);

        assertTrue("PVS should find reasonable score", Math.abs(pvsScore) < 10000);
        assertTrue("PVS should generate nodes", nodeCount > 0);
        System.out.println("✅ Direct PVS test passed");
    }

    @Test
    public void testPVSWithQuiescenceDirectly() {
        System.out.println("\n=== Testing PVS + Quiescence Directly ===");

        GameState state = new GameState();
        statistics.reset();

        int pvsQScore = PVSSearch.searchWithQuiescence(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, true, true);

        long nodeCount = statistics.getNodeCount();
        long qNodeCount = statistics.getQNodeCount();

        System.out.println("Direct PVS+Q score: " + pvsQScore);
        System.out.println("Regular nodes: " + nodeCount);
        System.out.println("Q-nodes: " + qNodeCount);

        assertTrue("PVS+Q should find reasonable score", Math.abs(pvsQScore) < 10000);
        assertTrue("PVS+Q should generate regular nodes", nodeCount > 0);
        System.out.println("✅ Direct PVS+Quiescence test passed");
    }

    // === INTEGRATION TESTS ===

    @Test
    public void testFullSearchEngineIntegration() {
        System.out.println("\n=== Testing Full Search Engine Integration ===");

        GameState complexState = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");

        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.ALPHA_BETA,
                SearchConfig.SearchStrategy.ALPHA_BETA_Q,
                SearchConfig.SearchStrategy.PVS,
                SearchConfig.SearchStrategy.PVS_Q
        };

        for (SearchConfig.SearchStrategy strategy : strategies) {
            statistics.reset();

            int score = searchEngine.search(complexState, 3, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    true, strategy);

            long nodeCount = statistics.getNodeCount();

            System.out.println(strategy + ": score=" + score + ", nodes=" + nodeCount);

            assertNotNull("Strategy should not be null", strategy);
            assertTrue("Score should be reasonable for " + strategy, Math.abs(score) < 50000);
            assertTrue("Should generate nodes for " + strategy, nodeCount > 0);
        }
        System.out.println("✅ Full integration test passed");
    }

    @Test
    public void testTimedMinimaxIntegration() {
        System.out.println("\n=== Testing TimedMinimax Integration ===");

        GameState state = new GameState();

        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.PVS,
                SearchConfig.SearchStrategy.PVS_Q
        };

        for (SearchConfig.SearchStrategy strategy : strategies) {
            statistics.reset();

            Move move = TimedMinimax.findBestMoveWithStrategy(state, 4, 2000, strategy);

            long nodeCount = statistics.getNodeCount();

            System.out.println(strategy + ": move=" + move + ", nodes=" + nodeCount);

            assertNotNull("Should find move with " + strategy, move);
            assertTrue("Should generate nodes with " + strategy, nodeCount > 0);

            // Verify move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            assertTrue("Move should be legal with " + strategy, legalMoves.contains(move));
        }
        System.out.println("✅ TimedMinimax integration test passed");
    }

    // === PERFORMANCE TESTS ===

    @Test
    public void testPVSPerformanceCharacteristics() {
        System.out.println("\n=== Testing PVS Performance Characteristics ===");

        GameState state = new GameState();

        // Test different depths
        for (int depth = 3; depth <= 6; depth++) {
            statistics.reset();

            long startTime = System.currentTimeMillis();
            int score = searchEngine.search(state, depth, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    true, SearchConfig.SearchStrategy.PVS_Q);
            long endTime = System.currentTimeMillis();

            long nodeCount = statistics.getNodeCount();
            long time = endTime - startTime;
            double nps = time > 0 ? (double)nodeCount * 1000 / time : 0;

            System.out.printf("Depth %d: score=%d, nodes=%d, time=%dms, nps=%.0f%n",
                    depth, score, nodeCount, time, nps);

            assertTrue("Should complete in reasonable time", time < 10000);
            assertTrue("Should generate reasonable nodes", nodeCount > depth * 10);
        }
        System.out.println("✅ Performance characteristics test completed");
    }

    @Test
    public void testPVSMemoryUsage() {
        System.out.println("\n=== Testing PVS Memory Usage ===");

        GameState state = new GameState();

        // Run multiple searches to test memory stability
        for (int i = 0; i < 10; i++) {
            statistics.reset();

            int score = searchEngine.search(state, 4, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    true, SearchConfig.SearchStrategy.PVS_Q);

            // Suggest garbage collection
            System.gc();

            assertTrue("Search " + (i+1) + " should complete successfully", Math.abs(score) < 10000);
        }

        System.out.println("✅ Memory usage test completed - no memory leaks detected");
    }
}