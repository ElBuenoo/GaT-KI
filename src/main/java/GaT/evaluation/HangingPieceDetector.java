package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * HANGING PIECE DETECTOR - CORRECTED for Turm und Wächter
 *
 * Detects pieces that are:
 * 1. Attacked by opponent
 * 2. Not adequately defended
 * 3. Can be captured for free or at a loss
 *
 * FIXED to use correct API: bitmasks + redToMove pattern
 */
public class HangingPieceDetector {

    // Piece values for hanging piece calculations
    private static final int GUARD_VALUE = 2000;
    private static final int TOWER_VALUE = 100;

    // Penalty multipliers for hanging pieces
    private static final int HANGING_GUARD_PENALTY = 3000;  // Severe penalty
    private static final int HANGING_TOWER_PENALTY = 150;   // Moderate penalty
    private static final int UNDEFENDED_BONUS = 500;        // Bonus for attacking undefended pieces

    /**
     * Main entry point - evaluate hanging piece penalties for current position
     */
    public static int evaluateHangingPieces(GameState state) {
        int penalty = 0;

        try {
            // Check for red hanging pieces (penalty for red)
            penalty += findHangingPieces(state, true);

            // Check for blue hanging pieces (bonus for red)
            penalty -= findHangingPieces(state, false);

        } catch (Exception e) {
            System.err.println("❌ Error in hanging piece detection: " + e.getMessage());
            return 0; // Safe fallback
        }

        return penalty;
    }

    /**
     * Find hanging pieces for a specific color
     */
    private static int findHangingPieces(GameState state, boolean checkingRed) {
        int totalPenalty = 0;

        // Get all opponent moves to find what they're attacking
        GameState opponentState = state.copy();
        opponentState.redToMove = !checkingRed; // FIXED: use redToMove instead of currentPlayer

        List<Move> opponentMoves = MoveGenerator.generateAllMoves(opponentState);

        // Track which squares are under attack
        Set<Integer> attackedSquares = new HashSet<>();

        // Find all squares under attack by opponent
        for (Move move : opponentMoves) {
            if (isCapture(move, state)) {
                attackedSquares.add(move.to);
            }
        }

        // Check each attacked square to see if piece is hanging
        for (Integer square : attackedSquares) {
            if (hasPiece(state, square, checkingRed)) {
                int hangingPenalty = calculateHangingPenalty(state, square, checkingRed);
                totalPenalty += hangingPenalty;

                // Debug output for critical hanging pieces
                if (hangingPenalty > 1000) {
                    System.out.printf("⚠️  HANGING PIECE: %s piece at %s (penalty: %d)%n",
                            checkingRed ? "RED" : "BLUE",
                            GameState.squareToString(square),
                            hangingPenalty);
                }
            }
        }

        return totalPenalty;
    }

    /**
     * Calculate penalty for a specific hanging piece
     */
    private static int calculateHangingPenalty(GameState state, int square, boolean isRed) {
        // FIXED: Use correct API for checking piece types
        long squareBit = GameState.bit(square);

        // Check if it's a guard
        boolean isGuard = isRed ?
                (state.redGuard & squareBit) != 0 :
                (state.blueGuard & squareBit) != 0;

        // Get stack height
        int stackHeight = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];

        if (stackHeight == 0 && !isGuard) return 0; // No piece here

        // Check if piece is defended
        boolean isDefended = isPieceDefended(state, square, isRed);

        if (!isDefended) {
            // Completely undefended - full penalty
            if (isGuard) {
                return HANGING_GUARD_PENALTY;
            } else {
                return HANGING_TOWER_PENALTY * stackHeight;
            }
        }

        // Piece is defended - use SEE to check if it's still hanging
        int netLoss = calculateNetLossIfCaptured(state, square, isRed);

        if (netLoss > 0) {
            // Still loses material despite being defended
            return netLoss / 2; // Reduced penalty since it's defended
        }

