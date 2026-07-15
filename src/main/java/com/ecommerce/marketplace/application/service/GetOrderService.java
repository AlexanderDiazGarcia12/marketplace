package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.GetOrderUseCase;
import com.ecommerce.marketplace.application.ports.in.query.OrderDetail;
import com.ecommerce.marketplace.application.ports.in.query.OrderLine;
import com.ecommerce.marketplace.application.ports.out.OrderRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.collection.Seq;
import io.vavr.control.Either;

/**
 * Implementation of {@link GetOrderUseCase}. Reuses {@link OrderRepositoryPort#findById} for the
 * order aggregate, then enriches each line with the current product name via
 * {@link ProductRepositoryPort#findBySku} — empty when the product has since been deleted.
 */
public final class GetOrderService implements GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;

    public GetOrderService(OrderRepositoryPort orderRepository, ProductRepositoryPort productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    public Either<Failure, OrderDetail> getById(OrderId orderId) {
        return orderRepository.findById(orderId)
                .map(this::describe)
                .toEither(() -> (Failure) new Failure.OrderNotFound(orderId));
    }

    private OrderDetail describe(Order order) {
        Seq<OrderLine> lines = order.items().map(item -> new OrderLine(
                item.sku(),
                productRepository.findBySku(item.sku()).map(Product::name),
                item.quantity(),
                item.unitPrice(),
                item.subtotal()));
        return new OrderDetail(order.id(), order.status(), order.totalAmount(), lines);
    }
}
