# Order Service — Deep Dive Design

> Mục tiêu: hiểu rõ từng bước trước khi tự tay code Kafka + Redis.
> CRUD đã quen — file này tập trung vào phần phức tạp: async communication, event-driven flow, distributed patterns.

---

## 1. Order Status Machine

Đây là trái tim của service. Mọi thứ xoay quanh việc status thay đổi như thế nào và **ai** thay đổi nó.

```
                   ┌─────────────────────────────────┐
                   │                                 │
  POST /orders ──► PENDING ──► (Kafka async) ──► CONFIRMED ──► SHIPPING ──► DELIVERED
                     │                                │
                     │         inventory-failed       │
                     └──────────────────────────────► CANCELLED
                                                      │
                     PUT /orders/{id}/cancel ─────────┘
                     (chỉ cancel được khi PENDING)
```

| Status | Ai set | Khi nào |
|--------|--------|---------|
| `PENDING` | order-service | Ngay khi tạo đơn, chưa biết kho còn hàng không |
| `CONFIRMED` | order-service | Sau khi nhận event `inventory-reserved` từ Kafka |
| `CANCELLED` | order-service | Nhận `inventory-failed` **hoặc** user gọi cancel API |
| `SHIPPING` | (Phase 2+) | Sau khi payment xong |
| `DELIVERED` | (Phase 2+) | Xác nhận giao hàng |

**Tại sao không check kho ngay khi tạo đơn?**
Vì inventory-service có thể chậm, bận, hoặc đang xử lý concurrent request từ nhiều user.
Nếu gọi sync thì order-service phải *chờ* → latency cao, coupling chặt.
Kafka cho phép order-service trả response ngay (`PENDING`), inventory xử lý bất đồng bộ.

---

## 2. Database Schema

```sql
-- Bảng orders
CREATE TABLE orders (
    id            VARCHAR(36) PRIMARY KEY,   -- UUID
    user_id       VARCHAR(36) NOT NULL,      -- lấy từ header X-User-Id (gateway forward)
    status        VARCHAR(20) NOT NULL,      -- PENDING | CONFIRMED | CANCELLED | ...
    total_amount  DECIMAL(15,2) NOT NULL,
    note          TEXT,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

-- Bảng order_items (1 order có nhiều items)
CREATE TABLE order_items (
    id            VARCHAR(36) PRIMARY KEY,
    order_id      VARCHAR(36) NOT NULL,      -- FK → orders.id
    product_id    VARCHAR(36) NOT NULL,
    product_name  VARCHAR(255) NOT NULL,     -- denormalize: lưu luôn tên sản phẩm
    quantity      INT NOT NULL,
    unit_price    DECIMAL(15,2) NOT NULL,    -- giá tại thời điểm đặt hàng
    subtotal      DECIMAL(15,2) NOT NULL     -- quantity * unit_price
);
```

**Tại sao denormalize `product_name` và `unit_price` vào order_items?**
- Giá sản phẩm có thể thay đổi sau khi đặt hàng — phải lưu giá *tại thời điểm đặt*
- Tên sản phẩm có thể đổi — đơn hàng lịch sử phải hiển thị đúng tên cũ
- Không cần JOIN sang product-service mỗi lần query đơn hàng

---

## 3. Flow Tạo Đơn Hàng (Chi Tiết)

```
Client: POST /api/orders
Body: {
  "items": [
    {"productId": "abc-123", "quantity": 2},
    {"productId": "def-456", "quantity": 1}
  ],
  "note": "Giao buoi sang"
}
Header: Authorization: Bearer <jwt-token>
```

**Bước 1 — Gateway xử lý:**
```
Gateway nhận request
  → Validate JWT token
  → Extract userId + role từ token
  → Thêm header: X-User-Id: "uuid-của-user"
  → Thêm header: X-User-Role: "USER"
  → Forward đến order-service
```

**Bước 2 — Order-service nhận request:**
```java
// Controller đọc userId từ header do gateway inject
@PostMapping
public ResponseEntity<?> createOrder(
    @RequestBody CreateOrderRequest request,
    @RequestHeader("X-User-Id") String userId  // ← gateway forward vào đây
) { ... }
```

**Bước 3 — Gọi sync sang product-service (OpenFeign):**
```
order-service → [Eureka lookup "product-service"] → product-service
GET /api/products/batch?ids=abc-123,def-456

Response: [
  {id: "abc-123", name: "iPhone 15 Pro", price: 29990000, active: true},
  {id: "def-456", name: "MacBook Air M3", price: 32990000, active: true}
]
```

