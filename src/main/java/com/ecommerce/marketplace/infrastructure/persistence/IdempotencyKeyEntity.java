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
 * JPA mapping of the {@code idempotency_keys} table, confined to {@code infrastructure.persistence}:
 * {@link IdempotencyKeyMapper} builds the application-layer
 * {@link com.ecommerce.marketplace.application.ports.out.IdempotencyRecord} from it so no
 * {@code @Entity} escapes this package. {@code key} is the client-supplied natural PK, so an
 * unconditional INSERT lets the UNIQUE B-Tree arbitrate concurrent same-key requests; {@code status}
 * and {@code responseSnapshot} are never mutated as an entity (the terminal transition is a native
 * guarded UPDATE), so the class exposes no setters.
 *
 * <p>Implements {@link Persistable} so a fresh instance always routes through {@code persist()} (an
 * INSERT) rather than {@code merge()}: with a client-assigned id, Spring Data would otherwise assume
 * the row already exists and silently overwrite an in-flight or completed row on retry.
 * {@link #isNew()} flips to {@code false} after {@code @PostPersist}/{@code @PostLoad}.</p>
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
