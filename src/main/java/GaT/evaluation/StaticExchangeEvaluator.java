package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import java.util.*;

/**
 * STATIC EXCHANGE EVALUATION - Stop Bad Trades
 *
 * Calculates the material gain/loss from a sequence of captures on a square.
 * Essential for avoiding bad trades and tactical blunders.
 */
public class StaticExchangeEvaluator {

    // === PIECE VALUES FOR SEE ===
    private static final int GUARD_VALUE = 1500;
    private static final int TOWER_BASE_VALUE = 100;

    // === DIRECTION VECTORS ===
    private static final int[] DIRECTIONS = {-1, 1, -7, 7}; // Left, Right, Up, Down

    /**
     * MAIN SEE INTERFACE - Evaluates a capture move
     */
    public static int evaluateCapture(Move captureMove, GameState state) {
        if (!isCapture(captureMove, state)) {
            return 0; // Not a capture
        }

        long toBit = GameState.bit(captureMove.to);
        boolean isRed = state.redToMove;

        // Get the initial capture value
        int capturedValue = getCapturedPieceValue(captureMove.to, state, isRed);

        if (capturedValue == 0) {
            return 0; // Nothing to capture
        }

        // Simulate the exchange sequence
        List<Integer> exchangeSequence = calculateExchangeSequence(captureMove, state);

        // Calculate net material gain from the exchange
        return calculateNetGain(exchangeSequence, capturedValue);
    }

    /**
     * ENHANCED SEE - Consider specific attacker value
     */
    public static int evaluateExchange(int square, GameState state, boolean byRed) {
        // Find the least valuable attacker
        AttackerInfo leastValuableAttacker = findLeastValuableAttacker(square, state, byRed);

        if (leastValuableAttacker == null) {
            return 0; // No attackers
        }

        // Create a virtual capture move
        Move virtualCapture = new Move(leastValuableAttacker.square, square, leastValuableAttacker.range);

        return evaluateCapture(virtualCapture, state);
    }

    /**
     * CALCULATE EXCHANGE SEQUENCE - Simulate captures and recaptures
     */
    private static List<Integer> calculateExchangeSequence(Move initialCapture, GameState state) {
        List<Integer> sequence = new ArrayList<>();

        // Create a working copy
        GameState workingState = state.copy();
        int targetSquare = initialCapture.to;

        // Initial capture
        int attackerValue = getAttackerValue(initialCapture, workingState);
        workingState.applyMove(initialCapture);
        sequence.add(attackerValue);

        // Simulate recaptures
        boolean currentSideIsRed = !state.redToMove; // Opponent responds

        for (int depth = 1; depth < 10; depth++) { // Limit to 10 exchanges
            AttackerInfo nextAttacker = findLeastValuableAttacker(targetSquare, workingState, currentSideIsRed);

            if (nextAttacker == null) {
                break; // No more attackers
            }

            // Apply the recapture
            Move recapture = new Move(nextAttacker.square, targetSquare, nextAttacker.range);
            sequence.add(nextAttacker.value);

            workingState.applyMove(recapture);
            currentSideIsRed = !currentSideIsRed;
        }

        return sequence;
    }

    /**
     * CALCULATE NET GAIN from exchange sequence
     */
    private static int calculateNetGain(List<Integer> exchangeSequence, int initialCaptureValue) {
        if (exchangeSequence.isEmpty()) {
            return 0;
        }

        // Build gain array: [captured_value, attacker1_value, attacker2_value, ...]
        List<Integer> gains = new ArrayList<>();
        gains.add(initialCaptureValue);
        gains.addAll(exchangeSequence);

        // Work backwards to find the best outcome for each side
        // This is the minimax part of SEE
        for (int i = gains.size() - 2; i >= 0; i--) {
            // Each side chooses to capture only if it's beneficial
            gains.set(i, Math.max(0, gains.get(i) - gains.get(i + 1)));
        }

        return gains.get(0);
    }

    /**
     * FIND LEAST VALUABLE ATTACKER to a square
     */
    private static AttackerInfo findLeastValuableAttacker(int square, GameState state, boolean byRed) {
        List<AttackerInfo> attackers = findAllAttackers(square, state, byRed);

        if (attackers.isEmpty()) {
            return null;
        }

        // Sort by value (ascending) to get least valuable first
        attackers.sort(Comparator.comparingInt(a -> a.value));

        return attackers.get(0);
    }

