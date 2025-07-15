package GaT.search;

import GaT.model.*;

/**
 * TERMINAL POSITION DETECTOR - Single source of truth for game-over detection
 *
 * ELIMINATES:
 * - Duplicate win/loss/stalemate checks across search layers
 * - Inconsistent terminal evaluation logic
 * - Scattered game-over detection code
 *
 * PROVIDES:
 * - Centralized terminal position detection
 * - Fast terminal evaluation
 * - Consistent game-over scoring
 * - Zero-overhead checks
 */
public final class TerminalPositionDetector {

    // === TERMINAL POSITION TYPES ===
    public enum TerminalType {
        NOT_TERMINAL,           // Game continues
        GUARD_CAPTURED_RED,     // Red guard captured (Blue wins)
        GUARD_CAPTURED_BLUE,    // Blue guard captured (Red wins)
        CASTLE_REACHED_RED,     // Red guard reached blue castle (Red wins)
        CASTLE_REACHED_BLUE,    // Blue guard reached red castle (Blue wins)
        NO_MOVES_RED,          // Red has no legal moves (Blue wins)
        NO_MOVES_BLUE,         // Blue has no legal moves (Red wins)
        STALEMATE              // Draw (rare in Turm & Wächter)
    }

    // === CASTLE POSITIONS (from ConsolidatedSearchConfig) ===
    private static final int RED_CASTLE = ConsolidatedSearchConfig.RED_CASTLE_INDEX;   // D7
    private static final int BLUE_CASTLE = ConsolidatedSearchConfig.BLUE_CASTLE_INDEX; // D1

    // === MAIN DETECTION METHOD ===

    /**
     * Check if position is terminal (game over)
     * FAST: O(1) operations only, no move generation
     */
    public static TerminalType detectTerminal(GameState state) {
        if (state == null || !state.isValid()) {
            return TerminalType.NOT_TERMINAL;
        }

        // 1. GUARD CAPTURE CHECK (highest priority)
        if (state.redGuard == 0) {
            return TerminalType.GUARD_CAPTURED_RED;
        }
        if (state.blueGuard == 0) {
            return TerminalType.GUARD_CAPTURED_BLUE;
        }

        // 2. CASTLE REACH CHECK (second highest priority)
        long redGuardBit = state.redGuard;
        long blueGuardBit = state.blueGuard;

        if ((redGuardBit & GameState.bit(BLUE_CASTLE)) != 0) {
            return TerminalType.CASTLE_REACHED_RED;
        }
        if ((blueGuardBit & GameState.bit(RED_CASTLE)) != 0) {
            return TerminalType.CASTLE_REACHED_BLUE;
        }

        // 3. NO LEGAL MOVES CHECK (expensive, only if needed)
        // Note: This requires move generation, so we do it last
        // Most games end by guard capture or castle reach

        return TerminalType.NOT_TERMINAL; // Most common case
    }

    /**
     * Check if position is terminal including no-moves detection
     * SLOW: Requires move generation, use sparingly
     */
    public static TerminalType detectTerminalWithMoveCheck(GameState state) {
        TerminalType fastResult = detectTerminal(state);
        if (fastResult != TerminalType.NOT_TERMINAL) {
            return fastResult;
        }

        // Check for no legal moves (expensive)
        try {
            java.util.List<Move> moves = GaT.search.MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                return state.redToMove ? TerminalType.NO_MOVES_RED : TerminalType.NO_MOVES_BLUE;
            }
        } catch (Exception e) {
            // If move generation fails, assume not terminal
            return TerminalType.NOT_TERMINAL;
        }

