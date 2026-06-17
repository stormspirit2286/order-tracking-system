# Test Data — Order Tracking System

> Generated: 2026-06-07 | Environment: localhost

---

## Users (5)

| # | Full Name | Email | Password | User ID | Role |
|---|-----------|-------|----------|---------|------|
| 1 | System Admin | admin@ordertracking.dev | Admin@123 | b641733f-1a99-49be-a262-b02a2f3cd7a4 | USER |
| 2 | Nguyen Van A | nguyenvana@gmail.com | Pass@123 | 6d95c793-d37c-475b-a05d-a1be8e432152 | USER |
| 3 | Tran Thi B | tranthib@gmail.com | Pass@123 | 18df1c62-b424-40c3-91c1-55450b009360 | USER |
| 4 | Le Hoang C | lehoangnc@gmail.com | Pass@123 | 089b21bd-d90f-4800-a405-ebc0fa118de1 | USER |
| 5 | Pham Thi D | phamthid@gmail.com | Pass@123 | 00f2a356-c9ee-4714-8af0-9139cf83cb6b | USER |

### Lấy token để test

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"nguyenvana@gmail.com","password":"Pass@123"}'
```

---

## Products (20)

### ELECTRONICS (9)

| Product ID | Name | Price (VND) |
|------------|------|-------------|
| c3fcaacc-1c0f-4072-9f30-b1576dba4946 | iPhone 15 Pro | 29,990,000 |
| 741882bd-06a7-4345-92d1-9eefde3a32c5 | Samsung Galaxy S24 | 22,990,000 |
| 91f5e8ef-17d3-45fe-8446-a291f6a1e276 | MacBook Air M3 | 32,990,000 |
| c7017dd9-ef67-4255-8421-64ae83e7054f | Sony WH-1000XM5 | 7,490,000 |
| d9ad02ab-1e34-42ba-bd0e-af61bc1d2553 | iPad Pro 12.9 | 27,990,000 |
| b1760adb-def8-4302-9d26-2bb5cf5ed7f7 | Ban Phim Co Keychron K2 Pro | 2,890,000 |
| 142e8802-0623-479a-af92-027325b70e47 | Chuot Logitech MX Master 3S | 1,990,000 |
| 63e65abb-1332-4483-954d-6ed73dfc9aa4 | Man Hinh Dell 27 4K USB-C | 12,990,000 |

### CLOTHING (4)

| Product ID | Name | Price (VND) |
|------------|------|-------------|
| 4500d746-9faf-4ea8-8fce-bc78923dd36f | Nike Air Max 270 | 3,290,000 |
| bc40b9a2-1f60-4e9d-860f-0585709afcf6 | Adidas Ultraboost 23 | 3,990,000 |
| d1fb7f94-512c-4d25-a75c-1d97cef8ba6a | Uniqlo Ultra Light Down Jacket | 1,490,000 |
| e0d3ee14-2e80-42a9-9040-cca83e361d57 | Levi 501 Original Jeans | 1,890,000 |

### BOOKS (4)

| Product ID | Name | Price (VND) |
|------------|------|-------------|
| 6b454df7-10d7-478d-adb5-578c8d6abc28 | Clean Code - Robert Martin | 350,000 |
| ea6d805a-8a3d-4d75-ac6f-ff10eca8c875 | Designing Data-Intensive Applications | 490,000 |
| 051d87cf-0830-4816-9dc4-2576aa3c41dd | System Design Interview Vol 1 | 420,000 |
| f24ace3c-21b8-4d88-9e04-38b131b11983 | Kafka: The Definitive Guide | 380,000 |

### FOOD (2)

| Product ID | Name | Price (VND) |
|------------|------|-------------|
| 6dc83009-0529-48b1-9871-a3d179993a11 | Ca Phe Trung Nguyen G7 | 89,000 |
| 50882b9f-26d2-4659-acb5-af5dfdf4911d | Nuoc Mam Chin Su 750ml | 45,000 |

### HOME (2)

| Product ID | Name | Price (VND) |
|------------|------|-------------|
| 8f82ed6b-28eb-458c-ac50-80368e36269b | Balo Laptop Samsonite 15.6 | 2,490,000 |
| 1b80bdb3-e256-480a-aadf-55c3bd86bd67 | Noi Chien Khong Dau Philips HD9270 | 3,290,000 |

---

## Flow Test Results

| Test | Endpoint | Expected | Result |
|------|----------|----------|--------|
| Register user | `POST /api/auth/register` | 200 + tokens | ✅ |
| Login | `POST /api/auth/login` | 200 + tokens | ✅ |
| List products (paginated) | `GET /api/products?page=0&size=5` | 20 total / 4 pages | ✅ |
| Filter by category | `GET /api/products?category=ELECTRONICS` | 8 items | ✅ |
| Batch fetch (for order-service) | `GET /api/products/batch?ids=...` | 3 items | ✅ |
| Protected route — no token | `GET /api/orders/my-orders` | 401 | ✅ |
| Protected route — valid token | `GET /api/orders/my-orders` | 503 (order-service not built) | ✅ |

---

## Quick curl snippets

```bash
# List products
curl http://localhost:8082/api/products?page=0&size=10

# Filter category
curl "http://localhost:8082/api/products?category=BOOKS"

# Batch (dùng cho order-service sau này)
curl "http://localhost:8082/api/products/batch?ids=c3fcaacc-1c0f-4072-9f30-b1576dba4946,741882bd-06a7-4345-92d1-9eefde3a32c5"

# Login và lấy token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"nguyenvana@gmail.com","password":"Pass@123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')

# Gọi protected route
curl http://localhost:8080/api/orders/my-orders -H "Authorization: Bearer $TOKEN"
```
