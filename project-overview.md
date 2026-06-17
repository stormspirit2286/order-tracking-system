# Real-time Order Tracking System

> Dự án microservices thực chiến — tập trung học Kafka, Redis, Saga Pattern, Sync/Async Communication trong Java Spring Boot.

---

## Mục tiêu dự án

Xây dựng hệ thống theo dõi đơn hàng realtime, domain đơn giản (đặt hàng → xử lý → giao hàng) nhưng cover đầy đủ các pattern quan trọng của distributed system. Không tập trung vào business phức tạp, mà tập trung vào **infrastructure, communication patterns, và production-grade concerns**.

---

## Tech Stack

| Layer | Công nghệ |
|-------|-----------|
| Frontend | ReactJS |
| Backend | Java 21 / Spring Boot 3.2.x |
| Database | PostgreSQL (database-per-service) |
| Message Broker | Apache Kafka |
| Cache | Redis |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Config | Spring Cloud Config Server |
| Auth | JWT (Spring Security) |
| Object Mapping | MapStruct 1.5.5 (tất cả service) |
| Containerization | Docker / Docker Compose |

---

## Kiến trúc tổng quan

```
                         ┌─────────────┐
                         │   React     │
                         │   Frontend  │
                         └──────┬──────┘
                                │
                         ┌──────▼──────┐
                         │ API Gateway │ ← JWT filter, routing, rate limit
                         └──────┬──────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         │                      │                       │
  ┌──────▼──────┐       ┌──────▼──────┐        ┌───────▼───────┐
  │   Order     │──sync─│  Product    │        │ Notification  │
  │   Service   │       │  Service    │        │   Service     │
  └──────┬──────┘       └─────────────┘        └───────┬───────┘
         │                                             │
         │              ┌─────────────┐                │
         │              │  Inventory  │                │
         │              │   Service   │                │
         │              └──────┬──────┘                │
         │                     │                       │
         └─────────► Kafka ◄───┴───────────────────────┘
                  (event bus chung)

  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │   Eureka     │  │   Config     │  │  Identity    │
  │   Service    │  │   Service    │  │  Service     │
  └──────────────┘  └──────────────┘  └──────────────┘

  ┌─────────────────────────────────┐
  │            Redis                │
  │  (cache, lock, rate limit,     │
  │   idempotency, unread count)   │
  └─────────────────────────────────┘
```

---

## Tất cả Services

### Nhóm 1: Infrastructure Services (4 services)

Không chứa business logic, phục vụ cho hệ thống vận hành.

#### 1. `eureka-service`
- Service registry
- Mỗi service khi start đăng ký vào Eureka
- API Gateway hỏi Eureka để tìm service thay vì hardcode IP
- Cho phép scale nhiều instance

#### 2. `config-service`
- Centralized configuration
- Tất cả config lưu tập trung (Git repo)
- Service start lên pull config từ đây
- Thay đổi config không cần rebuild

#### 3. `api-gateway`
- Cửa ngõ duy nhất, mọi request đi qua đây
- Routing request đến đúng service
- JWT filter — xác thực token trước khi cho request đi tiếp
- Rate limiting ở tầng gateway

#### 4. `identity-service`
- Đăng ký tài khoản, đăng nhập
- Cấp JWT token, refresh token
- Phân quyền (ROLE_USER, ROLE_ADMIN)
- Các service khác verify user dựa vào token

---

### Nhóm 2: Business Services / Downstream Services (4 services)

Chứa nghiệp vụ thực sự. Giao tiếp qua **Kafka (async)** và **REST (sync)**.

#### 1. `product-service` — Quản lý sản phẩm

**Nghiệp vụ:** Quản lý thông tin sản phẩm. Data ít thay đổi, read-heavy.

**Entities:**
- `Product` — id, name, description, price, imageUrl, category, createdAt, updatedAt

**API endpoints:**
- `GET /products` — Danh sách sản phẩm (có phân trang, filter theo category)
- `GET /products/{id}` — Chi tiết sản phẩm
- `GET /products/batch?ids=1,2,3` — Lấy nhiều sản phẩm theo danh sách ID (**endpoint quan trọng, order-service gọi sync vào đây**)
- `POST /products` — Thêm sản phẩm (admin)
- `PUT /products/{id}` — Cập nhật sản phẩm (admin)

**Redis:** Cache product detail, cache product list theo category (cache-aside pattern).

**Giao tiếp:** Được gọi **sync** bởi order-service qua REST (RestTemplate / WebClient / OpenFeign).

---

#### 2. `order-service` — Quản lý đơn hàng ⭐ Trái tim hệ thống

**Nghiệp vụ:** Quản lý toàn bộ vòng đời đơn hàng.

