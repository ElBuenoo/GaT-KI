package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;
import GaT.evaluation.ThreatDetector;
import GaT.evaluation.StaticExchangeEvaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ENHANCED MINIMAX FACADE - Integrating all tactical improvements
 *
 * NEW FEATURES INTEGRATED:
 * ✅ Static Exchange Evaluation
 * ✅ Threat Detection System
 * ✅ Null Move Pruning
 * ✅ Late Move Reductions
 */
public class Minimax {

    // === CORE COMPONENTS (ENHANCED) ===
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH CONSTANTS ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === BACKWARD COMPATIBILITY ===
    public static int counter = 0;

    // === ENHANCED SEARCH INTERFACES ===

    /**
     * Find best move with enhanced tactical awareness
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        statistics.reset();
        statistics.startSearch();

        // Pre-search threat analysis
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        if (threats.mustDefend) {
            System.out.println("⚠️ Critical threats detected - defensive mode");
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        // Enhanced move ordering with threat awareness
        moveOrdering.orderMovesEnhanced(moves, state, depth,
                getTranspositionEntry(state.hash()), null, 180000);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // If under severe threat, prioritize defensive moves
        if (threats.mustDefend && !threats.defensiveMoves.isEmpty()) {
            System.out.println("📛 Analyzing " + threats.defensiveMoves.size() + " defensive moves first");

            // Evaluate defensive moves with higher priority
            for (Move move : threats.defensiveMoves) {
                if (!moves.contains(move)) continue;

                GameState copy = state.copy();
                copy.applyMove(move);

                int score = searchEngine.search(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, strategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }
            }
        }

        // Regular move evaluation
        for (Move move : moves) {
            // Skip if already evaluated as defensive move
            if (threats.mustDefend && threats.defensiveMoves.contains(move)) continue;

            // SEE pruning for bad captures at low depths
            if (depth <= 4 && isCapture(move, state)) {
                int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                if (seeValue < -100) {
                    System.out.println("✂️ SEE pruning move " + move + " (SEE: " + seeValue + ")");
                    continue;
                }
            }

            GameState copy = state.copy();
            copy.applyMove(move);

            int score = searchEngine.search(copy, depth - 1, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, !isRed, strategy);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        statistics.endSearch();
        counter = (int) statistics.getNodeCount();

        // Log tactical statistics
        System.out.println("🎯 Tactical Stats: " +
                statistics.getNullMoveCutoffs() + " null cutoffs, " +
                statistics.getLMRReductions() + " LMR reductions");

        return bestMove;
    }

    /**
     * Enhanced aspiration window search with tactical features
     */
    public static Move findBestMoveWithAspiration(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        statistics.reset();
        statistics.startSearch();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        TTEntry previousEntry = getTranspositionEntry(state.hash());

        // Enhanced move ordering
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        moveOrdering.orderMovesEnhanced(moves, state, depth, previousEntry, null, 180000);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Aspiration window setup
        int previousScore = 0;
        if (previousEntry != null && previousEntry.depth >= depth - 2) {
            previousScore = previousEntry.score;
            if (previousEntry.bestMove != null && moves.contains(previousEntry.bestMove)) {
                moves.remove(previousEntry.bestMove);
                moves.add(0, previousEntry.bestMove);
            }
        }

        int delta = 50;
        int alpha = previousScore - delta;
        int beta = previousScore + delta;

        boolean searchComplete = false;
        int failCount = 0;

        while (!searchComplete && failCount < 3) {
            try {
                bestMove = null;
                bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

                for (Move move : moves) {
                    // Enhanced SEE-based pruning
                    if (isCapture(move, state) && !isSafeCapture(move, state)) {
                        continue;
                    }

                    GameState copy = state.copy();
                    copy.applyMove(move);

                    int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, strategy);

                    if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                        bestScore = score;
                        bestMove = move;
                    }

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

        statistics.endSearch();
        counter = (int) statistics.getNodeCount();

        return bestMove;
    }

    // === TACTICAL ANALYSIS METHODS ===

    /**
     * Analyze tactical complexity of position
     */
    public static int analyzeTacticalComplexity(GameState state) {
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        int complexity = 0;

        // Threat complexity
        complexity += threats.immediateThreats.size() * 10;
        complexity += threats.potentialThreats.size() * 5;
        complexity += threats.threatLevel * 5;

        // Capture complexity
        int captures = 0;
        int goodCaptures = 0;

        for (Move move : moves) {
            if (isCapture(move, state)) {
                captures++;
                if (StaticExchangeEvaluator.isSafeCapture(state, move)) {
                    goodCaptures++;
                }
            }
        }

        complexity += captures * 3;
        complexity += goodCaptures * 2;

        return complexity;
    }

    /**
     * Check if position requires tactical search
     */
    public static boolean isTacticalPosition(GameState state) {
        // Quick tactical check
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

        if (threats.mustDefend || threats.inCheck) {
            return true;
        }

        if (threats.immediateThreats.size() >= 2) {
            return true;
        }

        // Check for hanging pieces
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (isCapture(move, state)) {
                int seeValue = StaticExchangeEvaluator.evaluate(state, move);
                if (seeValue > 100) {
                    return true; // Free piece available
                }
            }
        }

        return false;
    }

