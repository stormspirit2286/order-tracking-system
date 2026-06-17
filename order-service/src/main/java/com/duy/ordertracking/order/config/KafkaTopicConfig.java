package com.duy.ordertracking.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_FAILED = "inventory.failed";

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(ORDER_CANCELLED).partitions(3).replicas(1).build();
    }

    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(INVENTORY_RESERVED).partitions(3).replicas(1).build();
    }

    public NewTopic inventoryFailedTopic() {
        return TopicBuilder.name(INVENTORY_FAILED).partitions(3).replicas(1).build();
    }
}
