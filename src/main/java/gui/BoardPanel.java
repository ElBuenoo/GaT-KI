package gui;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enhanced BoardPanel with circular pieces design matching the reference image
 */
public class BoardPanel extends JPanel {
    private static final int BOARD_SIZE = 7;
    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_OFFSET = 40;
    private static final int PIECE_SIZE = 60;
    private static final int PIECE_OFFSET = (SQUARE_SIZE - PIECE_SIZE) / 2;

    private GameState state;
    private Consumer<Move> moveHandler;
    private int selectedSquare = -1;
    private List<Move> possibleMoves = null;

    // Enhanced color scheme matching the reference image
    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);
    private static final Color BOARD_BORDER = new Color(139, 104, 66);

    // Piece colors with proper contrast
    private static final Color RED_PIECE = new Color(220, 20, 60);
    private static final Color RED_PIECE_BORDER = new Color(180, 15, 45);
    private static final Color BLUE_PIECE = new Color(30, 100, 200);
    private static final Color BLUE_PIECE_BORDER = new Color(20, 70, 150);

    // Highlight colors
    private static final Color SELECTED_COLOR = new Color(255, 255, 0, 128);
    private static final Color POSSIBLE_MOVE_COLOR = new Color(50, 255, 50, 100);
    private static final Color LAST_MOVE_COLOR = new Color(255, 255, 100, 80);

    // Text colors
    private static final Color PIECE_TEXT_COLOR = Color.WHITE;
    private static final Color COORDINATE_COLOR = new Color(80, 60, 40);

    public BoardPanel(GameState initialState, Consumer<Move> moveHandler) {
        this.state = initialState;
        this.moveHandler = moveHandler;

        setPreferredSize(new Dimension(
                BOARD_SIZE * SQUARE_SIZE + 2 * BOARD_OFFSET,
                BOARD_SIZE * SQUARE_SIZE + 2 * BOARD_OFFSET
        ));

        setBackground(new Color(245, 230, 200));

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
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw board background and border
        drawBoardBackground(g2);

        // Draw board squares
        drawBoard(g2);

        // Draw selection and possible moves BEFORE pieces
        drawHighlights(g2);

        // Draw pieces on top
        drawPieces(g2);

        // Draw coordinates
        drawCoordinates(g2);
    }

    private void drawBoardBackground(Graphics2D g) {
        // Draw board border
        g.setColor(BOARD_BORDER);
        g.fillRect(BOARD_OFFSET - 5, BOARD_OFFSET - 5,
                BOARD_SIZE * SQUARE_SIZE + 10, BOARD_SIZE * SQUARE_SIZE + 10);
    }

    private void drawBoard(Graphics2D g) {
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {
                int x = BOARD_OFFSET + file * SQUARE_SIZE;
                int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE;

                // Alternate square colors
                if ((rank + file) % 2 == 0) {
                    g.setColor(LIGHT_SQUARE);
                } else {
                    g.setColor(DARK_SQUARE);
                }
                g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

                // Subtle square border
                g.setColor(new Color(0, 0, 0, 20));
                g.drawRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

                // Castle squares (D1 and D7) with special marking
                if ((rank == 0 && file == 3) || (rank == 6 && file == 3)) {
                    g.setColor(new Color(255, 215, 0, 40));
                    g.fillRect(x + 2, y + 2, SQUARE_SIZE - 4, SQUARE_SIZE - 4);

                    // Castle crown symbol
                    g.setColor(new Color(255, 215, 0, 100));
                    g.setFont(new Font("Arial", Font.BOLD, 16));
                    FontMetrics fm = g.getFontMetrics();
                    String crown = "♜";
                    int textX = x + (SQUARE_SIZE - fm.stringWidth(crown)) / 2;
                    int textY = y + (SQUARE_SIZE + fm.getAscent()) / 2;
                    g.drawString(crown, textX, textY);
                }
            }
        }
    }

    private void drawHighlights(Graphics2D g) {
        // Selected square
        if (selectedSquare >= 0) {
            int rank = GameState.rank(selectedSquare);
            int file = GameState.file(selectedSquare);
            int x = BOARD_OFFSET + file * SQUARE_SIZE;
            int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE;

            g.setColor(SELECTED_COLOR);
            g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

            // Selection border
            g.setStroke(new BasicStroke(3));
            g.setColor(new Color(255, 255, 0, 200));
            g.drawRect(x + 1, y + 1, SQUARE_SIZE - 2, SQUARE_SIZE - 2);
        }

        // Possible moves
        if (possibleMoves != null && selectedSquare >= 0) {
            g.setColor(POSSIBLE_MOVE_COLOR);
            for (Move move : possibleMoves) {
                if (move.from == selectedSquare) {
                    int toRank = GameState.rank(move.to);
                    int toFile = GameState.file(move.to);
                    int x = BOARD_OFFSET + toFile * SQUARE_SIZE;
                    int y = BOARD_OFFSET + (BOARD_SIZE - 1 - toRank) * SQUARE_SIZE;

                    // Draw target square highlight
                    g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

                    // Draw move indicator circle
                    g.setColor(new Color(0, 150, 0, 150));
                    int circleSize = 20;
                    int circleX = x + (SQUARE_SIZE - circleSize) / 2;
                    int circleY = y + (SQUARE_SIZE - circleSize) / 2;
                    g.fillOval(circleX, circleY, circleSize, circleSize);
                }
            }
        }
    }

    private void drawPieces(Graphics2D g) {
        if (state == null) return;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);
            int file = GameState.file(i);
            int x = BOARD_OFFSET + file * SQUARE_SIZE + PIECE_OFFSET;
            int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE + PIECE_OFFSET;

            // Draw guards
            if ((state.redGuard & GameState.bit(i)) != 0) {
                drawPiece(g, x, y, RED_PIECE, RED_PIECE_BORDER, "RG", true);
            } else if ((state.blueGuard & GameState.bit(i)) != 0) {
                drawPiece(g, x, y, BLUE_PIECE, BLUE_PIECE_BORDER, "BG", true);
            }
            // Draw towers
            else if (state.redStackHeights[i] > 0) {
                drawPiece(g, x, y, RED_PIECE, RED_PIECE_BORDER,
                        String.valueOf(state.redStackHeights[i]), false);
            } else if (state.blueStackHeights[i] > 0) {
                drawPiece(g, x, y, BLUE_PIECE, BLUE_PIECE_BORDER,
                        String.valueOf(state.blueStackHeights[i]), false);
            }
        }
    }

    private void drawPiece(Graphics2D g, int x, int y, Color fillColor, Color borderColor,
                           String text, boolean isGuard) {
        // Create piece shape
        Ellipse2D.Double circle = new Ellipse2D.Double(x, y, PIECE_SIZE, PIECE_SIZE);

        // Fill piece
        g.setColor(fillColor);
        g.fill(circle);

        // Add gradient effect for depth
        GradientPaint gradient = new GradientPaint(
                x, y, new Color(255, 255, 255, 60),
                x + PIECE_SIZE, y + PIECE_SIZE, new Color(0, 0, 0, 60)
        );
        g.setPaint(gradient);
        g.fill(circle);

        // Draw border
        g.setStroke(new BasicStroke(3));
        g.setColor(borderColor);
        g.draw(circle);

        // Inner highlight
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(255, 255, 255, 100));
        Ellipse2D.Double innerCircle = new Ellipse2D.Double(x + 3, y + 3, PIECE_SIZE - 6, PIECE_SIZE - 6);
        g.draw(innerCircle);

        // Draw text
        g.setColor(PIECE_TEXT_COLOR);
        if (isGuard) {
            g.setFont(new Font("Arial", Font.BOLD, 16));
        } else {
            g.setFont(new Font("Arial", Font.BOLD, 24));
        }

        FontMetrics fm = g.getFontMetrics();
        int textX = x + (PIECE_SIZE - fm.stringWidth(text)) / 2;
        int textY = y + (PIECE_SIZE + fm.getAscent()) / 2 - 2;

        // Text shadow for better readability
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(text, textX + 1, textY + 1);

        g.setColor(PIECE_TEXT_COLOR);
        g.drawString(text, textX, textY);
    }

    private void drawCoordinates(Graphics2D g) {
        g.setColor(COORDINATE_COLOR);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();

        // Files (A-G)
        for (int file = 0; file < BOARD_SIZE; file++) {
            String label = String.valueOf((char)('A' + file));
            int x = BOARD_OFFSET + file * SQUARE_SIZE + (SQUARE_SIZE - fm.stringWidth(label)) / 2;

            // Top coordinates
            g.drawString(label, x, BOARD_OFFSET - 10);
            // Bottom coordinates
            g.drawString(label, x, BOARD_OFFSET + BOARD_SIZE * SQUARE_SIZE + 20);
        }

        // Ranks (1-7)
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            String label = String.valueOf(rank + 1);
            int y = BOARD_OFFSET + (BOARD_SIZE - 1 - rank) * SQUARE_SIZE +
                    (SQUARE_SIZE + fm.getAscent()) / 2;

            // Left coordinates
            g.drawString(label, BOARD_OFFSET - 25, y);
            // Right coordinates
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
                    repaint();
                    return;
                }
            }
        }

        // Select a piece (only current player's pieces)
        boolean isRedPiece = ((state.redGuard | state.redTowers) & GameState.bit(clickedSquare)) != 0;
        boolean isBluePiece = ((state.blueGuard | state.blueTowers) & GameState.bit(clickedSquare)) != 0;

        if ((state.redToMove && isRedPiece) || (!state.redToMove && isBluePiece)) {
            selectedSquare = clickedSquare;
            possibleMoves = MoveGenerator.generateAllMoves(state);
            repaint();
        } else {
            // Clicked on empty square or opponent's piece - deselect
            selectedSquare = -1;
            possibleMoves = null;
            repaint();
        }
    }

    // Additional utility methods for enhanced functionality

    public void highlightLastMove(Move lastMove) {
        // Could be implemented to show the last move played
        // by adding lastMove field and highlighting it
    }

    public void setFlipped(boolean flipped) {
        // Could be implemented to flip board for different perspectives
    }

    public void animateMove(Move move) {
        // Could be implemented for smooth move animations
    }
}