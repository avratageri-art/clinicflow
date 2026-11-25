package com.clinicflow.util;

/**
 * Thrown when loading, parsing, or accessing the dataset CSV fails.
 */
public class DatasetLoadingException extends ClinicFlowException {

    public DatasetLoadingException(String message) {
        super(message);
    }

    public DatasetLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
