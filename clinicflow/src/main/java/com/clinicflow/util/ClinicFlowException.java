package com.clinicflow.util;

/**
 * Base custom unchecked exception for the ClinicFlow application.
 * Demonstrates hierarchical Java Exception Handling.
 */
public class ClinicFlowException extends RuntimeException {

    public ClinicFlowException(String message) {
        super(message);
    }

    public ClinicFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
