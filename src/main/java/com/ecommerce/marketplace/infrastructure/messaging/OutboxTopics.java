package com.ecommerce.marketplace.infrastructure.messaging;

import com.ecommerce.marketplace.application.event.ImportRequested;
import com.ecommerce.marketplace.application.event.OrderPlaced;
import com.ecommerce.marketplace.application.event.ProductImported;
import io.vavr.control.Option;

import java.util.Map;

/**
 * Owns the {@code eventType → Kafka topic} routing here in infrastructure rather than on the
 * application-layer event, since the destination topic is a transport detail; adding an event means
 * adding one entry here, not touching the application contract. An unmapped event type yields
 * {@link Option#none()}, which the outbox adapter turns into a
 * {@link com.ecommerce.marketplace.domain.failure.Failure.EventPublishFailed} rather than guessing a
 * topic or silently dropping the event.
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
