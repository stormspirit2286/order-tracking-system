package com.duy.ordertracking.order.external.client;

import com.duy.ordertracking.order.dto.response.ApiResponse;
import com.duy.ordertracking.order.external.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/products/batch")
    ApiResponse<List<ProductResponse>> getProductsByIds(@RequestParam List<String> ids);
}