    /**
     * Enhanced capture checking with SEE
     */
    public static boolean isSafeCapture(Move move, GameState state) {
        return StaticExchangeEvaluator.isSafeCapture(state, move);
    }

    /**
     * Get capture value using SEE
     */
    public static int getCaptureValue(Move move, GameState state) {
        return StaticExchangeEvaluator.evaluate(state, move);
    }

    // === ENHANCED EVALUATION WITH TACTICS ===

    /**
     * Evaluate with tactical awareness
     */
    public static int evaluateTactical(GameState state, int depth) {
        int baseEval = evaluator.evaluate(state, depth);

        // Add tactical adjustments
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);

        if (threats.inCheck) {
            // Penalty for being in check
            baseEval += state.redToMove ? -50 : 50;
        }

        // Adjust for threat level
        int threatAdjustment = threats.threatLevel * 10;
        baseEval += state.redToMove ? -threatAdjustment : threatAdjustment;

        return baseEval;
    }

    // === LEGACY COMPATIBILITY METHODS ===

    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS);
    }

    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        return searchEngine.searchWithTimeout(state, depth, alpha, beta, maximizingPlayer,
                SearchConfig.SearchStrategy.ALPHA_BETA, timeoutCheck);
    }

    // === EVALUATION INTERFACE ===

    public static int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    public static void setRemainingTime(long timeMs) {
        Evaluator.setRemainingTime(timeMs);
        QuiescenceSearch.setRemainingTime(timeMs);
    }

    // === GAME STATE ANALYSIS ===

    public static boolean isGameOver(GameState state) {
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;

        return blueGuardOnD7 || redGuardOnD1;
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

    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        return evaluator.isGuardInDanger(state, checkRed);
    }

    // === ENHANCED MOVE SCORING ===

    public static int scoreMove(GameState state, Move move) {
        if (state == null) {
            return move.amountMoved;
        }

        int score = 0;
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Winning moves
        boolean entersCastle = (isRed && move.to == BLUE_CASTLE_INDEX) ||
                (!isRed && move.to == RED_CASTLE_INDEX);

        if (entersCastle && isGuardMove(move, state)) {
            score += 10000;
        }

        // Enhanced capture scoring with SEE
        if (isCapture(move, state)) {
            int seeValue = StaticExchangeEvaluator.evaluate(state, move);
            score += 1000 + seeValue;
        }

        // Stacking
        boolean stacksOnOwn = ((isRed ? state.redTowers : state.blueTowers) & toBit) != 0;
        if (stacksOnOwn) score += 10;

        score += move.amountMoved;
        return score;
    }

    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move);

        // Threat evasion bonus
        ThreatDetector.ThreatAnalysis threats = ThreatDetector.analyzeThreats(state);
        if (threats.mustDefend) {
            for (Move defensiveMove : threats.defensiveMoves) {
                if (move.equals(defensiveMove)) {
                    score += 1500;
                    break;
                }
            }
        }

        // Guard escape bonus
        if (isGuardMove(move, state)) {
            boolean guardInDanger = evaluator.isGuardInDanger(state, state.redToMove);
            if (guardInDanger) {
                score += 1500;
            }
        }

        return score;
    }

    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
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

    // === MOVE ORDERING INTERFACE ===

    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        moveOrdering.orderMoves(moves, state, depth, entry);
    }

    public static void storeKillerMove(Move move, int depth) {
        moveOrdering.storeKillerMove(move, depth);
    }

    public static void resetKillerMoves() {
        moveOrdering.resetKillerMoves();
    }

    // === SEARCH STATISTICS ===

    public static void resetPruningStats() {
        statistics.reset();
    }

    public static String getSearchStatistics() {
        return statistics.getSummary();
    }

    public static void setTimeoutChecker(BooleanSupplier checker) {
        searchEngine.setTimeoutChecker(checker);
    }

    public static void clearTimeoutChecker() {
        searchEngine.clearTimeoutChecker();
    }

    // === HELPER CLASSES ===

    private static class AspirationFailException extends RuntimeException {}

    // === COMPONENT ACCESS ===

    public static Evaluator getEvaluator() {
        return evaluator;
    }

    public static SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public static MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    public static TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    public static SearchConfig.SearchStrategy[] getAllStrategies() {
        return SearchConfig.SearchStrategy.values();
    }

    public static SearchConfig.SearchStrategy getStrategyByName(String name) {
        try {
            return SearchConfig.SearchStrategy.valueOf(name);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Unknown strategy: " + name + ", defaulting to ALPHA_BETA");
            return SearchConfig.SearchStrategy.ALPHA_BETA;
        }
    }
}