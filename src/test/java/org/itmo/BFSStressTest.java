package org.itmo;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.I_Result;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "All correct, no duplicates")
@Outcome(expect = Expect.ACCEPTABLE_INTERESTING, desc = "Non-zero difference, potential duplicates or skips")
@State
public class BFSStressTest {

    private static final int SIZE = 50000;
    private static final int EDGES = 300000;
    private static final int BATCH_SIZE = 256;
    private static final int MAX_LEVEL_SIZE = 8192;

    private final Graph graph;
    private final int[] level;
    private final AtomicIntegerArray visited;
    private final ConcurrentLinkedQueue<int[]> nextChunks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextSize = new AtomicInteger();
    private final AtomicInteger cursor = new AtomicInteger();

    public BFSStressTest() {
        RandomGraphGenerator gen = new RandomGraphGenerator();
        graph = gen.generateGraph(new Random(), SIZE, EDGES);
        int f = Math.min(MAX_LEVEL_SIZE, SIZE);
        level = new int[f];
        for (int i = 0; i < f; i++) level[i] = i;
        visited = new AtomicIntegerArray(SIZE);
        cursor.set(0);
        nextSize.set(0);
    }

    private void processBatch(int iStart, int iEnd) {
        int cap = BATCH_SIZE;
        int[] buf = new int[cap];
        int sz = 0;
        for (int i = iStart; i < iEnd; i++) {
            int u = level[i];
            List<Integer> vertNeighbors = graph.neighbors(u);
            for (int v : vertNeighbors) {
                if (visited.compareAndSet(v, 0, 1)) {
                    if (sz == cap) {
                        int newCapacity = cap * 2;
                        buf = Arrays.copyOf(buf, newCapacity);
                        cap = newCapacity;
                    }
                    buf[sz++] = v;
                }
            }
        }
        if (sz > 0) {
            nextChunks.add(Arrays.copyOf(buf, sz));
            nextSize.addAndGet(sz);
        }
    }

    private void work() {
        int levelSize = level.length;
        while (true) {
            int iStart = cursor.getAndAdd(BATCH_SIZE);
            if (iStart >= levelSize) {
                break;
            }
            int iEnd = Math.min(iStart + BATCH_SIZE, levelSize);
            processBatch(iStart, iEnd);
        }
    }

    @Actor public void actor1() { work(); }
    @Actor public void actor2() { work(); }
    @Actor public void actor3() { work(); }
    @Actor public void actor4() { work(); }
//    @Actor public void actor5() { work(); }
//    @Actor public void actor6() { work(); }

    @Arbiter
    public void arbiter(I_Result r) {
        int visitedCount = 0;
        for (int i = 0; i < visited.length(); i++) {
            if (visited.get(i) != 0) visitedCount++;
        }
        r.r1 = nextSize.get() - visitedCount;
    }
}


