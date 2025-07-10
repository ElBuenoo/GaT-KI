package GaT.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public class GameState {
    public static final int BOARD_SIZE = 7;
    public static final int NUM_SQUARES = BOARD_SIZE * BOARD_SIZE;
    public static final long BOARD_MASK = (1L << NUM_SQUARES) - 1;

    public long redGuard;
    public long redTowers;
    public int[] redStackHeights = new int[NUM_SQUARES];

    public long blueGuard;
    public long blueTowers;
    public int[] blueStackHeights = new int[NUM_SQUARES];

    public boolean redToMove = true;

    private static final long[][] ZOBRIST_RED_TOWER = new long[49][8];
    private static final long[][] ZOBRIST_BLUE_TOWER = new long[49][8];
    private static final long[] ZOBRIST_RED_GUARD = new long[49];
    private static final long[] ZOBRIST_BLUE_GUARD = new long[49];
    private static long ZOBRIST_TURN; // redToMove

    // static Syntax to Init static variables outside a non-static function
    static {
        initializeZobristKeys();
    }

    // Utilities
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

    // ADDED: Missing method for HangingPieceDetector
    public static String squareToString(int index) {
        if (!isOnBoard(index)) return "INVALID";
        int rank = rank(index);
        int file = file(index);
        char fileChar = (char)('A' + file);
        return "" + fileChar + (rank + 1);
    }

    //initializes the Random Keys for the Zobrest-Hashing
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


    /**
     * @param move Move to execute
     * @apiNote This function implies that the given move is legal
     */
    public void applyMove(Move move) {
        boolean isRed = redToMove;

        // Get source & target info
        int from = move.from;
        int to = move.to;
        int amount = move.amountMoved;

        long fromBit = bit(from);
        long toBit = bit(to);

        //Optional but may help at one point
        assert (redGuard & redTowers & toBit) == 0 : "Red guard and tower overlap!";
        assert (blueGuard & blueTowers & toBit) == 0 : "Blue guard and tower overlap!";

        if (amount == 1 && ((isRed ? redGuard : blueGuard) & fromBit) != 0) {
            // Moving the guard
            if (isRed) {
                redGuard &= ~fromBit;       //Remove old position
                redGuard |= toBit;          //Add new position
            } else {
                blueGuard &= ~fromBit;
                blueGuard |= toBit;
            }

            // Remove captured enemy piece (checking if it is free before would take the same time so we just clear it)
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
                redTowers = towers | toBit;     //Adding the piece back to its new index
                redStackHeights = stackHeights;
            } else {
                blueTowers = towers | toBit;
                blueStackHeights = stackHeights;
            }
        }

        // Switch turn
        redToMove = !redToMove;
    }

    private void clearEnemyPieceAt(int index, boolean isRed) {
        long mask = ~bit(index);        //Only the target index is off

        if (isRed) {
            redTowers &= mask;      //remove piece from index
            redGuard &= mask;
            redStackHeights[index] = 0;
        } else {
            blueTowers &= mask;
            blueGuard &= mask;
            blueStackHeights[index] = 0;
        }
    }



    public GameState() {
        // White guard on D1
        int blueGuardIndex = getIndex(0, 3);
        blueGuard = bit(blueGuardIndex);

        // White towers (pyramid layout)
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

        // Black guard on D7
        int redGuardIndex = getIndex(6, 3);
        redGuard = bit(redGuardIndex);

        // Black towers (mirror of white's)
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



    public GameState(long redGuard, long redTowers, int[] redHeights,
                     long blueGuard, long blueTowers, int[] blackHeights,
                     boolean redToMove) {
        this.redGuard = redGuard;
        this.redTowers = redTowers;
        this.redStackHeights = Arrays.copyOf(redHeights, NUM_SQUARES);

        this.blueGuard = blueGuard;
        this.blueTowers = blueTowers;
        this.blueStackHeights = Arrays.copyOf(blackHeights, NUM_SQUARES);

        this.redToMove = redToMove;
    }

    public GameState copy() {
        GameState copy = new GameState();
        copy.redGuard = this.redGuard;
        copy.blueGuard = this.blueGuard;
        copy.redTowers = this.redTowers;
        copy.blueTowers = this.blueTowers;
        copy.redToMove = this.redToMove;
        copy.redStackHeights = Arrays.copyOf(this.redStackHeights, NUM_SQUARES);
        copy.blueStackHeights = Arrays.copyOf(this.blueStackHeights, NUM_SQUARES);
        return copy;
    }


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


    public static GameState fromFen(String fen) {
        String[] parts = fen.trim().split(" ");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid FEN: expected board and turn");

        String boardPart = parts[0];
        String turnPart = parts[1];
        String[] ranks = boardPart.split("/");

        if (ranks.length != BOARD_SIZE)
            throw new IllegalArgumentException("Invalid FEN: expected " + BOARD_SIZE + " ranks");

        GameState state = new GameState();

        // Reset everything
        state.redGuard = 0;
        state.blueGuard = 0;
        state.redTowers = 0;
        state.blueTowers = 0;
        Arrays.fill(state.redStackHeights, 0);
        Arrays.fill(state.blueStackHeights, 0);

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
                    i +=2;

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

        return state;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GameState gameState)) return false;
        return redGuard == gameState.redGuard && redTowers == gameState.redTowers && blueGuard == gameState.blueGuard && blueTowers == gameState.blueTowers && redToMove == gameState.redToMove && Objects.deepEquals(redStackHeights, gameState.redStackHeights) && Objects.deepEquals(blueStackHeights, gameState.blueStackHeights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(redGuard, redTowers, Arrays.hashCode(redStackHeights), blueGuard, blueTowers, Arrays.hashCode(blueStackHeights), redToMove);
    }
}