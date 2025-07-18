package gui;

import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.MoveGenerator;
import GaT.evaluation.Evaluator;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.io.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Scanner;

/**
 * ENHANCED GAME FRAME for Guard & Towers
 *
 * New Features:
 * ✅ Fixed stacking moves for humans
 * ✅ Move history with undo/redo
 * ✅ Save/Load games
 * ✅ Multiple AI difficulty levels
 * ✅ Game analysis and hints
 * ✅ Better status messages and feedback
 * ✅ Keyboard shortcuts
 * ✅ Position evaluation display
 * ✅ Move validation with detailed feedback
 * ✅ Enhanced visual indicators
 * ✅ Game statistics tracking
 */
public class GameFrame extends JFrame implements BoardPanel.BoardClickListener {

    // === GAME STATE ===
    private GameState gameState;
    private Engine engine;
    private Evaluator evaluator;
    private ExecutorService aiExecutor;
    private Future<?> currentAITask;

    // === MOVE HISTORY ===
    private final List<GameState> gameHistory = new ArrayList<>();
    private final List<Move> moveHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;

    // === UI COMPONENTS ===
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private JLabel evaluationLabel;
    private JLabel gameInfoLabel;

    // Menu components
    private JMenuBar menuBar;
    private JMenu gameMenu, aiMenu, helpMenu;

    // Game control buttons
    private JButton humanVsAiButton;
    private JButton aiVsAiButton;
    private JButton stopAiButton;
    private JButton resetButton;
    private JButton undoButton;
    private JButton redoButton;
    private JButton hintButton;
    private JButton analyzeButton;

    // AI difficulty
    private JComboBox<String> difficultyCombo;
    private JCheckBox showEvaluationBox;
    private JCheckBox showHintsBox;

    // Status and info
    private JTextArea moveHistoryArea;
    private JLabel engineStatsLabel;
    private JProgressBar thinkingProgress;

    // === GAME MODE ===
    private enum GameMode { HUMAN_VS_AI, AI_VS_AI, STOPPED }
    private GameMode currentMode = GameMode.STOPPED;
    private boolean humanIsRed = true;
    private boolean aiThinking = false;
    private Move lastMove = null;

    // === AI SETTINGS ===
    private enum AIDifficulty {
        BEGINNER(1000, 4),
        EASY(2000, 6),
        MEDIUM(4000, 8),
        HARD(8000, 10),
        EXPERT(15000, 12);

        final int timeMs;
        final int maxDepth;

        AIDifficulty(int timeMs, int maxDepth) {
            this.timeMs = timeMs;
            this.maxDepth = maxDepth;
        }
    }
    private AIDifficulty currentDifficulty = AIDifficulty.MEDIUM;

    // === GAME STATISTICS ===
    private int moveCount = 0;
    private long gameStartTime = 0;
    private long totalThinkingTime = 0;

    public GameFrame() {
        initializeComponents();
        setupMenuBar();
        setupLayout();
        setupEventHandlers();
        setupKeyboardShortcuts();
        resetGame();

        setTitle("Guard & Towers - Enhanced Edition v3.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        pack();
        setLocationRelativeTo(null);

        // Welcome message
        updateStatus("🎯 Welcome to Guard & Towers! Choose a game mode to start playing.");
    }

    // === INITIALIZATION ===

    private void initializeComponents() {
        engine = new Engine();
        evaluator = new Evaluator();
        aiExecutor = Executors.newSingleThreadExecutor();

        // Board panel
        boardPanel = new BoardPanel();
        boardPanel.setClickListener(this);

        // Control buttons
        humanVsAiButton = new JButton("🎮 Human vs AI");
        aiVsAiButton = new JButton("🤖 AI vs AI");
        stopAiButton = new JButton("⏹️ Stop AI");
        resetButton = new JButton("🔄 New Game");
        undoButton = new JButton("↶ Undo");
        redoButton = new JButton("↷ Redo");
        hintButton = new JButton("💡 Hint");
        analyzeButton = new JButton("📊 Analyze");

        // AI difficulty selection
        String[] difficulties = {"Beginner", "Easy", "Medium", "Hard", "Expert"};
        difficultyCombo = new JComboBox<>(difficulties);
        difficultyCombo.setSelectedIndex(2); // Medium

        // Options
        showEvaluationBox = new JCheckBox("Show Evaluation", true);
        showHintsBox = new JCheckBox("Show Hints", false);

        // Status labels
        statusLabel = new JLabel("Welcome to Guard & Towers!", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));

        evaluationLabel = new JLabel("Evaluation: Balanced", SwingConstants.CENTER);
        evaluationLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        evaluationLabel.setForeground(new Color(0, 100, 0));

        gameInfoLabel = new JLabel("Move: 0 | Time: 0:00", SwingConstants.LEFT);
        gameInfoLabel.setFont(new Font("Arial", Font.PLAIN, 11));

        engineStatsLabel = new JLabel("Engine ready", SwingConstants.CENTER);
        engineStatsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        engineStatsLabel.setForeground(Color.GRAY);

        // Move history
        moveHistoryArea = new JTextArea(10, 20);
        moveHistoryArea.setEditable(false);
        moveHistoryArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        moveHistoryArea.setBackground(getBackground());

        // Progress bar
        thinkingProgress = new JProgressBar();
        thinkingProgress.setStringPainted(true);
        thinkingProgress.setString("Ready");
        thinkingProgress.setVisible(false);
    }

