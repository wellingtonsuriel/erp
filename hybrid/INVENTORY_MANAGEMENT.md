# World-Class Inventory Management System

## Overview

This document describes the enhanced inventory management system with proper separation of concerns, audit trail tracking, and inventory totals.

## Architecture Principles

### 1. **Separation of Concerns**
- **ShopInventory**: Stores metadata (prices, thresholds, suppliers, etc.) - one record per shop/product
- **InventoryTotal**: Maintains running total stock per shop/product
- **StockMovement**: Audit trail of all stock additions and reductions (creates new record for each operation)
- **ADD STOCK**: Creates StockMovement record and updates InventoryTotal
- **REDUCE STOCK**: Creates StockMovement record and updates InventoryTotal

### 2. **Data Integrity**
- `ShopInventory.quantity`: Configuration field for initial quantity
- `ShopInventory.inTransitQuantity`: Stock in transit from suppliers
- `InventoryTotal.totalstock`: **Current available stock** (increases with additions, decreases with reductions)
- `StockMovement`: **Complete audit trail** - new record for each stock operation

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

## Database Tables

### ShopInventory
Stores inventory metadata and configuration per shop/product.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `shop` | Shop | Reference to shop |
| `product` | Product | Reference to product |
| `supplier` | Supplier | Reference to supplier |
| `currency` | Currency | Price currency |
| `quantity` | Integer | Initial/configuration quantity |
| `inTransitQuantity` | Integer | Stock in transit |
| `unitPrice` | BigDecimal | Unit price |
| `expiryDate` | DateTime | Product expiry date |
| `reorderLevel` | Integer | Reorder trigger point |
| `minStock` | Integer | Minimum threshold |
| `maxStock` | Integer | Maximum stock limit |

### InventoryTotal
Maintains running total stock per shop/product.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `shop` | Shop | Reference to shop |
| `product` | Product | Reference to product |
| `totalstock` | Integer | **Current total stock** |
| `lastUpdated` | DateTime | Last update timestamp |
| `version` | Long | Optimistic locking version |

### StockMovement
Audit trail of all stock operations.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `shop` | Shop | Reference to shop |
| `product` | Product | Reference to product |
| `quantity` | Integer | Quantity added or reduced |
| `movementType` | Enum | ADDITION, REDUCTION, or ADJUSTMENT |
| `notes` | String | Optional notes |
| `createdAt` | DateTime | Operation timestamp |
| `createdBy` | String | User who performed operation |

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
  "totalStock": 50,  // From InventoryTotal table
  "inTransitQuantity": 0,
  "maxStock": 500
}
```

**Business Logic:**
1. Validates inventory doesn't already exist
2. Validates required fields (supplier, currency, unitPrice)
3. Validates initial stock doesn't exceed maxStock
4. Creates ShopInventory record
5. Creates InventoryTotal record with initial quantity
6. Creates StockMovement record (type: ADDITION) for audit trail
7. Returns 400 if inventory exists or validation fails

---

### Add Stock (Audit Trail Implementation)
```http
POST /api/shop-inventory/shop/{shopId}/product/{productId}/add-stock
Content-Type: application/json

{
  "quantity": 100,
  "notes": "Received from supplier XYZ"
}
```

**Response:**
```json
{
  "id": 1,
  "shopId": 1,
  "productId": 100,
  "totalstock": 150,    // Was 50, added 100
  "lastUpdated": "2026-01-09T12:00:00"
}
```

**Business Logic:**
1. Validates quantity is positive
2. Creates a new **StockMovement** record (audit trail):
   - quantity: 100
   - movementType: ADDITION
   - notes: "Received from supplier XYZ"
3. Acquires pessimistic lock on InventoryTotal
4. Updates InventoryTotal: `totalstock += 100`
5. Returns updated InventoryTotal

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

### Reduce Stock (Audit Trail Implementation)
```http
POST /api/shop-inventory/shop/{shopId}/product/{productId}/reduce-stock
Content-Type: application/json

