package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.IdempotencyRecord;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;

/**
 * Data Mapper between the {@code idempotency_keys} persistence row and the application-layer
 * {@link IdempotencyRecord}. Hand-written and package-private, matching the {@code ProductMapper}/
 * {@code ImportJobMapper} convention — the {@link IdempotencyKeyEntity} never leaves
 * {@code infrastructure.persistence}, and the persistence-only {@link IdempotencyStatus} is
 * translated here to the application-facing {@link IdempotencyRecord.IdempotencyStatus} so the port
 * carries no Hibernate-bound type.
 */
final class IdempotencyKeyMapper {

    private IdempotencyKeyMapper() {
    }

    static IdempotencyRecord toRecord(IdempotencyKeyEntity entity) {
        return new IdempotencyRecord(
                new IdempotencyKey(entity.getKey()),
                entity.getRequestHash(),
                toApplicationStatus(entity.getStatus()),
                entity.getResponseSnapshot());
    }

    private static IdempotencyRecord.IdempotencyStatus toApplicationStatus(IdempotencyStatus status) {
        return switch (status) {
            case IN_PROGRESS -> IdempotencyRecord.IdempotencyStatus.IN_PROGRESS;
            case COMPLETED -> IdempotencyRecord.IdempotencyStatus.COMPLETED;
        };
    }
}
