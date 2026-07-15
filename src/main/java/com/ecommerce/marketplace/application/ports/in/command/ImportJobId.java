package com.ecommerce.marketplace.application.ports.in.command;

import java.util.UUID;

/**
 * Identifier of an asynchronous CSV import job. Lives in {@code application} rather than {@code
 * domain.model} because an import job is a tracking concern, not a domain aggregate.
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
