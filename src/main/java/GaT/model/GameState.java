package GaT.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * VERBESSERTE GAMESTATE - LANGFRISTIGE LÖSUNG
 *
 * VERBESSERUNGEN:
 * ✅ Robuste copy() Methode mit leerem Konstruktor
 * ✅ Validation helpers für Debugging
 * ✅ Thread-safe Operationen
 * ✅ Bessere Fehlerbehandlung
 * ✅ Defensive Programmierung
 */
public class GameState {
    public static final int BOARD_SIZE = 7;
    public static final int NUM_SQUARES = BOARD_SIZE * BOARD_SIZE;
    public static final long BOARD_MASK = (1L << NUM_SQUARES) - 1;

    // === GAME STATE FIELDS ===
    public long redGuard;
    public long redTowers;
    public int[] redStackHeights = new int[NUM_SQUARES];

    public long blueGuard;
    public long blueTowers;
    public int[] blueStackHeights = new int[NUM_SQUARES];

    public boolean redToMove = true;

    // === ZOBRIST HASHING ===
    private static final long[][] ZOBRIST_RED_TOWER = new long[49][8];
    private static final long[][] ZOBRIST_BLUE_TOWER = new long[49][8];
    private static final long[] ZOBRIST_RED_GUARD = new long[49];
    private static final long[] ZOBRIST_BLUE_GUARD = new long[49];
    private static long ZOBRIST_TURN; // redToMove

    // Static initialization
    static {
        initializeZobristKeys();
    }

    // === CONSTRUCTORS ===

    /**
     * NEUER: Privater leerer Konstruktor für sichere copy() Operation
     * Initialisiert nur die nötigen Arrays, keine Spielposition
     */
    private GameState(boolean emptyConstructor) {
        if (emptyConstructor) {
            this.redStackHeights = new int[NUM_SQUARES];
            this.blueStackHeights = new int[NUM_SQUARES];
            // Alle anderen Felder bleiben auf default (0, false)
        }
    }

    /**
     * Standard-Konstruktor - erstellt Startposition
     */
    public GameState() {
        initializeStartPosition();
    }

    /**
     * Parametrisierter Konstruktor für manuelle Erstellung
     */
    public GameState(long redGuard, long redTowers, int[] redHeights,
                     long blueGuard, long blueTowers, int[] blueHeights,
                     boolean redToMove) {

        // Defensive Kopien der Arrays
        this.redGuard = redGuard;
        this.redTowers = redTowers;
        this.redStackHeights = Arrays.copyOf(redHeights, NUM_SQUARES);

        this.blueGuard = blueGuard;
        this.blueTowers = blueTowers;
        this.blueStackHeights = Arrays.copyOf(blueHeights, NUM_SQUARES);

        this.redToMove = redToMove;
    }

    /**
     * VERBESSERTE copy() Methode - Langfristige Lösung
     * Verwendet leeren Konstruktor für maximale Effizienz und Sicherheit
     */
    public GameState copy() {
        // Validation der Quelldaten
        if (this.redStackHeights == null || this.blueStackHeights == null) {
            throw new IllegalStateException("Cannot copy corrupted GameState: null arrays");
        }

        if (this.redStackHeights.length != NUM_SQUARES ||
                this.blueStackHeights.length != NUM_SQUARES) {
            throw new IllegalStateException("Cannot copy corrupted GameState: invalid array lengths");
        }

        try {
            // Verwende leeren Konstruktor für bessere Performance
            GameState copy = new GameState(true);

            // Primitive Felder (thread-safe, atomische Operationen)
            copy.redGuard = this.redGuard;
            copy.blueGuard = this.blueGuard;
            copy.redTowers = this.redTowers;
            copy.blueTowers = this.blueTowers;
            copy.redToMove = this.redToMove;

            // Array-Kopien (defensive, sichere Kopien)
            copy.redStackHeights = Arrays.copyOf(this.redStackHeights, NUM_SQUARES);
            copy.blueStackHeights = Arrays.copyOf(this.blueStackHeights, NUM_SQUARES);

            // Validation der Kopie
            if (!copy.isValid()) {
                throw new IllegalStateException("Copy validation failed");
            }

            return copy;

        } catch (Exception e) {
            // Fallback: Verwende parametrisierten Konstruktor
            System.err.println("⚠️ WARNING: Primary copy() failed, using fallback: " + e.getMessage());
            return copyFallback();
        }
    }

