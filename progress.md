# Project Progress

> Last updated: 2026-06-08 10:12 GMT+7

---

## Infrastructure Services ✅

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| eureka-service | 8761 | ✅ Done & Running | Service discovery, basic auth eureka/eureka123 |
| config-service | 8888 | ✅ Done & Running | Native profile, serves classpath:/configs |
| api-gateway | 8080 | ✅ Done & Running | JWT filter, public paths, routes đến tất cả service |
| identity-service | 8081 | ✅ Done & Running | Register, Login, Refresh token, BCrypt, JWT |

---

## Docker Infrastructure ✅

| Container | Port | Status |
|-----------|------|--------|
| PostgreSQL | 5432 | ✅ Running |
| Kafka (KRaft) | 9092 | ✅ Running |
| Kafka UI | 9091 | ✅ Running |
| Redis | 6379 | ✅ Running |
| Redis Commander | 8085 | ✅ Running |

Databases tạo sẵn: `identity_db`, `order_db`, `product_db`, `inventory_db`, `notification_db`

---

## Business Services

### product-service ✅ Port 8083 — Phase 1 Done

| Feature | Status |
|---------|--------|
| CRUD (Create, Read, Update, Delete) | ✅ |
| Pagination + filter by category | ✅ |
| `GET /api/products/batch?ids=...` | ✅ — order-service dùng endpoint này |
| Soft delete (active = false) | ✅ |
| MapStruct mapper | ✅ |
| Service / ServiceImpl pattern | ✅ |
| Eureka registration | ✅ |
| **Redis cache (Phase 3)** | ⏳ Chưa làm |

---

### order-service 🔄 Port 8083 — CRUD Done, Kafka đang làm

| Feature | Status | Ai làm |
|---------|--------|--------|
| Entity: Order, OrderItem, OrderStatus | ✅ | Done |
| DTO: CreateOrderRequest, OrderResponse | ✅ | Done |
| MapStruct mapper | ✅ | Done |
| Repository | ✅ | Done |
| OpenFeign → product-service | ✅ | Done |
| CRUD: createOrder, getById, getMyOrders, cancelOrder | ✅ | Done |
| `updateStatus()` idempotent (dùng cho Kafka consumer) | ✅ | Done |
| Event POJOs: OrderCreatedEvent, InventoryReservedEvent, ... | ✅ | Done |
| **KafkaTopicConfig** (khai báo topics) | 🔄 | **Bạn làm** |
| **OrderEventPublisher** (KafkaTemplate.send) | 🔄 | **Bạn làm** |
| **InventoryEventConsumer** (@KafkaListener) | 🔄 | **Bạn làm** |
| **Redis idempotency key (Phase 3)** | ⏳ | Chưa làm |

---

### notification-service ⏳ Chưa bắt đầu

| Feature | Status |
|---------|--------|
| Entity: Notification | ⏳ |
| Kafka Consumer: order-created, inventory-reserved/failed, order-cancelled | ⏳ |
| API: GET /notifications, PUT /{id}/read | ⏳ |
| Redis rate limiting + unread count | ⏳ |

---

### inventory-service ⏳ Chưa bắt đầu

| Feature | Status |
|---------|--------|
| Entity: Inventory (availableQuantity, reservedQuantity) | ⏳ |
| Kafka Consumer: order-created → check stock | ⏳ |
| Kafka Producer: inventory-reserved / inventory-failed | ⏳ |
| Redis Distributed Lock (chống oversell) | ⏳ |
| Redis cache tồn kho | ⏳ |

---

## Test Data ✅

Xem `test-data.md` — 5 users + 20 products đã tạo và test pass.

---

## Kafka Topics Cần Tạo

| Topic | Producer | Consumer |
|-------|----------|----------|
| `order-created` | order-service | inventory-service, notification-service |
| `order-cancelled` | order-service | inventory-service, notification-service |
| `inventory-reserved` | inventory-service | order-service, notification-service |
| `inventory-failed` | inventory-service | order-service, notification-service |

---

## Phase Roadmap

```
Phase 1 — Kafka cơ bản
  [x] Docker + infrastructure
  [x] product-service CRUD
  [x] order-service CRUD + OpenFeign
  [ ] order-service: publish order-created    ← đang làm
  [ ] notification-service: consume order-created

Phase 2 — Saga Pattern
  [ ] inventory-service: consume order-created → publish reserved/failed
  [ ] order-service: consume reserved/failed → update status
  [ ] notification-service: consume thêm reserved/failed

Phase 3 — Redis
  [ ] product-service: cache-aside
  [ ] order-service: idempotency key
  [ ] inventory-service: distributed lock + cache
  [ ] notification-service: rate limiting + unread count

Phase 4 — Production Concerns
  [ ] Dead Letter Topic
  [ ] Retry + backoff
  [ ] Circuit Breaker (Resilience4j)
  [ ] Monitoring
```

---

## Tech Decisions (đã chốt)

### Sync Communication — OpenFeign
- Dùng **OpenFeign** cho tất cả REST call giữa các service (order → product)
- Lý do: declarative interface, tự resolve Eureka, load balancing built-in, ít boilerplate
- Không dùng RestTemplate (deprecated Spring 5)
- Không dùng WebClient (reactive — chỉ cần khi chuyển sang WebFlux)
- Phase 4: thêm Resilience4j Circuit Breaker vào Feign bằng 1 annotation

### Deployment — AWS
- **Frontend (ReactJS):** S3 + CloudFront
- **Backend (microservices):** EC2 + Docker Compose (phù hợp learning project)
  - 1 EC2 `t3.medium/large` chạy tất cả service qua Docker Compose
  - RDS `t3.micro` cho PostgreSQL
  - ElastiCache `t3.micro` cho Redis
  - Kafka tự host trên EC2 (MSK quá đắt)
- **Việc cần làm trước khi deploy:**
  - Đóng gói từng service thành Docker image
  - Viết `docker-compose.prod.yml` với env variables
  - Config CORS cho API Gateway
  - HTTPS bằng ACM + Load Balancer

---

## Conventions

- **Service layer**: interface + `impl/XxxServiceImpl`
- **Mapping**: MapStruct (`@Mapper(componentModel = "spring")`)
- **No comments** trong code
- **Kafka key**: orderId làm partition key — đảm bảo ordering per order
- **Consumer**: luôn idempotent — check status trước khi update
- **userId**: không decode JWT trong service — đọc từ header `X-User-Id` do gateway inject