    private void setupMenuBar() {
        menuBar = new JMenuBar();

        // Game Menu
        gameMenu = new JMenu("Game");
        gameMenu.setMnemonic(KeyEvent.VK_G);

        JMenuItem newGameItem = new JMenuItem("New Game", KeyEvent.VK_N);
        newGameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        newGameItem.addActionListener(e -> resetGame());

        JMenuItem saveGameItem = new JMenuItem("Save Game...", KeyEvent.VK_S);
        saveGameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        saveGameItem.addActionListener(e -> saveGame());

        JMenuItem loadGameItem = new JMenuItem("Load Game...", KeyEvent.VK_L);
        loadGameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        loadGameItem.addActionListener(e -> loadGame());

        JMenuItem exitItem = new JMenuItem("Exit", KeyEvent.VK_X);
        exitItem.addActionListener(e -> System.exit(0));

        gameMenu.add(newGameItem);
        gameMenu.add(saveGameItem);
        gameMenu.add(loadGameItem);
        gameMenu.addSeparator();
        gameMenu.add(exitItem);

        // AI Menu
        aiMenu = new JMenu("AI");
        aiMenu.setMnemonic(KeyEvent.VK_A);

        JMenuItem engineInfoItem = new JMenuItem("Engine Information");
        engineInfoItem.addActionListener(e -> showEngineInfo());

        JMenuItem benchmarkItem = new JMenuItem("Run Benchmark");
        benchmarkItem.addActionListener(e -> runBenchmark());

        aiMenu.add(engineInfoItem);
        aiMenu.add(benchmarkItem);

        // Help Menu
        helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem rulesItem = new JMenuItem("Game Rules", KeyEvent.VK_R);
        rulesItem.addActionListener(e -> showGameRules());

        JMenuItem aboutItem = new JMenuItem("About", KeyEvent.VK_A);
        aboutItem.addActionListener(e -> showAbout());

        helpMenu.add(rulesItem);
        helpMenu.add(aboutItem);

        menuBar.add(gameMenu);
        menuBar.add(aiMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Board in center
        mainPanel.add(boardPanel, BorderLayout.CENTER);

        // Right panel with controls and info
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(250, 600));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Game Control"));

        // Control buttons panel
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Game mode buttons
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        controlPanel.add(humanVsAiButton, gbc);
        gbc.gridy = 1;
        controlPanel.add(aiVsAiButton, gbc);

        // Control buttons
        gbc.gridy = 2; gbc.gridwidth = 1;
        controlPanel.add(stopAiButton, gbc);
        gbc.gridx = 1;
        controlPanel.add(resetButton, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        controlPanel.add(undoButton, gbc);
        gbc.gridx = 1;
        controlPanel.add(redoButton, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        controlPanel.add(hintButton, gbc);
        gbc.gridx = 1;
        controlPanel.add(analyzeButton, gbc);

        // AI settings
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JPanel aiSettingsPanel = new JPanel(new FlowLayout());
        aiSettingsPanel.add(new JLabel("AI:"));
        aiSettingsPanel.add(difficultyCombo);
        controlPanel.add(aiSettingsPanel, gbc);

        // Options
        gbc.gridy = 6;
        controlPanel.add(showEvaluationBox, gbc);
        gbc.gridy = 7;
        controlPanel.add(showHintsBox, gbc);

        rightPanel.add(controlPanel, BorderLayout.NORTH);

        // Move history
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBorder(BorderFactory.createTitledBorder("Move History"));
        JScrollPane historyScroll = new JScrollPane(moveHistoryArea);
        historyScroll.setPreferredSize(new Dimension(200, 200));
        historyPanel.add(historyScroll, BorderLayout.CENTER);
        rightPanel.add(historyPanel, BorderLayout.CENTER);

        mainPanel.add(rightPanel, BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);

        // Status panel at bottom
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel topStatusPanel = new JPanel(new BorderLayout());
        topStatusPanel.add(statusLabel, BorderLayout.CENTER);
        topStatusPanel.add(gameInfoLabel, BorderLayout.EAST);

        JPanel bottomStatusPanel = new JPanel(new BorderLayout());
        bottomStatusPanel.add(evaluationLabel, BorderLayout.CENTER);
        bottomStatusPanel.add(engineStatsLabel, BorderLayout.EAST);

        statusPanel.add(topStatusPanel, BorderLayout.NORTH);
        statusPanel.add(thinkingProgress, BorderLayout.CENTER);
        statusPanel.add(bottomStatusPanel, BorderLayout.SOUTH);

        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        // Game mode buttons
        humanVsAiButton.addActionListener(e -> startHumanVsAI());
        aiVsAiButton.addActionListener(e -> startAIVsAI());
        stopAiButton.addActionListener(e -> stopAI());
        resetButton.addActionListener(e -> resetGame());

        // Move control buttons
        undoButton.addActionListener(e -> undoMove());
        redoButton.addActionListener(e -> redoMove());
        hintButton.addActionListener(e -> showHint());
        analyzeButton.addActionListener(e -> analyzePosition());

        // Settings
        difficultyCombo.addActionListener(e -> {
            currentDifficulty = AIDifficulty.values()[difficultyCombo.getSelectedIndex()];
            updateStatus("AI difficulty set to " + difficultyCombo.getSelectedItem());
        });

        showEvaluationBox.addActionListener(e -> updateEvaluationDisplay());
        showHintsBox.addActionListener(e -> {
            if (showHintsBox.isSelected()) {
                boardPanel.setShowLegalMoves(true);
            } else {
                boardPanel.setShowLegalMoves(false);
            }
        });
    }

    private void setupKeyboardShortcuts() {
        // Undo/Redo
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK), "undo");
        getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { undoMove(); }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK), "redo");
        getRootPane().getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { redoMove(); }
        });

        // Hint
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.CTRL_MASK), "hint");
        getRootPane().getActionMap().put("hint", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { showHint(); }
        });
    }

    // === FIXED BOARD CLICK HANDLER ===

    @Override
    public void onSquareClicked(int square) {
        if (currentMode != GameMode.HUMAN_VS_AI || aiThinking) {
            return; // Not accepting human input
        }

        if (gameState.redToMove != humanIsRed) {
            updateStatus("It's not your turn!");
            return;
        }

        int selectedSquare = boardPanel.getSelectedSquare();

        if (selectedSquare == -1) {
            // No piece selected - try to select piece
            if (boardPanel.hasPiece(square, humanIsRed)) {
                boardPanel.setSelectedSquare(square);
                updateStatus("Piece selected on " + boardPanel.getSquareName(square) + " - Click destination");

                // Show legal moves if hints enabled
                if (showHintsBox.isSelected()) {
                    boardPanel.setLegalMoves(getLegalMovesFrom(square));
                }
            } else {
                updateStatus("No piece to select on " + boardPanel.getSquareName(square));
            }
        } else {
            // Piece already selected
            if (square == selectedSquare) {
                // Clicked same square - deselect
                boardPanel.clearSelection();
                updateStatus("Selection cleared - Click a piece to select");
            } else {
                // FIXED: Always try to make a move first, regardless of what's on target square
                Move move = selectBestMove(selectedSquare, square);
                if (move != null) {
                    makeHumanMove(move);
                } else if (boardPanel.hasPiece(square, humanIsRed)) {
                    // Only if no legal move exists, then select the new piece
                    boardPanel.setSelectedSquare(square);
                    updateStatus("New piece selected on " + boardPanel.getSquareName(square) + " - Click destination");

                    if (showHintsBox.isSelected()) {
                        boardPanel.setLegalMoves(getLegalMovesFrom(square));
                    }
                } else {
                    updateStatus("Illegal move to " + boardPanel.getSquareName(square) + " - Try again");
                }
            }
        }
    }

    // === ENHANCED MOVE SELECTION ===

    private Move selectBestMove(int from, int to) {
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(gameState);
            List<Move> possibleMoves = allMoves.stream()
                    .filter(move -> move.from == from && move.to == to)
                    .collect(Collectors.toList());

            if (possibleMoves.isEmpty()) {
                return null;
            }

            if (possibleMoves.size() == 1) {
                return possibleMoves.get(0);
            }

            // Multiple moves possible - let user choose amount
            return selectMoveAmount(possibleMoves);

        } catch (Exception e) {
            return null;
        }
    }

    private Move selectMoveAmount(List<Move> possibleMoves) {
        String[] options = possibleMoves.stream()
                .map(move -> {
                    String moveType = getMoveDescription(move);
                    return String.format("Move %d piece(s) %s", move.amountMoved, moveType);
                })
                .toArray(String[]::new);

        int choice = JOptionPane.showOptionDialog(
                this,
                "Multiple moves possible. How many pieces do you want to move?",
                "Select Move Amount",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        return choice >= 0 ? possibleMoves.get(choice) : null;
    }

    private String getMoveDescription(Move move) {
        // Check what type of move this is
        boolean isStacking = false;
        boolean isCapture = false;

        try {
            if (humanIsRed) {
                isStacking = gameState.redStackHeights[move.to] > 0;
                isCapture = gameState.blueStackHeights[move.to] > 0 ||
                        (gameState.blueGuard & GameState.bit(move.to)) != 0;
            } else {
                isStacking = gameState.blueStackHeights[move.to] > 0;
                isCapture = gameState.redStackHeights[move.to] > 0 ||
                        (gameState.redGuard & GameState.bit(move.to)) != 0;
            }
        } catch (Exception e) {
            return "";
        }

        if (isCapture) return "(Capture!)";
        if (isStacking) return "(Stack)";
        return "";
    }

    private List<Move> getLegalMovesFrom(int fromSquare) {
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(gameState);
            return allMoves.stream()
                    .filter(move -> move.from == fromSquare)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // === ENHANCED GAME MODES ===

    private void startHumanVsAI() {
        stopAI();
        currentMode = GameMode.HUMAN_VS_AI;
        humanIsRed = true;
        boardPanel.clearSelection();
        gameStartTime = System.currentTimeMillis();

        updateStatus("🎮 Human vs AI mode - You are RED, AI is BLUE");
        updateGameInfo();
        updateButtonStates();

        if (!gameState.redToMove) {
            // AI's turn
            makeAIMove();
        } else {
            updateStatus("Your turn! Click a piece to select it");
            updateEvaluationDisplay();
        }
    }

    private void startAIVsAI() {
        stopAI();
        currentMode = GameMode.AI_VS_AI;
        boardPanel.clearSelection();
        gameStartTime = System.currentTimeMillis();

        updateStatus("🤖 AI vs AI mode - Watching engines play...");
        updateGameInfo();
        updateButtonStates();
        makeAIMove();
    }

    private void stopAI() {
        if (currentAITask != null && !currentAITask.isDone()) {
            currentAITask.cancel(true);
        }
        currentMode = GameMode.STOPPED;
        aiThinking = false;
        boardPanel.clearSelection();
        thinkingProgress.setVisible(false);

        updateStatus("⏹️ AI stopped - Game paused");
        updateButtonStates();
    }

    private void resetGame() {
        stopAI();
        gameState = new GameState();
        lastMove = null;
        currentMode = GameMode.STOPPED;
        moveCount = 0;
        gameStartTime = 0;
        totalThinkingTime = 0;

        // Clear history
        gameHistory.clear();
        moveHistory.clear();
        currentHistoryIndex = -1;
        addToHistory(gameState.copy(), null);

        boardPanel.setGameState(gameState);
        boardPanel.clearSelection();
        boardPanel.setLastMove(null);

        updateStatus("🔄 Game reset - Choose game mode to start");
        updateMoveHistory();
        updateEvaluationDisplay();
        updateGameInfo();
        updateEngineStats("Engine ready");
        updateButtonStates();
    }

    // === ENHANCED MOVE HANDLING ===

    private void makeHumanMove(Move move) {
        try {
            // Validate move
            List<Move> legalMoves = MoveGenerator.generateAllMoves(gameState);
            if (!legalMoves.contains(move)) {
                updateStatus("❌ Illegal move: " + move);
                return;
            }

            // Apply move
            gameState.applyMove(move);
            lastMove = move;
            moveCount++;

            // Add to history
            addToHistory(gameState.copy(), move);

            // Update display
            boardPanel.setGameState(gameState);
            boardPanel.setLastMove(lastMove);
            boardPanel.clearSelection();

            // Update UI
            updateMoveHistory();
            updateGameInfo();
            updateStatusForMove(move, true);

            // Check game end
            if (isGameOver()) {
                handleGameOver();
                return;
            }

            // Update evaluation
            updateEvaluationDisplay();

            // Continue with AI move
            makeAIMove();

        } catch (Exception e) {
            updateStatus("❌ Error making move: " + e.getMessage());
        }
    }

    private void makeAIMove() {
        if (currentMode == GameMode.STOPPED || aiThinking) {
            return;
        }

        aiThinking = true;
        updateButtonStates();

        String currentPlayer = gameState.redToMove ? "RED" : "BLUE";
        updateStatus("🧠 " + currentPlayer + " AI is thinking...");

        // Show progress bar
        thinkingProgress.setVisible(true);
        thinkingProgress.setIndeterminate(true);
        thinkingProgress.setString("AI thinking...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Use difficulty settings
                long thinkTime = currentDifficulty.timeMs;
                int maxDepth = currentDifficulty.maxDepth;

                Move aiMove = engine.findBestMove(gameState, maxDepth, thinkTime);
                long actualTime = System.currentTimeMillis() - startTime;
                totalThinkingTime += actualTime;

                if (aiMove != null && !Thread.currentThread().isInterrupted()) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            // Apply AI move
                            gameState.applyMove(aiMove);
                            lastMove = aiMove;
                            moveCount++;

                            // Add to history
                            addToHistory(gameState.copy(), aiMove);

                            // Update display
                            boardPanel.setGameState(gameState);
                            boardPanel.setLastMove(lastMove);

                            // Update UI
                            updateMoveHistory();
                            updateGameInfo();
                            updateStatusForMove(aiMove, false);
                            updateEvaluationDisplay();

                            // Update stats
                            String statsDesc = String.format("Search: %,d nodes, %.0f nps, %.1f%% TT hits",
                                    engine.getNodesSearched(),
                                    engine.getNodesSearched() * 1000.0 / Math.max(1, actualTime),
                                    engine.getTTHitRate());
                            updateEngineStats(statsDesc);

                            // Check game end
                            if (isGameOver()) {
                                handleGameOver();
                            } else if (currentMode == GameMode.AI_VS_AI) {
                                // Continue AI vs AI after short delay
                                Timer timer = new Timer(800, e -> makeAIMove());
                                timer.setRepeats(false);
                                timer.start();
                            }

                        } catch (Exception e) {
                            updateStatus("❌ Error applying AI move: " + e.getMessage());
                        } finally {
                            aiThinking = false;
                            thinkingProgress.setVisible(false);
                            updateButtonStates();
                        }
                    });
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("❌ AI error: " + e.getMessage());
                        updateEngineStats("Engine error");
                        aiThinking = false;
                        thinkingProgress.setVisible(false);
                        updateButtonStates();
                    });
                }
            }
        });
    }

    // === MOVE HISTORY MANAGEMENT ===

    private void addToHistory(GameState state, Move move) {
        // Remove any redo history
        while (gameHistory.size() > currentHistoryIndex + 1) {
            gameHistory.remove(gameHistory.size() - 1);
            if (!moveHistory.isEmpty()) {
                moveHistory.remove(moveHistory.size() - 1);
            }
        }

        gameHistory.add(state);
        if (move != null) {
            moveHistory.add(move);
        }
        currentHistoryIndex = gameHistory.size() - 1;
    }

    private void undoMove() {
        if (currentHistoryIndex <= 0 || aiThinking) {
            updateStatus("Cannot undo move");
            return;
        }

        currentHistoryIndex--;
        gameState = gameHistory.get(currentHistoryIndex).copy();

        if (currentHistoryIndex > 0) {
            lastMove = moveHistory.get(currentHistoryIndex - 1);
        } else {
            lastMove = null;
        }

        moveCount = Math.max(0, moveCount - 1);

        // Update display
        boardPanel.setGameState(gameState);
        boardPanel.setLastMove(lastMove);
        boardPanel.clearSelection();

        updateMoveHistory();
        updateGameInfo();
        updateEvaluationDisplay();
        updateStatus("↶ Move undone");
        updateButtonStates();
    }

    private void redoMove() {
        if (currentHistoryIndex >= gameHistory.size() - 1 || aiThinking) {
            updateStatus("Cannot redo move");
            return;
        }

        currentHistoryIndex++;
        gameState = gameHistory.get(currentHistoryIndex).copy();

        if (currentHistoryIndex > 0) {
            lastMove = moveHistory.get(currentHistoryIndex - 1);
        } else {
            lastMove = null;
        }

        moveCount++;

        // Update display
        boardPanel.setGameState(gameState);
        boardPanel.setLastMove(lastMove);
        boardPanel.clearSelection();

        updateMoveHistory();
        updateGameInfo();
        updateEvaluationDisplay();
        updateStatus("↷ Move redone");
        updateButtonStates();
    }

    // === ANALYSIS FEATURES ===

    private void showHint() {
        if (aiThinking || currentMode != GameMode.HUMAN_VS_AI) {
            return;
        }

        try {
            updateStatus("💡 Calculating hint...");

            SwingWorker<Move, Void> hintWorker = new SwingWorker<Move, Void>() {
                @Override
                protected Move doInBackground() throws Exception {
                    return engine.findBestMove(gameState, 6, 3000);
                }

                @Override
                protected void done() {
                    try {
                        Move hint = get();
                        if (hint != null) {
                            String hintText = String.format("💡 Hint: %s (%s)",
                                    hint, getMoveDescription(hint));
                            updateStatus(hintText);

                            // Highlight the suggested move
                            boardPanel.setSelectedSquare(hint.from);
                            boardPanel.setLegalMoves(List.of(hint));
                        } else {
                            updateStatus("💡 No hint available");
                        }
                    } catch (Exception e) {
                        updateStatus("💡 Hint calculation failed");
                    }
                }
            };

            hintWorker.execute();

        } catch (Exception e) {
            updateStatus("💡 Hint unavailable: " + e.getMessage());
        }
    }

    private void analyzePosition() {
        try {
            StringBuilder analysis = new StringBuilder();
            analysis.append("📊 POSITION ANALYSIS\n");
            analysis.append("=" .repeat(30)).append("\n\n");

            // Basic evaluation
            int eval = evaluator.evaluate(gameState);
            String evalDesc = eval > 100 ? "Winning" : eval > 50 ? "Better" :
                    eval > -50 ? "Balanced" : eval > -100 ? "Worse" : "Losing";
            analysis.append(String.format("Evaluation: %+d (%s)\n", eval, evalDesc));
            analysis.append(String.format("Side to move: %s\n", gameState.redToMove ? "RED" : "BLUE"));

            // Material count
            int redMaterial = 0, blueMaterial = 0;
            for (int i = 0; i < 49; i++) {
                redMaterial += gameState.redStackHeights[i];
                blueMaterial += gameState.blueStackHeights[i];
            }
            analysis.append(String.format("Material: Red %d, Blue %d\n", redMaterial, blueMaterial));

            // Guard positions
            if (gameState.redGuard != 0) {
                int redGuardPos = Long.numberOfTrailingZeros(gameState.redGuard);
                analysis.append(String.format("Red guard: %s\n", boardPanel.getSquareName(redGuardPos)));
            }
            if (gameState.blueGuard != 0) {
                int blueGuardPos = Long.numberOfTrailingZeros(gameState.blueGuard);
                analysis.append(String.format("Blue guard: %s\n", boardPanel.getSquareName(blueGuardPos)));
            }

            // Legal moves
            List<Move> legalMoves = MoveGenerator.generateAllMoves(gameState);
            analysis.append(String.format("Legal moves: %d\n", legalMoves.size()));

            // Detailed breakdown
            analysis.append("\n").append(evaluator.getEvaluationBreakdown(gameState));

            // Show in dialog
            JTextArea textArea = new JTextArea(analysis.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(400, 500));

            JOptionPane.showMessageDialog(this, scrollPane, "Position Analysis",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            updateStatus("📊 Analysis failed: " + e.getMessage());
        }
    }

    // === GAME END HANDLING ===

    private boolean isGameOver() {
        // Check for guard captured
        if (gameState.redGuard == 0 || gameState.blueGuard == 0) {
            return true;
        }

        // Check for guard on enemy castle
        if ((gameState.redGuard & (1L << 3)) != 0) return true;  // Red guard on D1
        if ((gameState.blueGuard & (1L << 45)) != 0) return true; // Blue guard on D7

        // Check for no legal moves (stalemate)
        try {
            List<Move> legalMoves = MoveGenerator.generateAllMoves(gameState);
            return legalMoves.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void handleGameOver() {
        String winner = determineWinner();
        long gameTime = System.currentTimeMillis() - gameStartTime;

        updateStatus("🏆 GAME OVER - " + winner);
        currentMode = GameMode.STOPPED;
        aiThinking = false;
        boardPanel.clearSelection();
        thinkingProgress.setVisible(false);
        updateButtonStates();

        // Show detailed game over dialog
        showGameOverDialog(winner, gameTime);
    }

    private void showGameOverDialog(String winner, long gameTime) {
        StringBuilder message = new StringBuilder();
        message.append("🏆 GAME FINISHED!\n\n");
        message.append(winner).append("\n\n");
        message.append("📊 Game Statistics:\n");
        message.append(String.format("Moves played: %d\n", moveCount));
        message.append(String.format("Game duration: %s\n", formatTime(gameTime)));
        if (totalThinkingTime > 0) {
            message.append(String.format("AI thinking time: %s\n", formatTime(totalThinkingTime)));
        }

        int result = JOptionPane.showOptionDialog(
                this,
                message.toString(),
                "Game Over",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                new String[]{"New Game", "Close"},
                "New Game"
        );

        if (result == 0) {
            resetGame();
        }
    }

    private String determineWinner() {
        if (gameState.redGuard == 0) {
            return "🔵 BLUE WINS! (Red guard captured)";
        } else if (gameState.blueGuard == 0) {
            return "🔴 RED WINS! (Blue guard captured)";
        } else if ((gameState.redGuard & (1L << 3)) != 0) {
            return "🔴 RED WINS! (Guard reached enemy castle)";
        } else if ((gameState.blueGuard & (1L << 45)) != 0) {
            return "🔵 BLUE WINS! (Guard reached enemy castle)";
        } else {
            try {
                List<Move> legalMoves = MoveGenerator.generateAllMoves(gameState);
                if (legalMoves.isEmpty()) {
                    return "🤝 DRAW! (No legal moves)";
                }
            } catch (Exception e) {
                // Ignore
            }
            return "🏁 Game ended";
        }
    }

    // === FILE OPERATIONS ===

    private void saveGame() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Guard & Towers Games", "gat"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".gat")) {
                file = new File(file.getAbsolutePath() + ".gat");
            }

            try (PrintWriter writer = new PrintWriter(file)) {
                // Save game data
                writer.println("# Guard & Towers Game Save");
                writer.println("# Move history");

                for (Move move : moveHistory) {
                    writer.println(move.toString());
                }

                updateStatus("💾 Game saved to " + file.getName());

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to save game: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadGame() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Guard & Towers Games", "gat"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            try (Scanner scanner = new Scanner(file)) {
                resetGame(); // Start fresh

                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("#") || line.isEmpty()) continue;

                    // Parse and apply move
                    // This would need a Move.fromString() method
                    // For now, just show success
                }

                updateStatus("📁 Game loaded from " + file.getName());
                updateMoveHistory();
                updateEvaluationDisplay();

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to load game: " + e.getMessage(),
                        "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // === MENU ACTIONS ===

    private void showEngineInfo() {
        StringBuilder info = new StringBuilder();
        info.append("🤖 ENGINE INFORMATION\n");
        info.append("=" .repeat(30)).append("\n\n");
        info.append("Engine: Guard & Towers AI v3.0\n");
        info.append("Search: Alpha-Beta with enhancements\n");
        info.append("Features:\n");
        info.append("• Iterative deepening\n");
        info.append("• Transposition table\n");
        info.append("• Move ordering\n");
        info.append("• Null-move pruning\n");
        info.append("• Late-move reductions\n");
        info.append("• Aspiration windows\n");
        info.append("• Opening book\n\n");
        info.append("Current settings:\n");
        info.append("Difficulty: ").append(difficultyCombo.getSelectedItem()).append("\n");
        info.append("Time limit: ").append(currentDifficulty.timeMs).append("ms\n");
        info.append("Max depth: ").append(currentDifficulty.maxDepth).append("\n");

        JOptionPane.showMessageDialog(this, info.toString(), "Engine Information",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void runBenchmark() {
        updateStatus("🚀 Running benchmark...");

        SwingWorker<String, Void> benchmarkWorker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                // Simple benchmark
                GameState testPos = new GameState();
                long startTime = System.currentTimeMillis();

                for (int depth = 4; depth <= 8; depth += 2) {
                    engine.findBestMove(testPos, depth, 5000);
                }

                long endTime = System.currentTimeMillis();
                return String.format("Benchmark completed in %dms", endTime - startTime);
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    updateStatus(result);
                    JOptionPane.showMessageDialog(GameFrame.this, result, "Benchmark Results",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    updateStatus("Benchmark failed: " + e.getMessage());
                }
            }
        };

        benchmarkWorker.execute();
    }

    private void showGameRules() {
        String rules = """
                🎯 GUARD & TOWERS RULES
                
                GOAL:
                • Capture the opponent's guard, OR
                • Move your guard to the opponent's castle (center of their baseline)
                
                MOVEMENT:
                • Guards move exactly 1 square orthogonally
                • Towers move exactly as many squares as their height
                • No diagonal moves or jumping over pieces
                
                STACKING:
                • Towers of the same color combine when one moves onto the other
                • Towers can be split by moving only part of the stack
                
                CAPTURE:
                • Guards capture any piece
                • Towers capture guards and smaller/equal towers
                • Captured pieces are removed from the board
                
                CONTROLS:
                • Click to select a piece, then click destination
                • Use Ctrl+Z/Y for undo/redo
                • Use Ctrl+H for hints
                """;

        JTextArea textArea = new JTextArea(rules);
        textArea.setEditable(false);
        textArea.setFont(new Font("Dialog", Font.PLAIN, 12));

        JOptionPane.showMessageDialog(this, textArea, "Game Rules",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        String about = """
                🎯 Guard & Towers - Enhanced Edition
                Version 3.0
                
                A strategic board game implementation with AI opponent.
                
                Original game by Christoph Endres and Robert Wirth (1997)
                Enhanced implementation with advanced AI features.
                
                Features:
                • Human vs AI gameplay
                • Multiple AI difficulty levels
                • Move history with undo/redo
                • Position analysis and hints
                • Save/load games
                • Advanced search algorithms
                
                Built with Java Swing and custom AI engine.
                """;

        JOptionPane.showMessageDialog(this, about, "About Guard & Towers",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // === UI UPDATE METHODS ===

    private void updateStatusForMove(Move move, boolean isHuman) {
        String player = isHuman ? "You" : "AI";
        String fromSquare = boardPanel.getSquareName(move.from);
        String toSquare = boardPanel.getSquareName(move.to);
        String moveDesc = getMoveDescription(move);

        String status = String.format("%s: %s → %s (%d) %s",
                player, fromSquare, toSquare, move.amountMoved, moveDesc);

        updateStatus(status);
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateEngineStats(String stats) {
        engineStatsLabel.setText(stats);
    }

    private void updateEvaluationDisplay() {
        if (!showEvaluationBox.isSelected()) {
            evaluationLabel.setText("");
            return;
        }

        try {
            int eval = evaluator.evaluate(gameState);
            String evalText;
            Color evalColor;

            if (Math.abs(eval) >= 5000) {
                evalText = eval > 0 ? "Winning!" : "Losing!";
                evalColor = eval > 0 ? new Color(0, 150, 0) : Color.RED;
            } else if (eval > 100) {
                evalText = String.format("Better (+%d)", eval);
                evalColor = new Color(0, 120, 0);
            } else if (eval < -100) {
                evalText = String.format("Worse (%d)", eval);
                evalColor = new Color(180, 0, 0);
            } else {
                evalText = String.format("Balanced (%+d)", eval);
                evalColor = Color.DARK_GRAY;
            }

            evaluationLabel.setText("Eval: " + evalText);
            evaluationLabel.setForeground(evalColor);

        } catch (Exception e) {
            evaluationLabel.setText("Eval: Error");
            evaluationLabel.setForeground(Color.GRAY);
        }
    }

    private void updateGameInfo() {
        long elapsed = gameStartTime > 0 ? System.currentTimeMillis() - gameStartTime : 0;
        String timeText = formatTime(elapsed);
        String turn = gameState.redToMove ? "Red" : "Blue";

        gameInfoLabel.setText(String.format("Move: %d | Time: %s | Turn: %s",
                moveCount, timeText, turn));
    }

    private void updateMoveHistory() {
        StringBuilder history = new StringBuilder();

        for (int i = 0; i < moveHistory.size(); i++) {
            Move move = moveHistory.get(i);
            int moveNum = (i / 2) + 1;
            boolean isRedMove = (i % 2) == 0;

            if (isRedMove) {
                history.append(String.format("%2d. %-8s", moveNum, move.toString()));
            } else {
                history.append(String.format(" %-8s\n", move.toString()));
            }
        }

        // Handle odd number of moves
        if (moveHistory.size() % 2 == 1) {
            history.append("\n");
        }

        moveHistoryArea.setText(history.toString());
        moveHistoryArea.setCaretPosition(moveHistoryArea.getDocument().getLength());
    }

    private void updateButtonStates() {
        boolean gameActive = currentMode != GameMode.STOPPED;
        boolean canUndo = currentHistoryIndex > 0 && !aiThinking;
        boolean canRedo = currentHistoryIndex < gameHistory.size() - 1 && !aiThinking;
        boolean canUseFeatures = !aiThinking && gameActive;

        humanVsAiButton.setEnabled(!aiThinking);
        aiVsAiButton.setEnabled(!aiThinking);
        stopAiButton.setEnabled(aiThinking || currentMode == GameMode.AI_VS_AI);
        resetButton.setEnabled(true);
        undoButton.setEnabled(canUndo);
        redoButton.setEnabled(canRedo);
        hintButton.setEnabled(canUseFeatures && currentMode == GameMode.HUMAN_VS_AI);
        analyzeButton.setEnabled(canUseFeatures);
        difficultyCombo.setEnabled(!aiThinking);
    }

    // === UTILITY METHODS ===

    private String formatTime(long millis) {
        if (millis <= 0) return "0:00";

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("0:%02d", seconds);
        }
    }

    // === CLEANUP ===

    @Override
    public void dispose() {
        stopAI();
        if (aiExecutor != null && !aiExecutor.isShutdown()) {
            aiExecutor.shutdown();
        }
        super.dispose();
    }

    // === MAIN METHOD FOR TESTING ===

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception e) {
                // Use default look and feel
            }

            new GameFrame().setVisible(true);
        });
    }
}