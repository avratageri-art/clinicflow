package com.clinicflow.service;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.ReminderRecord;
import com.clinicflow.repository.AppointmentRepository;
import com.clinicflow.repository.CsvReminderRepository;
import com.clinicflow.repository.ReminderRepository;
import com.clinicflow.util.EntityNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service managing appointment confirmation reminders.
 * For academic coursework demonstration, all SMS/phone reminders are simulated locally
 * with persistent logging to data/app/reminders.csv without incurring external messaging costs.
 */
public class ReminderService {

    public static final String ACADEMIC_DISCLAIMER =
            "Reminder simulated for academic demonstration. No third-party messaging charges incurred.";

    private final ReminderRepository reminderRepository;
    private final AppointmentRepository appointmentRepository;

    public ReminderService(ReminderRepository reminderRepository, AppointmentRepository appointmentRepository) {
        this.reminderRepository = Objects.requireNonNull(reminderRepository, "reminderRepository cannot be null");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "appointmentRepository cannot be null");
    }

    /**
     * Automatically scans upcoming appointments and synchronizes pending reminder entries.
     */
    public synchronized void syncRemindersWithAppointments() {
        LocalDate today = LocalDate.now();
        List<Appointment> upcoming = appointmentRepository.findAll().stream()
                .filter(a -> !a.getAppointmentDay().isBefore(today) && !a.getAttendanceStatus().isFinished())
                .collect(Collectors.toList());

        for (Appointment appt : upcoming) {
            List<ReminderRecord> existing = reminderRepository.findByAppointmentId(appt.getAppointmentId());
            if (existing.isEmpty()) {
                Long nextId;
                if (reminderRepository instanceof CsvReminderRepository) {
                    nextId = ((CsvReminderRepository) reminderRepository).generateNextId();
                } else {
                    nextId = System.currentTimeMillis();
                }
                ReminderRecord record = ReminderRecord.fromAppointment(nextId, appt);
                reminderRepository.save(record);
            }
        }
    }

    public List<ReminderRecord> getAllReminders() {
        return reminderRepository.findAll();
    }

    public List<ReminderRecord> getPendingReminders() {
        return reminderRepository.findPendingReminders();
    }

    public List<ReminderRecord> getSentReminders() {
        return reminderRepository.findSentReminders();
    }

    public Optional<ReminderRecord> getReminderById(Long id) {
        return reminderRepository.findById(id);
    }

    /**
     * Simulates sending a clinical reminder (SMS or Phone confirmation).
     */
    public synchronized ReminderRecord simulateSendReminder(Long reminderId) {
        ReminderRecord record = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("Reminder record with ID " + reminderId + " not found"));

        record.markSimulatedSent();
        reminderRepository.save(record);

        // Update corresponding appointment reminder status
        appointmentRepository.findById(record.getAppointmentId()).ifPresent(appt -> {
            appt.setReminderStatus("SENT (" + record.getRecommendedAction().name() + ")");
            appointmentRepository.save(appt);
        });

        return record;
    }
}
