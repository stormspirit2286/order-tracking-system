package com.duy.ordertracking.order.kafka;

import com.duy.ordertracking.order.config.KafkaTopicConfig;
import com.duy.ordertracking.order.event.OrderCancelledEvent;
import com.duy.ordertracking.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.ORDER_CREATED, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}: {}", event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published OrderCreatedEvent for order {}, partition {}, offset {}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.ORDER_CANCELLED, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCancelledEvent for order {}: {}", event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published OrderCancelledEvent for order {}, partition {}, offset {}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
