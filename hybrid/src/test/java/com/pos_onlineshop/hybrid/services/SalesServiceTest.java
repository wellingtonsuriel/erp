package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.mappers.SaleMapper;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.sales.Sales;
import com.pos_onlineshop.hybrid.sales.SalesRepository;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock private SalesRepository salesRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private CustomersRepository customersRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private SaleMapper saleMapper;
    @Mock private ZimraService zimraService;

    private SalesService service;

    @BeforeEach
    void setUp() {
        service = new SalesService(salesRepository, shopRepository, customersRepository, productRepository,
                currencyRepository, saleMapper, zimraService);
    }

    @Test
    void findAllAppliesABoundedPageableCapInsteadOfLoadingEveryPosSaleEver() {
        Sales sale = Sales.builder().id(1L).build();
        when(salesRepository.findAll(any(Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(sale)));

        List<Sales> result = service.findAll();

        assertEquals(1, result.size());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(salesRepository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertTrue(captor.getValue().getPageSize() > 0, "must apply a bounded page size, never an unbounded query");
    }
}