**Entities:**
- `Order` — id, userId, totalAmount, status, note, createdAt, updatedAt
- `OrderItem` — id, orderId, productId, productName, quantity, unitPrice

**Order status flow:**
```
PENDING → CONFIRMED → SHIPPING → DELIVERED
   │
   └──→ CANCELLED (khi inventory fail hoặc user hủy)
```

**API endpoints:**
- `POST /orders` — Tạo đơn hàng mới
- `GET /orders/{id}` — Xem chi tiết đơn hàng
- `GET /orders/my-orders` — Đơn hàng của user hiện tại (userId từ JWT)
- `PUT /orders/{id}/cancel` — Hủy đơn hàng

**Flow tạo đơn hàng (quan trọng):**
```
Client gửi: POST /orders
Body: [{ productId: 1, quantity: 2 }, { productId: 5, quantity: 1 }]
    │
    ▼
order-service nhận request
    │
    ▼
Gọi SYNC sang product-service: GET /products/batch?ids=1,5
    │
    ▼
Nhận về product info (name, price)
    │
    ▼
Tính totalAmount, lưu Order + OrderItem vào DB (status = PENDING)
    │
    ▼
Publish Kafka event: "order-created"
```

**Kafka:**
- **Producer:** publish `order-created`, `order-cancelled`
- **Consumer:** listen `inventory-reserved` → update CONFIRMED, listen `inventory-failed` → update CANCELLED

**Redis:**
- Cache order detail (cache-aside)
- Idempotency key: `SET order:idempotency:{requestId} NX EX 300` — chống duplicate request

---

#### 3. `inventory-service` — Quản lý tồn kho ⭐ Service học Kafka + Redis sâu nhất

**Nghiệp vụ:** Quản lý số lượng tồn kho. Khi có đơn hàng mới, check còn hàng không → reserve hoặc reject.

**Entities:**
- `Inventory` — id, productId, availableQuantity, reservedQuantity

**Tại sao tách `availableQuantity` và `reservedQuantity`?**
Khi đơn hàng được tạo, hàng chưa xuất kho mà chỉ "giữ chỗ". Ví dụ kho có 100, đơn đặt 3 → available = 97, reserved = 3. Đơn hàng confirm + shipped → giảm reserved. Đơn hàng hủy → trả lại available.

**API endpoints:**
- `GET /inventory/{productId}` — Xem tồn kho sản phẩm
- `PUT /inventory/{productId}` — Admin cập nhật số lượng kho (nhập hàng)

**Kafka:**
- **Consumer:** listen `order-created` → check tồn kho → trừ kho hoặc reject
- **Producer:** publish `inventory-reserved` hoặc `inventory-failed`

**Flow xử lý khi nhận `order-created`:**
```
Nhận event order-created (chứa danh sách items)
    │
    ▼
Acquire Redis Distributed Lock: LOCK:inventory:{productId}
    │
    ▼
Query DB: availableQuantity >= orderQuantity?
    │
    ├── Đủ hàng:
    │     Update DB: available -= qty, reserved += qty
    │     Invalidate Redis cache
    │     Release lock
    │     Publish "inventory-reserved"
    │
    └── Không đủ:
          Release lock
          Publish "inventory-failed" (kèm reason)
```

**Redis:**
- Cache tồn kho (read-heavy, user xem sản phẩm liên tục)
- **Distributed Lock** — `LOCK:inventory:{productId}`, chống oversell khi concurrent request
- Cache invalidation khi tồn kho thay đổi

---

#### 4. `notification-service` — Thông báo

**Nghiệp vụ:** Lắng nghe mọi event, gửi thông báo cho user. Giai đoạn đầu chỉ log/lưu DB, sau tích hợp email/WebSocket.

**Entities:**
- `Notification` — id, userId, type, title, message, isRead, createdAt

**Kafka — Consumer thuần, không produce:**

| Event nhận | Notification gửi |
|------------|-------------------|
| `order-created` | "Đơn hàng #123 đã được tạo, đang chờ xác nhận" |
| `inventory-reserved` | "Đơn hàng #123 đã được xác nhận" |
| `inventory-failed` | "Đơn hàng #123 bị hủy do hết hàng" |
| `order-cancelled` | "Đơn hàng #123 đã hủy thành công" |

**API endpoints:**
- `GET /notifications` — Danh sách notification của user
- `PUT /notifications/{id}/read` — Đánh dấu đã đọc
- `PUT /notifications/read-all` — Đánh dấu tất cả đã đọc

**Redis:**
- **Rate Limiting** — throttle notification khi event quá nhiều
- Unread count: `INCR notification:unread:{userId}` — client poll nhanh

---

## Kafka Topics & Events