Tại sao OpenFeign thay vì RestTemplate?
- Khai báo như interface, không viết URL cứng
- Tự dùng Eureka để resolve `product-service` → IP:port
- Tích hợp load balancing tự động

**Bước 4 — Tính toán và lưu DB:**
```
totalAmount = (2 × 29,990,000) + (1 × 32,990,000) = 92,970,000
Lưu Order {status=PENDING, userId, totalAmount}
Lưu OrderItem × 2
```

**Bước 5 — Publish Kafka event:**
```json
Topic: order-created
Key: "order-uuid"        ← partition key, đảm bảo cùng orderId đi cùng 1 partition
Value: {
  "orderId": "uuid",
  "userId": "uuid",
  "items": [
    {"productId": "abc-123", "quantity": 2},
    {"productId": "def-456", "quantity": 1}
  ],
  "totalAmount": 92970000,
  "createdAt": "2026-06-07T10:00:00"
}
```

**Bước 6 — Trả response ngay (không chờ kho):**
```json
HTTP 201 Created
{
  "code": 201,
  "message": "Order created",
  "data": {
    "orderId": "uuid",
    "status": "PENDING",
    "totalAmount": 92970000,
    "items": [...]
  }
}
```

---

## 4. Kafka — Producer (Phase 1)

### Config cần thiết trong application.yml

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      # Đảm bảo message không bị mất khi broker restart
      acks: all
      retries: 3
      properties:
        enable.idempotence: true        # chống gửi trùng khi retry
        max.in.flight.requests.per.connection: 1
```

### Topic cần tạo

Kafka tự tạo topic nếu `auto.create.topics.enable=true` (default).
Nhưng production nên tạo thủ công để control partition count:

```yaml
# Config trong application.yml để Spring tự tạo topic khi start
spring:
  kafka:
    admin:
      auto-create: true
```

Hoặc tạo bằng `@Bean NewTopic`:
```java
@Bean
public NewTopic orderCreatedTopic() {
    return TopicBuilder.name("order-created")
            .partitions(3)    // 3 partition = 3 consumer chạy song song tối đa
            .replicas(1)      // dev: 1 replica, prod: 3
            .build();
}
```

### Event Payload Classes

```java
// OrderCreatedEvent.java — object này sẽ được serialize thành JSON lên Kafka
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private List<OrderItemEvent> items;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}

public class OrderItemEvent {
    private String productId;
    private int quantity;
}
```

### Gửi message

```java
@Service
public class OrderEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(
            "order-created",    // topic
            event.getOrderId(), // key → partition routing
            event               // value → serialize to JSON
        );
        // KafkaTemplate.send() là async, trả về CompletableFuture
        // Có thể .get() để block và biết lỗi, hoặc .whenComplete() để handle callback
    }
}
```

**Partition key là orderId — tại sao quan trọng?**
```
Partition 0: orderId=AAA [created] → [cancelled]
Partition 1: orderId=BBB [created] → [reserved]
Partition 2: orderId=CCC [created] → [failed]

Cùng 1 orderId luôn đi cùng 1 partition
→ Consumer đọc theo đúng thứ tự: created trước, rồi mới tới reserved/failed
→ Không bao giờ xảy ra: nhận "reserved" trước "created"
```

---

## 5. Kafka — Consumer (Phase 2)

Order-service không chỉ produce — nó còn phải lắng nghe kết quả từ inventory-service.

### Consumer Group

```yaml
spring:
  kafka:
    consumer:
      group-id: order-service-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest  # đọc từ đầu nếu chưa có offset
      properties:
        spring.json.trusted.packages: "com.duy.ordertracking.*"
