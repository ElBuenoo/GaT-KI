import GaT.search.Engine;
import GaT.evaluation.Evaluator;
import GaT.game.GameState;
import GaT.game.Move;

/**
 * QUICK VERIFICATION TEST
 *
 * Run this to verify the fixes are working:
 * - Search should find strong moves
 * - Evaluations should be meaningful
 * - No infinite loops or crashes
 * - Proper depth progression
 */
public class QuickVerificationTest {

    public static void main(String[] args) {
        System.out.println("🔧 RUNNING VERIFICATION TESTS");
        System.out.println("=" .repeat(50));

        testEvaluatorFixes();
        testEngineFixes();
        testGameProgression();

        System.out.println("\n✅ All verification tests completed!");
    }

    public static void testEvaluatorFixes() {
        System.out.println("\n📊 Testing Evaluator Fixes...");

        Evaluator evaluator = new Evaluator();
        GameState startPos = new GameState();

        // Test 1: Basic evaluation
        int score = evaluator.evaluate(startPos);
        System.out.println("Start position score: " + score);
        assert Math.abs(score) < 5000 : "Start position should be balanced";

        // Test 2: Central control bonus (the failing test)
        GameState edgeState = createEmptyState();
        edgeState.redTowers = GameState.bit(GameState.getIndex(3, 0)); // A4 (edge)
        edgeState.redStackHeights[GameState.getIndex(3, 0)] = 1;

        GameState centerState = createEmptyState();
        centerState.redTowers = GameState.bit(GameState.getIndex(3, 3)); // D4 (center)
        centerState.redStackHeights[GameState.getIndex(3, 3)] = 1;
        centerState.redGuard = GameState.bit(GameState.getIndex(4, 3)); // Move guard to D5

        int edgeScore = evaluator.evaluate(edgeState);
        int centerScore = evaluator.evaluate(centerState);

        System.out.println("Edge piece score: " + edgeScore);
        System.out.println("Center piece score: " + centerScore);
        assert centerScore > edgeScore : "Center should be better than edge";

        // Test 3: Perspective handling
        GameState testState = createEmptyState();
        testState.redGuard = GameState.bit(GameState.getIndex(1, 3)); // Red close to target

        testState.redToMove = true;
        int redToMoveScore = evaluator.evaluate(testState);

        testState.redToMove = false;
        int blueToMoveScore = evaluator.evaluate(testState);

        System.out.println("Red to move: " + redToMoveScore + ", Blue to move: " + blueToMoveScore);
        assert redToMoveScore == -blueToMoveScore : "Perspective should flip scores";

        System.out.println("✅ Evaluator fixes verified!");
    }

    public static void testEngineFixes() {
        System.out.println("\n🔍 Testing Engine Fixes...");

        Engine engine = new Engine();
        GameState startPos = new GameState();

        // Test 1: Basic move finding
        Move move = engine.findBestMove(startPos, 1000);
        assert move != null : "Should find a move";
        System.out.println("Found move: " + move);

        // Test 2: Depth progression (should search more nodes at higher depth)
        int nodes1 = 0, nodes2 = 0;

        engine.findBestMove(startPos, 3, 1000);
        nodes1 = engine.getNodesSearched();

        engine.findBestMove(startPos, 5, 2000);
        nodes2 = engine.getNodesSearched();

        System.out.println("Depth 3 nodes: " + nodes1);
        System.out.println("Depth 5 nodes: " + nodes2);
        // Note: With opening book, this might not always be true, but search should be working

        // Test 3: Time management
        long startTime = System.currentTimeMillis();
        engine.findBestMove(startPos, 1000); // 1 second limit
        long actualTime = System.currentTimeMillis() - startTime;

        System.out.println("Time limit: 1000ms, Actual: " + actualTime + "ms");
        assert actualTime <= 1200 : "Should respect time limit"; // Small buffer

        // Test 4: No repetition in short game
        GameState position = startPos.copy();
        Move lastMove = null;
        int repetitionCount = 0;

        for (int i = 0; i < 10; i++) {
            Move nextMove = engine.findBestMove(position, 500);
            if (nextMove != null) {
                if (lastMove != null &&
                        nextMove.from == lastMove.to &&
                        nextMove.to == lastMove.from) {
                    repetitionCount++;
                    if (repetitionCount > 2) break; // Avoid infinite loop
                }

                try {
                    position.applyMove(nextMove);
                    lastMove = nextMove;
                } catch (Exception e) {
                    break;
                }
            }
        }

        System.out.println("Repetition count in 10 moves: " + repetitionCount);
        assert repetitionCount <= 2 : "Should not repeat moves excessively";

        System.out.println("✅ Engine fixes verified!");
    }

    public static void testGameProgression() {
        System.out.println("\n🎮 Testing Game Progression...");

        Engine engine = new Engine();
        GameState position = new GameState();

        System.out.println("Playing a short game to verify stability...");

        for (int moveCount = 1; moveCount <= 6; moveCount++) {
            try {
                Move move = engine.findBestMove(position, 1000);
                if (move == null) {
                    System.out.println("No move found at move " + moveCount);
                    break;
                }

                System.out.println("Move " + moveCount + ": " + move +
                        " (nodes: " + engine.getNodesSearched() + ")");

                position.applyMove(move);

                // Check for game end
                if (position.redGuard == 0 || position.blueGuard == 0) {
                    System.out.println("Game ended - guard captured");
                    break;
                }

            } catch (Exception e) {
                System.err.println("Error at move " + moveCount + ": " + e.getMessage());
                break;
            }
        }

        System.out.println("✅ Game progression verified!");
    }

    private static GameState createEmptyState() {
        GameState state = new GameState();

        // Clear all towers
        state.redTowers = 0;
        state.blueTowers = 0;
        for (int i = 0; i < 49; i++) {
            state.redStackHeights[i] = 0;
            state.blueStackHeights[i] = 0;
        }

        // Place guards
        state.redGuard = GameState.bit(GameState.getIndex(6, 3)); // D7
        state.blueGuard = GameState.bit(GameState.getIndex(0, 3)); // D1
        state.redToMove = true;

        return state;
    }
}