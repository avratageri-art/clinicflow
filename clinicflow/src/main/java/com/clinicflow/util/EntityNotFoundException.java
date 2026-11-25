package com.clinicflow.util;

/**
 * Thrown when a requested domain entity (patient, doctor, appointment) is not found.
 */
public class EntityNotFoundException extends ClinicFlowException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
