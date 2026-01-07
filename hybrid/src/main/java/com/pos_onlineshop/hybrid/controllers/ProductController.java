package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateProductRequest;
import com.pos_onlineshop.hybrid.dtos.SetPriceRequest;
import com.pos_onlineshop.hybrid.dtos.SetSpecialPriceRequest;

import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.services.CurrencyService;
import com.pos_onlineshop.hybrid.services.ProductService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;
    private final CurrencyService currencyService;

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }



    @GetMapping("/{id}/price")
    public ResponseEntity<Map<String, Object>> getProductPrice(
            @PathVariable Long id,
            @RequestParam String currency) {
        try {
            Product product = productService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            Currency curr = currencyService.findByCode(currency)
                    .orElseThrow(() -> new RuntimeException("Currency not found"));

            BigDecimal price = productService.getProductPrice(product, curr);

            return ResponseEntity.ok(Map.of(
                    "productId", id,
                    "currency", currency,
                    "price", price,
                    "formattedPrice", curr.getSymbol() + price
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody CreateProductRequest request) {
        // Note: Product entity does not have barcode, sku, price, baseCurrency, taxRate, minQuantity fields
        // Use ShopInventory for barcode/sku and SellingPrice for pricing
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .weighable(request.isWeighable())
                .weight(request.getWeight())
                .unitOfMeasure(request.getUnitOfMeasure())
                .actualMeasure(request.getActualMeasure())
                .minStock(request.getMinStock())
                .maxStock(request.getMaxStock())
                .build();

        Product created = productService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/prices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setProductPrice(
            @PathVariable Long id,
            @RequestBody SetPriceRequest request) {
        try {
            Product product = productService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            Currency currency = currencyService.findByCode(request.getCurrencyCode())
                    .orElseThrow(() -> new RuntimeException("Currency not found"));

            productService.setProductPrice(product, currency, request.getPrice());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

//    @PostMapping("/{id}/special-price")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<Void> setSpecialPrice(
//            @PathVariable Long id,
//            @RequestBody SetSpecialPriceRequest request) {
//        try {
//            Product product = productService.findById(id)
//                    .orElseThrow(() -> new RuntimeException("Product not found"));
//            Currency currency = currencyService.findByCode(request.getCurrencyCode())
//                    .orElseThrow(() -> new RuntimeException("Currency not found"));
//
//            productService.setSpecialPrice(
//                    product, currency, request.getSpecialPrice(),
//                    request.getValidFrom(), request.getValidTo()
//            );
//            return ResponseEntity.ok().build();
//        } catch (RuntimeException e) {
//            return ResponseEntity.badRequest().build();
//        }
//    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody Product product) {
        try {
            Product updated = productService.updateProduct(id, product);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // Other existing endpoints...
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<Product> getProductByBarcode(@PathVariable String barcode) {
        return productService.findByBarcode(barcode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        return productService.findBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {
        return productService.findByCategory(category);
    }

    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String name) {
        return productService.searchByName(name);
    }

    @GetMapping("/price-range")
    public List<Product> getProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        return productService.findByPriceRange(minPrice, maxPrice);
    }

    @GetMapping("/categories")
    public List<String> getAllCategories() {
        return productService.getAllCategories();
    }


}