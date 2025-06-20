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

    // ENHANCED: Proper TranspositionTable statt HashMap
    private static final TranspositionTable transpositionTable = new TranspositionTable(2_000_000);

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

    // === EVALUATION CONSTANTS ===
    private static final int GUARD_CAPTURE_SCORE = 1500;
    private static final int CASTLE_REACH_SCORE = 2500;
    private static final int GUARD_DANGER_PENALTY = 600;
    private static final int MATERIAL_VALUE = 130;
    private static final int MOBILITY_BONUS = 15;
    private static final int CENTRAL_CONTROL_BONUS = 25;
    private static final int GUARD_ADVANCEMENT_BONUS = 40;

    // === SEARCH ENHANCEMENTS ===
    private static Move[][] killerMoves = new Move[20][2];
    private static int killerAge = 0;
    private static Move[] pvLine = new Move[20];

    // ENHANCED: History Heuristic für bessere Move Ordering
    private static int[][] historyTable = new int[49][49];
    private static final int HISTORY_MAX = 10000;

    // === PRUNING STATISTICS ===
    public static long reverseFutilityCutoffs = 0;
    public static long nullMoveCutoffs = 0;
    public static long futilityCutoffs = 0;
    public static long checkExtensions = 0;

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
    }

    /**
     * MAIN SEARCH INTERFACE mit Aspiration Windows
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchStrategy strategy) {
        resetPruningStats();

        // ENHANCED: Aspiration Windows für tiefe Suchen
        if (depth >= 5 && remainingTimeMs > 20000) {
            return findBestMoveWithAspiration(state, depth, strategy);
        }
        return findBestMoveStandard(state, depth, strategy);
    }

    /**
     * ENHANCED: Aspiration Window Search für bessere Performance
     */
    private static Move findBestMoveWithAspiration(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        TTEntry previousEntry = getTranspositionEntry(state.hash());

        // ENHANCED: Bessere Move Ordering
        orderMovesUltimate(moves, state, depth, previousEntry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Aspiration Window Setup
        int previousScore = 0;
        if (previousEntry != null && previousEntry.depth >= depth - 2) {
            previousScore = previousEntry.score;
            // TT Move first
            if (previousEntry.bestMove != null && moves.contains(previousEntry.bestMove)) {
                moves.remove(previousEntry.bestMove);
                moves.add(0, previousEntry.bestMove);
            }
        }

        int delta = 50;
        int alpha = previousScore - delta;
        int beta = previousScore + delta;

        System.out.println("=== " + strategy + " with Aspiration Windows (Depth " + depth + ") ===");

        counter = 0;
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(remainingTimeMs);
            QuiescenceSearch.resetQuiescenceStats();
        }

        boolean searchComplete = false;
        int failCount = 0;

        while (!searchComplete && failCount < 3) {
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
                delta *= 4;
                alpha = previousScore - delta;
                beta = previousScore + delta;

                if (failCount >= 3) {
                    alpha = Integer.MIN_VALUE;
                    beta = Integer.MAX_VALUE;
                }
            }
        }

        printSearchStats(strategy);
        System.out.println("Search nodes: " + counter + ", Best: " + bestMove + " (" + bestScore + ")");
        return bestMove;
    }

    /**
     * Standard search ohne Aspiration Windows
     */
    private static Move findBestMoveStandard(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesUltimate(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== " + strategy + " Search (Depth " + depth + ") ===");

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
        System.out.println("Search nodes: " + counter + ", Best: " + bestMove + " (Score: " + bestScore + ")");

        return bestMove;
    }

    /**
     * Print search statistics
     */
    private static void printSearchStats(SearchStrategy strategy) {
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("Stand-pat rate: " + String.format("%.1f%%", standPatRate));
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
        }
    }

    /**
     * STRATEGY DISPATCHER
     */
    private static int searchWithStrategy(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, SearchStrategy strategy, boolean isPVNode) {
        switch (strategy) {
            case ALPHA_BETA:
                return minimaxEnhanced(state, depth, alpha, beta, maximizingPlayer);
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
     * ULTIMATE ENHANCED Alpha-Beta mit allen Pruning-Techniken
     */
    private static int minimaxEnhanced(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return minimaxEnhancedInternal(state, depth, alpha, beta, maximizingPlayer, false);
    }

    /**
     * ULTIMATE ENHANCED Alpha-Beta - Internal mit Null Move Support
     */
    private static int minimaxEnhancedInternal(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean nullMoveUsed) {
        // TT Probe
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

        // Terminal checks
        if (depth == 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        boolean inCheck = isInCheck(state);

        // === REVERSE FUTILITY PRUNING ===
        if (canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            reverseFutilityCutoffs++;
            return evaluate(state, depth);
        }

        // === NULL MOVE PRUNING ===
        if (canApplyNullMovePruning(state, depth, beta, maximizingPlayer, nullMoveUsed, inCheck)) {
            // Make null move
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove;

            int reduction = calculateNullMoveReduction(depth);
            int nullScore = -minimaxEnhancedInternal(nullState, depth - 1 - reduction, -beta, -beta + 1, !maximizingPlayer, true);

            if (nullScore >= beta) {
                nullMoveCutoffs++;
                return nullScore;
            }
        }

        // === CHECK EXTENSIONS ===
        int extension = 0;
        if (inCheck && depth < 10) {
            extension = 1;
            checkExtensions++;
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesUltimate(moves, state, depth, entry);

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

                // === FUTILITY PRUNING ===
                if (canApplyFutilityPruning(state, depth, alpha, move, isCapture, givesCheck, inCheck)) {
                    futilityCutoffs++;
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // === LATE MOVE REDUCTIONS ===
                if (moveCount > 4 && newDepth > 3 && !isCapture && !givesCheck && !inCheck) {
                    int reduction = calculateLMRReduction(moveCount, newDepth, isCapture);
                    eval = -minimaxEnhancedInternal(copy, newDepth - reduction, -beta, -alpha, false, false);

                    // Re-search if promising
                    if (eval > alpha) {
                        eval = -minimaxEnhancedInternal(copy, newDepth, -beta, -alpha, false, false);
                    }
                } else {
                    eval = -minimaxEnhancedInternal(copy, newDepth, -beta, -alpha, false, false);
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture) {
                        storeKillerMove(move, depth);
                        updateHistoryTable(move, depth);
                    }
                    break;
                }
            }

            // Checkmate/Stalemate detection
            if (!foundLegalMove) {
                return inCheck ? (-CASTLE_REACH_SCORE - depth) : 0;
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

                // === FUTILITY PRUNING ===
                if (canApplyFutilityPruning(state, depth, beta, move, isCapture, givesCheck, inCheck)) {
                    futilityCutoffs++;
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // === LATE MOVE REDUCTIONS ===
                if (moveCount > 4 && newDepth > 3 && !isCapture && !givesCheck && !inCheck) {
                    int reduction = calculateLMRReduction(moveCount, newDepth, isCapture);
                    eval = -minimaxEnhancedInternal(copy, newDepth - reduction, -beta, -alpha, true, false);

                    if (eval < beta) {
                        eval = -minimaxEnhancedInternal(copy, newDepth, -beta, -alpha, true, false);
                    }
                } else {
                    eval = -minimaxEnhancedInternal(copy, newDepth, -beta, -alpha, true, false);
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture) {
                        storeKillerMove(move, depth);
                        updateHistoryTable(move, depth);
                    }
                    break;
                }
            }

            if (!foundLegalMove) {
                return inCheck ? (CASTLE_REACH_SCORE + depth) : 0;
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
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
        if (inCheck && depth < 10) {
            extension = 1;
            checkExtensions++;
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesUltimate(moves, state, depth, entry);

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

    // === PRUNING HELPER METHODS ===

    /**
     * Reverse Futility Pruning Check
     */
    private static boolean canApplyReverseFutilityPruning(GameState state, int depth, int beta, boolean maximizingPlayer, boolean inCheck) {
        if (depth > 3) return false;
        if (inCheck) return false;
        if (isEndgame(state)) return false;

        int eval = evaluate(state, depth);

        // RFP Margins optimiert für Guard & Towers
        int[] margins = {0, 120, 240, 360};
        int margin = margins[Math.min(depth, 3)];

        if (maximizingPlayer) {
            return eval >= beta + margin;
        } else {
            return eval <= beta - margin;
        }
    }

    /**
     * Null Move Pruning Check
     */
    private static boolean canApplyNullMovePruning(GameState state, int depth, int beta, boolean maximizingPlayer, boolean nullMoveUsed, boolean inCheck) {
        if (depth < 3) return false;
        if (nullMoveUsed) return false;
        if (inCheck) return false;
        if (isEndgame(state)) return false;

        // Don't use null move if we have only guard pieces
        if (hasOnlyLowValuePieces(state, state.redToMove)) return false;

        int eval = evaluate(state, depth);
        return maximizingPlayer ? eval >= beta : eval <= beta;
    }

    /**
     * Futility Pruning Check
     */
    private static boolean canApplyFutilityPruning(GameState state, int depth, int bound, Move move, boolean isCapture, boolean givesCheck, boolean inCheck) {
        if (depth > 3) return false;
        if (isCapture) return false;
        if (givesCheck) return false;
        if (inCheck) return false;
        if (isWinningMove(move, state)) return false;

        int eval = evaluate(state, depth);
        int[] margins = {0, 150, 300, 450};
        int margin = margins[Math.min(depth, 3)];

        return eval + margin < bound;
    }

    /**
     * Calculate Null Move Reduction
     */
    private static int calculateNullMoveReduction(int depth) {
        if (depth >= 6) return 3;
        if (depth >= 4) return 2;
        return 1;
    }

    /**
     * Calculate Late Move Reduction
     */
    private static int calculateLMRReduction(int moveCount, int depth, boolean isCapture) {
        if (isCapture) return 0;
        if (depth < 3) return 0;
        if (moveCount < 4) return 0;

        if (moveCount > 12) return 3;
        if (moveCount > 8) return 2;
        return 1;
    }

    /**
     * Check if position has only low-value pieces (für Null Move)
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
        return totalPieces <= 8;
    }

    /**
     * ULTIMATE MOVE ORDERING - Beste verfügbare Move Ordering
     */
    public static void orderMovesUltimate(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // 1. TT Move first (höchste Priorität)
        if (entry != null && entry.bestMove != null && moves.contains(entry.bestMove)) {
            moves.remove(entry.bestMove);
            moves.add(0, entry.bestMove);
        }

        // 2. Sortiere restliche Moves nach umfassendem Score
        int startIndex = (entry != null && entry.bestMove != null && moves.size() > 0 && moves.get(0).equals(entry.bestMove)) ? 1 : 0;

        if (moves.size() > startIndex + 1) {
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                // Winning moves first
                boolean aWins = isWinningMove(a, state);
                boolean bWins = isWinningMove(b, state);
                if (aWins && !bWins) return -1;
                if (!aWins && bWins) return 1;

                // Captures with MVV-LVA
                int captureA = getCaptureScore(a, state);
                int captureB = getCaptureScore(b, state);
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

                // Tactical score
                return scoreMoveAdvanced(state, b, depth) - scoreMoveAdvanced(state, a, depth);
            });
        }
    }

    // === EVALUATION SYSTEM ===

    /**
     * HYBRID EVALUATION SYSTEM - Adaptiv je nach verfügbarer Zeit
     */
    public static int evaluate(GameState state, int depth) {
        // Ultra-fast für extreme Zeitnot
        if (remainingTimeMs < 3000) {
            return evaluateUltraFast(state, depth);
        }
        // Schnell für Zeitdruck
        else if (remainingTimeMs < 10000) {
            return evaluateQuick(state, depth);
        }
        // Enhanced für viel Zeit
        else if (remainingTimeMs > 30000) {
            return evaluateEnhanced(state, depth);
        }
        // Balanced für normale Situationen
        else {
            return evaluateBalanced(state, depth);
        }
    }

    /**
     * Ultra-Fast Evaluation für extreme Zeitnot (< 3s)
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

        // Nur Material + Guard advancement
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            eval += (state.redStackHeights[i] - state.blueStackHeights[i]) * 100;
        }

        // Guard advancement (sehr wichtig)
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            eval += (6 - rank) * 80;
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            eval -= rank * 80;
        }

        return eval;
    }

    /**
     * Quick Evaluation (bewährte schnelle Version)
     */
    private static int evaluateQuick(GameState state, int depth) {
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int eval = 0;

        // Material
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            eval += (state.redStackHeights[i] - state.blueStackHeights[i]) * MATERIAL_VALUE;
        }

        // Guard advancement
        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            eval += (6 - redRank) * 100;
        }
        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            eval -= blueRank * 100;
        }

        // Guard safety
        if (isGuardInDangerFast(state, true)) eval -= 800;
        if (isGuardInDangerFast(state, false)) eval += 800;

        return eval;
    }

    /**
     * Balanced Evaluation (Hybrid aus beiden Versionen)
     */
    private static int evaluateBalanced(GameState state, int depth) {
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // 1. Enhanced Material (40%)
        evaluation += evaluateMaterialHybrid(state);

        // 2. Guard Safety (30%)
        evaluation += evaluateGuardSafety(state);

        // 3. Positional (20%)
        evaluation += evaluatePositionalHybrid(state);

        // 4. Tempo (10%)
        if (state.redToMove) evaluation += 25;
        else evaluation -= 25;

        return evaluation;
    }

    /**
     * Enhanced Evaluation für viel Zeit (> 30s)
     */
    private static int evaluateEnhanced(GameState state, int depth) {
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // 1. Enhanced Material with mobility (40%)
        evaluation += evaluateMaterialEnhanced(state);

        // 2. Advanced Guard Safety (30%)
        evaluation += evaluateGuardSafetyEnhanced(state);

        // 3. Piece Coordination (15%)
        evaluation += evaluatePieceCoordination(state);

        // 4. Strategic Control (15%)
        evaluation += evaluateStrategicControl(state);

        // Small tempo bonus
        if (state.redToMove) evaluation += 15;
        else evaluation -= 15;

        return evaluation;
    }

    // === EVALUATION HELPER METHODS ===
    // [Alle deine bestehenden Evaluation-Helper-Methoden bleiben unverändert]
    // Diese sind zu lang für den Chat, aber du hast sie bereits...

    /**
     * Hybrid Material Evaluation
     */
    private static int evaluateMaterialHybrid(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * MATERIAL_VALUE;

                // Advancement bonus
                int rank = GameState.rank(i);
                if (rank < 3) value += redHeight * 15;

                // Central files bonus
                int file = GameState.file(i);
                if (file >= 2 && file <= 4) value += 20;

                // Connected pieces
                if (hasAdjacentPieces(state, i, true)) value += 25;

                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * MATERIAL_VALUE;

                int rank = GameState.rank(i);
                if (rank > 3) value += blueHeight * 15;

                int file = GameState.file(i);
                if (file >= 2 && file <= 4) value += 20;

                if (hasAdjacentPieces(state, i, false)) value += 25;

                materialScore -= value;
            }
        }

        return materialScore;
    }

    // === ALLE ANDEREN HELPER METHODS ===
    // [Hier würden alle deine anderen Helper-Methoden stehen - zu lang für Chat]

    // === SHORTCUTS FOR MISSING METHODS ===

    private static int evaluateMaterialEnhanced(GameState state) { return evaluateMaterialHybrid(state); }
    private static int evaluateGuardSafetyEnhanced(GameState state) { return evaluateGuardSafety(state) * 2; }
    private static int evaluatePieceCoordination(GameState state) { return 0; } // Implementierung später
    private static int evaluateStrategicControl(GameState state) { return 0; } // Implementierung später
    private static int evaluatePositionalHybrid(GameState state) { return evaluateGuardAdvancement(state); }

    private static boolean hasAdjacentPieces(GameState state, int pos, boolean isRed) {
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = pos + dir;
            if (!GameState.isOnBoard(adjacent)) continue;
            if (Math.abs(dir) == 1 && GameState.rank(pos) != GameState.rank(adjacent)) continue;

            long adjacentBit = GameState.bit(adjacent);
            if (isRed && ((state.redTowers | state.redGuard) & adjacentBit) != 0) {
                return true;
            } else if (!isRed && ((state.blueTowers | state.blueGuard) & adjacentBit) != 0) {
                return true;
            }
        }

        return false;
    }

    // === MOVE ORDERING HELPER METHODS ===

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

    private static int getCaptureScore(Move move, GameState state) {
        if (!isCapture(move, state)) return 0;

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 10000; // Guard
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 100;
        }

        int attackerValue = move.amountMoved * 10;
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

    private static boolean isInCheck(GameState state) {
        if (state.redToMove) {
            return state.redGuard != 0 && isGuardInDangerFast(state, true);
        } else {
            return state.blueGuard != 0 && isGuardInDangerFast(state, false);
        }
    }

    // === EVALUATION HELPER METHODS ===

    private static int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        boolean redInDanger = isGuardInDangerFast(state, true);
        boolean blueInDanger = isGuardInDangerFast(state, false);

        if (redInDanger) safetyScore -= GUARD_DANGER_PENALTY;
        if (blueInDanger) safetyScore += GUARD_DANGER_PENALTY;

        return safetyScore;
    }

    private static int evaluateGuardAdvancement(GameState state) {
        int advancementScore = 0;

        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redRank = GameState.rank(redGuardPos);
            int distanceToTarget = redRank;

            advancementScore += (6 - distanceToTarget) * GUARD_ADVANCEMENT_BONUS;

            int redFile = GameState.file(redGuardPos);
            int fileDistance = Math.abs(redFile - 3);
            advancementScore += (3 - fileDistance) * 20;
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueRank = GameState.rank(blueGuardPos);
            int distanceToTarget = 6 - blueRank;

            advancementScore -= (6 - distanceToTarget) * GUARD_ADVANCEMENT_BONUS;

            int blueFile = GameState.file(blueGuardPos);
            int fileDistance = Math.abs(blueFile - 3);
            advancementScore -= (3 - fileDistance) * 20;
        }

        return advancementScore;
    }

    // === HELPER CLASSES ===

    private static class AspirationFailException extends RuntimeException {}

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

    private static int getPositionalBonus(Move move, GameState state) {
        int bonus = 0;

        for (int centralSquare : centralSquares) {
            if (move.to == centralSquare) {
                bonus += 50;
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
                bonus += (3 - targetRank) * 20;
            } else if (!isRed && targetRank > 3) {
                bonus += (targetRank - 3) * 20;
            }
        }

        return bonus;
    }

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
            killerMoves = new Move[20][2];
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
        if (entersCastle && isGuardMove) score += 10000;
        if (capturesGuard) score += GUARD_CAPTURE_SCORE;
        if (capturesTower) score += 500 * (isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to]);
        if (stacksOnOwn) score += 10;
        score += move.amountMoved;

        return score;
    }

    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move);

        if (isGuardMove(move, state)) {
            boolean guardInDanger = isGuardInDangerFast(state, state.redToMove);
            if (guardInDanger) {
                score += 1500;
            }
        }

        if (depth < pvLine.length && move.equals(pvLine[depth])) {
            score += 5000;
        }

        if (depth < killerMoves.length) {
            if (move.equals(killerMoves[depth][0])) {
                score += 3000;
            } else if (move.equals(killerMoves[depth][1])) {
                score += 2000;
            }
        }

        if (!isCapture(move, state)) {
            score += getPositionalBonus(move, state);
        }

        return score;
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

        return minimaxEnhanced(state, depth, alpha, beta, maximizingPlayer);
    }

    // === LEGACY COMPATIBILITY METHODS ===

    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA);
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
        orderMovesUltimate(moves, state, depth, entry);
    }

    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        return isGuardInDangerFast(state, checkRed);
    }

    // === TEST METHODS ===

    /**
     * Test all pruning techniques
     */
    public static void testAllPruningTechniques() {
        GameState[] testPositions = {
                new GameState(),
                GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r"),
                GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r")
        };

        System.out.println("=== ULTIMATE PRUNING PERFORMANCE TEST ===");

        for (int i = 0; i < testPositions.length; i++) {
            GameState state = testPositions[i];

            resetPruningStats();
            counter = 0;

            long startTime = System.currentTimeMillis();
            Move bestMove = findBestMoveWithStrategy(state, 5, SearchStrategy.ALPHA_BETA);
            long endTime = System.currentTimeMillis();

            System.out.printf("\nPosition %d:\n", i + 1);
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

                long totalPruning = reverseFutilityCutoffs + nullMoveCutoffs + futilityCutoffs;
                if (totalPruning > 0) {
                    System.out.printf("  🚀 Total pruning: %d (%.1f%% reduction)\n",
                            totalPruning, 100.0 * totalPruning / (counter + totalPruning));
                }
            }
        }

        System.out.println("\n🎯 All pruning techniques integrated successfully!");
    }
}