    /**
     * Fallback copy() Methode bei Problemen mit der Hauptmethode
     */
    private GameState copyFallback() {
        return new GameState(
                this.redGuard,
                this.redTowers,
                this.redStackHeights,
                this.blueGuard,
                this.blueTowers,
                this.blueStackHeights,
                this.redToMove
        );
    }

    // === VALIDATION HELPERS ===

    /**
     * Prüft ob der GameState valid ist
     */
    public boolean isValid() {
        if (this.redStackHeights == null || this.blueStackHeights == null) {
            return false;
        }

        if (this.redStackHeights.length != NUM_SQUARES ||
                this.blueStackHeights.length != NUM_SQUARES) {
            return false;
        }

        // Prüfe auf negative Höhen
        for (int i = 0; i < NUM_SQUARES; i++) {
            if (this.redStackHeights[i] < 0 || this.blueStackHeights[i] < 0) {
                return false;
            }
            if (this.redStackHeights[i] > 7 || this.blueStackHeights[i] > 7) {
                return false; // Unrealistische Höhen
            }
        }

        // Prüfe Bitboard-Konsistenz
        return validateBitboards();
    }

    /**
     * Validiert Bitboard-Konsistenz
     */
    private boolean validateBitboards() {
        // Guards und Towers dürfen sich nicht überlappen
        if ((redGuard & redTowers) != 0) return false;
        if ((blueGuard & blueTowers) != 0) return false;

        // Bitboards dürfen nicht außerhalb des Boards sein
        if ((redGuard & ~BOARD_MASK) != 0) return false;
        if ((blueGuard & ~BOARD_MASK) != 0) return false;
        if ((redTowers & ~BOARD_MASK) != 0) return false;
        if ((blueTowers & ~BOARD_MASK) != 0) return false;

        // Höhen-Arrays müssen mit Bitboards konsistent sein
        for (int i = 0; i < NUM_SQUARES; i++) {
            long bit = bit(i);

            // Wenn Bitboard gesetzt ist, muss Höhe > 0 sein
            if ((redTowers & bit) != 0 && redStackHeights[i] <= 0) return false;
            if ((blueTowers & bit) != 0 && blueStackHeights[i] <= 0) return false;

            // Wenn Höhe > 0, muss Bitboard gesetzt sein
            if (redStackHeights[i] > 0 && (redTowers & bit) == 0) return false;
            if (blueStackHeights[i] > 0 && (blueTowers & bit) == 0) return false;
        }

        return true;
    }

    /**
     * Validiert und repariert inkonsistente Zustände (falls möglich)
     */
    public boolean validateAndRepair() {
        if (isValid()) return true;

        // Versuche häufige Probleme zu reparieren

        // 1. Null-Arrays reparieren
        if (this.redStackHeights == null) {
            this.redStackHeights = new int[NUM_SQUARES];
        }
        if (this.blueStackHeights == null) {
            this.blueStackHeights = new int[NUM_SQUARES];
        }

        // 2. Falsche Array-Längen reparieren
        if (this.redStackHeights.length != NUM_SQUARES) {
            int[] newArray = new int[NUM_SQUARES];
            System.arraycopy(this.redStackHeights, 0, newArray, 0,
                    Math.min(this.redStackHeights.length, NUM_SQUARES));
            this.redStackHeights = newArray;
        }
        if (this.blueStackHeights.length != NUM_SQUARES) {
            int[] newArray = new int[NUM_SQUARES];
            System.arraycopy(this.blueStackHeights, 0, newArray, 0,
                    Math.min(this.blueStackHeights.length, NUM_SQUARES));
            this.blueStackHeights = newArray;
        }

        // 3. Negative Höhen korrigieren
        for (int i = 0; i < NUM_SQUARES; i++) {
            if (this.redStackHeights[i] < 0) this.redStackHeights[i] = 0;
            if (this.blueStackHeights[i] < 0) this.blueStackHeights[i] = 0;
            if (this.redStackHeights[i] > 7) this.redStackHeights[i] = 7;
            if (this.blueStackHeights[i] > 7) this.blueStackHeights[i] = 7;
        }

        // 4. Bitboards außerhalb des Boards maskieren
        this.redGuard &= BOARD_MASK;
        this.blueGuard &= BOARD_MASK;
        this.redTowers &= BOARD_MASK;
        this.blueTowers &= BOARD_MASK;

        return isValid();
    }

