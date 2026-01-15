# API Reference Guide

## Table of Contents
- [Authentication](#authentication)
- [POS Operations](#pos-operations)
- [Order Management](#order-management)
- [Product Management](#product-management)
- [Inventory Management](#inventory-management)
- [Cashier Management](#cashier-management)
- [Customer Management](#customer-management)
- [Shop Management](#shop-management)
- [Currency Management](#currency-management)
- [Pricing Management](#pricing-management)
- [Tax Management](#tax-management)
- [Inventory Transfers](#inventory-transfers)
- [Financial Operations](#financial-operations)
- [Error Responses](#error-responses)

## Base URL

```
http://localhost:9090/api
```

For Swagger UI documentation:
```
http://localhost:9090/swagger-ui.html
```

## Authentication

The system uses Spring Security for authentication. Include authentication headers with your requests.

```http
Authorization: Bearer <token>
```

---

## POS Operations

### Quick Sale

Creates a quick sale transaction for POS operations.

**Endpoint**: `POST /api/pos/quick-sale`

**Request Body**:
```json
{
  "cashierId": 1,
  "shopId": 1,
  "items": [
    {
      "productId": 100,
      "quantity": 2,
      "unitPrice": 15.50
    },
    {
      "productId": 101,
      "quantity": 1,
      "unitPrice": 25.00
    }
  ],
  "paymentMethod": "CASH",
  "currencyId": 1,
  "amountTendered": 60.00,
  "customerTin": "12345678" // Optional for ZIMRA
}
```

**Response** (201 Created):
```json
{
  "saleId": 450,
  "orderNumber": "POS-2026-001450",
  "shopId": 1,
  "cashierId": 1,
  "totalAmount": 56.00,
  "taxAmount": 8.40,
  "grandTotal": 64.40,
  "amountTendered": 60.00,
  "change": -4.40,
  "paymentMethod": "CASH",
  "timestamp": "2026-01-15T14:30:00",
  "receiptNumber": "REC-450",
  "fiscalReceipt": {
    "verified": true,
    "fiscalCode": "ZIMRA-450-2026"
  }
}
```

### Get Daily Summary

Retrieves sales summary for a cashier session.

**Endpoint**: `GET /api/pos/daily-summary`

**Query Parameters**:
- `cashierId` (required): Cashier ID
- `date` (optional): Date in format YYYY-MM-DD (defaults to today)

**Example Request**:
```http
GET /api/pos/daily-summary?cashierId=1&date=2026-01-15
```

**Response** (200 OK):
```json
{
  "cashierId": 1,
  "cashierName": "John Doe",
  "date": "2026-01-15",
  "totalSales": 5450.00,
  "totalTransactions": 45,
  "averageTransactionValue": 121.11,
  "paymentMethodBreakdown": {
    "CASH": 3200.00,
    "CARD": 2250.00
  },
  "taxCollected": 817.50,
  "openingCash": 500.00,
  "closingCash": 3700.00,
  "expectedCash": 3700.00,
  "variance": 0.00
}
```

### Void Transaction

Cancels a completed transaction.

**Endpoint**: `POST /api/pos/void-transaction`

**Request Body**:
```json
{
  "transactionId": 450,
  "reason": "Customer request",
  "managerApprovalCode": "MGR-123"
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "transactionId": 450,
  "voidedAt": "2026-01-15T15:00:00",
  "message": "Transaction voided successfully",
  "refundAmount": 64.40
}
```

---

## Order Management

### Create Order

Creates a new order (online or POS).

**Endpoint**: `POST /api/orders`

**Request Body**:
```json
{
  "shopId": 1,
  "customerId": 25,
  "currencyId": 1,
  "salesChannel": "ONLINE",
  "paymentMethod": "CARD",
  "orderLines": [
    {
      "productId": 100,
      "quantity": 3,
      "unitPrice": 15.50,
      "discountPercent": 10.0
    },
    {
      "productId": 105,
      "quantity": 1,
      "unitPrice": 89.99
    }
  ],
  "shippingAddress": {
    "street": "123 Main St",
    "city": "Harare",
    "postalCode": "00263",
    "country": "Zimbabwe"
  },
  "notes": "Please deliver after 2 PM"
}
```

**Response** (201 Created):
```json
{
  "id": 890,
  "orderNumber": "ORD-2026-000890",
  "shopId": 1,
  "customerId": 25,
  "customerName": "Jane Smith",
  "status": "PENDING",
  "salesChannel": "ONLINE",
  "currencyCode": "USD",
  "subtotal": 131.94,
  "discountAmount": 4.65,
  "taxAmount": 19.09,
  "shippingFee": 5.00,
  "totalAmount": 151.38,
  "paymentMethod": "CARD",
  "paymentStatus": "PENDING",
  "createdAt": "2026-01-15T10:30:00",
  "orderLines": [
    {
      "id": 1450,
      "productId": 100,
      "productName": "Product A",
      "quantity": 3,
      "unitPrice": 15.50,
      "discountAmount": 4.65,
      "taxAmount": 6.28,
      "lineTotal": 41.85
    },
    {
      "id": 1451,
      "productId": 105,
      "productName": "Product B",
      "quantity": 1,
      "unitPrice": 89.99,
      "discountAmount": 0.00,
      "taxAmount": 13.50,
      "lineTotal": 103.49
    }
  ]
}
```

### List Orders

Retrieves paginated list of orders with optional filtering.

**Endpoint**: `GET /api/orders`

**Query Parameters**:
- `page` (optional, default: 0): Page number
- `size` (optional, default: 20): Page size
- `status` (optional): Filter by OrderStatus
- `shopId` (optional): Filter by shop
- `customerId` (optional): Filter by customer
- `fromDate` (optional): Start date (YYYY-MM-DD)
- `toDate` (optional): End date (YYYY-MM-DD)
- `salesChannel` (optional): Filter by channel

**Example Request**:
```http
GET /api/orders?page=0&size=10&status=PENDING&shopId=1
```

**Response** (200 OK):
```json
{
  "content": [
    {
      "id": 890,
      "orderNumber": "ORD-2026-000890",
      "customerName": "Jane Smith",
      "status": "PENDING",
      "totalAmount": 151.38,
      "createdAt": "2026-01-15T10:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Update Order Status

Updates the status of an order.

**Endpoint**: `PATCH /api/orders/{id}/status`

**Request Body**:
```json
{
  "status": "PROCESSING",
  "notes": "Order confirmed and being prepared"
}
```

**Response** (200 OK):
```json
{
  "id": 890,
  "orderNumber": "ORD-2026-000890",
  "status": "PROCESSING",
  "updatedAt": "2026-01-15T11:00:00",
  "statusHistory": [
    {
      "status": "PENDING",
      "timestamp": "2026-01-15T10:30:00"
    },
    {
      "status": "PROCESSING",
      "timestamp": "2026-01-15T11:00:00"
    }
  ]
}
```

---

## Product Management

### Create Product

Creates a new product in the catalog.

**Endpoint**: `POST /api/products`

**Request Body**:
```json
{
  "sku": "PROD-A-001",
  "barcode": "1234567890123",
  "name": "Premium Widget",
  "description": "High-quality widget for professional use",
  "category": "Electronics",
  "weight": 1.5,
  "minStock": 10,
  "maxStock": 1000,
  "attributes": {
    "color": "Blue",
    "size": "Medium",
    "warranty": "2 years"
  }
}
```

**Response** (201 Created):
```json
{
  "id": 150,
  "sku": "PROD-A-001",
  "barcode": "1234567890123",
  "name": "Premium Widget",
  "description": "High-quality widget for professional use",
  "category": "Electronics",
  "weight": 1.5,
  "minStock": 10,
  "maxStock": 1000,
  "isActive": true,
  "createdAt": "2026-01-15T09:00:00",
  "updatedAt": "2026-01-15T09:00:00"
}
```

### Search Product by Barcode

Retrieves product by barcode (useful for POS scanning).

**Endpoint**: `GET /api/products/barcode/{barcode}`

**Example Request**:
```http
GET /api/products/barcode/1234567890123
```

**Response** (200 OK):
```json
{
  "id": 150,
  "sku": "PROD-A-001",
  "barcode": "1234567890123",
  "name": "Premium Widget",
  "category": "Electronics",
  "availableStock": 450,
  "currentPrice": 15.50,
  "taxIncluded": false
}
```

### Check Product Availability

Checks stock availability across shops.

**Endpoint**: `GET /api/products/{id}/availability`

**Query Parameters**:
- `shopId` (optional): Specific shop, omit for all shops

**Example Request**:
```http
GET /api/products/150/availability?shopId=1
```

**Response** (200 OK):
```json
{
  "productId": 150,
  "productName": "Premium Widget",
  "availability": [
    {
      "shopId": 1,
      "shopName": "Main Store",
      "availableStock": 450,
      "reorderLevel": 100,
      "isLowStock": false,
      "lastUpdated": "2026-01-15T08:00:00"
    }
  ],
  "totalAvailableStock": 450
}
```

---

## Inventory Management

See [INVENTORY_MANAGEMENT.md](hybrid/INVENTORY_MANAGEMENT.md) for comprehensive documentation.

### Create Inventory Record

Creates a new inventory record for a product in a shop.

**Endpoint**: `POST /api/shop-inventory`

**Request Body**:
```json
{
  "shopId": 1,
  "productId": 150,
  "supplierId": 5,
  "currencyId": 1,
  "quantity": 100,
  "unitPrice": 12.50,
  "expiryDate": "2027-12-31T00:00:00",
  "reorderLevel": 20,
  "minStock": 10,
  "maxStock": 500
}
```

**Response** (201 Created):
```json
{
  "id": 245,
  "shopId": 1,
  "shopCode": "MAIN-001",
  "productId": 150,
  "productName": "Premium Widget",
  "supplierId": 5,
  "supplierName": "Acme Suppliers",
  "currencyCode": "USD",
  "quantity": 100,
  "totalStock": 100,
  "unitPrice": 12.50,
  "expiryDate": "2027-12-31T00:00:00",
  "reorderLevel": 20,
  "minStock": 10,
  "maxStock": 500,
  "createdAt": "2026-01-15T10:00:00"
}
```

### Add Stock

Adds stock to existing inventory (creates audit trail).

**Endpoint**: `POST /api/shop-inventory/shop/{shopId}/product/{productId}/add-stock`

**Request Body**:
```json
{
  "quantity": 50,
  "notes": "Received from Purchase Order #12345"
}
```

**Response** (200 OK):
```json
{
  "id": 78,
  "shopId": 1,
  "productId": 150,
  "totalstock": 150,
  "previousStock": 100,
  "addedQuantity": 50,
  "lastUpdated": "2026-01-15T11:30:00"
}
```

### Reduce Stock

Reduces stock (for sales, damages, etc.).

**Endpoint**: `POST /api/shop-inventory/shop/{shopId}/product/{productId}/reduce-stock`

**Request Body**:
```json
{
  "quantity": 25,
  "reason": "SALE",
  "notes": "Sold via Order #890"
}
```

**Response** (200 OK):
```json
{
  "id": 78,
  "shopId": 1,
  "productId": 150,
  "totalstock": 125,
  "previousStock": 150,
  "reducedQuantity": 25,
  "lastUpdated": "2026-01-15T12:00:00"
}
```

**Error Response** (400 Bad Request):
```json
{
  "error": "INSUFFICIENT_STOCK",
  "message": "Cannot reduce stock by 200. Available stock: 125",
  "availableStock": 125,
  "requestedQuantity": 200
}
```

### Update Inventory Metadata

Updates inventory metadata (prices, thresholds) without affecting stock.

**Endpoint**: `PATCH /api/shop-inventory/shop/{shopId}/product/{productId}`

**Request Body**:
```json
{
  "unitPrice": 13.50,
  "maxStock": 600,
  "reorderLevel": 25
}
```

**Response** (200 OK):
```json
{
  "id": 245,
  "shopId": 1,
  "productId": 150,
  "unitPrice": 13.50,
  "maxStock": 600,
  "reorderLevel": 25,
  "updatedAt": "2026-01-15T13:00:00"
}
```

---

## Cashier Management

### Create Cashier

Creates a new cashier account.

**Endpoint**: `POST /api/cashiers`

**Request Body**:
```json
{
  "employeeId": "EMP-001",
  "name": "John Doe",
  "email": "john.doe@example.com",
  "phone": "+263771234567",
  "role": "CASHIER",
  "shopId": 1,
  "pin": "1234"
}
```

**Response** (201 Created):
```json
{
  "id": 10,
  "employeeId": "EMP-001",
  "name": "John Doe",
  "email": "john.doe@example.com",
  "role": "CASHIER",
  "shopId": 1,
  "shopName": "Main Store",
  "isActive": true,
  "createdAt": "2026-01-15T08:00:00"
}
```

### Cashier Login

Authenticates cashier and starts a session.

**Endpoint**: `POST /api/cashiers/login`

**Request Body**:
```json
{
  "employeeId": "EMP-001",
  "pin": "1234",
  "shopId": 1,
  "openingCash": 500.00
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "sessionId": 450,
  "cashierId": 10,
  "cashierName": "John Doe",
  "shopId": 1,
  "sessionStart": "2026-01-15T08:00:00",
  "openingCash": 500.00,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Cashier Logout

Ends cashier session with cash reconciliation.

**Endpoint**: `POST /api/cashiers/logout`

**Request Body**:
```json
{
  "sessionId": 450,
  "closingCash": 3750.00,
  "notes": "End of shift"
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "sessionId": 450,
  "sessionEnd": "2026-01-15T18:00:00",
  "openingCash": 500.00,
  "closingCash": 3750.00,
  "expectedCash": 3700.00,
  "variance": 50.00,
  "totalSales": 3200.00,
  "transactionCount": 32
}
```

---

## Customer Management

### Create Customer

Creates a new customer record.

**Endpoint**: `POST /api/customers`

**Request Body**:
```json
{
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "phone": "+263771234567",
  "taxId": "TIN-12345678",
  "address": {
    "street": "123 Main St",
    "city": "Harare",
    "postalCode": "00263",
    "country": "Zimbabwe"
  },
  "creditLimit": 10000.00,
  "loyaltyTier": "GOLD"
}
```

**Response** (201 Created):
```json
{
  "id": 125,
  "customerId": "CUST-000125",
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "phone": "+263771234567",
  "taxId": "TIN-12345678",
  "loyaltyPoints": 0,
  "loyaltyTier": "GOLD",
  "creditLimit": 10000.00,
  "currentBalance": 0.00,
  "createdAt": "2026-01-15T09:00:00"
}
```

### Get Customer Loyalty Points

Retrieves customer loyalty information.

**Endpoint**: `GET /api/customers/{id}/loyalty`

**Example Request**:
```http
GET /api/customers/125/loyalty
```

**Response** (200 OK):
```json
{
  "customerId": 125,
  "customerName": "Jane Smith",
  "loyaltyPoints": 1250,
  "loyaltyTier": "GOLD",
  "tierBenefits": {
    "discountPercent": 10.0,
    "pointsMultiplier": 2.0,
    "freeShipping": true
  },
  "pointsToNextTier": 2750,
  "nextTier": "PLATINUM",
  "pointsHistory": [
    {
      "date": "2026-01-10",
      "points": 500,
      "reason": "Purchase - Order #850"
    },
    {
      "date": "2026-01-12",
      "points": 750,
      "reason": "Purchase - Order #870"
    }
  ]
}
```

---

## Shop Management

### Create Shop

Creates a new shop/warehouse location.

**Endpoint**: `POST /api/shops`

**Request Body**:
```json
{
  "code": "BRANCH-02",
  "name": "Downtown Branch",
  "address": "456 Commerce Ave, Harare, Zimbabwe",
  "phone": "+263771111111",
  "email": "downtown@example.com",
  "managerId": 5,
  "defaultCurrencyId": 1,
  "shopType": "RETAIL",
  "operatingHours": {
    "monday": "08:00-18:00",
    "tuesday": "08:00-18:00",
    "wednesday": "08:00-18:00",
    "thursday": "08:00-18:00",
    "friday": "08:00-20:00",
    "saturday": "09:00-17:00",
    "sunday": "Closed"
  }
}
```

**Response** (201 Created):
```json
{
  "id": 5,
  "code": "BRANCH-02",
  "name": "Downtown Branch",
  "address": "456 Commerce Ave, Harare, Zimbabwe",
  "phone": "+263771111111",
  "email": "downtown@example.com",
  "managerId": 5,
  "managerName": "Mike Manager",
  "defaultCurrencyCode": "USD",
  "shopType": "RETAIL",
  "isActive": true,
  "createdAt": "2026-01-15T10:00:00"
}
```

---

## Currency Management

### Create Currency

Adds a new currency to the system.

**Endpoint**: `POST /api/currencies`

**Request Body**:
```json
{
  "code": "ZWL",
  "name": "Zimbabwean Dollar",
  "symbol": "Z$",
  "isBaseCurrency": false,
  "decimalPlaces": 2
}
```

**Response** (201 Created):
```json
{
  "id": 5,
  "code": "ZWL",
  "name": "Zimbabwean Dollar",
  "symbol": "Z$",
  "isBaseCurrency": false,
  "decimalPlaces": 2,
  "isActive": true,
  "createdAt": "2026-01-15T09:00:00"
}
```

### Add Exchange Rate

Sets exchange rate between currencies.

**Endpoint**: `POST /api/exchange-rates`

**Request Body**:
```json
{
  "fromCurrencyId": 1,
  "toCurrencyId": 5,
  "rate": 350.50,
  "effectiveDate": "2026-01-15T00:00:00"
}
```

**Response** (201 Created):
```json
{
  "id": 45,
  "fromCurrency": "USD",
  "toCurrency": "ZWL",
  "rate": 350.50,
  "effectiveDate": "2026-01-15T00:00:00",
  "createdAt": "2026-01-15T09:00:00"
}
```

### Convert Currency

Converts amount between currencies.

**Endpoint**: `GET /api/exchange-rates/convert`

**Query Parameters**:
- `amount`: Amount to convert
- `from`: Source currency code
- `to`: Target currency code
- `date` (optional): Date for historical rate

**Example Request**:
```http
GET /api/exchange-rates/convert?amount=100&from=USD&to=ZWL
```

**Response** (200 OK):
```json
{
  "originalAmount": 100.00,
  "originalCurrency": "USD",
  "convertedAmount": 35050.00,
  "convertedCurrency": "ZWL",
  "exchangeRate": 350.50,
  "effectiveDate": "2026-01-15T00:00:00"
}
```

---

## Pricing Management

### Create Selling Price

Creates a price for a product.

**Endpoint**: `POST /api/selling-prices`

**Request Body**:
```json
{
  "productId": 150,
  "shopId": 1,
  "currencyId": 1,
  "priceType": "RETAIL",
  "unitPrice": 15.50,
  "discountPercent": 0.0,
  "minQuantity": 1,
  "maxQuantity": null,
  "effectiveFrom": "2026-01-15T00:00:00",
  "effectiveTo": null,
  "taxIncluded": false
}
```

**Response** (201 Created):
```json
{
  "id": 89,
  "productId": 150,
  "productName": "Premium Widget",
  "shopId": 1,
  "priceType": "RETAIL",
  "currencyCode": "USD",
  "unitPrice": 15.50,
  "discountPercent": 0.0,
  "finalPrice": 15.50,
  "minQuantity": 1,
  "maxQuantity": null,
  "effectiveFrom": "2026-01-15T00:00:00",
  "effectiveTo": null,
  "taxIncluded": false,
  "isActive": true
}
```

### Get Product Prices

Retrieves all prices for a product.

**Endpoint**: `GET /api/selling-prices/product/{productId}`

**Query Parameters**:
- `shopId` (optional): Filter by shop
- `priceType` (optional): Filter by price type

**Example Request**:
```http
GET /api/selling-prices/product/150?shopId=1
```

**Response** (200 OK):
```json
{
  "productId": 150,
  "productName": "Premium Widget",
  "prices": [
    {
      "id": 89,
      "priceType": "RETAIL",
      "unitPrice": 15.50,
      "discountPercent": 0.0,
      "finalPrice": 15.50,
      "minQuantity": 1
    },
    {
      "id": 90,
      "priceType": "WHOLESALE",
      "unitPrice": 12.50,
      "discountPercent": 5.0,
      "finalPrice": 11.88,
      "minQuantity": 10
    },
    {
      "id": 91,
      "priceType": "BULK",
      "unitPrice": 11.00,
      "discountPercent": 10.0,
      "finalPrice": 9.90,
      "minQuantity": 50
    }
  ]
}
```

---

## Tax Management

### Create Tax Rule

Creates a new tax configuration.

**Endpoint**: `POST /api/taxes`

**Request Body**:
```json
{
  "name": "Standard VAT",
  "taxNature": "VAT",
  "calculationType": "PERCENTAGE",
  "rate": 15.0,
  "isActive": true,
  "appliesTo": ["RETAIL", "WHOLESALE"]
}
```

**Response** (201 Created):
```json
{
  "id": 5,
  "name": "Standard VAT",
  "taxNature": "VAT",
  "calculationType": "PERCENTAGE",
  "rate": 15.0,
  "isActive": true,
  "createdAt": "2026-01-15T09:00:00"
}
```

---

## Inventory Transfers

### Create Transfer Request

Initiates a stock transfer between shops.

**Endpoint**: `POST /api/inventory-transfers`

**Request Body**:
```json
{
  "fromShopId": 1,
  "toShopId": 5,
  "transferType": "REPLENISHMENT",
  "priority": "HIGH",
  "requestedBy": 10,
  "notes": "Urgent restock for downtown branch",
  "items": [
    {
      "productId": 150,
      "quantity": 50
    },
    {
      "productId": 151,
      "quantity": 30
    }
  ]
}
```

**Response** (201 Created):
```json
{
  "id": 245,
  "transferNumber": "TRF-2026-000245",
  "fromShopId": 1,
  "fromShopName": "Main Store",
  "toShopId": 5,
  "toShopName": "Downtown Branch",
  "transferType": "REPLENISHMENT",
  "status": "PENDING",
  "priority": "HIGH",
  "requestedBy": 10,
  "requestedByName": "John Doe",
  "createdAt": "2026-01-15T10:00:00",
  "items": [
    {
      "id": 890,
      "productId": 150,
      "productName": "Premium Widget",
      "quantity": 50
    },
    {
      "id": 891,
      "productId": 151,
      "productName": "Standard Widget",
      "quantity": 30
    }
  ]
}
```

### Approve Transfer

Approves a pending transfer request.

**Endpoint**: `PATCH /api/inventory-transfers/{id}/approve`

**Request Body**:
```json
{
  "approvedBy": 5,
  "notes": "Approved - stock available"
}
```

**Response** (200 OK):
```json
{
  "id": 245,
  "transferNumber": "TRF-2026-000245",
  "status": "APPROVED",
  "approvedBy": 5,
  "approvedByName": "Mike Manager",
  "approvedAt": "2026-01-15T10:30:00"
}
```

### Ship Transfer

Marks transfer as shipped.

**Endpoint**: `PATCH /api/inventory-transfers/{id}/ship`

**Request Body**:
```json
{
  "shippedBy": 10,
  "trackingNumber": "TRACK-12345",
  "shippingMethod": "Courier",
  "notes": "Shipped via Express Delivery"
}
```

**Response** (200 OK):
```json
{
  "id": 245,
  "transferNumber": "TRF-2026-000245",
  "status": "SHIPPED",
  "shippedBy": 10,
  "shippedAt": "2026-01-15T11:00:00",
  "trackingNumber": "TRACK-12345",
  "estimatedArrival": "2026-01-16T11:00:00"
}
```

### Receive Transfer

Confirms receipt and updates inventory.

**Endpoint**: `PATCH /api/inventory-transfers/{id}/receive`

**Request Body**:
```json
{
  "receivedBy": 15,
  "receivedQuantities": [
    {
      "itemId": 890,
      "quantityReceived": 50,
      "condition": "GOOD"
    },
    {
      "itemId": 891,
      "quantityReceived": 28,
      "condition": "GOOD",
      "notes": "2 items damaged in transit"
    }
  ],
  "notes": "Received and inspected"
}
```

**Response** (200 OK):
```json
{
  "id": 245,
  "transferNumber": "TRF-2026-000245",
  "status": "RECEIVED",
  "receivedBy": 15,
  "receivedAt": "2026-01-16T09:00:00",
  "discrepancies": [
    {
      "itemId": 891,
      "expectedQuantity": 30,
      "receivedQuantity": 28,
      "variance": -2
    }
  ],
  "inventoryUpdated": true
}
```

---

## Financial Operations

### Create Accounting Entry

Creates a double-entry accounting record.

**Endpoint**: `POST /api/accountancy/entries`

**Request Body**:
```json
{
  "date": "2026-01-15T10:00:00",
  "description": "Sale - Order #890",
  "entries": [
    {
      "accountCode": "1100",
      "accountName": "Cash",
      "entryType": "DEBIT",
      "amount": 151.38,
      "currencyId": 1
    },
    {
      "accountCode": "4100",
      "accountName": "Sales Revenue",
      "entryType": "CREDIT",
      "amount": 131.94,
      "currencyId": 1
    },
    {
      "accountCode": "2200",
      "accountName": "VAT Payable",
      "entryType": "CREDIT",
      "amount": 19.44,
      "currencyId": 1
    }
  ]
}
```

**Response** (201 Created):
```json
{
  "id": 1450,
  "entryNumber": "ACC-2026-001450",
  "date": "2026-01-15T10:00:00",
  "description": "Sale - Order #890",
  "totalDebit": 151.38,
  "totalCredit": 151.38,
  "isBalanced": true,
  "entries": [
    {
      "accountCode": "1100",
      "accountName": "Cash",
      "entryType": "DEBIT",
      "amount": 151.38
    },
    {
      "accountCode": "4100",
      "accountName": "Sales Revenue",
      "entryType": "CREDIT",
      "amount": 131.94
    },
    {
      "accountCode": "2200",
      "accountName": "VAT Payable",
      "entryType": "CREDIT",
      "amount": 19.44
    }
  ],
  "createdAt": "2026-01-15T10:00:00"
}
```

### Submit to ZIMRA

Submits fiscal data to Zimbabwe Revenue Authority.

**Endpoint**: `POST /api/zimra/submit`

**Request Body**:
```json
{
  "fiscalPeriod": "2026-01",
  "businessTin": "12345678",
  "transactions": [450, 451, 452],
  "totalSales": 5450.00,
  "totalTax": 817.50
}
```

**Response** (200 OK):
```json
{
  "submissionId": "ZIMRA-2026-001",
  "status": "ACCEPTED",
  "fiscalPeriod": "2026-01",
  "submittedAt": "2026-01-15T18:00:00",
  "verificationCode": "ZIM-VER-12345",
  "totalSales": 5450.00,
  "totalTax": 817.50,
  "receiptUrl": "https://zimra.gov.zw/receipts/ZIMRA-2026-001"
}
```

---

## Error Responses

### Standard Error Response Format

All errors follow this structure:

```json
{
  "timestamp": "2026-01-15T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/orders",
  "errors": [
    {
      "field": "orderLines",
      "message": "Order must have at least one line item"
    }
  ]
}
```

### Common HTTP Status Codes

| Status | Meaning | Usage |
|--------|---------|-------|
| 200 | OK | Successful GET, PATCH, DELETE |
| 201 | Created | Successful POST |
| 204 | No Content | Successful DELETE with no response body |
| 400 | Bad Request | Validation errors, business rule violations |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource, concurrent modification |
| 422 | Unprocessable Entity | Invalid state transition |
| 500 | Internal Server Error | Server-side error |

### Common Error Codes

```json
{
  "error": "INSUFFICIENT_STOCK",
  "message": "Cannot reduce stock by 200. Available stock: 125"
}
```

```json
{
  "error": "INVENTORY_ALREADY_EXISTS",
  "message": "Inventory already exists for shop 1 and product 150"
}
```

```json
{
  "error": "EXCEEDS_MAX_STOCK",
  "message": "Adding 400 would exceed maximum stock of 500"
}
```

```json
{
  "error": "INVALID_ORDER_STATUS",
  "message": "Cannot change status from COMPLETED to PENDING"
}
```

```json
{
  "error": "CASHIER_SESSION_CLOSED",
  "message": "Cannot process sale. Cashier session is closed"
}
```

---

## Rate Limiting

API endpoints are rate-limited to prevent abuse:
- **Default**: 100 requests per minute per IP
- **Authentication**: 10 login attempts per minute per IP
- **Reports**: 20 requests per minute per user

Rate limit headers:
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1705322400
```

## Pagination

List endpoints support pagination with these parameters:
- `page`: Page number (0-indexed)
- `size`: Items per page (max 100)
- `sort`: Sort field and direction (e.g., `createdAt,desc`)

Response includes pagination metadata:
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

---

**Version**: 1.0
**Last Updated**: 2026-01-15

For interactive API testing, visit: `http://localhost:9090/swagger-ui.html`
