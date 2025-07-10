package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * STATIC EXCHANGE EVALUATOR for Turm und Wächter
 *
 * Analyzes capture sequences to determine net material gain/loss.
 * Used to evaluate whether captures are beneficial or result in material loss.
 */
public class StaticExchangeEvaluator {

    // Piece values for SEE calculation
    private static final int GUARD_VALUE = 2000;
    private static final int TOWER_BASE_VALUE = 100;

    /**
     * Main SEE evaluation method
     * @param state Current game state
     * @param move The capture move to evaluate
     * @return Net material value (positive = good for current player, negative = material loss)
     */
    public static int evaluate(GameState state, Move move) {
        if (!isCapture(move, state)) {
            return 0; // Not a capture
        }

        try {
            // Get the value of the captured piece
            int capturedValue = getCapturedPieceValue(state, move);

            // If no piece is captured, return 0
            if (capturedValue == 0) {
                return 0;
            }

            // Simulate the capture sequence
            return simulateCaptureSequence(state, move, capturedValue);

        } catch (Exception e) {
            // Fallback to basic capture value if SEE fails
            return getBasicCaptureValue(state, move);
        }
    }

    /**
     * Simulate the complete capture sequence using minimax approach
     */
    private static int simulateCaptureSequence(GameState state, Move initialMove, int capturedValue) {
        // Create a copy to simulate on
        GameState simState = state.copy();

        // Apply the initial capture
        int attackerValue = getAttackerValue(simState, initialMove);
        simState.applyMove(initialMove);

        // Find all possible recaptures on the target square
        List<Move> recaptures = findRecaptures(simState, initialMove.to);

        if (recaptures.isEmpty()) {
            // No recapture possible - we win the captured piece
            return capturedValue;
        }

        // Find the least valuable recapture (most efficient)
        Move bestRecapture = findLeastValuableAttacker(simState, recaptures);

        if (bestRecapture == null) {
            return capturedValue; // No valid recapture
        }

        // Recursively evaluate the recapture
        int recaptureValue = getAttackerValue(simState, bestRecapture);
        GameState afterRecapture = simState.copy();
        afterRecapture.applyMove(bestRecapture);

        // Continue the sequence from opponent's perspective
        int futureValue = -simulateCaptureSequence(afterRecapture, bestRecapture, attackerValue);

        // Current player can choose whether to recapture or not
        return Math.max(capturedValue - recaptureValue + futureValue, capturedValue);
    }

    /**
     * Get the value of the piece being captured
     */
    private static int getCapturedPieceValue(GameState state, Move move) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Check if capturing enemy guard - FIXED: Added parentheses for correct precedence
        if (isRed && ((state.blueGuard & toBit) != 0)) {
            return GUARD_VALUE;
        } else if (!isRed && ((state.redGuard & toBit) != 0)) {
            return GUARD_VALUE;
        }

        // Check tower height
        int stackHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return stackHeight * TOWER_BASE_VALUE;
    }

    /**
     * Get the value of the attacking piece
     */
    private static int getAttackerValue(GameState state, Move move) {
        long fromBit = GameState.bit(move.from);
        boolean isRed = state.redToMove;

        // Check if attacker is guard - FIXED: Added parentheses for correct precedence
        if (((isRed ? state.redGuard : state.blueGuard) & fromBit) != 0) {
            return GUARD_VALUE;
        }

        // Tower attacker - value based on amount moved
        return move.amountMoved * TOWER_BASE_VALUE;
    }

    /**
     * Find all possible recaptures on a square
     */
    private static List<Move> findRecaptures(GameState state, int targetSquare) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> recaptures = new ArrayList<>();

        for (Move move : allMoves) {
            if (move.to == targetSquare && isCapture(move, state)) {
                recaptures.add(move);
            }
        }

        return recaptures;
    }

    /**
     * Find the least valuable piece that can recapture (MVV-LVA principle)
     */
    private static Move findLeastValuableAttacker(GameState state, List<Move> recaptures) {
        if (recaptures.isEmpty()) {
            return null;
        }

        Move bestMove = null;
        int lowestValue = Integer.MAX_VALUE;

        for (Move move : recaptures) {
            int attackerValue = getAttackerValue(state, move);
            if (attackerValue < lowestValue) {
                lowestValue = attackerValue;
                bestMove = move;
            }
        }

        return bestMove;
    }

    /**
     * Check if a move is a capture
     */
    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        if (state.redToMove) {
            // Red to move - check if capturing blue piece
            return ((state.blueGuard & toBit) != 0) || ((state.blueTowers & toBit) != 0);
        } else {
            // Blue to move - check if capturing red piece
            return ((state.redGuard & toBit) != 0) || ((state.redTowers & toBit) != 0);
        }
    }

    /**
     * Fallback method for basic capture value (when SEE fails)
     */
    private static int getBasicCaptureValue(GameState state, Move move) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Value of captured piece - FIXED: Added parentheses for correct precedence
        if (isRed && ((state.blueGuard & toBit) != 0)) {
            return GUARD_VALUE; // Captured blue guard
        } else if (!isRed && ((state.redGuard & toBit) != 0)) {
            return GUARD_VALUE; // Captured red guard
        } else {
            // Captured tower
            int stackHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            return stackHeight * TOWER_BASE_VALUE;
        }
    }

    /**
     * Quick SEE evaluation for move ordering (simplified version)
     */
    public static int evaluateQuick(GameState state, Move move) {
        if (!isCapture(move, state)) {
            return 0;
        }

        int capturedValue = getCapturedPieceValue(state, move);
        int attackerValue = getAttackerValue(state, move);

        // Simple heuristic: captured value minus attacker value
        // This is much faster but less accurate than full SEE
        return capturedValue - attackerValue;
    }

    /**
     * Check if a capture is likely to be good (quick evaluation)
     */
    public static boolean isGoodCapture(GameState state, Move move) {
        return evaluate(state, move) > 0;
    }

    /**
     * Check if a capture loses material
     */
    public static boolean losesMaterial(GameState state, Move move) {
        return evaluate(state, move) < -50; // Small threshold for rounding errors
    }
}