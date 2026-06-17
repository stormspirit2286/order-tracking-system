# Hướng dẫn Setup Infrastructure Services

## Yêu cầu

- Java 17+
- Maven 3.8+
- Docker Desktop (chạy PostgreSQL, Kafka, Redis)
- IntelliJ IDEA (Ultimate hoặc Community)

---

## Bước 1: Khởi động Docker Infrastructure

```bash
cd order-tracking-system

# Chạy PostgreSQL, Kafka, Redis, và UI tools
docker-compose up -d

# Kiểm tra tất cả đã running
docker-compose ps
```

**Sau khi chạy xong, truy cập:**
- Kafka UI: http://localhost:9091 (xem topics, messages, consumers)
- Redis Commander: http://localhost:8085 (xem Redis keys, values)
- PostgreSQL: localhost:5432 (user: postgres, pass: postgres)

---

## Bước 2: Mở project trong IntelliJ

1. Open IntelliJ → **File → Open** → chọn folder `order-tracking-system`
2. IntelliJ sẽ tự detect đây là Maven project, click **Trust Project**
3. Đợi Maven download dependencies (lần đầu mất vài phút)
4. Nếu IntelliJ không tự detect: **Right-click pom.xml → Add as Maven Project**

---

## Bước 3: Khởi động theo thứ tự

**THỨ TỰ RẤT QUAN TRỌNG — phải chạy đúng thứ tự:**

### 3.1 Eureka Service (chạy TRƯỚC TIÊN)
- Mở `eureka-service/src/main/java/.../EurekaServiceApplication.java`
- Click ▶ Run
- Đợi log: `Started EurekaServiceApplication`
- Truy cập: http://localhost:8761 (login: eureka / eureka123)
- Lúc này dashboard trống, chưa có service nào đăng ký

### 3.2 Config Service (chạy THỨ 2)
- Mở `config-service/src/main/java/.../ConfigServiceApplication.java`
- Click ▶ Run
- Đợi log: `Started ConfigServiceApplication`
- Test: http://localhost:8888/api-gateway/default → thấy config JSON
- Quay lại Eureka dashboard → thấy CONFIG-SERVICE đã đăng ký

### 3.3 API Gateway (chạy THỨ 3)
- Mở `api-gateway/src/main/java/.../ApiGatewayApplication.java`
- Click ▶ Run
- Đợi log: `Started ApiGatewayApplication`
- Quay lại Eureka dashboard → thấy API-GATEWAY đã đăng ký

### 3.4 Identity Service (chạy THỨ 4)
- Mở `identity-service/src/main/java/.../IdentityServiceApplication.java`
- Click ▶ Run
- Đợi log: `Started IdentityServiceApplication`
- Quay lại Eureka dashboard → thấy IDENTITY-SERVICE đã đăng ký

---

## Bước 4: Test Identity Service

Dùng Postman hoặc curl:

### Đăng ký user mới
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "123456",
    "fullName": "Test User",
    "phone": "0123456789"
  }'
```

**Response mong đợi:**
```json
{
  "code": 200,
  "message": "Registration successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": "abc-123-...",
    "email": "test@example.com",
    "fullName": "Test User",
    "role": "USER"
  }
}
```

### Đăng nhập
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "123456"
  }'
```

### Test JWT protected route (sẽ dùng khi có business services)
```bash
# Không có token → 401
curl http://localhost:8080/api/orders/my-orders

# Có token → forward xuống order-service (chưa có nên sẽ 503)
curl http://localhost:8080/api/orders/my-orders \
  -H "Authorization: Bearer <access-token-từ-login>"
```

---

## Cấu trúc project

```
order-tracking-system/
├── pom.xml                          ← Parent POM (quản lý version chung)
├── docker-compose.yml               ← PostgreSQL, Kafka, Redis
├── init-databases.sql               ← Tạo DB cho mỗi service
│
├── eureka-service/                  ← Port 8761
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../EurekaServiceApplication.java
│       ├── java/.../SecurityConfig.java
│       └── resources/application.yml
│
├── config-service/                  ← Port 8888
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../ConfigServiceApplication.java
│       └── resources/
│           ├── application.yml
│           └── configs/             ← Config files cho các service
│               ├── api-gateway.yml
│               └── identity-service.yml
│
├── api-gateway/                     ← Port 8080
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../ApiGatewayApplication.java
│       ├── java/.../config/JwtUtil.java
│       ├── java/.../config/SecurityConfig.java
│       ├── java/.../filter/JwtAuthenticationFilter.java
│       └── resources/application.yml
│
└── identity-service/                ← Port 8081
    ├── pom.xml
    └── src/main/
        ├── java/.../IdentityServiceApplication.java
        ├── java/.../config/SecurityConfig.java
        ├── java/.../controller/AuthController.java
        ├── java/.../dto/request/LoginRequest.java
        ├── java/.../dto/request/RegisterRequest.java
        ├── java/.../dto/request/RefreshTokenRequest.java
        ├── java/.../dto/response/ApiResponse.java
        ├── java/.../dto/response/AuthResponse.java
        ├── java/.../entity/User.java
        ├── java/.../exception/AppException.java
        ├── java/.../exception/GlobalExceptionHandler.java
        ├── java/.../repository/UserRepository.java
        ├── java/.../service/AuthService.java
        ├── java/.../service/JwtService.java
        └── resources/application.yml

Tiếp theo (Phase 1): product-service, order-service, inventory-service, notification-service
```

