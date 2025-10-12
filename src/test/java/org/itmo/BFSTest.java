package org.itmo;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BFSTest {

    @Test
    public void bfsTest() throws IOException {
        int[] sizes = new int[]{10, 100, 1000, 10_000, 10_000, 50_000, 100_000, 1_000_000, 2_000_000};
        int[] connections = new int[]{50, 500, 5000, 50_000, 100_000, 1_000_000, 1_000_000, 10_000_000, 10_000_000};

        new File("tmp").mkdirs();
        new File("charts").mkdirs();

        Random r = new Random(42);

        List<Integer> sizesList = new ArrayList<>();
        List<Long> serialTimes = new ArrayList<>();
        List<Long> parallelTimes = new ArrayList<>();

        try (FileWriter fw = new FileWriter("tmp/results.txt")) {
            for (int i = 0; i < sizes.length; i++) {
                System.out.println("--------------------------");
                System.out.println("Generating graph of size " + sizes[i] + " ...wait");
                Graph g = new RandomGraphGenerator().generateGraph(r, sizes[i], connections[i]);
                System.out.println("Generation completed!\nStarting bfs");
                long serialTime = executeSerialBfsAndGetTime(g);
                long parallelTime = executeParallelBfsAndGetTime(g);
                sizesList.add(sizes[i]);
                serialTimes.add(serialTime);
                parallelTimes.add(parallelTime);
                fw.append("Times for " + sizes[i] + " vertices and " + connections[i] + " connections: ");
                fw.append("\nSerial: " + serialTime);
                fw.append("\nParallel: " + parallelTime);
                fw.append("\n--------\n");
            }
            fw.flush();
        }

        int threadsTestIndex = Math.min(6, sizes.length - 1);
        int baseSize = sizes[threadsTestIndex];
        int baseConn = connections[threadsTestIndex];

        int[] threadCounts = new int[]{1, 2, 4, 8, 16, 32, 64};
        List<Integer> threadsX = new ArrayList<>();
        List<Long> parallelByThreads = new ArrayList<>();

        Graph gThreads = new RandomGraphGenerator().generateGraph(r, baseSize, baseConn);

        for (int tc : threadCounts) {
            gThreads.setThreadPoolCount(tc);
            long t = executeParallelBfsAndGetTime(gThreads);
            threadsX.add(tc);
            parallelByThreads.add(t);
            System.out.println("Threads=" + tc + " -> parallel time " + t + " ms");
        }

        saveSizeChart(sizesList, serialTimes, parallelTimes, "charts/size_vs_time.png");
        saveThreadsChart(threadsX, parallelByThreads, baseSize, baseConn, "charts/parallel_time_vs_threads.png");
    }


    private long executeSerialBfsAndGetTime(Graph g) {
        long startTime = System.currentTimeMillis();
        g.bfs(0);
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    private long executeParallelBfsAndGetTime(Graph g) {
        long startTime = System.currentTimeMillis();
        g.parallelBFS(0);
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    private void saveSizeChart(List<Integer> sizes, List<Long> serialTimes, List<Long> parallelTimes, String outPath) throws IOException {

        double[] x = sizes.stream().mapToDouble(Integer::doubleValue).toArray();
        double[] ySerial = serialTimes.stream().mapToDouble(Long::doubleValue).toArray();
        double[] yParallel = parallelTimes.stream().mapToDouble(Long::doubleValue).toArray();

        XYChart chart = new XYChartBuilder()
                .width(900).height(600)
                .title("Время выполнения vs размер входных данных")
                .xAxisTitle("Количество вершин")
                .yAxisTitle("Время (мс)")
                .build();

        XYSeries s1 = chart.addSeries("Serial", x, ySerial);
        s1.setMarker(SeriesMarkers.CIRCLE);

        XYSeries s2 = chart.addSeries("Parallel", x, yParallel);
        s2.setMarker(SeriesMarkers.DIAMOND);

        BitmapEncoder.saveBitmap(chart, outPath, BitmapEncoder.BitmapFormat.PNG);
    }

    private void saveThreadsChart(List<Integer> threads, List<Long> times,
                                  int baseSize, int baseConn, String outPath) throws IOException {
        double[] xThreads = threads.stream().mapToDouble(Integer::doubleValue).toArray();
        double[] yTimes = times.stream().mapToDouble(Long::doubleValue).toArray();

        XYChart chart = new XYChartBuilder()
                .width(900).height(600)
                .title("Параллельный BFS: время vs число потоков (size=" + baseSize + ", edges≈" + baseConn + ")")
                .xAxisTitle("Число потоков")
                .yAxisTitle("Время (мс)")
                .build();

        XYSeries s = chart.addSeries("Parallel", xThreads, yTimes);
        s.setMarker(SeriesMarkers.SQUARE);

        BitmapEncoder.saveBitmap(chart, outPath, BitmapEncoder.BitmapFormat.PNG);
    }
}
