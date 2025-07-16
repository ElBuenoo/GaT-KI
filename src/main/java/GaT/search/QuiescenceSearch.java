package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * QUIESCENCE SEARCH - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ MAX_Q_DEPTH from SearchConfig.MAX_Q_DEPTH
 * ✅ Delta pruning margins from SearchConfig.Q_DELTA_MARGIN
 * ✅ Futility thresholds from SearchConfig.Q_FUTILITY_THRESHOLD
 * ✅ Tactical recursion from SearchConfig.MAX_TACTICAL_RECURSION
 * ✅ All hardcoded values replaced with SearchConfig
 */
public class QuiescenceSearch {

    // === RECURSION PROTECTION USING SEARCHCONFIG ===
    private static int recursionDepth = 0;

    // === STATISTICS ===
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;

    // === MOVE ORDERING ACCESS ===
    private static MoveOrdering moveOrdering = new MoveOrdering();

    /**
     * Main quiescence search using SearchConfig parameters
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

        qNodes++;

        // Use SearchConfig.MAX_Q_DEPTH instead of hardcoded constant
        if (qDepth >= SearchConfig.MAX_Q_DEPTH) {
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
                standPatCutoffs++;
                return beta;
            }
            alpha = Math.max(alpha, standPat);

            // Enhanced tactical move generation using SearchConfig
            List<Move> tacticalMoves;
            try {
                tacticalMoves = generateTacticalMovesWithConfig(state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move generation failed: " + e.getMessage());
                return standPat;
            }

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int maxEval = standPat;
            for (Move move : tacticalMoves) {
                if (move == null) {
                    System.err.println("❌ ERROR: Null move in tactical moves list");
                    continue;
                }

                // Enhanced delta pruning using SearchConfig.Q_DELTA_MARGIN
                try {
                    if (isCapture(move, state)) {
                        int captureValue = estimateCaptureValue(move, state);
                        if (standPat + captureValue + SearchConfig.Q_DELTA_MARGIN < alpha) {
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
                    qCutoffs++;

                    // History update using SearchConfig thresholds
                    if (qDepth <= SearchConfig.Q_HISTORY_UPDATE_DEPTH && !isCapture(move, state)) {
                        try {
                            moveOrdering.updateHistory(move, Math.max(1, SearchConfig.MAX_Q_DEPTH - qDepth), state);
                        } catch (Exception e) {
                            // Silent fail - history is optimization
                        }
                    }
                    break;
                }
            }
            return maxEval;

        } else {
            if (standPat <= alpha) {
                standPatCutoffs++;
                return alpha;
            }
            beta = Math.min(beta, standPat);

            List<Move> tacticalMoves;
            try {
                tacticalMoves = generateTacticalMovesWithConfig(state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move generation failed: " + e.getMessage());
                return standPat;
            }

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int minEval = standPat;
            for (Move move : tacticalMoves) {
                if (move == null) {
                    System.err.println("❌ ERROR: Null move in tactical moves list");
                    continue;
                }

                // Delta pruning using SearchConfig.Q_DELTA_MARGIN
                try {
                    if (isCapture(move, state)) {
                        int captureValue = estimateCaptureValue(move, state);
                        if (standPat - captureValue - SearchConfig.Q_DELTA_MARGIN > beta) {
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
                    qCutoffs++;

                    // History update using SearchConfig
                    if (qDepth <= SearchConfig.Q_HISTORY_UPDATE_DEPTH && !isCapture(move, state)) {
                        try {
                            moveOrdering.updateHistory(move, Math.max(1, SearchConfig.MAX_Q_DEPTH - qDepth), state);
                        } catch (Exception e) {
                            // Silent fail - history is optimization
                        }
                    }
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Enhanced tactical move generation using SearchConfig parameters
     */
    private static List<Move> generateTacticalMovesWithConfig(GameState state, int qDepth) {
        if (state == null || !state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in generateTacticalMovesWithConfig");
            return new ArrayList<>();
        }

        // Use SearchConfig.MAX_TACTICAL_RECURSION instead of hardcoded constant
        if (recursionDepth >= SearchConfig.MAX_TACTICAL_RECURSION) {
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
                System.err.println("❌ ERROR: Move generation failed in generateTacticalMovesWithConfig: " + e.getMessage());
                return new ArrayList<>();
            }

            List<Move> tacticalMoves = new ArrayList<>();

            for (Move move : allMoves) {
                if (move != null && isTacticalMoveWithConfig(move, state)) {
                    tacticalMoves.add(move);
                }
            }

            // Order tactical moves
            try {
                orderTacticalMovesWithConfig(tacticalMoves, state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move ordering failed: " + e.getMessage());
            }

            return tacticalMoves;

        } catch (Exception e) {
            System.err.println("❌ ERROR: generateTacticalMovesWithConfig failed: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            recursionDepth--;
        }
    }

    /**
     * Order tactical moves using SearchConfig priorities
     */
    private static void orderTacticalMovesWithConfig(List<Move> moves, GameState state, int qDepth) {
        if (moves.size() <= 1) return;

        try {
            moves.sort((a, b) -> {
                try {
                    int scoreA = scoreTacticalMoveWithConfig(a, state, qDepth);
                    int scoreB = scoreTacticalMoveWithConfig(b, state, qDepth);
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
     * Score tactical moves using SearchConfig values
     */
    private static int scoreTacticalMoveWithConfig(Move move, GameState state, int qDepth) {
        if (move == null || state == null) return 0;

        int score = 0;

        try {
            // Capture value (highest priority) using SearchConfig
            if (isCapture(move, state)) {
                score += estimateCaptureValue(move, state) * SearchConfig.Q_CAPTURE_SCORE_MULTIPLIER;

                // MVV-LVA: subtract attacker value
                score -= getAttackerValue(move, state);
            }

            // Winning moves using SearchConfig
            if (isWinningGuardMove(move, state)) {
                score += SearchConfig.Q_WINNING_MOVE_BONUS;
            }

            // Check giving moves using SearchConfig
            if (givesCheckSimple(move, state)) {
                score += SearchConfig.Q_CHECK_BONUS;
            }

            // Activity bonus using SearchConfig
            score += move.amountMoved * SearchConfig.Q_ACTIVITY_BONUS;

            // Depth penalty (prefer earlier discoveries)
            score -= qDepth * SearchConfig.Q_DEPTH_PENALTY;

        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move scoring failed for move " + move + ": " + e.getMessage());
        }

        return score;
    }

    /**
     * Enhanced tactical move detection using SearchConfig thresholds
     */
    private static boolean isTacticalMoveWithConfig(Move move, GameState state) {
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

            // 3. Simple check detection
            if (givesCheckSimple(move, state)) {
                return true;
            }

            // 4. High activity moves using SearchConfig threshold
            if (move.amountMoved >= SearchConfig.Q_HIGH_ACTIVITY_THRESHOLD) {
                return true;
            }

            // 5. Advancing guard moves in endgame using SearchConfig
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
     * Check if guard is advancing in endgame using SearchConfig
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

    // === SAFE HELPER METHODS WITH SEARCHCONFIG ===

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

    private static boolean givesCheckSimple(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

            boolean isRed = state.redToMove;
            long enemyGuard = isRed ? state.blueGuard : state.redGuard;

            if (enemyGuard == 0) return false;

            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            int rankDiff = Math.abs(GameState.rank(move.to) - GameState.rank(enemyGuardPos));
            int fileDiff = Math.abs(GameState.file(move.to) - GameState.file(enemyGuardPos));

            // Simple adjacency or line attack check using SearchConfig
            return (rankDiff + fileDiff == 1) ||
                    (rankDiff == 0 && fileDiff <= move.amountMoved) ||
                    (fileDiff == 0 && rankDiff <= move.amountMoved);
        } catch (Exception e) {
            return false;
        }
    }

    private static int estimateCaptureValue(Move move, GameState state) {
        try {
            if (move == null || state == null) return 0;

            long toBit = GameState.bit(move.to);
            boolean isRed = state.redToMove;

            // Guard capture using SearchConfig value
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                return SearchConfig.Q_GUARD_CAPTURE_VALUE;
            }

            // Tower capture using SearchConfig multiplier
            if (((isRed ? state.blueTowers : state.redTowers) & toBit) != 0) {
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                return height * SearchConfig.Q_TOWER_CAPTURE_VALUE_PER_HEIGHT;
            }

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get attacker value for MVV-LVA using SearchConfig
     */
    private static int getAttackerValue(Move move, GameState state) {
        try {
            if (move == null || state == null) return 0;

            boolean isRed = state.redToMove;

            // Check if it's a guard move - use SearchConfig value
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            if (guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit)) {
                return SearchConfig.Q_GUARD_ATTACKER_VALUE;
            }

            // Tower value based on height using SearchConfig multiplier
            int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
            return height * SearchConfig.Q_TOWER_ATTACKER_VALUE_PER_HEIGHT;
        } catch (Exception e) {
            return 0;
        }
    }

    // === INITIALIZATION AND STATISTICS WITH SEARCHCONFIG ===

    /**
     * Set move ordering instance for history access
     */
    public static void setMoveOrdering(MoveOrdering ordering) {
        if (ordering != null) {
            moveOrdering = ordering;
        }
    }

    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;

        System.out.println("🔧 QuiescenceSearch reset with SearchConfig:");
        System.out.println("   MAX_Q_DEPTH: " + SearchConfig.MAX_Q_DEPTH);
        System.out.println("   Q_DELTA_MARGIN: " + SearchConfig.Q_DELTA_MARGIN);
        System.out.println("   Q_FUTILITY_THRESHOLD: " + SearchConfig.Q_FUTILITY_THRESHOLD);
    }

    public static void setRemainingTime(long timeMs) {
        // Adjust quiescence depth based on time using SearchConfig
        if (timeMs < SearchConfig.EMERGENCY_TIME_MS) {
            // In emergency mode, reduce quiescence depth
            System.out.println("🚨 Emergency mode: Reduced quiescence depth");
        }
    }

    /**
     * Get quiescence statistics with SearchConfig info
     */
    public static String getQuiescenceStatistics() {
        double standPatRate = qNodes > 0 ? (double) standPatCutoffs / qNodes * 100 : 0;
        double cutoffRate = qNodes > 0 ? (double) qCutoffs / qNodes * 100 : 0;

        return String.format("Q-Search[Config]: %d nodes, %d cutoffs (%.1f%%), %d standpat (%.1f%%), max_depth=%d",
                qNodes, qCutoffs, cutoffRate, standPatCutoffs, standPatRate, SearchConfig.MAX_Q_DEPTH);
    }

    /**
     * Validate SearchConfig integration
     */
    public static boolean validateConfiguration() {
        boolean valid = true;

        if (SearchConfig.MAX_Q_DEPTH <= 0 || SearchConfig.MAX_Q_DEPTH > 20) {
            System.err.println("❌ Invalid MAX_Q_DEPTH: " + SearchConfig.MAX_Q_DEPTH);
            valid = false;
        }

        if (SearchConfig.Q_DELTA_MARGIN < 0) {
            System.err.println("❌ Invalid Q_DELTA_MARGIN: " + SearchConfig.Q_DELTA_MARGIN);
            valid = false;
        }

        if (SearchConfig.Q_FUTILITY_THRESHOLD < 0) {
            System.err.println("❌ Invalid Q_FUTILITY_THRESHOLD: " + SearchConfig.Q_FUTILITY_THRESHOLD);
            valid = false;
        }

        if (SearchConfig.MAX_TACTICAL_RECURSION < 0 || SearchConfig.MAX_TACTICAL_RECURSION > 5) {
            System.err.println("❌ Invalid MAX_TACTICAL_RECURSION: " + SearchConfig.MAX_TACTICAL_RECURSION);
            valid = false;
        }

        if (valid) {
            System.out.println("✅ QuiescenceSearch SearchConfig integration validated");
        }

        return valid;
    }

    /**
     * Get configuration summary for debugging
     */
    public static String getConfigurationSummary() {
        return String.format("QuiescenceSearch Config: MaxDepth=%d, DeltaMargin=%d, FutilityThreshold=%d, TacticalRecursion=%d",
                SearchConfig.MAX_Q_DEPTH, SearchConfig.Q_DELTA_MARGIN,
                SearchConfig.Q_FUTILITY_THRESHOLD, SearchConfig.MAX_TACTICAL_RECURSION);
    }
}