package com.duy.ordertracking.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private String orderId;
    private String userId;
    private String reason;
    private List<OrderCreatedEvent.OrderItemEvent> items;
    private LocalDateTime cancelledAt;
}