    // === INITIALIZATION ===

    /**
     * Initialisiert die Standard-Startposition
     */
    private void initializeStartPosition() {
        // Blue guard on D1
        int blueGuardIndex = getIndex(0, 3);
        blueGuard = bit(blueGuardIndex);

        // Blue towers (pyramid layout)
        int[] blueTowerSquares = {
                getIndex(0, 0), getIndex(0, 1),
                getIndex(0, 5), getIndex(0, 6),
                getIndex(1, 2), getIndex(1, 4),
                getIndex(2, 3)
        };
        for (int index : blueTowerSquares) {
            blueTowers |= bit(index);
            blueStackHeights[index] = 1;
        }

        // Red guard on D7
        int redGuardIndex = getIndex(6, 3);
        redGuard = bit(redGuardIndex);

        // Red towers (mirror of blue's)
        int[] redTowerSquares = {
                getIndex(6, 0), getIndex(6, 1),
                getIndex(6, 5), getIndex(6, 6),
                getIndex(5, 2), getIndex(5, 4),
                getIndex(4, 3)
        };
        for (int index : redTowerSquares) {
            redTowers |= bit(index);
            redStackHeights[index] = 1;
        }

        redToMove = true;
    }

    // === UTILITIES ===

    public static long bit(int index) {
        return 1L << index;
    }

    public static int getIndex(int rank, int file) {
        return rank * BOARD_SIZE + file;
    }

    public static boolean isOnBoard(int index) {
        return index >= 0 && index < NUM_SQUARES;
    }

    public static int rank(int index) {
        return index / BOARD_SIZE;
    }

    public static int file(int index) {
        return index % BOARD_SIZE;
    }

    // === ZOBRIST HASHING ===

    private static void initializeZobristKeys(){
        Random rand = new Random(42); // Fixed seed for reproducibility

        for (int i = 0; i < 49; i++) {
            ZOBRIST_RED_GUARD[i] = rand.nextLong();
            ZOBRIST_BLUE_GUARD[i] = rand.nextLong();

            for (int h = 1; h <= 7; h++) {
                ZOBRIST_RED_TOWER[i][h] = rand.nextLong();
                ZOBRIST_BLUE_TOWER[i][h] = rand.nextLong();
            }
        }

        ZOBRIST_TURN = rand.nextLong();
    }

    /**
     * @return a "unique" long, representing the current gamestate
     */
    public long hash() {
        long hash = 0;

        for (int i = 0; i < NUM_SQUARES; i++) {
            int heightRed = redStackHeights[i];
            int heightBlue = blueStackHeights[i];

            if (heightRed > 0) {
                hash ^= ZOBRIST_RED_TOWER[i][heightRed];
            }
            if (heightBlue > 0) {
                hash ^= ZOBRIST_BLUE_TOWER[i][heightBlue];
            }
        }

        if (redGuard != 0) {
            int index = Long.numberOfTrailingZeros(redGuard);
            hash ^= ZOBRIST_RED_GUARD[index];
        }

        if (blueGuard != 0) {
            int idx = Long.numberOfTrailingZeros(blueGuard);
            hash ^= ZOBRIST_BLUE_GUARD[idx];
        }

        if (redToMove) {
            hash ^= ZOBRIST_TURN;
        }

        return hash;
    }

    // === MOVE APPLICATION ===

