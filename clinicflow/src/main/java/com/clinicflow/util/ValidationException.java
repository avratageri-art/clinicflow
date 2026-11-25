package com.clinicflow.util;

/**
 * Thrown when clinical, patient, or appointment data fails business validation rules.
 */
public class ValidationException extends ClinicFlowException {

    public ValidationException(String message) {
        super(message);
    }
}
