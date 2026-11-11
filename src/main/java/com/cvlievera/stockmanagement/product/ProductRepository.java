package com.cvlievera.stockmanagement.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Product> findBySupplierId(Long supplierId);

    @Query("""
            SELECT p FROM Product p WHERE
            (:active IS NULL OR p.active = :active) AND
            (:name IS NULL OR LOWER(p.name) LIKE :name) AND
            (:categoryId IS NULL OR p.category.id = :categoryId) AND
            (:brand IS NULL OR LOWER(p.brand) = :brand)
            """)
    Page<Product> findWithFilters(
            @Param("active") Boolean active,
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("brand") String brand,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.quantityInStock <= p.minStockLevel AND p.active = true")
    List<Product> findLowStockProducts();
}
