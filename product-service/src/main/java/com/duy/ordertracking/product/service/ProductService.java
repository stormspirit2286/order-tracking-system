package com.duy.ordertracking.product.service;

import com.duy.ordertracking.product.dto.request.CreateProductRequest;
import com.duy.ordertracking.product.dto.request.UpdateProductRequest;
import com.duy.ordertracking.product.dto.response.ProductResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductService {

    Page<ProductResponse> getProducts(int page, int size, String category);

    ProductResponse getById(String id);

    List<ProductResponse> getByIds(List<String> ids);

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(String id, UpdateProductRequest request);

    void delete(String id);
}
