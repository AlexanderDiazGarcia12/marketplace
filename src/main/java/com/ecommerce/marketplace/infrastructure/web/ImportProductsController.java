package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.ImportProductsUseCase;
import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.command.ImportProductsCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV bulk-upload endpoint. {@code GET /products/import} renders an upload form;
 * {@code POST /products/import} validates only the envelope (type/header), stores the file and —
 * inside one transaction — creates the {@code PENDING} job and publishes {@code ImportRequested}
 * through the outbox, then responds {@code 202 Accepted} with the job id. Row-by-row ingestion runs
 * off the web thread, so the request never blocks on the import.
 *
 * <p>The transaction boundary lives here because the application layer is Spring-free. File storage
 * and envelope validation run before the transaction (writing a file is not a database operation);
 * the atomic unit shared by the job insert and the outbox insert wraps the whole
 * {@code requestImport} call, so a {@code PENDING} job is never left without its event.</p>
 */
@Controller
public class ImportProductsController {

    private static final String FORM_VIEW = "product-import";
    private static final String ACCEPTED_VIEW = "product-import-accepted";

    private final ImportProductsUseCase importProducts;
    private final CsvUploadStorage uploadStorage;
    private final TransactionTemplate transactionTemplate;

    public ImportProductsController(
            ImportProductsUseCase importProducts,
            CsvUploadStorage uploadStorage,
            TransactionTemplate transactionTemplate) {
        this.importProducts = importProducts;
        this.uploadStorage = uploadStorage;
        this.transactionTemplate = transactionTemplate;
    }

    @GetMapping("/products/import")
    public String importForm(Model model) {
        model.addAttribute("expectedHeader", CsvUploadValidator.EXPECTED_HEADER);
        return FORM_VIEW;
    }

    @PostMapping("/products/import")
    public String importCsv(@RequestParam("file") MultipartFile file, Model model, HttpServletResponse response) {
        return CsvUploadValidator.validate(file)
                .flatMap(uploadStorage::store)
                .map(fileReference -> new ImportProductsCommand(fileReference, file.getOriginalFilename()))
                .flatMap(this::requestImportAtomically)
                .fold(
                        failure -> renderRejected(failure, model, response),
                        jobId -> renderAccepted(jobId, model, response));
    }

    private Either<Failure, ImportJobId> requestImportAtomically(ImportProductsCommand command) {
        return transactionTemplate.execute(status -> {
            Either<Failure, ImportJobId> result = importProducts.requestImport(command);
            result.peekLeft(failure -> status.setRollbackOnly());
            return result;
        });
    }

    private String renderRejected(Failure failure, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        model.addAttribute("expectedHeader", CsvUploadValidator.EXPECTED_HEADER);
        model.addAttribute("error", messageFor(failure));
        return FORM_VIEW;
    }

    private String renderAccepted(ImportJobId jobId, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.ACCEPTED.value());
        model.addAttribute("jobId", jobId.value().toString());
        return ACCEPTED_VIEW;
    }

    private static String messageFor(Failure failure) {
        return failure instanceof Failure.InvalidCsvUpload rejected
                ? rejected.reason()
                : "The import could not be started. Please try again.";
    }
}