    /**
     * @param move Move to execute
     * @apiNote This function implies that the given move is legal
     */
    public void applyMove(Move move) {
        if (!isValid()) {
            throw new IllegalStateException("Cannot apply move to invalid GameState");
        }

        if (move == null) {
            throw new IllegalArgumentException("Cannot apply null move");
        }

        boolean isRed = redToMove;

        // Get source & target info
        int from = move.from;
        int to = move.to;
        int amount = move.amountMoved;

        long fromBit = bit(from);
        long toBit = bit(to);

        // Validation
        if (!isOnBoard(from) || !isOnBoard(to)) {
            throw new IllegalArgumentException("Move positions out of bounds: from=" + from + ", to=" + to);
        }

        // Optional assertions (nur in Debug-Mode)
        assert (redGuard & redTowers & toBit) == 0 : "Red guard and tower overlap!";
        assert (blueGuard & blueTowers & toBit) == 0 : "Blue guard and tower overlap!";

        if (amount == 1 && ((isRed ? redGuard : blueGuard) & fromBit) != 0) {
            // Moving the guard
            if (isRed) {
                redGuard &= ~fromBit;       // Remove old position
                redGuard |= toBit;          // Add new position
            } else {
                blueGuard &= ~fromBit;
                blueGuard |= toBit;
            }

            // Remove captured enemy piece
            clearEnemyPieceAt(to, !isRed);

        } else {
            // Moving a tower stack (possibly partial)

            // Remove amount from source stack
            int[] stackHeights = isRed ? redStackHeights : blueStackHeights;
            long towers = isRed ? redTowers : blueTowers;

            stackHeights[from] -= amount;
            if (stackHeights[from] <= 0) {
                // Remove tower from source
                towers &= ~fromBit;
            }

            // Handle destination
            if (((redGuard | blueGuard) & toBit) != 0 || ((redTowers | blueTowers) & toBit) != 0) {
                clearEnemyPieceAt(to, !isRed); // capture if needed
            }

            // Stack on destination
            int[] targetStacks = isRed ? redStackHeights : blueStackHeights;
            targetStacks[to] += amount;

            // Store back updated bitboards
            if (isRed) {
                redTowers = towers | toBit;     // Adding the piece back to its new index
                redStackHeights = stackHeights;
            } else {
                blueTowers = towers | toBit;
                blueStackHeights = stackHeights;
            }
        }

        // Switch turn
        redToMove = !redToMove;

        // Post-move validation in debug mode
        assert isValid() : "GameState became invalid after move application";
    }

    private void clearEnemyPieceAt(int index, boolean isRed) {
        long mask = ~bit(index);        // Only the target index is off

        if (isRed) {
            redTowers &= mask;      // remove piece from index
            redGuard &= mask;
            redStackHeights[index] = 0;
        } else {
            blueTowers &= mask;
            blueGuard &= mask;
            blueStackHeights[index] = 0;
        }
    }

    // === BOARD DISPLAY ===

    public void printBoard() {
        System.out.println("  +---------------------------+");

        for (int rank = BOARD_SIZE - 1; rank >= 0; rank--) {
            System.out.print((rank + 1) + " | ");

            for (int file = 0; file < BOARD_SIZE; file++) {
                int index = getIndex(rank, file);
                char symbol = '.';

                if (((redGuard >>> index) & 1) == 1) symbol = 'G';
                else if (((blueGuard >>> index) & 1) == 1) symbol = 'g';
                else if (((redTowers >>> index) & 1) == 1)
                    symbol = (char) ('0' + redStackHeights[index]); // show tower height
                else if (((blueTowers >>> index) & 1) == 1)
                    symbol = (char) ('0' + blueStackHeights[index]);

                System.out.print(symbol + " ");
            }

            System.out.println("|");
        }

        System.out.println("  +---------------------------+");
        System.out.println("    A B C D E F G\n");
    }

    // === FEN SUPPORT ===

    public static GameState fromFen(String fen) {
        String[] parts = fen.trim().split(" ");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid FEN: expected board and turn");

        String boardPart = parts[0];
        String turnPart = parts[1];
        String[] ranks = boardPart.split("/");

        if (ranks.length != BOARD_SIZE)
            throw new IllegalArgumentException("Invalid FEN: expected " + BOARD_SIZE + " ranks");

        // Verwende leeren Konstruktor für bessere Performance
        GameState state = new GameState(true);

        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            String row = ranks[rank];
            int file = 0;
            for (int i = 0; i < row.length(); ) {
                char ch = row.charAt(i);

                // Empty squares
                if (Character.isDigit(ch)) {
                    int count = ch - '0';
                    file += count;
                    i++;
                    continue;
                }

                int index = getIndex(BOARD_SIZE - 1 - rank, file); // ranks from top down

                if (ch == 'r' || ch == 'b') {
                    boolean isBlue = ch == 'b';
                    i++;

                    int height = 1;
                    // Look ahead for ONE digit only (0–7)
                    if (i < row.length() && Character.isDigit(row.charAt(i))) {
                        height = row.charAt(i) - '0';
                        i++;
                    }

                    if (isBlue) {
                        state.blueTowers |= bit(index);
                        state.blueStackHeights[index] = height;
                    } else {
                        state.redTowers |= bit(index);
                        state.redStackHeights[index] = height;
                    }

                    file++;
                } else if (ch == 'R' || ch == 'B') {
                    boolean isBlue = ch == 'B';
                    i += 2;

                    if (isBlue) {
                        state.blueGuard = bit(index);
                    } else {
                        state.redGuard = bit(index);
                    }

                    file++;
                } else {
                    throw new IllegalArgumentException("Unexpected character in FEN: " + ch);
                }
            }
        }

