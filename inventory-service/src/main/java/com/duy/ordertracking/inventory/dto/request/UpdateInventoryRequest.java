package com.duy.ordertracking.inventory.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateInventoryRequest {

    @Min(0)
    private int availableQuantity;
}
