package com.loghog;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Represents a single parsed log entry.
 * Using a record for an immutable data carrier.
 */
public record LogEntry(
    LocalDateTime timestamp,
    LogLevel level,
    String message,
    Optional<String> ipAddress,
    Optional<Integer> latencyMs
) {
    public enum LogLevel {
        INFO, WARN, ERROR, UNKNOWN
    }
}