```

### Consume inventory-reserved

```java
@KafkaListener(topics = "inventory-reserved", groupId = "order-service-group")
public void handleInventoryReserved(InventoryReservedEvent event) {
    // inventory-service đã giữ hàng thành công → confirm đơn
    orderService.updateStatus(event.getOrderId(), OrderStatus.CONFIRMED);
    log.info("Order {} confirmed after inventory reserved", event.getOrderId());
}
```

### Consume inventory-failed

```java
@KafkaListener(topics = "inventory-failed", groupId = "order-service-group")
public void handleInventoryFailed(InventoryFailedEvent event) {
    // Hết hàng → cancel đơn (compensating transaction)
    orderService.updateStatus(event.getOrderId(), OrderStatus.CANCELLED);
    log.info("Order {} cancelled: {}", event.getOrderId(), event.getReason());
}
```

**Idempotent Consumer — vấn đề quan trọng:**
Kafka đảm bảo *at-least-once* delivery — nghĩa là cùng 1 message có thể được deliver 2 lần
(khi consumer crash sau khi xử lý nhưng trước khi commit offset).

```java
@KafkaListener(topics = "inventory-reserved", groupId = "order-service-group")
public void handleInventoryReserved(InventoryReservedEvent event) {
    Order order = orderRepository.findById(event.getOrderId()).orElseThrow();

    // Guard: chỉ update nếu đang PENDING — tránh update 2 lần
    if (order.getStatus() != OrderStatus.PENDING) {
        log.warn("Order {} already processed, skipping", event.getOrderId());
        return;
    }

    order.setStatus(OrderStatus.CONFIRMED);
    orderRepository.save(order);
}
```

---

## 6. OpenFeign — Gọi Product-Service

### Dependency cần thêm

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

### Enable Feign trong Application class

```java
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication { ... }
```

### Feign Client Interface

```java
@FeignClient(name = "product-service")  // "product-service" = spring.application.name của product-service
public interface ProductClient {

    @GetMapping("/api/products/batch")
    ApiResponse<List<ProductResponse>> getProductsByIds(@RequestParam List<String> ids);
}
```

Eureka tự resolve `product-service` → `localhost:8082` (hoặc IP thật khi deploy).
Không cần hardcode URL.

### Error Handling khi product-service down

```java
// Nếu product-service không phản hồi → FeignException được throw
// Cần catch và xử lý gracefully

try {
    List<ProductResponse> products = productClient.getProductsByIds(productIds).getData();
} catch (FeignException.ServiceUnavailable e) {
    throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
} catch (FeignException e) {
    throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to fetch product info");
}
```

Timeout config (quan trọng — không để Feign chờ mãi):
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          product-service:
            connect-timeout: 3000   # 3s để connect
            read-timeout: 5000      # 5s để đọc response
```

---

## 7. Redis — Idempotency Key (Phase 3)

**Vấn đề:** Client gửi `POST /orders` 2 lần do network retry → tạo 2 đơn hàng giống nhau.

**Giải pháp:** Client gửi kèm `Idempotency-Key` header (UUID tự sinh phía client).
Server check Redis — nếu key đã tồn tại thì trả về response cũ, không tạo đơn mới.

```
Client gửi:
  POST /api/orders
  Idempotency-Key: "req-abc-123-xyz"
  Body: { items: [...] }

Lần 1:
  Redis GET "order:idempotency:req-abc-123-xyz" → null (chưa có)
  → Tạo đơn hàng
  → Redis SET "order:idempotency:req-abc-123-xyz" "{orderId: ...}" EX 300
  → Trả response

Lần 2 (retry từ client):
  Redis GET "order:idempotency:req-abc-123-xyz" → "{orderId: ...}" (đã có)
  → Return luôn response cũ, KHÔNG tạo đơn mới
```

Code pattern:
```java
public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
    // Check Redis trước
    String cached = redisTemplate.opsForValue().get("order:idempotency:" + idempotencyKey);
    if (cached != null) {
        return objectMapper.readValue(cached, OrderResponse.class);
    }

    // Tạo đơn hàng
    OrderResponse response = doCreateOrder(request);

    // Lưu vào Redis với TTL 5 phút
    redisTemplate.opsForValue().set(
        "order:idempotency:" + idempotencyKey,
        objectMapper.writeValueAsString(response),
        Duration.ofMinutes(5)
    );

    return response;
}
```

---

## 8. Toàn Bộ Kafka Topics — Order-Service Tham Gia

```
order-service PRODUCE:
  ├── order-created    → [inventory-service consume] [notification-service consume]
  └── order-cancelled  → [inventory-service consume] [notification-service consume]

order-service CONSUME:
  ├── inventory-reserved  → update status = CONFIRMED
  └── inventory-failed    → update status = CANCELLED
```

**Consumer Group ID quan trọng:**
- Mỗi service dùng group-id riêng: `order-service-group`
- Nếu inventory-service và notification-service cùng consume `order-created`,
  mỗi service phải có group-id **khác nhau**
