package org.itmo;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class Graph {
    private final int BATCH_SIZE = 256; // Эмпирическим путём выяснилось, что размер 256 +-самый оптимальный
    private final int MAX_THREADS = 6;

    private final int V;
    private final ArrayList<Integer>[] adjList;

    Graph(int vertices) {
        this.V = vertices;
        adjList = new ArrayList[vertices];
        for (int i = 0; i < vertices; ++i) {
            adjList[i] = new ArrayList<>();
        }
    }

    void addEdge(int src, int dest) {
        if (!adjList[src].contains(dest)) {
            adjList[src].add(dest);
        }
    }

    /**
     * Основной метод для параллельного обхода графа в ширину:<br>
     * Граф обходится по уровням, каждый уровень делится на батчи и обрабатывается параллельно
     * виртуальными потоками. Новые вершины временно копятся в локальных буферах
     * потоков и затем объединяются в следующий слой <br><br>
     * Посещение вершины отмечается атомарно
     *
     * @param startVertex исходная вершина обхода
     * @throws InterruptedException если ожидание завершения задач уровня было прервано
     */
//    void parallelBFS(int startVertex) throws InterruptedException {
//        // Размер батча, отвечает за то сколько вершин слоя обрабатывает одна задача
//        // Логика в том, чтобы мы отдавали потоку не одну вершину на просмотр, а сразу несколько
//        final int vertCount = adjList.length;
//
//        // Суммарный размер следующего слоя (увеличивается с каждым потоком)
//        final AtomicInteger nextSize = new AtomicInteger();
//
//        // 0 - не посещена, 1 - посещена
//        final AtomicIntegerArray visited = new AtomicIntegerArray(vertCount);
//        visited.set(startVertex, 1);
//
//        // Текущий уровень, начиная со startVertex
//        int[] level = new int[]{startVertex};
//
//        // Наборы вершин следующего уровня, которые мы аккумулируем из потоков
//        final ConcurrentLinkedQueue<int[]> nextLevelVertices = new ConcurrentLinkedQueue<>();
//
//        try (ExecutorService threads = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("bfs-vt-", 0).factory())) {
//            try {
//                while (level.length > 0) {
//                    // Сброс накопленных кусков и суммарного размера следующего слоя
//                    nextLevelVertices.clear();
//                    nextSize.set(0);
//
//                    final int levelSize = level.length;
//                    // Вычисляем сколько потоков надо запустить на уровень
//                    final int numBatches = (levelSize + BATCH_SIZE - 1) / BATCH_SIZE;
//
//                    if (numBatches == 1) {
//                        // Одну вершину на уровне пускаем в один поток (startVertex и узлы последних уровней)
//                        processBatch(level, startVertex, levelSize, adjList, visited, nextLevelVertices, nextSize);
//                    } else {
//                        CountDownLatch latch = new CountDownLatch(numBatches);
//                        for (int b = 0; b < numBatches; b++) {
//                            int vertStart = b * BATCH_SIZE;
//                            int vertEnd = Math.min(vertStart + BATCH_SIZE, levelSize);
//                            int[] finalLevel = level;
//                            threads.execute(() -> {
//                                processBatch(finalLevel, vertStart, vertEnd, adjList, visited, nextLevelVertices, nextSize);
//                                latch.countDown();
//                            });
//                        }
//                        // Ждём пока потоки пройдут все вершины нынешнего уровня, чтобы одновременно
//                        // перейти к следующему уровню
//                        latch.await();
//                    }
//
//                    int totalSize = nextSize.get();
//                    if (totalSize == 0) {
//                        level = new int[0];
//                    } else {
//                        // Тут происходит склейка вершин следующего уровня из буферов каждого потока
//                        int[] next = new int[totalSize];
//                        int off = 0;
//                        for (int[] chunk : nextLevelVertices) {
//                            System.arraycopy(chunk, 0, next, off, chunk.length);
//                            off += chunk.length;
//                        }
//                        level = next;
//                    }
//                }
//            } finally {
//                threads.shutdown();
//                boolean b = threads.awaitTermination(10, TimeUnit.SECONDS);
//            }
//        }
//    }

    /**
     * Вторая версия параллельного обхода для сравнения производительности 12ти фиксированных потоков и
     * какого-то количества виртуальных потоков. Комментариев по коду тут нет, но в "виртуальной" версии они есть
     * @param startVertex вершина, с которой надо начать обход графа
     * @throws InterruptedException
     */
    void parallelBFS(int startVertex) throws InterruptedException {
        final int vertCount = adjList.length;
        final AtomicInteger nextSize = new AtomicInteger();
        final AtomicIntegerArray visited = new AtomicIntegerArray(vertCount);
        final ConcurrentLinkedQueue<int[]> nextLevelVertices = new ConcurrentLinkedQueue<>();
        final AtomicInteger vertPointer = new AtomicInteger();
        int[] level = new int[]{startVertex};

        visited.set(startVertex, 1);

        try (ExecutorService threads = Executors.newFixedThreadPool(MAX_THREADS)) {
            try {
                while (level.length > 0) {
                    nextLevelVertices.clear();
                    nextSize.set(0);

                    final int levelSize = level.length;
                    final int numBatches = (levelSize + BATCH_SIZE - 1) / BATCH_SIZE;
                    final int workersNum = Math.min(MAX_THREADS, Math.max(1, numBatches));

                    if (numBatches == 1) {
                        processBatch(level, startVertex, levelSize, adjList, visited, nextLevelVertices, nextSize);
                    } else {
                        vertPointer.set(0);
                        CountDownLatch latch = new CountDownLatch(workersNum);
                        for (int i = 0; i < workersNum; i++) {
                            int[] finalLevel = level;
                            threads.execute(() -> {
                                while (true) {
                                    int vertStart = vertPointer.getAndAdd(BATCH_SIZE);
                                    if (vertStart >= levelSize) {
                                        break;
                                    }
                                    int vertEnd = Math.min(vertStart + BATCH_SIZE, levelSize);
                                    processBatch(finalLevel, vertStart, vertEnd, adjList, visited, nextLevelVertices, nextSize);
                                }
                                latch.countDown();
                            });
                        }

                        latch.await();
                    }

                    int totalSize = nextSize.get();
                    if (totalSize == 0) {
                        level = new int[0];
                    } else {
                        int[] next = new int[totalSize];
                        int off = 0;
                        for (int[] vertices : nextLevelVertices) {
                            System.arraycopy(vertices, 0, next, off, vertices.length);
                            off += vertices.length;
                        }
                        level = next;
                    }
                }
            } finally {
                threads.shutdown();
                boolean b = threads.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Метод обработки батча вершин текущего уровня. Для каждой вершины разворачивает
     * всех дочерей, неизвестных помечает
     * и добавляет в свой локальный буфер потока, который затем публикуется в общий
     * список вершин следующего уровня
     */
    private void processBatch(
            int[] level,
            int vertStart,
            int vertEnd,
            ArrayList<Integer>[] adjList,
            AtomicIntegerArray visited,
            ConcurrentLinkedQueue<int[]>  nextLevelVertices,
            AtomicInteger nextSize
    ) {
        // Локальный буфер результатов прохода потока по вершинам
        int cap = BATCH_SIZE;
        int[] buf = new int[cap];
        int sz = 0;

        for (int i = vertStart; i < vertEnd; i++) {
            int u = level[i];
            for (int v : adjList[u]) {
                if (visited.compareAndSet(v, 0, 1)) {
                    if (sz == cap) {
                        cap *= 2;
                        buf = Arrays.copyOf(buf, cap);
                    }
                    buf[sz++] = v;
                }
            }
        }

        if (sz > 0) {
            nextLevelVertices.add(Arrays.copyOf(buf, sz));
            nextSize.addAndGet(sz);
        }
    }


    //Generated by ChatGPT
    void bfs(int startVertex) {
        boolean[] visited = new boolean[V];

        LinkedList<Integer> queue = new LinkedList<>();

        visited[startVertex] = true;
        queue.add(startVertex);

        while (!queue.isEmpty()) {
            startVertex = queue.poll();

            for (int n : adjList[startVertex]) {
                if (!visited[n]) {
                    visited[n] = true;
                    queue.add(n);
                }
            }
        }
    }

    List<Integer> neighbors(int u) {
        return adjList[u];
    }
}