| Topic | Producer | Consumer | Payload chính |
|-------|----------|----------|---------------|
| `order-created` | order-service | inventory-service, notification-service | orderId, userId, items[{productId, qty}] |
| `order-cancelled` | order-service | inventory-service, notification-service | orderId, userId, reason |
| `inventory-reserved` | inventory-service | order-service, notification-service | orderId, userId |
| `inventory-failed` | inventory-service | order-service, notification-service | orderId, userId, reason |

---

## Saga Flow (Choreography)

### Happy Path
```
order-created ──→ inventory-service kiểm tra
                        │
                        ▼ (đủ hàng)
                inventory-reserved ──→ order-service update CONFIRMED
                                  ──→ notification-service gửi "đã xác nhận"
```

### Failure Path (Compensating Transaction)
```
order-created ──→ inventory-service kiểm tra
                        │
                        ▼ (hết hàng)
                inventory-failed ──→ order-service update CANCELLED
                                ──→ notification-service gửi "hết hàng"
```

---

## Giao tiếp giữa các service

| Kiểu | Từ → Đến | Tại sao |
|------|-----------|---------|
| **Sync (REST)** | order-service → product-service | Cần data ngay lập tức để tạo đơn hàng, không thể chờ |
| **Async (Kafka)** | order-service → inventory-service | Không cần response ngay, decouple, scale tốt |
| **Async (Kafka)** | order-service → notification-service | Fire-and-forget, không ảnh hưởng flow chính |
| **Async (Kafka)** | inventory-service → order-service | Update status sau khi check xong, eventually consistent |
| **Async (Kafka)** | inventory-service → notification-service | Thông báo kết quả |

---

## Phân chia Phase học

### Phase 1: Kafka Cơ bản ⏱️ ~1-2 tuần
**Mục tiêu:** Hiểu Producer / Consumer / Topic / Partition / Consumer Group

**Làm gì:**
- [x] Setup Docker Compose: Kafka (KRaft mode, không cần Zookeeper), PostgreSQL, Redis
- [ ] Tạo `order-service`: CRUD đơn hàng + publish `order-created` lên Kafka
- [ ] Tạo `notification-service`: consume `order-created`, log ra console + lưu DB
- [x] Tạo `product-service`: CRUD sản phẩm, expose endpoint `/products/batch` — MapStruct mapper
- [ ] Order-service gọi sync sang product-service (OpenFeign + Eureka service name)

**Kafka kiến thức:**
- [ ] Producer API — KafkaTemplate, serialize message (JSON)
- [ ] Consumer API — @KafkaListener, consumer group, offset
- [ ] Topic, Partition — tạo topic, hiểu partition key
- [ ] Spring Kafka config — application.yml, serializer/deserializer

**Sync communication kiến thức:**
- [ ] RestTemplate — cách cũ, blocking, synchronous
- [ ] WebClient — cách mới, non-blocking
- [ ] OpenFeign — declarative, tích hợp Eureka, gọi bằng service name
- [ ] Timeout config — connection timeout + read timeout
- [ ] Error handling khi service bị gọi không available

---

### Phase 2: Saga Pattern ⏱️ ~1-2 tuần
**Mục tiêu:** Hiểu Choreography Saga, Compensating Transaction, Eventually Consistent

**Làm gì:**
- [ ] Tạo `inventory-service`: consume `order-created`, check tồn kho
- [ ] Implement flow: đủ hàng → publish `inventory-reserved`, không đủ → publish `inventory-failed`
- [ ] Order-service consume `inventory-reserved` → update CONFIRMED
- [ ] Order-service consume `inventory-failed` → update CANCELLED (compensating transaction)
- [ ] Notification-service consume thêm `inventory-reserved`, `inventory-failed`
- [ ] Test scenario: tạo đơn hàng khi đủ hàng vs khi hết hàng

**Kafka kiến thức:**
- [ ] Consumer Group rebalancing — thêm/bớt instance
- [ ] Partition key by orderId — đảm bảo ordering event cùng 1 đơn
- [ ] At-least-once semantics — hiểu message có thể bị deliver lại

---

### Phase 3: Redis Layer ⏱️ ~1-2 tuần
**Mục tiêu:** Nắm vững Cache-aside, Distributed Lock, Rate Limiting, Idempotency

**Làm gì:**
- [ ] Product-service: cache product detail + list (cache-aside pattern)
- [ ] Order-service: cache order detail + idempotency key (chống duplicate request)
- [ ] Inventory-service: **distributed lock** khi trừ kho (chống oversell)
- [ ] Inventory-service: cache tồn kho + invalidation khi thay đổi
- [ ] Notification-service: rate limiting (sliding window) + unread count