- Cùng group-id = load balancing (1 trong N consumer nhận message)
- Khác group-id = broadcast (tất cả đều nhận)

```
Topic: order-created (3 partitions)

inventory-service-group:
  └── consumer-1 → đọc partition 0, 1, 2

notification-service-group:
  └── consumer-1 → đọc partition 0, 1, 2

→ Cả 2 service đều nhận TỪNG message (broadcast behavior)
```

---

## 9. Cấu Trúc Package order-service

```
order-service/
└── src/main/java/com/duy/ordertracking/order/
    ├── OrderServiceApplication.java
    ├── config/
    │   ├── SecurityConfig.java
    │   └── KafkaTopicConfig.java       ← khai báo NewTopic beans
    ├── controller/
    │   └── OrderController.java
    ├── dto/
    │   ├── request/
    │   │   └── CreateOrderRequest.java
    │   └── response/
    │       ├── OrderResponse.java
    │       └── OrderItemResponse.java
    ├── entity/
    │   ├── Order.java
    │   └── OrderItem.java
    ├── event/
    │   ├── producer/
    │   │   ├── OrderCreatedEvent.java   ← payload object
    │   │   └── OrderEventPublisher.java ← KafkaTemplate wrapper
    │   └── consumer/
    │       ├── InventoryReservedEvent.java
    │       ├── InventoryFailedEvent.java
    │       └── InventoryEventConsumer.java ← @KafkaListener methods
    ├── exception/
    │   ├── AppException.java
    │   └── GlobalExceptionHandler.java
    ├── external/
    │   └── ProductClient.java          ← @FeignClient interface
    ├── mapper/
    │   └── OrderMapper.java            ← MapStruct
    ├── repository/
    │   ├── OrderRepository.java
    │   └── OrderItemRepository.java
    └── service/
        ├── OrderService.java           ← interface
        └── impl/
            └── OrderServiceImpl.java
```

---

## 10. Thứ Tự Tự Code

### Phase 1 — Làm trước (không cần inventory-service)
1. Entity `Order` + `OrderItem`
2. `CreateOrderRequest` DTO (chứa list items)
3. `OrderResponse` + `OrderItemResponse` DTO
4. MapStruct mapper
5. Repository
6. `ProductClient` — OpenFeign interface
7. `OrderServiceImpl.createOrder()` — gọi Feign + tính total + lưu DB
8. **`OrderCreatedEvent`** + **`OrderEventPublisher`** — publish lên Kafka
9. `OrderController` — `POST /orders`, `GET /orders/{id}`, `GET /orders/my-orders`
10. `KafkaTopicConfig` — tạo topic `order-created`
11. Test: tạo đơn → check Kafka UI http://localhost:9091 thấy message

### Phase 2 — Saga (cần inventory-service)
12. `InventoryReservedEvent` + `InventoryFailedEvent` payload classes
13. `InventoryEventConsumer` với `@KafkaListener`
14. `OrderServiceImpl.updateStatus()` — idempotent
15. Test full saga: tạo đơn → inventory nhận → reserved/failed → order update status

### Phase 3 — Redis
16. Thêm `spring-boot-starter-data-redis` dependency
17. `RedisConfig` — cấu hình RedisTemplate
18. Idempotency key pattern trong `createOrder()`
19. Cache order detail (cache-aside)

---

## 11. Điều Cần Nhớ Khi Tự Code

**Kafka producer:**
- `KafkaTemplate<String, Object>` — String key, Object value (JsonSerializer tự convert)
- `kafkaTemplate.send(topic, key, value)` — key là orderId để đảm bảo ordering
- Production cần handle `CompletableFuture` để biết gửi có thành công không

**Kafka consumer:**
- `@KafkaListener(topics = "...", groupId = "...")` đặt trên method
- Method nhận thẳng object nếu config đúng JsonDeserializer
- Luôn làm **idempotent**: check trạng thái trước khi update
- Nếu throw exception trong listener → Kafka sẽ retry (at-least-once)

**OpenFeign:**
- `@FeignClient(name = "product-service")` — name phải khớp `spring.application.name`
- `@EnableFeignClients` trên Application class
- Eureka phải đang chạy để resolve service name

**Luồng userId:**
- Client gửi JWT → Gateway verify → extract userId → inject header `X-User-Id`
- Order-service đọc `@RequestHeader("X-User-Id") String userId` — không tự verify JWT
- Order-service **tin tưởng** gateway, không tự decode token
