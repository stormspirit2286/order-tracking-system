# AWS Deploy Progress — Order Tracking System

## Mục tiêu
Deploy hệ thống microservices Spring Boot lên AWS EC2 để học.
Chiến lược: **EC2 t3.medium + Docker Compose** (sau này chuyển ECS Fargate).

---

## Kiến trúc hệ thống

**7 Spring Boot services (Maven multi-module):**

| Service | Port | Vai trò |
|---|---|---|
| eureka-service | 8761 | Service discovery |
| config-service | 8888 | Centralized config |
| api-gateway | 8080 | Entry point, JWT filter |
| identity-service | 8081 | Auth: register, login, JWT |
| product-service | 8082 | CRUD sản phẩm |
| order-service | 8083 | CRUD đơn hàng + Kafka producer |
| inventory-service | 8084 | Kafka consumer + Redis distributed lock |

**Infrastructure (Docker):** PostgreSQL 16, Kafka (KRaft), Redis 7

**Tech stack:** Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1, Maven multi-module

---

## Những gì đã làm xong ✅

### 1. Dockerfile — tất cả 7 service
- Đặt tại: `{service-name}/Dockerfile`
- Pattern: multi-stage build (Maven builder → JRE runner)
- Builder image: `maven:3.9-eclipse-temurin-21-alpine`
- Runner image: `eclipse-temurin:21-jre-alpine`
- Build từ root: `docker build -f {service}/Dockerfile -t {service}:latest .`

**2 lỗi đã gặp và fix:**
- `mvn: not found` → phải dùng `maven:3.9-eclipse-temurin-21-alpine`, không phải `eclipse-temurin:21-jdk-alpine`
- `Child module does not exist` → Maven multi-module yêu cầu copy **tất cả** `pom.xml` của các module vào build context

### 2. docker-compose.prod.yml
- Đặt tại: root của project
- Tất cả service nói chuyện qua Docker network `backend` (dùng tên container thay `localhost`)
- Secrets đọc từ file `.env`
- Postgres có healthcheck, business services dùng `depends_on: condition: service_healthy`
- Không expose port nội bộ (postgres, redis, kafka) ra ngoài

### 3. File .env
- `.env.example` — template commit lên git
- `.env` — giá trị thật, đã thêm vào `.gitignore`

**Nội dung `.env` hiện tại (local test):**
```
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
JWT_SECRET_KEY=my-super-secret-key-for-jwt-token-must-be-at-least-256-bits-long-for-hs256
JWT_ACCESS_TOKEN_EXPIRATION=86400000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
```

### 4. Đã test chạy local thành công ✅
```bash
docker compose -f docker-compose.prod.yml build --parallel
docker compose -f docker-tracking-system/docker-compose.prod.yml up -d
```
- 10/10 container Up
- 4 service đăng ký Eureka: IDENTITY, PRODUCT, ORDER, INVENTORY
- API Gateway health: `{"status":"UP"}`

---

## Quy tắc env var Spring Boot (quan trọng)

Spring Boot đọc env var theo quy tắc: dấu `.` và `-` → `_`, toàn uppercase.

```
spring.datasource.url           → SPRING_DATASOURCE_URL
spring.datasource.username      → SPRING_DATASOURCE_USERNAME
spring.kafka.bootstrap-servers  → SPRING_KAFKA_BOOTSTRAP_SERVERS
spring.data.redis.host          → SPRING_DATA_REDIS_HOST
eureka.client.service-url.defaultZone → EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
jwt.secret-key                  → JWT_SECRET_KEY
```

---

## Cấu trúc file đã tạo

```
order-tracking-system/
├── eureka-service/Dockerfile
├── config-service/Dockerfile
├── api-gateway/Dockerfile
├── identity-service/Dockerfile
├── product-service/Dockerfile
├── order-service/Dockerfile
├── inventory-service/Dockerfile
├── docker-compose.yml          ← dev (chỉ infra: postgres, kafka, redis)
├── docker-compose.prod.yml     ← production (tất cả service)
├── .env                        ← secrets thật (gitignored)
└── .env.example                ← template
```

---

## Bước tiếp theo — Deploy lên EC2

### Đang làm: Bước 3 — Launch EC2

**Bước 1 ✅ — Tạo Key Pair**
- Name: `order-tracking-key`
- Type: RSA, format: `.pem`
- Lưu tại: `~/.ssh/order-tracking-key.pem`
- Phân quyền: `chmod 400 ~/.ssh/order-tracking-key.pem`

**Bước 2 ✅ — Tạo Security Group**
- Name: `order-tracking-sg`
- Inbound rules:
  - Port 22 (SSH) — My IP only
  - Port 8080 (API Gateway) — 0.0.0.0/0
  - Port 8761 (Eureka UI) — My IP only

**Bước 3 — Launch EC2 Instance** ← đang ở đây
- AMI: Ubuntu Server 22.04 LTS
- Instance type: t3.medium (4GB RAM)
- Key pair: order-tracking-key
- Security group: order-tracking-sg
- Storage: 20GB

**Bước 4 — SSH vào server và cài Docker**
```bash
ssh -i ~/.ssh/order-tracking-key.pem ubuntu@{EC2_PUBLIC_IP}

# Cài Docker
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu
newgrp docker

# Kiểm tra
docker --version
docker compose version
```

**Bước 5 — Copy project lên EC2**

Option A — Git clone (nếu code đã push lên GitHub):
```bash
git clone https://github.com/{username}/order-tracking-system.git
cd order-tracking-system
```

Option B — Copy thủ công bằng scp:
```bash
# Chạy từ máy local
scp -i ~/.ssh/order-tracking-key.pem -r \
  /path/to/order-tracking-system \
  ubuntu@{EC2_PUBLIC_IP}:~/order-tracking-system
```

**Bước 6 — Tạo .env trên server**
```bash
cd order-tracking-system
cp .env.example .env
nano .env   # điền giá trị thật (password mạnh hơn cho production)
```

**Bước 7 — Build và chạy**
```bash
docker compose -f docker-compose.prod.yml up --build -d
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

**Bước 8 — Smoke test**
```bash
# Từ máy local
curl http://{EC2_PUBLIC_IP}:8080/actuator/health
open http://{EC2_PUBLIC_IP}:8761
```

---

## Chi phí ước tính

- t3.medium: $0.047/giờ
- Chạy 4 giờ/ngày × 30 ngày: ~$5.6/tháng
- EBS 20GB: ~$2/tháng
- **Tổng: ~$8/tháng → $200 dùng được ~25 tháng**

**Quan trọng:** Stop EC2 khi không dùng — instance stopped chỉ tốn tiền EBS, không tốn compute.

---

## Roadmap học tiếp

```
EC2 + Docker Compose (đang làm)
    ↓ hiểu container, network, env vars, SSH, Linux
ECS Fargate
    ↓ thêm: ECR, Task Definition, ALB, IAM role
EKS (Kubernetes) — tương lai xa
```
