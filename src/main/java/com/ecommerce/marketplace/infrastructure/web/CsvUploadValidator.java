package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Envelope-only validation of a CSV upload before any job is created: presence, file extension, and
 * that the first line matches the expected product header. Data rows are validated later by the
 * asynchronous worker, so this only confirms the upload is the right kind of file. Every rejection is
 * returned as {@link Failure.InvalidCsvUpload}, never thrown; the size ceiling is enforced upstream by
 * Spring's multipart limits.
 */
final class CsvUploadValidator {

    static final String EXPECTED_HEADER = "name,sku,description,category,price,stock,weight_kg";
    private static final String CSV_EXTENSION = ".csv";

    private CsvUploadValidator() {
    }

    static Either<Failure, MultipartFile> validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return reject("Choose a non-empty .csv file to import.");
        }
        if (!hasCsvExtension(file.getOriginalFilename())) {
            return reject("The file must be a .csv file.");
        }
        return readHeaderLine(file).flatMap(CsvUploadValidator::checkHeader).map(header -> file);
    }

    private static boolean hasCsvExtension(String originalFilename) {
        return originalFilename != null && originalFilename.toLowerCase().endsWith(CSV_EXTENSION);
    }

    private static Either<Failure, String> readHeaderLine(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            return header == null
                    ? Either.left(new Failure.InvalidCsvUpload("The file is empty."))
                    : Either.right(header);
        } catch (IOException readError) {
            return Either.left(new Failure.InvalidCsvUpload("The file could not be read."));
        }
    }

    private static Either<Failure, String> checkHeader(String header) {
        return normalize(header).equals(EXPECTED_HEADER)
                ? Either.right(header)
                : reject("Unexpected CSV header. Expected: " + EXPECTED_HEADER);
    }

    private static String normalize(String header) {
        return header.replace("﻿", "").trim().toLowerCase().replace(" ", "");
    }

    private static <T> Either<Failure, T> reject(String reason) {
        return Either.left(new Failure.InvalidCsvUpload(reason));
    }
}