        return TerminalType.NOT_TERMINAL;
    }

    // === TERMINAL EVALUATION ===

    /**
     * Get score for terminal position
     */
    public static int evaluateTerminal(TerminalType terminal, int depthFromRoot) {
        switch (terminal) {
            case GUARD_CAPTURED_RED:
                return -GameValues.CHECKMATE_VALUE + depthFromRoot; // Blue wins
            case GUARD_CAPTURED_BLUE:
                return GameValues.CHECKMATE_VALUE - depthFromRoot;  // Red wins
            case CASTLE_REACHED_RED:
                return GameValues.CHECKMATE_VALUE - depthFromRoot;  // Red wins
            case CASTLE_REACHED_BLUE:
                return -GameValues.CHECKMATE_VALUE + depthFromRoot; // Blue wins
            case NO_MOVES_RED:
                return -GameValues.CHECKMATE_VALUE + depthFromRoot; // Blue wins
            case NO_MOVES_BLUE:
                return GameValues.CHECKMATE_VALUE - depthFromRoot;  // Red wins
            case STALEMATE:
                return GameValues.STALEMATE_VALUE;                  // Draw
            case NOT_TERMINAL:
            default:
                return 0; // Should not be called for non-terminal
        }
    }

    /**
     * Convenience method: detect and evaluate in one call
     */
    public static Integer checkAndEvaluateTerminal(GameState state, int depthFromRoot) {
        TerminalType terminal = detectTerminal(state);
        if (terminal == TerminalType.NOT_TERMINAL) {
            return null; // Not terminal
        }
        return evaluateTerminal(terminal, depthFromRoot);
    }

    // === CONVENIENCE METHODS ===

    /**
     * Quick check: is this position terminal? (fast)
     */
    public static boolean isTerminal(GameState state) {
        return detectTerminal(state) != TerminalType.NOT_TERMINAL;
    }

    /**
     * Check if game is won by red
     */
    public static boolean isRedWin(TerminalType terminal) {
        return terminal == TerminalType.GUARD_CAPTURED_BLUE ||
                terminal == TerminalType.CASTLE_REACHED_RED ||
                terminal == TerminalType.NO_MOVES_BLUE;
    }

    /**
     * Check if game is won by blue
     */
    public static boolean isBlueWin(TerminalType terminal) {
        return terminal == TerminalType.GUARD_CAPTURED_RED ||
                terminal == TerminalType.CASTLE_REACHED_BLUE ||
                terminal == TerminalType.NO_MOVES_RED;
    }

    /**
     * Check if game is drawn
     */
    public static boolean isDraw(TerminalType terminal) {
        return terminal == TerminalType.STALEMATE;
    }

    // === GAME-SPECIFIC CHECKS ===

    /**
     * Check if red guard is in danger (one move from capture)
     */
    public static boolean isRedGuardInDanger(GameState state) {
        if (state.redGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(state.redGuard);
        return canBlueAttackSquare(state, guardPos);
    }

    /**
     * Check if blue guard is in danger (one move from capture)
     */
    public static boolean isBlueGuardInDanger(GameState state) {
        if (state.blueGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
        return canRedAttackSquare(state, guardPos);
    }

    private static boolean canRedAttackSquare(GameState state, int targetSquare) {
        // Check if any red piece can attack the target square
        for (int square = 0; square < 49; square++) { // 7x7 board
            if (state.redStackHeights[square] > 0) {
                if (canTowerAttack(square, targetSquare, state.redStackHeights[square])) {
                    return true;
                }
            }
        }

        // Check red guard
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (isAdjacent(redGuardPos, targetSquare)) {
                return true;
            }
        }

        return false;
    }

    private static boolean canBlueAttackSquare(GameState state, int targetSquare) {
        // Check if any blue piece can attack the target square
        for (int square = 0; square < 49; square++) { // 7x7 board
            if (state.blueStackHeights[square] > 0) {
                if (canTowerAttack(square, targetSquare, state.blueStackHeights[square])) {
                    return true;
                }
            }
        }

        // Check blue guard
        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (isAdjacent(blueGuardPos, targetSquare)) {
                return true;
            }
        }

        return false;
    }

    private static boolean canTowerAttack(int fromSquare, int toSquare, int towerHeight) {
        // Tower can move up to its height in orthogonal directions
        int fromRank = fromSquare / 7;
        int fromFile = fromSquare % 7;
        int toRank = toSquare / 7;
        int toFile = toSquare % 7;

        // Check if move is orthogonal
        boolean sameRank = fromRank == toRank;
        boolean sameFile = fromFile == toFile;

        if (!sameRank && !sameFile) {
            return false; // Not orthogonal
        }

        // Check distance
        int distance = Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
        return distance <= towerHeight;
    }

    private static boolean isAdjacent(int square1, int square2) {
        int rank1 = square1 / 7;
        int file1 = square1 % 7;
        int rank2 = square2 / 7;
        int file2 = square2 % 7;

        int rankDiff = Math.abs(rank1 - rank2);
        int fileDiff = Math.abs(file1 - file2);

        return (rankDiff == 1 && fileDiff == 0) || (rankDiff == 0 && fileDiff == 1);
    }

    // === FORCING SEQUENCES ===

    /**
     * Check if position has forcing moves (captures, checks)
     */
    public static boolean hasForcingMoves(GameState state) {
        return isRedGuardInDanger(state) || isBlueGuardInDanger(state);
    }

    /**
     * Estimate how many moves until likely terminal position
     */
    public static int estimateMovesToTerminal(GameState state) {
        TerminalType terminal = detectTerminal(state);
        if (terminal != TerminalType.NOT_TERMINAL) {
            return 0; // Already terminal
        }

        // Simple heuristic based on guard proximity to enemy castle
        int redGuardPos = state.redGuard != 0 ? Long.numberOfTrailingZeros(state.redGuard) : -1;
        int blueGuardPos = state.blueGuard != 0 ? Long.numberOfTrailingZeros(state.blueGuard) : -1;

        int minMoves = Integer.MAX_VALUE;

        if (redGuardPos >= 0) {
            int distanceToBluecastle = getManhattanDistance(redGuardPos, BLUE_CASTLE);
            minMoves = Math.min(minMoves, distanceToBluecastle);
        }

        if (blueGuardPos >= 0) {
            int distanceToRedCastle = getManhattanDistance(blueGuardPos, RED_CASTLE);
            minMoves = Math.min(minMoves, distanceToRedCastle);
        }

        return minMoves == Integer.MAX_VALUE ? 20 : minMoves; // Default to 20 if no guards
    }

    private static int getManhattanDistance(int square1, int square2) {
        int rank1 = square1 / 7;
        int file1 = square1 % 7;
        int rank2 = square2 / 7;
        int file2 = square2 % 7;

        return Math.abs(rank1 - rank2) + Math.abs(file1 - file2);
    }

    // === DIAGNOSTIC METHODS ===

    /**
     * Get human-readable description of terminal type
     */
    public static String getTerminalDescription(TerminalType terminal) {
        switch (terminal) {
            case NOT_TERMINAL: return "Game continues";
            case GUARD_CAPTURED_RED: return "Red guard captured - Blue wins";
            case GUARD_CAPTURED_BLUE: return "Blue guard captured - Red wins";
            case CASTLE_REACHED_RED: return "Red guard reached blue castle - Red wins";
            case CASTLE_REACHED_BLUE: return "Blue guard reached red castle - Blue wins";
            case NO_MOVES_RED: return "Red has no legal moves - Blue wins";
            case NO_MOVES_BLUE: return "Blue has no legal moves - Red wins";
            case STALEMATE: return "Stalemate - Draw";
            default: return "Unknown terminal state";
        }
    }

    /**
     * Get detailed position analysis
     */
    public static String analyzePosition(GameState state) {
        TerminalType terminal = detectTerminal(state);
        StringBuilder analysis = new StringBuilder();

        analysis.append("Position Analysis:\n");
        analysis.append("- Terminal: ").append(getTerminalDescription(terminal)).append("\n");

        if (terminal == TerminalType.NOT_TERMINAL) {
            analysis.append("- Red guard in danger: ").append(isRedGuardInDanger(state)).append("\n");
            analysis.append("- Blue guard in danger: ").append(isBlueGuardInDanger(state)).append("\n");
            analysis.append("- Moves to terminal: ~").append(estimateMovesToTerminal(state)).append("\n");
            analysis.append("- Has forcing moves: ").append(hasForcingMoves(state)).append("\n");
        }

        return analysis.toString();
    }
}