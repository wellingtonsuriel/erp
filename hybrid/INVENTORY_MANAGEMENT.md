# World-Class Inventory Management System

## Overview

This document describes the enhanced inventory management system with proper separation of concerns between creation and stock operations.

## Architecture Principles

### 1. **Separation of Concerns**
- **CREATE**: Strictly creates new inventory records only
- **UPDATE**: Modifies metadata (prices, thresholds, suppliers, etc.)
- **ADD STOCK**: Handles all stock increases with validation
- **REDUCE STOCK**: Handles stock decreases (sales, transfers)

### 2. **Data Integrity**
- `quantity`: Current available stock (can increase or decrease)
- `inTransitQuantity`: Stock in transit from suppliers
- `totalStock`: **NEW** - Cumulative lifetime stock additions (audit trail)

### 3. **Thread Safety**
- All stock operations use pessimistic locking
- Prevents race conditions in concurrent environments
- Atomic transactions for all critical operations

### 4. **Business Rules Validation**
- maxStock enforcement on creation and stock additions
- Required fields validation (supplier, currency, unitPrice)
- Positive quantity validation
- Inventory existence checks

---

## Key Fields

| Field | Type | Description | Behavior |
|-------|------|-------------|----------|
| `quantity` | Integer | Current available stock | Can increase (addStock) or decrease (reduceStock) |
| `inTransitQuantity` | Integer | Stock in transit | Updated via updateInTransitQuantity() |
| `totalStock` | Integer | **Lifetime cumulative additions** | Only increases, never decreases (audit trail) |
| `maxStock` | Integer | Maximum stock limit | Enforced on creation and addStock |
| `minStock` | Integer | Minimum threshold | For reorder alerts |
| `reorderLevel` | Integer | Reorder trigger point | For automated ordering |

---

## API Endpoints

### Create Inventory (Recommended)
```http
POST /api/shop-inventory
Content-Type: application/json

{
  "shopId": 1,
  "productId": 100,
  "supplierId": 5,
  "currencyId": 1,
  "quantity": 50,
  "inTransitQuantity": 0,
  "unitPrice": 10.50,
  "expiryDate": "2026-12-31T00:00:00",
  "reorderLevel": 10,
  "minStock": 5,
  "maxStock": 500
}
```

**Response:**
```json
{
  "id": 123,
  "shopId": 1,
  "shopCode": "SHOP001",
  "productId": 100,
  "productName": "Product A",
  "quantity": 50,
  "totalStock": 50,  // Initialized with initial quantity
  "inTransitQuantity": 0,
  "maxStock": 500
}
```

**Business Logic:**
1. Validates inventory doesn't already exist
2. Validates required fields (supplier, currency, unitPrice)
3. Validates initial stock doesn't exceed maxStock
4. Initializes `totalStock = quantity`
5. Returns 400 if inventory exists or validation fails

---

### Add Stock (World-Class Implementation)
```http
POST /api/shop-inventory/shop/{shopId}/product/{productId}/add-stock
Content-Type: application/json

{
  "quantity": 100
}
```

**Response:**
```json
{
  "id": 123,
  "shopId": 1,
  "productId": 100,
  "quantity": 150,      // Was 50, added 100
  "totalStock": 150,    // Was 50, added 100
  "maxStock": 500
}
```

**Business Logic:**
1. Acquires pessimistic lock for thread safety
2. Validates quantity is positive
3. Validates inventory exists (returns 400 if not)
4. Validates new quantity won't exceed maxStock
5. Updates `quantity += additionalQuantity`
6. Updates `totalStock += additionalQuantity`
7. Logs detailed audit trail

**Example Validation Error:**
```
Request: Add 400 to inventory with quantity=150, maxStock=500
Result: 400 Bad Request
Reason: 150 + 400 = 550 > 500 (maxStock)
```

---

### Update Metadata
```http
PATCH /api/shop-inventory/shop/{shopId}/product/{productId}
Content-Type: application/json

{
  "unitPrice": 12.00,
  "maxStock": 600,
  "reorderLevel": 15
}
```

**Note:** This endpoint updates metadata only. Use `addStock` for stock increases.

---

### Reduce Stock
```http
POST /api/shop-inventory/shop/{shopId}/product/{productId}/reduce-stock
Content-Type: application/json

{
  "quantity": 30
}
```

**Note:** This decreases `quantity` but does NOT affect `totalStock` (audit trail).

---

## Usage Flow

### Scenario: Creating and Managing Inventory

