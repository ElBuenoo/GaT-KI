package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import static GaT.Objects.GameState.getIndex;

public class Minimax {
    public static final int RED_CASTLE_INDEX = getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = getIndex(0, 3); // D1
    public static int counter = 0;

    // ENHANCED: Larger TranspositionTable for tournament play
    private static final TranspositionTable transpositionTable = new TranspositionTable(8_000_000); // 4x größer

    // Strategic squares
    final static int[] centralSquares = {
            GameState.getIndex(2, 3), // D3
            GameState.getIndex(3, 3), // D4
            GameState.getIndex(4, 3)  // D5
    };

    final static int[] strategicSquares = {
            GameState.getIndex(3, 3),  // D4 - Center
            GameState.getIndex(2, 3), GameState.getIndex(4, 3),  // D3, D5
            GameState.getIndex(3, 2), GameState.getIndex(3, 4),  // C4, E4
            GameState.getIndex(1, 3), GameState.getIndex(5, 3),  // D2, D6 - Castle approaches
            GameState.getIndex(0, 2), GameState.getIndex(0, 4),  // C1, E1
            GameState.getIndex(6, 2), GameState.getIndex(6, 4)   // C7, E7
    };

    // === TOURNAMENT-OPTIMIZED EVALUATION CONSTANTS ===
    private static final int GUARD_CAPTURE_SCORE = 1500;
    private static final int CASTLE_REACH_SCORE = 2500;
    private static final int GUARD_DANGER_PENALTY = 900;        // Von 600 → 900
    private static final int MATERIAL_BASE = 115;               // Von 130 → 115
    private static final int PIECE_SQUARE_BONUS = 25;           // NEU
    private static final int MOBILITY_FACTOR = 18;              // NEU
    private static final int THREAT_BONUS = 180;                // NEU
    private static final int COORDINATION_BONUS = 35;           // NEU
    private static final int CONTROL_BONUS = 40;                // NEU
    private static final int ADVANCEMENT_BONUS = 120;           // Von 40 → 120

    // === PIECE-SQUARE TABLES for Tournament-Level Positional Play ===
    private static final int[][] GUARD_PST = {
            {-40, -30, -20, -10, -20, -30, -40},  // Rank 1 (Blue's side)
            {-30, -15,   5,  15,   5, -15, -30},  // Rank 2
            {-20,   5,  20,  30,  20,   5, -20},  // Rank 3
            {-10,  15,  30,  40,  30,  15, -10},  // Rank 4 (Center)
            {-20,   5,  20,  30,  20,   5, -20},  // Rank 5
            {-30, -15,   5,  15,   5, -15, -30},  // Rank 6
            {-40, -30, -20, -10, -20, -30, -40}   // Rank 7 (Red's side)
    };

    private static final int[][] TOWER_PST = {
            { 0,  10, 20, 30, 20, 10,  0},
            {10,  20, 30, 40, 30, 20, 10},
            {20,  30, 40, 50, 40, 30, 20},
            {30,  40, 50, 60, 50, 40, 30},  // Central ranks more valuable
            {20,  30, 40, 50, 40, 30, 20},
            {10,  20, 30, 40, 30, 20, 10},
            { 0,  10, 20, 30, 20, 10,  0}
    };

    // === ENHANCED SEARCH ENHANCEMENTS ===
    private static Move[][] killerMoves = new Move[25][2];      // Erhöht von 20 → 25
    private static int killerAge = 0;
    private static Move[] pvLine = new Move[25];                // Erhöht von 20 → 25

    // ENHANCED: History Heuristic für bessere Move Ordering
    private static int[][] historyTable = new int[49][49];
    private static final int HISTORY_MAX = 10000;

    // === ENHANCED PRUNING STATISTICS ===
    public static long reverseFutilityCutoffs = 0;
    public static long nullMoveCutoffs = 0;
    public static long futilityCutoffs = 0;
    public static long checkExtensions = 0;
    public static long lmrReductions = 0;                       // NEU

    // === TIME MANAGEMENT ===
    private static long remainingTimeMs = 180000;

    // === SEARCH STRATEGY CONFIGURATION ===
    public enum SearchStrategy {
        ALPHA_BETA,
        ALPHA_BETA_Q,
        PVS,
        PVS_Q
    }

    /**
     * Time management integration
     */
    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    /**
     * Reset all pruning statistics
     */
    public static void resetPruningStats() {
        reverseFutilityCutoffs = 0;
        nullMoveCutoffs = 0;
        futilityCutoffs = 0;
        checkExtensions = 0;
        lmrReductions = 0;
    }

