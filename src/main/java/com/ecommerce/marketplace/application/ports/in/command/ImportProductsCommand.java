package com.ecommerce.marketplace.application.ports.in.command;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.ImportProductsUseCase}.
 * Carries a {@code fileReference} (an opaque handle to already-persisted content) rather than the
 * file bytes, since multipart/streaming handling stays a web-layer concern; {@code
 * originalFilename} is retained for job auditing and display.
 */
public record ImportProductsCommand(String fileReference, String originalFilename) {

    public ImportProductsCommand {
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("ImportProductsCommand requires a non-blank fileReference");
        }
        originalFilename = originalFilename == null ? "" : originalFilename.trim();
    }
}
