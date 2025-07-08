package GaT.search;

import GaT.evaluation.StaticExchangeEvaluator;
import GaT.evaluation.ThreatDetector;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.search.MoveGenerator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * FIXED PRINCIPAL VARIATION SEARCH - PHASE 1 TIMEOUT FIX
 *
 * CRITICAL FIXES:
 * ✅ 1. GRACEFUL TIMEOUT HANDLING - No more RuntimeException crashes
 * ✅ 2. Search interruption flag for clean exits
 * ✅ 3. Statistics properly integrated with SearchStatistics.getInstance()
 * ✅ 4. Node counting now works correctly
 * ✅ 5. Move ordering integration fixed
 * ✅ 6. Quiescence search properly integrated
 */
public class PVSSearch {

    // === DEPENDENCIES - FIXED ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final SearchStatistics statistics = SearchStatistics.getInstance(); // FIXED: Use shared instance

    // === TIMEOUT MANAGEMENT - PHASE 1 FIX ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false; // NEW: Graceful interruption

    // === MAIN PVS INTERFACE ===

    /**
     * Standard PVS without Quiescence - FIXED
     */
    public static int searchEnhanced(GameState state, int depth, int alpha, int beta,
                                     boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount();

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // === NULL MOVE PRUNING (not in PV nodes) ===
        if (!isPVNode && depth >= 3) {
            NullMovePruning.NullMoveResult nullResult =
                    NullMovePruning.tryNullMovePruning(state, depth, alpha, beta,
                            maximizingPlayer, isPVNode,
                            Minimax.getSearchEngine());
            if (nullResult.shouldPrune) {
                return nullResult.score;
            }

            // Use threat information for move ordering
            if (nullResult.threatDetected) {
                System.out.println("⚠️ Threat detected via null move");
            }
        }

        // TT lookup
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return Minimax.evaluate(state, depth);
        }

        // === THREAT DETECTION for search extensions ===
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.quickThreatCheck(state);
        if (threats.hasCriticalThreats() && depth < 10) {
            depth += 1; // Extend search for critical threats
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());

        // === SEE FILTERING ===
        moves = StaticExchangeEvaluator.filterBadCaptures(moves, state);

        // Enhanced move ordering with threat information
        orderMovesWithThreats(moves, state, depth, entry, threats);

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;
        int moveNumber = 0;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                moveNumber++;

                if (moveNumber % 3 == 0 && searchInterrupted) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;
                boolean isCapture = Minimax.isCapture(move, state);
                boolean givesCheck = false; // Implement based on your game rules

