package com.ecommerce.marketplace.application.ports.in.command;

import java.util.UUID;

/**
 * Identifier of an asynchronous CSV import job (US-16's {@code import_jobs} table uses a UUID
 * primary key). Lives in {@code application} rather than {@code domain.model} because import
 * jobs are an application/infrastructure tracking concern, not a domain aggregate — {@code
 * Product} and {@code Order} are the domain's aggregates; a job is metadata about a batch
 * operation on the former.
 */
public record ImportJobId(UUID value) {

    public ImportJobId {
        if (value == null) {
            throw new IllegalArgumentException("ImportJobId requires a non-null UUID");
        }
    }

    public static ImportJobId generate() {
        return new ImportJobId(UUID.randomUUID());
    }
}
