package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CopyPricesRequest;
import com.pos_onlineshop.hybrid.dtos.SellingPriceCreateRequest;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.services.SellingPriceService;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.tax.TaxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every mutating endpoint on this controller used to trust a request-body "createdBy"/
 * "updatedBy" field for its audit trail - the same class of bug already fixed on
 * CashierController.grantPermission (grantedBy spoofing). These tests confirm the authenticated
 * principal's own username is used instead, and that a client-supplied value in the request body
 * is ignored rather than trusted.
 */
@ExtendWith(MockitoExtension.class)
class SellingPriceControllerTest {

    @Mock private SellingPriceService sellingPriceService;
    @Mock private ProductRepository productRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private TaxRepository taxRepository;

    private SellingPriceController controller;

    private UserDetails principal(String username) {
        return new User(username, "hashed", true, true, true, true, List.of());
    }

    @Test
    void createSellingPriceUsesTheAuthenticatedPrincipalAsCreatedByNeverTheRequestBody() {
        controller = new SellingPriceController(sellingPriceService, productRepository, shopRepository,
                currencyRepository, taxRepository);

        Product product = Product.builder().id(1L).build();
        Shop shop = Shop.builder().id(2L).build();
        Currency currency = Currency.builder().id(3L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(shopRepository.findById(2L)).thenReturn(Optional.of(shop));
        when(currencyRepository.findById(3L)).thenReturn(Optional.of(currency));
        when(sellingPriceService.createOrUpdatePrice(any(SellingPrice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sellingPriceService.toResponse(any())).thenReturn(null);

        SellingPriceCreateRequest request = new SellingPriceCreateRequest();
        request.setProductId(1L);
        request.setShopId(2L);
        request.setCurrencyId(3L);
        request.setPriceType(PriceType.REGULAR);
        request.setSellingPrice(new BigDecimal("10.00"));
        request.setBasePrice(new BigDecimal("8.00"));
        request.setCreatedBy("attacker-supplied-name");

        controller.createSellingPrice(request, principal("real-manager"));

        ArgumentCaptor<SellingPrice> captor = ArgumentCaptor.forClass(SellingPrice.class);
        verify(sellingPriceService).createOrUpdatePrice(captor.capture());
        assertEquals("real-manager", captor.getValue().getCreatedBy());
        assertNotEquals("attacker-supplied-name", captor.getValue().getCreatedBy());
    }

    @Test
    void copyPricesFromShopUsesTheAuthenticatedPrincipalAsCreatedByNeverTheRequestBody() {
        controller = new SellingPriceController(sellingPriceService, productRepository, shopRepository,
                currencyRepository, taxRepository);

        Shop source = Shop.builder().id(1L).build();
        Shop target = Shop.builder().id(2L).build();
        when(shopRepository.findById(1L)).thenReturn(Optional.of(source));
        when(shopRepository.findById(2L)).thenReturn(Optional.of(target));

        CopyPricesRequest request = new CopyPricesRequest();
        request.setSourceShopId(1L);
        request.setTargetShopId(2L);
        request.setCreatedBy("attacker-supplied-name");

        controller.copyPricesFromShop(request, principal("real-admin"));

        verify(sellingPriceService).copyPricesFromShop(source, target, "real-admin");
    }
}
