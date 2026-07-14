package com.ecommerce.marketplace.infrastructure.messaging;

import com.ecommerce.marketplace.application.event.ImportRequested;
import com.ecommerce.marketplace.application.event.OrderPlaced;
import com.ecommerce.marketplace.application.event.ProductImported;
import io.vavr.control.Option;

import java.util.Map;

/**
 * Owns the {@code eventType → Kafka topic} routing that used to live on {@code DomainEvent.topic()}
 * (US-15 decision, resolving the US-04 audit's deferred recommendation): the destination topic is
 * an infrastructure transport detail, so it is derived here in the adapter rather than declared by
 * the application-layer event. Adding a new event means adding one entry here, not touching the
 * application contract.
 *
 * <p>Covers exactly the three topics named by the US-15 CA. An event whose type has no mapping
 * yields {@link Option#none()}, which the outbox adapter turns into a
 * {@link com.ecommerce.marketplace.domain.failure.Failure.EventPublishFailed} rather than guessing
 * a topic or silently dropping the event.</p>
 */
final class OutboxTopics {

    private static final Map<String, String> TOPIC_BY_EVENT_TYPE = Map.of(
            ProductImported.EVENT_TYPE, "product-imported",
            OrderPlaced.EVENT_TYPE, "order-placed",
            ImportRequested.EVENT_TYPE, "import-requested");

    private OutboxTopics() {
    }

    static Option<String> resolve(String eventType) {
        return Option.of(TOPIC_BY_EVENT_TYPE.get(eventType));
    }
}
