# API Reference Guide

## Table of Contents
- [Overview](#overview)
- [Authentication](#authentication)
- [User Account Management](#user-account-management)
- [POS Operations](#pos-operations)
- [Order Management](#order-management)
- [Sales Management](#sales-management)
- [Product Management](#product-management)
- [Inventory Management](#inventory-management)
- [Shop Inventory Management](#shop-inventory-management)
- [Inventory Transfers](#inventory-transfers)
- [Cashier Management](#cashier-management)
- [Customer Management](#customer-management)
- [Supplier Management](#supplier-management)
- [Shop Management](#shop-management)
- [Currency Management](#currency-management)
- [Pricing Management](#pricing-management)
- [Tax Management](#tax-management)
- [Cart Management](#cart-management)
- [File Storage](#file-storage)
- [Analytics](#analytics)
- [Accountancy](#accountancy)
- [ZIMRA Fiscalisation](#zimra-fiscalisation)
- [Enums Reference](#enums-reference)
- [Error Responses](#error-responses)

---

## Overview

### Base URL

```
http://localhost:9090/api
```

### Swagger UI Documentation

```
http://localhost:9090/swagger-ui.html
```

### Technology Stack
- **Framework**: Spring Boot 3.5.3
- **Language**: Java 17
- **Database**: MySQL
- **ORM**: Hibernate/JPA
- **Port**: 9090

---

## Authentication

The system uses Spring Security with JWT Bearer token authentication.

### Headers

```http
Authorization: Bearer <token>
Content-Type: application/json
```

### Roles

| Role | Description |
|------|-------------|
| `USER` | Online shop customers |
| `ADMIN` | System administrators |
| `CASHIER` | POS staff members |

---

## User Account Management

Base path: `/api/users`

### Register User

Creates a new user account.

**Endpoint**: `POST /api/users/register`

**Request Body**:
```json
{
  "username": "john_doe",
  "password": "securePassword123",
  "email": "john.doe@example.com"
}
```

**Response** (201 Created):
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john.doe@example.com",
  "roles": ["USER"],
  "enabled": true,
  "createdAt": "2026-01-15T10:00:00"
}
```

### Get Current User

**Endpoint**: `GET /api/users/me`
**Authorization**: `USER` role required

### Update Current User

**Endpoint**: `PUT /api/users/me`
**Authorization**: `USER` role required

### Get All Users

**Endpoint**: `GET /api/users`
**Authorization**: `ADMIN` role required

### Get User by ID

**Endpoint**: `GET /api/users/{id}`
**Authorization**: `ADMIN` role required

### Get Users by Role

**Endpoint**: `GET /api/users/role/{role}`
**Authorization**: `ADMIN` role required

**Path Parameters**:
- `role`: `USER`, `ADMIN`, or `CASHIER`

### Get User by Email

**Endpoint**: `GET /api/users/email/{email}`
**Authorization**: `ADMIN` role required

### Enable/Disable User

**Endpoint**: `PUT /api/users/{id}/enable?enabled={boolean}`
**Authorization**: `ADMIN` role required

### Add Role to User

**Endpoint**: `POST /api/users/{id}/roles`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "role": "ADMIN"
}
```

### Remove Role from User

**Endpoint**: `DELETE /api/users/{id}/roles/{role}`
**Authorization**: `ADMIN` role required

### Get User Statistics

**Endpoint**: `GET /api/users/statistics`
**Authorization**: `ADMIN` role required

**Response**:
```json
{
  "totalUsers": 150,
  "activeUsers": 142,
  "adminCount": 5,
  "cashierCount": 20,
  "customerCount": 125
}
```

---

## POS Operations

Base path: `/api/pos`

### Quick Sale

Processes a quick sale transaction at the POS terminal.

**Endpoint**: `POST /api/pos/quick-sale`

**Request Body**:
```json
{
  "cashierId": 1,
  "items": [
    {
      "productId": 100,
      "quantity": 2,
      "unitPrice": 15.50
    }
  ],
  "paymentMethod": "CASH",
  "cashGiven": 50.00
}
```

**Response** (200 OK): Returns the created `Order` object.

### Barcode Scan

Retrieves product information by barcode.

**Endpoint**: `POST /api/pos/barcode-scan`

**Request Body**:
```json
{
  "barcode": "1234567890123"
}
```

**Response** (200 OK): Returns the `Product` object.

### Get Daily Summary

**Endpoint**: `GET /api/pos/daily-summary`

**Query Parameters**:
- `date` (optional): Date in YYYY-MM-DD format (defaults to today)
- `shopId` (optional): Filter by shop

**Response** (200 OK):
```json
{
  "totalSales": 5450.00,
  "totalTransactions": 45,
  "cashSales": 3200.00,
  "cardSales": 2250.00,
  "averageTransactionValue": 121.11
}
```

### Open Cash Drawer

**Endpoint**: `POST /api/pos/open-cash-drawer`

**Request Body**:
```json
{
  "cashierId": 1
}
```

**Note**: Requires `OPEN_CASH_DRAWER` permission.

### Get Receipt

**Endpoint**: `GET /api/pos/receipt/{orderId}`

### Void Transaction

**Endpoint**: `POST /api/pos/void-transaction/{orderId}`

**Request Body**:
```json
{
  "cashierId": 1,
  "reason": "Customer request"
}
```

**Note**: Requires `VOID_TRANSACTION` permission.

---

## Order Management

Base path: `/api/orders`

### Create Order

Creates an order from the user's cart.

**Endpoint**: `POST /api/orders`
**Authorization**: `USER` role required

**Request Body**:
```json
{
  "shippingAddress": "123 Main St, Harare, Zimbabwe",
  "paymentMethod": "CREDIT_CARD",
  "salesChannel": "ONLINE",
  "currencyCode": "USD"
}
```

**Response** (201 Created): Returns the created `Order` object.

### Get All Orders

**Endpoint**: `GET /api/orders`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Orders (Paginated)

**Endpoint**: `GET /api/orders/paginated`
**Authorization**: `ADMIN` or `CASHIER` role required

**Query Parameters**:
- `page` (default: 0)
- `size` (default: 20)
- `sort` (e.g., `createdAt,desc`)

### Get Order by ID

**Endpoint**: `GET /api/orders/{id}`
**Authorization**: `USER` role required (own orders or ADMIN/CASHIER for any)

### Get My Orders

**Endpoint**: `GET /api/orders/my-orders`
**Authorization**: `USER` role required

**Query Parameters**: Standard pagination

### Get Orders by Status

**Endpoint**: `GET /api/orders/status/{status}`
**Authorization**: `ADMIN` or `CASHIER` role required

**Path Parameters**:
- `status`: `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `COMPLETED`

### Get Orders by Channel

**Endpoint**: `GET /api/orders/channel/{channel}`
**Authorization**: `ADMIN` or `CASHIER` role required

**Path Parameters**:
- `channel`: `ONLINE`, `POS`, `PHONE`

### Update Order

**Endpoint**: `PUT /api/orders/{id}`
**Authorization**: `ADMIN` role required

### Delete Order

**Endpoint**: `DELETE /api/orders/{id}`
**Authorization**: `ADMIN` role required

### Order Statistics

**Get Revenue Statistics**:
`GET /api/orders/statistics/revenue?currencyCode={code}`
**Authorization**: `ADMIN` role required

**Get Recent Order Stats**:
`GET /api/orders/statistics/recent`
**Authorization**: `ADMIN` role required

**Get Channel Statistics**:
`GET /api/orders/statistics/channels?startDate={datetime}&endDate={datetime}`
**Authorization**: `ADMIN` role required

**Get Top Products**:
`GET /api/orders/statistics/top-products`
**Authorization**: `ADMIN` role required

---

## Sales Management

Base path: `/api/sales`

### Get All Sales

**Endpoint**: `GET /api/sales`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sales (Paginated)

**Endpoint**: `GET /api/sales/paginated`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sale by ID

**Endpoint**: `GET /api/sales/{id}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sales by Shop

**Endpoint**: `GET /api/sales/shop/{shopId}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sales by Customer

**Endpoint**: `GET /api/sales/customer/{customerId}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sales by Product

**Endpoint**: `GET /api/sales/product/{productId}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sales by Type

**Endpoint**: `GET /api/sales/type/{saleType}`
**Authorization**: `ADMIN` or `CASHIER` role required

**Path Parameters**:
- `saleType`: `CASH`, `CREDIT`

### Get Sales by Payment Method

**Endpoint**: `GET /api/sales/payment-method/{paymentMethod}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Sales by Date Range

**Endpoint**: `GET /api/sales/date-range?startDate={datetime}&endDate={datetime}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Shop Sales by Date Range

**Endpoint**: `GET /api/sales/shop/{shopId}/date-range?startDate={datetime}&endDate={datetime}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Recent Sales by Shop

**Endpoint**: `GET /api/sales/shop/{shopId}/recent`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Total Sales by Shop

**Endpoint**: `GET /api/sales/shop/{shopId}/total`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Total Sales by Shop and Date Range

**Endpoint**: `GET /api/sales/shop/{shopId}/total/date-range?startDate={datetime}&endDate={datetime}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Today's Sales by Shop

**Endpoint**: `GET /api/sales/shop/{shopId}/today`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Total Sales by Customer

**Endpoint**: `GET /api/sales/customer/{customerId}/total`
**Authorization**: `ADMIN` or `CASHIER` role required

### Create Sale

**Endpoint**: `POST /api/sales`
**Authorization**: `ADMIN` or `CASHIER` role required

### Update Sale

**Endpoint**: `PUT /api/sales/{id}`
**Authorization**: `ADMIN` role required

### Delete Sale

**Endpoint**: `DELETE /api/sales/{id}`
**Authorization**: `ADMIN` role required

---

## Product Management

Base path: `/api/products`

### Get All Products

**Endpoint**: `GET /api/products`

### Get Product by ID

**Endpoint**: `GET /api/products/{id}`

### Create Product

**Endpoint**: `POST /api/products`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "name": "Premium Widget",
  "description": "High-quality widget",
  "category": "Electronics",
  "barcode": "1234567890123",
  "sku": "PROD-A-001",
  "weighable": false,
  "minStock": 10,
  "maxStock": 1000,
  "weight": 1.5,
  "unitOfMeasure": "piece",
  "actualMeasure": 1,
  "imageUrl": "https://example.com/image.jpg"
}
```

### Update Product

**Endpoint**: `PUT /api/products/{id}`
**Authorization**: `ADMIN` role required

### Delete Product

**Endpoint**: `DELETE /api/products/{id}`
**Authorization**: `ADMIN` role required

### Get Product by Barcode

**Endpoint**: `GET /api/products/barcode/{barcode}`

### Get Product by SKU

**Endpoint**: `GET /api/products/sku/{sku}`

### Get Products by Category

**Endpoint**: `GET /api/products/category/{category}`

### Search Products

**Endpoint**: `GET /api/products/search?name={searchTerm}`

### Get All Categories

**Endpoint**: `GET /api/products/categories`

---

## Inventory Management

Base path: `/api/inventory`

**Note**: All endpoints require `ADMIN` role.

### Get Inventory by Product

**Endpoint**: `GET /api/inventory/product/{productId}`

### Get Product Availability

**Endpoint**: `GET /api/inventory/product/{productId}/availability`

**Response**:
```json
{
  "online": 100,
  "pos": 50,
  "reserved": 10
}
```

### Add Stock

**Endpoint**: `POST /api/inventory/product/{productId}/add`

**Request Body**:
```json
{
  "quantity": 50
}
```

### Remove Stock

**Endpoint**: `POST /api/inventory/product/{productId}/remove`

**Request Body**:
```json
{
  "quantity": 25
}
```

### Reserve Stock

**Endpoint**: `POST /api/inventory/product/{productId}/reserve`

**Request Body**:
```json
{
  "quantity": 10
}
```

### Release Reservation

**Endpoint**: `POST /api/inventory/product/{productId}/release`

**Request Body**:
```json
{
  "quantity": 10
}
```

### Check Stock

**Endpoint**: `GET /api/inventory/check/{productId}?quantity={quantity}`

**Response**: `true` or `false`

### Get Low Stock Items

**Endpoint**: `GET /api/inventory/low-stock`

### Get Total Inventory Value

**Endpoint**: `GET /api/inventory/total-value`

### Update Reorder Level

**Endpoint**: `PUT /api/inventory/product/{productId}/reorder-level`

**Request Body**:
```json
{
  "reorderLevel": 20
}
```

---

## Shop Inventory Management

Base path: `/api/shop-inventory`

### Get Shop Inventory for Product

**Endpoint**: `GET /api/shop-inventory/shop/{shopId}/product/{productId}`

### Get All Shop Inventory

**Endpoint**: `GET /api/shop-inventory/shop/{shopId}`

### Get Product Inventory Across Shops

**Endpoint**: `GET /api/shop-inventory/product/{productId}`

### Get Warehouse Inventory

**Endpoint**: `GET /api/shop-inventory/warehouse/product/{productId}`

### Create Shop Inventory

**Endpoint**: `POST /api/shop-inventory`

**Request Body**:
```json
{
  "shopId": 1,
  "productId": 100,
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

### Update Shop Inventory

**Endpoint**: `PATCH /api/shop-inventory/shop/{shopId}/product/{productId}`

**Request Body**:
```json
{
  "unitPrice": 13.50,
  "maxStock": 600,
  "reorderLevel": 25
}
```

### Delete Shop Inventory

**Endpoint**: `DELETE /api/shop-inventory/shop/{shopId}/product/{productId}`

### Check In-Stock Status

**Endpoint**: `GET /api/shop-inventory/shop/{shopId}/product/{productId}/in-stock?quantity={quantity}`

### Reduce Stock

**Endpoint**: `POST /api/shop-inventory/shop/{shopId}/product/{productId}/reduce-stock`

**Request Body**:
```json
{
  "quantity": 25
}
```

### Get Products by Shop

**Endpoint**: `GET /api/shop-inventory/shop/{shopId}/products`

---

## Inventory Transfers

Base path: `/api/inventory-transfers`

### Create Transfer

**Endpoint**: `POST /api/inventory-transfers`

**Request Body**:
```json
{
  "fromShopId": 1,
  "toShopId": 2,
  "initiatorId": 10,
  "transferType": "REPLENISHMENT",
  "priority": "HIGH",
  "notes": "Urgent restock for downtown branch"
}
```

### Get Transfer by ID

**Endpoint**: `GET /api/inventory-transfers/{transferId}`

### Get Transfer by Number

**Endpoint**: `GET /api/inventory-transfers/number/{transferNumber}`

### Get Transfers for Shop

**Endpoint**: `GET /api/inventory-transfers/shop/{shopId}`

**Query Parameters**: Standard pagination

### Get Outgoing Transfers

**Endpoint**: `GET /api/inventory-transfers/shop/{shopId}/outgoing`

### Get Incoming Transfers

**Endpoint**: `GET /api/inventory-transfers/shop/{shopId}/incoming`

### Get Transfers by Status

**Endpoint**: `GET /api/inventory-transfers/status/{status}`

**Path Parameters**:
- `status`: `PENDING`, `APPROVED`, `IN_TRANSIT`, `RECEIVED`, `COMPLETED`, `CANCELLED`

### Get Overdue Transfers

**Endpoint**: `GET /api/inventory-transfers/overdue`

### Get Transfers by Date Range

**Endpoint**: `GET /api/inventory-transfers/date-range?startDate={datetime}&endDate={datetime}`

### Add Items to Transfer

**Endpoint**: `POST /api/inventory-transfers/{transferId}/items`

**Request Body**:
```json
{
  "productIds": [100, 101, 102],
  "quantity": 50,
  "unitCost": 12.50,
  "notes": "Handle with care"
}
```

### Remove Item from Transfer

**Endpoint**: `DELETE /api/inventory-transfers/{transferId}/items/{itemId}`

### Approve Transfer

**Endpoint**: `POST /api/inventory-transfers/{transferId}/approve`

**Request Body**:
```json
{
  "approverId": 5
}
```

### Ship Transfer

**Endpoint**: `POST /api/inventory-transfers/{transferId}/ship`

**Request Body**:
```json
{
  "shipperId": 10
}
```

### Receive Transfer

**Endpoint**: `POST /api/inventory-transfers/{transferId}/receive`

**Request Body**:
```json
{
  "receiverId": 15,
  "receivedItems": [
    {
      "itemId": 890,
      "receivedQuantity": 50,
      "damagedQuantity": 0,
      "notes": "All items in good condition"
    },
    {
      "itemId": 891,
      "receivedQuantity": 28,
      "damagedQuantity": 2,
      "notes": "2 items damaged in transit"
    }
  ]
}
```

### Cancel Transfer

**Endpoint**: `POST /api/inventory-transfers/{transferId}/cancel`

**Request Body**:
```json
{
  "reason": "Items no longer needed"
}
```

### Complete Transfer

**Endpoint**: `POST /api/inventory-transfers/{transferId}/complete`

### Get Transfer Inventory Impact

**Endpoint**: `GET /api/inventory-transfers/{transferId}/inventory-impact`

### Get Transfer History

**Endpoint**: `GET /api/inventory-transfers/{transferId}/history`

---

## Cashier Management

Base path: `/api/cashiers`

### Register Cashier

**Endpoint**: `POST /api/cashiers/register`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "employeeId": "EMP-001",
  "username": "john_cashier",
  "password": "securePassword",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "+263771234567",
  "role": "CASHIER",
  "pinCode": "1234"
}
```

### Get All Cashiers

**Endpoint**: `GET /api/cashiers`
**Authorization**: `ADMIN` or `MANAGER` role required

### Get Cashier by ID

**Endpoint**: `GET /api/cashiers/{id}`
**Authorization**: `ADMIN` or `MANAGER` role required

### Get Cashier by Username

**Endpoint**: `GET /api/cashiers/username/{username}`
**Authorization**: `ADMIN` or `MANAGER` role required

### Get Cashier by Employee ID

**Endpoint**: `GET /api/cashiers/employee/{employeeId}`
**Authorization**: `ADMIN` or `MANAGER` role required

### Get Cashiers by Shop

**Endpoint**: `GET /api/cashiers/shop/{shopId}`
**Authorization**: `ADMIN` or `MANAGER` role required

### Update Cashier

**Endpoint**: `PUT /api/cashiers/{id}`
**Authorization**: `ADMIN` or `MANAGER` role required

### Deactivate Cashier

**Endpoint**: `PUT /api/cashiers/{id}/deactivate`
**Authorization**: `ADMIN` role required

### Assign Cashier to Shop

**Endpoint**: `POST /api/cashiers/{id}/assign-shop`
**Authorization**: `ADMIN` or `MANAGER` role required

**Request Body**:
```json
{
  "shopId": 1
}
```

### Session Management

**Start Session**:
`POST /api/cashiers/sessions/start`

**Request Body**:
```json
{
  "cashierId": 10,
  "shopId": 1,
  "terminalId": "TERM-001",
  "openingCash": 500.00
}
```

**End Session**:
`POST /api/cashiers/sessions/{sessionId}/end`

**Request Body**:
```json
{
  "closingCash": 3750.00,
  "notes": "End of shift"
}
```

**Get Active Sessions**:
`GET /api/cashiers/sessions/active`

**Get Session History**:
`GET /api/cashiers/sessions/history/{cashierId}?startDate={datetime}&endDate={datetime}`

**Get Cashier's Active Session**:
`GET /api/cashiers/{cashierId}/sessions/active`

### Permission Management

**Grant Permission**:
`POST /api/cashiers/{id}/permissions`

**Request Body**:
```json
{
  "permission": "VOID_TRANSACTION"
}
```

**Get Permissions**:
`GET /api/cashiers/{id}/permissions`

**Check Permission**:
`GET /api/cashiers/{id}/permissions/check?permission={permission}`

### Authentication

**Authenticate by Username/Password**:
`POST /api/cashiers/authenticate`

**Request Body**:
```json
{
  "username": "john_cashier",
  "password": "securePassword"
}
```

**Authenticate by PIN**:
`POST /api/cashiers/authenticate/pin`

**Request Body**:
```json
{
  "employeeId": "EMP-001",
  "pin": "1234"
}
```

### Shop-specific Queries

**Get Active Cashiers by Shop**:
`GET /api/cashiers/shop/{shopId}/active`

**Get Cashiers by Shop and Role**:
`GET /api/cashiers/shop/{shopId}/role/{role}`

**Get Managers by Shop**:
`GET /api/cashiers/shop/{shopId}/managers`

**Get Cashier Count by Shop**:
`GET /api/cashiers/shop/{shopId}/count`

**Check Cashier Capacity**:
`GET /api/cashiers/shop/{shopId}/capacity-available?maxCashiers={max}`

---

## Customer Management

Base path: `/api/customers`

### Get All Customers

**Endpoint**: `GET /api/customers`

### Get Customers (Paginated)

**Endpoint**: `GET /api/customers/paginated`

### Get Customer by ID

**Endpoint**: `GET /api/customers/{id}`

### Get Customer by Code

**Endpoint**: `GET /api/customers/code/{code}`

### Get Customer by Email

**Endpoint**: `GET /api/customers/email/{email}`

### Get Customer by Tax ID

**Endpoint**: `GET /api/customers/tax-id/{taxId}`

### Get Active Customers

**Endpoint**: `GET /api/customers/active`

### Get Verified Customers

**Endpoint**: `GET /api/customers/verified`

### Get Active and Verified Customers

**Endpoint**: `GET /api/customers/active-verified`

### Get Active Customers (Ordered)

**Endpoint**: `GET /api/customers/active/ordered`

### Get Active and Verified Customers (Ordered)

**Endpoint**: `GET /api/customers/active-verified/ordered`

### Search Customers

**Endpoint**: `GET /api/customers/search?term={searchTerm}`

### Get Customers by Country

**Endpoint**: `GET /api/customers/country/{country}`

### Get Customers by City

**Endpoint**: `GET /api/customers/city/{city}`

### Get Customers by Minimum Loyalty Points

**Endpoint**: `GET /api/customers/loyalty-points?minPoints={points}`

### Create Customer

**Endpoint**: `POST /api/customers`
**Authorization**: `ADMIN` or `CASHIER` role required

**Request Body**:
```json
{
  "code": "CUST-001",
  "name": "Jane Smith",
  "email": "jane@example.com",
  "phoneNumber": "+263771234567",
  "address": "123 Main St",
  "city": "Harare",
  "country": "Zimbabwe",
  "taxId": "TIN-12345678",
  "active": true,
  "verified": false,
  "loyaltyPoints": 0
}
```

### Update Customer

**Endpoint**: `PUT /api/customers/{id}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Delete Customer

**Endpoint**: `DELETE /api/customers/{id}`
**Authorization**: `ADMIN` role required

### Activate Customer

**Endpoint**: `POST /api/customers/{id}/activate`
**Authorization**: `ADMIN` role required

### Deactivate Customer

**Endpoint**: `POST /api/customers/{id}/deactivate`
**Authorization**: `ADMIN` role required

### Verify Customer

**Endpoint**: `POST /api/customers/{id}/verify`
**Authorization**: `ADMIN` role required

### Unverify Customer

**Endpoint**: `POST /api/customers/{id}/unverify`
**Authorization**: `ADMIN` role required

### Add Loyalty Points

**Endpoint**: `POST /api/customers/{id}/loyalty-points/add`
**Authorization**: `ADMIN` or `CASHIER` role required

**Request Body**:
```json
{
  "points": 100
}
```

### Redeem Loyalty Points

**Endpoint**: `POST /api/customers/{id}/loyalty-points/redeem`
**Authorization**: `ADMIN` or `CASHIER` role required

**Request Body**:
```json
{
  "points": 50
}
```

### Bulk Activate Customers

**Endpoint**: `POST /api/customers/bulk-activate`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "customerIds": [1, 2, 3, 4, 5]
}
```

### Bulk Deactivate Customers

**Endpoint**: `POST /api/customers/bulk-deactivate`
**Authorization**: `ADMIN` role required

### Check Customer Exists

**By Code**: `GET /api/customers/exists/code/{code}`
**By Tax ID**: `GET /api/customers/exists/tax-id/{taxId}`
**By Email**: `GET /api/customers/exists/email/{email}`

---

## Supplier Management

Base path: `/api/suppliers`

### Get All Suppliers

**Endpoint**: `GET /api/suppliers`

### Get Suppliers (Paginated)

**Endpoint**: `GET /api/suppliers/paginated`

### Get Supplier by ID

**Endpoint**: `GET /api/suppliers/{id}`

### Get Supplier by Code

**Endpoint**: `GET /api/suppliers/code/{code}`

### Get Supplier by Tax ID

**Endpoint**: `GET /api/suppliers/tax-id/{taxId}`

### Get Active Suppliers

**Endpoint**: `GET /api/suppliers/active`

### Get Verified Suppliers

**Endpoint**: `GET /api/suppliers/verified`

### Get Active and Verified Suppliers

**Endpoint**: `GET /api/suppliers/active-verified`

### Get Active Suppliers (Ordered)

**Endpoint**: `GET /api/suppliers/active/ordered`

### Get Active and Verified Suppliers (Ordered)

**Endpoint**: `GET /api/suppliers/active-verified/ordered`

### Search Suppliers

**Endpoint**: `GET /api/suppliers/search?term={searchTerm}`

### Get Suppliers by Country

**Endpoint**: `GET /api/suppliers/country/{country}`

### Get Suppliers by City

**Endpoint**: `GET /api/suppliers/city/{city}`

### Get Suppliers by Minimum Rating

**Endpoint**: `GET /api/suppliers/rating?minRating={rating}`

### Create Supplier

**Endpoint**: `POST /api/suppliers`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "code": "SUP-001",
  "name": "Acme Suppliers",
  "contactPerson": "John Contact",
  "email": "contact@acme.com",
  "phoneNumber": "+263771234567",
  "mobileNumber": "+263771234568",
  "faxNumber": "+263771234569",
  "address": "456 Industrial Ave",
  "city": "Harare",
  "state": "Harare",
  "country": "Zimbabwe",
  "postalCode": "00263",
  "taxId": "TIN-87654321",
  "registrationNumber": "REG-12345",
  "paymentTerms": "Net 30",
  "creditLimit": 50000.00,
  "website": "https://acme.com",
  "notes": "Premium supplier",
  "active": true,
  "verified": true,
  "rating": 5,
  "leadTimeDays": 7,
  "minimumOrderAmount": 100.00
}
```

### Update Supplier

**Endpoint**: `PUT /api/suppliers/{id}`
**Authorization**: `ADMIN` role required

### Delete Supplier

**Endpoint**: `DELETE /api/suppliers/{id}`
**Authorization**: `ADMIN` role required

### Activate Supplier

**Endpoint**: `POST /api/suppliers/{id}/activate`
**Authorization**: `ADMIN` role required

### Deactivate Supplier

**Endpoint**: `POST /api/suppliers/{id}/deactivate`
**Authorization**: `ADMIN` role required

### Verify Supplier

**Endpoint**: `POST /api/suppliers/{id}/verify`
**Authorization**: `ADMIN` role required

### Unverify Supplier

**Endpoint**: `POST /api/suppliers/{id}/unverify`
**Authorization**: `ADMIN` role required

### Update Supplier Rating

**Endpoint**: `PUT /api/suppliers/{id}/rating`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "rating": 4
}
```

### Bulk Activate Suppliers

**Endpoint**: `POST /api/suppliers/bulk-activate`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "supplierIds": [1, 2, 3]
}
```

### Bulk Deactivate Suppliers

**Endpoint**: `POST /api/suppliers/bulk-deactivate`
**Authorization**: `ADMIN` role required

### Check Supplier Exists

**By Code**: `GET /api/suppliers/exists/code/{code}`
**By Tax ID**: `GET /api/suppliers/exists/tax-id/{taxId}`

---

## Shop Management

Base path: `/api/shops`

### Create Shop

**Endpoint**: `POST /api/shops`

**Request Body**:
```json
{
  "name": "Downtown Branch",
  "code": "BRANCH-02",
  "address": "456 Commerce Ave, Harare, Zimbabwe",
  "phoneNumber": "+263771111111",
  "email": "downtown@example.com",
  "openingTime": "08:00",
  "closingTime": "18:00",
  "type": "RETAIL",
  "active": true,
  "maxCashiers": 10,
  "storageCapacity": 1000,
  "defaultCurrencyId": 1
}
```

### Get Shop by ID

**Endpoint**: `GET /api/shops/{id}`

### Get Shop by Code

**Endpoint**: `GET /api/shops/code/{code}`

### Get All Shops

**Endpoint**: `GET /api/shops`

### Get Active Shops

**Endpoint**: `GET /api/shops/active`

### Get Shops by Type

**Endpoint**: `GET /api/shops/type/{type}`

**Path Parameters**:
- `type`: `WAREHOUSE`, `RETAIL`, `OUTLET`, `ONLINE`

### Get Warehouse

**Endpoint**: `GET /api/shops/warehouse`

### Get Shops Managed by Cashier

**Endpoint**: `GET /api/shops/managed-by/{cashierId}`

### Update Shop

**Endpoint**: `PUT /api/shops/{id}`

### Assign Cashier to Shop

**Endpoint**: `POST /api/shops/{shopId}/assign-cashier/{cashierId}`

### Remove Cashier from Shop

**Endpoint**: `POST /api/shops/remove-cashier/{cashierId}`

### Set Shop Manager

**Endpoint**: `POST /api/shops/{shopId}/set-manager/{managerId}`

### Add Shop Manager

**Endpoint**: `POST /api/shops/{shopId}/add-manager/{managerId}`

### Remove Shop Manager

**Endpoint**: `POST /api/shops/{shopId}/remove-manager/{managerId}`

### Deactivate Shop

**Endpoint**: `POST /api/shops/{shopId}/deactivate`

### Get Active Shops Count by Type

**Endpoint**: `GET /api/shops/count/type/{type}`

---

## Currency Management

Base path: `/api/currencies`

### Get All Active Currencies

**Endpoint**: `GET /api/currencies`

### Get Currency by Code

**Endpoint**: `GET /api/currencies/{code}`

### Get Base Currency

**Endpoint**: `GET /api/currencies/base`

### Create Currency

**Endpoint**: `POST /api/currencies`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "code": "ZWL",
  "name": "Zimbabwean Dollar",
  "symbol": "Z$",
  "decimalPlaces": 2,
  "baseCurrency": false,
  "displayOrder": 2
}
```

### Update Currency

**Endpoint**: `PUT /api/currencies/{id}`
**Authorization**: `ADMIN` role required

### Get Exchange Rate

**Endpoint**: `GET /api/currencies/exchange-rate?from={code}&to={code}`

### Create Exchange Rate

**Endpoint**: `POST /api/currencies/exchange-rates`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "fromCurrencyCode": "USD",
  "toCurrencyCode": "ZWL",
  "rate": 350.50,
  "effectiveDate": "2026-01-15T00:00:00",
  "expiryDate": "2026-12-31T23:59:59"
}
```

### Convert Currency

**Endpoint**: `GET /api/currencies/convert?amount={amount}&from={code}&to={code}`

**Response**:
```json
{
  "originalAmount": 100.00,
  "fromCurrency": "USD",
  "toCurrency": "ZWL",
  "convertedAmount": 35050.00,
  "exchangeRate": 350.50
}
```

---

## Pricing Management

Base path: `/api/selling-prices`

### Create Selling Price

**Endpoint**: `POST /api/selling-prices`

**Request Body**:
```json
{
  "productId": 100,
  "shopId": 1,
  "currencyId": 1,
  "priceType": "REGULAR",
  "sellingPrice": 15.50,
  "basePrice": 12.00,
  "taxIds": [1, 2],
  "discountPercentage": 0.0,
  "minSellingPrice": 14.00,
  "maxSellingPrice": 20.00,
  "quantityBreak": null,
  "bulkPrice": null,
  "effectiveFrom": "2026-01-15T00:00:00",
  "effectiveTo": null,
  "priority": 1,
  "createdBy": "admin",
  "notes": "Standard retail price"
}
```

### Update Selling Price

**Endpoint**: `PUT /api/selling-prices/{priceId}`

### Get Selling Price by ID

**Endpoint**: `GET /api/selling-prices/{priceId}`

### Get Current Price

**Endpoint**: `GET /api/selling-prices/shop/{shopId}/product/{productId}/current`

### Get Active Prices

**Endpoint**: `GET /api/selling-prices/shop/{shopId}/product/{productId}`

### Get Price by Type

**Endpoint**: `GET /api/selling-prices/shop/{shopId}/product/{productId}/type/{priceType}`

**Path Parameters**:
- `priceType`: `SALE`, `REGULAR`, `PROMOTIONAL`, `CLEARANCE`, `BULK`, `MEMBER`, `WHOLESALE`, `RETAIL`, `ONLINE`, `SEASONAL`, `FLASH_SALE`

### Get Product Prices

**Endpoint**: `GET /api/selling-prices/product/{productId}`

### Get Shop Prices

**Endpoint**: `GET /api/selling-prices/shop/{shopId}`

### Set Promotional Price

**Endpoint**: `POST /api/selling-prices/shop/{shopId}/product/{productId}/promotional`

**Request Body**:
```json
{
  "currencyId": 1,
  "promotionalPrice": 12.00,
  "expiryDate": "2026-02-28T23:59:59",
  "createdBy": "admin"
}
```

### Set Bulk Price

**Endpoint**: `POST /api/selling-prices/shop/{shopId}/product/{productId}/bulk`

**Request Body**:
```json
{
  "currencyId": 1,
  "regularPrice": 15.50,
  "bulkPrice": 12.00,
  "quantityBreak": 50,
  "createdBy": "admin"
}
```

### Update Price with Cost Calculation

**Endpoint**: `PUT /api/selling-prices/{priceId}/cost-based`

**Request Body**:
```json
{
  "costPrice": 10.00,
  "markupPercentage": 55.0
}
```

### Calculate Selling Price from Markup

**Endpoint**: `POST /api/selling-prices/calculate-from-markup`

**Request Body**:
```json
{
  "costPrice": 10.00,
  "markupPercentage": 55.0
}
```

### Calculate Markup

**Endpoint**: `POST /api/selling-prices/calculate-markup`

**Request Body**:
```json
{
  "costPrice": 10.00,
  "sellingPrice": 15.50
}
```

### Deactivate Price

**Endpoint**: `POST /api/selling-prices/{priceId}/deactivate`

### Get Prices in Range

**Endpoint**: `GET /api/selling-prices/price-range?minPrice={min}&maxPrice={max}`

### Get Products Without Prices

**Endpoint**: `GET /api/selling-prices/shop/{shopId}/products-without-prices`

### Get Low Markup Prices

**Endpoint**: `GET /api/selling-prices/low-markup?threshold={percentage}`

### Find Duplicate Prices

**Endpoint**: `GET /api/selling-prices/duplicates`

### Bulk Update Prices

**Endpoint**: `POST /api/selling-prices/shop/{shopId}/bulk-update`

**Request Body**:
```json
{
  "priceType": "REGULAR",
  "percentage": 5.0,
  "updatedBy": "admin"
}
```

### Copy Prices Between Shops

**Endpoint**: `POST /api/selling-prices/copy-prices`

**Request Body**:
```json
{
  "sourceShopId": 1,
  "targetShopId": 2,
  "createdBy": "admin"
}
```

### Expire Promotional Prices

**Endpoint**: `POST /api/selling-prices/expire-promotions`

---

## Tax Management

Base path: `/api/taxes`

### Create Tax

**Endpoint**: `POST /api/taxes`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "taxName": "Standard VAT",
  "taxNature": "VAT",
  "calculationType": "PERCENTAGE",
  "rate": 15.0,
  "active": true
}
```

### Update Tax

**Endpoint**: `PUT /api/taxes/{taxId}`
**Authorization**: `ADMIN` role required

### Delete Tax

**Endpoint**: `DELETE /api/taxes/{taxId}`
**Authorization**: `ADMIN` role required

### Get Tax by ID

**Endpoint**: `GET /api/taxes/{taxId}`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get All Taxes

**Endpoint**: `GET /api/taxes`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get All Taxes (Paginated)

**Endpoint**: `GET /api/taxes/paginated`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Active Taxes

**Endpoint**: `GET /api/taxes/active`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Taxes by Nature

**Endpoint**: `GET /api/taxes/nature/{taxNature}`
**Authorization**: `ADMIN` or `CASHIER` role required

**Path Parameters**:
- `taxNature`: `VAT`, `FAST_FOOD_TAX`, `EXCISE_DUTY`, `ENVIRONMENTAL_LEVY`, `CUSTOMS_DUTY`, `SURTAX`, `OTHER`

### Get Active Taxes by Nature

**Endpoint**: `GET /api/taxes/nature/{taxNature}/active`
**Authorization**: `ADMIN` or `CASHIER` role required

### Get Taxes by Calculation Type

**Endpoint**: `GET /api/taxes/calculation-type/{calculationType}`
**Authorization**: `ADMIN` or `CASHIER` role required

**Path Parameters**:
- `calculationType`: `FIXED`, `PERCENTAGE`, `TIERED`

### Get Active Taxes by Calculation Type

**Endpoint**: `GET /api/taxes/calculation-type/{calculationType}/active`
**Authorization**: `ADMIN` or `CASHIER` role required

### Search Taxes by Name

**Endpoint**: `GET /api/taxes/search?name={searchTerm}`
**Authorization**: `ADMIN` or `CASHIER` role required

---

## Cart Management

Base path: `/api/cart`

**Note**: All endpoints require `USER` role.

### Get Cart

**Endpoint**: `GET /api/cart`

### Add Item to Cart

**Endpoint**: `POST /api/cart/items`

**Request Body**:
```json
{
  "productId": 100,
  "quantity": 2
}
```

### Update Cart Item

**Endpoint**: `PUT /api/cart/items/{itemId}`

**Request Body**:
```json
{
  "quantity": 5
}
```

### Remove Item from Cart

**Endpoint**: `DELETE /api/cart/items/{itemId}`

### Clear Cart

**Endpoint**: `DELETE /api/cart`

### Get Cart Summary

**Endpoint**: `GET /api/cart/summary`

**Response**:
```json
{
  "totalItems": 10,
  "itemCount": 3
}
```

### Get Abandoned Carts

**Endpoint**: `GET /api/cart/abandoned?hours={hours}`
**Authorization**: `ADMIN` role required

**Query Parameters**:
- `hours` (default: 24): Hours since last activity

---

## File Storage

Base path: `/api/files`

### Upload File

**Endpoint**: `POST /api/files/upload`
**Authorization**: `USER` role required
**Content-Type**: `multipart/form-data`

**Form Parameters**:
- `file`: The file to upload
- `referenceType` (optional): Type of entity this file relates to
- `referenceId` (optional): ID of the related entity

### Download File

**Endpoint**: `GET /api/files/{id}`

### Get My Files

**Endpoint**: `GET /api/files/my-files`
**Authorization**: `USER` role required

### Get Files by Reference

**Endpoint**: `GET /api/files/reference/{referenceType}/{referenceId}`
**Authorization**: `USER` role required

### Delete File

**Endpoint**: `DELETE /api/files/{id}`
**Authorization**: `USER` role required (owner or ADMIN)

### Get Storage Usage

**Endpoint**: `GET /api/files/storage-usage`
**Authorization**: `USER` role required

**Response**: Returns bytes used by the user.

---

## Analytics

Base path: `/api/analytics`

**Note**: All endpoints require `ADMIN` role.

### Get Dashboard Data

**Endpoint**: `GET /api/analytics/dashboard`

**Query Parameters**:
- `currencyCode` (default: "USD")
- `shopId` (optional)

**Response**:
```json
{
  "todayOrders": 45,
  "weekOrders": 312,
  "monthOrders": 1250,
  "todayRevenue": 5450.00,
  "pendingRevenue": 1200.00,
  "totalRevenue": 45000.00,
  "totalInventoryValue": 150000.00,
  "lowStockCount": 15,
  "totalUsers": 500,
  "activeUsers": 480,
  "currency": {
    "code": "USD",
    "symbol": "$",
    "name": "US Dollar"
  },
  "averageOrderValue": 36.00,
  "conversionRate": 75.5
}
```

### Get Sales Trend

**Endpoint**: `GET /api/analytics/sales-trend`

**Query Parameters**:
- `startDate`: Start date (YYYY-MM-DD)
- `endDate`: End date (YYYY-MM-DD)
- `currencyCode` (default: "USD")

### Get Performance Metrics

**Endpoint**: `GET /api/analytics/performance`

**Query Parameters**:
- `currencyCode` (default: "USD")
- `shopId` (optional)

### Get Revenue Data

**Endpoint**: `GET /api/analytics/revenue`

**Query Parameters**:
- `currencyCode` (default: "USD")
- `days` (default: 30)
- `shopId` (optional)

### Get Inventory Analytics

**Endpoint**: `GET /api/analytics/inventory`

**Query Parameters**:
- `shopId` (optional)

---

## Accountancy

Base path: `/api/accountancy`

### Get Entries by Date Range

**Endpoint**: `GET /api/accountancy/entries?startDate={datetime}&endDate={datetime}`
**Authorization**: `ADMIN` role required

### Get My Entries

**Endpoint**: `GET /api/accountancy/my-entries`
**Authorization**: `USER` role required

**Query Parameters**: Standard pagination

### Get Periodic Summary

**Endpoint**: `GET /api/accountancy/summary/periodic`
**Authorization**: `ADMIN` role required

### Get Balance for Period

**Endpoint**: `GET /api/accountancy/balance/{period}`
**Authorization**: `ADMIN` role required

### Get Summary by Type

**Endpoint**: `GET /api/accountancy/summary/by-type`
**Authorization**: `ADMIN` role required

**Response**:
```json
{
  "debit": [...],
  "credit": [...]
}
```

### Create Entry

**Endpoint**: `POST /api/accountancy/entries`
**Authorization**: `ADMIN` role required

**Request Body**:
```json
{
  "type": "DEBIT",
  "amount": 151.38,
  "currency": {...},
  "description": "Sale - Order #890",
  "userId": 1,
  "referenceType": "ORDER",
  "referenceId": 890
}
```

---

## ZIMRA Fiscalisation

Base path: `/api/zimra`

### Fiscalise Order

**Endpoint**: `POST /api/zimra/fiscalise/order/{orderId}`

**Request Body**:
```json
{
  "deviceId": 1,
  "operatorId": "OP001"
}
```

### Fiscalise Sale

**Endpoint**: `POST /api/zimra/fiscalise/sale/{saleId}`

### Get by Fiscal Code

**Endpoint**: `GET /api/zimra/fiscal-code/{fiscalCode}`

### Verify by Code

**Endpoint**: `GET /api/zimra/verification/{verificationCode}`

### Get Shop Fiscalisations

**Endpoint**: `GET /api/zimra/shop/{shopId}?page={page}&size={size}`

### Get Fiscalisations by Date Range

**Endpoint**: `GET /api/zimra/shop/{shopId}/date-range?startDate={datetime}&endDate={datetime}`

### Get Total Fiscalised Amount

**Endpoint**: `GET /api/zimra/shop/{shopId}/total`

### Retry Failed Fiscalisations

**Endpoint**: `POST /api/zimra/retry-failed`

### Get Fiscal Device

**Endpoint**: `GET /api/zimra/devices/{deviceId}`

### Get Shop Devices

**Endpoint**: `GET /api/zimra/devices/shop/{shopId}`

---

## Enums Reference

### OrderStatus
- `PENDING` - Order created, awaiting confirmation
- `CONFIRMED` - Order confirmed
- `PROCESSING` - Order being processed
- `SHIPPED` - Order shipped
- `DELIVERED` - Order delivered
- `CANCELLED` - Order cancelled
- `COMPLETED` - Order completed

### PaymentMethod
- `ECOCASH` - EcoCash mobile money
- `SWIPE` - Card swipe
- `ONEMONEY` - OneMoney mobile money
- `CASH` - Cash payment
- `CREDIT_CARD` - Credit card
- `DEBIT_CARD` - Debit card
- `CHEQUE` - Cheque payment
- `ONLINE_PAYMENT` - Online payment

### SalesChannel
- `ONLINE` - Online shop
- `POS` - Point of sale
- `PHONE` - Phone order

### ShopType
- `WAREHOUSE` - Warehouse/distribution center
- `RETAIL` - Retail store
- `OUTLET` - Outlet store
- `ONLINE` - Online-only shop

### PriceType
- `SALE` - Sale price
- `REGULAR` - Regular price
- `PROMOTIONAL` - Promotional price
- `CLEARANCE` - Clearance price
- `BULK` - Bulk price
- `MEMBER` - Member price
- `WHOLESALE` - Wholesale price
- `RETAIL` - Retail price
- `ONLINE` - Online price
- `SEASONAL` - Seasonal price
- `FLASH_SALE` - Flash sale price

### TransferStatus
- `PENDING` - Pending approval
- `APPROVED` - Approved
- `IN_TRANSIT` - In transit
- `RECEIVED` - Received
- `COMPLETED` - Completed
- `CANCELLED` - Cancelled

### TransferType
- `REPLENISHMENT` - Stock replenishment
- `REBALANCING` - Inventory rebalancing
- `EMERGENCY` - Emergency transfer
- `RETURN` - Product return
- `EXPIRED` - Expired product transfer
- `DAMAGED` - Damaged product transfer
- `SEASONAL` - Seasonal transfer
- `PROMOTION` - Promotional transfer

### TransferPriority
- `LOW` - Low priority
- `NORMAL` - Normal priority
- `HIGH` - High priority
- `URGENT` - Urgent
- `CRITICAL` - Critical

### CashierRole
- `CASHIER` - Regular cashier
- `SUPERVISOR` - Shift supervisor
- `MANAGER` - Shop manager
- `ADMIN` - Administrator

### Role (User Accounts)
- `USER` - Regular user
- `ADMIN` - Administrator
- `CASHIER` - Cashier

### Permission
- `PROCESS_SALE` - Process sales
- `PROCESS_RETURN` - Process returns
- `APPLY_DISCOUNT` - Apply discounts
- `OPEN_CASH_DRAWER` - Open cash drawer
- `PERFORM_CASH_COUNT` - Perform cash count
- `ADJUST_CASH` - Adjust cash
- `VIEW_INVENTORY` - View inventory
- `TRANSFER_INVENTORY` - Transfer inventory
- `ADJUST_INVENTORY` - Adjust inventory
- `VIEW_REPORTS` - View reports
- `MANAGE_CASHIERS` - Manage cashiers
- `OVERRIDE_PRICE` - Override price
- `VOID_TRANSACTION` - Void transaction
- `ACCESS_BACK_OFFICE` - Access back office
- `MODIFY_SETTINGS` - Modify settings

### TaxNature
- `VAT` - Value Added Tax
- `FAST_FOOD_TAX` - Fast food levy
- `EXCISE_DUTY` - Excise duty
- `ENVIRONMENTAL_LEVY` - Environmental levy
- `CUSTOMS_DUTY` - Customs duty
- `SURTAX` - Surtax
- `OTHER` - Other taxes

### TaxCalculationType
- `FIXED` - Fixed amount per item
- `PERCENTAGE` - Percentage of price
- `TIERED` - Tiered/bracketed calculation

### SaleType
- `CASH` - Cash sale
- `CREDIT` - Credit sale

### EntryType (Accountancy)
- `DEBIT` - Debit entry
- `CREDIT` - Credit entry

---

## Error Responses

### Standard Error Format

```json
{
  "timestamp": "2026-01-15T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/endpoint"
}
```

### HTTP Status Codes

| Status | Meaning | Usage |
|--------|---------|-------|
| 200 | OK | Successful GET, PUT, POST |
| 201 | Created | Successful resource creation |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Validation errors, invalid input |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource, insufficient inventory |
| 500 | Internal Server Error | Server-side error |

### Common Error Scenarios

**Insufficient Stock**:
```json
{
  "status": 409,
  "error": "Insufficient Inventory",
  "message": "Cannot transfer 200 units. Available: 125"
}
```

**Resource Not Found**:
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Shop not found with ID: 999"
}
```

**Validation Error**:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Quantity must be greater than zero"
}
```

**Permission Denied**:
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied"
}
```

---

## Pagination

List endpoints support pagination with these parameters:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `page` | Page number (0-indexed) | 0 |
| `size` | Items per page | 20 |
| `sort` | Sort field and direction | varies |

**Example Request**:
```http
GET /api/orders/paginated?page=0&size=10&sort=createdAt,desc
```

**Response Structure**:
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "sorted": true,
      "direction": "DESC"
    }
  },
  "totalElements": 150,
  "totalPages": 15,
  "first": true,
  "last": false
}
```

---

## Date/Time Formats

All date/time parameters use ISO 8601 format:

- **Date**: `YYYY-MM-DD` (e.g., `2026-01-15`)
- **DateTime**: `YYYY-MM-DDTHH:mm:ss` (e.g., `2026-01-15T10:30:00`)

---

**Version**: 2.0
**Last Updated**: 2026-02-02

For interactive API testing, visit: `http://localhost:9090/swagger-ui.html`
