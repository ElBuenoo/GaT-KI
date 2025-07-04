package client;

import java.util.List;
import java.util.ArrayList;

import GaT.engine.GameEngine;
import GaT.engine.TimedGameEngine;
import GaT.engine.TimeManager;
import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.GameRules;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * FULLY CORRECTED GAME CLIENT
 *
 * All fixes implemented:
 * ✅ Better error handling with stack traces
 * ✅ Proper move validation
 * ✅ Robust JSON parsing
 * ✅ Debug output for troubleshooting
 * ✅ Fallback mechanisms
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // Game engines
    private static final GameEngine gameEngine = new GameEngine();
    private static final TimedGameEngine timedEngine = new TimedGameEngine();
    private static final TimeManager timeManager = new TimeManager(180000, 30);

    // Game state tracking
    private static int moveNumber = 0;
    private static long gameStartTime = System.currentTimeMillis();
    private static long totalTimeUsed = 0;
    private static boolean debugMode = true; // Enable for troubleshooting

    // Move history for debugging
    private static final List<String> moveHistory = new ArrayList<>();

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();

        if (network.getP() == null) {
            System.err.println("❌ Failed to connect to server");
            return;
        }

        int player = Integer.parseInt(network.getP());

        printStartupBanner(player);

        // Enable debug mode on timed engine
        timedEngine.setDebugMode(debugMode);

        while (running) {
            try {
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null || gameData.isEmpty()) {
                    System.err.println("❌ No game data received");
                    Thread.sleep(1000);
                    continue;
                }

                JsonObject game = parseGameResponse(gameData);
                if (game == null) {
                    System.err.println("❌ Failed to parse game data");
                    continue;
                }

                if (game.get("bothConnected").getAsBoolean()) {
                    running = processGameTurn(game, player, network);
                } else {
                    waitForOpponent();
                }

            } catch (Exception e) {
                System.err.println("❌ Error in game loop: " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }

                // Try to recover
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        cleanup(network);
    }

    /**
     * Process a game turn
     */
    private static boolean processGameTurn(JsonObject game, int player, Network network) {
        String turn = game.get("turn").getAsString();
        String board = game.get("board").getAsString();
        long timeRemaining = game.get("time").getAsLong();

        // Check if it's our turn
        if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
            moveNumber++;
            printMoveHeader(moveNumber, player, board, timeRemaining);

            // Calculate and execute move
            long moveStartTime = System.currentTimeMillis();
            String moveResult = calculateAndSendMove(board, player, timeRemaining, network);
            long moveTime = System.currentTimeMillis() - moveStartTime;

            if (moveResult != null) {
                totalTimeUsed += moveTime;
                moveHistory.add(moveResult);
                printMoveComplete(moveTime, totalTimeUsed);
            } else {
                System.err.println("❌ Failed to calculate/send move");
                return false; // End game on critical error
            }
        }

        // Check for game end
        if (game.has("winner")) {
            handleGameEnd(game.get("winner").getAsString(), player, timeRemaining);
            return false;
        } else if (game.has("end") && game.get("end").getAsBoolean()) {
            handleGameEnd("Draw", player, timeRemaining);
            return false;
        }

        return true; // Continue game
    }

    /**
     * Calculate and send move with comprehensive error handling
     */
    private static String calculateAndSendMove(String board, int player, long timeRemaining, Network network) {
        GameState state = null;

        try {
            // Step 1: Parse game state
            state = parseGameState(board);
            if (state == null) {
                System.err.println("❌ Failed to parse board state");
                return sendEmergencyMove(network, null);
            }

            // Step 2: Verify it's our turn
            boolean correctPlayer = (player == 0 && state.redToMove) ||
                    (player == 1 && !state.redToMove);

            if (!correctPlayer) {
                System.err.println("❌ Turn mismatch! Player " + player +
                        " but state says " + (state.redToMove ? "RED" : "BLUE") + " to move");
                return null; // Don't make a move if it's not our turn
            }

            // Step 3: Analyze position
            analyzePosition(state);

            // Step 4: Calculate time allocation
            long allocatedTime = calculateTimeAllocation(timeRemaining, moveNumber);

            System.out.printf("🧠 AI CALCULATION:%n");
            System.out.printf("   ⏰ Time allocated: %dms (%.1f%% of remaining)%n",
                    allocatedTime, 100.0 * allocatedTime / timeRemaining);

            // Step 5: Find best move
            Move bestMove = null;

            try {
                bestMove = timedEngine.findBestMove(
                        state,
                        allocatedTime,
                        SearchConfig.SearchStrategy.PVS_Q
                );
            } catch (Exception e) {
                System.err.println("❌ Search error: " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }
            }

            // Step 6: Validate move
            bestMove = validateAndRepairMove(state, bestMove);

            if (bestMove == null) {
                System.err.println("❌ No valid move found");
                return sendEmergencyMove(network, state);
            }

            // Step 7: Send move
            String moveString = bestMove.toString();

            if (debugMode) {
                printMoveDebugInfo(bestMove, state);
            }

            String response = network.send(gson.toJson(moveString));

            if (response == null || response.isEmpty()) {
                System.err.println("❌ No response from server for move: " + moveString);
                // Try once more with a different move
                return sendEmergencyMove(network, state);
            }

            System.out.println("📤 Move sent successfully: " + moveString);
            displaySearchStatistics();

            return moveString;

        } catch (Exception e) {
            System.err.println("❌ Critical error in move calculation: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
            return sendEmergencyMove(network, state);
        }
    }

    /**
     * Validate and repair move if necessary
     */
    private static Move validateAndRepairMove(GameState state, Move move) {
        if (move == null) {
            System.err.println("⚠️ AI returned null move");
            return findBestLegalMove(state);
        }

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

        if (debugMode && legalMoves.size() < 10) {
            System.out.println("📋 All legal moves:");
            for (int i = 0; i < legalMoves.size(); i++) {
                System.out.println("   " + (i+1) + ". " + legalMoves.get(i));
            }
        }

        // Check if move is legal
        if (legalMoves.contains(move)) {
            System.out.printf("✅ Move validated: %s%n", move);
            return move;
        }

        System.err.printf("❌ Invalid move detected: %s%n", move);

        // Try to find similar legal move
        for (Move legal : legalMoves) {
            // Same from and to, different amount
            if (legal.from == move.from && legal.to == move.to) {
                System.out.println("   🔧 Found similar legal move: " + legal);
                return legal;
            }
        }

        // Try to find move with same from
        for (Move legal : legalMoves) {
            if (legal.from == move.from) {
                System.out.println("   🔧 Found alternative from same square: " + legal);
                return legal;
            }
        }

        System.err.println("   ⚠️ No similar move found, selecting best legal move");
        return findBestLegalMove(state);
    }

    /**
     * Find best legal move with comprehensive strategy
     */
    private static Move findBestLegalMove(GameState state) {
        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        System.out.printf("🎯 Selecting from %d legal moves...%n", legalMoves.size());

        // Priority 1: Winning moves
        for (Move move : legalMoves) {
            if (isWinningMove(move, state)) {
                System.out.printf("💎 Found winning move: %s%n", move);
                return move;
            }
        }

        // Priority 2: Capture enemy guard
        for (Move move : legalMoves) {
            if (capturesEnemyGuard(move, state)) {
                System.out.printf("🎯 Found guard capture: %s%n", move);
                return move;
            }
        }

        // Priority 3: Best capture
        Move bestCapture = findBestCapture(legalMoves, state);
        if (bestCapture != null) {
            return bestCapture;
        }

        // Priority 4: Best positional move
        Move bestMove = legalMoves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (Move move : legalMoves) {
            int score = evaluateMove(move, state);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        System.out.printf("📍 Selected positional move: %s (score=%d)%n", bestMove, bestScore);
        return bestMove;
    }

    /**
     * Send emergency move when all else fails
     */
    private static String sendEmergencyMove(Network network, GameState state) {
        System.out.println("🚨 EMERGENCY MOVE SYSTEM ACTIVATED");

        try {
            // If we have state, try to find a simple move
            if (state != null) {
                List<Move> moves = MoveGenerator.generateAllMoves(state);
                if (!moves.isEmpty()) {
                    // Prefer safe moves
                    for (Move move : moves) {
                        if (!isRiskyMove(move, state)) {
                            String moveStr = move.toString();
                            System.out.printf("🚑 Emergency safe move: %s%n", moveStr);
                            network.send(gson.toJson(moveStr));
                            return moveStr;
                        }
                    }

                    // Any move is better than none
                    Move emergency = moves.get(0);
                    String moveStr = emergency.toString();
                    System.out.printf("🚑 Emergency move: %s%n", moveStr);
                    network.send(gson.toJson(moveStr));
                    return moveStr;
                }
            }

            // Absolute fallback - try standard opening moves
            String[] fallbackMoves = {
                    "D7-D6-1", "A7-A6-1", "G7-G6-1", // Red opening moves
                    "D1-D2-1", "A1-A2-1", "G1-G2-1"  // Blue opening moves
            };

            for (String fallback : fallbackMoves) {
                System.out.printf("🆘 Trying fallback move: %s%n", fallback);
                String response = network.send(gson.toJson(fallback));
                if (response != null && !response.isEmpty()) {
                    return fallback;
                }
            }

        } catch (Exception e) {
            System.err.println("Emergency move failed: " + e.getMessage());
        }

        return null;
    }

    /**
     * Parse game response with better error handling
     */
    private static JsonObject parseGameResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        try {
            // Try direct parse first
            return gson.fromJson(response, JsonObject.class);

        } catch (JsonSyntaxException e) {
            // Handle concatenated JSON responses
            if (debugMode) {
                System.out.println("🔧 Handling concatenated JSON response");
            }

            // Find last complete JSON object
            int lastOpen = response.lastIndexOf('{');
            int lastClose = response.lastIndexOf('}');

            if (lastOpen >= 0 && lastClose > lastOpen) {
                try {
                    String lastJson = response.substring(lastOpen, lastClose + 1);
                    return gson.fromJson(lastJson, JsonObject.class);
                } catch (Exception e2) {
                    System.err.println("Failed to parse extracted JSON: " + e2.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * Parse and validate game state
     */
    private static GameState parseGameState(String board) {
        try {
            GameState state = GameState.fromFen(board);

            // Basic validation
            if (state.redGuard == 0 && state.blueGuard == 0) {
                System.err.println("⚠️ Invalid state: no guards found");
                return null;
            }

            return state;

        } catch (Exception e) {
            System.err.println("❌ Failed to parse FEN: " + board);
            System.err.println("   Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Analyze position for display
     */
    private static void analyzePosition(GameState state) {
        System.out.println("   🔍 Position Analysis:");

        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        System.out.printf("      📊 Legal moves: %d%n", allMoves.size());

        // Material count
        int redMaterial = calculateMaterial(state, true);
        int blueMaterial = calculateMaterial(state, false);
        int materialDiff = redMaterial - blueMaterial;

        System.out.printf("      ⚖️  Material: Red=%d, Blue=%d (diff=%+d)%n",
                redMaterial, blueMaterial, materialDiff);

        // Guard positions
        if (state.redGuard != 0) {
            int pos = Long.numberOfTrailingZeros(state.redGuard);
            System.out.printf("      🔴 Red guard: %s%n", coordinateName(pos));
        }
        if (state.blueGuard != 0) {
            int pos = Long.numberOfTrailingZeros(state.blueGuard);
            System.out.printf("      🔵 Blue guard: %s%n", coordinateName(pos));
        }

        // Tactical analysis
        analyzeTacticalOpportunities(state, allMoves);

        // Game phase
        String gamePhase = determineGamePhase(redMaterial + blueMaterial);
        System.out.printf("      🎯 Game phase: %s%n", gamePhase);
    }

    /**
     * Calculate time allocation
     */
    private static long calculateTimeAllocation(long timeRemaining, int moveNumber) {
        long allocated = timeManager.calculateMoveTime(timeRemaining, moveNumber, 60);

        // Emergency adjustments
        if (timeRemaining < 5000) {
            allocated = Math.min(allocated, timeRemaining / 4);
            System.out.println("⚡ CRITICAL TIME: Reducing allocation");
        } else if (timeRemaining < 10000) {
            allocated = Math.min(allocated, timeRemaining / 3);
            System.out.println("⚡ TIME PRESSURE: Reducing allocation");
        }

        // Never use more than 5 seconds per move
        allocated = Math.min(allocated, 5000);

        // Always leave buffer for network
        allocated = Math.max(100, allocated - 100);

        return allocated;
    }

    // === UTILITY METHODS ===

    private static void analyzeTacticalOpportunities(GameState state, List<Move> moves) {
        int captures = 0;
        int guardThreats = 0;
        int winningMoves = 0;
        int checks = 0;

        for (Move move : moves) {
            if (GameRules.isCapture(move, state)) {
                captures++;
                if (capturesEnemyGuard(move, state)) {
                    guardThreats++;
                }
            }
            if (isWinningMove(move, state)) {
                winningMoves++;
            }
            if (createsCheck(move, state)) {
                checks++;
            }
        }

        if (captures > 0) {
            System.out.printf("      ⚔️  Captures available: %d%n", captures);
        }
        if (guardThreats > 0) {
            System.out.printf("      🎯 GUARD THREATS: %d%n", guardThreats);
        }
        if (winningMoves > 0) {
            System.out.printf("      🏆 WINNING MOVES: %d%n", winningMoves);
        }
        if (checks > 0) {
            System.out.printf("      👑 Checks available: %d%n", checks);
        }
    }

    private static boolean isWinningMove(Move move, GameState state) {
        // Check if guard reaches enemy castle
        if (GameRules.isGuardMove(move, state)) {
            boolean isRed = state.redToMove;
            int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
            return move.to == targetCastle;
        }

        // Check if move captures enemy guard
        return capturesEnemyGuard(move, state);
    }

    private static boolean capturesEnemyGuard(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        return (enemyGuard & GameState.bit(move.to)) != 0;
    }

    private static boolean createsCheck(Move move, GameState state) {
        // Simulate move and check if enemy guard is threatened
        GameState afterMove = state.copy();
        afterMove.applyMove(move);
        return GameRules.isInCheck(afterMove);
    }

    private static boolean isRiskyMove(Move move, GameState state) {
        // Check if move exposes our guard to capture
        if (GameRules.isGuardMove(move, state)) {
            GameState afterMove = state.copy();
            afterMove.applyMove(move);
            return GameRules.isInCheck(afterMove);
        }
        return false;
    }

    private static Move findBestCapture(List<Move> moves, GameState state) {
        Move bestCapture = null;
        int bestValue = 0;

        for (Move move : moves) {
            if (GameRules.isCapture(move, state)) {
                int value = getCaptureValue(move, state);
                if (value > bestValue) {
                    bestValue = value;
                    bestCapture = move;
                }
            }
        }

        if (bestCapture != null) {
            System.out.printf("⚔️  Found capture: %s (value=%d)%n", bestCapture, bestValue);
        }

        return bestCapture;
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Guard capture is most valuable
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 10000;
        }

        // Tower capture value based on height
        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * 100;
    }

    private static int evaluateMove(Move move, GameState state) {
        int score = 0;
        boolean isRed = state.redToMove;

        // Advancement bonus
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        if (isRed && toRank < fromRank) {
            score += (fromRank - toRank) * 50;
        } else if (!isRed && toRank > fromRank) {
            score += (toRank - fromRank) * 50;
        }

        // Central control
        int toFile = GameState.file(move.to);
        if (toFile >= 2 && toFile <= 4) {
            score += 30;
            if (toFile == 3) score += 20; // D-file bonus
        }

        // Activity bonus
        score += move.amountMoved * 10;

        // Guard moves get bonus
        if (GameRules.isGuardMove(move, state)) {
            score += 100;
        }

        return score;
    }

    private static int calculateMaterial(GameState state, boolean isRed) {
        int total = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            total += isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
        }
        return total;
    }

    private static String determineGamePhase(int totalMaterial) {
        if (totalMaterial <= 6) return "ENDGAME";
        if (totalMaterial <= 12) return "MIDDLEGAME";
        return "OPENING";
    }

    private static String coordinateName(int index) {
        int rank = GameState.rank(index);
        int file = GameState.file(index);
        return "" + (char)('A' + file) + (rank + 1);
    }

    private static void displaySearchStatistics() {
        try {
            String stats = timedEngine.getSearchStats();
            if (stats != null && !stats.isEmpty()) {
                System.out.println("   📊 Search Statistics:");
                String[] lines = stats.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty() && !line.contains("===")) {
                        System.out.println("      " + line.trim());
                    }
                }
            }
        } catch (Exception e) {
            // Ignore stats errors
        }
    }

    // === UI METHODS ===

    private static void printStartupBanner(int player) {
        System.out.println("🎮 Guards & Towers AI Client - FIXED VERSION 2.0");
        System.out.println("👤 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Architecture: GameEngine + TimedGameEngine");
        System.out.println("🎯 Strategy: PVS + Quiescence");
        System.out.println("🔧 Debug Mode: " + (debugMode ? "ON" : "OFF"));
        System.out.println("📝 Features: Robust error handling, move validation, emergency fallbacks");
        System.out.println();
    }

    private static void printMoveHeader(int moveNum, int player, String board, long timeRemaining) {
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("🔥 MOVE #%d - %s TO PLAY%n", moveNum,
                (player == 0 ? "RED" : "BLUE"));
        System.out.println("📋 Board FEN: " + board);
        System.out.println("⏱️  Time remaining: " + formatTime(timeRemaining));
    }

    private static void printMoveComplete(long moveTime, long totalTime) {
        System.out.printf("⏱️  Move calculated in %dms%n", moveTime);
        System.out.printf("📊 Cumulative time used: %s%n", formatTime(totalTime));
        System.out.println("=".repeat(70));
    }

    private static void printMoveDebugInfo(Move move, GameState state) {
        System.out.println("   🔍 Move Debug Info:");
        System.out.println("      From: " + coordinateName(move.from) + " (index " + move.from + ")");
        System.out.println("      To: " + coordinateName(move.to) + " (index " + move.to + ")");
        System.out.println("      Amount: " + move.amountMoved);
        System.out.println("      Type: " + (GameRules.isGuardMove(move, state) ? "Guard" : "Tower"));
        System.out.println("      Capture: " + GameRules.isCapture(move, state));
    }

    private static void waitForOpponent() throws InterruptedException {
        System.out.print("⏳ Waiting for opponent");
        for (int i = 0; i < 3; i++) {
            Thread.sleep(333);
            System.out.print(".");
        }
        System.out.println();
    }

    private static void handleGameEnd(String winner, int player, long timeRemaining) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🏁 GAME FINISHED!");
        System.out.println("🏆 Winner: " + winner);

        boolean weWon = (player == 0 && winner.equals("r")) ||
                (player == 1 && winner.equals("b"));

        if (weWon) {
            System.out.println("🎉 VICTORY! WE WON!");
        } else if (winner.equals("Draw")) {
            System.out.println("🤝 DRAW GAME");
        } else {
            System.out.println("😔 Defeat - We'll win next time!");
        }

        printFinalStatistics(timeRemaining);
    }

    private static void printFinalStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 FINAL STATISTICS:");
        System.out.printf("   🎮 Total moves: %d%n", moveNumber);
        System.out.printf("   ⏱️  Time remaining: %s%n", formatTime(finalTimeRemaining));
        System.out.printf("   📊 Total time used: %s%n", formatTime(totalTimeUsed));

        if (moveNumber > 0) {
            long avgTimePerMove = totalTimeUsed / moveNumber;
            System.out.printf("   ⚡ Average time per move: %dms%n", avgTimePerMove);
        }

        if (!moveHistory.isEmpty()) {
            System.out.println("   📝 Move history:");
            for (int i = 0; i < Math.min(10, moveHistory.size()); i++) {
                System.out.printf("      %d. %s%n", i + 1, moveHistory.get(i));
            }
            if (moveHistory.size() > 10) {
                System.out.println("      ... (" + (moveHistory.size() - 10) + " more moves)");
            }
        }

        System.out.println("=".repeat(70));
    }

    private static void cleanup(Network network) {
        System.out.println("\n🧹 CLEANUP:");

        if (timedEngine != null) {
            timedEngine.shutdown();
            System.out.println("   ✅ AI engine shutdown");
        }

        if (gameEngine != null) {
            gameEngine.clearCaches();
            System.out.println("   ✅ Caches cleared");
        }

        if (network != null) {
            network.close();
            System.out.println("   ✅ Network connection closed");
        }

        System.out.println("👋 Thanks for playing! Goodbye!");
    }

    private static String formatTime(long milliseconds) {
        if (milliseconds < 0) return "0s";

        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else if (seconds >= 10) {
            return String.format("%ds", seconds);
        } else {
            return String.format("%.1fs", milliseconds / 1000.0);
        }
    }
}