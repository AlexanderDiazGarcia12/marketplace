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

## Purchase idempotency store

Checkout (US-22/US-23) accepts a client-supplied `Idempotency-Key` header so a network retry or a
double-click never charges or ships an order twice. `PostgreSQLIdempotencyStoreAdapter`
(`infrastructure.persistence`) is the sole place `idempotency_keys` rows are read or written; it is a
pure key-lifecycle registry that never runs business logic itself — the caller decides what to do
with the `Either` it returns.

`begin(key, requestHash)` always tries an unconditional INSERT first and lets the `key`'s primary key
constraint (`idempotency_keys_pkey`) arbitrate a same-key race: exactly one concurrent request wins
the INSERT, and every loser (including a later, non-concurrent retry) falls through to the same
decision tree against the existing row:

| Existing row | Incoming request hash | Outcome | Suggested HTTP status |
|---|---|---|---|
| *(none)* | — | fresh row inserted, `IN_PROGRESS` | 2xx, request proceeds |
| any status | different from stored `request_hash` | `Failure.IdempotencyKeyMismatch` | 422 |
| `IN_PROGRESS` | matches stored `request_hash` | `Failure.DuplicateOrderRequest` | 409 |
| `COMPLETED` | matches stored `request_hash` | stored `responseSnapshot` returned, nothing re-executed | 2xx, replayed response |

The hash-mismatch check always takes priority over the status check: reusing the same key for a
genuinely different request body is a client usage error independent of timing, so it is rejected
before the adapter even considers whether the original request is still in flight or already done.

`complete(key, responseSnapshot)` performs a guarded `IN_PROGRESS → COMPLETED` transition (`UPDATE …
WHERE status = 'IN_PROGRESS'`), so a duplicate or out-of-order completion can never overwrite an
already-stored snapshot — the same hardening applied to the CSV import job's terminal transitions.