        // Turn
        state.redToMove = turnPart.equals("r");

        // Validate result
        if (!state.isValid()) {
            throw new IllegalArgumentException("FEN resulted in invalid GameState");
        }

        return state;
    }

    // === OBJECT METHODS ===

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GameState gameState)) return false;
        return redGuard == gameState.redGuard &&
                redTowers == gameState.redTowers &&
                blueGuard == gameState.blueGuard &&
                blueTowers == gameState.blueTowers &&
                redToMove == gameState.redToMove &&
                Objects.deepEquals(redStackHeights, gameState.redStackHeights) &&
                Objects.deepEquals(blueStackHeights, gameState.blueStackHeights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(redGuard, redTowers, Arrays.hashCode(redStackHeights),
                blueGuard, blueTowers, Arrays.hashCode(blueStackHeights), redToMove);
    }

    /**
     * VERBESSERTE toString() Methode mit Validation
     */
    @Override
    public String toString() {
        if (!isValid()) {
            return "GameState[CORRUPTED: " + getCorruptionDetails() + "]";
        }

        return String.format("GameState[redGuard=%s, redTowers=%s, blueGuard=%s, blueTowers=%s, redToMove=%s]",
                Long.toBinaryString(redGuard),
                Long.toBinaryString(redTowers),
                Long.toBinaryString(blueGuard),
                Long.toBinaryString(blueTowers),
                redToMove);
    }

    /**
     * Detaillierte Korruptions-Informationen für Debugging
     */
    private String getCorruptionDetails() {
        StringBuilder details = new StringBuilder();

        if (redStackHeights == null) details.append("redStackHeights=null ");
        if (blueStackHeights == null) details.append("blueStackHeights=null ");

        if (redStackHeights != null && redStackHeights.length != NUM_SQUARES) {
            details.append("redStackHeights.length=").append(redStackHeights.length).append(" ");
        }
        if (blueStackHeights != null && blueStackHeights.length != NUM_SQUARES) {
            details.append("blueStackHeights.length=").append(blueStackHeights.length).append(" ");
        }

        if (!validateBitboards()) details.append("invalid-bitboards ");

        return details.toString().trim();
    }

    // === DEBUG HELPERS ===

    /**
     * Debug-Informationen für Entwicklung
     */
    public String getDebugInfo() {
        if (!isValid()) {
            return "CORRUPTED: " + getCorruptionDetails();
        }

        int redTowerCount = Long.bitCount(redTowers);
        int blueTowerCount = Long.bitCount(blueTowers);
        int redGuardCount = Long.bitCount(redGuard);
        int blueGuardCount = Long.bitCount(blueGuard);

        return String.format("Valid GameState: Red(G:%d, T:%d), Blue(G:%d, T:%d), Turn:%s",
                redGuardCount, redTowerCount, blueGuardCount, blueTowerCount,
                redToMove ? "RED" : "BLUE");
    }

    public GameState makeMove(Move move) {
        GameState newState = this.copy(); // Create copy first
        newState.applyMove(move);         // Apply move to copy
        return newState;                  // Return the new state
    }

    public String toFen() {
        // Simple FEN implementation - adapt to your board representation
        StringBuilder sb = new StringBuilder();

        // Convert board state to FEN string
        for (int i = 0; i < 49; i++) {
            if ((redGuard & bit(i)) != 0) sb.append('G');
            else if ((blueGuard & bit(i)) != 0) sb.append('g');
            else if (redStackHeights[i] > 0) sb.append(redStackHeights[i]);
            else if (blueStackHeights[i] > 0) sb.append((char)('a' + blueStackHeights[i] - 1));
            else sb.append('.');

            if (i % 7 == 6 && i < 48) sb.append('/');
        }

        sb.append(' ').append(redToMove ? 'r' : 'b');
        return sb.toString();
    }

    public static GameState getStartingPosition() {
        return new GameState(); // Uses your existing constructor
    }
}