package com.duy.ordertracking.inventory.mapper;

import com.duy.ordertracking.inventory.dto.response.InventoryResponse;
import com.duy.ordertracking.inventory.entity.Inventory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    InventoryResponse toResponse(Inventory inventory);
}