{
  "quantity": 30,
  "notes": "Sold to customer"
}
```

**Response:**
```json
{
  "id": 1,
  "shopId": 1,
  "productId": 100,
  "totalstock": 120,    // Was 150, reduced by 30
  "lastUpdated": "2026-01-09T13:00:00"
}
```

**Business Logic:**
1. Validates quantity is positive
2. Validates sufficient stock available in InventoryTotal
3. Creates a new **StockMovement** record (audit trail):
   - quantity: 30
   - movementType: REDUCTION
   - notes: "Sold to customer"
4. Acquires pessimistic lock on InventoryTotal
5. Updates InventoryTotal: `totalstock -= 30`
6. Returns updated InventoryTotal

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
   Database Records Created:
   - ShopInventory: id=123, quantity=50, unitPrice=10.50, ...
   - InventoryTotal: id=1, totalstock=50
   - StockMovement: id=1, quantity=50, type=ADDITION, notes="Initial inventory creation"

2. Add Stock (First Purchase)
   POST /api/shop-inventory/shop/1/product/100/add-stock
   { "quantity": 100, "notes": "Purchase order #123" }
   Database Records:
   - ShopInventory: unchanged
   - InventoryTotal: totalstock=150 (was 50)
   - StockMovement: NEW RECORD id=2, quantity=100, type=ADDITION

3. Sell Products (Reduce Stock)
   POST /api/shop-inventory/shop/1/product/100/reduce-stock
   { "quantity": 30, "notes": "Sale #456" }
   Database Records:
   - ShopInventory: unchanged
   - InventoryTotal: totalstock=120 (was 150)
   - StockMovement: NEW RECORD id=3, quantity=30, type=REDUCTION

4. Add Stock (Second Purchase)
   POST /api/shop-inventory/shop/1/product/100/add-stock
   { "quantity": 50, "notes": "Purchase order #124" }
   Database Records:
   - ShopInventory: unchanged
   - InventoryTotal: totalstock=170 (was 120)
   - StockMovement: NEW RECORD id=4, quantity=50, type=ADDITION

5. Update Price (Metadata)
   PATCH /api/shop-inventory/shop/1/product/100
   { "unitPrice": 12.00 }
   Database Records:
   - ShopInventory: unitPrice updated to 12.00
   - InventoryTotal: unchanged
   - StockMovement: no new record
```

**Audit Trail (StockMovement table):**
| ID | Quantity | Type | Notes | Created At |
|----|----------|------|-------|------------|
| 1  | 50       | ADDITION | Initial inventory creation | 2026-01-09 10:00 |
| 2  | 100      | ADDITION | Purchase order #123 | 2026-01-09 11:00 |
| 3  | 30       | REDUCTION | Sale #456 | 2026-01-09 12:00 |
| 4  | 50       | ADDITION | Purchase order #124 | 2026-01-09 13:00 |

**Current State:**
- InventoryTotal.totalstock = 170 (current available stock)
- Total additions: 50 + 100 + 50 = 200
- Total reductions: 30
- Net stock: 200 - 30 = 170 ✓

---

## Database Schema

