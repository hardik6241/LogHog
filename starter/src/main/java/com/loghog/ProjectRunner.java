package com.loghog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ProjectRunner {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java --add-modules jdk.incubator.vector -jar <your-jar-file> <logFilePath>");
            System.exit(1);
        }

        Path logFile = Paths.get(args[0]);

        if (!Files.exists(logFile)) {
            System.err.println("Error: File not found at " + logFile);
            System.exit(1);
        }

        // Reflection Discovery & Caching of Extractor Methods
        LogMetricsParser metricsParser = new LogMetricsParser();
        Map<Pattern, List<Method>> extractorCache = new HashMap<>();

        for (Method method : LogMetricsParser.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Extractor.class)) {
                Extractor annotation = method.getAnnotation(Extractor.class);
                Pattern pattern = Pattern.compile(annotation.value());
                extractorCache.computeIfAbsent(pattern, k -> new ArrayList<>()).add(method);
            }
        }

        long startTime = System.nanoTime();

        Path badDataFile = Paths.get("bad_data.log");
        Files.writeString(badDataFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Bounded concurrency using a Semaphore to prevent unbounded future accumulation
        int maxInFlightBatches = 64;
        Semaphore semaphore = new Semaphore(maxInFlightBatches);
        int batchSize = 1000;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             BufferedWriter badDataWriter = Files.newBufferedWriter(badDataFile, StandardOpenOption.APPEND);
             Stream<String> lines = Files.lines(logFile)) {

            List<String> currentBatch = new ArrayList<>(batchSize);
            Iterable<String> iterable = lines::iterator;

            for (String line : iterable) {
                currentBatch.add(line);
                if (currentBatch.size() >= batchSize) {
                    List<String> batchToProcess = new ArrayList<>(currentBatch);
                    currentBatch.clear();

                    semaphore.acquire(); // Throttle producer if consumer threads are saturated
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            processBatch(batchToProcess, metricsParser, extractorCache, badDataWriter);
                        } finally {
                            semaphore.release();
                        }
                    }, executor);
                    futures.add(future);
                }
            }

            if (!currentBatch.isEmpty()) {
                List<String> batchToProcess = new ArrayList<>(currentBatch);
                semaphore.acquire();
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        processBatch(batchToProcess, metricsParser, extractorCache, badDataWriter);
                    } finally {
                        semaphore.release();
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // SIMD Statistics with the Vector API
        List<Integer> latenciesList = metricsParser.getCheckoutLatencies();
        int[] latenciesArray = latenciesList.stream().mapToInt(i -> i).toArray();
        double[] stats = VectorStatistics.calculateMeanAndStdDev(latenciesArray);

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        // --- Final Report ---
        System.out.println("\n--- LogHog Analysis Complete ---");
        System.out.println("Total ERROR logs found: " + metricsParser.getErrorCount());
        System.out.println("Unique IP addresses causing errors: " + metricsParser.getUniqueErrorIps());
        System.out.printf("Average latency for /checkout (Vector API): %.2f ms (StdDev: %.2f ms)\n", stats[0], stats[1]);
        System.out.println("\nTotal execution time: " + durationMs + " ms");

        waitForProfiler();
    }

    private static void processBatch(List<String> batch,
                                     LogMetricsParser metricsParser,
                                     Map<Pattern, List<Method>> extractorCache,
                                     BufferedWriter badDataWriter) {
        for (String line : batch) {
            try {
                LogEntry entry = LogParser.parse(line);

                for (Map.Entry<Pattern, List<Method>> entryPattern : extractorCache.entrySet()) {
                    if (entryPattern.getKey().matcher(line).matches()) {
                        for (Method method : entryPattern.getValue()) {
                            method.invoke(metricsParser, entry);
                        }
                    }
                }
            } catch (Exception e) {
                synchronized (badDataWriter) {
                    try {
                        badDataWriter.write(line);
                        badDataWriter.newLine();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        }
    }

    private static void waitForProfiler() {
        System.out.println("\nProcessing complete. Press Enter to exit...");
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }
    }
}