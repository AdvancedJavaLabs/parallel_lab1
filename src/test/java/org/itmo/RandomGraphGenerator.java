package org.itmo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class RandomGraphGenerator {

    private long pack(int u, int v) {
        return (((long) u) << 32) | (v & 0xffffffffL);
    }

    private int unpackU(long key) {
        return (int) (key >>> 32);
    }

    private int unpackV(long key) {
        return (int) (key & 0xffffffffL);
    }

    Graph generateGraph(Random r, int size, int numEdges) {
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        if (numEdges < size - 1) throw new IllegalArgumentException("We need min size-1 edges");
        long maxDirected = (long) size * (size - 1);
        if (numEdges > maxDirected) throw new IllegalArgumentException("Too many edges for directed graph without self-loops");

        int[] perm = IntStream.range(0, size).toArray();
        for (int i = size - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }

        final int chainCount = size - 1;
        final int needMore = numEdges - chainCount;

        final int oversample = Math.max(needMore / 50, 100_000);
        int toGenerate = needMore + oversample;

        long[] keys = new long[chainCount + toGenerate];

        for (int i = 1; i < size; i++) {
            int u = perm[i - 1], v = perm[i];
            keys[i - 1] = pack(u, v);
        }

        final int threads = Math.max(1, ForkJoinPool.getCommonPoolParallelism());
        final int offset = chainCount;
        final int chunk = (toGenerate + threads - 1) / threads;

        final SplittableRandom base = new SplittableRandom(r.nextLong());
        final SplittableRandom[] seeds = new SplittableRandom[threads];
        for (int t = 0; t < threads; t++) seeds[t] = base.split();

        long[] finalKeys = keys;
        IntStream.range(0, threads).parallel().forEach(t -> {
            SplittableRandom rnd = seeds[t];
            int start = offset + t * chunk;
            int end = Math.min(offset + toGenerate, start + chunk);
            for (int i = start; i < end; i++) {
                int u = rnd.nextInt(size);
                int v = rnd.nextInt(size - 1);
                if (v >= u) v++;
                finalKeys[i] = pack(u, v);
            }
        });

        Arrays.parallelSort(keys);
        int w = 1;
        for (int i = 1; i < keys.length; i++) {
            if (keys[i] != keys[i - 1]) {
                keys[w++] = keys[i];
            }
        }
        int unique = w;

        while (unique < numEdges) {
            int missing = numEdges - unique;
            int extra = Math.max(missing / 2, 10_000);
            int add = missing + extra;

            long[] more = new long[unique + add];
            System.arraycopy(keys, 0, more, 0, unique);

            final SplittableRandom base2 = base.split();
            final SplittableRandom[] seeds2 = new SplittableRandom[threads];
            for (int t = 0; t < threads; t++) seeds2[t] = base2.split();

            final int offset2 = unique;
            final int chunk2 = (add + threads - 1) / threads;
            IntStream.range(0, threads).parallel().forEach(t -> {
                SplittableRandom rnd = seeds2[t];
                int start = offset2 + t * chunk2;
                int end = Math.min(offset2 + add, start + chunk2);
                for (int i = start; i < end; i++) {
                    int u = rnd.nextInt(size);
                    int v = rnd.nextInt(size - 1);
                    if (v >= u) v++;
                    more[i] = pack(u, v);
                }
            });

            Arrays.parallelSort(more);
            w = 1;
            for (int i = 1; i < more.length; i++) {
                if (more[i] != more[i - 1]) {
                    more[w++] = more[i];
                }
            }
            unique = w;
            keys = more;
        }

        Set<Long> chainSet = new HashSet<>(chainCount * 2);
        for (int i = 1; i < size; i++) {
            chainSet.add(pack(perm[i - 1], perm[i]));
        }

        int p = 0;
        for (int i = 0; i < unique && p < chainCount; i++) {
            long e = keys[i];
            if (chainSet.remove(e)) {
                // swap keys[p] и keys[i]
                long tmp = keys[p];
                keys[p] = keys[i];
                keys[i] = tmp;
                p++;
            }
        }

        SplittableRandom shuf = base.split();
        for (int i = p; i < numEdges; i++) {
            int j = i + shuf.nextInt(unique - i);
            long tmp = keys[i];
            keys[i] = keys[j];
            keys[j] = tmp;
        }

        Graph g = new Graph(size);
        for (int i = 0; i < numEdges; i++) {
            long key = keys[i];
            int u = unpackU(key);
            int v = unpackV(key);
            g.addEdge(u, v);
        }
        return g;
    }
}