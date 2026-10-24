package com.utilityresolver.storage;

import com.utilityresolver.exception.EntityNotFoundException;
import com.utilityresolver.model.Identifiable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** GENERIC storage contract for any entity that has an id. */
public interface Repository<T extends Identifiable> {

    void save(T item) throws IOException;

    boolean delete(String id) throws IOException;

    Optional<T> findById(String id);

    T getOrThrow(String id) throws EntityNotFoundException;

    List<T> findAll();

    List<T> findWhere(Predicate<T> condition);

    void clear() throws IOException;
}
