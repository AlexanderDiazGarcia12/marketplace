package com.ecommerce.marketplace.application.ports.out;

/**
 * Application-visible lifecycle of an import job, decoupled from the persistence-layer
 * {@code ImportJobStatus} (which mirrors the native Postgres enum and stays inside
 * {@code infrastructure.persistence}). The US-17 worker branches on the state it reads back through
 * {@link ImportJobRepositoryPort#currentState} to decide whether a redelivered {@code ImportRequested}
 * should be processed, retried or treated as a no-op — see {@link ImportJobRepositoryPort}.
 */
public enum ImportJobState {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
