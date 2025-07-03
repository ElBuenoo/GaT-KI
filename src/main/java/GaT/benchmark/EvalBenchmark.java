package GaT.benchmark;

import GaT.model.GameState;
import GaT.engine.GameEngine;
import GaT.evaluation.Evaluator;

import java.util.ArrayList;
import java.util.List;

public class EvalBenchmark {
    public static void main(String[] args) {

        // ✅ Use new GameEngine instead of static Minimax
        GameEngine engine = new GameEngine();

        // ✅ Or use Evaluator directly for pure evaluation benchmarks
        Evaluator evaluator = new Evaluator();

        GameState state = GameState.fromFen("3RG3/4r32/2b34/7/7/7/3BG3 r");
        List<Long> benchmarks = new ArrayList<>();

        System.out.println("=== EVALUATION BENCHMARK ===");
        System.out.println("Testing position: 3RG3/4r32/2b34/7/7/7/3BG3 r");
        System.out.println("Iterations: 100 x 10,000 evaluations");
        System.out.println();

        for (int i = 0; i < 100; i++) {
            long startTime = System.currentTimeMillis();

            for (int j = 0; j < 10_000; j++) {
                // ✅ NEW: Use evaluator instead of Minimax.evaluate
                evaluator.evaluate(state, 1);
            }

            long endTime = System.currentTimeMillis();
            benchmarks.add(endTime - startTime);

            // Progress indicator
            if ((i + 1) % 10 == 0) {
                System.out.printf("Completed %d/100 iterations...\n", i + 1);
            }
        }

        // Calculate statistics
        long avg = benchmarks.stream().reduce(0L, Long::sum) / benchmarks.size();
        long min = benchmarks.stream().min(Long::compare).orElse(0L);
        long max = benchmarks.stream().max(Long::compare).orElse(0L);

        double avgSeconds = (double) avg / 1000;
        double evaluationsPerSecond = 10_000.0 / avgSeconds;

        System.out.println();
        System.out.println("=== BENCHMARK RESULTS ===");
        System.out.println("Raw Values: " + benchmarks);
        System.out.println("Average Execution Time: " + avg + " milliseconds");
        System.out.println("Min Time: " + min + "ms, Max Time: " + max + "ms");
        System.out.printf("Average per iteration: %.2f seconds\n", avgSeconds);
        System.out.printf("Evaluations per second: %.0f\n", evaluationsPerSecond);
        System.out.printf("Microseconds per evaluation: %.2f μs\n", (avgSeconds * 1_000_000) / 10_000);

        // ✅ Test different evaluation methods
        benchmarkEvaluationMethods(state);
    }

    /**
     * ✅ NEW: Benchmark different evaluation approaches
     */
    private static void benchmarkEvaluationMethods(GameState state) {
        System.out.println("\n=== EVALUATION METHOD COMPARISON ===");

        Evaluator evaluator = new Evaluator();
        GameEngine engine = new GameEngine();

        // Test different evaluation depths
        int[] depths = {0, 1, 2, 3};
        int iterations = 1000;

        for (int depth : depths) {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < iterations; i++) {
                evaluator.evaluate(state, depth);
            }

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double timePerEval = (double) totalTime / iterations;

            System.out.printf("Depth %d: %dms total, %.3fms per evaluation\n",
                    depth, totalTime, timePerEval);
        }

        // ✅ Benchmark GameEngine.evaluate vs direct Evaluator
        System.out.println("\nDirect Evaluator vs GameEngine comparison:");

        // Direct evaluator
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            evaluator.evaluate(state, 0);
        }
        long directTime = System.currentTimeMillis() - startTime;

        // Through GameEngine
        startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            engine.evaluate(state);
        }
        long engineTime = System.currentTimeMillis() - startTime;

        System.out.printf("Direct Evaluator: %dms\n", directTime);
        System.out.printf("Through GameEngine: %dms\n", engineTime);
        System.out.printf("Overhead: %.1f%%\n",
                ((double)(engineTime - directTime) / directTime) * 100);
    }

    /**
     * ✅ NEW: Benchmark complex positions
     */
    public static void benchmarkComplexPositions() {
        System.out.println("\n=== COMPLEX POSITION BENCHMARK ===");

        Evaluator evaluator = new Evaluator();

        String[] testPositions = {
                "r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r", // Opening
                "7/7/7/3RG3/7/7/3BG3 r",                                      // Simple
                "7/7/3b33/BG1r43/3RG3/7/7 r",                                 // Tactical
                "3RG3/7/7/7/7/7/7 r"                                          // Endgame
        };

        String[] names = {"Opening", "Simple", "Tactical", "Endgame"};

        for (int pos = 0; pos < testPositions.length; pos++) {
            GameState state = GameState.fromFen(testPositions[pos]);

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                evaluator.evaluate(state, 1);
            }
            long endTime = System.currentTimeMillis();

            System.out.printf("%s position: %dms (1000 evaluations)\n",
                    names[pos], endTime - startTime);
        }
    }

    /**
     * ✅ NEW: Memory usage benchmark
     */
    public static void benchmarkMemoryUsage() {
        System.out.println("\n=== MEMORY USAGE BENCHMARK ===");

        Runtime runtime = Runtime.getRuntime();

        // Force garbage collection
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create multiple engines
        List<GameEngine> engines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            engines.add(new GameEngine());
        }

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        System.out.printf("Memory used by 10 GameEngines: %.2f MB\n",
                memoryUsed / (1024.0 * 1024.0));
        System.out.printf("Memory per GameEngine: %.2f KB\n",
                memoryUsed / (10.0 * 1024.0));

        // Cleanup
        engines.clear();
        runtime.gc();
    }
}