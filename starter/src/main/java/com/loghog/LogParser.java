package com.loghog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Regex matching timestamp, level, and message body
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(INFO|WARN|ERROR):\\s+(.*)$");
    
    // Regex to extract IP addresses from error messages
    private static final Pattern IP_PATTERN = Pattern.compile("client\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
    
    // Regex to extract latency values
    private static final Pattern LATENCY_PATTERN = Pattern.compile("latency_ms=(\\d+)");

    public static LogEntry parse(String line) throws Exception {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Blank or null line");
        }
        
        Matcher matcher = LOG_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Line does not match expected log format");
        }

        String tsStr = matcher.group(1);
        String levelStr = matcher.group(2);
        String message = matcher.group(3);

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(tsStr, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid timestamp format", e);
        }

        LogEntry.LogLevel level;
        try {
            level = LogEntry.LogLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            level = LogEntry.LogLevel.UNKNOWN;
        }

        Optional<String> ipAddress = Optional.empty();
        Matcher ipMatcher = IP_PATTERN.matcher(message);
        if (ipMatcher.find()) {
            ipAddress = Optional.of(ipMatcher.group(1));
        }

        Optional<Integer> latencyMs = Optional.empty();
        Matcher latMatcher = LATENCY_PATTERN.matcher(message);
        if (latMatcher.find()) {
            try {
                latencyMs = Optional.of(Integer.parseInt(latMatcher.group(1)));
            } catch (NumberFormatException ignored) {}
        }

        return new LogEntry(timestamp, level, message, ipAddress, latencyMs);
    }
}