---

## Port Summary

| Service | Port | URL |
|---------|------|-----|
| Eureka Dashboard | 8761 | http://localhost:8761 |
| Config Service | 8888 | http://localhost:8888 |
| API Gateway | 8080 | http://localhost:8080 |
| Identity Service | 8081 | http://localhost:8081 |
| PostgreSQL | 5432 | localhost:5432 |
| Kafka | 9092 | localhost:9092 |
| Kafka UI | 9091 | http://localhost:9091 |
| Redis | 6379 | localhost:6379 |
| Redis Commander | 8085 | http://localhost:8085 |

---

## Troubleshooting

**Eureka: "Connection refused"**
→ Eureka chưa start xong, đợi thêm hoặc check port 8761

**Identity Service: "Could not connect to PostgreSQL"**
→ Docker chưa chạy hoặc database chưa tạo. Chạy `docker-compose up -d` và check log

**API Gateway: "503 Service Unavailable"**
→ Downstream service chưa start hoặc chưa đăng ký Eureka. Check Eureka dashboard

**Config Service: "Could not resolve placeholder"**
→ Config file chưa có trong `configs/` folder hoặc tên file không khớp tên service




-----
# Infrastructure Services Setup Guide — For Claude Code

> Hướng dẫn này dùng để Claude Code tự động tạo 3 infrastructure services còn lại.
> **eureka-service đã tạo xong và chạy được trên port 8761.**
> GroupId: `com.duy.ordertracking`
> Java: 21
> Spring Boot: 3.2.5, Spring Cloud: 2023.0.1

---

## THÔNG TIN CHUNG

### Parent pom.xml (đã có tại root `order-tracking-system/pom.xml`)

```xml
<groupId>com.duy.ordertracking</groupId>
<artifactId>order-tracking-system</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>

<properties>
    <java.version>21</java.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <jjwt.version>0.12.5</jjwt.version>
</properties>
```

### Quy tắc chung cho TẤT CẢ module

1. Mỗi module `pom.xml` kế thừa parent, KHÔNG tự khai báo `<properties>`, `<dependencyManagement>`, `<build>` — parent đã quản lý.
2. Mỗi module pom.xml có dạng gọn:

```xml
<parent>
    <groupId>com.duy.ordertracking</groupId>
    <artifactId>order-tracking-system</artifactId>
    <version>1.0.0</version>
</parent>
<artifactId>ten-service</artifactId>
<name>ten-service</name>
<dependencies>...</dependencies>
```

3. Dùng `application.yml` (KHÔNG dùng `application.properties`).
4. Sau khi tạo xong tất cả, cập nhật parent `pom.xml`:

```xml
<modules>
    <module>eureka-service</module>
    <module>config-service</module>
    <module>api-gateway</module>
    <module>identity-service</module>
</modules>
```

### Eureka connection (tất cả service cần đăng ký Eureka đều dùng block này)

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

## SERVICE 1: config-service

### 1.1 Tạo thư mục

```
config-service/
├── pom.xml
└── src/main/
    ├── java/com/duy/ordertracking/config/
    │   └── ConfigServiceApplication.java
    └── resources/
        ├── application.yml
        └── configs/
            ├── api-gateway.yml
            └── identity-service.yml
```

### 1.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.duy.ordertracking</groupId>
        <artifactId>order-tracking-system</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>config-service</artifactId>
    <name>config-service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 1.3 ConfigServiceApplication.java

```java
package com.duy.ordertracking.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
```

### 1.4 application.yml

```yaml
server:
  port: 8888

spring:
  application:
    name: config-service
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/configs

eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

### 1.5 configs/api-gateway.yml

```yaml
server:
  port: 8080
