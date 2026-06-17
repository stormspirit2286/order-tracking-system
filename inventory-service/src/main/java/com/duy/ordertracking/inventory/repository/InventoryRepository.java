package com.duy.ordertracking.inventory.repository;

import com.duy.ordertracking.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

    Optional<Inventory> findByProductId(String productId);

    List<Inventory> findAllByProductIdIn(List<String> productIds);

    boolean existsByProductId(String productId);
}
