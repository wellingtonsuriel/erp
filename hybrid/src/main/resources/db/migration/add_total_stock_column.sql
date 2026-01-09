-- Migration: Add total_stock column to shop_inventories table
-- Date: 2026-01-09
-- Description: Adds totalStock field for tracking cumulative lifetime stock additions
--              This provides an audit trail of all stock ever added to the inventory

-- Add the total_stock column
ALTER TABLE shop_inventories
ADD COLUMN total_stock INT NOT NULL DEFAULT 0
COMMENT 'Total cumulative stock added over lifetime (audit trail)';

-- Initialize total_stock with current quantity for existing records
UPDATE shop_inventories
SET total_stock = quantity
WHERE total_stock = 0;

-- Add index for performance (optional but recommended)
CREATE INDEX idx_shop_inventories_total_stock ON shop_inventories(total_stock);
