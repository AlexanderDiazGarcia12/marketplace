package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.GetImportJobStatusUseCase;
import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.query.ImportJobStatusReport;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Try;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Import-job status page. {@code GET /products/import/{jobId}} renders the progress, counters and
 * per-row errors of an asynchronous CSV import so an administrator can watch it advance and correct
 * rejected rows. A well-formed id matching no job and a segment that is not a valid UUID both fold
 * into the same friendly 404 view. The template meta-refreshes only while the job is in flight
 * ({@code PENDING}/{@code PROCESSING}) and stops once terminal.
 */
@Controller
public class ImportJobStatusController {

    private static final String STATUS_VIEW = "import-job-status";
    private static final String NOT_FOUND_VIEW = "import-job-not-found";

    private final GetImportJobStatusUseCase getImportJobStatus;

    public ImportJobStatusController(GetImportJobStatusUseCase getImportJobStatus) {
        this.getImportJobStatus = getImportJobStatus;
    }

    @GetMapping("/products/import/{jobId}")
    public String status(@PathVariable("jobId") String rawJobId, Model model, HttpServletResponse response) {
        return parse(rawJobId)
                .flatMap(getImportJobStatus::statusOf)
                .fold(
                        failure -> renderNotFound(rawJobId, model, response),
                        report -> renderStatus(report, model));
    }

    private Either<Failure, ImportJobId> parse(String rawJobId) {
        return Try.of(() -> new ImportJobId(UUID.fromString(rawJobId)))
                .toEither(() -> (Failure) new Failure.ImportJobNotFound(rawJobId));
    }

    private String renderStatus(ImportJobStatusReport report, Model model) {
        model.addAttribute("job", ImportJobStatusView.from(report));
        return STATUS_VIEW;
    }

    private String renderNotFound(String rawJobId, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("jobId", rawJobId);
        return NOT_FOUND_VIEW;
    }
}
