package com.ecommerce.marketplace.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * JPA mapping of the {@code idempotency_keys} table (V7). Lives strictly inside
 * {@code infrastructure.persistence}: it never crosses into {@code domain}/{@code application} or
 * the views — {@link IdempotencyKeyMapper} builds the application-layer
 * {@link com.ecommerce.marketplace.application.ports.out.IdempotencyRecord} from it, so no
 * {@code @Entity} escapes this package.
 *
 * <p>Design notes tied to the physical schema:</p>
 * <ul>
 *   <li>{@code key} is the natural, client-supplied {@code VARCHAR(128)} PK — there is no synthetic
 *       id and no {@code @GeneratedValue}. The key itself is the point of contention; making it the
 *       PK lets the UNIQUE B-Tree arbitrate concurrent same-key {@code begin()} requests (the loser's
 *       INSERT fails on PK violation), exactly as {@code uq_products_sku} does for {@code DuplicateSku}
 *       in US-09.</li>
 *   <li>{@code status} reuses the native {@code idempotency_status} enum from V1 via
 *       {@code @JdbcTypeCode(NAMED_ENUM)} (binds by {@link Enum#name()}), matching the
 *       {@code products.category}/{@code import_jobs.status}/{@code outbox_events.status}
 *       convention (no mirror enum).</li>
 *   <li>{@code responseSnapshot} maps the {@code response_snapshot JSONB} column via
 *       {@code @JdbcTypeCode(SqlTypes.JSON)} over a {@code String}, exactly like
 *       {@code OutboxEventEntity.payload}. The snapshot arrives already serialized from the future
 *       checkout caller — this store never parses or generates the JSON. It is nullable: a fresh
 *       {@code IN_PROGRESS} row has no response yet.</li>
 *   <li>{@code createdAt} is DB-defaulted ({@code now()}), so it is {@code insertable = false} and
 *       read back after insert.</li>
 * </ul>
 *
 * <p>Accessors are package-private (Lombok {@code @Getter(PACKAGE)}) with no generic setters,
 * matching {@code JPAProductEntity}/{@code ImportJobEntity}/{@code OutboxEventEntity} — the entity
 * cannot leak out of this package. The {@code IN_PROGRESS → COMPLETED} transition is a native
 * guarded UPDATE ({@link SpringDataIdempotencyKeyJpaRepository#completeIfInProgress}), never a
 * dirty-checked entity mutation, so this class has no setter for {@code status}/{@code
 * responseSnapshot}.</p>
 *
 * <p>Implements {@link Persistable} because {@code key} is a client-assigned natural PK with no
 * {@code @GeneratedValue}: without it, {@code SimpleJpaRepository.save()} sees a non-null id on a
 * freshly built instance and assumes it is already persistent, routing the write through
 * {@code EntityManager.merge()} (a SELECT to load the existing row, then an UPDATE) instead of
 * {@code persist()}. That silently overwrites an in-flight or completed row on retry and never
 * throws — defeating the adapter's whole "unconditional INSERT, let the PK arbitrate the race"
 * mechanism. {@link #isNew()} flips to {@code false} after {@code @PostPersist}/{@code @PostLoad},
 * so only a brand-new, never-flushed instance routes to {@code persist()}.</p>
 */
@Entity
@Table(name = "idempotency_keys")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyEntity implements Persistable<String> {

    @Id
    @Column(name = "key", updatable = false, nullable = false, length = 128)
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", columnDefinition = "jsonb")
    private String responseSnapshot;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "idempotency_status")
    private IdempotencyStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient
    private boolean isNew = true;

    IdempotencyKeyEntity(String key, String requestHash) {
        this.key = key;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    @Override
    public String getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
