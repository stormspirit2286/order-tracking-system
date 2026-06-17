package com.duy.ordertracking.order.service.impl;

import com.duy.ordertracking.order.dto.request.CreateOrderItemRequest;
import com.duy.ordertracking.order.dto.request.CreateOrderRequest;
import com.duy.ordertracking.order.dto.response.OrderResponse;
import com.duy.ordertracking.order.entity.Order;
import com.duy.ordertracking.order.entity.OrderItem;
import com.duy.ordertracking.order.entity.OrderStatus;
import com.duy.ordertracking.order.event.OrderCancelledEvent;
import com.duy.ordertracking.order.event.OrderCreatedEvent;
import com.duy.ordertracking.order.exception.AppException;
import com.duy.ordertracking.order.external.client.ProductClient;
import com.duy.ordertracking.order.external.dto.ProductResponse;
import com.duy.ordertracking.order.kafka.OrderEventPublisher;
import com.duy.ordertracking.order.mapper.OrderMapper;
import com.duy.ordertracking.order.repository.OrderRepository;
import com.duy.ordertracking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final OrderEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String userId) {
        List<String> productIds = request.getItems().stream()
                .map(CreateOrderItemRequest::getProductId)
                .toList();

        Map<String, ProductResponse> productMap = productClient.getProductsByIds(productIds)
                .getData().stream()
                .collect(Collectors.toMap(ProductResponse::getId, p -> p));

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .note(request.getNote())
                .build();

        List<OrderItem> items = buildItems(request.getItems(), productMap, order);
        order.setItems(items);
        order.setTotalAmount(
                items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        Order saved = orderRepository.save(order);
        log.info("Order created: {} for user: {}", saved.getId(), userId);

        eventPublisher.publishOrderCreated(OrderCreatedEvent.builder()
                .orderId(saved.getId())
                .userId(userId)
                .items(saved.getItems().stream()
                        .map(item -> OrderCreatedEvent.OrderItemEvent.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .totalAmount(saved.getTotalAmount())
                .createdAt(saved.getCreatedAt())
                .build());

        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponse getById(String orderId, String userId) {
        Order order = findOrderById(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return orderMapper.toResponse(order);
    }

    @Override
    public List<OrderResponse> getMyOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderId, String userId) {
        Order order = findOrderById(orderId);

        if (!order.getUserId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot cancel order with status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        log.info("Order cancelled: {}", orderId);

        List<OrderCancelledEvent.OrderItemEvent> cancelledItems = saved.getItems().stream()
                .map(item -> OrderCancelledEvent.OrderItemEvent.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        eventPublisher.publishOrderCancelled(OrderCancelledEvent.builder()
                .orderId(saved.getId())
                .userId(userId)
                .reason("Cancelled by user")
                .items(cancelledItems)
                .cancelledAt(saved.getUpdatedAt())
                .build());

        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void updateStatus(String orderId, OrderStatus status) {
        Order order = findOrderById(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} already processed with status: {}, skipping update to {}",
                    orderId, order.getStatus(), status);
            return;
        }

        order.setStatus(status);
        orderRepository.save(order);
        log.info("Order {} status updated to {}", orderId, status);
    }

    private Order findOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
    }

    private List<OrderItem> buildItems(List<CreateOrderItemRequest> requests,
                                       Map<String, ProductResponse> productMap,
                                       Order order) {
        return requests.stream().map(req -> {
            ProductResponse product = productMap.get(req.getProductId());
            if (product == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Product not found: " + req.getProductId());
            }
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
            return OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(req.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build();
        }).toList();
    }
}