```

### 1.6 configs/identity-service.yml

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/identity_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

jwt:
  secret-key: my-super-secret-key-for-jwt-token-must-be-at-least-256-bits-long-for-hs256
  access-token-expiration: 86400000
  refresh-token-expiration: 604800000
```

### 1.7 Verify

- Chạy `ConfigServiceApplication`
- Truy cập http://localhost:8888/api-gateway/default → phải thấy JSON chứa config
- Kiểm tra Eureka dashboard http://localhost:8761 → CONFIG-SERVICE registered

---

## SERVICE 2: api-gateway

### 2.1 Tạo thư mục

```
api-gateway/
├── pom.xml
└── src/main/
    ├── java/com/duy/ordertracking/gateway/
    │   ├── ApiGatewayApplication.java
    │   ├── config/
    │   │   ├── JwtUtil.java
    │   │   └── SecurityConfig.java
    │   └── filter/
    │       └── JwtAuthenticationFilter.java
    └── resources/
        └── application.yml
```

### 2.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.duy.ordertracking</groupId>
        <artifactId>order-tracking-system</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>api-gateway</artifactId>
    <name>api-gateway</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 2.3 ApiGatewayApplication.java

```java
package com.duy.ordertracking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### 2.4 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: identity-service
          uri: lb://identity-service
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=0

        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
          filters:
            - StripPrefix=0

        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=0

        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          filters:
            - StripPrefix=0

        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
          filters:
            - StripPrefix=0

eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
  instance:
    prefer-ip-address: true

jwt:
  secret-key: my-super-secret-key-for-jwt-token-must-be-at-least-256-bits-long-for-hs256

app:
  security:
    public-paths:
      - /api/auth/register
      - /api/auth/login
      - /api/auth/refresh-token
      - /api/products/**
      - /actuator/**

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

### 2.5 config/JwtUtil.java

```java
package com.duy.ordertracking.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret-key}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }
}
```

### 2.6 config/SecurityConfig.java

**QUAN TRỌNG: Gateway dùng WebFlux, PHẢI dùng reactive security config.**

```java
package com.duy.ordertracking.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }
}
```

### 2.7 filter/JwtAuthenticationFilter.java

```java
package com.duy.ordertracking.gateway.filter;

import com.duy.ordertracking.gateway.config.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.security.public-paths}")
    private List<String> publicPaths;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid or expired JWT token for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);

        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .build();

        log.info("Authenticated user: {} with role: {} -> {}", userId, role, path);

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
```

### 2.8 Verify

- Chạy `ApiGatewayApplication` (eureka phải đang chạy)
- Eureka dashboard → API-GATEWAY registered
- `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

## SERVICE 3: identity-service

### 3.1 Tạo thư mục

```
identity-service/
├── pom.xml
└── src/main/
    ├── java/com/duy/ordertracking/identity/
    │   ├── IdentityServiceApplication.java
    │   ├── config/
    │   │   └── SecurityConfig.java
    │   ├── controller/
    │   │   └── AuthController.java
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── RegisterRequest.java
    │   │   │   └── RefreshTokenRequest.java
    │   │   └── response/
    │   │       ├── ApiResponse.java
    │   │       └── AuthResponse.java
    │   ├── entity/
    │   │   └── User.java
    │   ├── exception/
    │   │   ├── AppException.java
    │   │   └── GlobalExceptionHandler.java
    │   ├── repository/
    │   │   └── UserRepository.java
    │   └── service/
    │       ├── AuthService.java
    │       └── JwtService.java
    └── resources/
        └── application.yml
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.duy.ordertracking</groupId>
        <artifactId>order-tracking-system</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>identity-service</artifactId>
    <name>identity-service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 3.3 IdentityServiceApplication.java

```java
package com.duy.ordertracking.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```

### 3.4 application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: identity-service
  cloud:
    config:
      enabled: false
  datasource:
    url: jdbc:postgresql://localhost:5432/identity_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
  instance:
    prefer-ip-address: true

jwt:
  secret-key: my-super-secret-key-for-jwt-token-must-be-at-least-256-bits-long-for-hs256
  access-token-expiration: 86400000
  refresh-token-expiration: 604800000
```

### 3.5 entity/User.java

```java
package com.duy.ordertracking.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Role {
        USER, ADMIN
    }
}
```

### 3.6 dto/request/RegisterRequest.java

```java
package com.duy.ordertracking.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone is required")
    private String phone;
}
```

### 3.7 dto/request/LoginRequest.java

```java
package com.duy.ordertracking.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

### 3.8 dto/request/RefreshTokenRequest.java

```java
package com.duy.ordertracking.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

### 3.9 dto/response/ApiResponse.java

```java
package com.duy.ordertracking.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
```

### 3.10 dto/response/AuthResponse.java

