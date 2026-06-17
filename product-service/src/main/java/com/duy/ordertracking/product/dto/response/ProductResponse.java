package com.duy.ordertracking.product.dto.response;

import com.duy.ordertracking.product.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Product.Category category;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
