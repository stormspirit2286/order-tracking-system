package com.duy.ordertracking.inventory.service;

import com.duy.ordertracking.inventory.dto.request.CreateInventoryRequest;
import com.duy.ordertracking.inventory.dto.request.StockReservationItem;
import com.duy.ordertracking.inventory.dto.request.UpdateInventoryRequest;
import com.duy.ordertracking.inventory.dto.response.InventoryResponse;

import java.util.List;

public interface InventoryService {

    InventoryResponse create(CreateInventoryRequest request);

    InventoryResponse getByProductId(String productId);

    InventoryResponse update(String productId, UpdateInventoryRequest request);

    void reserveStock(String orderId, String userId, List<StockReservationItem> items);

    void releaseStock(String orderId, List<StockReservationItem> items);
}
