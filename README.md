# Hybrid POS/E-commerce ERP System

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Database Schema](#database-schema)
- [Modules](#modules)
- [Configuration](#configuration)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)

## Overview

A comprehensive Enterprise Resource Planning (ERP) system designed for hybrid retail operations, combining Point of Sale (POS) and e-commerce capabilities. The system provides robust multi-shop, multi-currency inventory management, sales tracking, financial reporting, and regulatory compliance (Zimbabwe ZIMRA).

**Version:** 0.0.1-SNAPSHOT
**Java Version:** 17
**Spring Boot Version:** 3.5.3

## Features

### Core Capabilities

#### Multi-Channel Sales
- **Point of Sale (POS)**: Full-featured retail POS with cashier management, sessions, and receipt printing
- **E-commerce Platform**: Online shopping with cart management and user accounts
- **Integrated Operations**: Unified inventory and order management across channels

#### Inventory Management
- Real-time stock tracking with audit trails
- Multi-shop inventory with transfer capabilities
- Automatic reorder notifications based on stock thresholds
- Expiry date tracking for perishable products
- Pessimistic and optimistic locking for data integrity
- See [INVENTORY_MANAGEMENT.md](hybrid/INVENTORY_MANAGEMENT.md) for detailed documentation

#### Multi-Currency Support
- Support for 20+ currencies (ISO 4217 compliant)
- Real-time exchange rate management
- Shop-level and order-level currency flexibility
- Automatic currency conversion

#### Dynamic Pricing
- Multiple price types (Retail, Wholesale, Bulk, Promotional)
- Time-bound pricing with effective date ranges
- Quantity-based bulk discounts
- Tax-inclusive pricing options

#### Financial Management
- Double-entry accounting system
- Multi-currency financial reporting
- Tax management (VAT, excise, levies, custom taxes)
- Zimbabwe ZIMRA fiscal compliance
- Automated fiscal device integration

#### Security & Access Control
- Spring Security integration
- Role-based access control (RBAC)
- Cashier-specific permission system
- Shop-based access restrictions
- User authentication and authorization

#### Reporting & Analytics
- Daily sales summaries
- Inventory reports (stock levels, aging, turnover)
- Financial statements
- Tax compliance reports
- Custom analytics queries

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.5.3
- **Language**: Java 17
- **ORM**: Spring Data JPA / Hibernate 6
- **Database**: MySQL 8.0+
- **Security**: Spring Security
- **API Documentation**: Springdoc OpenAPI 2.5.0 (Swagger UI)
- **Validation**: Jakarta Bean Validation
- **WebSocket**: Spring WebSocket for real-time updates

### Build & Development Tools
- **Build Tool**: Maven 3.8+
- **Code Generation**: Lombok 1.18.30
- **Database Driver**: MySQL Connector/J

### Key Dependencies
```xml
<dependencies>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-security</dependency>
    <dependency>spring-boot-starter-websocket</dependency>
    <dependency>spring-boot-starter-validation</dependency>
    <dependency>springdoc-openapi-starter-webmvc-ui</dependency>
    <dependency>mysql-connector-j</dependency>
    <dependency>lombok</dependency>
</dependencies>
```

## System Architecture

### Layered Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   REST API Layer                        │
│              (21 REST Controllers)                      │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                  Service Layer                          │
│            (19 Business Services)                       │
│         Transaction Management & Logic                  │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│              Data Access Layer                          │
│          (25+ Spring Data Repositories)                 │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                  Database Layer                         │
│                  (MySQL 8.0+)                           │
└─────────────────────────────────────────────────────────┘
```

### Package Structure

```
com.pos_onlineshop.hybrid/
├── HybridApplication.java          # Application entry point
├── config/                         # Spring configuration
├── controllers/                    # REST API endpoints (21 controllers)
├── services/                       # Business logic (19 services)
├── repositories/                   # Data access (25+ repositories)
├── dtos/                          # Data Transfer Objects (50+)
├── mappers/                       # Entity-DTO mappers
├── enums/                         # Business enumerations (20+)
├── exceptions/                    # Custom exception handling
└── [domain modules]/              # Domain-specific packages
    ├── accountancyEntry/
    ├── cart/ & cartItem/
    ├── cashier/ & cashierSessions/
    ├── currency/ & exchangeRate/
    ├── customers/
    ├── fiscalDevice/
    ├── inventory/ & inventoryTotal/
    ├── inventoryTransfer/ & inventoryTransferItems/
    ├── orders/ & orderLines/
    ├── products/
    ├── sales/
    ├── selling_price/
    ├── shop/ & shopInventory/
    ├── storedFiles/
    ├── suppliers/
    ├── tax/
    ├── userAccount/
    └── zimra/
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- MySQL 8.0 or higher
- Git

### Installation

1. **Clone the repository**
```bash
git clone <repository-url>
cd erp/hybrid
```

2. **Configure the database**

Create a MySQL database:
```sql
CREATE DATABASE pos_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. **Configure application properties**

Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pos_system
spring.datasource.username=root
spring.datasource.password=your_password
```

4. **Build the application**
```bash
./mvnw clean install
```

5. **Run the application**
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:9090`

### Verify Installation

- API Documentation: `http://localhost:9090/swagger-ui.html`
- Health Check: `http://localhost:9090/actuator/health` (if actuator is enabled)

## API Documentation

### Interactive API Documentation

Access the Swagger UI at: `http://localhost:9090/swagger-ui.html`

### Main API Endpoints

#### POS Operations
```
POST   /api/pos/quick-sale              - Quick sale transaction
GET    /api/pos/daily-summary           - Daily sales summary
POST   /api/pos/void-transaction        - Void a transaction
GET    /api/pos/receipt/{id}            - Get receipt
```

#### Order Management
```
POST   /api/orders                      - Create new order
GET    /api/orders                      - List orders (paginated)
GET    /api/orders/{id}                 - Get order details
PUT    /api/orders/{id}                 - Update order
DELETE /api/orders/{id}                 - Delete order
PATCH  /api/orders/{id}/status          - Update order status
```

#### Product Management
```
POST   /api/products                    - Create product
GET    /api/products                    - List products
GET    /api/products/{id}               - Get product
PUT    /api/products/{id}               - Update product
DELETE /api/products/{id}               - Delete product
GET    /api/products/barcode/{barcode}  - Search by barcode
GET    /api/products/availability       - Check availability
```

#### Inventory Management
```
POST   /api/shop-inventory              - Create inventory record
GET    /api/shop-inventory              - List inventory
POST   /api/shop-inventory/shop/{shopId}/product/{productId}/add-stock
                                        - Add stock (with audit trail)
POST   /api/shop-inventory/shop/{shopId}/product/{productId}/reduce-stock
                                        - Reduce stock (with audit trail)
PATCH  /api/shop-inventory/shop/{shopId}/product/{productId}
                                        - Update inventory metadata
GET    /api/shop-inventory/shop/{shopId}
                                        - Get shop inventory
GET    /api/shop-inventory/product/{productId}
                                        - Get product inventory across shops
```

#### Cashier Management
```
POST   /api/cashiers                    - Create cashier
POST   /api/cashiers/login              - Cashier login
POST   /api/cashiers/logout             - Cashier logout
GET    /api/cashiers/sessions/{id}      - Get cashier session
GET    /api/cashiers/{id}/permissions   - Get cashier permissions
```

#### Shop Management
```
POST   /api/shops                       - Create shop
GET    /api/shops                       - List shops
GET    /api/shops/{id}                  - Get shop details
PUT    /api/shops/{id}                  - Update shop
```

#### Customer Management
```
POST   /api/customers                   - Create customer
GET    /api/customers                   - List customers
GET    /api/customers/{id}              - Get customer
PUT    /api/customers/{id}              - Update customer
GET    /api/customers/{id}/loyalty      - Get loyalty points
```

#### Inventory Transfers
```
POST   /api/inventory-transfers         - Create transfer request
GET    /api/inventory-transfers         - List transfers
GET    /api/inventory-transfers/{id}    - Get transfer details
PATCH  /api/inventory-transfers/{id}/approve
                                        - Approve transfer
PATCH  /api/inventory-transfers/{id}/ship
                                        - Mark as shipped
PATCH  /api/inventory-transfers/{id}/receive
                                        - Receive transfer
PATCH  /api/inventory-transfers/{id}/cancel
                                        - Cancel transfer
```

#### Currency & Exchange Rates
```
POST   /api/currencies                  - Create currency
GET    /api/currencies                  - List currencies
POST   /api/exchange-rates              - Add exchange rate
GET    /api/exchange-rates/convert      - Convert currency
```

#### Pricing Management
```
POST   /api/selling-prices              - Create selling price
GET    /api/selling-prices/product/{id} - Get product prices
PUT    /api/selling-prices/{id}         - Update price
DELETE /api/selling-prices/{id}         - Delete price
```

#### Tax Management
```
POST   /api/taxes                       - Create tax rule
GET    /api/taxes                       - List taxes
GET    /api/taxes/{id}                  - Get tax details
PUT    /api/taxes/{id}                  - Update tax
```

#### Financial Reporting
```
GET    /api/accountancy/entries         - List accounting entries
POST   /api/accountancy/entries         - Create entry
GET    /api/accountancy/balance         - Get balance sheet
GET    /api/zimra/fiscalization/{id}    - Get ZIMRA records
POST   /api/zimra/submit                - Submit to ZIMRA
```

## Database Schema

### Core Entities

#### Products
```sql
products (
  id BIGINT PRIMARY KEY,
  sku VARCHAR(100) UNIQUE,
  barcode VARCHAR(100) UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  category VARCHAR(100),
  weight DECIMAL(10,3),
  min_stock INT,
  max_stock INT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

#### Shops
```sql
shops (
  id BIGINT PRIMARY KEY,
  code VARCHAR(50) UNIQUE,
  name VARCHAR(255) NOT NULL,
  address TEXT,
  phone VARCHAR(50),
  manager_id BIGINT,
  default_currency_id BIGINT,
  created_at TIMESTAMP
)
```

#### Inventory Management
```sql
shop_inventories (
  id BIGINT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  currency_id BIGINT NOT NULL,
  quantity INT DEFAULT 0,
  unit_price DECIMAL(19,4) NOT NULL,
  expiry_date DATETIME,
  reorder_level INT,
  min_stock INT,
  max_stock INT,
  created_at TIMESTAMP,
  UNIQUE KEY (shop_id, product_id)
)

inventory_total (
  id BIGINT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  totalstock INT NOT NULL DEFAULT 0,
  last_updated DATETIME NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY (shop_id, product_id)
)
```

#### Orders
```sql
orders (
  id BIGINT PRIMARY KEY,
  order_number VARCHAR(50) UNIQUE,
  shop_id BIGINT,
  customer_id BIGINT,
  currency_id BIGINT,
  status VARCHAR(50),
  total_amount DECIMAL(19,4),
  tax_amount DECIMAL(19,4),
  payment_method VARCHAR(50),
  sales_channel VARCHAR(50),
  created_at TIMESTAMP
)

order_lines (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(19,4),
  discount_amount DECIMAL(19,4),
  tax_amount DECIMAL(19,4),
  line_total DECIMAL(19,4)
)
```

#### Cashier System
```sql
cashiers (
  id BIGINT PRIMARY KEY,
  employee_id VARCHAR(50) UNIQUE,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  role VARCHAR(50),
  shop_id BIGINT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP
)

cashier_sessions (
  id BIGINT PRIMARY KEY,
  cashier_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  session_start TIMESTAMP,
  session_end TIMESTAMP,
  opening_cash DECIMAL(19,4),
  closing_cash DECIMAL(19,4),
  status VARCHAR(50)
)
```

### Key Enumerations

```java
OrderStatus: PENDING, PROCESSING, COMPLETED, CANCELLED, SHIPPED
PaymentMethod: CASH, CARD, CHECK, BANK_TRANSFER, CRYPTO, INSTALLMENT
SalesChannel: ONLINE, POS, MARKETPLACE
CashierRole: CASHIER, MANAGER, SUPERVISOR, ADMIN
TransferStatus: PENDING, APPROVED, SHIPPED, RECEIVED, CANCELLED
TaxNature: VAT, EXCISE, LEVY, STANDARD
PriceType: RETAIL, WHOLESALE, BULK, PROMOTIONAL
```

## Modules

### 1. Point of Sale (POS)
**Location**: `cashier/`, `cashierSessions/`, `cashierPermission/`
- Cashier management and authentication
- Session tracking with cash reconciliation
- Role-based permissions
- Quick sales and receipt printing
- Fiscal device integration

### 2. Inventory Management
**Location**: `inventory/`, `inventoryTotal/`, `shopInventory/`
- Real-time stock tracking
- Multi-shop inventory management
- Stock addition/reduction with audit trails
- Reorder level alerts
- See [INVENTORY_MANAGEMENT.md](hybrid/INVENTORY_MANAGEMENT.md)

### 3. Inventory Transfers
**Location**: `inventoryTransfer/`, `inventoryTransferItems/`
- Inter-shop stock transfers
- Approval workflows
- Transfer tracking (pending → approved → shipped → received)
- Priority management

### 4. Order Management
**Location**: `orders/`, `orderLines/`
- Multi-channel order processing
- Order lifecycle management
- Line item tracking
- Payment processing

### 5. Product Catalog
**Location**: `products/`, `selling_price/`
- Product master data
- Dynamic pricing strategies
- Multi-currency pricing
- Bulk discount management

### 6. Customer Management
**Location**: `customers/`, `userAccount/`
- Customer profiles
- Loyalty points
- Credit limit management
- Purchase history

### 7. Financial Management
**Location**: `accountancyEntry/`, `tax/`, `zimra/`
- Double-entry accounting
- Tax calculations and reporting
- ZIMRA fiscal compliance
- Financial reports

### 8. Shopping Cart
**Location**: `cart/`, `cartItem/`
- Online shopping cart
- Cart persistence
- Item management

### 9. Currency Management
**Location**: `currency/`, `exchangeRate/`
- Multi-currency support
- Exchange rate management
- Automatic conversion

### 10. Supplier Management
**Location**: `suppliers/`
- Supplier profiles
- Lead time tracking
- Payment terms management

## Configuration

### Application Properties

**Location**: `src/main/resources/application.properties`

```properties
# Application
spring.application.name=hybrid
server.port=9090

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/pos_system
spring.datasource.username=root
spring.datasource.password=

# JPA/Hibernate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update

# ZIMRA Fiscalisation
zimra.business.tin=12345678
zimra.business.name=POS Online Shop
zimra.business.address=Harare, Zimbabwe
zimra.tax.rate=15.0
zimra.auto-fiscalise=true
```

### Environment-Specific Configuration

For production, create `application-prod.properties`:
```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
logging.level.root=WARN
```

Run with: `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`

## Development

### Building the Project

```bash
# Clean build
./mvnw clean install

# Skip tests
./mvnw clean install -DskipTests

# Run specific test
./mvnw test -Dtest=ProductServiceTest
```

### Running the Application

```bash
# Standard run
./mvnw spring-boot:run

# With specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Debug mode
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### Code Style

- Follow Java naming conventions
- Use Lombok annotations for boilerplate code
- Document public APIs with JavaDoc
- Keep methods focused and under 50 lines
- Use meaningful variable and method names

### Testing

```bash
# Run all tests
./mvnw test

# Run integration tests
./mvnw verify

# Generate test coverage report
./mvnw jacoco:report
```

## Deployment

### Production Build

```bash
# Create production JAR
./mvnw clean package -Pprod

# JAR location
target/hybrid-0.0.1-SNAPSHOT.jar
```

### Running in Production

```bash
# Run JAR with production profile
java -jar target/hybrid-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# With custom properties
java -jar target/hybrid-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://prod-db:3306/pos_system \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=secure_password
```

### Docker Deployment

Create `Dockerfile`:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/hybrid-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t hybrid-erp:latest .
docker run -p 9090:9090 -e SPRING_PROFILES_ACTIVE=prod hybrid-erp:latest
```

### Database Migration

For production deployments:
1. Set `spring.jpa.hibernate.ddl-auto=validate`
2. Use database migration tools (Flyway or Liquibase)
3. Always backup before schema changes
4. Test migrations in staging first

## System Requirements

### Minimum Requirements
- CPU: 2 cores
- RAM: 4 GB
- Disk: 20 GB
- OS: Linux, Windows, or macOS
- Java: 17+
- MySQL: 8.0+

### Recommended Requirements
- CPU: 4+ cores
- RAM: 8+ GB
- Disk: 50+ GB SSD
- OS: Linux (Ubuntu 20.04+ or similar)
- Java: 17
- MySQL: 8.0+ (dedicated server)

## Troubleshooting

### Common Issues

**Connection refused to MySQL**
```bash
# Check MySQL is running
sudo systemctl status mysql

# Verify connection
mysql -u root -p -e "SELECT 1"
```

**Port 9090 already in use**
- Change port in application.properties: `server.port=8080`
- Or kill the process: `lsof -ti:9090 | xargs kill -9`

**Out of memory errors**
```bash
# Increase JVM heap
java -Xmx2g -jar target/hybrid-0.0.1-SNAPSHOT.jar
```

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/my-feature`
5. Submit a pull request

## License

[Specify license here]

## Contact & Support

For questions, issues, or contributions, please contact the development team or create an issue in the project repository.

## Additional Documentation

- [Inventory Management Guide](hybrid/INVENTORY_MANAGEMENT.md) - Detailed inventory system documentation
- API Documentation: `http://localhost:9090/swagger-ui.html`
- Database Schema: See [Database Schema](#database-schema) section above

---

**Version**: 0.0.1-SNAPSHOT
**Last Updated**: 2026-01-15
