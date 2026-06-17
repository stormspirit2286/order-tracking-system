package com.duy.ordertracking.inventory.kafka;

import com.duy.ordertracking.inventory.config.KafkaTopicConfig;
import com.duy.ordertracking.inventory.dto.request.StockReservationItem;
import com.duy.ordertracking.inventory.event.OrderCancelledEvent;
import com.duy.ordertracking.inventory.event.OrderCreatedEvent;
import com.duy.ordertracking.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = KafkaTopicConfig.ORDER_CREATED, groupId = "inventory-service-group")
    public void handleOrderCreated(@Payload OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order {}", event.getOrderId());
        List<StockReservationItem> items = event.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .toList();
        inventoryService.reserveStock(event.getOrderId(), event.getUserId(), items);
    }

    @KafkaListener(topics = KafkaTopicConfig.ORDER_CANCELLED, groupId = "inventory-service-group")
    public void handleOrderCancelled(@Payload OrderCancelledEvent event) {
        log.warn("Received OrderCancelledEvent for order {}, reason: {}", event.getOrderId(), event.getReason());
        List<StockReservationItem> items = event.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .toList();
        inventoryService.releaseStock(event.getOrderId(), items);
    }
}