**Redis kiến thức:**
- [ ] Data structures — String, Hash, Set, Sorted Set
- [ ] TTL, eviction policy (LRU, LFU)
- [ ] Spring Data Redis / RedisTemplate / Lettuce
- [ ] Cache-aside pattern — check Redis → miss → query DB → set Redis
- [ ] Cache invalidation — khi nào xóa cache vs update cache
- [ ] Distributed Lock — `SET key NX EX` hoặc Redisson
- [ ] Rate Limiting — sliding window counter
- [ ] Idempotency Store — `SET requestId NX EX 300`

---

### Phase 4: Production Concerns ⏱️ ~1-2 tuần
**Mục tiêu:** Error handling, resilience, monitoring — chuyển từ "chạy được" sang "chạy ổn định"

**Làm gì:**
- [ ] Dead Letter Topic (DLT) — message lỗi retry N lần rồi đẩy vào DLT
- [ ] Retry policy — RetryTemplate, exponential backoff
- [ ] Idempotent Consumer — inventory-service xử lý trùng message không trừ kho 2 lần
- [ ] Circuit Breaker (Resilience4j) — order-service gọi product-service, product chết thì không chết theo
- [ ] Fallback — product-service down → đọc từ Redis cache
- [ ] Outbox Pattern (nâng cao) — ghi event vào DB trước, rồi đẩy lên Kafka
- [ ] Monitoring — Kafka consumer lag, Redis hit/miss ratio

**Kafka kiến thức:**
- [ ] Dead Letter Topic — SeekToCurrentErrorHandler, DLT config
- [ ] Exactly-once semantics — idempotent producer, transactional outbox
- [ ] Kafka transaction
- [ ] Schema evolution — message format thay đổi theo thời gian

**Resilience kiến thức:**
- [ ] Circuit Breaker — Resilience4j, states (CLOSED → OPEN → HALF_OPEN)
- [ ] Retry + Backoff
- [ ] Fallback strategy
- [ ] Timeout handling

---

### Phase 5 (Bonus): Mở rộng
**Khi đã vững Phase 1-4, thêm service mới để mở rộng Saga:**

- [ ] `payment-service` — consume `inventory-reserved` → xử lý thanh toán → produce `payment-completed` / `payment-failed`. Saga dài thêm 1 bước, compensating transaction phức tạp hơn.
- [ ] `shipping-service` — consume `payment-completed` → tạo vận đơn → produce `shipping-created`
- [ ] Distributed Tracing — Zipkin/Jaeger, trace request qua nhiều service
- [ ] WebSocket — push realtime order status lên ReactJS

---

## Sau khi hoàn thành — Trình độ ở đâu?

| Lĩnh vực | Đánh giá |
|-----------|----------|
| Kafka | Đủ dùng production, trả lời phỏng vấn mid-senior |
| Redis | Nắm vững pattern thực tế, không chỉ GET/SET |
| Distributed System | Hiểu core concepts (eventual consistency, idempotency, distributed lock) |
| Saga Pattern | Hiểu choreography, compensating transaction, trade-off với orchestration |
| Sync vs Async Communication | Biết khi nào dùng REST, khi nào dùng Kafka, tại sao |
| Resilience | Circuit breaker, retry, fallback, DLT |
| Microservices overall | Đã build và vận hành, không chỉ lý thuyết |

---

## Câu hỏi phỏng vấn có thể trả lời từ project

1. Sao chọn Kafka mà không dùng RabbitMQ?
2. Giải thích Saga Pattern? Choreography vs Orchestration?
3. Eventual consistency là gì? Chấp nhận được không?
4. Distributed lock giải quyết vấn đề gì?
5. Cache invalidation strategy?
6. Dead Letter Topic dùng khi nào?
7. Circuit breaker hoạt động thế nào?
8. Idempotency trong distributed system?
9. Outbox pattern giải quyết vấn đề gì?
10. Khi nào gọi sync, khi nào async giữa các service?

---

## Coding Conventions

- **Service layer:** luôn tách `interface` + `impl` — controller inject interface, `@Service` đặt ở impl
- **Mapping:** toàn bộ project dùng **MapStruct** (`@Mapper(componentModel = "spring")`), không dùng manual mapping
- **Comment:** không viết comment trong code — tên class/method/variable phải tự giải thích
- **Package impl:** `service/impl/XxxServiceImpl.java` implements `service/XxxService.java`

## Ghi chú

- Mỗi business service có **DB riêng** (database-per-service pattern), không share DB
- Giao tiếp giữa business services: **Kafka (async)** là chính, **REST (sync)** chỉ khi cần data ngay
- Infra services (eureka, config, gateway, identity) setup 1 lần, ít thay đổi
- Tập trung thời gian vào 4 business services + Kafka + Redis