package com.clinicflow.repository;

import com.clinicflow.model.ReminderRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link ReminderRepository}.
 */
public class InMemoryReminderRepository implements ReminderRepository {

    private final Map<Long, ReminderRecord> storage = new ConcurrentHashMap<>();

    @Override
    public ReminderRecord save(ReminderRecord entity) {
        if (entity == null) {
            throw new IllegalArgumentException("ReminderRecord cannot be null");
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<ReminderRecord> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<ReminderRecord> findAll() {
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
    public List<ReminderRecord> findByAppointmentId(Long appointmentId) {
        if (appointmentId == null) return List.of();
        return storage.values().stream()
                .filter(r -> r.getAppointmentId().equals(appointmentId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReminderRecord> findPendingReminders() {
        return storage.values().stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReminderRecord> findSentReminders() {
        return storage.values().stream()
                .filter(r -> "SENT".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
    }
}
