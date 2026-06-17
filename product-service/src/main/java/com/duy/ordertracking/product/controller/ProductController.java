package com.duy.ordertracking.product.controller;

import com.duy.ordertracking.product.dto.request.CreateProductRequest;
import com.duy.ordertracking.product.dto.request.UpdateProductRequest;
import com.duy.ordertracking.product.dto.response.ApiResponse;
import com.duy.ordertracking.product.dto.response.ProductResponse;
import com.duy.ordertracking.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        Page<ProductResponse> products = productService.getProducts(page, size, category);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable String id) {
        ProductResponse product = productService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    // Endpoint dành cho order-service gọi sync để lấy nhiều sản phẩm cùng lúc
    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getByIds(@RequestParam List<String> ids) {
        List<ProductResponse> products = productService.getByIds(ids);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created", product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse product = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated", product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted", null));
    }
}
