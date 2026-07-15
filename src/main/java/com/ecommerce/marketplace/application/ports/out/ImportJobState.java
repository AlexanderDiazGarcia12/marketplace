package com.ecommerce.marketplace.application.ports.out;

/**
 * Application-visible lifecycle of an import job, decoupled from the persistence-layer
 * {@code ImportJobStatus} that mirrors the native Postgres enum. The worker branches on the state
 * read back through {@link ImportJobRepositoryPort#currentState} to decide whether a redelivered
 * request should be processed, retried or treated as a no-op.
 */
public enum ImportJobState {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
