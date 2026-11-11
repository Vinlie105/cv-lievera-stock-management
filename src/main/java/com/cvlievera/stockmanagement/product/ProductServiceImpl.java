package com.cvlievera.stockmanagement.product;

import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.cvlievera.stockmanagement.exception.ResourceNotFoundException;
import com.cvlievera.stockmanagement.supplier.Supplier;
import com.cvlievera.stockmanagement.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    @Override
    public Page<ProductDTO.Response> getProducts(String search, Long categoryId, String brand, Boolean active, Pageable pageable) {
        Boolean activeFilter = (active != null) ? active : Boolean.TRUE;
        String nameFilter = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        String brandFilter = (brand != null && !brand.isBlank()) ? brand.trim().toLowerCase() : null;
        return productRepository.findWithFilters(activeFilter, nameFilter, categoryId, brandFilter, pageable)
                .map(this::toResponse);
    }

    @Override
    public ProductDTO.Response getProductById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public ProductDTO.Response getProductBySku(String sku) {
        return toResponse(productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU: " + sku)));
    }

    @Override
    public List<ProductDTO.Response> getLowStockProducts() {
        return productRepository.findLowStockProducts().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductDTO.Response createProduct(ProductDTO.Request request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new BadRequestException("Product with SKU '" + request.getSku() + "' already exists");
        }
        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku())
                .description(request.getDescription())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .quantityInStock(request.getQuantityInStock())
                .minStockLevel(request.getMinStockLevel())
                .unit(request.getUnit() != null ? request.getUnit() : Unit.PCS)
                .brand(request.getBrand())
                .barcode(request.getBarcode())
                .expiryTracked(request.isExpiryTracked())
                .active(true)
                .category(resolveCategory(request.getCategoryId()))
                .supplier(resolveSupplier(request.getSupplierId()))
                .build();
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductDTO.Response updateProduct(Long id, ProductDTO.Request request) {
        Product product = findById(id);
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setDescription(request.getDescription());
        product.setUnitPrice(request.getUnitPrice());
        product.setCostPrice(request.getCostPrice());
        product.setQuantityInStock(request.getQuantityInStock());
        product.setMinStockLevel(request.getMinStockLevel());
        product.setUnit(request.getUnit() != null ? request.getUnit() : Unit.PCS);
        product.setBrand(request.getBrand());
        product.setBarcode(request.getBarcode());
        product.setExpiryTracked(request.isExpiryTracked());
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setSupplier(resolveSupplier(request.getSupplierId()));
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductDTO.Response deactivateProduct(Long id) {
        Product product = findById(id);
        if (!product.isActive()) {
            throw new BadRequestException("Product is already inactive");
        }
        product.setActive(false);
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductDTO.Response activateProduct(Long id) {
        Product product = findById(id);
        if (product.isActive()) {
            throw new BadRequestException("Product is already active");
        }
        product.setActive(true);
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductDTO.StockResponse getProductStock(Long id) {
        Product product = findById(id);
        return ProductDTO.StockResponse.builder()
                .productId(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .unit(product.getUnit())
                .quantityInStock(product.getQuantityInStock())
                .minStockLevel(product.getMinStockLevel())
                .lowStock(product.getQuantityInStock() <= product.getMinStockLevel())
                .active(product.isActive())
                .build();
    }

    @Override
    public void deleteProduct(Long id) {
        productRepository.delete(findById(id));
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private Supplier resolveSupplier(Long supplierId) {
        if (supplierId == null) return null;
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
    }

    private ProductDTO.Response toResponse(Product product) {
        return ProductDTO.Response.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .unitPrice(product.getUnitPrice())
                .costPrice(product.getCostPrice())
                .quantityInStock(product.getQuantityInStock())
                .minStockLevel(product.getMinStockLevel())
                .unit(product.getUnit())
                .brand(product.getBrand())
                .barcode(product.getBarcode())
                .active(product.isActive())
                .expiryTracked(product.isExpiryTracked())
                .lowStock(product.getQuantityInStock() <= product.getMinStockLevel())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .supplierName(product.getSupplier() != null ? product.getSupplier().getName() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
