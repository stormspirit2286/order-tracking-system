package com.duy.ordertracking.product.service.impl;

import com.duy.ordertracking.product.dto.request.CreateProductRequest;
import com.duy.ordertracking.product.dto.request.UpdateProductRequest;
import com.duy.ordertracking.product.dto.response.ProductResponse;
import com.duy.ordertracking.product.entity.Product;
import com.duy.ordertracking.product.exception.AppException;
import com.duy.ordertracking.product.mapper.ProductMapper;
import com.duy.ordertracking.product.repository.ProductRepository;
import com.duy.ordertracking.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public Page<ProductResponse> getProducts(int page, int size, String category) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (category != null && !category.isBlank()) {
            Product.Category cat = parseCategory(category);
            return productRepository.findByCategoryAndActiveTrue(cat, pageable)
                    .map(productMapper::toResponse);
        }

        return productRepository.findByActiveTrue(pageable)
                .map(productMapper::toResponse);
    }

    @Override
    public ProductResponse getById(String id) {
        return productMapper.toResponse(findActiveProductById(id));
    }

    @Override
    public List<ProductResponse> getByIds(List<String> ids) {
        return productRepository.findAllByIdInAndActiveTrue(ids).stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = productRepository.save(productMapper.toEntity(request));
        log.info("Product created: {}", product.getId());
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse update(String id, UpdateProductRequest request) {
        Product product = findActiveProductById(id);
        productMapper.updateEntity(request, product);
        product = productRepository.save(product);
        log.info("Product updated: {}", id);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Product product = findActiveProductById(id);
        product.setActive(false);
        productRepository.save(product);
        log.info("Product deleted: {}", id);
    }

    private Product findActiveProductById(String id) {
        return productRepository.findById(id)
                .filter(Product::getActive)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    private Product.Category parseCategory(String category) {
        try {
            return Product.Category.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid category: " + category);
        }
    }
}
