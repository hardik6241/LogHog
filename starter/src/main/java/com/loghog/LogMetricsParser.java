package com.loghog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.DoubleAdder;

public class LogMetricsParser {
    private final AtomicLong errorCount = new AtomicLong(0);
    private final Set<String> uniqueErrorIps = ConcurrentHashMap.newKeySet();

    // Thread-safe running totals keeping memory footprint completely flat (O(1))
    private final LongAdder latencyCount = new LongAdder();
    private final LongAdder latencySum = new LongAdder();
    private final DoubleAdder latencySumSquares = new DoubleAdder();

    @Extractor(".*ERROR.*")
    public void countErrors(LogEntry entry) {
        errorCount.incrementAndGet();
        entry.ipAddress().ifPresent(uniqueErrorIps::add);
    }

    @Extractor(".*POST /checkout.*")
    public void measureLatency(LogEntry entry) {
        entry.latencyMs().ifPresent(ms -> {
            latencyCount.increment();
            latencySum.add(ms);
            latencySumSquares.add((double) ms * ms);
        });
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public Set<String> getUniqueErrorIps() {
        return uniqueErrorIps;
    }

    public double[] getLatencyStats() {
        long n = latencyCount.sum();
        if (n == 0) {
            return new double[]{0.0, 0.0};
        }
        double mean = (double) latencySum.sum() / n;
        double variance = latencySumSquares.sum() / n - mean * mean;
        double stdDev = Math.sqrt(Math.max(variance, 0.0));
        return new double[]{mean, stdDev};
    }
}