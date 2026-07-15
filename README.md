# Marketplace

A server-rendered e-commerce marketplace backend — product catalog, bulk CSV import, search, and a
transactional checkout with a simulated payment gateway. Built around one central constraint: model
every business outcome, including failures, as explicit, compiler-checked data instead of exceptions
and side effects.

This document explains *why* the project is put together the way it is, what alternatives were
considered and rejected, and how to run it locally.

## Contents

- [Approach and architecture](#approach-and-architecture)
- [Key decisions and rationale](#key-decisions-and-rationale)
- [Alternatives considered](#alternatives-considered)
- [Tech stack](#tech-stack)
- [Local setup](#local-setup)
- [Sample data provenance](#sample-data-provenance)
- [Fake payment gateway prefixes](#fake-payment-gateway-prefixes)
- [Purchase idempotency store](#purchase-idempotency-store)

## Approach and architecture

The codebase follows **Hexagonal Architecture (Ports & Adapters)** combined with **Domain-Driven
Design**: the rules of the business — what a product is, what makes a checkout valid, what counts
as a failure — shouldn't know that Spring, Hibernate, or a web browser exist. Frameworks are
*details* plugged in from the outside, not the foundation everything else is built on.

Three layers, under `com.ecommerce.marketplace`:

- **`domain`** — immutable Java records modeling the business (`Product`, `Order`, `PaymentToken`,
  `SKU`, …) and a sealed `Failure` hierarchy describing every way an operation can fail. Zero
  imports from Spring, Jakarta, Hibernate, or Jackson.
- **`application`** — use cases ("input ports") and the output ports they depend on
  (`ProductRepositoryPort`, `PaymentGatewayPort`, `IdempotencyStorePort`, …), expressed as
  interfaces. Business orchestration, still with no framework dependency.
- **`infrastructure`** — everything that talks to the outside world: `@Controller` classes (Spring
  MVC + Thymeleaf), JPA repositories and entities, the Kafka producer/consumer, the Redis cache
  decorator, the fake payment adapter. The only layer allowed to import Spring/JPA.

This is enforced automatically: `HexagonalArchitectureTest` (ArchUnit) fails the build if a domain
or application class imports anything from `infrastructure` or a framework package. That's what
makes it safe to unit-test business logic without a Spring context, and what stops "just one quick
`@Autowired`" from eroding the boundary over time.

The web layer is plain **server-side rendering** with Thymeleaf and Tailwind — no separate frontend
build, no JSON API to version. For this project's scope (an admin-style catalog and a checkout
flow), a full SPA would add a build pipeline and an API contract without buying anything the
requirements actually needed.

## Key decisions and rationale

### Why functional programming, and why Vavr specifically

The core bet: **business failures are not exceptional** — `InsufficientStock`, `PaymentRejected`, a
duplicate checkout request — these are everyday outcomes of running a store, not bugs. Java
exceptions are a poor fit for them: checked exceptions pollute signatures and don't compose with
streams; unchecked exceptions are invisible in a signature; stack-trace capture on every throw is
wasted work for something routine.

[Vavr](https://vavr.io) makes failure a visible part of every signature instead:

- **`Either<Failure, T>`** — the return type of nearly every use case. A method that can fail says
  so in its signature. Combined with the sealed `Failure` interface, the compiler can verify every
  failure case is handled somewhere.
- **`Option<T>`** replaces `null` for legitimately-absent values.
- **`Validation<Seq<E>, T>`** — used for form input, it accumulates *every* validation error in one
  pass instead of failing on the first, so a user fixes everything in one round trip.

The one deliberate exception to "no `try/catch` for business flow" is at persistence-boundary
translation points — e.g. turning a database constraint violation into a domain `Failure` inside a
repository adapter. That's translating one layer's vocabulary into another's, not business branching,
and it stays confined to `infrastructure`.

The sealed `Failure` interface itself matters: because it's a closed set of record implementations,
a `switch` over it can be exhaustive — the compiler forces every new failure variant to be handled
everywhere it's switched on, instead of silently falling through a `default` and shipping a bug.

### Why PostgreSQL

The domain is relational at its core — products, orders and order items have real referential
relationships, and correctness depends on strong ACID guarantees (a stock decrement and an order
creation must succeed or fail together). That rules out reaching for a document or
eventually-consistent store.

Postgres specifically, because the project leans on a few of its features directly:

- **`GIN`/`pg_trgm` indexes** for fast free-text product search combined with category filtering,
  without standing up a separate search engine for what's a well-indexed-query problem at this
  scale.
- **Native `UPDATE … WHERE version = ? RETURNING *`** for optimistic-locking stock decrements — a
  statement that can affect zero rows without throwing (see below).
- **`ON CONFLICT (sku) DO UPDATE`** for the idempotent CSV import upsert, and **`JSONB`** for
  idempotency response snapshots.
- **Flyway** manages schema evolution as versioned SQL migrations — the schema history is explicit
  and reviewable rather than inferred from JPA annotations.

### Why Kafka (and the Transactional Outbox pattern)

CSV imports must never block the web request thread, and the event announcing "a product changed"
must never be lost — not even on a crash. Writing to the database and then separately publishing to
Kafka leaves a window where a crash loses the message despite the commit having succeeded.

The **Transactional Outbox** pattern closes that gap: the event is written to an `outbox_events`
table in the *same* transaction as the business change, atomic by construction. A separate
scheduler (`OutboxRelayScheduler`) polls that table and publishes to Kafka independently.

Kafka carries that relay rather than a simpler in-process queue because it decouples the producer
(the relay) from the consumer (`ProductImportConsumer`) entirely — a slow or restarting consumer
never backs up the web tier, and a durable log lets a consumer resume exactly where it left off. The
tradeoff is at-least-once delivery, which requires the consumer to be idempotent: a SKU-keyed upsert
(`ON CONFLICT (sku) DO UPDATE`, incrementing a version column) makes redelivery a no-op.

### Why Redis, and why it's optional

Product pages are read far more often than products change — the textbook read-through cache case.
`RedisCachingProductRepositoryAdapter` wraps the real repository and implements the same output
port; from the application layer's point of view, caching doesn't exist.

Redis, not an in-process cache like Caffeine, because an in-process cache only stays consistent on
one instance — behind a load balancer with multiple replicas, a local cache would let instances
disagree on stock or price. Redis, being external and shared, gives every instance the same view.

Caching sits behind a Spring **`cache`** profile and is entirely optional — the app runs correctly
with zero cache, just a lower hit rate. Caching is a *performance* optimization here, not a
correctness dependency, and it also means a reviewer isn't forced to run an extra container just to
get the app working.

### Concurrency: optimistic locking done the way Postgres wants it

Both product updates and checkout stock decrements handle concurrent writers with optimistic
locking. The first attempt used JPA's standard `@Version` approach: load, mutate, let Hibernate
throw `OptimisticLockException` on a conflicting flush, catch it, retry.

That turned out to be structurally broken under Spring's transaction management: per the JPA spec,
an `OptimisticLockException` at flush time marks the **entire physical transaction**
rollback-only — not just the failed operation. A caught-and-retried `merge()` inside the same
transaction is doomed regardless of the retry logic, since the eventual commit throws
`UnexpectedRollbackException` no matter what. This surfaced only by testing against real Postgres,
not mocks.

The fix: a native, versioned `UPDATE products SET stock = stock - ?, version = version + 1 WHERE
sku = ? AND version = ? RETURNING *`. Returning **zero rows** because the version didn't match is a
completely ordinary outcome — not an exception — so the calling code retries in a plain loop without
poisoning the transaction.

### Idempotent checkout

Checkout accepts a client-supplied idempotency key so a network retry or a double-click never
charges or ships twice. `PostgreSQLIdempotencyStoreAdapter` treats `idempotency_keys` as a small
state machine (`IN_PROGRESS` → `COMPLETED`) arbitrated by the key's primary key, so a race between
two requests resolves itself at the database level. Full behavior in
[Purchase idempotency store](#purchase-idempotency-store) below.

A related detail: checkout uses **two separate transactions**. The main one runs the purchase and
rolls back completely on any failure. But a declined payment still needs a durable record of that
rejection plus a completed idempotency key — a write that must survive even though the main
transaction it's related to just rolled back. That's only possible with a second, genuinely
independent (`REQUIRES_NEW`) transaction; a nested one sharing the main transaction would roll back
right along with it.

## Alternatives considered

| Decision point | Chosen | Considered and rejected because |
|---|---|---|
| Entity-to-domain mapping | Hand-written mappers | **MapStruct** needs accessible getters, but this project's JPA entities intentionally keep package-private getters so they never leak past the persistence adapter. Hand-written mappers keep that boundary intact at a small boilerplate cost. |
| Stock concurrency control | Native versioned `UPDATE … RETURNING *` | Standard JPA `@Version` + caught `OptimisticLockException` retry was tried first and found structurally unsound — see [above](#concurrency-optimistic-locking-done-the-way-postgres-wants-it). |
| Async import transport | Kafka + Transactional Outbox | A simpler in-process `@Async`/`TaskExecutor` doesn't survive a crash between the database write and the async dispatch — it defeats the point of decoupling import from the request thread. |
| Product cache | Redis (optional, decorator) | An in-process cache (Caffeine) was rejected for correctness under horizontal scaling — see [above](#why-redis-and-why-its-optional). Making it hard-required was also rejected, to avoid a dependency for a pure performance optimization. |
| API documentation | None (Swagger/OpenAPI not added) | Every endpoint here is a server-rendered `@Controller` returning Thymeleaf view names over form-encoded input, not a JSON API. Swagger's value proposition — typed schemas, "try it out" — doesn't apply to HTML forms, so adding it would produce a technically-present but semantically thin spec. |
| Test data isolation | Real Postgres/Kafka via Docker Compose | Mocking the database for integration tests was rejected — it's exactly the kind of mock/reality gap that hid the optimistic-locking bug above. |

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4.1.0 (Web MVC, Data JPA, Validation, Actuator) |
| Functional error handling | Vavr 1.0.1 (`Either`, `Option`, `Validation`) |
| Database | PostgreSQL 17 |
| Schema migrations | Flyway |
| Messaging | Apache Kafka 3.9 (via a Transactional Outbox relay) |
| Cache (optional) | Redis 7, behind the `cache` Spring profile |
| Views | Thymeleaf (SSR) + Tailwind CSS |
| Architecture enforcement | ArchUnit (`HexagonalArchitectureTest`) |
| Coverage | JaCoCo (merged unit + integration report) |
| CI | GitHub Actions (`.github/workflows/ci.yml`) |

## Local setup

### Prerequisites

- **Java 25** (matching `<java.version>` in `pom.xml`)
- **Docker** with Compose v2 (`docker compose`) — runs PostgreSQL and Kafka locally via
  `compose.yaml`

Nothing else needs to be installed globally: use the bundled wrapper, `./mvnw`, never a system-wide
Maven.

### Run the app

```bash
./mvnw spring-boot:run
```

The `spring-boot-docker-compose` integration detects `compose.yaml` and starts `postgres`/`kafka`
automatically if they aren't already running. The app comes up on **http://localhost:8080**.

To manage the containers yourself instead (recommended when also running tests, see below):

```bash
docker compose up -d --wait   # starts postgres + kafka and waits for their healthchecks
./mvnw spring-boot:run
```

To also start the optional Redis cache: `docker compose --profile cache up -d --wait`.

Tear down when done: `docker compose down` (add `-v` only to also wipe data volumes).

### Run the tests

The suite includes real integration tests against Postgres (no mocked repositories), so Compose
needs to be up first:

```bash
docker compose up -d --wait
./mvnw verify
```

`./mvnw verify` runs unit tests (Surefire), integration tests (Failsafe, classes ending in `*IT`),
and produces a merged coverage report at `target/site/jacoco/index.html`.

### Continuous Integration

Every push and pull request runs the same `docker compose up --wait` → `./mvnw verify` sequence in
GitHub Actions, publishing a coverage summary and the JaCoCo/Surefire/Failsafe reports as build
artifacts.

## Sample data provenance

The seed product catalog ships with this repository as
`src/main/resources/static/LoanPro Code Challenge E-Commerce - LoanPro Code Challenge E-Commerce.csv`
(columns `name,sku,description,category,price,stock,weight_kg`).

- **Downloaded on:** Thursday, July 9, 2026.

Every ingestion through the asynchronous CSV import pipeline emits a structured, single-line audit
entry via SLF4J so a reviewer can confirm which file was processed, when, and with what outcome.
Filter the application logs for `CSV import audit` — one line marks `event=processing_started` (job
id, original filename, file reference) and one marks `event=processing_finished`
(`result=COMPLETED`/`FAILED` with the `total`/`accepted`/`rejected` row counters).

## Fake payment gateway prefixes

Checkout charges through a deterministic fake gateway so payment outcomes are reproducible in tests
and demos, in the spirit of Stripe's test cards. `FakePaymentGatewayAdapter` decides the outcome
purely from a **case-insensitive prefix** of the payment token value. The domain `PaymentToken` VO
stays a plain format-validated string with no knowledge of these prefixes — the mapping lives only
in the adapter.

| Token prefix (case-insensitive) | Outcome | Meaning |
|---|---|---|
| `approved-` | success (`PaymentConfirmation`) | charge accepted |
| `insufficient-funds-` | `PaymentRejected` | business decline by the issuing bank |
| `gateway-error-` | `PaymentGatewayUnavailable` | simulated infrastructure outage (textually distinct from a bank decline) |
| anything else | `PaymentRejected` | **default rejection** — an unrecognized token never silently succeeds |

Example tokens: `approved-visa-1`, `insufficient-funds-1`, `gateway-error-1`.

The charge is fully deterministic: the same `(token, amount)` pair always yields the same result,
including a stable `confirmationReference` derived from the token and amount, so tests can assert it
exactly.

## Purchase idempotency store

Checkout accepts a client-supplied `Idempotency-Key` so a network retry or a double-click never
charges or ships an order twice. `PostgreSQLIdempotencyStoreAdapter` is the sole place
`idempotency_keys` rows are read or written — a pure key-lifecycle registry that never runs business
logic itself; the caller decides what to do with the `Either` it returns.

`begin(key, requestHash)` always tries an unconditional INSERT first and lets the key's primary-key
constraint arbitrate a same-key race: exactly one concurrent request wins the insert, and every loser
(including a later retry) falls through to the same decision tree against the existing row:

| Existing row | Incoming request hash | Outcome | Suggested HTTP status |
|---|---|---|---|
| *(none)* | — | fresh row inserted, `IN_PROGRESS` | 2xx, request proceeds |
| any status | different from stored `request_hash` | `Failure.IdempotencyKeyMismatch` | 422 |
| `IN_PROGRESS` | matches stored `request_hash` | `Failure.DuplicateOrderRequest` | 409 |
| `COMPLETED` | matches stored `request_hash` | stored `responseSnapshot` returned, nothing re-executed | 2xx, replayed response |

The hash-mismatch check always takes priority over the status check: reusing a key for a genuinely
different request is a client usage error independent of timing, rejected before the adapter even
considers whether the original request is still in flight or already done.

`complete(key, responseSnapshot)` performs a guarded `IN_PROGRESS → COMPLETED` transition
(`UPDATE … WHERE status = 'IN_PROGRESS'`), so a duplicate or out-of-order completion can never
overwrite an already-stored snapshot.
