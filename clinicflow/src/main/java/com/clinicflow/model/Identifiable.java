package com.clinicflow.model;

/**
 * Generic contract for domain entities with a unique identifier.
 * Demonstrates Java interface and generics.
 *
 * @param <ID> the type of identifier
 */
public interface Identifiable<ID> {
    ID getId();
}
