package com.duy.ordertracking.order.service;

import com.duy.ordertracking.order.dto.request.CreateOrderRequest;
import com.duy.ordertracking.order.dto.response.OrderResponse;
import com.duy.ordertracking.order.entity.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, String userId);

    OrderResponse getById(String orderId, String userId);

    List<OrderResponse> getMyOrders(String userId);

    OrderResponse cancelOrder(String orderId, String userId);

    void updateStatus(String orderId, OrderStatus status);
}
