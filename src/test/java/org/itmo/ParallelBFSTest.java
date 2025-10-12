package org.itmo;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

@JCStressTest
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "Оба запуска случайно прошли без гонки")
@Outcome(id = "1, 0", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Гонка во втором запуске")
@Outcome(id = "0, 1", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Гонка в первом запуске")
@Outcome(id = "0, 0", expect = Expect.ACCEPTABLE_INTERESTING, desc = "Гонка в обоих запусках")
@State
public class ParallelBFSTest {

    private static final int SIZE = 256;
    private static final int START = 0;

    private final Graph g;
    private int a1, a2;

    public ParallelBFSTest() {
        RandomGraphGenerator gen = new RandomGraphGenerator();
        int edges = SIZE * (SIZE - 1);
        g = gen.generateGraph(new java.util.Random(1L), SIZE, edges);
    }

    @Actor
    public void actor1() {
        a1 = g.parallelBFS(START);
    }

    @Actor
    public void actor2() {
        a2 = g.parallelBFS(START);
    }

    @Arbiter
    public void arbiter(II_Result r) {
        r.r1 = a1;
        r.r2 = a2;
    }
}
