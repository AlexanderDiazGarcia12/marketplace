package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Persists an accepted CSV upload to a configurable directory ({@code marketplace.import.storage-dir})
 * under a UUID-based name and returns its absolute path as an opaque {@code fileReference} the
 * asynchronous worker re-opens to stream rows. The stream is copied in a single pass so the web
 * thread's footprint stays bounded; a filesystem error is returned as
 * {@link Failure.InvalidCsvUpload}, never thrown across the request.
 */
@Component
public class CsvUploadStorage {

    private final Path storageDir;

    public CsvUploadStorage(@Value("${marketplace.import.storage-dir}") String storageDir) {
        this.storageDir = Path.of(storageDir);
    }

    public Either<Failure, String> store(MultipartFile file) {
        try (InputStream source = file.getInputStream()) {
            Files.createDirectories(storageDir);
            Path target = storageDir.resolve(UUID.randomUUID() + ".csv");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return Either.right(target.toAbsolutePath().toString());
        } catch (IOException storageError) {
            return Either.left(new Failure.InvalidCsvUpload(
                    "The file could not be stored for processing. Please try again."));
        }
    }
}
