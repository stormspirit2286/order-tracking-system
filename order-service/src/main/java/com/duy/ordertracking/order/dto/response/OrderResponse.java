package com.duy.ordertracking.order.dto.response;

import com.duy.ordertracking.order.entity.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private String id;
    private String userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String note;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
