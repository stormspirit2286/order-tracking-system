package com.duy.ordertracking.product.repository;

import com.duy.ordertracking.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryAndActiveTrue(Product.Category category, Pageable pageable);

    List<Product> findAllByIdInAndActiveTrue(List<String> ids);
}
