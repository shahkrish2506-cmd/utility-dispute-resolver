package com.utilityresolver.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Appends every change (bill added, dispute resolved, ...) to audit.log so that
 * roommates can see who changed what and when. FILE HANDLING (append mode).
 */
public class AuditLogger {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path file;

    public AuditLogger(Path file) {
        this.file = file;
    }

    public synchronized void log(String action, String details) {
        String line = LocalDateTime.now().format(FMT) + " | " + action + " | " + details.replace('\n', ' ');
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
            w.newLine();
        } catch (IOException e) {
            System.err.println("Could not write audit log: " + e.getMessage());
        }
    }

    /** Most recent entries first. */
    public synchronized List<String> recent(int limit) {
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            Collections.reverse(lines);
            return lines.subList(0, Math.min(limit, lines.size()));
        } catch (IOException e) {
            return Collections.singletonList("Could not read audit log: " + e.getMessage());
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("Could not clear audit log: " + e.getMessage());
        }
    }
}
