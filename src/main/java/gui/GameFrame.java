package gui;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.engine.TimedMinimax;
import GaT.search.Minimax;
import GaT.search.MoveGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GameFrame extends JFrame {
    // Thread-safe game state management
    private volatile GameState state;
    private BoardPanel board;
    private volatile boolean aiThinking = false;
    private volatile boolean gameInProgress = true;
    private final Object stateLock = new Object();

    // Human vs AI mode fields
    private volatile boolean humanVsAiMode = false;
    private volatile boolean humanIsRed = true; // true = human plays red, false = human plays blue

    // Thread management
    private ExecutorService aiExecutor;
    private Future<?> currentAITask;

    // UI Components
    private JButton humanVsAiButton;
    private JButton aiVsHumanButton;
    private JButton aiVsAiButton;
    private JButton resetButton;
    private JButton stopAIButton;
    private JLabel statusLabel;

    // Board configuration - FIXED starting positions
    static String boardString = "7/2RG4/1b11r1b32/1b15/7/6r3/5BG1 r"; // Original tactical position
    static String standardStart = "b1b11BG1b1b1/2b11b12/3b13/7/3r13/2r11r12/r1r11RG1r1r1 r"; // CORRECTED: Guards on their own castles

    public GameFrame() {
        super("Guard & Towers - ULTIMATE AI (PVS + Quiescence) - HUMAN vs AI");

        // Initialize thread pool for AI
        aiExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AI-Worker");
            t.setDaemon(true);
            return t;
        });

        initializeGame();
        initializeUI();
        setupKeyboardShortcuts();
    }

    private void initializeGame() {
        synchronized (stateLock) {
            try {
                // Try the original board string first
                state = GameState.fromFen(boardString);
                System.out.println("✅ Game initialized with custom position - Red to move: " + state.redToMove);

                // VALIDATION: Ensure game state is valid
                List<Move> testMoves = MoveGenerator.generateAllMoves(state);
                System.out.println("✅ Legal moves available: " + testMoves.size());

                // Debug: Check if position is game over
                boolean isGameOver = Minimax.isGameOver(state);
                System.out.println("✅ Game over check: " + isGameOver);

                if (isGameOver || testMoves.isEmpty()) {
                    System.out.println("⚠️ Custom position is game over or has no moves, trying standard start");
                    state = GameState.fromFen(standardStart);
                    testMoves = MoveGenerator.generateAllMoves(state);
                    isGameOver = Minimax.isGameOver(state);
                    System.out.println("✅ Standard position - Legal moves: " + testMoves.size() + ", Game over: " + isGameOver);
                }

            } catch (Exception e) {
                System.err.println("❌ Failed to load positions, using default: " + e.getMessage());
                state = new GameState(); // Fallback to default starting position
                List<Move> testMoves = MoveGenerator.generateAllMoves(state);
                boolean isGameOver = Minimax.isGameOver(state);
                System.out.println("✅ Default game initialized - Red to move: " + state.redToMove +
                        ", Legal moves: " + testMoves.size() + ", Game over: " + isGameOver);
            }

            // Initialize game mode flags
            gameInProgress = true;
            humanVsAiMode = false;
            humanIsRed = true;
            aiThinking = false;
        }
    }

    private void initializeUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create board panel with thread-safe state access
        board = new BoardPanel(getStateCopy(), this::onMoveSelected);
        add(board, BorderLayout.CENTER);

        // Create control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.SOUTH);

        // Create status bar
        statusLabel = new JLabel("✅ Ready - Choose your game mode: Human vs AI, AI vs Human, or AI vs AI");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.NORTH);

        // Window settings
        setSize(750, 800); // Extra width and height for all controls
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);

        // Proper cleanup when window closes
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cleanup();
            }
        });

        updateUI();
        updateButtonStates();
    }

    private void setupKeyboardShortcuts() {
        // Add keyboard shortcuts for common actions
        JRootPane rootPane = getRootPane();

        // R for reset
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("R"), "reset");
        rootPane.getActionMap().put("reset", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { resetGame(); }
        });

        // H for Human vs AI
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("H"), "humanVsAi");
        rootPane.getActionMap().put("humanVsAi", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!aiThinking) startHumanVsAI(true);
            }
        });

        // B for AI vs Human (Blue)
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("B"), "aiVsHuman");
        rootPane.getActionMap().put("aiVsHuman", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!aiThinking) startHumanVsAI(false);
            }
        });

        // A for AI vs AI
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("A"), "aiVsAi");
        rootPane.getActionMap().put("aiVsAi", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!aiThinking) runAiMatch();
            }
        });

        // Space to stop AI
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("SPACE"), "stopAi");
        rootPane.getActionMap().put("stopAi", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { stopAI(); }
        });
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 5, 5)); // 2 rows, 4 columns
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Human vs AI button (Human = Red, AI = Blue)
        humanVsAiButton = new JButton("🧑 Human vs 🤖 AI");
        humanVsAiButton.setToolTipText("You play as Red, AI plays as Blue (Press H)");
        humanVsAiButton.addActionListener(e -> {
            if (!aiThinking) {
                startHumanVsAI(true); // Human plays Red
            }
        });

        // AI vs Human button (AI = Red, Human = Blue)
        aiVsHumanButton = new JButton("🤖 AI vs 🧑 Human");
        aiVsHumanButton.setToolTipText("AI plays as Red, you play as Blue (Press B)");
        aiVsHumanButton.addActionListener(e -> {
            if (!aiThinking) {
                startHumanVsAI(false); // Human plays Blue
            }
        });

        // AI vs AI button
        aiVsAiButton = new JButton("🤖 AI vs 🤖 AI");
        aiVsAiButton.setToolTipText("Watch AI play against itself (Press A)");
        aiVsAiButton.addActionListener(e -> {
            if (!aiThinking) {
                runAiMatch();
            }
        });

        // Reset game button
        resetButton = new JButton("🔄 Reset");
        resetButton.setToolTipText("Reset game to starting position (Press R)");
        resetButton.addActionListener(e -> resetGame());

        // Stop AI button
        stopAIButton = new JButton("⛔ Stop");
        stopAIButton.setToolTipText("Stop AI thinking (Press Space)");
        stopAIButton.addActionListener(e -> stopAI());
        stopAIButton.setEnabled(false);

        // Evaluate position button
        JButton evaluateButton = new JButton("📊 Eval");
        evaluateButton.setToolTipText("Show position evaluation");
        evaluateButton.addActionListener(e -> showPositionEvaluation());

        // Strategy comparison button
        JButton compareButton = new JButton("⚖️ Compare");
        compareButton.setToolTipText("Compare different AI strategies");
        compareButton.addActionListener(e -> showStrategyComparison());

        // Help button
        JButton helpButton = new JButton("❓ Help");
        helpButton.setToolTipText("Show game rules and controls");
        helpButton.addActionListener(e -> showHelp());

        // Add buttons to panel (first row)
        panel.add(humanVsAiButton);
        panel.add(aiVsHumanButton);
        panel.add(aiVsAiButton);
        panel.add(resetButton);

        // Second row
        panel.add(stopAIButton);
        panel.add(evaluateButton);
        panel.add(compareButton);
        panel.add(helpButton);

        return panel;
    }

    private void startHumanVsAI(boolean humanPlaysRed) {
        synchronized (stateLock) {
            // Reset to standard starting position for best Human vs AI experience
            try {
                state = GameState.fromFen(standardStart);
                System.out.println("🎮 Human vs AI starting with standard position");
            } catch (Exception e) {
                try {
                    state = GameState.fromFen(boardString);
                    System.out.println("🎮 Human vs AI starting with custom position");
                } catch (Exception e2) {
                    state = new GameState();
                    System.out.println("🎮 Human vs AI starting with default position");
                }
            }
            gameInProgress = true;
            humanVsAiMode = true;
            humanIsRed = humanPlaysRed;
            aiThinking = false;
        }

        updateUI();
        updateButtonStates();

        String humanColor = humanPlaysRed ? "Red" : "Blue";
        String aiColor = humanPlaysRed ? "Blue" : "Red";

        updateStatus("🎮 Human vs AI - You are " + humanColor + ", AI is " + aiColor +
                (state.redToMove == humanPlaysRed ? " - Your turn!" : " - AI thinking..."));

        System.out.println("🎮 Starting Human vs AI - Human: " + humanColor + ", AI: " + aiColor);

        // If AI should move first
        if (state.redToMove != humanPlaysRed) {
            makeAIMove();
        }
    }

    private void onMoveSelected(Move move) {
        System.out.println("🎮 onMoveSelected called with move: " + move);
        System.out.println("🎮 Current mode - humanVsAiMode: " + humanVsAiMode + ", aiThinking: " + aiThinking + ", gameInProgress: " + gameInProgress);

        // Prevent moves during AI thinking or game over
        if (aiThinking || !gameInProgress) {
            updateStatus("Please wait...");
            return;
        }

        // Handle different game modes
        if (humanVsAiMode) {
            handleHumanMove(move);
        } else {
            // In non-human mode, just show a message
            updateStatus("Not in Human vs AI mode - use AI vs AI or start Human vs AI mode");
            System.out.println("⚠️ Move attempted but not in human vs AI mode");
        }
    }

    private void handleHumanMove(Move move) {
        System.out.println("🎮 handleHumanMove called with: " + move);
        System.out.println("🎮 Human is Red: " + humanIsRed + ", Red to move: " + state.redToMove);

        // Check if it's the human's turn
        boolean isHumanTurn = (humanIsRed && state.redToMove) || (!humanIsRed && !state.redToMove);
        System.out.println("🎮 Is human's turn: " + isHumanTurn);

        if (!isHumanTurn) {
            updateStatus("⏳ Wait for AI's turn to complete");
            return;
        }

        // Validate and apply human move
        synchronized (stateLock) {
            if (!gameInProgress) {
                System.out.println("⚠️ Game not in progress");
                return;
            }

            System.out.println("🎮 Human move attempt: " + move + " (Human is " +
                    (humanIsRed ? "Red" : "Blue") + ", Red to move: " + state.redToMove + ")");

            // Validate move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            System.out.println("🎮 Legal moves available: " + legalMoves.size());

            if (!legalMoves.contains(move)) {
                System.out.println("❌ Illegal move: " + move);
                updateStatus("❌ Illegal move: " + move);

                // Reset status after 2 seconds
                javax.swing.Timer timer = new javax.swing.Timer(2000, e -> {
                    String currentPlayer = (humanIsRed && state.redToMove) || (!humanIsRed && !state.redToMove)
                            ? "Your turn" : "AI thinking...";
                    updateStatus("🎮 Human vs AI - " + currentPlayer);
                });
                timer.setRepeats(false);
                timer.start();
                return;
            }

            // Apply the move
            state.applyMove(move);
            System.out.println("✅ Human move applied: " + move);

            // Check for game over
            if (Minimax.isGameOver(state)) {
                gameInProgress = false;
                humanVsAiMode = false;
                String winner = state.redToMove ? "Blue" : "Red"; // Winner is opposite of current player
                String result = winner.equals(humanIsRed ? "Red" : "Blue") ? "🎉 You Won!" : "😔 AI Won!";
                updateStatus("🏁 Game Over! " + result);
                updateButtonStates();
                updateUI();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, result + "\n\nGreat game!",
                            "Game Over", JOptionPane.INFORMATION_MESSAGE);
                });
                return;
            }
        }

        updateUI();

        // Now it's AI's turn
        updateStatus("🤖 AI thinking...");
        System.out.println("🎮 Calling makeAIMove() after human move");
        makeAIMove();
    }

    private void makeAIMove() {
        if (!gameInProgress || !humanVsAiMode) {
            System.out.println("⚠️ makeAIMove called but not in human vs AI mode");
            return;
        }

        System.out.println("🤖 makeAIMove() called - starting AI thinking");
        aiThinking = true;
        updateButtonStates();

        currentAITask = aiExecutor.submit(() -> {
            try {
                GameState currentState = getStateCopy();

                System.out.println("🤖 AI calculating move - Red to move: " + currentState.redToMove);
                System.out.println("🤖 Human is Red: " + humanIsRed + ", so AI should be: " + (humanIsRed ? "Blue" : "Red"));

                // Check if it's really AI's turn
                boolean isAiTurn = (humanIsRed && !currentState.redToMove) || (!humanIsRed && currentState.redToMove);
                System.out.println("🤖 Is AI's turn: " + isAiTurn);

                if (!isAiTurn) {
                    System.out.println("⚠️ Not AI's turn, returning");
                    SwingUtilities.invokeLater(() -> {
                        aiThinking = false;
                        updateButtonStates();
                        updateStatus("🎮 Your turn!");
                    });
                    return;
                }

                // Check for game over
                if (Minimax.isGameOver(currentState)) {
                    System.out.println("🏁 Game is over");
                    SwingUtilities.invokeLater(() -> {
                        aiThinking = false;
                        gameInProgress = false;
                        humanVsAiMode = false;
                        updateStatus("🏁 Game Over!");
                        updateButtonStates();
                        showGameOverDialog();
                    });
                    return;
                }

                List<Move> legalMoves = MoveGenerator.generateAllMoves(currentState);
                System.out.println("🤖 Legal moves available: " + legalMoves.size());

                if (legalMoves.isEmpty()) {
                    System.out.println("❌ No legal moves for AI");
                    SwingUtilities.invokeLater(() -> {
                        aiThinking = false;
                        gameInProgress = false;
                        humanVsAiMode = false;
                        updateStatus("🏁 Game Over - No legal moves!");
                        updateButtonStates();
                    });
                    return;
                }

                long startTime = System.currentTimeMillis();

                // AI makes its move using TimedMinimax
                Move aiMove = TimedMinimax.findBestMoveUltimate(currentState, 6, 3000); // 3 second think time

                if (aiMove == null) {
                    System.err.println("❌ AI returned null move, using first legal move");
                    aiMove = legalMoves.get(0);
                }

                long moveTime = System.currentTimeMillis() - startTime;
                System.out.println("🤖 AI selected move: " + aiMove + " (" + moveTime + "ms)");

                // Apply AI move
                synchronized (stateLock) {
                    if (!gameInProgress || !humanVsAiMode) {
                        System.out.println("⚠️ Game ended while AI was thinking");
                        return;
                    }

                    // Validate AI move
                    List<Move> currentLegalMoves = MoveGenerator.generateAllMoves(state);
                    if (currentLegalMoves.contains(aiMove)) {
                        state.applyMove(aiMove);
                        System.out.println("✅ AI move applied: " + aiMove);
                    } else {
                        System.err.println("❌ Invalid AI move: " + aiMove);
                        if (!currentLegalMoves.isEmpty()) {
                            aiMove = currentLegalMoves.get(0);
                            state.applyMove(aiMove);
                            System.out.println("🚨 Applied fallback move: " + aiMove);
                        }
                    }

                    // Check for game over after AI move
                    if (Minimax.isGameOver(state)) {
                        gameInProgress = false;
                        humanVsAiMode = false;
                        String winner = state.redToMove ? "Blue" : "Red";
                        String result = winner.equals(humanIsRed ? "Red" : "Blue") ? "🎉 You Won!" : "😔 AI Won!";

                        SwingUtilities.invokeLater(() -> {
                            aiThinking = false;
                            updateUI();
                            updateStatus("🏁 Game Over! " + result);
                            updateButtonStates();

                            JOptionPane.showMessageDialog(this, result + "\n\nGreat game!",
                                    "Game Over", JOptionPane.INFORMATION_MESSAGE);
                        });
                        return;
                    }
                }

                // Update UI after AI move
                final Move finalAiMove = aiMove;
                final long finalMoveTime = moveTime;
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateUI();
                    updateStatus("🤖 AI played: " + finalAiMove + " (" + finalMoveTime + "ms) - Your turn!");
                    updateButtonStates();
                });

            } catch (Exception e) {
                System.err.println("❌ AI move error: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateStatus("❌ AI error: " + e.getMessage());
                    updateButtonStates();
                });
            }
        });
    }

    private void showHelp() {
        String helpText = """
            🎯 GUARD & TOWERS - How to Play:
            
            📋 OBJECTIVE:
            • Capture the opponent's guard, OR
            • Move your guard to the opponent's castle (center of opposite baseline)
            
            🎮 HUMAN CONTROLS:
            • Click a piece to select it
            • Click destination to move
            • Only legal moves are allowed
            
            📐 MOVEMENT RULES:
            • Guard moves exactly 1 square (orthogonally)
            • Tower moves exactly as many squares as its height
            • No diagonal moves, no jumping over pieces
            
            🏗️ STACKING:
            • Same-color towers combine when one moves to another
            • You can split towers by moving only part of them
            
            ⚔️ CAPTURING:
            • Guard captures any piece
            • Any tower captures the guard
            • Tower captures equal/smaller tower
            
            ⌨️ KEYBOARD SHORTCUTS:
            • H - Human vs AI    • B - AI vs Human
            • A - AI vs AI       • R - Reset game
            • Space - Stop AI
            
            💡 TIPS:
            • Red pieces: GUARD (G), towers (numbers show height)
            • Blue pieces: guard (g), towers (numbers show height)
            • Castle squares are in the center of each baseline
            """;

        JOptionPane.showMessageDialog(this, helpText, "Game Rules & Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    private void testSingleAIMove() {
        if (aiThinking) {
            updateStatus("AI is already thinking...");
            return;
        }

        updateStatus("🧪 Testing AI move generation...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                GameState testState = getStateCopy();
                System.out.println("\n🧪 === AI MOVE TEST ===");
                System.out.println("Current state - Red to move: " + testState.redToMove);
                testState.printBoard();

                List<Move> legalMoves = MoveGenerator.generateAllMoves(testState);
                System.out.println("Legal moves: " + legalMoves.size());

                if (legalMoves.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("❌ No legal moves available!");
                        JOptionPane.showMessageDialog(this, "No legal moves available in current position!",
                                "Test Result", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }

                long startTime = System.currentTimeMillis();
                Move aiMove = TimedMinimax.findBestMoveUltimate(testState, 5, 3000);
                long endTime = System.currentTimeMillis();

                SwingUtilities.invokeLater(() -> {
                    if (aiMove != null) {
                        updateStatus("✅ AI Test successful: " + aiMove + " (" + (endTime - startTime) + "ms)");
                        JOptionPane.showMessageDialog(this,
                                "AI Move Test Result:\n\n" +
                                        "Best Move: " + aiMove + "\n" +
                                        "Time: " + (endTime - startTime) + "ms\n" +
                                        "Legal Moves: " + legalMoves.size(),
                                "AI Test Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        updateStatus("❌ AI Test failed: Returned null move");
                        JOptionPane.showMessageDialog(this,
                                "AI Test FAILED!\n\n" +
                                        "Returned: null\n" +
                                        "Legal Moves Available: " + legalMoves.size() + "\n" +
                                        "This indicates a bug in the AI search.",
                                "AI Test Failure", JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("❌ AI Test error: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "AI Test ERROR!\n\n" + e.getMessage(),
                            "AI Test Error", JOptionPane.ERROR_MESSAGE);
                });
                e.printStackTrace();
            }
        });
    }

    private void resetGame() {
        stopAI();

        synchronized (stateLock) {
            try {
                // Try standard starting position first (more likely to work well)
                state = GameState.fromFen(standardStart);
                System.out.println("🔄 Game reset to standard starting position");
            } catch (Exception e) {
                try {
                    // Fallback to original position
                    state = GameState.fromFen(boardString);
                    System.out.println("🔄 Game reset to custom position");
                } catch (Exception e2) {
                    // Final fallback
                    state = new GameState();
                    System.out.println("🔄 Game reset to default position");
                }
            }
            gameInProgress = true;
            aiThinking = false;
            humanVsAiMode = false;
            humanIsRed = true;
        }

        updateUI();
        updateButtonStates();
        updateStatus("✅ Game reset - Choose a game mode");
        System.out.println("🔄 Game reset completed");
    }

    private void stopAI() {
        aiThinking = false;

        if (currentAITask != null && !currentAITask.isDone()) {
            currentAITask.cancel(true);
        }

        updateButtonStates();

        if (humanVsAiMode) {
            updateStatus("🛑 AI stopped - Your turn");
        } else {
            updateStatus("🛑 AI stopped");
        }
    }

    private void runAiMatch() {
        // Stop any existing AI and reset state properly
        stopAI();

        // Disable human vs AI mode when starting AI vs AI
        synchronized (stateLock) {
            humanVsAiMode = false;
            // Reset to standard starting position for AI vs AI
            try {
                state = GameState.fromFen(standardStart);
                System.out.println("🔄 AI vs AI starting with standard position");
            } catch (Exception e) {
                try {
                    state = GameState.fromFen(boardString);
                    System.out.println("🔄 AI vs AI starting with custom position");
                } catch (Exception e2) {
                    state = new GameState();
                    System.out.println("🔄 AI vs AI starting with default position");
                }
            }
            gameInProgress = true;
            aiThinking = false;
        }

        updateUI();
        updateButtonStates();

        // Debug the starting position
        GameState debugState = getStateCopy();
        System.out.println("🧪 AI vs AI Debug - Red to move: " + debugState.redToMove);
        System.out.println("🧪 Game over check: " + Minimax.isGameOver(debugState));
        List<Move> debugMoves = MoveGenerator.generateAllMoves(debugState);
        System.out.println("🧪 Legal moves: " + debugMoves.size());

        if (debugMoves.isEmpty() || Minimax.isGameOver(debugState)) {
            updateStatus("❌ Cannot start AI vs AI - position is game over or no legal moves");
            return;
        }

        aiThinking = true;
        updateButtonStates();
        updateStatus("🤖 vs 🤖 AI match starting...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                final int[] moveCount = {0};
                final int maxMoves = 200; // Prevent infinite games

                System.out.println("🤖 AI vs AI thread started");

                while (gameInProgress && aiThinking && !Thread.currentThread().isInterrupted() && moveCount[0] < maxMoves) {
                    // Get current state snapshot
                    GameState currentState = getStateCopy();

                    System.out.println("🤖 Move " + (moveCount[0] + 1) + " - Red to move: " + currentState.redToMove);

                    // Check for game over
                    if (Minimax.isGameOver(currentState)) {
                        System.out.println("🏁 Game over detected");
                        break;
                    }

                    // Check for legal moves
                    List<Move> legalMoves = MoveGenerator.generateAllMoves(currentState);
                    System.out.println("🤖 Legal moves available: " + legalMoves.size());

                    if (legalMoves.isEmpty()) {
                        System.err.println("❌ No legal moves available in AI vs AI");
                        break;
                    }

                    long startTime = System.currentTimeMillis();
                    String currentPlayer = currentState.redToMove ? "Red" : "Blue";

                    // Use Ultimate AI with null protection
                    Move move = TimedMinimax.findBestMoveUltimate(currentState, 6, 1500); // Faster for AI vs AI

                    // CRITICAL NULL CHECK
                    if (move == null) {
                        System.err.println("❌ AI returned null, using first legal move");
                        move = legalMoves.get(0);
                    }

                    long moveTime = System.currentTimeMillis() - startTime;

                    // Apply move with validation
                    synchronized (stateLock) {
                        if (!gameInProgress || !aiThinking) break;

                        // Final validation
                        List<Move> currentLegalMoves = MoveGenerator.generateAllMoves(state);
                        if (currentLegalMoves.contains(move)) {
                            state.applyMove(move);
                            moveCount[0]++;
                            System.out.println("✅ " + currentPlayer + " played: " + move + " (" + moveTime + "ms)");
                        } else {
                            System.err.println("❌ Invalid move: " + move);
                            if (!currentLegalMoves.isEmpty()) {
                                move = currentLegalMoves.get(0);
                                state.applyMove(move);
                                moveCount[0]++;
                                System.out.println("🚨 Fallback move: " + move);
                            } else {
                                System.err.println("❌ No valid moves available!");
                                break;
                            }
                        }
                    }

                    // Update UI
                    final int currentMoveCount = moveCount[0];
                    final Move finalMove = move;
                    final long finalMoveTime = moveTime;
                    SwingUtilities.invokeLater(() -> {
                        updateUI();
                        updateStatus("🤖 vs 🤖 AI - " + currentPlayer + " played: " + finalMove + " (" + finalMoveTime + "ms) [Move " + currentMoveCount + "]");
                    });

                    // Pause between moves for visibility
                    Thread.sleep(1000);
                }

                // Game ended
                final int finalMoveCount = moveCount[0];
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();

                    if (finalMoveCount >= maxMoves) {
                        updateStatus("🤖 vs 🤖 AI ended - Move limit reached");
                        JOptionPane.showMessageDialog(this, "Game ended due to move limit (" + maxMoves + " moves)",
                                "Game Ended", JOptionPane.INFORMATION_MESSAGE);
                    } else if (Minimax.isGameOver(getStateCopy())) {
                        gameInProgress = false;
                        showGameOverDialog();
                    } else {
                        updateStatus("🤖 vs 🤖 AI stopped");
                    }
                });

            } catch (InterruptedException e) {
                System.out.println("🛑 AI vs AI interrupted");
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("🤖 vs 🤖 AI interrupted");
                });
            } catch (Exception e) {
                System.err.println("❌ AI vs AI Exception: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("❌ AI vs AI error: " + e.getMessage());
                });
            }
        });
    }

    private void showPositionEvaluation() {
        if (aiThinking) {
            updateStatus("Please wait for AI to finish thinking");
            return;
        }

        GameState currentState = getStateCopy();

        // Quick evaluation
        int eval = Minimax.evaluate(currentState, 0);
        String evalStr = String.format("Position evaluation: %+d", eval);

        if (eval > 1000) evalStr += " (Advantage: " + (currentState.redToMove ? "Red" : "Blue") + ")";
        else if (eval < -1000) evalStr += " (Advantage: " + (currentState.redToMove ? "Blue" : "Red") + ")";
        else evalStr += " (Roughly equal)";

        // Show legal moves count
        List<Move> legalMoves = MoveGenerator.generateAllMoves(currentState);
        evalStr += "\nLegal moves: " + legalMoves.size();

        // Show whose turn
        evalStr += "\nCurrent turn: " + (currentState.redToMove ? "Red" : "Blue");

        if (humanVsAiMode) {
            evalStr += "\nYou are: " + (humanIsRed ? "Red" : "Blue");
            evalStr += "\nGame mode: Human vs AI";
        } else {
            evalStr += "\nGame mode: " + (aiThinking ? "AI vs AI (running)" : "Ready for new game");
        }

        JOptionPane.showMessageDialog(this, evalStr, "Position Analysis", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showStrategyComparison() {
        if (aiThinking) {
            updateStatus("Please wait for AI to finish thinking...");
            return;
        }

        GameState currentState = getStateCopy();

        // Run comparison in background
        aiExecutor.submit(() -> {
            try {
                SwingUtilities.invokeLater(() -> updateStatus("Comparing AI strategies... (FIXED version)"));

                System.out.println("\n=== STRATEGY COMPARISON - FIXED ===");
                currentState.printBoard();

                // Test different strategies
                SearchConfig.SearchStrategy[] strategies = SearchConfig.SearchStrategy.values();

                StringBuilder results = new StringBuilder("Strategy Comparison Results (FIXED):\n\n");

                for (SearchConfig.SearchStrategy strategy : strategies) {
                    long startTime = System.currentTimeMillis();

                    Move move = null;
                    try {
                        // Use unified findBestMoveWithStrategy method
                        move = TimedMinimax.findBestMoveWithStrategy(currentState, 4, 3000, strategy);
                    } catch (Exception e) {
                        System.err.println("Error testing " + strategy + ": " + e.getMessage());
                    }

                    long endTime = System.currentTimeMillis();
                    long searchTime = endTime - startTime;

                    // Evaluate the resulting position if move is valid
                    int evaluation = 0;
                    if (move != null) {
                        try {
                            GameState resultState = currentState.copy();
                            resultState.applyMove(move);
                            evaluation = Minimax.evaluate(resultState, 0);
                        } catch (Exception e) {
                            System.err.println("Error evaluating result for " + strategy + ": " + e.getMessage());
                        }
                    }

                    results.append(String.format("%s:\n", strategy.displayName));
                    results.append(String.format("  Move: %s\n", move != null ? move : "NULL/ERROR"));
                    results.append(String.format("  Evaluation: %+d\n", evaluation));
                    results.append(String.format("  Time: %dms\n\n", searchTime));

                    System.out.printf("FIXED %s: Move=%s, Eval=%+d, Time=%dms\n",
                            strategy.displayName, move, evaluation, searchTime);
                }

                SwingUtilities.invokeLater(() -> {
                    updateStatus("✅ Strategy comparison completed (FIXED)!");

                    JTextArea textArea = new JTextArea(results.toString());
                    textArea.setEditable(false);
                    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

                    JScrollPane scrollPane = new JScrollPane(textArea);
                    scrollPane.setPreferredSize(new Dimension(500, 400));

                    JOptionPane.showMessageDialog(this, scrollPane,
                            "AI Strategy Comparison - FIXED", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("❌ Strategy comparison failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Error comparing strategies: " + e.getMessage(),
                            "Comparison Error", JOptionPane.ERROR_MESSAGE);
                });
                e.printStackTrace();
            }
        });
    }

    // Thread-safe state access
    private GameState getStateCopy() {
        synchronized (stateLock) {
            return state.copy();
        }
    }

    private void updateUI() {
        SwingUtilities.invokeLater(() -> {
            GameState currentState = getStateCopy();
            board.updateState(currentState);
            board.repaint();

            // Update window title with current turn and mode
            String turn = currentState.redToMove ? "Red" : "Blue";
            String mode;
            if (humanVsAiMode) {
                mode = humanIsRed ? "Human(Red) vs AI(Blue)" : "AI(Red) vs Human(Blue)";
            } else {
                mode = aiThinking ? "AI vs AI (Running)" : "Ready";
            }
            setTitle("Guard & Towers - " + mode + " - " + turn + " to move");
        });
    }

    private void updateButtonStates() {
        SwingUtilities.invokeLater(() -> {
            boolean canStartGame = !aiThinking && gameInProgress;
            humanVsAiButton.setEnabled(canStartGame);
            aiVsHumanButton.setEnabled(canStartGame);
            aiVsAiButton.setEnabled(canStartGame);
            stopAIButton.setEnabled(aiThinking);
            resetButton.setEnabled(true); // Always allow reset
        });
    }

    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
        });
    }

    private void showGameOverDialog() {
        SwingUtilities.invokeLater(() -> {
            String winner = determineWinner();
            updateStatus("🏁 Game Over - " + winner);

            String message = winner + "\n\nAI Engine: ULTIMATE (FIXED)";
            if (humanVsAiMode) {
                boolean humanWon = (humanIsRed && winner.contains("Red")) || (!humanIsRed && winner.contains("Blue"));
                message = (humanWon ? "🎉 Congratulations! You won!" : "😔 AI won this time!") +
                        "\n\n" + winner + "\n\nGreat game!";
            }

            JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private String determineWinner() {
        synchronized (stateLock) {
            // Check for captured guards
            if (state.redGuard == 0) return "Blue wins! (Red guard captured)";
            if (state.blueGuard == 0) return "Red wins! (Blue guard captured)";

            // Check for castle captures
            long redCastlePos = GameState.bit(GameState.getIndex(0, 3)); // D1 (Blue's castle)
            long blueCastlePos = GameState.bit(GameState.getIndex(6, 3)); // D7 (Red's castle)

            if ((state.redGuard & redCastlePos) != 0) {
                return "Red wins! (Reached Blue's castle)";
            }
            if ((state.blueGuard & blueCastlePos) != 0) {
                return "Blue wins! (Reached Red's castle)";
            }

            return "Game over!";
        }
    }

    private void cleanup() {
        // Stop any running AI tasks
        if (currentAITask != null && !currentAITask.isDone()) {
            currentAITask.cancel(true);
        }

        // Shutdown AI executor
        if (aiExecutor != null && !aiExecutor.isShutdown()) {
            aiExecutor.shutdown();
            try {
                if (!aiExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    aiExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                aiExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("🧹 GameFrame cleanup completed");
    }

    @Override
    public void dispose() {
        // Clean shutdown
        gameInProgress = false;
        stopAI();
        cleanup();
        super.dispose();
    }

    public static void main(String[] args) {
        // Create and show the game with default look and feel
        SwingUtilities.invokeLater(() -> {
            new GameFrame();
        });
    }
}