```sql
-- ShopInventory: Metadata and configuration
CREATE TABLE shop_inventories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  currency_id BIGINT NOT NULL,

  quantity INT NOT NULL DEFAULT 0,
  in_transit_quantity INT NOT NULL DEFAULT 0,

  unit_price DECIMAL(19,4) NOT NULL,
  expiry_date DATETIME,
  reorder_level INT,
  min_stock INT,
  max_stock INT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_shop_product (shop_id, product_id),
  INDEX idx_shop_product (shop_id, product_id)
);

-- InventoryTotal: Running totals per shop/product
CREATE TABLE inventory_total (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  totalstock INT NOT NULL DEFAULT 0,
  last_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,

  UNIQUE KEY uk_shop_product (shop_id, product_id),
  INDEX idx_totalstock (totalstock),
  CONSTRAINT fk_invtotal_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT fk_invtotal_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- StockMovement: Complete audit trail
CREATE TABLE stock_movements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  movement_type VARCHAR(20) NOT NULL,
  notes VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(255),

  INDEX idx_shop_product (shop_id, product_id),
  INDEX idx_created_at (created_at),
  INDEX idx_movement_type (movement_type),
  CONSTRAINT fk_stockmove_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT fk_stockmove_product FOREIGN KEY (product_id) REFERENCES products(id)
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

### 1. **Complete Audit Trail**
- **StockMovement** table creates a new record for every stock operation
- Never modified or deleted - complete historical record
- Tracks who performed operations, when, and why (via notes)
- Can reconstruct inventory state at any point in time

### 2. **Data Integrity**
- Separate tables for metadata (ShopInventory), totals (InventoryTotal), and audit (StockMovement)
- Validation ensures business rules are always enforced
- Thread-safe operations with pessimistic locking prevent race conditions
- Optimistic locking on InventoryTotal prevents lost updates

### 3. **Clear API Design**
- Intent-driven endpoints (create vs add-stock vs reduce-stock vs update)
- Self-documenting API structure
- Prevents misuse of endpoints
- Each operation creates appropriate audit records

### 4. **Performance**
- Pessimistic locking only where needed (InventoryTotal operations)
- Read-only queries don't acquire locks
- Indexed fields for fast lookups (shop_id, product_id, created_at)
- StockMovement inserts are fast (append-only, no updates)

### 5. **Scalability & Analytics**
- Separate audit table allows independent archival strategies
- Can query StockMovement for detailed analytics without impacting operational tables
- InventoryTotal provides fast current stock lookups
- ShopInventory metadata rarely changes, can be heavily cached

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
-- Current total stock across all shops
SELECT
  p.name as product_name,
  SUM(it.totalstock) as total_stock_all_shops
FROM inventory_total it
JOIN products p ON it.product_id = p.id
GROUP BY p.id, p.name;

-- Stock movement summary (audit analysis)
SELECT
  s.code as shop_code,
  p.name as product_name,
  SUM(CASE WHEN movement_type = 'ADDITION' THEN quantity ELSE 0 END) as total_additions,
  SUM(CASE WHEN movement_type = 'REDUCTION' THEN quantity ELSE 0 END) as total_reductions,
  SUM(CASE WHEN movement_type = 'ADDITION' THEN quantity ELSE -quantity END) as net_change
FROM stock_movements sm
JOIN shops s ON sm.shop_id = s.id
JOIN products p ON sm.product_id = p.id
GROUP BY s.code, p.name;

-- Recent stock movements (last 7 days)
SELECT
  sm.created_at,
  s.code as shop_code,
  p.name as product_name,
  sm.quantity,
  sm.movement_type,
  sm.notes
FROM stock_movements sm
JOIN shops s ON sm.shop_id = s.id
JOIN products p ON sm.product_id = p.id
WHERE sm.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY sm.created_at DESC;

-- Verify inventory integrity (should always match)
SELECT
  it.shop_id,
  it.product_id,
  it.totalstock as current_total,
  COALESCE(SUM(CASE WHEN sm.movement_type = 'ADDITION' THEN sm.quantity ELSE -sm.quantity END), 0) as calculated_total
FROM inventory_total it
LEFT JOIN stock_movements sm ON it.shop_id = sm.shop_id AND it.product_id = sm.product_id
GROUP BY it.shop_id, it.product_id, it.totalstock
HAVING current_total != calculated_total;

-- Products approaching max capacity
SELECT
  si.id,
  s.code as shop_code,
  p.name as product_name,
  it.totalstock as current_stock,
  si.max_stock,
  ROUND((it.totalstock * 100.0 / si.max_stock), 2) as capacity_percent
FROM shop_inventories si
JOIN shops s ON si.shop_id = s.id
JOIN products p ON si.product_id = p.id
JOIN inventory_total it ON si.shop_id = it.shop_id AND si.product_id = it.product_id
WHERE it.totalstock > (si.max_stock * 0.9)
ORDER BY capacity_percent DESC;

-- Products below reorder level
SELECT
  s.code as shop_code,
  p.name as product_name,
  it.totalstock as current_stock,
  si.reorder_level,
  si.min_stock
FROM shop_inventories si
JOIN shops s ON si.shop_id = s.id
JOIN products p ON si.product_id = p.id
JOIN inventory_total it ON si.shop_id = it.shop_id AND si.product_id = it.product_id
WHERE it.totalstock <= si.reorder_level
ORDER BY it.totalstock ASC;
```

---

## Support & Contact

For questions or issues, please contact the development team or create an issue in the project repository.

**Version:** 1.0
**Last Updated:** 2026-01-09