        return 0; // Safe piece
    }

    /**
     * Check if a piece is defended by friendly pieces
     */
    private static boolean isPieceDefended(GameState state, int square, boolean isRed) {
        // Create a state where we're the defending player
        GameState defenderState = state.copy();
        defenderState.redToMove = isRed; // FIXED: use redToMove

        List<Move> defenderMoves = MoveGenerator.generateAllMoves(defenderState);

        // Check if any friendly piece can recapture on this square
        for (Move move : defenderMoves) {
            if (move.to == square && isCapture(move, state)) {
                return true; // Found a defender
            }
        }

        return false;
    }

    /**
     * Calculate net material loss if piece is captured using simplified SEE
     */
    private static int calculateNetLossIfCaptured(GameState state, int square, boolean isRed) {
        try {
            // Find the best opponent capture on this square
            GameState opponentState = state.copy();
            opponentState.redToMove = !isRed; // FIXED: use redToMove

            List<Move> opponentMoves = MoveGenerator.generateAllMoves(opponentState);

            Move bestCapture = null;
            int bestCaptureValue = -999999;

            for (Move move : opponentMoves) {
                if (move.to == square && isCapture(move, state)) {
                    int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                    if (seeValue > bestCaptureValue) {
                        bestCaptureValue = seeValue;
                        bestCapture = move;
                    }
                }
            }

            if (bestCapture != null && bestCaptureValue > 0) {
                return bestCaptureValue; // Material loss from best opponent capture
            }

        } catch (Exception e) {
            // Fallback calculation if SEE fails
            return calculateBasicHangingValue(state, square, isRed);
        }

        return 0; // No material loss
    }

    /**
     * Fallback calculation for hanging piece value
     */
    private static int calculateBasicHangingValue(GameState state, int square, boolean isRed) {
        long squareBit = GameState.bit(square);

        // FIXED: Use correct API for checking piece types
        boolean isGuard = isRed ?
                (state.redGuard & squareBit) != 0 :
                (state.blueGuard & squareBit) != 0;

        int stackHeight = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];

        if (isGuard) {
            return GUARD_VALUE;
        } else {
            return TOWER_VALUE * stackHeight;
        }
    }

    /**
     * Quick analysis for move evaluation
     */
    public static int getHangingPieceThreat(GameState state, Move move) {
        // Check if this move leaves any pieces hanging
        GameState afterMove = state.copy();
        afterMove.applyMove(move);

        return evaluateHangingPieces(afterMove);
    }

    /**
     * Emergency hanging piece scan - returns true if any critical pieces are hanging
     */
    public static boolean hasEmergencyHangingPieces(GameState state, boolean forRed) {
        try {
            GameState opponentState = state.copy();
            opponentState.redToMove = !forRed; // FIXED: use redToMove

            List<Move> opponentMoves = MoveGenerator.generateAllMoves(opponentState);

            for (Move move : opponentMoves) {
                if (isCapture(move, state)) {
                    long squareBit = GameState.bit(move.to);

                    // FIXED: Check if capturing a guard or valuable piece using correct API
                    boolean isGuard = forRed ?
                            (state.redGuard & squareBit) != 0 :
                            (state.blueGuard & squareBit) != 0;

                    int stackHeight = forRed ? state.redStackHeights[move.to] : state.blueStackHeights[move.to];

                    if (isGuard || stackHeight >= 3) {
                        // Check if it's defended
                        if (!isPieceDefended(state, move.to, forRed)) {
                            return true; // Emergency: valuable undefended piece
                        }
                    }
                }
            }

        } catch (Exception e) {
            return false; // Safe fallback
        }

        return false;
    }

    // === HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        // FIXED: Use correct API - check both guards and towers with bitmasks
        if (state.redToMove) {
            // Red to move - check if capturing blue piece
            return (state.blueGuard & toBit) != 0 || (state.blueTowers & toBit) != 0;
        } else {
            // Blue to move - check if capturing red piece
            return (state.redGuard & toBit) != 0 || (state.redTowers & toBit) != 0;
        }
    }

    private static boolean hasPiece(GameState state, int square, boolean isRed) {
        long squareBit = GameState.bit(square);

        // FIXED: Use correct API - check both guards and towers with bitmasks
        if (isRed) {
            return (state.redGuard & squareBit) != 0 || (state.redTowers & squareBit) != 0;
        } else {
            return (state.blueGuard & squareBit) != 0 || (state.blueTowers & squareBit) != 0;
        }
    }
}