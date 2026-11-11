package com.loghog;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LogMetricsParser {
    private final AtomicLong errorCount = new AtomicLong(0);
    private final Set<String> uniqueErrorIps = ConcurrentHashMap.newKeySet();
    private final List<Integer> checkoutLatencies = Collections.synchronizedList(new ArrayList<>());

    @Extractor(".*ERROR.*")
    public void countErrors(LogEntry entry) {
        errorCount.incrementAndGet();
        entry.ipAddress().ifPresent(uniqueErrorIps::add);
    }

    @Extractor(".*POST /checkout.*")
    public void measureLatency(LogEntry entry) {
        entry.latencyMs().ifPresent(checkoutLatencies::add);
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public Set<String> getUniqueErrorIps() {
        return uniqueErrorIps;
    }

    public List<Integer> getCheckoutLatencies() {
        return checkoutLatencies;
    }
}