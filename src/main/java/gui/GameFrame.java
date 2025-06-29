package gui;

import GaT.engine.GameEngine;
import GaT.engine.TimedGameEngine;
import GaT.model.GameState;
import GaT.model.GameRules;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.search.MoveGenerator;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Game GUI using new refactored architecture
 */
public class GameFrame extends JFrame {
    // NEW: Use GameEngine instead of static Minimax
    private final GameEngine gameEngine;
    private final TimedGameEngine timedEngine;

    // Thread-safe game state management
    private volatile GameState state;
    private BoardPanel board;
    private volatile boolean aiThinking = false;
    private volatile boolean gameInProgress = true;
    private final Object stateLock = new Object();

    // Thread management
    private ExecutorService aiExecutor;
    private Future<?> currentAITask;

    // UI Components
    private JButton aiVsAiButton;
    private JButton resetButton;
    private JButton stopAIButton;
    private JLabel statusLabel;

    static String boardString = "7/2RG4/1b11r1b32/1b15/7/6r3/5BG1 r";

    public GameFrame() {
        super("Guards & Towers - NEW ARCHITECTURE (Clean & Fast)");

        // Initialize engines
        gameEngine = new GameEngine();
        timedEngine = new TimedGameEngine();

        // Initialize thread pool for AI
        aiExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AI-Worker");
            t.setDaemon(true);
            return t;
        });

        initializeGame();
        initializeUI();
    }

    private void initializeGame() {
        synchronized (stateLock) {
            try {
                state = GameState.fromFen(boardString);
                System.out.println("✅ Game initialized with new architecture");
                System.out.println("📍 Turn: " + (state.redToMove ? "RED" : "BLUE"));

                // Validate using new API
                List<Move> testMoves = MoveGenerator.generateAllMoves(state);
                System.out.println("✅ Legal moves available: " + testMoves.size());

                gameInProgress = !gameEngine.isGameOver(state);

            } catch (Exception e) {
                System.err.println("❌ Failed to load position: " + e.getMessage());
                state = new GameState(); // Fallback
                gameInProgress = true;
            }
        }
    }

    private void initializeUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create board panel
        board = new BoardPanel(getStateCopy(), this::onMoveSelected);
        add(board, BorderLayout.CENTER);

        // Create control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.SOUTH);

        // Create status bar
        statusLabel = new JLabel("✅ Ready - NEW ARCHITECTURE - No circular dependencies!");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.NORTH);

        // Window settings
        setSize(660, 750);
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);

        updateUI();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        // AI vs AI button
        aiVsAiButton = new JButton("AI vs AI (PVS+Q)");
        aiVsAiButton.addActionListener(e -> {
            if (!aiThinking) {
                runAiMatch();
            }
        });

        // Reset game button
        resetButton = new JButton("Reset Game");
        resetButton.addActionListener(e -> resetGame());

        // Stop AI button
        stopAIButton = new JButton("Stop AI");
        stopAIButton.setEnabled(false);
        stopAIButton.addActionListener(e -> stopAI());

        // Eval button
        JButton evalButton = new JButton("Show Evaluation");
        evalButton.addActionListener(e -> showPositionEvaluation());

        panel.add(aiVsAiButton);
        panel.add(resetButton);
        panel.add(stopAIButton);
        panel.add(evalButton);

        return panel;
    }

    private void onMoveSelected(Move move) {
        if (aiThinking || !gameInProgress) {
            return;
        }

        synchronized (stateLock) {
            // Apply human move
            state.applyMove(move);
            updateUI();

            // Check game over
            if (gameEngine.isGameOver(state)) {
                gameInProgress = false;
                showGameOverDialog();
                return;
            }

            // AI response
            makeAIMove();
        }
    }

    private void makeAIMove() {
        if (!gameInProgress || aiThinking) return;

        aiThinking = true;
        updateButtonStates();
        updateStatus("🤖 AI thinking (using NEW architecture)...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                GameState currentState = getStateCopy();
                long startTime = System.currentTimeMillis();

                // Use new TimedGameEngine
                Move aiMove = timedEngine.findBestMove(
                        currentState,
                        2000, // 2 seconds
                        SearchConfig.SearchStrategy.PVS_Q
                );

                long elapsed = System.currentTimeMillis() - startTime;

                if (aiMove != null) {
                    SwingUtilities.invokeLater(() -> {
                        synchronized (stateLock) {
                            state.applyMove(aiMove);
                            updateUI();

                            String stats = gameEngine.getSearchStats();
                            updateStatus(String.format("🤖 AI played %s in %dms",
                                    aiMove, elapsed));

                            // Show brief stats in console
                            System.out.println("\n" + stats);

                            if (gameEngine.isGameOver(state)) {
                                gameInProgress = false;
                                showGameOverDialog();
                            }
                        }
                        aiThinking = false;
                        updateButtonStates();
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("❌ AI error: " + e.getMessage());
                });
            }
        });
    }

    private void runAiMatch() {
        aiThinking = true;
        updateButtonStates();
        updateStatus("🤖 vs 🤖 AI Match running (NEW architecture)...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                int moveCount = 0;
                final int maxMoves = 200;
                boolean useRed = true;

                while (gameInProgress && moveCount < maxMoves && aiThinking) {
                    GameState currentState = getStateCopy();

                    // Alternate strategies for variety
                    SearchConfig.SearchStrategy strategy = useRed ?
                            SearchConfig.SearchStrategy.PVS_Q :
                            SearchConfig.SearchStrategy.ALPHA_BETA_Q;

                    Move aiMove = timedEngine.findBestMove(currentState, 1000, strategy);

                    if (aiMove == null) break;

                    moveCount++;
                    final int currentMoveCount = moveCount; // FIXED: Create final copy for lambda
                    final String player = currentState.redToMove ? "RED" : "BLUE";

                    SwingUtilities.invokeLater(() -> {
                        synchronized (stateLock) {
                            state.applyMove(aiMove);
                            updateUI();
                            updateStatus(String.format("Move %d: %s played %s",
                                    currentMoveCount, player, aiMove)); // Use final copy
                        }
                    });

                    Thread.sleep(500); // Pause for visibility

                    // Check game over
                    if (gameEngine.isGameOver(getStateCopy())) {
                        gameInProgress = false;
                        break;
                    }

                    useRed = !useRed; // Alternate strategies
                }

                // FIXED: Create final copies for the final lambda
                final int finalMoveCount = moveCount;
                final boolean gameStillInProgress = gameInProgress;

                // Final UI update
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();

                    if (finalMoveCount >= maxMoves) { // Use final copy
                        JOptionPane.showMessageDialog(this,
                                "Game ended due to move limit (" + maxMoves + " moves)",
                                "Game Ended", JOptionPane.INFORMATION_MESSAGE);
                    } else if (!gameStillInProgress) { // Use final copy
                        showGameOverDialog();
                    } else {
                        updateStatus("🤖 vs 🤖 AI match stopped");
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("❌ AI match error: " + e.getMessage());
                });
            }
        });
    }

    private void stopAI() {
        if (currentAITask != null && !currentAITask.isDone()) {
            currentAITask.cancel(true);
            aiThinking = false;
            updateButtonStates();
            updateStatus("🛑 AI stopped");
        }
    }

    private void resetGame() {
        synchronized (stateLock) {
            initializeGame();
            updateUI();
            updateStatus("🔄 Game reset - NEW architecture ready!");
        }
    }

    private void showGameOverDialog() {
        GameState finalState = getStateCopy();
        String winner = "";

        if (finalState.redGuard == 0) {
            winner = "Blue wins (Red guard captured)!";
        } else if (finalState.blueGuard == 0) {
            winner = "Red wins (Blue guard captured)!";
        } else {
            int redGuardPos = Long.numberOfTrailingZeros(finalState.redGuard);
            int blueGuardPos = Long.numberOfTrailingZeros(finalState.blueGuard);

            if (redGuardPos == GameRules.BLUE_CASTLE) {
                winner = "Red wins (Guard reached castle)!";
            } else if (blueGuardPos == GameRules.RED_CASTLE) {
                winner = "Blue wins (Guard reached castle)!";
            }
        }

        JOptionPane.showMessageDialog(this,
                "Game Over!\n" + winner + "\n\nUsing NEW clean architecture!",
                "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showPositionEvaluation() {
        GameState currentState = getStateCopy();

        // Quick evaluation
        int eval = gameEngine.evaluate(currentState);
        boolean gameOver = gameEngine.isGameOver(currentState);

        String message = String.format(
                "Position Evaluation: %+d\n\n" +
                        "Positive = Good for Red\n" +
                        "Negative = Good for Blue\n\n" +
                        "Current turn: %s\n" +
                        "Game over: %s\n\n" +
                        "Using NEW GameEngine architecture!",
                eval,
                currentState.redToMove ? "RED" : "BLUE",
                gameOver ? "YES" : "NO"
        );

        JOptionPane.showMessageDialog(this, message,
                "Position Evaluation", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateUI() {
        board.updatePosition(getStateCopy());
        board.repaint();
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateButtonStates() {
        aiVsAiButton.setEnabled(!aiThinking);
        resetButton.setEnabled(!aiThinking);
        stopAIButton.setEnabled(aiThinking);
    }

    private GameState getStateCopy() {
        synchronized (stateLock) {
            return state.copy();
        }
    }

    // Cleanup on window close
    @Override
    public void dispose() {
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        if (timedEngine != null) {
            timedEngine.shutdown();
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}