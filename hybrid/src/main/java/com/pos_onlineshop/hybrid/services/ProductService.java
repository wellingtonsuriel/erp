package com.pos_onlineshop.hybrid.services;



import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;


    /**
     * Create a new product
     */
    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product.getName());

        // Validate required fields
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Product category is required");
        }

        Product savedProduct = productRepository.save(product);

        log.info("Successfully created product: {} with ID: {}", savedProduct.getName(), savedProduct.getId());
        return savedProduct;
    }



    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        return productRepository.findBySku(sku);
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    @Transactional(readOnly = true)
    public List<Product> findWeighableProducts() {
        return productRepository.findByWeighableTrue();
    }

    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return productRepository.findAllCategories();
    }

    /**
     * Update product details
     */
    public Product updateProduct(Long id, Product productDetails) {
        return productRepository.findById(id)
                .map(product -> {
                    // Update basic fields
                    if (productDetails.getName() != null) {
                        product.setName(productDetails.getName());
                    }
                    if (productDetails.getDescription() != null) {
                        product.setDescription(productDetails.getDescription());
                    }
                    if (productDetails.getCategory() != null) {
                        product.setCategory(productDetails.getCategory());
                    }
                    if (productDetails.getImageUrl() != null) {
                        product.setImageUrl(productDetails.getImageUrl());
                    }
                    if (productDetails.getWeight() != null) {
                        product.setWeight(productDetails.getWeight());
                    }
                    if (productDetails.getUnitOfMeasure() != null) {
                        product.setUnitOfMeasure(productDetails.getUnitOfMeasure());
                    }
                    if (productDetails.getActualMeasure() != null) {
                        product.setActualMeasure(productDetails.getActualMeasure());
                    }
                    if (productDetails.getMaxStock() != null) {
                        product.setMaxStock(productDetails.getMaxStock());
                    }
                    if (productDetails.getMinStock() != null) {
                        product.setMinStock(productDetails.getMinStock());
                    }

                    // Update weighable flag
                    product.setWeighable(productDetails.isWeighable());
                    product.setActive(productDetails.isActive());

                    Product updated = productRepository.save(product);

                    log.info("Updated product: {}", updated.getName());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    /**
     * Soft delete product (set inactive)
     */
    public void deactivateProduct(Long id) {
        productRepository.findById(id)
                .ifPresentOrElse(
                        product -> {
                            product.setActive(false);
                            productRepository.save(product);
                            log.info("Deactivated product: {}", product.getName());
                        },
                        () -> {
                            throw new RuntimeException("Product not found: " + id);
                        }
                );
    }



    /**
     * Activate/reactivate product
     */
    public void activateProduct(Long id) {
        productRepository.findById(id)
                .ifPresentOrElse(
                        product -> {
                            product.setActive(true);
                            productRepository.save(product);
                            log.info("Activated product: {}", product.getName());
                        },
                        () -> {
                            throw new RuntimeException("Product not found: " + id);
                        }
                );
    }

}