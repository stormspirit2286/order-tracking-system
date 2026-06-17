package com.duy.ordertracking.inventory.service.impl;

import com.duy.ordertracking.inventory.dto.request.CreateInventoryRequest;
import com.duy.ordertracking.inventory.dto.request.StockReservationItem;
import com.duy.ordertracking.inventory.dto.request.UpdateInventoryRequest;
import com.duy.ordertracking.inventory.dto.response.InventoryResponse;
import com.duy.ordertracking.inventory.entity.Inventory;
import com.duy.ordertracking.inventory.event.InventoryFailedEvent;
import com.duy.ordertracking.inventory.event.InventoryReservedEvent;
import com.duy.ordertracking.inventory.exception.AppException;
import com.duy.ordertracking.inventory.kafka.InventoryEventPublisher;
import com.duy.ordertracking.inventory.mapper.InventoryMapper;
import com.duy.ordertracking.inventory.repository.InventoryRepository;
import com.duy.ordertracking.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final long LOCK_TTL_SECONDS = 5;

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final InventoryEventPublisher eventPublisher;

    // -------------------------------------------------------------------------
    // CRUD — không cần Redis hay Kafka
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public InventoryResponse create(CreateInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Inventory already exists for product: " + request.getProductId());
        }

        Inventory inventory = Inventory.builder()
                .productId(request.getProductId())
                .availableQuantity(request.getAvailableQuantity())
                .build();

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .map(inventoryMapper::toResponse)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId));
    }

    @Override
    @Transactional
    public InventoryResponse update(String productId, UpdateInventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId));

        inventory.setAvailableQuantity(request.getAvailableQuantity());

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    // -------------------------------------------------------------------------
    // Kafka + Redis — được gọi bởi OrderEventConsumer
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void reserveStock(String orderId, String userId, List<StockReservationItem> items) {
        List<String> lockKeys = items.stream()
                .map(i -> LOCK_PREFIX + i.getProductId())
                .toList();

        // Danh sách các lock đã acquire được — dùng để release trong finally
        List<String> acquiredLocks = new ArrayList<>();

        try {
            // Bước 1: Acquire Redis lock cho từng product
            // setIfAbsent = SETNX trong Redis: chỉ set nếu key chưa tồn tại
            // → đảm bảo tại một thời điểm chỉ có 1 request được xử lý cho product đó
            for (String lockKey : lockKeys) {
                Boolean locked = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, orderId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);

                if (!Boolean.TRUE.equals(locked)) {
                    log.warn("Could not acquire lock {} for order {}", lockKey, orderId);
                    publishFailed(orderId, userId, "System busy, please retry order");
                    return;
                }
                acquiredLocks.add(lockKey);
            }

            // Bước 2: Load tất cả inventory trong 1 query thay vì N query
            List<String> productIds = items.stream()
                    .map(StockReservationItem::getProductId)
                    .toList();

            Map<String, Inventory> inventoryMap = inventoryRepository
                    .findAllByProductIdIn(productIds)
                    .stream()
                    .collect(Collectors.toMap(Inventory::getProductId, i -> i));

            // Bước 3: Kiểm tra TẤT CẢ items trước khi trừ bất kỳ cái nào
            // → nếu thiếu 1 item thì rollback toàn bộ, không trừ kho nửa vời
            for (StockReservationItem item : items) {
                Inventory inventory = inventoryMap.get(item.getProductId());

                if (inventory == null) {
                    publishFailed(orderId, userId,
                            "Product not found in inventory: " + item.getProductId());
                    return;
                }

                if (inventory.getAvailableQuantity() < item.getQuantity()) {
                    publishFailed(orderId, userId,
                            "Insufficient stock for product: " + item.getProductId()
                            + " (available: " + inventory.getAvailableQuantity()
                            + ", requested: " + item.getQuantity() + ")");
                    return;
                }
            }

            // Bước 4: Đủ hàng tất cả → trừ available, tăng reserved
            items.forEach(item -> {
                Inventory inventory = inventoryMap.get(item.getProductId());
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() - item.getQuantity());
                inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            });

            inventoryRepository.saveAll(new ArrayList<>(inventoryMap.values()));

            // Bước 5: Publish thành công → order-service sẽ set CONFIRMED
            eventPublisher.publishInventoryReserved(InventoryReservedEvent.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .reservedAt(LocalDateTime.now())
                    .build());

            log.info("Stock reserved successfully for order {}", orderId);

        } finally {
            // Luôn release lock dù thành công hay thất bại
            acquiredLocks.forEach(redisTemplate::delete);
        }
    }

    @Override
    @Transactional
    public void releaseStock(String orderId, List<StockReservationItem> items) {
        List<String> lockKeys = items.stream()
                .map(i -> LOCK_PREFIX + i.getProductId())
                .toList();

        List<String> acquiredLocks = new ArrayList<>();

        try {
            // Acquire lock trước khi hoàn kho — tránh race condition với reserveStock
            for (String lockKey : lockKeys) {
                Boolean locked = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, orderId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);

                if (!Boolean.TRUE.equals(locked)) {
                    log.warn("Could not acquire lock {} for releasing order {}", lockKey, orderId);
                    return;
                }
                acquiredLocks.add(lockKey);
            }

            List<String> productIds = items.stream()
                    .map(StockReservationItem::getProductId)
                    .toList();

            Map<String, Inventory> inventoryMap = inventoryRepository
                    .findAllByProductIdIn(productIds)
                    .stream()
                    .collect(Collectors.toMap(Inventory::getProductId, i -> i));

            // Hoàn kho: tăng available, giảm reserved
            // Math.max(0, ...) đề phòng data inconsistency — reserved không bao giờ âm
            items.forEach(item -> {
                Inventory inventory = inventoryMap.get(item.getProductId());
                if (inventory != null) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + item.getQuantity());
                    inventory.setReservedQuantity(
                            Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
                }
            });

            inventoryRepository.saveAll(new ArrayList<>(inventoryMap.values()));

            log.info("Stock released successfully for order {}", orderId);

        } finally {
            acquiredLocks.forEach(redisTemplate::delete);
        }
    }

    // -------------------------------------------------------------------------
    // Private helper
    // -------------------------------------------------------------------------

    private void publishFailed(String orderId, String userId, String reason) {
        log.warn("Inventory reservation failed for order {}: {}", orderId, reason);
        eventPublisher.publishInventoryFailed(InventoryFailedEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .failedAt(LocalDateTime.now())
                .build());
    }
}
