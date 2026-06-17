package com.duy.ordertracking.inventory.kafka;

import com.duy.ordertracking.inventory.config.KafkaTopicConfig;
import com.duy.ordertracking.inventory.event.InventoryFailedEvent;
import com.duy.ordertracking.inventory.event.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryReserved(InventoryReservedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.INVENTORY_RESERVED, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryReservedEvent for order {}: {}", event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published InventoryReservedEvent for order {}, partition {}, offset {}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishInventoryFailed(InventoryFailedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.INVENTORY_FAILED, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryFailedEvent for order {}: {}", event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published InventoryFailedEvent for order {}, partition {}, offset {}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
