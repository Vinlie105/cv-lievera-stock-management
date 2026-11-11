package com.cvlievera.stockmanagement.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    Page<ProductDTO.Response> getProducts(String search, Long categoryId, String brand, Boolean active, Pageable pageable);

    ProductDTO.Response getProductById(Long id);

    ProductDTO.Response getProductBySku(String sku);

    List<ProductDTO.Response> getLowStockProducts();

    ProductDTO.Response createProduct(ProductDTO.Request request);

    ProductDTO.Response updateProduct(Long id, ProductDTO.Request request);

    ProductDTO.Response deactivateProduct(Long id);

    ProductDTO.Response activateProduct(Long id);

    ProductDTO.StockResponse getProductStock(Long id);

    void deleteProduct(Long id);
}
