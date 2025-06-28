package GaT;

import GaT.engine.GameEngine;
import GaT.model.*;
import GaT.search.MoveGenerator;

/**
 * Example usage of the new refactored architecture
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== Guards & Towers AI - Refactored Architecture ===\n");

        // Create game engine (replaces static Minimax)
        GameEngine engine = new GameEngine();

        // Test position
        GameState state = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");

        System.out.println("Initial position:");
        state.printBoard();

        // Find best move with different strategies
        testStrategy(engine, state, SearchConfig.SearchStrategy.ALPHA_BETA, 5);
        testStrategy(engine, state, SearchConfig.SearchStrategy.PVS, 5);
        testStrategy(engine, state, SearchConfig.SearchStrategy.PVS_Q, 5);

        // Complex position test
        System.out.println("\n=== Complex Position Test ===");
        GameState complex = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");

        long startTime = System.currentTimeMillis();
        Move bestMove = engine.findBestMove(complex, 4);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("Best move: " + bestMove);
        System.out.println("Time: " + elapsed + "ms");
        System.out.println("\nStatistics:");
        System.out.println(engine.getSearchStats());

        // Test backward compatibility
        System.out.println("\n=== Testing Backward Compatibility ===");
        testBackwardCompatibility();
    }

    private static void testStrategy(GameEngine engine, GameState state,
                                     SearchConfig.SearchStrategy strategy, int depth) {
        System.out.println("\n--- Testing " + strategy + " ---");

        engine.resetStatistics();

        long startTime = System.currentTimeMillis();
        Move move = engine.findBestMove(state, depth, strategy);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("Best move: " + move);
        System.out.println("Time: " + elapsed + "ms");
        System.out.println("Nodes: " + engine.getStatistics().getNodeCount());
    }

    @SuppressWarnings("deprecation")
    private static void testBackwardCompatibility() {
        GameState state = new GameState();

        // Old API (deprecated)
        Move oldMove = GaT.search.Minimax.findBestMove(state, 3);
        System.out.println("Old API move: " + oldMove);

        // New API
        GameEngine engine = new GameEngine();
        Move newMove = engine.findBestMove(state, 3);
        System.out.println("New API move: " + newMove);

        // Check game over
        boolean oldGameOver = GaT.search.Minimax.isGameOver(state);
        boolean newGameOver = engine.isGameOver(state);
        System.out.println("Game over check: old=" + oldGameOver + ", new=" + newGameOver);
    }
}