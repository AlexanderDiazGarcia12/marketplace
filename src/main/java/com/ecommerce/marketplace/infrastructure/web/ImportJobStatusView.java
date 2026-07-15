package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.query.ImportJobStatusReport;
import com.ecommerce.marketplace.application.ports.out.ImportJobState;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Presentation-layer projection of an {@link ImportJobStatusReport} for the status view. Keeps the
 * application-layer report out of the template and pre-computes the per-state Tailwind badge classes,
 * the in-flight flag that drives the meta-refresh, and the formatted timestamps.
 */
record ImportJobStatusView(
        String jobId,
        String state,
        String badgeClasses,
        boolean inProgress,
        long totalRows,
        long acceptedRows,
        long rejectedRows,
        String originalFilename,
        String createdAt,
        String completedAt,
        List<Row> errors) {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    record Row(int rowNumber, String rawLine, List<String> reasons) {
    }

    static ImportJobStatusView from(ImportJobStatusReport report) {
        ImportJobState state = report.detail().state();
        return new ImportJobStatusView(
                report.detail().id().value().toString(),
                state.name(),
                badgeClassesFor(state),
                state == ImportJobState.PENDING || state == ImportJobState.PROCESSING,
                report.detail().counters().total(),
                report.detail().counters().accepted(),
                report.detail().counters().rejected(),
                report.detail().originalFilename(),
                format(report.detail().createdAt()),
                format(report.detail().completedAt()),
                report.errors()
                        .map(error -> new Row(error.rowNumber(), error.rawLine(), error.reasons().toJavaList()))
                        .toJavaList());
    }

    private static String badgeClassesFor(ImportJobState state) {
        return switch (state) {
            case PENDING -> "bg-amber-100 text-amber-800 ring-amber-200";
            case PROCESSING -> "bg-sky-100 text-sky-800 ring-sky-200";
            case COMPLETED -> "bg-emerald-100 text-emerald-800 ring-emerald-200";
            case FAILED -> "bg-rose-100 text-rose-800 ring-rose-200";
        };
    }

    private static String format(OffsetDateTime timestamp) {
        return timestamp == null ? "—" : TIMESTAMP.format(timestamp);
    }
}
