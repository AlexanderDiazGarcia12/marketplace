package com.ecommerce.marketplace.infrastructure.messaging;

import com.ecommerce.marketplace.application.service.RawProductRow;
import io.vavr.control.Try;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Streams a stored CSV file into bounded chunks of {@link ParsedRow}s without ever loading the whole
 * file into memory. Wraps Apache Commons CSV ({@link CSVFormat#DEFAULT} is RFC-4180 compliant) so
 * quoted fields with embedded commas parse as a single field rather than split on a naive
 * {@code String.split(",")}. {@link #forEachChunk} fills one bounded list at a time and reuses the
 * buffer, so at most one chunk is resident regardless of file size, and returns the total data-row
 * count the worker uses as the idempotent {@code total_rows}. Data rows are numbered 1-based to match
 * the {@code import_job_errors} contract; the CSV library stays confined to {@code infrastructure},
 * exposing only the framework-free {@link RawProductRow}.
 */
final class CsvProductRowReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(false)
            .build();

    private CsvProductRowReader() {
    }

    static Try<Long> forEachChunk(Path file, int chunkSize, Consumer<List<ParsedRow>> chunkConsumer) {
        return Try.of(() -> {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                 CSVParser parser = CSVParser.parse(reader, FORMAT)) {
                return streamChunks(parser, chunkSize, chunkConsumer);
            }
        });
    }

    private static long streamChunks(CSVParser parser, int chunkSize, Consumer<List<ParsedRow>> chunkConsumer) {
        Iterator<CSVRecord> records = parser.iterator();
        List<ParsedRow> chunk = new ArrayList<>(chunkSize);
        long dataRowNumber = 0;
        while (records.hasNext()) {
            CSVRecord record = records.next();
            dataRowNumber++;
            chunk.add(new ParsedRow(Math.toIntExact(dataRowNumber), toRawRow(record), rawLineOf(record)));
            if (chunk.size() == chunkSize) {
                chunkConsumer.accept(chunk);
                chunk = new ArrayList<>(chunkSize);
            }
        }
        if (!chunk.isEmpty()) {
            chunkConsumer.accept(chunk);
        }
        return dataRowNumber;
    }

    private static RawProductRow toRawRow(CSVRecord record) {
        return new RawProductRow(
                valueAt(record, 0),
                valueAt(record, 1),
                valueAt(record, 2),
                valueAt(record, 3),
                valueAt(record, 4),
                valueAt(record, 5),
                valueAt(record, 6));
    }

    private static String valueAt(CSVRecord record, int index) {
        return index < record.size() ? record.get(index) : null;
    }

    private static String rawLineOf(CSVRecord record) {
        return String.join(",", record.toList());
    }

    record ParsedRow(int rowNumber, RawProductRow raw, String rawLine) {
    }
}
