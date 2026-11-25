package com.clinicflow.repository;

import com.clinicflow.model.Patient;
import java.util.List;

/**
 * Specialized repository interface for {@link Patient} records.
 */
public interface PatientRepository extends Repository<Patient, Long> {

    List<Patient> findByNameContaining(String namePart);

    List<Patient> findByHasChronicConditions();
}
