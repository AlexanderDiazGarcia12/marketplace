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
 * file into memory (RNF-2/RNF-3). Wraps Apache Commons CSV, whose {@link CSVFormat#DEFAULT} is
 * RFC-4180 compliant, so quoted fields with embedded commas (e.g.
 * {@code "Single origin, medium roast, 1kg bag"}) are parsed as a single field rather than split —
 * the naive {@code String.split(",")} the reference file would break on is deliberately avoided. The
 * header line is skipped; only data rows are counted (1-based, so {@code row_number} matches the
 * {@code import_job_errors} contract).
 *
 * <p>Reading is pull-based over the file's own {@link CSVParser} iterator: {@link #forEachChunk}
 * fills one {@code chunkSize}-bounded list at a time, hands it to the caller (which processes it in a
 * single transaction), then reuses the buffer — so at most one chunk of rows is resident at once,
 * regardless of file size. Returns the total number of data rows read this pass, which the worker
 * uses as the idempotent {@code total_rows}. The CSV library stays confined to {@code infrastructure};
 * the application row validator only ever sees the framework-free {@link RawProductRow}.</p>
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
