package com.ecommerce.marketplace.application.ports.in.command;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.ImportProductsUseCase}.
 *
 * <p>Per US-16, uploading a CSV only validates format/headers/size on the web thread, persists
 * the file to a referenceable location and creates an {@code import_jobs} row in {@code PENDING}
 * — the actual row-by-row processing happens later, asynchronously, in the US-17 consumer. This
 * command therefore carries a {@code fileReference} (an opaque handle to already-persisted
 * content, e.g. a storage path or key) rather than the file bytes themselves: streaming/multipart
 * handling is a web-layer concern (US-16), not something the application port should model.
 * {@code originalFilename} is kept for job auditing/display (US-18).</p>
 */
public record ImportProductsCommand(String fileReference, String originalFilename) {

    public ImportProductsCommand {
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("ImportProductsCommand requires a non-blank fileReference");
        }
        originalFilename = originalFilename == null ? "" : originalFilename.trim();
    }
}
