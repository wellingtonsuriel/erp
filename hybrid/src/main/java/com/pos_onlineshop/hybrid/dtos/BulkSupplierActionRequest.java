package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;
import java.util.List;

@Data
public class BulkSupplierActionRequest {
    private List<Long> supplierIds;
}
