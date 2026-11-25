package com.clinicflow.repository;

import com.clinicflow.model.Doctor;

import java.util.List;
import java.util.Optional;

/**
 * Specialized repository interface for {@link Doctor} records.
 */
public interface DoctorRepository extends Repository<Doctor, Long> {

    List<Doctor> findAvailableDoctors();

    List<Doctor> findBySpecialty(String specialty);

    Optional<Doctor> findByName(String name);
}