```
1. Create Inventory
   POST /api/shop-inventory
   {
     "shopId": 1,
     "productId": 100,
     "quantity": 50,
     "maxStock": 500,
     ...
   }
   Result: quantity=50, totalStock=50

2. Add Stock (First Purchase)
   POST /api/shop-inventory/shop/1/product/100/add-stock
   { "quantity": 100 }
   Result: quantity=150, totalStock=150

3. Sell Products (Reduce Stock)
   POST /api/shop-inventory/shop/1/product/100/reduce-stock
   { "quantity": 30 }
   Result: quantity=120, totalStock=150 (unchanged!)

4. Add Stock (Second Purchase)
   POST /api/shop-inventory/shop/1/product/100/add-stock
   { "quantity": 50 }
   Result: quantity=170, totalStock=200

5. Update Price
   PATCH /api/shop-inventory/shop/1/product/100
   { "unitPrice": 12.00 }
   Result: quantity=170, totalStock=200 (unchanged)
```

**Audit Trail:**
- `quantity` = 170 (current available stock)
- `totalStock` = 200 (50 + 100 + 50 = total ever added)
- This shows: 200 total added, 30 sold, 170 remaining

---

## Database Schema

```sql
CREATE TABLE shop_inventories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  currency_id BIGINT NOT NULL,

  quantity INT NOT NULL DEFAULT 0,
  in_transit_quantity INT NOT NULL DEFAULT 0,
  total_stock INT NOT NULL DEFAULT 0,  -- NEW FIELD

  unit_price DECIMAL(19,4) NOT NULL,
  expiry_date DATETIME,
  reorder_level INT,
  min_stock INT,
  max_stock INT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_shop_product (shop_id, product_id),
  INDEX idx_total_stock (total_stock)
);
```

---

## Migration Guide

### For Existing Data
Run the migration script:
```sql
-- Add column
ALTER TABLE shop_inventories
ADD COLUMN total_stock INT NOT NULL DEFAULT 0;

-- Initialize with current quantity
UPDATE shop_inventories
SET total_stock = quantity
WHERE total_stock = 0;
```

### For Spring Boot (Auto-DDL)
The application uses `spring.jpa.hibernate.ddl-auto=update`, so the column will be automatically created on next startup.

---

## Benefits of This Design

### 1. **Audit Trail**
- `totalStock` provides lifetime tracking of all stock additions
- Never decreases, only increases
- Helps identify discrepancies between expected and actual stock

### 2. **Data Integrity**
- Separate create and update operations prevent accidental data overwrites
- Validation ensures business rules are always enforced
- Thread-safe operations prevent race conditions

### 3. **Clear API Design**
- Intent-driven endpoints (create vs add-stock vs update)
- Self-documenting API structure
- Prevents misuse of endpoints

### 4. **Performance**
- Pessimistic locking only where needed (stock operations)
- Read-only queries don't acquire locks
- Indexed totalStock for analytics queries

### 5. **Scalability**
- Thread-safe design supports concurrent operations
- Atomic transactions prevent partial updates
- Proper separation allows independent scaling of read/write operations

---

## Error Handling

### Common Errors

| Error | HTTP Status | Cause | Solution |
|-------|-------------|-------|----------|
| Inventory already exists | 400 | Trying to create duplicate | Use addStock or update endpoints |
| Inventory not found | 400 | Stock operation on non-existent inventory | Create inventory first |
| Exceeds maxStock | 400 | Adding stock beyond limit | Adjust maxStock or reduce quantity |
| Invalid quantity | 400 | Negative or zero quantity | Use positive numbers |
| Missing required field | 400 | Supplier/currency/price missing | Provide all required fields |

---

## Best Practices

### DO ✅
- Use `POST /api/shop-inventory` for creating new inventory
- Use `addStock` endpoint for all stock increases
- Use `PATCH` endpoint for metadata updates only
- Set reasonable `maxStock` limits
- Monitor `totalStock` for audit purposes

### DON'T ❌
- Don't use the legacy endpoint (deprecated)
- Don't update quantity via PATCH endpoint
- Don't bypass maxStock validation
- Don't create inventory without required fields
- Don't manually modify totalStock

---

## Monitoring & Analytics

### Key Metrics to Track

```sql
-- Total stock added across all products
SELECT SUM(total_stock) FROM shop_inventories;

-- Current available vs total added (shrinkage analysis)
SELECT
  product_id,
  quantity as current_stock,
  total_stock as lifetime_added,
  (total_stock - quantity) as total_sold_or_lost
FROM shop_inventories;

-- Products approaching max capacity
SELECT * FROM shop_inventories
WHERE quantity > (max_stock * 0.9);

-- Products below reorder level
SELECT * FROM shop_inventories
WHERE quantity <= reorder_level;
```

---

## Support & Contact

For questions or issues, please contact the development team or create an issue in the project repository.

**Version:** 1.0
**Last Updated:** 2026-01-09
