package GaT.benchmark;

import GaT.model.GameState;

import java.util.ArrayList;
import java.util.List;

public class EvalBenchmark {
    public static void main(String[] args) {

        GameState state = GameState.fromFen("3RG3/4r32/2b34/7/7/7/3BG3 r");
        List<Long> benchmarks= new ArrayList();

        for (int i=0; i<100; i++){

            long startTime= System.currentTimeMillis();
            for (int j=0; j<10_000; j++){
                Minimax.evaluate(state, 1);
            }
            long endTime = System.currentTimeMillis();
            benchmarks.add((endTime-startTime));
        }

        long avg  = benchmarks.stream().reduce(0L, (x,y)-> x+y) / benchmarks.size();
        System.out.println("Raw Values: " + benchmarks);
        System.out.println("Average Execution Time: " + avg + " milliseconds");

    }
}
