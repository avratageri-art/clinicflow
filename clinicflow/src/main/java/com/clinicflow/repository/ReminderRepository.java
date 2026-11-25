package com.clinicflow.repository;

import com.clinicflow.model.ReminderRecord;

import java.util.List;

/**
 * Specialized repository interface for {@link ReminderRecord} entries.
 */
public interface ReminderRepository extends Repository<ReminderRecord, Long> {

    List<ReminderRecord> findByAppointmentId(Long appointmentId);

    List<ReminderRecord> findPendingReminders();

    List<ReminderRecord> findSentReminders();
}
