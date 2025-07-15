package GaT;

import GaT.engine.TurmWaechterEngine;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.UnifiedStatistics;

public class Main {

    public static void main(String[] args) {
        System.out.println("🚀 Testing Optimized Engine");
        System.out.println("===========================");

        // Initialize optimized engine
        TurmWaechterEngine engine = new TurmWaechterEngine();

        // Test position
        GameState state = GameState.fromFen("7/7/7/BG6/3b33/3RG3/7 r");

        System.out.println("📋 Position:");
        state.printBoard();

        // Reset statistics
        UnifiedStatistics.getInstance().reset();

        // Search with 5 second time limit
        System.out.println("\n🔍 Searching...");
        long startTime = System.currentTimeMillis();
        Move bestMove = engine.findBestMove(state, 5000);
        long searchTime = System.currentTimeMillis() - startTime;

        // Results
        UnifiedStatistics stats = UnifiedStatistics.getInstance();

        System.out.println("\n✅ RESULTS:");
        System.out.println("Best Move: " + bestMove);
        System.out.println("Search Time: " + searchTime + "ms");
        System.out.println("Max Depth: " + stats.getMaxDepth());
        System.out.println("Nodes Searched: " + String.format("%,d", stats.getNodeCount()));
        System.out.println("Nodes/Second: " + String.format("%,d", stats.getNodesPerSecond()));

        // Show if move is good
        if (bestMove != null) {
            System.out.println("✅ Search works!");
        } else {
            System.out.println("❌ Search failed!");
        }
    }
}