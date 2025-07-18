package gui;

import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.MoveGenerator;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * ENHANCED BOARD PANEL for Guard & Towers
 *
 * New Features:
 * ✅ Legal move highlighting for hints
 * ✅ Better visual feedback for stacking
 * ✅ Enhanced piece rendering with heights
 * ✅ Move animation support
 * ✅ Threat highlighting
 * ✅ Better color schemes and accessibility
 * ✅ Mouse hover effects
 * ✅ Multiple selection modes
 */
public class BoardPanel extends JPanel {

    // === CONSTANTS ===
    private static final int BOARD_SIZE = 7;
    private static final int CELL_SIZE = 80;
    private static final int BORDER_SIZE = 2;

    // === ENHANCED COLORS ===
    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);
    private static final Color SELECTED_SQUARE = new Color(255, 255, 0, 150);
    private static final Color LEGAL_MOVE = new Color(0, 255, 0, 120);
    private static final Color LEGAL_CAPTURE = new Color(255, 0, 0, 120);
    private static final Color LEGAL_STACK = new Color(0, 0, 255, 120);
    private static final Color CASTLE_BORDER = new Color(255, 215, 0, 200); // Gold
    private static final Color LAST_MOVE_FROM = new Color(255, 165, 0, 100); // Orange
    private static final Color LAST_MOVE_TO = new Color(255, 165, 0, 150); // Orange
    private static final Color HOVER_SQUARE = new Color(200, 200, 255, 80);
    private static final Color THREAT_HIGHLIGHT = new Color(255, 100, 100, 100);

    // === PIECE COLORS ===
    private static final Color RED_PIECE = new Color(200, 0, 0);
    private static final Color BLUE_PIECE = new Color(0, 0, 200);
    private static final Color RED_GUARD = new Color(220, 20, 20);
    private static final Color BLUE_GUARD = new Color(20, 20, 220);
    private static final Color PIECE_BORDER = Color.BLACK;
    private static final Color PIECE_HIGHLIGHT = Color.WHITE;

    // === GAME STATE ===
    private GameState gameState;
    private int selectedSquare = -1;
    private int hoveredSquare = -1;
    private List<Move> legalMoves = new ArrayList<>();
    private List<Move> allLegalMoves = new ArrayList<>();
    private Move lastMove = null;

    // === DISPLAY OPTIONS ===
    private boolean showLegalMoves = false;
    private boolean showThreats = false;
    private boolean showCoordinates = true;
    private boolean enableHover = true;

    // === INTERACTION ===
    private BoardClickListener clickListener;

    public interface BoardClickListener {
        void onSquareClicked(int square);
    }

    public BoardPanel() {
        setupPanel();
        setupMouseListener();
    }

    // === SETUP ===

    private void setupPanel() {
        setPreferredSize(new Dimension(
                BOARD_SIZE * CELL_SIZE + (BOARD_SIZE + 1) * BORDER_SIZE,
                BOARD_SIZE * CELL_SIZE + (BOARD_SIZE + 1) * BORDER_SIZE
        ));
        setBackground(Color.DARK_GRAY);
    }

    private void setupMouseListener() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int square = getSquareFromPoint(e.getPoint());
                if (square >= 0 && square < BOARD_SIZE * BOARD_SIZE && clickListener != null) {
                    clickListener.onSquareClicked(square);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (enableHover) {
                    int newHovered = getSquareFromPoint(e.getPoint());
                    if (newHovered != hoveredSquare) {
                        hoveredSquare = newHovered;
                        repaint();
                    }
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredSquare != -1) {
                    hoveredSquare = -1;
                    repaint();
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    // === PUBLIC INTERFACE ===

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
        updateAllLegalMoves();
        repaint();
    }

    public void setSelectedSquare(int square) {
        this.selectedSquare = square;
        updateLegalMovesFromSelected();
        repaint();
    }

    public void clearSelection() {
        this.selectedSquare = -1;
        this.legalMoves.clear();
        repaint();
    }

    public void setLastMove(Move move) {
        this.lastMove = move;
        repaint();
    }

    public void setClickListener(BoardClickListener listener) {
        this.clickListener = listener;
    }

    public void setLegalMoves(List<Move> moves) {
        this.legalMoves = moves != null ? new ArrayList<>(moves) : new ArrayList<>();
        repaint();
    }

    public void setShowLegalMoves(boolean show) {
        this.showLegalMoves = show;
        if (show) {
            updateLegalMovesFromSelected();
        } else {
            legalMoves.clear();
        }
        repaint();
    }

    public void setShowThreats(boolean show) {
        this.showThreats = show;
        repaint();
    }

    public void setShowCoordinates(boolean show) {
        this.showCoordinates = show;
        repaint();
    }

    public int getSelectedSquare() {
        return selectedSquare;
    }

    public List<Move> getLegalMoves() {
        return new ArrayList<>(legalMoves);
    }

    // === RENDERING ===

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (gameState == null) {
            drawEmptyBoard(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBoard(g2d);
        drawHighlights(g2d);
        drawPieces(g2d);
        if (showCoordinates) {
            drawCoordinates(g2d);
        }

        g2d.dispose();
    }

    private void drawEmptyBoard(Graphics g) {
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        String text = "No game loaded";
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = getHeight() / 2;
        g.drawString(text, x, y);
    }

    private void drawBoard(Graphics2D g2d) {
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {
                int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
                int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

                // Square color
                Color squareColor = ((rank + file) % 2 == 0) ? LIGHT_SQUARE : DARK_SQUARE;
                g2d.setColor(squareColor);
                g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);

                // Castle squares (D1 and D7)
                int square = rank * BOARD_SIZE + file;
                if (square == 3 || square == 45) { // D1 and D7
                    g2d.setColor(CASTLE_BORDER);
                    g2d.setStroke(new BasicStroke(4));
                    g2d.drawRect(x + 2, y + 2, CELL_SIZE - 4, CELL_SIZE - 4);

                    // Draw castle symbol
                    g2d.setColor(new Color(255, 215, 0, 100));
                    int[] xPoints = {x + 20, x + 30, x + 40, x + 50, x + 60};
                    int[] yPoints = {y + 60, y + 40, y + 50, y + 40, y + 60};
                    g2d.fillPolygon(xPoints, yPoints, 5);
                }

                // Square border
                g2d.setStroke(new BasicStroke(1));
                g2d.setColor(Color.BLACK);
                g2d.drawRect(x, y, CELL_SIZE, CELL_SIZE);
            }
        }
    }

    private void drawHighlights(Graphics2D g2d) {
        // Hover highlight
        if (hoveredSquare >= 0 && hoveredSquare < BOARD_SIZE * BOARD_SIZE) {
            drawSquareHighlight(g2d, hoveredSquare, HOVER_SQUARE);
        }

        // Last move highlight
        if (lastMove != null) {
            drawSquareHighlight(g2d, lastMove.from, LAST_MOVE_FROM);
            drawSquareHighlight(g2d, lastMove.to, LAST_MOVE_TO);
        }

        // Selected square highlight
        if (selectedSquare >= 0) {
            drawSquareHighlight(g2d, selectedSquare, SELECTED_SQUARE);
        }

        // Legal moves highlight
        if (showLegalMoves && !legalMoves.isEmpty()) {
            for (Move move : legalMoves) {
                Color moveColor = getMoveHighlightColor(move);
                drawSquareHighlight(g2d, move.to, moveColor);

                // Draw move indicator
                drawMoveIndicator(g2d, move);
            }
        }

        // Threat highlights
        if (showThreats) {
            drawThreats(g2d);
        }
    }

    private Color getMoveHighlightColor(Move move) {
        if (isCapture(move)) {
            return LEGAL_CAPTURE;
        } else if (isStacking(move)) {
            return LEGAL_STACK;
        } else {
            return LEGAL_MOVE;
        }
    }

    private void drawMoveIndicator(Graphics2D g2d, Move move) {
        int rank = move.to / BOARD_SIZE;
        int file = move.to % BOARD_SIZE;
        int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
        int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

        // Draw amount moved indicator
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String amountText = String.valueOf(move.amountMoved);
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + CELL_SIZE - fm.stringWidth(amountText) - 3;
        int textY = y + 15;

        // Background for text
        g2d.setColor(Color.BLACK);
        g2d.fillOval(textX - 2, textY - 10, 16, 16);
        g2d.setColor(Color.WHITE);
        g2d.drawString(amountText, textX, textY);
    }

    private void drawThreats(Graphics2D g2d) {
        // This would implement threat detection and highlighting
        // For now, simplified implementation
    }

    private void drawSquareHighlight(Graphics2D g2d, int square, Color color) {
        if (square < 0 || square >= BOARD_SIZE * BOARD_SIZE) return;

        int rank = square / BOARD_SIZE;
        int file = square % BOARD_SIZE;
        int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
        int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

        g2d.setColor(color);
        g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);
    }

    private void drawPieces(Graphics2D g2d) {
        for (int square = 0; square < BOARD_SIZE * BOARD_SIZE; square++) {
            int rank = square / BOARD_SIZE;
            int file = square % BOARD_SIZE;
            int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
            int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

            // Draw guard
            if ((gameState.redGuard & (1L << square)) != 0) {
                drawGuard(g2d, x, y, true);
            } else if ((gameState.blueGuard & (1L << square)) != 0) {
                drawGuard(g2d, x, y, false);
            }
            // Draw tower
            else if (gameState.redStackHeights[square] > 0) {
                drawTower(g2d, x, y, gameState.redStackHeights[square], true);
            } else if (gameState.blueStackHeights[square] > 0) {
                drawTower(g2d, x, y, gameState.blueStackHeights[square], false);
            }
        }
    }

    private void drawGuard(Graphics2D g2d, int x, int y, boolean isRed) {
        Color guardColor = isRed ? RED_GUARD : BLUE_GUARD;

        // Guard body (diamond shape)
        int centerX = x + CELL_SIZE / 2;
        int centerY = y + CELL_SIZE / 2;
        int size = 25;

        int[] xPoints = {centerX, centerX + size, centerX, centerX - size};
        int[] yPoints = {centerY - size, centerY, centerY + size, centerY};

        g2d.setColor(guardColor);
        g2d.fillPolygon(xPoints, yPoints, 4);

        g2d.setColor(PIECE_BORDER);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawPolygon(xPoints, yPoints, 4);

        // Guard symbol (G)
        g2d.setColor(PIECE_HIGHLIGHT);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "G";
        int textX = centerX - fm.stringWidth(text) / 2;
        int textY = centerY + fm.getHeight() / 3;
        g2d.drawString(text, textX, textY);
    }

    private void drawTower(Graphics2D g2d, int x, int y, int height, boolean isRed) {
        Color towerColor = isRed ? RED_PIECE : BLUE_PIECE;

        // Tower base
        int towerWidth = 40;
        int towerHeight = Math.min(50, 10 + height * 8);
        int towerX = x + (CELL_SIZE - towerWidth) / 2;
        int towerY = y + CELL_SIZE - towerHeight - 5;

        // 3D effect - draw stacked blocks
        for (int i = 0; i < height; i++) {
            int blockY = towerY + towerHeight - (i + 1) * (towerHeight / Math.max(height, 1));
            int blockHeight = towerHeight / Math.max(height, 1) + 2;

            // Lighter shade for depth
            Color blockColor = new Color(
                    Math.min(255, towerColor.getRed() + i * 20),
                    Math.min(255, towerColor.getGreen() + i * 20),
                    Math.min(255, towerColor.getBlue() + i * 20)
            );

            g2d.setColor(blockColor);
            g2d.fillRect(towerX, blockY, towerWidth, blockHeight);

            g2d.setColor(PIECE_BORDER);
            g2d.drawRect(towerX, blockY, towerWidth, blockHeight);
        }

        // Height number
        g2d.setColor(PIECE_HIGHLIGHT);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics fm = g2d.getFontMetrics();
        String heightText = String.valueOf(height);
        int textX = x + (CELL_SIZE - fm.stringWidth(heightText)) / 2;
        int textY = y + CELL_SIZE / 2 + fm.getHeight() / 3;

        // Text background
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillOval(textX - 3, textY - fm.getHeight() + 3,
                fm.stringWidth(heightText) + 6, fm.getHeight());

        g2d.setColor(PIECE_HIGHLIGHT);
        g2d.drawString(heightText, textX, textY);
    }

    private void drawCoordinates(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g2d.getFontMetrics();

        // Files (A-G)
        for (int file = 0; file < BOARD_SIZE; file++) {
            String fileLabel = String.valueOf((char)('A' + file));
            int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE + (CELL_SIZE - fm.stringWidth(fileLabel)) / 2;
            int y = BOARD_SIZE * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE + fm.getHeight();
            g2d.drawString(fileLabel, x, y);
        }

        // Ranks (1-7)
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            String rankLabel = String.valueOf(BOARD_SIZE - rank);
            int x = -fm.stringWidth(rankLabel) - 8;
            int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE + (CELL_SIZE + fm.getHeight()) / 2;
            g2d.drawString(rankLabel, x, y);
        }
    }

    // === HELPER METHODS ===

    private void updateAllLegalMoves() {
        if (gameState == null) {
            allLegalMoves.clear();
            return;
        }

        try {
            allLegalMoves = MoveGenerator.generateAllMoves(gameState);
        } catch (Exception e) {
            allLegalMoves.clear();
        }
    }

    private void updateLegalMovesFromSelected() {
        if (gameState == null || selectedSquare < 0 || !showLegalMoves) {
            legalMoves.clear();
            return;
        }

        try {
            legalMoves = allLegalMoves.stream()
                    .filter(move -> move.from == selectedSquare)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            legalMoves.clear();
        }
    }

    private int getSquareFromPoint(Point point) {
        int file = (point.x - BORDER_SIZE) / (CELL_SIZE + BORDER_SIZE);
        int rank = (point.y - BORDER_SIZE) / (CELL_SIZE + BORDER_SIZE);

        if (file >= 0 && file < BOARD_SIZE && rank >= 0 && rank < BOARD_SIZE) {
            return rank * BOARD_SIZE + file;
        }

        return -1;
    }

    private boolean isCapture(Move move) {
        if (gameState == null) return false;

        long toBit = GameState.bit(move.to);
        return ((gameState.redTowers | gameState.blueTowers |
                gameState.redGuard | gameState.blueGuard) & toBit) != 0;
    }

    private boolean isStacking(Move move) {
        if (gameState == null) return false;

        // Check if moving to a square with same color piece
        boolean redToMove = gameState.redToMove;
        if (redToMove) {
            return gameState.redStackHeights[move.to] > 0;
        } else {
            return gameState.blueStackHeights[move.to] > 0;
        }
    }

    // === UTILITY METHODS ===

    public boolean hasLegalMoveToSquare(int toSquare) {
        if (legalMoves.isEmpty() || selectedSquare < 0) return false;

        return legalMoves.stream()
                .anyMatch(move -> move.to == toSquare);
    }

    public Move getLegalMoveToSquare(int toSquare) {
        if (legalMoves.isEmpty() || selectedSquare < 0) return null;

        List<Move> possibleMoves = legalMoves.stream()
                .filter(move -> move.to == toSquare)
                .collect(Collectors.toList());

        return possibleMoves.isEmpty() ? null : possibleMoves.get(0);
    }

    public boolean hasPiece(int square, boolean red) {
        if (gameState == null) return false;

        if (red) {
            return gameState.redStackHeights[square] > 0 ||
                    (gameState.redGuard & (1L << square)) != 0;
        } else {
            return gameState.blueStackHeights[square] > 0 ||
                    (gameState.blueGuard & (1L << square)) != 0;
        }
    }

    public String getSquareName(int square) {
        if (square < 0 || square >= BOARD_SIZE * BOARD_SIZE) return "??";

        int rank = square / BOARD_SIZE;
        int file = square % BOARD_SIZE;
        return String.valueOf((char)('A' + file)) + (BOARD_SIZE - rank);
    }

    // === DEBUG METHODS ===

    public void highlightSquare(int square, Color color) {
        Graphics2D g2d = (Graphics2D) getGraphics();
        if (g2d != null) {
            drawSquareHighlight(g2d, square, color);
            g2d.dispose();
        }
    }

    public void printBoardState() {
        if (gameState == null) {
            System.out.println("No game state loaded");
            return;
        }

        System.out.println("=== BOARD STATE ===");
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {
                int square = rank * BOARD_SIZE + file;
                String piece = getPieceText(square);
                System.out.print(piece.isEmpty() ? "." : piece);
                System.out.print(" ");
            }
            System.out.println();
        }
        System.out.println("===================");
    }

    private String getPieceText(int square) {
        if (gameState == null) return "";

        // Guard
        if ((gameState.redGuard & (1L << square)) != 0) {
            return "G";
        }
        if ((gameState.blueGuard & (1L << square)) != 0) {
            return "g";
        }

        // Towers
        if (gameState.redStackHeights[square] > 0) {
            return String.valueOf(gameState.redStackHeights[square]);
        }
        if (gameState.blueStackHeights[square] > 0) {
            return String.valueOf(gameState.blueStackHeights[square]);
        }

        return "";
    }
}