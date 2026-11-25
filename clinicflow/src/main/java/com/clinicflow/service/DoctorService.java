package com.clinicflow.service;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
import com.clinicflow.repository.AppointmentRepository;
import com.clinicflow.repository.CsvDoctorRepository;
import com.clinicflow.repository.DoctorRepository;
import com.clinicflow.util.ClinicFlowException;
import com.clinicflow.util.DuplicateEntityException;
import com.clinicflow.util.EntityNotFoundException;
import com.clinicflow.util.ValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service managing physician profiles, specialty assignments, and availability schedules.
 */
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    public DoctorService(DoctorRepository doctorRepository, AppointmentRepository appointmentRepository) {
        this.doctorRepository = Objects.requireNonNull(doctorRepository, "doctorRepository cannot be null");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "appointmentRepository cannot be null");
    }

    public Doctor registerDoctor(Long id, String name, Gender gender, int age, String specialty,
                                 String roomNumber, String contactNumber, boolean available) {
        validateDoctorInputs(name, specialty);

        Long docId = id;
        if (docId == null) {
            if (doctorRepository instanceof CsvDoctorRepository) {
                docId = ((CsvDoctorRepository) doctorRepository).generateNextId();
            } else {
                docId = System.currentTimeMillis();
            }
        } else if (doctorRepository.existsById(docId)) {
            throw new DuplicateEntityException("Doctor with ID " + docId + " already exists");
        }

        Doctor doc = new Doctor(docId, name, gender, age, specialty, roomNumber, contactNumber, available);
        return doctorRepository.save(doc);
    }

    public Doctor updateDoctor(Long id, String name, Gender gender, int age, String specialty,
                               String roomNumber, String contactNumber, boolean available) {
        if (!doctorRepository.existsById(id)) {
            throw new EntityNotFoundException("Doctor with ID " + id + " not found");
        }
        validateDoctorInputs(name, specialty);

        Doctor doc = new Doctor(id, name, gender, age, specialty, roomNumber, contactNumber, available);
        return doctorRepository.save(doc);
    }

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    public List<Doctor> getAvailableDoctors() {
        return doctorRepository.findAvailableDoctors();
    }

    public Optional<Doctor> getDoctorById(Long id) {
        return doctorRepository.findById(id);
    }

    public List<Doctor> searchDoctors(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllDoctors();
        }
        String lower = query.trim().toLowerCase();
        return doctorRepository.findAll().stream()
                .filter(d -> d.getName().toLowerCase().contains(lower) ||
                        d.getSpecialty().toLowerCase().contains(lower) ||
                        d.getRoomNumber().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public Doctor toggleAvailability(Long id) {
        Doctor doc = doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor with ID " + id + " not found"));
        doc.setAvailable(!doc.isAvailable());
        return doctorRepository.save(doc);
    }

    public boolean deleteDoctor(Long id) {
        if (!doctorRepository.existsById(id)) {
            throw new EntityNotFoundException("Doctor with ID " + id + " not found");
        }
        // Verify no upcoming/active appointments for this doctor
        LocalDate today = LocalDate.now();
        List<Appointment> upcoming = appointmentRepository.findAll().stream()
                .filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(id))
                .filter(a -> !a.getAppointmentDay().isBefore(today) && !a.getAttendanceStatus().isFinished())
                .collect(Collectors.toList());

        if (!upcoming.isEmpty()) {
            throw new ClinicFlowException("Cannot remove Doctor with ID " + id + " because they have " +
                    upcoming.size() + " active or upcoming scheduled appointments");
        }
        return doctorRepository.deleteById(id);
    }

    private void validateDoctorInputs(String name, String specialty) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Doctor name cannot be empty");
        }
        if (specialty == null || specialty.trim().isEmpty()) {
            throw new ValidationException("Medical specialty cannot be empty");
        }
    }
}
