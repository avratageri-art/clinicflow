package com.clinicflow.repository;

import com.clinicflow.model.Identifiable;
import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface defining standard CRUD and query operations.
 * Demonstrates Java Generics and Interface design.
 *
 * @param <T>  Entity type implementing {@link Identifiable}
 * @param <ID> Primary key / identifier type
 */
public interface Repository<T extends Identifiable<ID>, ID> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    boolean existsById(ID id);

    boolean deleteById(ID id);

    long count();

    void clear();
}