```java
package com.duy.ordertracking.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String email;
    private String fullName;
    private String role;
}
```

### 3.11 repository/UserRepository.java

```java
package com.duy.ordertracking.identity.repository;

import com.duy.ordertracking.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

### 3.12 service/JwtService.java

```java
package com.duy.ordertracking.identity.service;

import com.duy.ordertracking.identity.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${jwt.secret-key}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("fullName", user.getFullName());

        return buildToken(claims, user.getId(), accessTokenExpiration);
    }

    public String generateRefreshToken(User user) {
        return buildToken(new HashMap<>(), user.getId(), refreshTokenExpiration);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

### 3.13 service/AuthService.java

```java
package com.duy.ordertracking.identity.service;

import com.duy.ordertracking.identity.dto.request.LoginRequest;
import com.duy.ordertracking.identity.dto.request.RefreshTokenRequest;
import com.duy.ordertracking.identity.dto.request.RegisterRequest;
import com.duy.ordertracking.identity.dto.response.AuthResponse;
import com.duy.ordertracking.identity.entity.User;
import com.duy.ordertracking.identity.exception.AppException;
import com.duy.ordertracking.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(User.Role.USER)
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!user.getActive()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }

        log.info("User logged in: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.validateToken(refreshToken)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        String userId = jwtService.extractUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        log.info("Token refreshed for user: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(jwtService.generateRefreshToken(user))
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
```

### 3.14 exception/AppException.java

```java
package com.duy.ordertracking.identity.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
```

### 3.15 exception/GlobalExceptionHandler.java

```java
package com.duy.ordertracking.identity.exception;

import com.duy.ordertracking.identity.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.error(e.getStatus().value(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(500, "Internal server error: " + e.getMessage()));
    }
}
```

### 3.16 controller/AuthController.java

```java
package com.duy.ordertracking.identity.controller;

import com.duy.ordertracking.identity.dto.request.LoginRequest;
import com.duy.ordertracking.identity.dto.request.RefreshTokenRequest;
import com.duy.ordertracking.identity.dto.request.RegisterRequest;
import com.duy.ordertracking.identity.dto.response.ApiResponse;
import com.duy.ordertracking.identity.dto.response.AuthResponse;
import com.duy.ordertracking.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }
}
```

### 3.17 config/SecurityConfig.java

```java
package com.duy.ordertracking.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 3.18 Prerequisite: PostgreSQL

Trước khi chạy identity-service, cần có PostgreSQL với database `identity_db`. Dùng Docker:

```bash
docker run -d \
  --name order-tracking-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine

# Tạo database
docker exec -it order-tracking-postgres psql -U postgres -c "CREATE DATABASE identity_db;"
```

Hoặc dùng docker-compose.yml đầy đủ (tạo tại root project):

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: order-tracking-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: order-tracking-kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,EXTERNAL://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    ports:
      - "9092:9092"
    volumes:
      - kafka_data:/var/lib/kafka/data

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: order-tracking-kafka-ui
    depends_on:
      - kafka
    ports:
      - "9091:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: order-tracking
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092

  redis:
    image: redis:7-alpine
    container_name: order-tracking-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: order-tracking-redis-ui
    depends_on:
      - redis
    ports:
      - "8085:8081"
    environment:
      REDIS_HOSTS: local:redis:6379

volumes:
  postgres_data:
  kafka_data:
  redis_data:
```

File `init-databases.sql` (tạo tại root project):

```sql
CREATE DATABASE identity_db;
CREATE DATABASE order_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE notification_db;
```

### 3.19 Verify

- PostgreSQL đang chạy với database `identity_db`
- Chạy `IdentityServiceApplication`
- Eureka dashboard → IDENTITY-SERVICE registered
- Test qua gateway:

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"123456","fullName":"Test User","phone":"0123456789"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"123456"}'
```

Cả 2 request phải trả về JSON chứa `accessToken`, `refreshToken`, `userId`, `email`, `fullName`, `role`.

---

## THỨ TỰ KHỞI ĐỘNG

1. `docker-compose up -d` (PostgreSQL, Kafka, Redis)
2. `eureka-service` (port 8761)
3. `config-service` (port 8888)
4. `api-gateway` (port 8080)
5. `identity-service` (port 8081)

## PORT SUMMARY

| Service | Port |
|---------|------|
| Eureka Dashboard | 8761 |
| Config Service | 8888 |
| API Gateway | 8080 |
| Identity Service | 8081 |
| PostgreSQL | 5432 |
| Kafka | 9092 |
| Kafka UI | 9091 |
| Redis | 6379 |
| Redis Commander | 8085 |