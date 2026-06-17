package com.duy.ordertracking.product.dto.request;

import com.duy.ordertracking.product.entity.Product;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {

    private String name;

    private String description;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    private String imageUrl;

    private Product.Category category;

    private Boolean active;
}
