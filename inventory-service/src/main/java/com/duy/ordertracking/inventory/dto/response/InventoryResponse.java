package com.duy.ordertracking.inventory.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryResponse {

    private String id;
    private String productId;
    private int availableQuantity;
    private int reservedQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
