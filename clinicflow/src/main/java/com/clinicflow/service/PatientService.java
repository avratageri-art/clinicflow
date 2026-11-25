package com.clinicflow.service;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.Gender;
import com.clinicflow.model.Patient;
import com.clinicflow.repository.AppointmentRepository;
import com.clinicflow.repository.CsvPatientRepository;
import com.clinicflow.repository.PatientRepository;
import com.clinicflow.util.ClinicFlowException;
import com.clinicflow.util.DuplicateEntityException;
import com.clinicflow.util.EntityNotFoundException;
import com.clinicflow.util.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing patient registration, updates, search, and appointment history.
 */
public class PatientService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public PatientService(PatientRepository patientRepository, AppointmentRepository appointmentRepository) {
        this.patientRepository = Objects.requireNonNull(patientRepository, "patientRepository cannot be null");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "appointmentRepository cannot be null");
    }

    public Patient registerPatient(Long id, String name, Gender gender, int age, String contactNumber,
                                   boolean scholarship, boolean hipertension, boolean diabetes,
                                   boolean alcoholism, int handicap) {
        validatePatientInputs(name, age, contactNumber);

        Long patientId = id;
        if (patientId == null) {
            if (patientRepository instanceof CsvPatientRepository) {
                patientId = ((CsvPatientRepository) patientRepository).generateNextId();
            } else {
                patientId = System.currentTimeMillis();
            }
        } else if (patientRepository.existsById(patientId)) {
            throw new DuplicateEntityException("Patient with ID " + patientId + " already exists");
        }

        Patient patient = new Patient(patientId, name, gender, age, scholarship, hipertension, diabetes, alcoholism, handicap, contactNumber);
        return patientRepository.save(patient);
    }

    public Patient updatePatient(Long id, String name, Gender gender, int age, String contactNumber,
                                 boolean scholarship, boolean hipertension, boolean diabetes,
                                 boolean alcoholism, int handicap) {
        if (!patientRepository.existsById(id)) {
            throw new EntityNotFoundException("Patient with ID " + id + " not found");
        }
        validatePatientInputs(name, age, contactNumber);

        Patient patient = new Patient(id, name, gender, age, scholarship, hipertension, diabetes, alcoholism, handicap, contactNumber);
        return patientRepository.save(patient);
    }

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Optional<Patient> getPatientById(Long id) {
        return patientRepository.findById(id);
    }

    public List<Patient> searchPatients(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllPatients();
        }
        return patientRepository.findByNameContaining(query);
    }

    public boolean deletePatient(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new EntityNotFoundException("Patient with ID " + id + " not found");
        }
        List<Appointment> appts = appointmentRepository.findByPatientId(id);
        if (!appts.isEmpty()) {
            throw new ClinicFlowException("Cannot delete patient with ID " + id + " because " +
                    appts.size() + " appointment records are associated with this patient");
        }
        return patientRepository.deleteById(id);
    }

    public List<Appointment> getPatientHistory(Long id) {
        return appointmentRepository.findByPatientId(id);
    }

    private void validatePatientInputs(String name, int age, String contact) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Patient name cannot be empty");
        }
        if (age < 0 || age > 125) {
            throw new ValidationException("Patient age must be between 0 and 125, got: " + age);
        }
        if (contact != null && !contact.trim().isEmpty()) {
            // reasonable phone format: digits, +, -, spaces
            if (!contact.matches("^[+0-9\\s-]{6,20}$")) {
                throw new ValidationException("Invalid contact number format: " + contact);
            }
        }
    }
}