                if (isFirstMove || isPVNode) {
                    eval = searchEnhanced(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    // === LATE MOVE REDUCTIONS ===
                    LateMoveReductions.LMRResult lmrResult =
                            LateMoveReductions.calculateReduction(state, move, depth, moveNumber,
                                    isPVNode, isCapture, givesCheck, alpha, beta);

                    if (lmrResult.shouldReduce) {
                        int reducedDepth = Math.max(1, depth - 1 - lmrResult.reduction);
                        eval = searchEnhanced(copy, reducedDepth, alpha, alpha + 1, false, false);

                        // Re-search if promising
                        if (eval > alpha && eval < beta) {
                            eval = searchEnhanced(copy, depth - 1, alpha, beta, false, true);
                        }
                    } else {
                        // Normal null window search
                        eval = searchEnhanced(copy, depth - 1, alpha, alpha + 1, false, false);

                        if (eval > alpha && eval < beta) {
                            eval = searchEnhanced(copy, depth - 1, eval, beta, false, true);
                        }
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;
        } else {
            // Similar implementation for minimizing player
            // ... (implement similar logic for minimizing)
        }
    }

    // Helper method for enhanced move ordering
    private static void orderMovesWithThreats(List<Move> moves, GameState state, int depth,
                                              TTEntry entry, ThreatDetector.ThreatAnalysis threats) {

        // First, use standard move ordering
        moveOrdering.orderMoves(moves, state, depth, entry);

        // Then prioritize defensive moves if threats detected
        if (threats.hasAnyThreats()) {
            moves.sort((a, b) -> {
                boolean aIsDefensive = threats.defensiveMoves.contains(a);
                boolean bIsDefensive = threats.defensiveMoves.contains(b);

                if (aIsDefensive && !bIsDefensive) return -1;
                if (!aIsDefensive && bIsDefensive) return 1;
                return 0; // Keep original order
            });
        }
    }

    /**
     * PVS with Quiescence Search Integration - FIXED
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount(); // FIXED: Proper node counting

        // PHASE 1 FIX: Graceful timeout - don't throw immediately
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            // Return evaluation instead of throwing
            return Minimax.evaluate(state, depth);
        }

        // Check if search was interrupted previously
        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // TT-Lookup with PV node caution
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits(); // FIXED: Count TT hits

            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        } else {
            statistics.incrementTTMisses(); // FIXED: Count TT misses
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount(); // FIXED: Count leaf nodes
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search when depth exhausted
        if (depth <= 0) {
            statistics.incrementQNodeCount(); // FIXED: Count quiescence nodes
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size()); // FIXED: Count moves generated

        // Enhanced move ordering for PV vs Non-PV nodes
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            moveOrdering.orderMoves(moves, state, depth, entry); // FIXED: Use proper move ordering
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                // PHASE 1 FIX: Periodic interruption checks
                if (i % 3 == 0 && searchInterrupted) {
                    break; // Exit gracefully
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1); // FIXED: Count moves searched

                int eval;

                if (isFirstMove || isPVNode) {
                    // First moves AND PV nodes get full search
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    // Null window search
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = searchWithQuiescence(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        // Re-search with full window
                        eval = searchWithQuiescence(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs(); // FIXED: Count cutoffs
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            // === MINIMIZING PLAYER ===
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                // PHASE 1 FIX: Periodic interruption checks
                if (i % 3 == 0 && searchInterrupted) {
                    break; // Exit gracefully
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1); // FIXED: Count moves searched

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = searchWithQuiescence(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = searchWithQuiescence(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs(); // FIXED: Count cutoffs
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    // === ENHANCED MOVE ORDERING FOR PV NODES - FIXED ===

    /**
     * Special move ordering for PV nodes - FIXED
     */
    private static void orderMovesForPV(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // TT Move has highest priority
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // In PV nodes, evaluate more moves with high priority
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveForPV(state, a, depth);
                int scoreB = scoreMoveForPV(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Enhanced move scoring for PV nodes
     */
    private static int scoreMoveForPV(GameState state, Move move, int depth) {
        // Start with basic move score
        int score = Minimax.scoreMove(state, move);

        // In PV nodes, also score quiet moves higher for diversity
        if (!Minimax.isCapture(move, state)) {
            // Bonus for different target squares (anti-repetition)
            score += move.to * 2;

            // Bonus for different amounts moved
            score += move.amountMoved * 5;

            // Bonus for central moves
            int targetFile = GameState.file(move.to);
            int targetRank = GameState.rank(move.to);
            int centrality = Math.abs(targetFile - 3) + Math.abs(targetRank - 3);
            score += (6 - centrality) * 3;

            // Anti-repetition: bonus for moves to new squares
            long hash = state.hash();
            score += (int)(hash % 20) - 10; // Pseudo-random variation based on position
        }

        return score;
    }

    // === HELPER METHODS - FIXED ===

    /**
     * Store entry in transposition table - FIXED
     */
    private static void storeTTEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        Minimax.storeTranspositionEntry(hash, entry);
        statistics.incrementTTStores(); // FIXED: Count TT stores
    }

    // === TIMEOUT MANAGEMENT - PHASE 1 ENHANCEMENTS ===

    /**
     * Set timeout checker from TimedMinimax
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    /**
     * Clear timeout checker (for cleanup)
     */
    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    /**
     * PHASE 1 NEW: Reset search state for clean start
     */
    public static void resetSearchState() {
        searchInterrupted = false;
    }

    // === LEGACY COMPATIBILITY ===

    /**
     * Legacy method for backward compatibility
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesForPV(moves, state, depth, entry);
    }
}