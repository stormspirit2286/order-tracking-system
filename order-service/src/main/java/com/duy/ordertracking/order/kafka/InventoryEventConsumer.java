package com.duy.ordertracking.order.kafka;

import com.duy.ordertracking.order.config.KafkaTopicConfig;
import com.duy.ordertracking.order.entity.OrderStatus;
import com.duy.ordertracking.order.event.InventoryFailedEvent;
import com.duy.ordertracking.order.event.InventoryReservedEvent;
import com.duy.ordertracking.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_RESERVED, groupId = "order-service-group")
    public void handleInventoryReserved(@Payload InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent for order {}", event.getOrderId());
        orderService.updateStatus(event.getOrderId(), OrderStatus.CONFIRMED);
    }

    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_FAILED, groupId = "order-service-group")
    public void handleInventoryFailed(@Payload InventoryFailedEvent event) {
        log.warn("Received InventoryFailedEvent for order {}, reason: {}", event.getOrderId(), event.getReason());
        orderService.updateStatus(event.getOrderId(), OrderStatus.FAILED);
    }
}
