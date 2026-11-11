package com.loghog;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class LogGenerator {

    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String[] IPS = {"192.168.1.1", "10.0.0.5", "172.16.3.14", "203.0.113.42"};
    private static final String CHECKOUT_ENDPOINT = "POST /checkout HTTP/1.1";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java com.loghog.LogGenerator <outputFile> <numLines>");
            System.exit(1);
        }

        String outputFile = args[0];
        int numLines = Integer.parseInt(args[1]);

        System.out.println("Generating " + numLines + " log lines to " + outputFile + "...");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (int i = 0; i < numLines; i++) {
                writer.write(generateLogLine());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Log file generation complete.");
    }

    private static String generateLogLine() {
        LocalDateTime timestamp = LocalDateTime.now().minusSeconds(RANDOM.nextInt(3600));
        String ts = timestamp.format(FORMATTER);
        int type = RANDOM.nextInt(100);

        if (type < 80) { // 80% INFO
            int latency = 50 + RANDOM.nextInt(150);
            return String.format("%s INFO: Request successful: %s status=200 latency_ms=%d", ts, CHECKOUT_ENDPOINT, latency);
        } else if (type < 95) { // 15% WARN
            return String.format("%s WARN: High memory usage detected on service-payments.", ts);
        } else if (type < 99) { // 4% ERROR
            String ip = IPS[RANDOM.nextInt(IPS.length)];
            return String.format("%s ERROR: Connection timeout from client %s. Failed to process request for /checkout.", ts, ip);
        } else { // 1% Corrupt Data
            return "CORRUPT_DATA_ENTRY" + ts.replaceAll("\\s", "_");
        }
    }
}