# marketplace
e-comerce application

## Sample data provenance

The seed product catalog ships with this repository as
`src/main/resources/static/LoanPro Code Challenge E-Commerce - LoanPro Code Challenge E-Commerce.csv`
(columns `name,sku,description,category,price,stock,weight_kg`).

- **Downloaded on:** Thursday, July 9, 2026.

Every time this file is ingested through the asynchronous CSV import pipeline, the worker
(`ProductImportConsumer`) emits a structured, single-line audit entry via SLF4J so a reviewer
can confirm which file was processed, when, and with what outcome. Filter the application logs
for `CSV import audit` — one line marks `event=processing_started` (job id, original filename,
file reference) and one marks `event=processing_finished` (`result=COMPLETED`/`FAILED` with the
`total`/`accepted`/`rejected` row counters).
