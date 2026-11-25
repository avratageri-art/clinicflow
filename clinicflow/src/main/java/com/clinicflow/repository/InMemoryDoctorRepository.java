package com.clinicflow.repository;

import com.clinicflow.model.Doctor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link DoctorRepository}.
 */
public class InMemoryDoctorRepository implements DoctorRepository {

    private final Map<Long, Doctor> storage = new ConcurrentHashMap<>();

    @Override
    public Doctor save(Doctor entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Doctor cannot be null");
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<Doctor> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Doctor> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean existsById(Long id) {
        if (id == null) return false;
        return storage.containsKey(id);
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) return false;
        return storage.remove(id) != null;
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void clear() {
        storage.clear();
    }

    @Override
    public List<Doctor> findAvailableDoctors() {
        return storage.values().stream()
                .filter(Doctor::isAvailable)
                .collect(Collectors.toList());
    }

    @Override
    public List<Doctor> findBySpecialty(String specialty) {
        if (specialty == null) return List.of();
        return storage.values().stream()
                .filter(d -> d.getSpecialty().equalsIgnoreCase(specialty.trim()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Doctor> findByName(String name) {
        if (name == null) return Optional.empty();
        return storage.values().stream()
                .filter(d -> d.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }
}