    /**
     * FIND ALL ATTACKERS to a square
     */
    private static List<AttackerInfo> findAllAttackers(int square, GameState state, boolean byRed) {
        List<AttackerInfo> attackers = new ArrayList<>();

        long friendlyPieces = byRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((friendlyPieces & GameState.bit(i)) == 0) {
                continue; // Not our piece
            }

            // Check if this piece can attack the target square
            int range = getPieceRange(i, state, byRed);
            if (range > 0 && canAttackSquare(i, square, range, state)) {
                int value = getPieceValue(i, state, byRed);
                attackers.add(new AttackerInfo(i, value, range));
            }
        }

        return attackers;
    }

    /**
     * CHECK if piece can attack square (considering path blocking)
     */
    private static boolean canAttackSquare(int from, int to, int range, GameState state) {
        if (from == to) return false;

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file (Guard & Towers rule)
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        if (distance > range) return false;

        // Check if path is clear
        return isPathClear(from, to, state);
    }

    /**
     * PATH CLEAR CHECK - ensuring no pieces block the attack
     */
    private static boolean isPathClear(int from, int to, GameState state) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        int step;
        if (rankDiff != 0) {
            step = rankDiff > 0 ? 7 : -7; // Vertical movement
        } else {
            step = fileDiff > 0 ? 1 : -1;  // Horizontal movement
        }

        int current = from + step;
        while (current != to) {
            if (isSquareOccupied(current, state)) {
                return false; // Path blocked
            }
            current += step;
        }

        return true;
    }

    // === HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static int getCapturedPieceValue(int square, GameState state, boolean capturedByRed) {
        long bit = GameState.bit(square);

        // Check enemy guard
        long enemyGuard = capturedByRed ? state.blueGuard : state.redGuard;
        if ((enemyGuard & bit) != 0) {
            return GUARD_VALUE;
        }

        // Check enemy towers
        long enemyTowers = capturedByRed ? state.blueTowers : state.redTowers;
        if ((enemyTowers & bit) != 0) {
            int height = capturedByRed ? state.blueStackHeights[square] : state.redStackHeights[square];
            return height * TOWER_BASE_VALUE;
        }

        return 0;
    }

    private static int getAttackerValue(Move move, GameState state) {
        return getPieceValue(move.from, state, state.redToMove);
    }

    private static int getPieceValue(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);

        // Check if it's a guard
        long guard = isRed ? state.redGuard : state.blueGuard;
        if ((guard & bit) != 0) {
            return GUARD_VALUE;
        }

        // Check towers
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
        return height * TOWER_BASE_VALUE;
    }

    private static int getPieceRange(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);

        // Check if it's a guard
        long guard = isRed ? state.redGuard : state.blueGuard;
        if ((guard & bit) != 0) {
            return 1; // Guards move 1 square
        }

        // Tower range = height
        return isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
    }

    private static boolean isSquareOccupied(int square, GameState state) {
        long bit = GameState.bit(square);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & bit) != 0;
    }

    // === INNER CLASSES ===

    private static class AttackerInfo {
        final int square;
        final int value;
        final int range;

        AttackerInfo(int square, int value, int range) {
            this.square = square;
            this.value = value;
            this.range = range;
        }
    }

    // === PUBLIC UTILITY METHODS ===

    /**
     * QUICK SEE CHECK - Is this capture profitable?
     */
    public static boolean isCaptureProfitable(Move capture, GameState state) {
        return evaluateCapture(capture, state) > 0;
    }

    /**
     * MOVE FILTERING - Filter out bad captures
     */
    public static List<Move> filterBadCaptures(List<Move> moves, GameState state) {
        return moves.stream()
                .filter(move -> !isCapture(move, state) || isCaptureProfitable(move, state))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * DEBUGGING - Get SEE score with explanation
     */
    public static String explainSEE(Move capture, GameState state) {
        if (!isCapture(capture, state)) {
            return "Not a capture move";
        }

        int score = evaluateCapture(capture, state);
        int capturedValue = getCapturedPieceValue(capture.to, state, state.redToMove);
        int attackerValue = getAttackerValue(capture, state);

        return String.format("SEE: %s captures piece worth %d with piece worth %d → Net: %+d",
                capture, capturedValue, attackerValue, score);
    }
}