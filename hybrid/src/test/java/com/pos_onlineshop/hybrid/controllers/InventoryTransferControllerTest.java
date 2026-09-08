package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.services.InventoryTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * The audit's P2 date-format finding: the admin transfers screen's date-range picker sends
 * date-only values ("2024-01-20") which previously failed to bind against a LocalDateTime
 * parameter requiring ISO_DATE_TIME. LocalDate params now accept the picker's actual format,
 * expanded here to a start-of-day/end-of-day range before reaching the service.
 */
@ExtendWith(MockitoExtension.class)
class InventoryTransferControllerTest {

    @Mock private InventoryTransferService transferService;
    @Mock private CashierRepository cashierRepository;

    private InventoryTransferController controller;

    @Test
    void getTransfersByDateRangeExpandsPlainDatesToAnInclusiveWholeDayRange() {
        controller = new InventoryTransferController(transferService, cashierRepository);
        when(transferService.findByDateRange(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        ResponseEntity<List<InventoryTransfer>> response = controller.getTransfersByDateRange(
                LocalDate.of(2024, 1, 20), LocalDate.of(2024, 1, 22));

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(transferService).findByDateRange(startCaptor.capture(), endCaptor.capture());

        assertEquals(LocalDateTime.of(2024, 1, 20, 0, 0, 0), startCaptor.getValue());
        assertEquals(LocalDateTime.of(2024, 1, 22, 23, 59, 59), endCaptor.getValue());
        assertEquals(200, response.getStatusCode().value());
    }
}