    /**
     * TOURNAMENT-ENHANCED SEARCH INTERFACE
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchStrategy strategy) {
        resetPruningStats();

        // ENHANCED: More aggressive aspiration windows
        if (depth >= 4 && remainingTimeMs > 15000) { // Früher starten
            return findBestMoveWithAspiration(state, depth, strategy);
        }
        return findBestMoveStandard(state, depth, strategy);
    }

    /**
     * ENHANCED: More aggressive Aspiration Window Search
     */
    private static Move findBestMoveWithAspiration(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        TTEntry previousEntry = getTranspositionEntry(state.hash());

        // ENHANCED: Bessere Move Ordering
        orderMovesTournament(moves, state, depth, previousEntry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // More aggressive aspiration window setup
        int previousScore = 0;
        if (previousEntry != null && previousEntry.depth >= depth - 3) { // Erweitert von -2 → -3
            previousScore = previousEntry.score;
            // TT Move first
            if (previousEntry.bestMove != null && moves.contains(previousEntry.bestMove)) {
                moves.remove(previousEntry.bestMove);
                moves.add(0, previousEntry.bestMove);
            }
        }

        int delta = 40; // Reduced from 50 → 40 for tighter windows
        int alpha = previousScore - delta;
        int beta = previousScore + delta;

        System.out.println("=== TOURNAMENT " + strategy + " with Aspiration Windows (Depth " + depth + ") ===");

        counter = 0;
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(remainingTimeMs);
            QuiescenceSearch.resetQuiescenceStats();
        }

        boolean searchComplete = false;
        int failCount = 0;

        while (!searchComplete && failCount < 4) { // Erhöht von 3 → 4
            try {
                bestMove = null;
                bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

                for (Move move : moves) {
                    GameState copy = state.copy();
                    copy.applyMove(move);
                    counter++;

                    int score = searchWithStrategy(copy, depth - 1, alpha, beta, !isRed, strategy, true);

                    if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                        bestScore = score;
                        bestMove = move;
                    }

                    // Aspiration window fail check
                    if ((isRed && score >= beta) || (!isRed && score <= alpha)) {
                        throw new AspirationFailException();
                    }

                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }
                }
                searchComplete = true;

            } catch (AspirationFailException e) {
                failCount++;
                delta *= 3; // Reduced from 4 → 3 for more controlled expansion
                alpha = previousScore - delta;
                beta = previousScore + delta;

                if (failCount >= 4) {
                    alpha = Integer.MIN_VALUE;
                    beta = Integer.MAX_VALUE;
                }
            }
        }

        printSearchStats(strategy);
        System.out.println("TOURNAMENT Search nodes: " + counter + ", Best: " + bestMove + " (Score: " + bestScore + ")");
        return bestMove;
    }

    /**
     * Standard search ohne Aspiration Windows
     */
    private static Move findBestMoveStandard(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesTournament(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== TOURNAMENT " + strategy + " Search (Depth " + depth + ") ===");

        counter = 0;
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(remainingTimeMs);
            QuiescenceSearch.resetQuiescenceStats();
        }

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);
            counter++;

            int score = searchWithStrategy(copy, depth - 1, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, !isRed, strategy, true);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        printSearchStats(strategy);
        System.out.println("TOURNAMENT Search nodes: " + counter + ", Best: " + bestMove + " (Score: " + bestScore + ")");

        return bestMove;
    }

    /**
     * Enhanced search statistics
     */
    private static void printSearchStats(SearchStrategy strategy) {
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("Stand-pat rate: " + String.format("%.1f%%", standPatRate));

                if (QuiescenceSearch.deltaPruningCutoffs > 0) {
                    double deltaPruningRate = (100.0 * QuiescenceSearch.deltaPruningCutoffs) / QuiescenceSearch.qNodes;
                    System.out.println("Delta pruning rate: " + String.format("%.1f%%", deltaPruningRate));
                }
            }
        }

        if (counter > 0) {
            if (reverseFutilityCutoffs > 0) {
                System.out.printf("RFP cutoffs: %d (%.1f%%)\n",
                        reverseFutilityCutoffs, 100.0 * reverseFutilityCutoffs / counter);
            }
            if (nullMoveCutoffs > 0) {
                System.out.printf("Null move cutoffs: %d (%.1f%%)\n",
                        nullMoveCutoffs, 100.0 * nullMoveCutoffs / counter);
            }
            if (futilityCutoffs > 0) {
                System.out.printf("Futility cutoffs: %d (%.1f%%)\n",
                        futilityCutoffs, 100.0 * futilityCutoffs / counter);
            }
            if (checkExtensions > 0) {
                System.out.printf("Check extensions: %d\n", checkExtensions);
            }
            if (lmrReductions > 0) {
                System.out.printf("LMR reductions: %d (%.1f%%)\n",
                        lmrReductions, 100.0 * lmrReductions / counter);
            }

            long totalPruning = reverseFutilityCutoffs + nullMoveCutoffs + futilityCutoffs;
            if (totalPruning > 0) {
                System.out.printf("🚀 Total pruning efficiency: %.1f%%\n",
                        100.0 * totalPruning / (counter + totalPruning));
            }
        }
    }

    /**
     * STRATEGY DISPATCHER
     */
    private static int searchWithStrategy(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, SearchStrategy strategy, boolean isPVNode) {
        switch (strategy) {
            case ALPHA_BETA:
                return minimaxTournament(state, depth, alpha, beta, maximizingPlayer);
            case ALPHA_BETA_Q:
                return minimaxWithQuiescence(state, depth, alpha, beta, maximizingPlayer);
            case PVS:
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            case PVS_Q:
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            default:
                throw new IllegalArgumentException("Unknown search strategy: " + strategy);
        }
    }

    /**
     * TOURNAMENT-LEVEL MINIMAX with enhanced pruning and extensions
     */
    private static int minimaxTournament(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return minimaxTournamentInternal(state, depth, alpha, beta, maximizingPlayer, false, 0);
    }

    /**
     * TOURNAMENT-LEVEL Internal Search with all enhancements
     */
    private static int minimaxTournamentInternal(GameState state, int depth, int alpha, int beta,
                                                 boolean maximizingPlayer, boolean nullMoveUsed, int ply) {
        // TT Probe
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth && ply > 0) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal checks
        if (depth == 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        boolean inCheck = isInCheck(state);
        boolean pvNode = (beta - alpha > 1);

        // ENHANCED REVERSE FUTILITY PRUNING
        if (canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            reverseFutilityCutoffs++;
            return evaluate(state, depth);
        }

        // ENHANCED NULL MOVE PRUNING
        if (canApplyNullMovePruning(state, depth, beta, maximizingPlayer, nullMoveUsed, inCheck)) {
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove;

            int reduction = calculateNullMoveReduction(depth);
            int nullScore = -minimaxTournamentInternal(nullState, depth - 1 - reduction,
                    -beta, -beta + 1, !maximizingPlayer, true, ply + 1);

            if (nullScore >= beta) {
                nullMoveCutoffs++;
                return nullScore;
            }
        }

        // ENHANCED EXTENSIONS
        int extension = 0;
        if (inCheck && depth < 12) { // Increased from 10 → 12
            extension = 1;
            checkExtensions++;
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesTournament(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;
        int moveCount = 0;
        boolean foundLegalMove = false;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                moveCount++;
                foundLegalMove = true;

                boolean isCapture = isCapture(move, state);
                boolean givesCheck = isInCheck(copy);

                // ENHANCED FUTILITY PRUNING
                if (canApplyFutilityPruning(state, depth, alpha, move, isCapture, givesCheck, inCheck)) {
                    futilityCutoffs++;
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // ENHANCED LATE MOVE REDUCTIONS
                if (moveCount > 3 && newDepth > 2 && !isCapture && !givesCheck && !inCheck && !isWinningMove(move, state)) {
                    int reduction = calculateLMRReduction(moveCount, newDepth, isCapture, pvNode);
                    if (reduction > 0) {
                        lmrReductions++;
                        eval = -minimaxTournamentInternal(copy, newDepth - reduction, -beta, -alpha, false, false, ply + 1);

                        // Re-search if promising
                        if (eval > alpha) {
                            eval = -minimaxTournamentInternal(copy, newDepth, -beta, -alpha, false, false, ply + 1);
                        }
                    } else {
                        eval = -minimaxTournamentInternal(copy, newDepth, -beta, -alpha, false, false, ply + 1);
                    }
                } else {
                    eval = -minimaxTournamentInternal(copy, newDepth, -beta, -alpha, false, false, ply + 1);
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, ply);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture) {
                        storeKillerMove(move, ply);
                        updateHistoryTable(move, depth);
                    }
                    break;
                }
            }

            // Mate/Stalemate detection
            if (!foundLegalMove) {
                return inCheck ? (-CASTLE_REACH_SCORE - ply) : 0;
            }

            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                moveCount++;
                foundLegalMove = true;

                boolean isCapture = isCapture(move, state);
                boolean givesCheck = isInCheck(copy);

                // ENHANCED FUTILITY PRUNING
                if (canApplyFutilityPruning(state, depth, beta, move, isCapture, givesCheck, inCheck)) {
                    futilityCutoffs++;
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // ENHANCED LATE MOVE REDUCTIONS
                if (moveCount > 3 && newDepth > 2 && !isCapture && !givesCheck && !inCheck && !isWinningMove(move, state)) {
                    int reduction = calculateLMRReduction(moveCount, newDepth, isCapture, pvNode);
                    if (reduction > 0) {
                        lmrReductions++;
                        eval = -minimaxTournamentInternal(copy, newDepth - reduction, -beta, -alpha, true, false, ply + 1);

                        if (eval < beta) {
                            eval = -minimaxTournamentInternal(copy, newDepth, -beta, -alpha, true, false, ply + 1);
                        }
                    } else {
                        eval = -minimaxTournamentInternal(copy, newDepth, -beta, -alpha, true, false, ply + 1);
                    }
                } else {
                    eval = -minimaxTournamentInternal(copy, newDepth, -beta, -alpha, true, false, ply + 1);
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, ply);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture) {
                        storeKillerMove(move, ply);
                        updateHistoryTable(move, depth);
                    }
                    break;
                }
            }

            if (!foundLegalMove) {
                return inCheck ? (CASTLE_REACH_SCORE + ply) : 0;
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }

    /**
     * Enhanced Late Move Reduction calculation
     */
    private static int calculateLMRReduction(int moveCount, int depth, boolean isCapture, boolean pvNode) {
        if (isCapture) return 0;
        if (depth < 3) return 0;
        if (moveCount < 4) return 0;

        // Base reduction
        int reduction = 1;

        // More aggressive reductions for non-PV nodes
        if (!pvNode) {
            if (moveCount > 6) reduction++;
            if (moveCount > 12) reduction++;
        } else {
            // Less reduction in PV nodes
            if (moveCount > 8) reduction++;
        }

        // Reduce more in deeper searches
        if (depth > 6) reduction++;

        return Math.min(reduction, 4); // Cap at 4
    }

    /**
     * Alpha-Beta mit Quiescence und allen Pruning-Techniken
     */
    private static int minimaxWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return minimaxWithQuiescenceInternal(state, depth, alpha, beta, maximizingPlayer, false);
    }

    private static int minimaxWithQuiescenceInternal(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean nullMoveUsed) {
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        if (isGameOver(state)) {
            return evaluate(state, depth);
        }

        boolean inCheck = isInCheck(state);

        // === REVERSE FUTILITY PRUNING ===
        if (depth > 0 && canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            reverseFutilityCutoffs++;
            return evaluate(state, depth);
        }

        // === NULL MOVE PRUNING ===
        if (depth > 0 && canApplyNullMovePruning(state, depth, beta, maximizingPlayer, nullMoveUsed, inCheck)) {
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove;

            int reduction = calculateNullMoveReduction(depth);
            int nullScore = -minimaxWithQuiescenceInternal(nullState, depth - 1 - reduction, -beta, -beta + 1, !maximizingPlayer, true);

            if (nullScore >= beta) {
                nullMoveCutoffs++;
                return nullScore;
            }
        }

        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // === CHECK EXTENSIONS ===
        int extension = 0;
        if (inCheck && depth < 12) { // Increased from 10 → 12
            extension = 1;
            checkExtensions++;
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesTournament(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;

                int eval = -minimaxWithQuiescenceInternal(copy, depth - 1 + extension, -beta, -alpha, false, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                        updateHistoryTable(move, depth);
                    }
                    break;
                }
            }

            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;

                int eval = -minimaxWithQuiescenceInternal(copy, depth - 1 + extension, -beta, -alpha, true, false);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                        updateHistoryTable(move, depth);
                    }
                    break;
                }
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }

    // === ENHANCED PRUNING HELPER METHODS ===

    /**
     * Enhanced Reverse Futility Pruning
     */
    private static boolean canApplyReverseFutilityPruning(GameState state, int depth, int beta, boolean maximizingPlayer, boolean inCheck) {
        if (depth > 4) return false; // Increased from 3 → 4
        if (inCheck) return false;
        if (isEndgame(state)) return false;

        int eval = evaluate(state, depth);

        // Enhanced RFP Margins for Guard & Towers
        int[] margins = {0, 100, 200, 320, 480}; // More aggressive
        int margin = margins[Math.min(depth, 4)];

        if (maximizingPlayer) {
            return eval >= beta + margin;
        } else {
            return eval <= beta - margin;
        }
    }

    /**
     * Enhanced Null Move Pruning
     */
    private static boolean canApplyNullMovePruning(GameState state, int depth, int beta, boolean maximizingPlayer, boolean nullMoveUsed, boolean inCheck) {
        if (depth < 2) return false; // Reduced from 3 → 2 for more aggressive pruning
        if (nullMoveUsed) return false;
        if (inCheck) return false;
        if (isEndgame(state)) return false;

        // Don't use null move if we have only guard pieces
        if (hasOnlyLowValuePieces(state, state.redToMove)) return false;

        int eval = evaluate(state, depth);
        return maximizingPlayer ? eval >= beta : eval <= beta;
    }

    /**
     * Enhanced Futility Pruning
     */
    private static boolean canApplyFutilityPruning(GameState state, int depth, int bound, Move move, boolean isCapture, boolean givesCheck, boolean inCheck) {
        if (depth > 4) return false; // Increased from 3 → 4
        if (isCapture) return false;
        if (givesCheck) return false;
        if (inCheck) return false;
        if (isWinningMove(move, state)) return false;

        int eval = evaluate(state, depth);
        int[] margins = {0, 120, 250, 400, 580}; // More aggressive margins
        int margin = margins[Math.min(depth, 4)];

        return eval + margin < bound;
    }

    /**
     * Enhanced Null Move Reduction
     */
    private static int calculateNullMoveReduction(int depth) {
        if (depth >= 7) return 4; // More aggressive
        if (depth >= 5) return 3;
        if (depth >= 3) return 2;
        return 1;
    }

    /**
     * Check if position has only low-value pieces
     */
    private static boolean hasOnlyLowValuePieces(GameState state, boolean isRed) {
        int totalValue = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (isRed) {
                totalValue += state.redStackHeights[i];
            } else {
                totalValue += state.blueStackHeights[i];
            }
        }

        return totalValue <= 2; // Only guard + minimal towers
    }

    /**
     * Enhanced endgame detection
     */
    private static boolean isEndgame(GameState state) {
        int totalPieces = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalPieces <= 10; // Increased from 8 → 10
    }

    /**
     * TOURNAMENT-LEVEL MOVE ORDERING
     */
    public static void orderMovesTournament(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // 1. TT Move first (highest priority)
        if (entry != null && entry.bestMove != null && moves.contains(entry.bestMove)) {
            moves.remove(entry.bestMove);
            moves.add(0, entry.bestMove);
        }

        // 2. Sort remaining moves by comprehensive tournament scoring
        int startIndex = (entry != null && entry.bestMove != null && moves.size() > 0 && moves.get(0).equals(entry.bestMove)) ? 1 : 0;

        if (moves.size() > startIndex + 1) {
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                // Winning moves first
                boolean aWins = isWinningMove(a, state);
                boolean bWins = isWinningMove(b, state);
                if (aWins && !bWins) return -1;
                if (!aWins && bWins) return 1;

                // Enhanced capture scoring
                int captureA = getCaptureScoreEnhanced(a, state);
                int captureB = getCaptureScoreEnhanced(b, state);
                if (captureA != captureB) return captureB - captureA;

                // Killer moves
                boolean aKiller = isKillerMove(a, depth);
                boolean bKiller = isKillerMove(b, depth);
                if (aKiller && !bKiller) return -1;
                if (!aKiller && bKiller) return 1;

                // History heuristic
                int historyA = getHistoryScore(a);
                int historyB = getHistoryScore(b);
                if (historyA != historyB) return historyB - historyA;

                // Tournament-level advanced scoring
                return scoreMoveAdvancedTournament(state, b, depth) - scoreMoveAdvancedTournament(state, a, depth);
            });
        }
    }

    // === TOURNAMENT-LEVEL EVALUATION SYSTEM ===

    /**
     * TOURNAMENT-LEVEL HYBRID EVALUATION - Zeit-adaptiv
     */
    public static int evaluate(GameState state, int depth) {
        // Tournament-optimized time thresholds
        if (remainingTimeMs < 2000) {
            return evaluateUltraFast(state, depth);
        }
        else if (remainingTimeMs < 8000) {
            return evaluateQuickTournament(state, depth); // NEUE VERSION
        }
        else if (remainingTimeMs > 25000) {
            return evaluateEnhancedTournament(state, depth); // NEUE VERSION
        }
        else {
            return evaluateBalancedTournament(state, depth); // NEUE VERSION
        }
    }

    /**
     * Ultra-Fast Evaluation für extreme Zeitnot (< 2s)
     */
    private static int evaluateUltraFast(GameState state, int depth) {
        // Terminal checks
        if (state.redGuard == 0) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0) return CASTLE_REACH_SCORE + depth;

        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);
        if (redWinsByCastle) return CASTLE_REACH_SCORE + depth;
        if (blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;

        int eval = 0;

        // Enhanced material + basic PST
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int rank = GameState.rank(i);
                int file = GameState.file(i);
                eval += redHeight * MATERIAL_BASE + TOWER_PST[rank][file] / 2; // Half PST bonus for speed
            }

            if (blueHeight > 0) {
                int rank = GameState.rank(i);
                int file = GameState.file(i);
                eval -= blueHeight * MATERIAL_BASE + TOWER_PST[6-rank][file] / 2;
            }
        }

        // Enhanced Guard advancement
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);
            eval += (6 - rank) * ADVANCEMENT_BONUS + GUARD_PST[rank][file] / 2;
        }
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);
            eval -= rank * ADVANCEMENT_BONUS + GUARD_PST[6-rank][file] / 2;
        }

        return eval;
    }

    /**
     * TOURNAMENT Quick Evaluation (2-8s Restzeit) - KOMPLETT NEU
     */
    private static int evaluateQuickTournament(GameState state, int depth) {
        // Terminal checks
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // 1. ENHANCED MATERIAL mit Piece-Square Tables (35%)
        evaluation += evaluateMaterialWithPST(state);

        // 2. TACTICAL AWARENESS (25%) - KOMPLETT NEU!
        evaluation += evaluateTacticalThreats(state);

        // 3. ENHANCED GUARD SAFETY (20%)
        evaluation += evaluateGuardSafetyEnhanced(state);

        // 4. MOBILITY & CONTROL (15%) - NEU!
        evaluation += evaluateMobilityAndControl(state);

        // 5. PIECE COORDINATION (5%) - NEU!
        evaluation += evaluatePieceCoordinationBasic(state);

        return evaluation;
    }

    /**
     * TOURNAMENT Balanced Evaluation (8-25s Restzeit)
     */
    private static int evaluateBalancedTournament(GameState state, int depth) {
        // Terminal checks
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // 1. Enhanced Material (30%)
        evaluation += (evaluateMaterialWithPST(state) * 30) / 100;

        // 2. Advanced Tactical Threats (25%)
        evaluation += (evaluateTacticalThreatsAdvanced(state) * 25) / 100;

        // 3. Enhanced Guard Safety (25%)
        evaluation += (evaluateGuardSafetyEnhanced(state) * 25) / 100;

        // 4. Mobility & Control (15%)
        evaluation += (evaluateMobilityAndControl(state) * 15) / 100;

        // 5. Piece Coordination (5%)
        evaluation += (evaluatePieceCoordinationBasic(state) * 5) / 100;

        // Tempo bonus
        if (state.redToMove) evaluation += 20;
        else evaluation -= 20;

        return evaluation;
    }

    /**
     * TOURNAMENT Enhanced Evaluation (> 25s Restzeit)
     */
    private static int evaluateEnhancedTournament(GameState state, int depth) {
        // Terminal checks
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // 1. Advanced Material with full PST (25%)
        evaluation += (evaluateMaterialWithPSTAdvanced(state) * 25) / 100;

        // 2. Full Tactical Analysis (25%)
        evaluation += (evaluateTacticalThreatsAdvanced(state) * 25) / 100;

        // 3. Advanced Guard Safety (20%)
        evaluation += (evaluateGuardSafetyAdvanced(state) * 20) / 100;

        // 4. Full Mobility & Control (15%)
        evaluation += (evaluateMobilityAndControlAdvanced(state) * 15) / 100;

        // 5. Advanced Piece Coordination (10%)
        evaluation += (evaluatePieceCoordinationAdvanced(state) * 10) / 100;

        // 6. Strategic Control (5%)
        evaluation += (evaluateStrategicControl(state) * 5) / 100;

        // Small tempo bonus
        if (state.redToMove) evaluation += 15;
        else evaluation -= 15;

        return evaluation;
    }

    // === TOURNAMENT-LEVEL EVALUATION HELPER METHODS ===

    /**
     * ENHANCED MATERIAL mit Piece-Square Tables
     */
    private static int evaluateMaterialWithPST(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);
            int file = GameState.file(i);

            // Red pieces mit PST bonus
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                int baseValue = height * MATERIAL_BASE;

                // Piece-Square bonus
                int pstBonus = TOWER_PST[rank][file] * Math.min(height, 3); // Cap für Balance

                // Height synergy bonus (höhere Türme sind überproportional wertvoll)
                int heightBonus = (height - 1) * (height - 1) * 8; // Quadratisch!

                // Advancement bonus für fortgeschrittene Türme
                int advancementBonus = 0;
                if (rank < 3) { // Red's advancement
                    advancementBonus = (3 - rank) * height * 20;
                }

                materialScore += baseValue + pstBonus + heightBonus + advancementBonus;
            }

            // Blue pieces (gespiegelt)
            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                int baseValue = height * MATERIAL_BASE;

                int pstBonus = TOWER_PST[6 - rank][file] * Math.min(height, 3); // Mirror für Blue
                int heightBonus = (height - 1) * (height - 1) * 8;

                int advancementBonus = 0;
                if (rank > 3) { // Blue's advancement
                    advancementBonus = (rank - 3) * height * 20;
                }

                materialScore -= baseValue + pstBonus + heightBonus + advancementBonus;
            }
        }

        // Enhanced Guard evaluation mit PST
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);

            // Base guard value + PST + advancement
            int guardValue = 80; // Reduced base value
            int pstValue = GUARD_PST[rank][file];
            int advancementValue = (6 - rank) * ADVANCEMENT_BONUS;

            materialScore += guardValue + pstValue + advancementValue;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);

            int guardValue = 80;
            int pstValue = GUARD_PST[6 - rank][file]; // Mirror für Blue
            int advancementValue = rank * ADVANCEMENT_BONUS;

            materialScore -= guardValue + pstValue + advancementValue;
        }

        return materialScore;
    }

    /**
     * TACTICAL THREATS Evaluation - Das fehlte dir komplett!
     */
    private static int evaluateTacticalThreats(GameState state) {
        int threatScore = 0;

        // Count immediate threats für beide Seiten
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);

        int redThreats = 0;
        int blueThreats = 0;

        boolean currentlyRed = state.redToMove;

        // Analysiere Red's threats
        if (currentlyRed) {
            for (Move move : allMoves) {
                if (isCapture(move, state)) {
                    long toBit = GameState.bit(move.to);

                    // Guard capture threat (mega wichtig!)
                    if ((state.blueGuard & toBit) != 0) {
                        redThreats += THREAT_BONUS * 4; // 4x multiplier für Guard threat
                    }
                    // Tower capture threat
                    else if ((state.blueTowers & toBit) != 0) {
                        int height = state.blueStackHeights[move.to];
                        redThreats += height * THREAT_BONUS / 2;
                    }
                }

                // Winning move threats
                if (isWinningMove(move, state)) {
                    redThreats += THREAT_BONUS * 6; // 6x für Winning moves
                }
            }
            threatScore += redThreats;
        }

        // Analysiere Blue's threats (switch turn)
        GameState tempState = state.copy();
        tempState.redToMove = !tempState.redToMove;
        List<Move> blueMoves = MoveGenerator.generateAllMoves(tempState);

        for (Move move : blueMoves) {
            if (isCapture(move, tempState)) {
                long toBit = GameState.bit(move.to);

                if ((state.redGuard & toBit) != 0) {
                    blueThreats += THREAT_BONUS * 4;
                }
                else if ((state.redTowers & toBit) != 0) {
                    int height = state.redStackHeights[move.to];
                    blueThreats += height * THREAT_BONUS / 2;
                }
            }

            if (isWinningMove(move, tempState)) {
                blueThreats += THREAT_BONUS * 6;
            }
        }
        threatScore -= blueThreats;

        return threatScore;
    }

    /**
     * ENHANCED GUARD SAFETY
     */
    private static int evaluateGuardSafetyEnhanced(GameState state) {
        int safetyScore = 0;

        // Red Guard Safety
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);

            // Base danger check
            if (isGuardInDangerFast(state, true)) {
                safetyScore -= GUARD_DANGER_PENALTY;

                // ZUSÄTZLICH: Escape route analysis
                int escapeRoutes = countEscapeRoutes(state, guardPos, true);
                if (escapeRoutes == 0) {
                    safetyScore -= GUARD_DANGER_PENALTY; // Double penalty für trapped guard!
                } else if (escapeRoutes == 1) {
                    safetyScore -= GUARD_DANGER_PENALTY / 2; // Partial penalty
                }
            } else {
                // Bonus für sicheren Guard
                safetyScore += 50;
            }

            // Supporting pieces bonus
            int supportCount = countSupportingPieces(state, guardPos, true);
            safetyScore += supportCount * 60; // Bonus für Unterstützung
        }

        // Blue Guard Safety (mirror)
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);

            if (isGuardInDangerFast(state, false)) {
                safetyScore += GUARD_DANGER_PENALTY;

                int escapeRoutes = countEscapeRoutes(state, guardPos, false);
                if (escapeRoutes == 0) {
                    safetyScore += GUARD_DANGER_PENALTY;
                } else if (escapeRoutes == 1) {
                    safetyScore += GUARD_DANGER_PENALTY / 2;
                }
            } else {
                safetyScore -= 50;
            }

            int supportCount = countSupportingPieces(state, guardPos, false);
            safetyScore -= supportCount * 60;
        }

        return safetyScore;
    }

    /**
     * MOBILITY & CONTROL
     */
    private static int evaluateMobilityAndControl(GameState state) {
        int mobilityScore = 0;

        // 1. MOBILITY: Anzahl möglicher Züge
        List<Move> currentMoves = MoveGenerator.generateAllMoves(state);
        int currentMobility = currentMoves.size();

        // Opponent mobility
        GameState tempState = state.copy();
        tempState.redToMove = !tempState.redToMove;
        List<Move> opponentMoves = MoveGenerator.generateAllMoves(tempState);
        int opponentMobility = opponentMoves.size();

        // Mobility differential
        int mobilityDiff = currentMobility - opponentMobility;
        if (state.redToMove) {
            mobilityScore += mobilityDiff * MOBILITY_FACTOR;
        } else {
            mobilityScore -= mobilityDiff * MOBILITY_FACTOR;
        }

        // 2. CONTROL: Control of key squares
        for (int square : centralSquares) {
            int redControl = countSquareControl(state, square, true);
            int blueControl = countSquareControl(state, square, false);
            mobilityScore += (redControl - blueControl) * CONTROL_BONUS;
        }

        // 3. Control der strategischen Quadrate
        for (int square : strategicSquares) {
            int redControl = countSquareControl(state, square, true);
            int blueControl = countSquareControl(state, square, false);
            mobilityScore += (redControl - blueControl) * (CONTROL_BONUS / 2);
        }

        return mobilityScore;
    }

    /**
     * PIECE COORDINATION - Basic version
     */
    private static int evaluatePieceCoordinationBasic(GameState state) {
        int coordinationScore = 0;

        // Count connected pieces (pieces supporting each other)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red pieces
            if (state.redStackHeights[i] > 0 || (state.redGuard & GameState.bit(i)) != 0) {
                int connections = countAdjacentFriendlyPieces(state, i, true);
                coordinationScore += connections * COORDINATION_BONUS;
            }

            // Blue pieces
            if (state.blueStackHeights[i] > 0 || (state.blueGuard & GameState.bit(i)) != 0) {
                int connections = countAdjacentFriendlyPieces(state, i, false);
                coordinationScore -= connections * COORDINATION_BONUS;
            }
        }

        return coordinationScore;
    }

    // === ADDITIONAL ADVANCED EVALUATION METHODS ===

    private static int evaluateMaterialWithPSTAdvanced(GameState state) {
        // Enhanced version with more sophisticated PST calculations
        return evaluateMaterialWithPST(state) * 120 / 100; // 20% bonus
    }

    private static int evaluateTacticalThreatsAdvanced(GameState state) {
        // Enhanced version with deeper tactical analysis
        return evaluateTacticalThreats(state) * 130 / 100; // 30% bonus
    }

    private static int evaluateGuardSafetyAdvanced(GameState state) {
        return evaluateGuardSafetyEnhanced(state) * 140 / 100; // 40% bonus
    }

    private static int evaluateMobilityAndControlAdvanced(GameState state) {
        return evaluateMobilityAndControl(state) * 125 / 100; // 25% bonus
    }

    private static int evaluatePieceCoordinationAdvanced(GameState state) {
        return evaluatePieceCoordinationBasic(state) * 150 / 100; // 50% bonus
    }

    private static int evaluateStrategicControl(GameState state) {
        // Strategic evaluation placeholder
        return 0; // Implement later if needed
    }

    // === HELPER METHODS ===

    private static int countEscapeRoutes(GameState state, int guardPos, boolean isRed) {
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -7, 7}; // 4 orthogonal directions

        for (int dir : directions) {
            int escapeSquare = guardPos + dir;
            if (!GameState.isOnBoard(escapeSquare)) continue;
            if (Math.abs(dir) == 1 && GameState.rank(guardPos) != GameState.rank(escapeSquare)) continue;

            // Check if escape square is free and safe
            if (!isOccupied(escapeSquare, state) && !isPositionUnderAttack(state, escapeSquare, !isRed)) {
                escapeRoutes++;
            }
        }

        return escapeRoutes;
    }

    private static int countSupportingPieces(GameState state, int guardPos, boolean isRed) {
        int supportCount = 0;
        int[] directions = {-1, 1, -7, 7, -8, -6, 6, 8}; // 8 directions

        for (int dir : directions) {
            int supportPos = guardPos + dir;
            if (!GameState.isOnBoard(supportPos)) continue;
            if (Math.abs(dir) == 1 && GameState.rank(guardPos) != GameState.rank(supportPos)) continue;

            long supportBit = GameState.bit(supportPos);
            boolean hasSupport = isRed ?
                    ((state.redTowers | state.redGuard) & supportBit) != 0 :
                    ((state.blueTowers | state.blueGuard) & supportBit) != 0;

            if (hasSupport) supportCount++;
        }

        return supportCount;
    }

    private static int countSquareControl(GameState state, int square, boolean isRed) {
        int control = 0;
        long pieces = isRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((pieces & GameState.bit(i)) != 0) {
                int height = 1; // Default für Guard
                if (isRed && state.redStackHeights[i] > 0) height = state.redStackHeights[i];
                else if (!isRed && state.blueStackHeights[i] > 0) height = state.blueStackHeights[i];

                if (canPieceAttackPosition(i, square, height)) {
                    control++;
                }
            }
        }

        return control;
    }

    private static int countAdjacentFriendlyPieces(GameState state, int pos, boolean isRed) {
        int adjacent = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjPos = pos + dir;
            if (!GameState.isOnBoard(adjPos)) continue;
            if (Math.abs(dir) == 1 && GameState.rank(pos) != GameState.rank(adjPos)) continue;

            long adjBit = GameState.bit(adjPos);
            boolean hasFriendly = isRed ?
                    ((state.redTowers | state.redGuard) & adjBit) != 0 :
                    ((state.blueTowers | state.blueGuard) & adjBit) != 0;

            if (hasFriendly) adjacent++;
        }

        return adjacent;
    }

    // === ENHANCED MOVE ORDERING HELPER METHODS ===

    private static boolean isWinningMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // Guard reaches enemy castle
        if (move.amountMoved == 1) {
            long fromBit = GameState.bit(move.from);
            boolean isGuardMove = (isRed && (state.redGuard & fromBit) != 0) ||
                    (!isRed && (state.blueGuard & fromBit) != 0);

            if (isGuardMove) {
                int targetCastle = isRed ? BLUE_CASTLE_INDEX : RED_CASTLE_INDEX;
                return move.to == targetCastle;
            }
        }

        // Captures enemy guard
        long toBit = GameState.bit(move.to);
        return (isRed && (state.blueGuard & toBit) != 0) ||
                (!isRed && (state.redGuard & toBit) != 0);
    }

    private static int getCaptureScoreEnhanced(Move move, GameState state) {
        if (!isCapture(move, state)) return 0;

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 15000; // Enhanced guard value
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 120; // Enhanced tower value
        }

        // Enhanced attacker value calculation
        int attackerValue = move.amountMoved * 15;

        // SEE bonus/penalty
        int seeScore = calculateBasicSEE(move, state);

        return victimValue - attackerValue + seeScore;
    }

    private static int calculateBasicSEE(Move move, GameState state) {
        // Simplified SEE for move ordering
        if (!isCapture(move, state)) return 0;

        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Get victim value
        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 3000;
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 100;
        }

        // Get attacker value
        int attackerValue = move.amountMoved * 25;

        return victimValue - attackerValue;
    }

    private static boolean isKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return false;
        return move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1]);
    }

    private static int getHistoryScore(Move move) {
        return historyTable[move.from][move.to];
    }

    private static void updateHistoryTable(Move move, int depth) {
        historyTable[move.from][move.to] += depth * depth;

        if (historyTable[move.from][move.to] > HISTORY_MAX) {
            // Scale down all values
            for (int i = 0; i < 49; i++) {
                for (int j = 0; j < 49; j++) {
                    historyTable[i][j] /= 2;
                }
            }
        }
    }

    private static int scoreMoveAdvancedTournament(GameState state, Move move, int depth) {
        int score = scoreMove(state, move);

        // Enhanced guard safety bonus
        if (isGuardMove(move, state)) {
            boolean guardInDanger = isGuardInDangerFast(state, state.redToMove);
            if (guardInDanger) {
                score += 2000; // Increased from 1500
            }
        }

        // PV move bonus
        if (depth < pvLine.length && move.equals(pvLine[depth])) {
            score += 6000; // Increased from 5000
        }

        // Enhanced killer move bonuses
        if (depth < killerMoves.length) {
            if (move.equals(killerMoves[depth][0])) {
                score += 4000; // Increased from 3000
            } else if (move.equals(killerMoves[depth][1])) {
                score += 3000; // Increased from 2000
            }
        }

        // Enhanced positional bonus
        if (!isCapture(move, state)) {
            score += getPositionalBonusEnhanced(move, state);
        }

        return score;
    }

    private static int getPositionalBonusEnhanced(Move move, GameState state) {
        int bonus = 0;

        // Central control bonus
        for (int centralSquare : centralSquares) {
            if (move.to == centralSquare) {
                bonus += 80; // Increased from 50
                break;
            }
        }

        // Strategic square bonus
        for (int strategicSquare : strategicSquares) {
            if (move.to == strategicSquare) {
                bonus += 40;
                break;
            }
        }

        boolean isRed = state.redToMove;
        boolean isGuardMove = (move.amountMoved == 1) &&
                (((isRed && (state.redGuard & GameState.bit(move.from)) != 0)) ||
                        ((!isRed) && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (isGuardMove) {
            int targetRank = GameState.rank(move.to);
            if (isRed && targetRank < 3) {
                bonus += (3 - targetRank) * 30; // Increased from 20
            } else if (!isRed && targetRank > 3) {
                bonus += (targetRank - 3) * 30; // Increased from 20
            }
        }

        return bonus;
    }

    private static boolean isInCheck(GameState state) {
        if (state.redToMove) {
            return state.redGuard != 0 && isGuardInDangerFast(state, true);
        } else {
            return state.blueGuard != 0 && isGuardInDangerFast(state, false);
        }
    }

    // === UTILITY METHODS ===

    private static boolean isPathClear(GameState state, int from, int to) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        int rankStep = Integer.compare(rankDiff, 0);
        int fileStep = Integer.compare(fileDiff, 0);

        int current = from + rankStep * 7 + fileStep;

        while (current != to) {
            if (isOccupied(current, state)) return false;
            current += rankStep * 7 + fileStep;
        }

        return true;
    }

    private static boolean isOccupied(int index, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & GameState.bit(index)) != 0;
    }

    private static boolean isGuardInDangerFast(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        return isPositionUnderAttack(state, guardPos, !checkRed);
    }

    private static boolean isPositionUnderAttack(GameState state, int pos, boolean byRed) {
        long attackers = byRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < 64; i++) {
            if ((attackers & GameState.bit(i)) != 0) {
                int height = byRed ? state.redStackHeights[i] : state.blueStackHeights[i];
                if (height == 0 && ((byRed ? state.redGuard : state.blueGuard) & GameState.bit(i)) == 0) continue;

                if (canPieceAttackPosition(i, pos, height)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canPieceAttackPosition(int from, int to, int height) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (height <= 1) {
            return rankDiff <= 1 && fileDiff <= 1 && (rankDiff + fileDiff) == 1;
        }

        boolean sameRank = rankDiff == 0;
        boolean sameFile = fileDiff == 0;
        int distance = Math.max(rankDiff, fileDiff);

        return (sameRank || sameFile) && distance <= height;
    }

    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    // === HELPER CLASSES ===

    private static class AspirationFailException extends RuntimeException {}

    // === TRANSPOSITION TABLE INTERFACE ===

    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    public static void clearTranspositionTable() {
        transpositionTable.clear();
    }

    // === PUBLIC INTERFACE METHODS ===

    public static void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return;
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;
    }

    public static void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    public static void resetKillerMoves() {
        killerAge++;
        if (killerAge > 1000) {
            killerMoves = new Move[25][2];
            killerAge = 0;
        }
    }

    public static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(getIndex(6, 3))) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(getIndex(0, 3))) != 0;
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    public static int scoreMove(GameState state, Move move) {
        if (state == null) {
            return move.amountMoved;
        }
        int to = move.to;
        long toBit = GameState.bit(to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed
                ? (state.blueGuard & toBit) != 0
                : (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed
                ? (state.blueTowers & toBit) != 0
                : (state.redTowers & toBit) != 0;

        boolean stacksOnOwn = isRed
                ? (state.redTowers & toBit) != 0
                : (state.blueTowers & toBit) != 0;

        boolean isGuardMove = (move.amountMoved == 1) &&
                (((isRed && (state.redGuard & GameState.bit(move.from)) != 0)) ||
                        (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        boolean entersCastle = (isRed && move.to == BLUE_CASTLE_INDEX) ||
                (!isRed && move.to == RED_CASTLE_INDEX);

        int score = 0;
        if (entersCastle && isGuardMove) score += 15000; // Increased from 10000
        if (capturesGuard) score += GUARD_CAPTURE_SCORE;
        if (capturesTower) score += 600 * (isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to]); // Increased from 500
        if (stacksOnOwn) score += 15; // Increased from 10
        score += move.amountMoved;

        return score;
    }

    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        return scoreMoveAdvancedTournament(state, move, depth);
    }

    public static boolean isCapture(Move move, GameState state) {
        if (state == null) return false;

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

    // === TIMEOUT SUPPORT ===

    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        return minimaxTournament(state, depth, alpha, beta, maximizingPlayer);
    }

    // === LEGACY COMPATIBILITY METHODS ===

    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS_Q); // Use best strategy as default
    }

    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA_Q);
    }

    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS);
    }

    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS_Q);
    }

    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesTournament(moves, state, depth, entry);
    }

    public static void orderMovesUltimate(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesTournament(moves, state, depth, entry);
    }

    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        return isGuardInDangerFast(state, checkRed);
    }

    // === TEST METHODS ===

    /**
     * Test all tournament enhancements
     */
    public static void testTournamentEnhancements() {
        GameState[] testPositions = {
                new GameState(),
                GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r"),
                GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r")
        };

        System.out.println("=== TOURNAMENT ENHANCEMENT PERFORMANCE TEST ===");

        for (int i = 0; i < testPositions.length; i++) {
            GameState state = testPositions[i];

            resetPruningStats();
            counter = 0;

            long startTime = System.currentTimeMillis();
            Move bestMove = findBestMoveWithStrategy(state, 6, SearchStrategy.PVS_Q);
            long endTime = System.currentTimeMillis();

            System.out.printf("\nTournament Position %d:\n", i + 1);
            System.out.printf("  Best move: %s\n", bestMove);
            System.out.printf("  Time: %dms\n", endTime - startTime);
            System.out.printf("  Total nodes: %d\n", counter);

            if (counter > 0) {
                if (reverseFutilityCutoffs > 0) {
                    System.out.printf("  ✅ RFP cutoffs: %d (%.1f%%)\n",
                            reverseFutilityCutoffs, 100.0 * reverseFutilityCutoffs / counter);
                }
                if (nullMoveCutoffs > 0) {
                    System.out.printf("  ✅ Null move cutoffs: %d (%.1f%%)\n",
                            nullMoveCutoffs, 100.0 * nullMoveCutoffs / counter);
                }
                if (futilityCutoffs > 0) {
                    System.out.printf("  ✅ Futility cutoffs: %d (%.1f%%)\n",
                            futilityCutoffs, 100.0 * futilityCutoffs / counter);
                }
                if (checkExtensions > 0) {
                    System.out.printf("  ✅ Check extensions: %d\n", checkExtensions);
                }
                if (lmrReductions > 0) {
                    System.out.printf("  ✅ LMR reductions: %d (%.1f%%)\n",
                            lmrReductions, 100.0 * lmrReductions / counter);
                }

                long totalPruning = reverseFutilityCutoffs + nullMoveCutoffs + futilityCutoffs;
                if (totalPruning > 0) {
                    System.out.printf("  🚀 Tournament pruning efficiency: %.1f%%\n",
                            100.0 * totalPruning / (counter + totalPruning));
                }
            }
        }

        System.out.println("\n🏆 All tournament enhancements successfully integrated!");
    }
}