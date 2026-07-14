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

## Fake payment gateway prefixes

Checkout (US-22/US-23) charges through a deterministic fake gateway so payment outcomes are
reproducible in tests and demos, in the spirit of Stripe's test cards. The `FakePaymentGatewayAdapter`
(`infrastructure.payment`) decides the outcome purely from a **case-insensitive prefix** of the
payment token value. The domain `PaymentToken` VO stays a plain format-validated string and has no
knowledge of these prefixes — the mapping lives only in the adapter.

| Token prefix (case-insensitive) | Outcome | Meaning |
|---|---|---|
| `approved-` | success (`PaymentConfirmation`) | charge accepted |
| `insufficient-funds-` | `PaymentRejected` | business decline by the issuing bank |
| `gateway-error-` | `PaymentRejected` | simulated infrastructure outage of the gateway (textually distinct from a bank decline) |
| anything else | `PaymentRejected` | **default rejection** — an unrecognized token never silently succeeds |

Example tokens: `approved-visa-1`, `insufficient-funds-1`, `gateway-error-1`.

The charge is fully deterministic: the same `(token, amount)` pair always yields the same result,
including a stable `confirmationReference` derived from the token and amount (not a random value),
so checkout tests can assert the reference exactly.
