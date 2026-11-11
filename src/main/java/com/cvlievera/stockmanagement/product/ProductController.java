package com.cvlievera.stockmanagement.product;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.cvlievera.stockmanagement.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductDTO.Response>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(productService.getProducts(search, categoryId, brand, active, pageable))));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<ProductDTO.Response>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getLowStockProducts()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO.Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiResponse<ProductDTO.Response>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductBySku(sku)));
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<ProductDTO.StockResponse>> getStock(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductStock(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDTO.Response>> create(@Valid @RequestBody ProductDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(productService.createProduct(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody ProductDTO.Request request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateProduct(id, request)));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<ProductDTO.Response>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.deactivateProduct(id)));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<ProductDTO.Response>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.activateProduct(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
