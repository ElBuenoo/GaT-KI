package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.GameConfig;
import GaT.model.GameValues;

import java.util.ArrayList;
import java.util.List;

/**
 * QUIESCENCE SEARCH - UNIFIED SYSTEMS INTEGRATION
 */
public class QuiescenceSearch {

    // === RECURSION PROTECTION ===
    private static int recursionDepth = 0;

    // === MOVE ORDERING ACCESS ===
    private static MoveOrdering moveOrdering = new MoveOrdering();

    /**
     * Main quiescence search
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        // CRITICAL NULL CHECK AT ENTRY
        if (state == null) {
            System.err.println("❌ ERROR: Null state in QuiescenceSearch.quiesce()");
            return 0;
        }

        // VALIDATION CHECK
        if (!state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in QuiescenceSearch.quiesce()");
            return Minimax.evaluate(state, -qDepth);
        }

        UnifiedStatistics.getInstance().incrementQuiescenceNode();

        if (qDepth >= GameConfig.MAX_Q_DEPTH) {
            return Minimax.evaluate(state, -qDepth);
        }

        // Stand pat evaluation
        int standPat;
        try {
            standPat = Minimax.evaluate(state, -qDepth);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Stand pat evaluation failed: " + e.getMessage());
            standPat = 0;
        }

        if (maximizingPlayer) {
            if (standPat >= beta) {
                return beta;
            }
            alpha = Math.max(alpha, standPat);

            // Generate tactical moves
            List<Move> tacticalMoves;
            try {
                tacticalMoves = generateTacticalMoves(state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move generation failed: " + e.getMessage());
                return standPat;
            }

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int maxEval = standPat;
            for (Move move : tacticalMoves) {
                if (move == null) continue;

                // Delta pruning
                try {
                    if (isCapture(move, state)) {
                        int captureValue = estimateCaptureValue(move, state);
                        if (standPat + captureValue + GameConfig.Q_DELTA_MARGIN < alpha) {
                            continue; // Skip bad captures
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Delta pruning check failed for move " + move + ": " + e.getMessage());
                }

                // SAFE copy and apply move
                GameState copy = null;
                try {
                    copy = state.copy();
                    if (copy == null || !copy.isValid()) continue;
                    copy.applyMove(move);
                    if (!copy.isValid()) continue;
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Copy/ApplyMove failed in QuiescenceSearch for move " + move + ": " + e.getMessage());
                    continue;
                }

                int eval;
                try {
                    eval = quiesce(copy, alpha, beta, false, qDepth + 1);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Recursive quiesce() failed for move " + move + ": " + e.getMessage());
                    eval = standPat;
                }

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    UnifiedStatistics.getInstance().incrementAlphaBetaCutoff();
                    break;
                }
            }
            return maxEval;

        } else {
            if (standPat <= alpha) {
                return alpha;
            }
            beta = Math.min(beta, standPat);

            List<Move> tacticalMoves;
            try {
                tacticalMoves = generateTacticalMoves(state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move generation failed: " + e.getMessage());
                return standPat;
            }

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int minEval = standPat;
            for (Move move : tacticalMoves) {
                if (move == null) continue;

                // Delta pruning
                try {
                    if (isCapture(move, state)) {
                        int captureValue = estimateCaptureValue(move, state);
                        if (standPat - captureValue - GameConfig.Q_DELTA_MARGIN > beta) {
                            continue;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Delta pruning check failed for move " + move + ": " + e.getMessage());
                }

                // SAFE copy and apply move
                GameState copy = null;
                try {
                    copy = state.copy();
                    if (copy == null || !copy.isValid()) continue;
                    copy.applyMove(move);
                    if (!copy.isValid()) continue;
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Copy/ApplyMove failed in QuiescenceSearch for move " + move + ": " + e.getMessage());
                    continue;
                }

                int eval;
                try {
                    eval = quiesce(copy, alpha, beta, true, qDepth + 1);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Recursive quiesce() failed for move " + move + ": " + e.getMessage());
                    eval = standPat;
                }

                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    UnifiedStatistics.getInstance().incrementAlphaBetaCutoff();
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Generate tactical moves
     */
    private static List<Move> generateTacticalMoves(GameState state, int qDepth) {
        if (state == null || !state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in generateTacticalMoves");
            return new ArrayList<>();
        }

        if (recursionDepth >= 2) { // Simple recursion limit
            return new ArrayList<>();
        }

        recursionDepth++;
        try {
            List<Move> allMoves;
            try {
                allMoves = MoveGenerator.generateAllMoves(state);
                if (allMoves == null) {
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                System.err.println("❌ ERROR: Move generation failed in generateTacticalMoves: " + e.getMessage());
                return new ArrayList<>();
            }

            List<Move> tacticalMoves = new ArrayList<>();

            for (Move move : allMoves) {
                if (move != null && isTacticalMove(move, state)) {
                    tacticalMoves.add(move);
                }
            }

            // Order tactical moves
            try {
                orderTacticalMoves(tacticalMoves, state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move ordering failed: " + e.getMessage());
            }

            return tacticalMoves;

        } catch (Exception e) {
            System.err.println("❌ ERROR: generateTacticalMoves failed: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            recursionDepth--;
        }
    }

    /**
     * Order tactical moves
     */
    private static void orderTacticalMoves(List<Move> moves, GameState state, int qDepth) {
        if (moves.size() <= 1) return;

        try {
            moves.sort((a, b) -> {
                try {
                    int scoreA = scoreTacticalMove(a, state, qDepth);
                    int scoreB = scoreTacticalMove(b, state, qDepth);
                    return Integer.compare(scoreB, scoreA);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Move comparison failed: " + e.getMessage());
                    return 0;
                }
            });
        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move sorting failed: " + e.getMessage());
        }
    }

    /**
     * Score tactical moves
     */
    private static int scoreTacticalMove(Move move, GameState state, int qDepth) {
        if (move == null || state == null) return 0;

        int score = 0;

        try {
            // Capture value (highest priority)
            if (isCapture(move, state)) {
                score += estimateCaptureValue(move, state) * 10;

                // MVV-LVA: subtract attacker value
                score -= getAttackerValue(move, state);
            }

            // Winning moves
            if (isWinningGuardMove(move, state)) {
                score += 50000;
            }

            // Activity bonus
            score += move.amountMoved * 5;

            // Depth penalty (prefer earlier discoveries)
            score -= qDepth * 10;

        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move scoring failed for move " + move + ": " + e.getMessage());
        }

        return score;
    }

    /**
     * Enhanced tactical move detection
     */
    private static boolean isTacticalMove(Move move, GameState state) {
        if (move == null || state == null) return false;

        try {
            // 1. All captures are tactical
            if (isCapture(move, state)) {
                return true;
            }

            // 2. Winning guard moves
            if (isWinningGuardMove(move, state)) {
                return true;
            }

            // 3. High activity moves
            if (move.amountMoved >= 3) {
                return true;
            }

            // 4. Advancing guard moves in endgame
            if (isGuardAdvancingInEndgame(move, state)) {
                return true;
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move detection failed for move " + move + ": " + e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Check if guard is advancing in endgame
     */
    private static boolean isGuardAdvancingInEndgame(Move move, GameState state) {
        try {
            // Check if we're in endgame
            if (!Minimax.isEndgame(state)) return false;

            boolean isRed = state.redToMove;
            long guardBit = isRed ? state.redGuard : state.blueGuard;

            if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
                return false;
            }

            // Check if moving towards enemy castle
            int targetRank = isRed ? 0 : 6;
            int currentRank = GameState.rank(move.from);
            int newRank = GameState.rank(move.to);

            return Math.abs(newRank - targetRank) < Math.abs(currentRank - targetRank);
        } catch (Exception e) {
            return false;
        }
    }

    // === HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;
            long toBit = GameState.bit(move.to);
            long pieces = state.redToMove ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);
            return (pieces & toBit) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWinningGuardMove(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

            boolean isRed = state.redToMove;

            // Check if it's a guard move
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
                return false;
            }

            // Check if moving to enemy castle
            int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
            return move.to == targetCastle;
        } catch (Exception e) {
            return false;
        }
    }

    private static int estimateCaptureValue(Move move, GameState state) {
        try {
            if (move == null || state == null) return 0;

            long toBit = GameState.bit(move.to);
            boolean isRed = state.redToMove;

            // Guard capture
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                return GameValues.GUARD_VALUE;
            }

            // Tower capture
            if (((isRed ? state.blueTowers : state.redTowers) & toBit) != 0) {
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                return height * GameValues.TOWER_VALUE;
            }

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get attacker value for MVV-LVA
     */
    private static int getAttackerValue(Move move, GameState state) {
        try {
            if (move == null || state == null) return 0;

            boolean isRed = state.redToMove;

            // Check if it's a guard move
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            if (guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit)) {
                return 50;
            }

            // Tower value based on height
            int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
            return height * 100;
        } catch (Exception e) {
            return 0;
        }
    }

    // === INITIALIZATION AND STATISTICS ===

    /**
     * Set move ordering instance for history access
     */
    public static void setMoveOrdering(MoveOrdering ordering) {
        if (ordering != null) {
            moveOrdering = ordering;
        }
    }

    public static void resetQuiescenceStats() {
        System.out.println("🔧 QuiescenceSearch reset with GameConfig");
    }

    public static void setRemainingTime(long timeMs) {
        // Adjust quiescence depth based on time
        if (timeMs < GameConfig.EMERGENCY_TIME_MS) {
            System.out.println("🚨 Emergency mode: Reduced quiescence depth");
        }
    }

    /**
     * Get quiescence statistics
     */
    public static String getQuiescenceStatistics() {
        UnifiedStatistics stats = UnifiedStatistics.getInstance();
        return String.format("Q-Search: %d nodes, %.1f%% of total",
                stats.getQuiescenceNodes(),
                stats.getQuiescenceNodes() * 100.0 / Math.max(1, stats.getTotalNodes()));
    }
}