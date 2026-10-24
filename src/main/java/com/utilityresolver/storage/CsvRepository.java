package com.utilityresolver.storage;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.exception.EntityNotFoundException;
import com.utilityresolver.model.Identifiable;
import com.utilityresolver.util.CsvUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * GENERIC repository that keeps entities in memory (LinkedHashMap keeps insertion
 * order) and persists them to a CSV file on every change. FILE HANDLING + COLLECTIONS + GENERICS.
 */
public class CsvRepository<T extends Identifiable> implements Repository<T> {
    private final Path file;
    private final CsvMapper<T> mapper;
    private final String entityName;
    private final Map<String, T> items = new LinkedHashMap<>();

    public CsvRepository(Path file, CsvMapper<T> mapper, String entityName) {
        this.file = file;
        this.mapper = mapper;
        this.entityName = entityName;
    }

    /** Reads the CSV file; rows that fail validation are skipped with a warning. */
    public synchronized void load() throws IOException {
        items.clear();
        if (!Files.exists(file)) {
            return;
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        List<String[]> rows = CsvUtil.parse(text);
        for (int i = 1; i < rows.size(); i++) { // row 0 is the header
            try {
                T item = mapper.fromRow(rows.get(i));
                items.put(item.getId(), item);
            } catch (AppException | RuntimeException e) {
                System.err.println("Skipping bad row " + (i + 1) + " in " + file.getFileName() + ": " + e.getMessage());
            }
        }
    }

    /** Writes to a temp file first, then replaces the real file (no half-written files). */
    private void persist() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(CsvUtil.toLine(mapper.header()));
            w.newLine();
            for (T item : items.values()) {
                w.write(CsvUtil.toLine(mapper.toRow(item)));
                w.newLine();
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public synchronized void save(T item) throws IOException {
        items.put(item.getId(), item);
        persist();
    }

    /** Re-write the file after an object was modified in place. */
    public synchronized void flush() throws IOException {
        persist();
    }

    @Override
    public synchronized boolean delete(String id) throws IOException {
        boolean removed = items.remove(id) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    @Override
    public synchronized Optional<T> findById(String id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public synchronized T getOrThrow(String id) throws EntityNotFoundException {
        T item = items.get(id);
        if (item == null) {
            throw new EntityNotFoundException(entityName, id);
        }
        return item;
    }

    @Override
    public synchronized List<T> findAll() {
        return new ArrayList<>(items.values());
    }

    @Override
    public synchronized List<T> findWhere(Predicate<T> condition) {
        return items.values().stream().filter(condition).collect(Collectors.toList());
    }

    @Override
    public synchronized void clear() throws IOException {
        items.clear();
        persist();
    }

    public List<String> ids() {
        return new ArrayList<>(items.keySet());
    }
}
