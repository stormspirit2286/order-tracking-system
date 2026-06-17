package com.duy.ordertracking.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent {
    private String orderId;
    private String userId;
    private String reason;
    private LocalDateTime failedAt;
}
