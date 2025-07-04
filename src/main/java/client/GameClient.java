package client;

import java.util.List;

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

/**
 * CORRECTED GAME CLIENT - Working version with all fixes
 *
 * ✅ FIXED: Uses bestMove.toString() for correct server protocol
 * ✅ FIXED: Handles concatenated JSON responses from server
 * ✅ FIXED: Removed problematic getEvaluator() calls
 * ✅ WORKING: Uses modern GameEngine architecture
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // NEW ARCHITECTURE - Use modern engines
    private static final GameEngine gameEngine = new GameEngine();
    private static final TimedGameEngine timedEngine = new TimedGameEngine();
    private static final TimeManager timeManager = new TimeManager(180000, 30);

    // Game state tracking
    private static int moveNumber = 0;
    private static long gameStartTime = System.currentTimeMillis();
    private static long totalTimeUsed = 0;

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();

        if (network.getP() == null) {
            System.err.println("❌ Failed to connect to server");
            return;
        }

        int player = Integer.parseInt(network.getP());

        System.out.println("🎮 Guards & Towers AI Client - CORRECTED VERSION");
        System.out.println("👤 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Architecture: NEW (GameEngine + TimedGameEngine)");
        System.out.println("🎯 Strategy: PVS + Quiescence");
        System.out.println("🔧 Protocol: FIXED (bestMove.toString())");
        System.out.println("📦 JSON: FIXED (handles concatenated responses)");

        while (running) {
            try {
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("❌ Couldn't get game data");
                    break;
                }

                // ✅ FIXED: Handle concatenated JSON responses
                JsonObject game = parseLastJsonObject(gameData);

                if (game.get("bothConnected").getAsBoolean()) {
                    String turn = game.get("turn").getAsString();
                    String board = game.get("board").getAsString();
                    long timeRemaining = game.get("time").getAsLong();

                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        moveNumber++;
                        System.out.println("\n" + "=".repeat(70));
                        System.out.printf("🔥 MOVE #%d - %s TO PLAY%n", moveNumber,
                                (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board FEN: " + board);
                        System.out.println("⏱️  Time remaining: " + formatTime(timeRemaining));

                        // Calculate and execute move
                        long moveStartTime = System.currentTimeMillis();
                        String moveResult = getAIMove(board, player, timeRemaining, network);
                        long moveTime = System.currentTimeMillis() - moveStartTime;

                        totalTimeUsed += moveTime;

                        if (moveResult != null) {
                            System.out.printf("⏱️  Move calculated in %dms%n", moveTime);
                            System.out.printf("📊 Cumulative time used: %s%n", formatTime(totalTimeUsed));
                            System.out.println("=".repeat(70));
                        } else {
                            System.err.println("❌ Failed to get move");
                            break;
                        }
                    }

                    // Check for game end
                    if (game.has("winner")) {
                        String winner = game.get("winner").getAsString();
                        handleGameEnd(winner, player, timeRemaining);
                        running = false;
                    } else if (game.has("end") && game.get("end").getAsBoolean()) {
                        handleGameEnd("Draw", player, timeRemaining);
                        running = false;
                    }

                } else {
                    System.out.print("⏳ Waiting for opponent");
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(333);
                        System.out.print(".");
                    }
                    System.out.println();
                }

            } catch (Exception e) {
                System.err.println("❌ Error in game loop: " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }

        // Cleanup
        cleanup(network);
    }

    /**
     * ✅ FIXED: Extract the last complete JSON object from server response
     * Handles server responses that contain multiple concatenated JSON objects
     */
    private static JsonObject parseLastJsonObject(String response) {
        try {
            // Try parsing as-is first
            return gson.fromJson(response, JsonObject.class);
        } catch (Exception e) {
            // Server sent multiple JSON objects - find the last complete one
            int lastOpenBrace = response.lastIndexOf('{');
            int lastCloseBrace = response.lastIndexOf('}');

            if (lastOpenBrace != -1 && lastCloseBrace != -1 && lastCloseBrace > lastOpenBrace) {
                String lastJson = response.substring(lastOpenBrace, lastCloseBrace + 1);
                System.out.println("🔧 Extracted last JSON from concatenated response");
                return gson.fromJson(lastJson, JsonObject.class);
            }

            throw new RuntimeException("Could not parse server response: " + response);
        }
    }

    /**
     * ✅ CORRECTED: AI move calculation with working protocol
     */
    private static String getAIMove(String board, int player, long timeRemaining, Network network) {
        try {
            // Parse game state
            GameState state = parseGameState(board);
            if (state == null) {
                return getEmergencyMove(network);
            }

            // ✅ FIXED: Simplified analysis (no getEvaluator() calls)
            analyzePosition(state);

            // Calculate time allocation
            long allocatedTime = timeManager.calculateMoveTime(timeRemaining, moveNumber, 60);

            // Apply time pressure adjustments
            if (timeRemaining < 10000) { // Less than 10 seconds
                allocatedTime = Math.min(allocatedTime, timeRemaining / 3);
                System.out.println("⚡ TIME PRESSURE: Reducing allocated time");
            }

            System.out.printf("🧠 AI CALCULATION:%n");
            System.out.printf("   ⏰ Time allocated: %dms (%.1f%% of remaining)%n",
                    allocatedTime, 100.0 * allocatedTime / timeRemaining);

            // Use TimedGameEngine with PVS + Quiescence
            Move bestMove = timedEngine.findBestMove(
                    state,
                    allocatedTime,
                    SearchConfig.SearchStrategy.PVS_Q
            );

            // Validate move
            bestMove = validateMove(state, bestMove);

            if (bestMove == null) {
                return getEmergencyMove(network);
            }

            // ✅ CRITICAL FIX: Use bestMove.toString() - the working format!
            String moveString = bestMove.toString(); // "D5-D4-1" format

            // Send move to server
            String response = network.send(gson.toJson(moveString));

            if (response == null) {
                System.err.println("❌ No response from server for move: " + moveString);
                return null;
            } else {
                System.out.println("📤 Move sent successfully: " + moveString);

                // Show search statistics
                displaySearchStatistics();
            }

            return moveString;

        } catch (Exception e) {
            System.err.println("❌ Error in AI calculation: " + e.getMessage());
            e.printStackTrace();
            return getEmergencyMove(network);
        }
    }

    /**
     * ✅ FIXED: Simplified position analysis (no evaluator calls)
     */
    private static void analyzePosition(GameState state) {
        System.out.println("   🔍 Position Analysis:");

        // Basic position info
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        System.out.printf("      📊 Legal moves: %d%n", allMoves.size());

        // Simple material analysis
        int redMaterial = calculateMaterial(state, true);
        int blueMaterial = calculateMaterial(state, false);
        int materialDiff = redMaterial - blueMaterial;

        System.out.printf("      ⚖️  Material: Red=%d, Blue=%d (diff=%+d)%n",
                redMaterial, blueMaterial, materialDiff);

        // Check for tactical opportunities
        analyzeTacticalOpportunities(state, allMoves);

        // Determine game phase
        String gamePhase = determineGamePhase(redMaterial + blueMaterial);
        System.out.printf("      🎯 Game phase: %s%n", gamePhase);
    }

    /**
     * ✅ FIXED: Simple material calculation
     */
    private static int calculateMaterial(GameState state, boolean isRed) {
        int total = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            total += isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
        }
        return total;
    }

    /**
     * ✅ FIXED: Tactical analysis without evaluator
     */
    private static void analyzeTacticalOpportunities(GameState state, List<Move> moves) {
        int captures = 0;
        int guardThreats = 0;
        int winningMoves = 0;

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
    }

    /**
     * Parse and validate game state
     */
    private static GameState parseGameState(String board) {
        try {
            return GameState.fromFen(board);
        } catch (Exception e) {
            System.err.println("❌ Failed to parse FEN: " + board);
            try {
                return new GameState(); // Fallback to starting position
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Validate that the AI move is legal
     */
    private static Move validateMove(GameState state, Move move) {
        if (move == null) {
            System.err.println("⚠️ AI returned null move");
            return findBestLegalMove(state);
        }

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

        if (legalMoves.contains(move)) {
            System.out.printf("✅ Move validated: %s%n", move);
            return move;
        } else {
            System.err.printf("❌ Invalid move detected: %s%n", move);
            return findBestLegalMove(state);
        }
    }

    /**
     * Find best legal move as fallback
     */
    private static Move findBestLegalMove(GameState state) {
        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("No legal moves available!");
        }

        System.out.printf("🎯 Finding best among %d legal moves...%n", legalMoves.size());

        // Priority 1: Winning moves
        for (Move move : legalMoves) {
            if (isWinningMove(move, state)) {
                System.out.printf("💎 Found winning move: %s%n", move);
                return move;
            }
        }

        // Priority 2: Guard captures
        for (Move move : legalMoves) {
            if (capturesEnemyGuard(move, state)) {
                System.out.printf("🎯 Found guard capture: %s%n", move);
                return move;
            }
        }

        // Priority 3: Best capture by value
        Move bestCapture = null;
        int bestValue = 0;
        for (Move move : legalMoves) {
            if (GameRules.isCapture(move, state)) {
                int value = getCaptureValue(move, state);
                if (value > bestValue) {
                    bestValue = value;
                    bestCapture = move;
                }
            }
        }

        if (bestCapture != null) {
            System.out.printf("⚔️ Found capture: %s (value=%d)%n", bestCapture, bestValue);
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

        System.out.printf("📍 Using positional move: %s (score=%d)%n", bestMove, bestScore);
        return bestMove;
    }

    // === UTILITY METHODS ===

    private static boolean isWinningMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle && GameRules.isGuardMove(move, state);
    }

    private static boolean capturesEnemyGuard(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        return (enemyGuard & GameState.bit(move.to)) != 0;
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 1500; // Guard
        }

        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * 100; // Tower
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

        // Central control bonus
        int file = GameState.file(move.to);
        if (file >= 2 && file <= 4) score += 30;
        if (file == 3) score += 20; // D-file bonus

        // Activity bonus
        score += move.amountMoved * 10;

        return score;
    }

    private static String determineGamePhase(int totalMaterial) {
        if (totalMaterial <= 6) return "ENDGAME";
        if (totalMaterial <= 12) return "MIDDLEGAME";
        return "OPENING";
    }

    private static void displaySearchStatistics() {
        String stats = timedEngine.getSearchStats();
        if (stats != null && !stats.isEmpty()) {
            System.out.println("   📊 Search Statistics:");
            String[] lines = stats.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    System.out.println("      " + line.trim());
                }
            }
        }
    }

    private static String getEmergencyMove(Network network) {
        System.out.println("🚨 EMERGENCY MOVE SYSTEM");
        try {
            GameState state = new GameState(); // Use starting position
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (!moves.isEmpty()) {
                Move emergency = moves.get(0);
                String moveStr = emergency.toString();
                System.out.printf("🚑 Emergency move: %s%n", moveStr);
                network.send(gson.toJson(moveStr));
                return moveStr;
            }
        } catch (Exception e) {
            System.err.println("Emergency failed: " + e.getMessage());
        }

        // Absolute last resort
        String lastResort = "A1-A2-1";
        System.out.printf("🆘 Last resort move: %s%n", lastResort);
        network.send(gson.toJson(lastResort));
        return lastResort;
    }

    private static void handleGameEnd(String winner, int player, long timeRemaining) {
        System.out.println("\n🏁 GAME FINISHED!");
        System.out.println("🏆 Winner: " + winner);

        boolean weWon = (player == 0 && winner.equals("r")) ||
                (player == 1 && winner.equals("b"));

        if (weWon) {
            System.out.println("🎉 WE WON!");
        } else if (winner.equals("Draw")) {
            System.out.println("🤝 DRAW GAME");
        } else {
            System.out.println("😔 We lost this time");
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

        System.out.println("   🏗️  Architecture: NEW (GameEngine + TimedGameEngine)");
        System.out.println("   🎯 Strategy: PVS + Quiescence");
        System.out.println("   🔧 Protocol: FIXED");
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

        System.out.println("👋 Game client shutdown complete");
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