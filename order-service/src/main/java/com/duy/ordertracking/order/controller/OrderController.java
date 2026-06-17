package com.duy.ordertracking.order.controller;

import com.duy.ordertracking.order.dto.request.CreateOrderRequest;
import com.duy.ordertracking.order.dto.response.ApiResponse;
import com.duy.ordertracking.order.dto.response.OrderResponse;
import com.duy.ordertracking.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") String userId) {
        OrderResponse order = orderService.createOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created", order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        OrderResponse order = orderService.getById(id, userId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @RequestHeader("X-User-Id") String userId) {
        List<OrderResponse> orders = orderService.getMyOrders(userId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        OrderResponse order = orderService.cancelOrder(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
    }
}
