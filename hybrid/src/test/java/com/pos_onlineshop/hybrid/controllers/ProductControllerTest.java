package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.services.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The audit's P2 finding: DELETE /api/products/{id} previously never called any service method
 * at all - it just returned 204 unconditionally, so "deleting" a product silently did nothing.
 * It now delegates to ProductService.deactivateProduct (a pre-existing soft-delete that was
 * simply never wired up), consistent with not hard-deleting a product that order/inventory/
 * pricing history may still reference.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock private ProductService productService;

    private ProductController controller;

    @Test
    void deleteProductActuallyDeactivatesTheProduct() {
        controller = new ProductController(productService);
        doNothing().when(productService).deactivateProduct(1L);

        ResponseEntity<Void> response = controller.deleteProduct(1L);

        verify(productService).deactivateProduct(1L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void deleteProductReturns404ForAnUnknownProductRatherThanFalselySucceeding() {
        controller = new ProductController(productService);
        doThrow(new RuntimeException("Product not found: 99")).when(productService).deactivateProduct(99L);

        ResponseEntity<Void> response = controller.deleteProduct(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
