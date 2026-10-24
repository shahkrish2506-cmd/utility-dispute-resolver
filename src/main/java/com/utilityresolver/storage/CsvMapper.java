package com.utilityresolver.storage;

import com.utilityresolver.exception.AppException;

/** Converts one entity type to/from a CSV row. One implementation per entity. */
public interface CsvMapper<T> {

    String[] header();

    String[] toRow(T item);

    T fromRow(String[] row) throws AppException;
}
