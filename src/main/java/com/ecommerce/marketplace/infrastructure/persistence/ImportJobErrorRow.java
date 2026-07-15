package com.ecommerce.marketplace.infrastructure.persistence;

/**
 * Spring Data interface-based projection over the {@code import_job_errors} columns the status view
 * lists. {@code reasons} is the {@code error_reason} JSONB array cast to {@code text} in the query,
 * which {@link PostgreSQLImportErrorRepositoryAdapter} deserializes back into a {@code Seq<String>}
 * with the same {@code ObjectMapper} that serialized it on write. Confined to
 * {@code infrastructure.persistence}.
 */
interface ImportJobErrorRow {

    int getRowNumber();

    String getRawLine();

    String getReasons();
}
