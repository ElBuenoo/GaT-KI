package gui;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Board panel for GUI display
 */
public class BoardPanel extends JPanel {
    private static final int BOARD_SIZE = 7;
    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_OFFSET = 40;

    private GameState state;
    private Consumer<Move> moveHandler;
    private int selectedSquare = -1;
    private List<Move> possibleMoves = null;

    // Colors
    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);
    private static final Color SELECTED_COLOR = new Color(255, 255, 0, 128);
    private static final Color POSSIBLE_MOVE_COLOR = new Color(0, 255, 0, 64);
    private static final Color RED_COLOR = new Color(220, 20, 60);
    private static final Color BLUE_COLOR = new Color(30, 144, 255);

    public BoardPanel(GameState initialState, Consumer<Move> moveHandler) {
        this.state = initialState;
        this.moveHandler = moveHandler;

        setPreferredSize(new Dimension(
                BOARD_SIZE * SQUARE_SIZE + 2 * BOARD_OFFSET,
                BOARD_SIZE * SQUARE_SIZE + 2 * BOARD_OFFSET
        ));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
    }

    public void updatePosition(GameState newState) {
        this.state = newState;
        this.selectedSquare = -1;
        this.possibleMoves = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw board
        drawBoard(g2);

        // Draw pieces
        drawPieces(g2);

        // Draw selection and possible moves
        if (selectedSquare >= 0) {
            drawSelection(g2);
        }

        // Draw coordinates
        drawCoordinates(g2);
    }

    private void drawBoard(Graphics2D g) {
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {
                int x = BOARD_OFFSET + file * SQUARE_SIZE;
                int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE;

                // Alternate colors
                if ((rank + file) % 2 == 0) {
                    g.setColor(LIGHT_SQUARE);
                } else {
                    g.setColor(DARK_SQUARE);
                }
                g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

                // Castle squares
                if ((rank == 0 && file == 3) || (rank == 6 && file == 3)) {
                    g.setColor(new Color(255, 215, 0, 64)); // Gold overlay
                    g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);
                }
            }
        }
    }

    private void drawPieces(Graphics2D g) {
        if (state == null) return;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);
            int file = GameState.file(i);
            int x = BOARD_OFFSET + file * SQUARE_SIZE + SQUARE_SIZE / 2;
            int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE + SQUARE_SIZE / 2;

            // Draw guards
            if ((state.redGuard & GameState.bit(i)) != 0) {
                drawGuard(g, x, y, RED_COLOR);
            } else if ((state.blueGuard & GameState.bit(i)) != 0) {
                drawGuard(g, x, y, BLUE_COLOR);
            }

            // Draw towers
            if (state.redStackHeights[i] > 0) {
                drawTower(g, x, y, RED_COLOR, state.redStackHeights[i]);
            } else if (state.blueStackHeights[i] > 0) {
                drawTower(g, x, y, BLUE_COLOR, state.blueStackHeights[i]);
            }
        }
    }

    private void drawGuard(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.fillOval(x - 30, y - 30, 60, 60);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawOval(x - 30, y - 30, 60, 60);

        // Draw crown symbol
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("♔", x - 12, y + 8);
    }

    private void drawTower(Graphics2D g, int x, int y, Color color, int height) {
        g.setColor(color);
        int size = 20 + height * 5;
        g.fillRect(x - size/2, y - size/2, size, size);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRect(x - size/2, y - size/2, size, size);

        // Draw height number
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString(String.valueOf(height), x - 5, y + 5);
    }

    private void drawSelection(Graphics2D g) {
        if (selectedSquare < 0) return;

        int rank = GameState.rank(selectedSquare);
        int file = GameState.file(selectedSquare);
        int x = BOARD_OFFSET + file * SQUARE_SIZE;
        int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE;

        g.setColor(SELECTED_COLOR);
        g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

        // Draw possible moves
        if (possibleMoves != null) {
            g.setColor(POSSIBLE_MOVE_COLOR);
            for (Move move : possibleMoves) {
                if (move.from == selectedSquare) {
                    int toRank = GameState.rank(move.to);
                    int toFile = GameState.file(move.to);
                    int toX = BOARD_OFFSET + toFile * SQUARE_SIZE;
                    int toY = BOARD_OFFSET + (BOARD_SIZE - 1 - toRank) * SQUARE_SIZE;
                    g.fillRect(toX, toY, SQUARE_SIZE, SQUARE_SIZE);
                }
            }
        }
    }

    private void drawCoordinates(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));

        // Files (A-G)
        for (int file = 0; file < BOARD_SIZE; file++) {
            String label = String.valueOf((char)('A' + file));
            int x = BOARD_OFFSET + file * SQUARE_SIZE + SQUARE_SIZE / 2 - 5;
            g.drawString(label, x, BOARD_OFFSET - 10);
            g.drawString(label, x, BOARD_OFFSET + BOARD_SIZE * SQUARE_SIZE + 20);
        }

        // Ranks (1-7)
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            String label = String.valueOf(rank + 1);
            int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE + SQUARE_SIZE / 2 + 5;
            g.drawString(label, BOARD_OFFSET - 20, y);
            g.drawString(label, BOARD_OFFSET + BOARD_SIZE * SQUARE_SIZE + 10, y);
        }
    }

    private void handleClick(int mouseX, int mouseY) {
        if (state == null || moveHandler == null) return;

        // Convert to board coordinates
        int file = (mouseX - BOARD_OFFSET) / SQUARE_SIZE;
        int rank = BOARD_SIZE - 1 - (mouseY - BOARD_OFFSET) / SQUARE_SIZE;

        if (file < 0 || file >= BOARD_SIZE || rank < 0 || rank >= BOARD_SIZE) {
            return;
        }

        int clickedSquare = GameState.getIndex(rank, file);

        // If we have a selection, try to make a move
        if (selectedSquare >= 0 && possibleMoves != null) {
            for (Move move : possibleMoves) {
                if (move.from == selectedSquare && move.to == clickedSquare) {
                    moveHandler.accept(move);
                    selectedSquare = -1;
                    possibleMoves = null;
                    return;
                }
            }
        }

        // Select a piece
        boolean isRedPiece = ((state.redGuard | state.redTowers) & GameState.bit(clickedSquare)) != 0;
        boolean isBluePiece = ((state.blueGuard | state.blueTowers) & GameState.bit(clickedSquare)) != 0;

        if ((state.redToMove && isRedPiece) || (!state.redToMove && isBluePiece)) {
            selectedSquare = clickedSquare;
            possibleMoves = MoveGenerator.generateAllMoves(state);
            repaint();
        } else {
            selectedSquare = -1;
            possibleMoves = null;
            repaint();
        }
    }
}