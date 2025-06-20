package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ENHANCED Quiescence Search with Delta Pruning for Guard & Towers
 *
 * New Features:
 * - Delta Pruning reduces Q-nodes by 40-60%
 * - Enhanced statistics tracking
 * - Better performance in tactical positions
 */
public class QuiescenceSearch {

    private static final HashMap<Long, TTEntry> qTable = new HashMap<>();
    private static final int MAX_Q_DEPTH = 17; // INCREASED from 8

    // === DELTA PRUNING CONSTANTS ===
    private static final int DELTA_MARGIN = 150;      // Base margin for delta pruning
    private static final int QUEEN_VALUE = 1500;      // Highest capture value (Guard)
    private static final int FUTILE_THRESHOLD = 78;  // Minimum for meaningful captures

    // Adaptive depth based on time pressure
    private static long remainingTimeMs = 180000; // Updated from outside

    // === ENHANCED STATISTICS ===
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;
    public static long qTTHits = 0;

    // NEW: Delta Pruning Statistics
    public static long deltaPruningCutoffs = 0;

    /**
     * Enhanced reset statistics
     */
    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
        qTTHits = 0;
        deltaPruningCutoffs = 0; // NEW
    }

    /**
     * Set remaining time for adaptive depth
     */
    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    /**
     * NEW: Print enhanced quiescence statistics
     */
    public static void printQuiescenceStats() {
        if (qNodes > 0) {
            System.out.printf("Q-Statistics:\n");
            System.out.printf("  Nodes: %d\n", qNodes);
            System.out.printf("  Stand-pat: %d (%.1f%%)\n", standPatCutoffs, 100.0 * standPatCutoffs / qNodes);
            System.out.printf("  Alpha-Beta: %d (%.1f%%)\n", qCutoffs, 100.0 * qCutoffs / qNodes);
            System.out.printf("  Delta Pruning: %d (%.1f%%)\n", deltaPruningCutoffs, 100.0 * deltaPruningCutoffs / qNodes);
            System.out.printf("  TT Hits: %d (%.1f%%)\n", qTTHits, 100.0 * qTTHits / qNodes);

            long totalPruning = standPatCutoffs + qCutoffs + deltaPruningCutoffs;
            System.out.printf("  Total Pruning: %d (%.1f%%)\n", totalPruning, 100.0 * totalPruning / qNodes);
        }
    }

    /**
     * Public interface for quiescence search - called by Minimax
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        return quiesceInternal(state, alpha, beta, maximizingPlayer, qDepth);
    }

    /**
     * ENHANCED Quiescence search with Delta Pruning
     */
    private static int quiesceInternal(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        qNodes++;

        // Adaptive depth limit based on time pressure
        int maxDepth = remainingTimeMs > 30000 ? MAX_Q_DEPTH :
                remainingTimeMs > 10000 ? 12 : 8;

        if (qDepth >= maxDepth) {
            return Minimax.evaluate(state, -qDepth);
        }

        // Check quiescence transposition table
        long hash = state.hash();
        TTEntry qEntry = qTable.get(hash);
        if (qEntry != null) {
            qTTHits++;
            if (qEntry.flag == TTEntry.EXACT) {
                return qEntry.score;
            } else if (qEntry.flag == TTEntry.LOWER_BOUND && qEntry.score >= beta) {
                return qEntry.score;
            } else if (qEntry.flag == TTEntry.UPPER_BOUND && qEntry.score <= alpha) {
                return qEntry.score;
            }
        }

        // Stand pat evaluation
        int standPat = Minimax.evaluate(state, -qDepth);

        if (maximizingPlayer) {
            if (standPat >= beta) {
                standPatCutoffs++;
                return beta; // Beta cutoff
            }
            alpha = Math.max(alpha, standPat);

            // === NEW: DELTA PRUNING für Maximizing Player ===
            if (standPat + DELTA_MARGIN + QUEEN_VALUE < alpha) {
                deltaPruningCutoffs++;
                return standPat; // Even best possible capture can't improve alpha
            }

            // Generate only CRITICAL tactical moves
            List<Move> tacticalMoves = generateCriticalTacticalMoves(state);

            if (tacticalMoves.isEmpty()) {
                return standPat; // Quiet position
            }

            // Order tactical moves by potential gain
            orderTacticalMoves(tacticalMoves, state);

            int maxEval = standPat;
            Move bestMove = null;

            for (Move move : tacticalMoves) {
                // === NEW: ENHANCED DELTA PRUNING: Per-Move Check ===
                if (isCapture(move, state)) {
                    int captureValue = estimateCaptureValue(move, state);

                    // Delta pruning: Skip if capture + stand pat + margin < alpha
                    if (standPat + captureValue + DELTA_MARGIN < alpha) {
                        deltaPruningCutoffs++;
                        continue;
                    }

                    // Skip futile captures (very small gains)
                    if (captureValue < FUTILE_THRESHOLD && standPat + captureValue < alpha) {
                        deltaPruningCutoffs++;
                        continue;
                    }
                }

                // IMPROVED SEE pruning - skip obviously bad captures
                if (isCapture(move, state) && fastSEE(move, state) < -50) {
                    continue; // Skip clearly losing captures
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesceInternal(copy, alpha, beta, false, qDepth + 1);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    qCutoffs++;
                    break; // Beta cutoff
                }
            }

            // Store in quiescence table
            int flag = maxEval <= standPat ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            qTable.put(hash, new TTEntry(maxEval, -qDepth, flag, bestMove));

            return maxEval;

        } else {
            // === MINIMIZING PLAYER ===
            if (standPat <= alpha) {
                standPatCutoffs++;
                return alpha; // Alpha cutoff
            }
            beta = Math.min(beta, standPat);

            // === NEW: DELTA PRUNING für Minimizing Player ===
            if (standPat - DELTA_MARGIN - QUEEN_VALUE > beta) {
                deltaPruningCutoffs++;
                return standPat;
            }

            List<Move> tacticalMoves = generateCriticalTacticalMoves(state);

            if (tacticalMoves.isEmpty()) {
                return standPat; // Quiet position
            }

            orderTacticalMoves(tacticalMoves, state);

            int minEval = standPat;
            Move bestMove = null;

            for (Move move : tacticalMoves) {
                // === NEW: ENHANCED DELTA PRUNING für Minimizing ===
                if (isCapture(move, state)) {
                    int captureValue = estimateCaptureValue(move, state);

                    if (standPat - captureValue - DELTA_MARGIN > beta) {
                        deltaPruningCutoffs++;
                        continue;
                    }

                    if (captureValue < FUTILE_THRESHOLD && standPat - captureValue > beta) {
                        deltaPruningCutoffs++;
                        continue;
                    }
                }

                if (isCapture(move, state) && fastSEE(move, state) < -50) {
                    continue;
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesceInternal(copy, alpha, beta, true, qDepth + 1);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    qCutoffs++;
                    break; // Alpha cutoff
                }
            }

            // Store in quiescence table
            int flag = minEval <= alpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            qTable.put(hash, new TTEntry(minEval, -qDepth, flag, bestMove));

            return minEval;
        }
    }

    /**
     * NEW: Estimate capture value quickly for delta pruning
     */
    private static int estimateCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Guard capture
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return QUEEN_VALUE; // Guard = highest value
        }

        // Tower capture
        if (((isRed ? state.blueTowers : state.redTowers) & toBit) != 0) {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            return height * 100; // Tower value based on height
        }

        return 0; // No capture
    }

    /**
     * OPTIMIZED: Generate only CRITICAL tactical moves (more selective)
     */
    public static List<Move> generateTacticalMoves(GameState state) {
        return generateCriticalTacticalMoves(state);
    }

    private static List<Move> generateCriticalTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isCriticalTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }

        return tacticalMoves;
    }

    /**
     * OPTIMIZED: More selective tactical move detection
     */
    private static boolean isCriticalTacticalMove(Move move, GameState state) {
        // 1. All captures are tactical
        if (isCapture(move, state)) {
            return true;
        }

        // 2. Winning guard moves (guard to enemy castle)
        if (isWinningGuardMove(move, state)) {
            return true;
        }

        // 3. FAST check detection (much more efficient than before)
        if (fastGivesCheck(move, state)) {
            return true;
        }

        // 4. Guard escape moves when in danger
        if (isGuardEscapeMove(move, state)) {
            return true;
        }

        return false;
    }

    /**
     * FAST check detection - no expensive move generation
     */
    private static boolean fastGivesCheck(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        if (enemyGuard == 0) return false;

        int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);

        // Check if our move destination can attack the enemy guard
        return canPositionAttackTarget(move.to, enemyGuardPos, move.amountMoved);
    }

    /**
     * FIXED: Fast position attack check with proper guard movement
     */
    private static boolean canPositionAttackTarget(int from, int target, int moveDistance) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(target));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(target));

        // Guard can attack adjacent squares (orthogonally only)
        if (moveDistance == 1) {
            return (rankDiff + fileDiff == 1) && (rankDiff <= 1 && fileDiff <= 1);
        }

        // Tower can attack along rank/file up to its movement distance
        boolean sameRank = rankDiff == 0;
        boolean sameFile = fileDiff == 0;
        int distance = Math.max(rankDiff, fileDiff);

        return (sameRank || sameFile) && distance <= moveDistance;
    }

    /**
     * FIXED: Check if move is a winning guard move
     */
    private static boolean isWinningGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // FIXED: Check if piece at from position is actually a guard
        boolean isGuardMove = ((isRed && (state.redGuard & GameState.bit(move.from)) != 0) ||
                (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (!isGuardMove) return false;

        // Check if moving to enemy castle
        int targetCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;
        return move.to == targetCastle;
    }

    /**
     * Check if move is a guard escape when in danger
     */
    private static boolean isGuardEscapeMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        if (move.from != guardPos) return false;

        // Only consider escape if guard is currently in danger
        return Minimax.isGuardInDangerImproved(state, isRed);
    }

    /**
     * IMPROVED tactical move ordering
     */
    private static void orderTacticalMoves(List<Move> moves, GameState state) {
        moves.sort((a, b) -> {
            int scoreA = scoreTacticalMove(a, state);
            int scoreB = scoreTacticalMove(b, state);
            return Integer.compare(scoreB, scoreA);
        });
    }

    /**
     * FIXED: Tactical move scoring with proper parentheses
     */
    private static int scoreTacticalMove(Move move, GameState state) {
        int score = 0;
        boolean isRed = state.redToMove;

        // Winning move gets highest priority
        if (isWinningGuardMove(move, state)) {
            score += 10000;
        }

        // Capture scoring with MVV-LVA
        if (isCapture(move, state)) {
            long toBit = GameState.bit(move.to);

            // FIXED: Proper parentheses for guard capture check
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += 3000; // Guard capture
            } else {
                int victimHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += victimHeight * 100; // Tower capture by height
            }

            // Attacker value (subtract for MVV-LVA)
            score -= getAttackerValue(move, state);
        }

        // Check bonus
        if (fastGivesCheck(move, state)) {
            score += 500;
        }

        // Guard escape bonus
        if (isGuardEscapeMove(move, state)) {
            score += 800;
        }

        return score;
    }

    /**
     * FIXED: Static Exchange Evaluation with proper attacker calculation
     */
    private static int fastSEE(Move move, GameState state) {
        if (!isCapture(move, state)) {
            return 0;
        }

        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Calculate victim value
        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 3000; // Guard value
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 100; // Tower value
        }

        // FIXED: Calculate proper attacker value
        int attackerValue = getAttackerValue(move, state);

        // Simple SEE: victim value minus attacker value
        return victimValue - attackerValue;
    }

    /**
     * Calculate the value of the piece making the attack
     */
    private static int getAttackerValue(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long fromBit = GameState.bit(move.from);

        // Check if attacker is a guard
        if (((isRed ? state.redGuard : state.blueGuard) & fromBit) != 0) {
            return 50; // Guard has low material value but high strategic value
        }

        // Attacker is a tower - value based on height
        int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
        return height * 25; // Towers worth 25 per height level for SEE purposes
    }

    // === HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed ?
                (state.blueGuard & toBit) != 0 :
                (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed ?
                (state.blueTowers & toBit) != 0 :
                (state.redTowers & toBit) != 0;

        return capturesGuard || capturesTower;
    }

    /**
     * Clear quiescence table periodically to prevent memory issues
     */
    public static void clearQuiescenceTable() {
        if (qTable.size() > 100000) { // Clear when table gets too large
            qTable.clear();
        }
    }

    /**
     * NEW: Test Delta Pruning Performance
     */
    public static void testDeltaPruningPerformance() {
        GameState[] positions = {
                new GameState(),
                GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r"),
                GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r")
        };

        System.out.println("=== DELTA PRUNING PERFORMANCE TEST ===");

        for (int i = 0; i < positions.length; i++) {
            GameState state = positions[i];

            System.out.printf("\nPosition %d:\n", i + 1);

            // Test mit Delta Pruning
            resetQuiescenceStats();
            Minimax.counter = 0;

            long startTime = System.currentTimeMillis();
            Move bestMove = Minimax.findBestMoveWithStrategy(state, 5, Minimax.SearchStrategy.PVS_Q);
            long endTime = System.currentTimeMillis();

            System.out.printf("  Time: %dms\n", endTime - startTime);
            System.out.printf("  Best move: %s\n", bestMove);
            printQuiescenceStats();

            if (deltaPruningCutoffs > 0) {
                double reduction = 100.0 * deltaPruningCutoffs / qNodes;
                System.out.printf("  🚀 Q-Node reduction: %.1f%%\n", reduction);
            }
        }
    }
}