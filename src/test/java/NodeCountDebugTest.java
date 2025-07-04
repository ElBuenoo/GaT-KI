
import GaT.engine.GameEngine;
import GaT.engine.TimedGameEngine;
import GaT.model.*;
import GaT.search.MoveGenerator;
import java.util.List;

/**
 * Debug-Test zur Identifikation des Knotenzahl-Problems
 */
public class NodeCountDebugTest {

    public static void main(String[] args) {
        System.out.println("=== NODE COUNT DEBUG TEST ===\n");

        // Test 1: Zuggenerierung
        testMoveGeneration();

        // Test 2: Einfache Suche ohne TT
        testSimpleSearch();

        // Test 3: Suche mit TT
        testSearchWithTT();

        // Test 4: Zeitbasierte Suche
        testTimedSearch();
    }

    private static void testMoveGeneration() {
        System.out.println("TEST 1: Move Generation");
        System.out.println("-".repeat(50));

        // Startposition
        GameState state = new GameState();
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        System.out.println("Start position moves: " + moves.size());

        // Nach einem Zug
        if (!moves.isEmpty()) {
            state.applyMove(moves.get(0));
            moves = MoveGenerator.generateAllMoves(state);
            System.out.println("After first move: " + moves.size());
        }

        // Mittlere Position
        state = GameState.fromFen("r1r11RG3/6r1/3r11r21/7/3b23/1b15/b12BG1b1b1 b");
        moves = MoveGenerator.generateAllMoves(state);
        System.out.println("Mid-game position moves: " + moves.size());

        System.out.println();
    }

    private static void testSimpleSearch() {
        System.out.println("TEST 2: Simple Search (No TT)");
        System.out.println("-".repeat(50));

        GameEngine engine = new GameEngine();

        // Deaktiviere TT temporär
        engine.clearCaches();

        GameState state = new GameState();

        // Suche mit verschiedenen Tiefen
        for (int depth = 1; depth <= 5; depth++) {
            engine.resetStatistics();

            long startTime = System.currentTimeMillis();
            Move move = engine.findBestMove(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA);
            long timeMs = System.currentTimeMillis() - startTime;

            long nodes = engine.getStatistics().getTotalNodes();
            double nps = timeMs > 0 ? (nodes * 1000.0 / timeMs) : 0;

            System.out.printf("Depth %d: %d nodes in %dms (%.0f nps) - Move: %s%n",
                    depth, nodes, timeMs, nps, move);
        }

        System.out.println();
    }

    private static void testSearchWithTT() {
        System.out.println("TEST 3: Search with TT");
        System.out.println("-".repeat(50));

        GameEngine engine = new GameEngine();
        GameState state = new GameState();

        // Erste Suche
        engine.resetStatistics();
        long start1 = System.currentTimeMillis();
        Move move1 = engine.findBestMove(state, 6, SearchConfig.SearchStrategy.PVS_Q);
        long time1 = System.currentTimeMillis() - start1;
        long nodes1 = engine.getStatistics().getTotalNodes();

        System.out.printf("First search: %d nodes in %dms - Move: %s%n",
                nodes1, time1, move1);

        // Zweite Suche (sollte TT nutzen)
        engine.resetStatistics();
        long start2 = System.currentTimeMillis();
        Move move2 = engine.findBestMove(state, 6, SearchConfig.SearchStrategy.PVS_Q);
        long time2 = System.currentTimeMillis() - start2;
        long nodes2 = engine.getStatistics().getTotalNodes();

        System.out.printf("Second search: %d nodes in %dms - Move: %s%n",
                nodes2, time2, move2);

        double ttHitRate = engine.getStatistics().getTTHitRate();
        System.out.printf("TT Hit Rate: %.1f%%%n", ttHitRate * 100);

        System.out.println();
    }

    private static void testTimedSearch() {
        System.out.println("TEST 4: Timed Search");
        System.out.println("-".repeat(50));

        TimedGameEngine timedEngine = new TimedGameEngine();
        timedEngine.setDebugMode(true);

        GameState state = new GameState();

        // Test mit verschiedenen Zeitlimits
        long[] timeLimits = {1000, 2000, 5000};

        for (long timeLimit : timeLimits) {
            System.out.printf("\nTime limit: %dms%n", timeLimit);

            long startTime = System.currentTimeMillis();
            Move move = timedEngine.findBestMove(state, timeLimit,
                    SearchConfig.SearchStrategy.PVS_Q);
            long actualTime = System.currentTimeMillis() - startTime;

            System.out.printf("Actual time used: %dms - Move: %s%n",
                    actualTime, move);

            // Stats ausgeben
            String stats = timedEngine.getSearchStats();
            if (stats != null && !stats.isEmpty()) {
                System.out.println("Stats: " + stats);
            }
        }
    }

    /**
     * Hilfsklasse zum Tracken der Suche
     */
    static class SearchTracer {
        private int maxDepthSeen = 0;
        private int[] nodesPerDepth = new int[100];

        public void traceNode(int depth) {
            if (depth >= 0 && depth < nodesPerDepth.length) {
                nodesPerDepth[depth]++;
                maxDepthSeen = Math.max(maxDepthSeen, depth);
            }
        }

        public void printReport() {
            System.out.println("\nSearch Trace Report:");
            for (int d = 0; d <= maxDepthSeen; d++) {
                if (nodesPerDepth[d] > 0) {
                    System.out.printf("  Depth %d: %d nodes%n", d, nodesPerDepth[d]);
                }
            }
        }
    }
}