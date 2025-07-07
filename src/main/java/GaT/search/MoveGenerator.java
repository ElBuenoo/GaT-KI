package GaT.search;

import GaT.model.GameRules;
import GaT.model.GameState;
import GaT.model.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * Move generator for Guards & Towers
 * FIXED VERSION - Always returns mutable ArrayList
 */
public class MoveGenerator {

    /**
     * Generate all legal moves for current position
     * IMPORTANT: Returns mutable ArrayList for sorting
     */
    public static List<Move> generateAllMoves(GameState state) {
        // CRITICAL: Use ArrayList, not List.of() or ImmutableList
        ArrayList<Move> moves = new ArrayList<>();

        boolean isRed = state.redToMove;

        // Generate moves for each piece
        for (int from = 0; from < 49; from++) {
            generateMovesFrom(state, from, isRed, moves);
        }

        return moves; // Return mutable ArrayList
    }

    /**
     * Generate moves from a specific square
     */
    private static void generateMovesFrom(GameState state, int from, boolean isRed, List<Move> moves) {
        // Get piece count at square
        int pieceCount = isRed ? state.redStackHeights[from] : state.blueStackHeights[from];

        if (pieceCount == 0) {
            return; // No pieces to move
        }

        // Check if it's a guard
        boolean isGuard = isRed ?
                (state.redGuard & (1L << from)) != 0 :
                (state.blueGuard & (1L << from)) != 0;

        // Generate moves for different amounts (1 to pieceCount)
        for (int amount = 1; amount <= pieceCount; amount++) {
            if (isGuard) {
                // Guard moves
                generateGuardMoves(state, from, amount, isRed, moves);
            } else {
                // Regular tower moves
                generateTowerMoves(state, from, amount, isRed, moves);
            }
        }
    }

    /**
     * Generate guard moves
     */
    private static void generateGuardMoves(GameState state, int from, int amount, boolean isRed, List<Move> moves) {
        int row = from / 7;
        int col = from % 7;

        // Guards can move to any adjacent square (8 directions)
        int[] dRow = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dCol = {-1, 0, 1, -1, 1, -1, 0, 1};

        for (int i = 0; i < 8; i++) {
            int newRow = row + dRow[i];
            int newCol = col + dCol[i];

            if (isValidSquare(newRow, newCol)) {
                int to = newRow * 7 + newCol;

                // Check if move is legal
                if (isLegalMove(state, from, to, amount, isRed)) {
                    moves.add(new Move(from, to, amount));
                }
            }
        }
    }

    /**
     * Generate tower moves
     */
    private static void generateTowerMoves(GameState state, int from, int amount, boolean isRed, List<Move> moves) {
        int row = from / 7;
        int col = from % 7;

        // Towers move in straight lines (4 directions)
        int[] dRow = {-1, 0, 0, 1};
        int[] dCol = {0, -1, 1, 0};

        for (int dir = 0; dir < 4; dir++) {
            // Try each distance
            for (int dist = 1; dist < 7; dist++) {
                int newRow = row + dRow[dir] * dist;
                int newCol = col + dCol[dir] * dist;

                if (!isValidSquare(newRow, newCol)) {
                    break; // Out of bounds
                }

                int to = newRow * 7 + newCol;

                // Check if square is occupied
                if (state.redStackHeights[to] > 0 || state.blueStackHeights[to] > 0) {
                    // Can capture if enemy piece
                    if (isEnemyPiece(state, to, isRed) && isLegalMove(state, from, to, amount, isRed)) {
                        moves.add(new Move(from, to, amount));
                    }
                    break; // Can't jump over pieces
                } else {
                    // Empty square - can move here
                    if (isLegalMove(state, from, to, amount, isRed)) {
                        moves.add(new Move(from, to, amount));
                    }
                }
            }
        }
    }

    /**
     * Check if square is valid
     */
    private static boolean isValidSquare(int row, int col) {
        return row >= 0 && row < 7 && col >= 0 && col < 7;
    }

    /**
     * Check if square contains enemy piece
     */
    private static boolean isEnemyPiece(GameState state, int square, boolean isRed) {
        if (isRed) {
            return state.blueStackHeights[square] > 0;
        } else {
            return state.redStackHeights[square] > 0;
        }
    }

    /**
     * Check if move is legal according to game rules
     */
    private static boolean isLegalMove(GameState state, int from, int to, int amount, boolean isRed) {
        // Basic validation
        if (from == to) return false;
        if (amount <= 0) return false;

        // Check piece ownership
        int pieceCount = isRed ? state.redStackHeights[from] : state.blueStackHeights[from];
        if (amount > pieceCount) return false;

        // Check target square
        if (isRed && state.redStackHeights[to] > 0) {
            return false; // Can't capture own pieces
        }
        if (!isRed && state.blueStackHeights[to] > 0) {
            return false; // Can't capture own pieces
        }

        // Additional validation can be added here
        return true;
    }

    /**
     * Generate only capture moves (for quiescence search)
     * IMPORTANT: Returns mutable ArrayList
     */
    public static List<Move> generateCaptures(GameState state) {
        ArrayList<Move> captures = new ArrayList<>();
        List<Move> allMoves = generateAllMoves(state);

        for (Move move : allMoves) {
            if (GameRules.isCapture(move, state)) {
                captures.add(move);
            }
        }

        return captures;
    }

    /**
     * Count legal moves (for mobility evaluation)
     */
    public static int countLegalMoves(GameState state) {
        return generateAllMoves(state).size();
    }

    /**
     * Check if any legal moves exist
     */
    public static boolean hasLegalMoves(GameState state) {
        // Optimized version that returns early
        boolean isRed = state.redToMove;

        for (int from = 0; from < 49; from++) {
            int pieceCount = isRed ? state.redStackHeights[from] : state.blueStackHeights[from];

            if (pieceCount > 0) {
                // Quick check for at least one move
                List<Move> tempMoves = new ArrayList<>();
                generateMovesFrom(state, from, isRed, tempMoves);

                if (!tempMoves.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }
}