package com.clinicflow.repository;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.AttendanceStatus;
import com.clinicflow.util.DuplicateEntityException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory storage implementation for {@link Appointment} records.
 * Demonstrates Java Collections (HashMap, ArrayList).
 */
public class InMemoryAppointmentRepository implements AppointmentRepository {

    private final Map<Long, Appointment> storage = new HashMap<>();

    @Override
    public synchronized Appointment save(Appointment entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    public synchronized Appointment saveNew(Appointment entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }
        if (storage.containsKey(entity.getId())) {
            throw new DuplicateEntityException("Appointment with ID " + entity.getId() + " already exists.");
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public synchronized Optional<Appointment> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<Appointment> findAll() {
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
    public synchronized List<Appointment> findByPatientId(Long patientId) {
        if (patientId == null) return Collections.emptyList();
        List<Appointment> matches = new ArrayList<>();
        for (Appointment a : storage.values()) {
            if (patientId.equals(a.getPatient().getId())) {
                matches.add(a);
            }
        }
        return matches;
    }

    @Override
    public synchronized List<Appointment> findByAppointmentDay(LocalDate date) {
        if (date == null) return Collections.emptyList();
        List<Appointment> matches = new ArrayList<>();
        for (Appointment a : storage.values()) {
            if (date.equals(a.getAppointmentDay())) {
                matches.add(a);
            }
        }
        return matches;
    }

    @Override
    public synchronized List<Appointment> findByStatus(AttendanceStatus status) {
        if (status == null) return Collections.emptyList();
        List<Appointment> matches = new ArrayList<>();
        for (Appointment a : storage.values()) {
            if (status.equals(a.getAttendanceStatus())) {
                matches.add(a);
            }
        }
        return matches;
    }
}
