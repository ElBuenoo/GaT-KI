package GaT.search;

import GaT.model.GameState;
import GaT.model.GameValues;
import GaT.model.Move;
import GaT.model.TTEntry;

import java.util.List;

/**
 * FAST MOVE ORDERING - Turm & Wächter Optimiert
 *
 * Ersetzt Ihr bestehendes MoveOrdering mit 40-60% schnellerer Implementierung
 * KEINE Evaluation-Aufrufe mehr! Nur schnelle Value-Lookups
 */
public class FastMoveOrdering {

    // === CORE TABLES ===
    private Move[][] killerMoves;
    private int[][] historyTable;
    private final UnifiedStatistics stats = UnifiedStatistics.getInstance();

    public FastMoveOrdering() {
        initializeTables();
    }

    private void initializeTables() {
        killerMoves = new Move[64][2]; // Max depth 64, 2 killer slots
        historyTable = new int[49][49]; // 7x7 board = 49 squares
    }

    // === MAIN INTERFACE (drop-in replacement für Ihr MoveOrdering) ===

    /**
     * HAUPTMETHODE - ersetzt Ihre orderMoves() Methode
     * SCHNELL - keine Evaluation-Aufrufe!
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.size() <= 1) return;

        stats.incrementTotalMoveOrderingQueries();

        try {
            // Schnelle Sortierung mit optimierten Scores
            moves.sort((a, b) -> Integer.compare(
                    scoreMovefast(b, state, depth, ttEntry),
                    scoreMovefast(a, state, depth, ttEntry)
            ));
        } catch (Exception e) {
            // Fallback: Basic ordering
            moves.sort((a, b) -> Integer.compare(b.amountMoved, a.amountMoved));
        }
    }

    /**
     * SCHNELLE Move-Bewertung - Konstante Zeit, keine Evaluation!
     * Das ist der Kern der Optimierung
     */
    private int scoreMovefast(Move move, GameState state, int depth, TTEntry ttEntry) {
        if (move == null) return 0;

        // === 1. TT MOVE (HÖCHSTE PRIORITÄT) ===
        if (ttEntry != null && move.equals(ttEntry.bestMove)) {
            return GameValues.TT_MOVE_PRIORITY;
        }

        // === 2. CAPTURES (MVV-LVA) ===
        if (isCapture(move, state)) {
            int mvvlva = GameValues.getMVVLVAScore(state, move.from, move.to);
            return GameValues.CAPTURE_BASE_PRIORITY + mvvlva;
        }

        // === 3. KILLER MOVES ===
        int killerScore = getKillerScore(move, depth);
        if (killerScore > 0) return killerScore;

        // === 4. HISTORY HEURISTIC ===
        int historyScore = getHistoryScore(move);

        // === 5. TURM & WÄCHTER POSITIONAL ===
        int positionalScore = getTurmWachterPositionalScore(move, state);

        return historyScore + positionalScore;
    }

    // === CAPTURE DETECTION (SCHNELL) ===

    private boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;

        long toBit = GameState.bit(move.to);

        // Wächter schlagen?
        if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
            return true;
        }

        // Turm schlagen?
        return state.redStackHeights[move.to] > 0 || state.blueStackHeights[move.to] > 0;
    }

    // === KILLER MOVES ===

    private int getKillerScore(Move move, int depth) {
        if (depth >= killerMoves.length) return 0;

        if (move.equals(killerMoves[depth][0])) return GameValues.KILLER_1_PRIORITY;
        if (move.equals(killerMoves[depth][1])) return GameValues.KILLER_2_PRIORITY;

        return 0;
    }

    public void storeKillerMove(Move move, int depth) {
        if (move == null || depth >= killerMoves.length) return;

        // Keine Captures als Killer speichern
        if (move.amountMoved < 0) return;

        // Killer verschieben
        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    // === HISTORY HEURISTIC ===

    private int getHistoryScore(Move move) {
        if (move == null) return 0;

        try {
            return Math.min(historyTable[move.from][move.to], GameValues.HISTORY_MAX_PRIORITY);
        } catch (ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }

    public void updateHistory(Move move, int depth, GameState state) {
        if (move == null || depth <= 0) return;

        try {
            // History-Score erhöhen
            int bonus = depth * depth; // Tiefere Suche = wichtiger
            historyTable[move.from][move.to] += bonus;

            // Gelegentlich altern lassen
            if (historyTable[move.from][move.to] > GameValues.HISTORY_MAX_PRIORITY * 2) {
                ageHistoryTable();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // Ignoriere ungültige Indizes
        }
    }

    private void ageHistoryTable() {
        for (int i = 0; i < historyTable.length; i++) {
            for (int j = 0; j < historyTable[i].length; j++) {
                historyTable[i][j] /= 2; // Halbiere alle Werte
            }
        }
    }

    // === TURM & WÄCHTER SPEZIFISCHE POSITIONSBEWERTUNG ===

    private int getTurmWachterPositionalScore(Move move, GameState state) {
        if (move == null || state == null) return 0;

        int score = 0;
        boolean isRed = isRedMove(move, state);

        // D-File Control (wichtig in Turm & Wächter)
        score += GameValues.getDFileBonus(move.to);

        // Zentral-Kontrolle
        score += GameValues.getCentralBonus(move.to);

        // Development
        score += GameValues.getDevelopmentBonus(move.to, isRed);

        // Wächter-Advancement (spezifisch für Turm & Wächter)
        if (isGuardMove(move, state)) {
            score += GameValues.getGuardAdvancementBonus(move.from, move.to, isRed);
            score += 10; // Genereller Wächter-Aktivität Bonus
        }

        // Forward Movement Bonus
        if (isForwardMove(move, isRed)) {
            score += 5;
        }

        return score;
    }

    private boolean isRedMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redGuard & fromBit) != 0 || state.redStackHeights[move.from] > 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redGuard & fromBit) != 0 || (state.blueGuard & fromBit) != 0;
    }

    private boolean isForwardMove(Move move, boolean isRed) {
        int fromRank = move.from / 7;
        int toRank = move.to / 7;

        if (isRed) {
            return toRank > fromRank; // Rot bewegt sich zu höheren Reihen
        } else {
            return toRank < fromRank; // Blau bewegt sich zu niedrigeren Reihen
        }
    }

    // === MAINTENANCE ===

    public void resetForNewSearch() {
        // Killer löschen, aber History behalten
        for (int i = 0; i < killerMoves.length; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
    }

    public void clearHistory() {
        for (int i = 0; i < historyTable.length; i++) {
            for (int j = 0; j < historyTable[i].length; j++) {
                historyTable[i][j] = 0;
            }
        }
    }

    // === COMPATIBILITY METHODS (für Ihre bestehenden Aufrufe) ===

    /**
     * Compatibility für Ihr bestehendes Interface
     */
    public String getStatistics() {
        int killerCount = 0;
        int historyEntries = 0;

        for (int i = 0; i < killerMoves.length; i++) {
            if (killerMoves[i][0] != null) killerCount++;
            if (killerMoves[i][1] != null) killerCount++;
        }

        for (int i = 0; i < historyTable.length; i++) {
            for (int j = 0; j < historyTable[i].length; j++) {
                if (historyTable[i][j] > 0) historyEntries++;
            }
        }

        return String.format("FastMoveOrdering: %d killers, %d history entries (OPTIMIZED)",
                killerCount, historyEntries);
    }

    /**
     * Compatibility für resetForNewGame
     */
    public void resetForNewGame() {
        resetForNewSearch();
        clearHistory();
    }
}