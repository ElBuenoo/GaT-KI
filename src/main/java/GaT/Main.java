package GaT;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.Minimax;
import GaT.search.MoveGenerator;
import GaT.search.QuiescenceSearch;

import static GaT.search.Minimax.evaluate;

public class Main {


    public static void main(String[] args) {
        GameState state = GameState.fromFen("7/7/7/BG6/3b33/3RG3/7 r");

        state.printBoard();
        GameState copy = state.copy();

        long startTime = System.currentTimeMillis();
        Move best = Minimax.findBestMove(state, 5);
        long endTime = System.currentTimeMillis();
        copy.applyMove(best);
        System.out.println("Best move: " + best);
        System.out.println("Evaluation: "+ evaluate(copy, 0));
        copy.printBoard();
        System.out.println("Time taken: "+ (endTime -startTime) + "ms");
        System.out.println(MoveGenerator.generateAllMoves(state).size());


//        Move best1 = TimedMinimax.findBestMoveWithTime(state, 99, 2000); // depth cap 99, time limit 5s
//        GameState copy1 = state.copy();
//        copy.applyMove(best1);
//        System.out.println("Best move: " + best1);
//        System.out.println("Evalutation: "+ evaluate(copy1, 1));


        System.out.println("=== With Quiescence ===");
        Minimax.counter = 0;
        QuiescenceSearch.resetQuiescenceStats();

        startTime = System.currentTimeMillis();
        Move bestQ = Minimax.findBestMoveWithQuiescence(state, 5);
        endTime = System.currentTimeMillis();

        GameState copyQ = state.copy();
        copyQ.applyMove(bestQ);
        System.out.println("Best move: " + bestQ);
        System.out.println("Evaluation: "+ evaluate(copyQ, 0));
        System.out.println("Time taken: "+ (endTime - startTime) + "ms");
        System.out.println("Regular nodes: " + Minimax.counter);
        System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);

        System.out.println("\nMoves different: " + !best.equals(bestQ));


        // Test tactical position where quiescence should activate
        System.out.println("\n=== Tactical Position Test ===");
        GameState tactical = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");
        tactical.printBoard();

        Minimax.counter = 0;
        QuiescenceSearch.resetQuiescenceStats();

        startTime = System.currentTimeMillis();
        Move tacticalMove = Minimax.findBestMoveWithQuiescence(tactical, 4);
        endTime = System.currentTimeMillis();

        System.out.println("Best move: " + tacticalMove);
        System.out.println("Time: " + (endTime - startTime) + "ms");
        System.out.println("Regular nodes: " + Minimax.counter);
        System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);


    }







}
