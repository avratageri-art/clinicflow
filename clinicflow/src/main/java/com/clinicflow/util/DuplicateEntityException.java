package com.clinicflow.util;

/**
 * Thrown when attempting to register an entity with an existing duplicate ID.
 */
public class DuplicateEntityException extends ClinicFlowException {

    public DuplicateEntityException(String message) {
        super(message);
    }
}
