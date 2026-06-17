package com.duy.ordertracking.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInventoryRequest {

    @NotBlank
    private String productId;

    @Min(0)
    private int availableQuantity;
}
