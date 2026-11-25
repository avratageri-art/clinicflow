package com.clinicflow.repository;

import com.clinicflow.model.Patient;
import com.clinicflow.util.DuplicateEntityException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory storage implementation for {@link Patient} entities.
 * Demonstrates Java Collections (HashMap, ArrayList) and Encapsulation.
 */
public class InMemoryPatientRepository implements PatientRepository {

    private final Map<Long, Patient> storage = new HashMap<>();

    @Override
    public synchronized Patient save(Patient entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    public synchronized Patient saveNew(Patient entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        if (storage.containsKey(entity.getId())) {
            throw new DuplicateEntityException("Patient with ID " + entity.getId() + " already exists.");
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public synchronized Optional<Patient> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<Patient> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public synchronized boolean existsById(Long id) {
        if (id == null) return false;
        return storage.containsKey(id);
    }

    @Override
    public synchronized boolean deleteById(Long id) {
        if (id == null) return false;
        return storage.remove(id) != null;
    }

    @Override
    public synchronized long count() {
        return storage.size();
    }

    @Override
    public synchronized void clear() {
        storage.clear();
    }

    @Override
    public synchronized List<Patient> findByNameContaining(String namePart) {
        if (namePart == null || namePart.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lower = namePart.trim().toLowerCase();
        List<Patient> result = new ArrayList<>();
        for (Patient p : storage.values()) {
            if (p.getName().toLowerCase().contains(lower)) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public synchronized List<Patient> findByHasChronicConditions() {
        List<Patient> result = new ArrayList<>();
        for (Patient p : storage.values()) {
            if (p.hasChronicConditions()) {
                result.add(p);
            }
        }
        return result;
    }
}
