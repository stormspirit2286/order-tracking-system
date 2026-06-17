package com.duy.ordertracking.order.mapper;

import com.duy.ordertracking.order.dto.response.OrderItemResponse;
import com.duy.ordertracking.order.dto.response.OrderResponse;
import com.duy.ordertracking.order.entity.Order;
import com.duy.ordertracking.order.entity.OrderItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);
}
