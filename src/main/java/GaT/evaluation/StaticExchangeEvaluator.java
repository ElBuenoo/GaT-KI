package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import java.util.ArrayList;
import java.util.List;

/**
 * STATIC EXCHANGE EVALUATION (SEE)
 * Evaluates the outcome of a sequence of captures on a single square
 * to determine if a capture is profitable or leads to material loss.
 */
public class StaticExchangeEvaluator {

    // Piece values for SEE calculations
    private static final int GUARD_VALUE = 2000;
    private static final int TOWER_VALUE = 100;

    /**
     * Main SEE interface - evaluates if a capture is winning, losing, or neutral
     * @return positive if capture gains material, negative if loses, 0 if equal
     */
    public static int evaluate(GameState state, Move move) {
        if (!isCapture(move, state)) {
            return 0; // Not a capture, no exchange
        }

        // Get initial capture value
        int capturedValue = getCapturedPieceValue(state, move);

        // Simulate the capture
        GameState copy = state.copy();
        copy.applyMove(move);

        // Get all attackers to the target square
        List<Attacker> attackers = getAllAttackers(copy, move.to);

        // If no more attackers, we simply win the captured piece
        if (attackers.isEmpty()) {
            return capturedValue;
        }

        // Run the exchange simulation
        return capturedValue - simulateExchanges(copy, move.to, getMovingPieceValue(state, move), attackers);
    }

    /**
     * Quick SEE check - returns true if capture appears to be safe
     */
    public static boolean isSafeCapture(GameState state, Move move) {
        return evaluate(state, move) >= 0;
    }

    /**
     * Simulate the exchange sequence
     */
    private static int simulateExchanges(GameState state, int square, int lastCapturedValue, List<Attacker> attackers) {
        if (attackers.isEmpty()) {
            return 0;
        }

        // Find least valuable attacker (to maximize exchange outcome)
        Attacker bestAttacker = null;
        for (Attacker attacker : attackers) {
            if (bestAttacker == null || attacker.value < bestAttacker.value) {
                bestAttacker = attacker;
            }
        }

        // If the least valuable attacker is more valuable than what it would capture, don't capture
        if (bestAttacker.value > lastCapturedValue) {
            return 0; // Stand pat
        }

        // Simulate this capture
        GameState copy = state.copy();
        Move captureMove = new Move(bestAttacker.position, square, bestAttacker.moveAmount);
        copy.applyMove(captureMove);

        // Get remaining attackers after this capture
        List<Attacker> remainingAttackers = getAllAttackers(copy, square);

        // Remove attackers from the same side as the one that just captured
        remainingAttackers.removeIf(a -> a.isRed == bestAttacker.isRed);

        // Recursive evaluation
        int futureExchanges = simulateExchanges(copy, square, bestAttacker.value, remainingAttackers);

        // Return value is what we capture minus what we lose in future
        return lastCapturedValue - futureExchanges;
    }

    /**
     * Get all pieces that can attack a square
     */
    private static List<Attacker> getAllAttackers(GameState state, int targetSquare) {
        List<Attacker> attackers = new ArrayList<>();

        // Check all squares for potential attackers
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red pieces
            if (state.redStackHeights[i] > 0) {
                if (canAttack(i, targetSquare, state.redStackHeights[i])) {
                    attackers.add(new Attacker(i, TOWER_VALUE * state.redStackHeights[i],
                            state.redStackHeights[i], true));
                }
            }

            // Red guard
            if ((state.redGuard & GameState.bit(i)) != 0) {
                if (canAttack(i, targetSquare, 1)) {
                    attackers.add(new Attacker(i, GUARD_VALUE, 1, true));
                }
            }

            // Blue pieces
            if (state.blueStackHeights[i] > 0) {
                if (canAttack(i, targetSquare, state.blueStackHeights[i])) {
                    attackers.add(new Attacker(i, TOWER_VALUE * state.blueStackHeights[i],
                            state.blueStackHeights[i], false));
                }
            }

            // Blue guard
            if ((state.blueGuard & GameState.bit(i)) != 0) {
                if (canAttack(i, targetSquare, 1)) {
                    attackers.add(new Attacker(i, GUARD_VALUE, 1, false));
                }
            }
        }

        return attackers;
    }

    /**
     * Check if a piece at 'from' can attack 'to' with given range
     */
    private static boolean canAttack(int from, int to, int range) {
        if (from == to) return false;

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        // Check range
        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    /**
     * Get the value of the piece being captured
     */
    private static int getCapturedPieceValue(GameState state, Move move) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Check guards first (more valuable)
        if ((isRed && (state.blueGuard & toBit) != 0) ||
                (!isRed && (state.redGuard & toBit) != 0)) {
            return GUARD_VALUE;
        }

        // Check towers
        if (isRed && (state.blueTowers & toBit) != 0) {
            return state.blueStackHeights[move.to] * TOWER_VALUE;
        } else if (!isRed && (state.redTowers & toBit) != 0) {
            return state.redStackHeights[move.to] * TOWER_VALUE;
        }

        return 0; // No capture (shouldn't happen if isCapture was true)
    }

    /**
     * Get the value of the piece making the move
     */
    private static int getMovingPieceValue(GameState state, Move move) {
        long fromBit = GameState.bit(move.from);
        boolean isRed = state.redToMove;

        // Check if it's a guard move
        if ((isRed && (state.redGuard & fromBit) != 0) ||
                (!isRed && (state.blueGuard & fromBit) != 0)) {
            return GUARD_VALUE;
        }

        // Otherwise it's a tower move
        return move.amountMoved * TOWER_VALUE;
    }

    /**
     * Check if a move is a capture
     */
    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Internal class to represent an attacker
     */
    private static class Attacker {
        final int position;
        final int value;
        final int moveAmount;
        final boolean isRed;

        Attacker(int position, int value, int moveAmount, boolean isRed) {
            this.position = position;
            this.value = value;
            this.moveAmount = moveAmount;
            this.isRed = isRed;
        }
    }
}